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
	/**
	 * What it holds came with a load, its program's (going on): the first reset after it (the card
	 * joining its computer's bus, a hub's chunk loading after the computer's) keeps it, as a save
	 * isn't a power cut; any reset after that clears. Its program taking or adding events ends it.
	 */
	private boolean restored;

	public EventQueue(InterruptLine line, Runnable changed, java.util.function.BooleanSupplier accepting) {
		this.line = line;
		this.changed = changed;
		this.accepting = accepting;
	}

	public void add(Map<String, Object> event) {
		if (!accepting.getAsBoolean()) {
			return; // its card takes no data now
		}
		restored = false;
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
	 * A buffered device's command ended ({@link Buffered}): {@code {event = method, ok = true[,
	 * result]}}, or {@code {event = method, ok = false, why}} when it failed (or was stopped). News.
	 */
	public void ended(String method, boolean ok, String why, Object result) {
		if (Buffered.STOPPED.equals(why) || Buffered.BLOCKED.equals(why) || Buffered.DRIFTED.equals(why) || Buffered.INTERRUPTED.equals(why) || Buffered.LOST.equals(why)) {
			// (one shape each, the user's: only their own methods build them)
			throw new IllegalArgumentException(why + " is built by its own method, not ended");
		}
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

	/**
	 * Something ended because a program called its device's stop (the user's: one shape, wherever
	 * it's said, always a method's end): {@code {event = method, ok = false, why = "stopped", call}}.
	 */
	public void stopped(String event, Map<String, Object> call) {
		Map<String, Object> stopped = new java.util.LinkedHashMap<>();
		stopped.put("event", event);
		stopped.put("ok", false);
		stopped.put("why", Buffered.STOPPED);
		stopped.put("call", call);
		add(stopped);
	}

	/**
	 * A move a block stopped (the user's: one shape, wherever it's said): {@code {event = event, ok =
	 * false, why = "blocked", name, face}}, {@code name} the block's (its registry id; {@code ""} when no one
	 * block can be named: null given), {@code face} the side of what moved that met it, in the
	 * device's own terms; and its call.
	 */
	public void blocked(String event, String block, String face, Map<String, Object> call) {
		Map<String, Object> blocked = new java.util.LinkedHashMap<>();
		blocked.put("event", event);
		blocked.put("ok", false);
		blocked.put("why", Buffered.BLOCKED);
		blocked.put("name", block == null ? "" : block); // (a thing's name: its registry id, the user's)
		blocked.put("face", face);
		blocked.put("call", call);
		add(blocked);
	}

	/**
	 * A command under way at a save that couldn't go on after the load (the user's: one shape, a
	 * fallback: a command goes on after a load when it can): {@code {event = method, ok = false, why =
	 * "interrupted", call}}.
	 */
	public void interrupted(String event, Map<String, Object> call) {
		Map<String, Object> interrupted = new java.util.LinkedHashMap<>();
		interrupted.put("event", event);
		interrupted.put("ok", false);
		interrupted.put("why", Buffered.INTERRUPTED);
		interrupted.put("call", call);
		add(interrupted);
	}

	/**
	 * What a command was acting on is gone (the user's: one shape): {@code {event, ok = false, why =
	 * "lost", name, id, at, call}}: its name (registry id), its own id ("" for a block), where it last
	 * was ({forward, right, up}, the device's frame).
	 */
	public void lost(String event, String name, String id, Map<String, Object> at, Map<String, Object> call) {
		Map<String, Object> lost = new java.util.LinkedHashMap<>();
		lost.put("event", event);
		lost.put("ok", false);
		lost.put("why", Buffered.LOST);
		lost.put("name", name == null ? "" : name);
		lost.put("id", id == null ? "" : id);
		lost.put("at", at);
		lost.put("call", call);
		add(lost);
	}

	/** Its own loss, no program's command (a field's: what it held is gone), a notice: {@code {event = "notice", why = "lost", name, id, at}}. */
	public void lostNotice(String name, String id, Map<String, Object> at) {
		Map<String, Object> lost = new java.util.LinkedHashMap<>();
		lost.put("event", "notice");
		lost.put("why", Buffered.LOST);
		lost.put("name", name == null ? "" : name);
		lost.put("id", id == null ? "" : id);
		lost.put("at", at);
		add(lost);
	}

	/**
	 * A positional command over time given off where its last command left it (the user's: one
	 * shape): {@code {event, ok = false, why = "drifted", drift = {forward, right, up}, call}}.
	 */
	public void drifted(String event, Map<?, ?> drift, Map<String, Object> call) {
		Map<String, Object> drifted = new java.util.LinkedHashMap<>();
		drifted.put("event", event);
		drifted.put("ok", false);
		drifted.put("why", Buffered.DRIFTED);
		drifted.put("drift", drift);
		drifted.put("call", call);
		add(drifted);
	}

	/**
	 * A cut-short command's call (the user's): {@code {args, progress}}, its arguments as the program
	 * gave them (a list) and how far it got, in their terms.
	 */
	/** A call's arguments as kept: as given, its trailing nils dropped (a Lua call's count). */
	public static java.util.List<Object> given(java.util.List<Object> args) {
		int n = args.size();
		while (n > 0 && args.get(n - 1) == null) {
			n--;
		}
		return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(args.subList(0, n)));
	}

	public static Map<String, Object> call(java.util.List<Object> args, Object progress) {
		Map<String, Object> call = new java.util.LinkedHashMap<>();
		call.put("args", args);
		call.put("progress", progress);
		return call;
	}

	public Map<String, Object> pull() {
		restored = false;
		Map<String, Object> event = events.pollFirst();
		if (event != null) {
			changed.run();
		}
		return event;
	}


	public boolean pending() {
		return !events.isEmpty();
	}

	/**
	 * Its card was reset (its program's gone, or it joined a bus): its events go, and true; but the
	 * first reset after a load keeps them (they're the saved program's), false, so its card keeps
	 * what it restored too (a command under way's end).
	 */
	public boolean clear() {
		if (restored) {
			restored = false;
			return false;
		}
		if (events.isEmpty() && lost == 0) {
			return true; // nothing changed
		}
		events.clear();
		lost = 0;
		changed.run();
		return true;
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
		restored = true;
	}
}
