package com.payonel.modularcomputers.internet;

/**
 * How far internet cards' requests may go (a server's settings: {@code internet.*}, as loaded by
 * {@code ModConfig}): their timeouts, the sizes of what they send and take in, and how many run at
 * once. A request takes them as it starts, so a reload reaches the requests made after it.
 *
 * @param connectMillis how long connecting may take
 * @param readMillis how long a read may wait with nothing coming
 * @param requestNanos how long a request may take in all, time waiting for room counted
 * @param maxHeaderBytes the most a response's status line and headers may take together
 * @param maxUploadBytes the most a request's own body may be
 * @param perCard requests a card has open at once
 * @param perServer requests running at once on the whole server
 */
public record InternetLimits(int connectMillis, int readMillis, long requestNanos, int maxHeaderBytes, int maxUploadBytes, int perCard,
	int perServer) {
	/** The longest a request may be given in all, in nanoseconds (146 years): any longer is for ever, and still fits a long. */
	private static final long LONGEST_NANOS = Long.MAX_VALUE / 2;

	/** Limits from settings in an admin's units: seconds (more than 0) and KiB, as {@code ModConfig} reads them. */
	public static InternetLimits of(double connectSeconds, double readSeconds, double requestSeconds, int maxHeaderKib, int maxUploadKib,
		int perCard, int perServer) {
		return new InternetLimits(millis(connectSeconds), millis(readSeconds), nanos(requestSeconds), bytes(maxHeaderKib), bytes(maxUploadKib), perCard,
			perServer);
	}

	/** Seconds as a request's time in all: at least a nanosecond, at most {@link #LONGEST_NANOS}. */
	private static long nanos(double seconds) {
		double nanos = seconds * 1e9;
		return nanos >= LONGEST_NANOS ? LONGEST_NANOS : Math.max(1, (long) nanos);
	}

	/** Seconds as a socket's timeout: at least a millisecond (0 is no timeout at all to a socket), at most what it takes. */
	private static int millis(double seconds) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(seconds * 1000)));
	}

	private static int bytes(int kib) {
		return (int) Math.min(Integer.MAX_VALUE, (long) kib * 1024);
	}
}
