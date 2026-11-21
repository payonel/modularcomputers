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
	/** The default limits (the settings at their defaults, as the tests' JVM starts). */
	private static final InternetLimits DEFAULTS = com.payonel.modularcomputers.config.ModConfig.internetLimits();
	/** A timeout set short, in seconds. */
	private static final double SHORT_SECONDS = 0.2;
	/** How long a request timed out by it may take at most, in milliseconds: well short of the defaults (30 s with nothing read, 10 minutes in all). */
	private static final long SHORT_ENDS_WITHIN = 10_000;
	/** A header longer than a 1 KiB limit, in bytes. */
	private static final int LONG_HEADER = 2048;
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
		server.createContext("/long", exchange -> {
			requests.incrementAndGet();
			exchange.getResponseHeaders().add("X-Long", "x".repeat(LONG_HEADER));
			exchange.sendResponseHeaders(204, -1);
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
		return run(url, method, headers, body, policy, DEFAULTS, resolver);
	}

	/** One with these limits. */
	private Heard run(String url, String method, Map<String, String> headers, String body, InternetPolicy policy, InternetLimits limits,
		HttpExchange.Resolver resolver) {
		Heard heard = new Heard();
		HttpExchange.of(url, method, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8), policy, limits, resolver, heard).run();
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
		int most = DEFAULTS.maxUploadBytes();
		assertThat(most).as("the default: 1 MiB").isEqualTo(1024 * 1024);
		assertThat(refused(base + "/echo", "POST", Map.of(), new byte[most + 1]))
			.isEqualTo(Map.of("why", "limit", "of", "body", "max", (long) most));
		assertThat(requests.get()).isZero();
	}

	/** Limits as the settings give them (an admin's units: seconds, KiB), the counts the defaults. */
	private static InternetLimits limits(double connect, double read, double request, int headerKib, int uploadKib) {
		return InternetLimits.of(connect, read, request, headerKib, uploadKib, DEFAULTS.perCard(), DEFAULTS.perServer());
	}

	/**
	 * The card's limits are the settings' (the user's, 2026-11-21): a request's own body past
	 * internet.max_upload_kib is refused, saying that max (in bytes); a response's head past
	 * internet.max_header_kib fails.
	 */
	@Test
	void anUploadAndAResponsesHeadersAreKeptToTheSizesSet() {
		InternetLimits small = limits(10, 30, 600, 1, 1);
		assertThat(refused(base + "/echo", "POST", Map.of(), new byte[1025], small)).isEqualTo(Map.of("why", "limit", "of", "body", "max", 1024L));
		assertThat(run(base + "/echo", "POST", Map.of(), "x".repeat(1024), LOCAL, small, HttpExchange.SYSTEM).status).as("at the max: made").isEqualTo(201);
		assertThat(run(base + "/long", null, Map.of(), null, LOCAL, small, HttpExchange.SYSTEM).failed.table())
			.isEqualTo(Map.of("why", "network", "reason", "headers_too_long", "message", "response headers too long"));
		assertThat(run(base + "/long", null, Map.of(), null, LOCAL, HttpExchange.SYSTEM).status).as("within the default").isEqualTo(204);
	}

	/** A server on this machine that takes one connection, reads its request, then does what it's given with it. */
	private static java.net.ServerSocket answering(java.util.function.Consumer<java.io.OutputStream> answer) throws java.io.IOException {
		java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(() -> {
			try (java.net.Socket client = socket.accept()) {
				java.io.InputStream in = client.getInputStream();
				for (int matched = 0; matched < 4; ) { // (to the request's end: CR LF CR LF)
					int c = in.read();
					if (c < 0) {
						return;
					}
					matched = c == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : c == '\r' ? 1 : 0;
				}
				answer.accept(client.getOutputStream());
				while (in.read() >= 0) {
					// (holds on till the request closes it)
				}
			} catch (java.io.IOException e) {
				// (closed)
			}
		}, "answering server");
		thread.setDaemon(true);
		thread.start();
		return socket;
	}

	/** A request with these limits: what it heard, and how long it took to end, in milliseconds. */
	private Heard timed(String url, InternetLimits limits, long[] millis) {
		long start = System.nanoTime();
		Heard heard = run(url, null, Map.of(), null, LOCAL, limits, HttpExchange.SYSTEM);
		millis[0] = (System.nanoTime() - start) / 1_000_000;
		return heard;
	}

	/**
	 * Its timeouts are the settings' (the user's, 2026-11-21): a server that says nothing times a
	 * request out at internet.read_timeout; one dripping its body, at internet.request_timeout, its
	 * time in all. Each set short ends well before its default.
	 */
	@Test
	void aRequestTimesOutAtTheTimesSet() throws Exception {
		long[] millis = new long[1];
		Map<String, Object> timedOut = Map.of("why", "network", "reason", "timed_out", "message", "timed out");
		try (java.net.ServerSocket quiet = answering(out -> { })) {
			Heard heard = timed("http://127.0.0.1:" + quiet.getLocalPort() + "/", limits(10, SHORT_SECONDS, 600, 64, 1024), millis);
			assertThat(heard.failed.table()).isEqualTo(timedOut);
			assertThat(millis[0]).as("its read timeout, not the default's").isLessThan(SHORT_ENDS_WITHIN);
		}
		try (java.net.ServerSocket dripping = answering(out -> {
			try {
				out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
				for (int i = 0; i < 1_000_000; i++) {
					out.write('x');
					out.flush();
					Thread.sleep(DRIP_MILLIS);
				}
			} catch (java.io.IOException | InterruptedException e) {
				// (the request gave up)
			}
		})) {
			Heard heard = timed("http://127.0.0.1:" + dripping.getLocalPort() + "/", limits(10, 30, SHORT_SECONDS, 64, 1024), millis);
			assertThat(heard.status).as("it had its answer").isEqualTo(200);
			assertThat(heard.failed.table()).isEqualTo(timedOut);
			assertThat(millis[0]).as("its time in all, not the default's").isLessThan(SHORT_ENDS_WITHIN);
		}
	}

	/** How often the dripping server sends a byte, in milliseconds: well within a read's timeout. */
	private static final long DRIP_MILLIS = 10;

	/** A request refused before it starts: its failure, as a program gets it. */
	private Map<String, Object> refused(String url, String method, Map<String, String> headers, byte[] body) {
		return refused(url, method, headers, body, DEFAULTS);
	}

	private Map<String, Object> refused(String url, String method, Map<String, String> headers, byte[] body, InternetLimits limits) {
		try {
			HttpExchange.of(url, method, headers, body, LOCAL, limits, HttpExchange.SYSTEM, new Heard());
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
