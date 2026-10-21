package com.payonel.modularcomputers.bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A device's interrupt line, wired to the computers that have it on their bus. Raising it wakes
 * a computer waiting for an interrupt right away, as a real interrupt pulls a CPU out of
 * {@code HLT}, instead of at its next tick. The interrupt itself stays level-triggered: it's
 * pending while the device has data ({@link Device#interruptPending()}); the line only says
 * "look now".
 */
public final class InterruptLine {
	private final Set<Runnable> wired = Collections.newSetFromMap(new IdentityHashMap<>());

	public void connect(Runnable computer) {
		wired.add(computer);
	}

	public void disconnect(Runnable computer) {
		wired.remove(computer);
	}

	/** The device has something for its computers: wake them. */
	public void raise() {
		new ArrayList<>(wired).forEach(Runnable::run);
	}
}
