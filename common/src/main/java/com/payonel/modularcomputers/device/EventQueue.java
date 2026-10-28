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
public final class EventQueue {
	public static final int CAPACITY = 32;
	private final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>();
	private final InterruptLine line;
	/** Its card, told when its contents change. */
	private final Runnable changed;
	/** Whether its card takes data now: events that come while it doesn't are dropped. */
	private final java.util.function.BooleanSupplier accepting;
	private long lost;

	public EventQueue(InterruptLine line, Runnable changed, java.util.function.BooleanSupplier accepting) {
		this.line = line;
		this.changed = changed;
		this.accepting = accepting;
	}

	public void add(Map<String, Object> event) {
		if (!accepting.getAsBoolean()) {
			return; // its card takes no data now
		}
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

	/**
	 * Puts an event in only if there's room and its card takes data now: whether it did (a device
	 * that refuses rather than drops: its sender hears, and tries again).
	 */
	boolean offer(Map<String, Object> event) {
		if (!accepting.getAsBoolean() || events.size() >= CAPACITY) {
			return false;
		}
		events.addLast(event);
		changed.run();
		line.raise();
		return true;
	}

	/**
	 * A buffered device's command ended (.agents/BUFFERED.md): {@code {event = method, ok = true[,
	 * result]}}, or {@code {event = method, ok = false, why}} when it failed (or was stopped). News.
	 */
	public void ended(String method, boolean ok, String why, Object result) {
		Map<String, Object> event = new java.util.LinkedHashMap<>();
		event.put("event", method);
		event.put("ok", ok);
		if (!ok) {
			event.put("why", why);
		}
		if (result != null) {
			event.put("result", com.payonel.modularcomputers.runtime.Values.normalize(result));
		}
		add(event);
	}

	public Map<String, Object> pull() {
		Map<String, Object> event = events.pollFirst();
		if (event != null) {
			changed.run();
		}
		return event;
	}


	public boolean pending() {
		return !events.isEmpty();
	}

	public void clear() {
		if (events.isEmpty() && lost == 0) {
			return; // nothing changed
		}
		events.clear();
		lost = 0;
		changed.run();
	}

	/** What it holds, to save: the events waiting, and how many were lost since the last kept. */
	public void save(Map<String, Object> saved) {
		saved.put("events", java.util.List.copyOf(events));
		saved.put("lost", lost);
	}

	/** Takes back what {@link #save} kept. */
	@SuppressWarnings("unchecked")
	public void restore(Map<String, Object> saved) {
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
