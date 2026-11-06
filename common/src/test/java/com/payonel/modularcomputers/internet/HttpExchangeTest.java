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
		com.payonel.modularcomputers.runtime.Failure failed;

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
		public void failed(com.payonel.modularcomputers.runtime.Failure why) {
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
		assertThat(heard.failed.table()).as("denied, its reason and the address judged")
			.isEqualTo(Map.of("why", "denied", "reason", "loopback", "value", server.getAddress().getAddress().getHostAddress()));
		assertThat(requests.get()).as("no connection made").isZero();
		Heard named = run("http://localhost:" + server.getAddress().getPort() + "/echo", null, Map.of(), null, InternetPolicy.DEFAULT, HttpExchange.SYSTEM);
		assertThat(named.failed.why()).as("by its name too").isEqualTo("denied");
		assertThat(named.failed.get("reason")).isEqualTo("loopback");
		assertThat(requests.get()).isZero();
	}

	@Test
	void aNameThatAlsoResolvesPrivatelyIsRefusedAndTheAddressJudgedIsTheOneConnected() throws Exception {
		InetAddress loopback = InetAddress.getByName("127.0.0.1");
		InetAddress open = InetAddress.getByName("93.184.216.34");
		HttpExchange.Resolver rebinding = host -> List.of(open, loopback);
		Heard heard = run("http://rebind.example:" + server.getAddress().getPort() + "/echo", null, Map.of(), null, InternetPolicy.DEFAULT, rebinding);
		assertThat(heard.failed.table()).as("the address that was refused").isEqualTo(Map.of("why", "denied", "reason", "loopback", "value", "127.0.0.1"));
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

	/** What can't be a request is refused before it starts, each in its why's shape (the user's): which argument, as given, what it takes; or denied, or a limit. */
	@Test
	void whatCantBeARequestIsRefusedBeforeItStarts() {
		Map<String, Map<String, Object>> urls = new LinkedHashMap<>();
		urls.put("file:///etc/passwd", Map.of("why", "bad_argument", "argument", 1L, "value", "file:///etc/passwd", "expected", "http"));
		urls.put("ftp://example.com/", Map.of("why", "bad_argument", "argument", 1L, "value", "ftp://example.com/", "expected", "http"));
		urls.put("not a url", Map.of("why", "bad_argument", "argument", 1L, "value", "not a url", "expected", "url"));
		urls.put("http:///nohost", Map.of("why", "bad_argument", "argument", 1L, "value", "http:///nohost", "expected", "host"));
		urls.put("http://user:pass@example.com/", Map.of("why", "denied", "reason", "credentials", "value", "http://user:pass@example.com/"));
		urls.forEach((url, shape) -> assertThat(refused(url, null, Map.of(), null)).as(url).isEqualTo(shape));
		assertThat(refused(base + "/echo", null, Map.of("X-Bad", "a\r\nInjected: yes"), null))
			.isEqualTo(Map.of("why", "bad_argument", "argument", 3L, "value", "X-Bad", "expected", "header"));
		assertThat(refused(base + "/echo", null, Map.of("Bad Name", "x"), null))
			.isEqualTo(Map.of("why", "bad_argument", "argument", 3L, "value", "Bad Name", "expected", "header"));
		for (String own : List.of("Host", "Content-Length", "Transfer-Encoding", "Connection")) {
			assertThat(refused(base + "/echo", null, Map.of(own, "x"), null)).as(own).isEqualTo(Map.of("why", "denied", "reason", "reserved_header", "value", own));
		}
		assertThat(refused(base + "/echo", "CONNECT", Map.of(), null)).isEqualTo(Map.of("why", "bad_argument", "argument", 2L, "value", "CONNECT", "expected", "method"));
		assertThat(refused(base + "/echo", "POST", Map.of(), new byte[HttpExchange.MAX_BODY + 1]))
			.isEqualTo(Map.of("why", "limit", "of", "body", "max", (long) HttpExchange.MAX_BODY));
		assertThat(requests.get()).isZero();
	}

	/** A request refused before it starts: its failure, as a program gets it. */
	private Map<String, Object> refused(String url, String method, Map<String, String> headers, byte[] body) {
		try {
			HttpExchange.of(url, method, headers, body, LOCAL, HttpExchange.SYSTEM, new Heard());
		} catch (HttpExchange.Refused e) {
			return e.failure().table();
		}
		throw new AssertionError("not refused: " + url);
	}

	/** Failed in transit, one shape (the user's): network, its reason, and that reason's own words, never Java's (it could tell of the server), whichever way it was found. */
	@Test
	void aNameThatDoesntResolveFailsInTransitItsReasonAndMessage() {
		HttpExchange.Resolver none = host -> {
			throw new java.net.UnknownHostException(host);
		};
		Heard heard = run("http://nowhere.invalid/", null, Map.of(), null, InternetPolicy.DEFAULT, none);
		assertThat(heard.failed.table()).isEqualTo(Map.of("why", "network", "reason", "unknown_host", "message", "host not found"));
		Heard empty = run("http://nowhere.invalid/", null, Map.of(), null, InternetPolicy.DEFAULT, host -> List.of());
		assertThat(empty.failed.table()).as("no address: the same words").isEqualTo(Map.of("why", "network", "reason", "unknown_host", "message", "host not found"));
	}
}
