package com.payonel.modularcomputers.runtime;

/**
 * Memory a device holds for a program outside its VM (a file handle's buffered writes), charged to
 * the program's memory (user, 2026-10-13: not byte-exact, but every byte a program can make grow
 * is accounted for, so nothing it does grows without a cost). Given to device methods with their
 * arguments ({@link Arguments#account}).
 */
public interface MemoryAccount {
	/** No limit: a program with no memory to count (a test's Java program). */
	MemoryAccount UNLIMITED = new MemoryAccount() {
		@Override
		public boolean charge(long bytes) {
			return true;
		}

		@Override
		public void force(long bytes) {
		}

		@Override
		public void release(long bytes) {
		}
	};

	/** Takes {@code bytes} more, if there's room: else false, and nothing's taken. */
	boolean charge(long bytes);

	/** Takes {@code bytes} more whether or not there's room (what a restored state held already). */
	void force(long bytes);

	/** Gives back {@code bytes} taken. */
	void release(long bytes);
}
