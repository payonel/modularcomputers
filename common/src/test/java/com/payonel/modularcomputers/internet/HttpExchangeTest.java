package com.payonel.modularcomputers.internet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The internet card's HTTP client, against a server on this machine (which the tests' policy allows, and the default refuses). */
class HttpExchangeTest {
	/** Loopback allowed: these tests' server is on this machine. */
	private static final InternetPolicy LOCAL = new InternetPolicy(true, List.of("127.0.0.1"), List.of());
	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();
	private String base;

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/echo", exchange -> {
			requests.incrementAndGet();
			byte[] body = (exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("X-Mine") + " "
				+ new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("X-Back", "yes");
			exchange.sendResponseHeaders(201, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/chunked", exchange -> {
			requests.incrementAndGet();
			exchange.sendResponseHeaders(200, 0); // (0: chunked)
			for (int i = 0; i < 3; i++) {
				exchange.getResponseBody().write(("piece" + i + ";").getBytes(StandardCharsets.UTF_8));
				exchange.getResponseBody().flush();
			}
			exchange.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	/** What a request's listener heard: its response, its body, its end or why it failed. */
	private static final class Heard implements HttpExchange.Listener {
		int status;
		Map<String, String> headers = new LinkedHashMap<>();
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		final List<String> pieces = new ArrayList<>();
		boolean ended;
		String failed;

		@Override
		public void response(int status, String message, Map<String, String> headers) {
			this.status = status;
			this.headers = headers;
		}

		@Override
		public boolean data(byte[] bytes) {
			body.writeBytes(bytes);
			pieces.add(new String(bytes, StandardCharsets.UTF_8));
			return true;
		}

		@Override
		public void end() {
			ended = true;
		}

		@Override
		public void failed(String why) {
			failed = why;
		}
	}

	private Heard run(String url, String method, Map<String, String> headers, String body, InternetPolicy policy, HttpExchange.Resolver resolver) {
		Heard heard = new Heard();
		HttpExchange.of(url, method, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8), policy, resolver, heard).run();
		return heard;
	}

	@Test
	void aRequestGetsItsStatusHeadersAndBodyAndSendsWhatItsAsked() {
		Heard heard = run(base + "/echo?q=1", "POST", Map.of("X-Mine", "mine"), "hello", LOCAL, HttpExchange.SYSTEM);
		assertThat(heard.failed).isNull();
		assertThat(heard.status).isEqualTo(201);
		assertThat(heard.headers).containsEntry("x-back", "yes");
		assertThat(heard.body.toString(StandardCharsets.UTF_8)).isEqualTo("POST /echo?q=1 mine hello");
		assertThat(heard.ended).isTrue();
	}

	@Test
	void aChunkedBodyComesWhole() {
		Heard heard = run(base + "/chunked", null, Map.of(), null, LOCAL, HttpExchange.SYSTEM);
		assertThat(heard.body.toString(StandardCharsets.UTF_8)).isEqualTo("piece0;piece1;piece2;");
		assertThat(heard.ended).isTrue();
	}

	@Test
	void aHeadRequestHasNoBody() {
		Heard heard = run(base + "/echo", "HEAD", Map.of(), null, LOCAL, HttpExchange.SYSTEM);
		assertThat(heard.status).isEqualTo(201);
		assertThat(heard.body.size()).isZero();
		assertThat(heard.ended).isTrue();
	}

	@Test
	void byDefaultTheServersOwnMachineIsRefusedBeforeAnythingIsSent() {
		Heard heard = run(base + "/echo", null, Map.of(), null, InternetPolicy.DEFAULT, HttpExchange.SYSTEM);
		assertThat(heard.failed).startsWith("refused:");
		assertThat(requests.get()).as("no connection made").isZero();
		Heard named = run("http://localhost:" + server.getAddress().getPort() + "/echo", null, Map.of(), null, InternetPolicy.DEFAULT, HttpExchange.SYSTEM);
		assertThat(named.failed).as("by its name too").startsWith("refused:");
		assertThat(requests.get()).isZero();
	}

	@Test
	void aNameThatAlsoResolvesPrivatelyIsRefusedAndTheAddressJudgedIsTheOneConnected() throws Exception {
		InetAddress loopback = InetAddress.getByName("127.0.0.1");
		InetAddress open = InetAddress.getByName("93.184.216.34");
		HttpExchange.Resolver rebinding = host -> List.of(open, loopback);
		Heard heard = run("http://rebind.example:" + server.getAddress().getPort() + "/echo", null, Map.of(), null, InternetPolicy.DEFAULT, rebinding);
		assertThat(heard.failed).startsWith("refused:");
		assertThat(requests.get()).isZero();
		// Allowed by name, its judged address is the one it connects to: no second lookup.
		InternetPolicy allowed = new InternetPolicy(true, List.of("pinned.example"), List.of());
		HttpExchange.Resolver once = new HttpExchange.Resolver() {
			int asked;

			@Override
			public List<InetAddress> resolve(String host) {
				asked++;
				assertThat(asked).as("looked up once").isEqualTo(1);
				return List.of(loopback);
			}
		};
		Heard pinned = run("http://pinned.example:" + server.getAddress().getPort() + "/echo", null, Map.of(), null, allowed, once);
		assertThat(pinned.status).isEqualTo(201);
	}

	@Test
	void whatCantBeARequestIsRefusedBeforeItStarts() {
		for (String url : List.of("file:///etc/passwd", "ftp://example.com/", "gopher://example.com/", "http://user:pass@example.com/", "not a url",
			"http:///nohost")) {
			assertThatThrownBy(() -> HttpExchange.of(url, null, Map.of(), null, LOCAL, HttpExchange.SYSTEM, new Heard())).as(url)
				.isInstanceOf(IllegalArgumentException.class);
		}
		for (Map<String, String> headers : List.of(Map.of("X-Bad", "a\r\nInjected: yes"), Map.of("Bad Name", "x"), Map.of("Host", "elsewhere"),
			Map.of("Content-Length", "0"), Map.of("Transfer-Encoding", "chunked"), Map.of("Connection", "keep-alive"))) {
			assertThatThrownBy(() -> HttpExchange.of(base + "/echo", null, headers, null, LOCAL, HttpExchange.SYSTEM, new Heard())).as(headers.toString())
				.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> HttpExchange.of(base + "/echo", "CONNECT", Map.of(), null, LOCAL, HttpExchange.SYSTEM, new Heard()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(requests.get()).isZero();
	}
}
