package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.InterruptLine;
import java.util.ArrayDeque;
import java.util.Map;

/**
 * A sensor card's events, waiting for its program: its interrupt is pending while any wait, and
 * raising its line wakes its computer. Bounded: when it's full the oldest goes, and the next event
 * says how many were lost ({@code lost}), so a program can tell (this is the card's own choice of
 * what to drop: a device keeps its data until there's room for it, until it can't).
 */
final class EventQueue {
	static final int CAPACITY = 32;
	private final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>();
	private final InterruptLine line;
	private long lost;

	EventQueue(InterruptLine line) {
		this.line = line;
	}

	void add(Map<String, Object> event) {
		if (events.size() >= CAPACITY) {
			events.removeFirst();
			lost++;
		}
		if (lost > 0) {
			var counted = new java.util.LinkedHashMap<>(event);
			counted.put("lost", lost);
			event = counted;
			lost = 0;
		}
		events.addLast(event);
		line.raise();
	}

	Map<String, Object> pull() {
		return events.pollFirst();
	}

	boolean pending() {
		return !events.isEmpty();
	}

	void clear() {
		events.clear();
		lost = 0;
	}
}
