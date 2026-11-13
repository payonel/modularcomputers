package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.internet.InternetPolicy;
import com.payonel.modularcomputers.runtime.Arguments;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** The internet card's interrupt: what keeps it pending, and what a program's call answers for. */
class InternetCardTest {
	/** How long a request on the loopback may take to end, in milliseconds. */
	private static final long ENDS_WITHIN = 10_000;

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	/**
	 * A log entry is the card's news until it's taken (its pull): a program's call about its own
	 * request answers for that request's news only. Else one program's read of its body, between the
	 * entry and the kernel's look, left the entry in the card with nothing pending: a log reader
	 * never woke for it (the internet log example, now and then, on a loaded host).
	 */
	@Test
	@ResourceLock("InternetCard.policy")
	void aLogEntryStaysPendingTillItsTakenWhateverElseIsAsked() throws Exception {
		HttpServer web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			byte[] body = "hi".getBytes();
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		web.start();
		InternetCard.policyFrom(() -> new InternetPolicy(true, List.of("127.0.0.1"), List.of()));
		try {
			InternetCard card = new InternetCard();
			card.accepting(true); // (as on a running computer's bus: it takes data)
			long handle = (Long) card.request(args("http://127.0.0.1:" + web.getAddress().getPort() + "/")).first();
			long deadline = System.currentTimeMillis() + ENDS_WITHIN;
			Object piece;
			do {
				piece = card.read(args(handle)).first(); // (as curl reads its body: an ask about its own request)
				assertThat(System.currentTimeMillis()).as("it ended in time").isLessThan(deadline);
			} while (!"".equals(piece));
			while (!card.interruptPending()) { // (its log entry comes as it ends)
				card.tick();
				assertThat(System.currentTimeMillis()).as("its log entry came").isLessThan(deadline);
				Thread.onSpinWait();
			}
			card.tick(); // (its end on its way into the log)
			card.response(args(handle)); // (asked about its request again: that news is looked at)
			assertThat(card.interruptPending()).as("its log entry, untaken, is still news").isTrue();
			assertThat(card.pull(args()).first()).isInstanceOf(Map.class);
			assertThat(card.interruptPending()).as("taken: nothing left").isFalse();
			card.close(args(handle));
		} finally {
			InternetCard.policyFrom(() -> InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}

	/** What a request holds of its body unread, in bytes (its buffer, looked at from outside). */
	private static long held(InternetCard card, long handle) throws Exception {
		java.lang.reflect.Field open = InternetCard.class.getDeclaredField("open");
		open.setAccessible(true);
		Object request = ((Map<?, ?>) open.get(card)).get(handle);
		java.lang.reflect.Field field = request.getClass().getDeclaredField("body");
		field.setAccessible(true);
		Object body = field.get(request);
		if (body instanceof java.util.Collection<?> pieces) { // (pieces, as it once held them)
			return pieces.stream().mapToLong(piece -> ((String) piece).length()).sum();
		}
		java.lang.reflect.Method size = body.getClass().getDeclaredMethod("size");
		size.setAccessible(true);
		return ((Number) size.invoke(body)).longValue();
	}

	/** Waits till the request holds {@code bytes} of its body unread. */
	private static void untilHeld(InternetCard card, long handle, long bytes) throws Exception {
		long deadline = System.currentTimeMillis() + ENDS_WITHIN;
		while (held(card, handle) < bytes) {
			assertThat(System.currentTimeMillis()).as("it holds " + bytes + " bytes in time").isLessThan(deadline);
			Thread.sleep(1);
		}
	}

	/**
	 * A read takes everything the request holds, up to what it asks (the user's, 2026-11-13: read(2)'s
	 * way, the card joining what came, never the program): a body of three socket reads is one read.
	 */
	@Test
	@ResourceLock("InternetCard.policy")
	void aReadTakesEverythingHeldUpToWhatItAsks() throws Exception {
		int size = 3 * DATA_PIECE;
		HttpServer web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			exchange.sendResponseHeaders(200, size);
			exchange.getResponseBody().write(new byte[size]);
			exchange.close();
		});
		web.start();
		InternetCard.policyFrom(() -> new InternetPolicy(true, List.of("127.0.0.1"), List.of()));
		try {
			InternetCard card = new InternetCard();
			card.accepting(true);
			long handle = (Long) card.request(args("http://127.0.0.1:" + web.getAddress().getPort() + "/")).first();
			untilHeld(card, handle, size);
			assertThat(((String) card.read(args(handle, 100L)).first()).length()).as("at most what it asks").isEqualTo(100);
			assertThat(((String) card.read(args(handle)).first()).length()).as("the rest, at once").isEqualTo(size - 100);
			card.close(args(handle));
		} finally {
			InternetCard.policyFrom(() -> InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}

	/**
	 * The response buffer is bytes (the user's, 2026-11-13): a server sending its body in tiny chunks
	 * still fills a request's whole buffer before its download waits, and one read takes it all.
	 */
	@Test
	@ResourceLock("InternetCard.policy")
	void tinyChunksStillFillTheWholeBuffer() throws Exception {
		int buffer = InternetCard.RESPONSE_BUFFER_KIB * 1024;
		HttpServer web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			exchange.sendResponseHeaders(200, 0); // (chunked)
			try (var out = exchange.getResponseBody()) {
				for (int sent = 0; sent < 4 * buffer; sent += TINY_CHUNK) {
					out.write(new byte[TINY_CHUNK]);
					out.flush(); // (a chunk each)
				}
			} catch (java.io.IOException closed) {
				// (the card closed it)
			}
		});
		web.start();
		InternetCard.policyFrom(() -> new InternetPolicy(true, List.of("127.0.0.1"), List.of()));
		InternetCard.responseBuffer(InternetCard.RESPONSE_BUFFER_KIB);
		try {
			InternetCard card = new InternetCard();
			card.accepting(true);
			long handle = (Long) card.request(args("http://127.0.0.1:" + web.getAddress().getPort() + "/")).first();
			untilHeld(card, handle, buffer);
			assertThat(((String) card.read(args(handle)).first()).length()).as("its whole buffer, in one read").isEqualTo(buffer);
			card.close(args(handle));
		} finally {
			InternetCard.policyFrom(() -> InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}

	/** A socket read's size, at most (HttpExchange's), and a tiny chunk a server might send. */
	private static final int DATA_PIECE = 4096;
	private static final int TINY_CHUNK = 10;

	/** Waits till the card's log has an entry, and takes it. */
	@SuppressWarnings("unchecked")
	private static Map<Object, Object> entry(InternetCard card) {
		long deadline = System.currentTimeMillis() + ENDS_WITHIN;
		Object entry;
		while ((entry = card.pull(args()).first()) == null) {
			card.tick(); // (its block's tick: what ended on a request's thread goes into its log)
			assertThat(System.currentTimeMillis()).as("its entry came").isLessThan(deadline);
			Thread.onSpinWait();
		}
		return (Map<Object, Object>) entry;
	}

	/**
	 * A request's log entry is its end (the user's, 2026-11-05): done, its method and URL and how it
	 * went in result; closed by its program first, stopped, its call the arguments as given (its body
	 * cut to its first 16 bytes) and its progress, the body's whole size in sent. Logged once, the
	 * close and its thread's end meeting or not.
	 */
	@Test
	@ResourceLock("InternetCard.policy")
	void aRequestsEntryIsItsEndDoneOrStoppedWithItsCall() throws Exception {
		java.util.concurrent.CountDownLatch hold = new java.util.concurrent.CountDownLatch(1);
		HttpServer web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			byte[] body = "hi".getBytes();
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		web.createContext("/slow", exchange -> {
			exchange.getRequestBody().readAllBytes();
			try {
				hold.await(); // (never answers till the test lets it go)
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		web.start();
		InternetCard.policyFrom(() -> new InternetPolicy(true, List.of("127.0.0.1"), List.of()));
		try {
			String base = "http://127.0.0.1:" + web.getAddress().getPort();
			InternetCard card = new InternetCard();
			card.accepting(true); // (as on a running computer's bus: it takes data)
			long done = (Long) card.request(args(base + "/")).first();
			Map<Object, Object> ended = entry(card);
			assertThat(ended).containsEntry("event", "request").containsEntry("ok", true).containsEntry("method", "GET")
				.containsEntry("url", base + "/").doesNotContainKey("call");
			assertThat(ended.get("result")).isEqualTo(Map.of("status", 200L, "bytes", 2L, "millis", ((Map<?, ?>) ended.get("result")).get("millis")));
			card.close(args(done));

			String body = "0123456789abcdef-and-the-rest";
			long slow = (Long) card.request(args(base + "/slow", "POST", Map.of(), body)).first();
			Thread.sleep(200); // (under way: sent, waiting for its answer)
			card.close(args(slow));
			Map<Object, Object> stopped = entry(card);
			assertThat(stopped).containsEntry("event", "request").containsEntry("ok", false).containsEntry("method", "POST")
				.containsEntry("url", base + "/slow").containsEntry("why", "stopped");
			Map<?, ?> call = (Map<?, ?>) stopped.get("call");
			assertThat(call.get("args")).as("as given, its body's start").isEqualTo(List.of(base + "/slow", "POST", Map.of(), "0123456789abcdef"));
			Map<?, ?> progress = (Map<?, ?>) call.get("progress");
			assertThat(progress).isEqualTo(Map.of("status", 0L, "bytes", 0L, "sent", (long) body.length(), "millis", progress.get("millis")));
			assertThat(card.pull(args()).first()).as("logged once").isNull();
		} finally {
			hold.countDown();
			InternetCard.policyFrom(() -> InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}
}
