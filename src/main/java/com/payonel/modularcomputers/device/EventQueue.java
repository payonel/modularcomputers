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
	/** Its card, told when its contents change. */
	private final Runnable changed;
	private long lost;

	EventQueue(InterruptLine line, Runnable changed) {
		this.line = line;
		this.changed = changed;
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
		changed.run();
		line.raise();
	}

	Map<String, Object> pull() {
		Map<String, Object> event = events.pollFirst();
		if (event != null) {
			changed.run();
		}
		return event;
	}


	boolean pending() {
		return !events.isEmpty();
	}

	void clear() {
		events.clear();
		lost = 0;
		changed.run();
	}

	/** What it holds, to save: the events waiting, and how many were lost since the last kept. */
	void save(Map<String, Object> saved) {
		saved.put("events", java.util.List.copyOf(events));
		saved.put("lost", lost);
	}

	/** Takes back what {@link #save} kept. */
	@SuppressWarnings("unchecked")
	void restore(Map<String, Object> saved) {
		events.clear();
		if (saved.get("events") instanceof java.util.List<?> kept) {
			for (Object event : kept) {
				if (event instanceof Map<?, ?> map && events.size() < CAPACITY) {
					events.addLast((Map<String, Object>) map);
				}
			}
		}
		lost = saved.get("lost") instanceof Long n ? n : 0;
	}
}
