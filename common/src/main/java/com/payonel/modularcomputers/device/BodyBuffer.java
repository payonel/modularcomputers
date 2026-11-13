package com.payonel.modularcomputers.device;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * A request's body as it waits to be read (the user's, 2026-11-13: a socket's receive buffer, counted
 * in bytes, read as read(2) reads): its download puts what came, as much as there's room for, and
 * waits for room for the rest (the handshake: nothing is dropped); a read takes everything held, up
 * to what it asks. Its bytes are chars of a Latin-1 string, as the card's answers are.
 */
final class BodyBuffer {
	private final int capacity;
	private final StringBuilder held = new StringBuilder();

	BodyBuffer(int capacity) {
		this.capacity = Math.max(1, capacity);
	}

	/**
	 * The download's: puts {@code data}, waiting for room for what doesn't fit, looking every
	 * {@code waitMillis} whether it's still {@code open}. {@code heard} hears each part as it goes in
	 * (its size). False when it was closed first: the rest wasn't put.
	 */
	synchronized boolean put(String data, BooleanSupplier open, long waitMillis, IntConsumer heard) throws InterruptedException {
		int from = 0;
		while (from < data.length()) {
			if (!open.getAsBoolean()) {
				return false;
			}
			int room = capacity - held.length();
			if (room <= 0) {
				wait(waitMillis);
				continue;
			}
			int to = Math.min(data.length(), from + room);
			held.append(data, from, to);
			heard.accept(to - from);
			from = to;
		}
		return true;
	}

	/** The reader's: everything held, at most {@code most} bytes; null when nothing is. */
	synchronized String take(long most) {
		if (held.isEmpty()) {
			return null;
		}
		int cut = (int) Math.min(most, held.length());
		String taken = held.substring(0, cut);
		held.delete(0, cut);
		notifyAll(); // room for the download
		return taken;
	}

	/** How many bytes it holds (tests). */
	synchronized int size() {
		return held.length();
	}
}
