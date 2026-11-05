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
			long handle = (Long) card.request(args("http://127.0.0.1:" + web.getAddress().getPort() + "/")).first();
			long deadline = System.currentTimeMillis() + ENDS_WITHIN;
			Object piece;
			do {
				piece = card.read(args(handle)).first(); // (as curl reads its body: an ask about its own request)
				assertThat(System.currentTimeMillis()).as("it ended in time").isLessThan(deadline);
			} while (!"".equals(piece));
			while (!card.interruptPending()) { // (its log entry comes as it ends)
				assertThat(System.currentTimeMillis()).as("its log entry came").isLessThan(deadline);
				Thread.onSpinWait();
			}
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
}
