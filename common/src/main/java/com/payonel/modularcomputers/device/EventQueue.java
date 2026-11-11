package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.InterruptLine;
import java.util.ArrayDeque;
import java.util.Map;

/**
 * A device's events, waiting for its program: its interrupt is pending while any wait, and raising
 * its line wakes its computer. Two kinds, kept in order (the user's, 2026-11-11: command ends are
 * lossless): news (a sensor's sighting, a change, a notice) is a window, {@link #window} at most,
 * its oldest going when it's full and the next event saying how many were lost ({@code lost}), as
 * news can be read again from the device's state; a command's end (an event with {@code ok}, built
 * by the end builders here) is never dropped, {@link #ends} at most, its device refusing its next
 * command while that many wait unread ({@link #endsFull}: the handshake). A log ({@link #record})
 * is a window of all it holds.
 */
public final class EventQueue {
	/** A device's news window by default: the newest it keeps (the user's, 2026-11-11: 1024; a setting, {@link #window}). */
	public static final int WINDOW = 1024;
	/** The most command ends a device holds unread by default before it refuses its next command (the user's: 1024; a setting, {@link #ends}). */
	public static final int ENDS = 1024;
	/** A network card's frame ring: frames waiting for its program, the next refused when it's full (a data ring's handshake, not a window). */
	public static final int FRAMES = 32;
	/** The settings, as last loaded ({@code device_event_window}, {@code device_command_ends}). */
	private static volatile int window = WINDOW;
	private static volatile int endsLimit = ENDS;
	/** An event waiting, and whether it's a command's end (never dropped). */
	private record Waiting(Map<String, Object> event, boolean end) {
	}
	private final ArrayDeque<Waiting> events = new ArrayDeque<>();
	/** How many of what waits are ends. */
	private int ends;
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

	/**
	 * An event without a why (a sensor's, a frame): a why is only ever built by its own method here
	 * (the user's: one shape each, no deviating), so an event that has one is refused.
	 */
	public void add(Map<String, Object> event) {
		if (event.containsKey("why")) {
			throw new IllegalArgumentException("a why is built by its own method, not added: " + event.get("why"));
		}
		put(event);
	}

	private void put(Map<String, Object> event) {
		if (!accepting.getAsBoolean()) {
			return; // its card takes no data now
		}
		restored = false;
		boolean end = event.containsKey("ok"); // (a command's end: every end builder here gives one; news and notices have none)
		if (!end && events.size() - ends >= window) {
			for (var each = events.iterator(); each.hasNext(); ) { // (the oldest news goes: an end never does)
				if (!each.next().end()) {
					each.remove();
					break;
				}
			}
			lost++;
		}
		keep(event, end);
	}

	/** Puts an event in, saying how many went before it (lost). */
	private void keep(Map<String, Object> event, boolean end) {
		if (lost > 0) {
			var counted = new java.util.LinkedHashMap<>(event);
			counted.put("lost", lost);
			event = counted;
			lost = 0;
		}
		events.addLast(new Waiting(event, end));
		if (end) {
			ends++;
		}
		changed.run();
		line.raise();
	}

	/** The most ends it holds unread before its device refuses a command (the setting, as it reads it now). */
	public int endsMax() {
		return endsLimit;
	}

	/** Whether its command ends fill it: its device takes no more commands till its program reads some (lossless, the handshake). */
	public boolean endsFull() {
		return ends >= endsLimit;
	}

	/** The news window now (the setting). */
	public static int window() {
		return window;
	}

	/** The most unread ends now (the setting). */
	public static int ends() {
		return endsLimit;
	}

	/** The server's settings (ModConfig's): each at least 1. */
	public static void configure(int newsWindow, int commandEnds) {
		window = Math.max(1, newsWindow);
		endsLimit = Math.max(1, commandEnds);
	}

	/** A log's entry: a window of all it holds, its oldest going past {@link #window}. */
	private void window(Map<String, Object> entry) {
		if (!accepting.getAsBoolean()) {
			return;
		}
		restored = false;
		if (events.size() >= window) {
			// A burst over its window between a program's pulls loses its oldest here however fast the
			// program drains: it takes only as fast as its board calls run, a share of each tick. For a
			// log (the network card's traffic, the internet card's requests) that's a lost line, not
			// lost data (frames refuse instead: offer), so it's accepted for now. The user's idea, if it
			// matters later: no bound within a tick, then at the server tick, once the program has had
			// its chance to take them, drop the oldest back under the limit (lost counted as here).
			events.removeFirst();
			lost++;
		}
		keep(entry, false);
	}

	/**
	 * A log's entry (a device's record of what it did: the network card's traffic, the internet card's
	 * requests), kept as any event is, its oldest going past {@link #window} and the next saying how
	 * many as {@code lost}: built by its device from the builders (a refusal's fields a {@link
	 * com.payonel.modularcomputers.runtime.Failure}'s, an end's {@link #failed}), so its why isn't
	 * refused here as {@link #add}'s is.
	 */
	void record(Map<String, Object> entry) {
		window(entry);
	}

	/**
	 * Puts an event in only if there's room and its card takes data now: whether it did (a device
	 * that refuses rather than drops: its sender hears, and tries again).
	 */
	boolean offer(Map<String, Object> event) {
		if (!accepting.getAsBoolean() || events.size() >= FRAMES) {
			return false;
		}
		events.addLast(new Waiting(event, false));
		changed.run();
		line.raise();
		return true;
	}

	/**
	 * A buffered device's command done ({@link Buffered}): {@code {event = method, ok = true[,
	 * result]}}. A command that didn't do it ends by its why's own method (stopped, blocked...), so
	 * no why is ever built another way (the user's). News.
	 */
	public void ended(String method, Object result) {
		Map<String, Object> event = new java.util.LinkedHashMap<>();
		event.put("event", method);
		event.put("ok", true);
		if (result != null) {
			event.put("result", com.payonel.modularcomputers.runtime.Values.normalize(result));
		}
		put(event);
	}

	/**
	 * A command refused as it was given again (taken while its robot was held, given once let go):
	 * its end says why in its why's shape, as the call's answer would have (the user's: an answer at
	 * once and the event it becomes are one shape). Only a refusal with an event's shape can be.
	 */
	public void refused(String event, com.payonel.modularcomputers.runtime.Result refusal, Map<String, Object> call) {
		if (Buffered.FAULT.equals(refusal.error())) {
			fault(event, refusal.failure(), call);
			return;
		}
		throw new IllegalArgumentException("no event shape for the refusal " + refusal.error());
	}

	/**
	 * Something wrong with the machine itself (the user's: one shape): {@code {event, ok = false, why =
	 * "fault", condition, part, message, call}}, the fault's own fields ({@link
	 * com.payonel.modularcomputers.runtime.Failure#missing}...).
	 */
	public void fault(String event, com.payonel.modularcomputers.runtime.Failure fault, Map<String, Object> call) {
		Map<String, Object> failed = withCall(event, faultOf(fault), call);
		failed.remove("call");
		failed.putAll(fault.fields());
		failed.put("call", call);
		put(failed);
	}

	/** A fault no program's command met (a field letting go, its power gone), a notice: {@code {event = "notice", why = "fault", condition, part, message}}. */
	public void faultNotice(com.payonel.modularcomputers.runtime.Failure fault) {
		Map<String, Object> notice = notice(faultOf(fault));
		notice.putAll(fault.fields());
		put(notice);
	}

	private static String faultOf(com.payonel.modularcomputers.runtime.Failure fault) {
		if (!Buffered.FAULT.equals(fault.why())) {
			throw new IllegalArgumentException("not a fault: " + fault.why());
		}
		return fault.why();
	}

	/** A robot shoved off where its last command left it, a notice: {@code {event = "notice", why = "shove", drift}}. */
	public void shove(Map<String, Object> drift) {
		Map<String, Object> notice = notice(Buffered.SHOVE);
		notice.put("drift", drift);
		put(notice);
	}

	/** A robot's fall, as it begins and as it lands, a notice: {@code {event = "notice", why = "fall", drift, below}}. */
	public void fall(Map<String, Object> drift, String below) {
		Map<String, Object> notice = notice(Buffered.FALL);
		notice.put("drift", drift);
		notice.put("below", below);
		put(notice);
	}

	/** A field taking hold of a robot ({@code id} the field's own) or letting go ({@code ""}), a notice: {@code {event = "notice", why = "held", id}}. */
	public void heldNotice(String id) {
		Map<String, Object> notice = notice(Buffered.HELD);
		notice.put("id", id == null ? "" : id);
		put(notice);
	}

	private static Map<String, Object> notice(String why) {
		Map<String, Object> notice = new java.util.LinkedHashMap<>();
		notice.put("event", "notice");
		notice.put("why", why);
		return notice;
	}

	/**
	 * Something ended because a program called its device's stop (the user's: one shape, wherever
	 * it's said, always a method's end): {@code {event = method, ok = false, why = "stopped", call}}.
	 */
	public void stopped(String event, Map<String, Object> call) {
		put(withCall(event, Buffered.STOPPED, call));
	}

	/**
	 * What its input makes nothing (the user's: one shape, a craft's grid empty or matching no recipe):
	 * {@code {event, ok = false, why = "missing_inputs", name, call}}, {@code name} what it was making (its
	 * last craft's, "" when none was made).
	 */
	public void missingInputs(String event, String name, Map<String, Object> call) {
		put(named(withCall(event, Buffered.MISSING_INPUTS, call), name));
	}

	/**
	 * No room for its output (the user's: one shape, a craft's storage full): {@code {event, ok =
	 * false, why = "no_space", name, call}}, {@code name} what it would have made.
	 */
	public void noSpace(String event, String name, Map<String, Object> call) {
		put(named(withCall(event, Buffered.NO_SPACE, call), name));
	}

	/**
	 * Blocked, no program's command (a field let go of what it held, out of its reach), a notice:
	 * {@code {event = "notice", why = "blocked", name, face}}, as {@link #blocked}'s.
	 */
	public void blockedNotice(String block, String face) {
		Map<String, Object> notice = notice(Buffered.BLOCKED);
		notice.put("name", block == null ? "" : block);
		notice.put("face", face);
		put(notice);
	}

	/** An end with a thing's name ("" for none, never nil), before its call. */
	private static Map<String, Object> named(Map<String, Object> ended, String name) {
		Object call = ended.remove("call");
		ended.put("name", name == null ? "" : name);
		ended.put("call", call);
		return ended;
	}

	/**
	 * An end kept outside a queue (a record of its own: the internet card's access log), built here as
	 * every end is: done, {@code {event, ok = true, result}}; failed, {@code {event, ok = false, why,
	 * ...its fields, call}}, the failure's own shape; stopped, {@code {event, ok = false, why =
	 * "stopped", call}}.
	 */
	public static Map<String, Object> done(String event, Object result) {
		Map<String, Object> ended = new java.util.LinkedHashMap<>();
		ended.put("event", event);
		ended.put("ok", true);
		ended.put("result", com.payonel.modularcomputers.runtime.Values.normalize(result));
		return ended;
	}

	public static Map<String, Object> failed(String event, com.payonel.modularcomputers.runtime.Failure failure, Map<String, Object> call) {
		Map<String, Object> ended = withCall(event, failure.why(), call);
		ended.remove("call");
		ended.putAll(failure.fields());
		ended.put("call", call);
		return ended;
	}

	public static Map<String, Object> stoppedEnd(String event, Map<String, Object> call) {
		return withCall(event, Buffered.STOPPED, call);
	}

	/** A command's end that says only its why and its call. */
	private static Map<String, Object> withCall(String event, String why, Map<String, Object> call) {
		Map<String, Object> ended = new java.util.LinkedHashMap<>();
		ended.put("event", event);
		ended.put("ok", false);
		ended.put("why", why);
		ended.put("call", call);
		return ended;
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
		put(blocked);
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
		put(interrupted);
	}

	/**
	 * What a command was acting on is gone (the user's: one shape): {@code {event, ok = false, why =
	 * "lost", name, id, at, call}}: its name (registry id), its own id ("" for a block), where it last
	 * was ({forward, right, up}, the device's frame).
	 */
	public void lost(String event, String name, String id, Map<String, Object> at, Map<String, Object> call) {
		put(thing(event, Buffered.LOST, name, id, at, call));
	}

	/**
	 * The game won't let what a command acted on be broken (the user's: one shape, the same fields as
	 * {@link #lost}'s): {@code {event, ok = false, why = "unbreakable", name, id, at, call}}.
	 */
	public void unbreakable(String event, String name, String id, Map<String, Object> at, Map<String, Object> call) {
		put(thing(event, Buffered.UNBREAKABLE, name, id, at, call));
	}

	/** A command's end over a thing: its why, what (its name, its id) and where, and the call. */
	private static Map<String, Object> thing(String event, String why, String name, String id, Map<String, Object> at, Map<String, Object> call) {
		Map<String, Object> thing = new java.util.LinkedHashMap<>();
		thing.put("event", event);
		thing.put("ok", false);
		thing.put("why", why);
		thing.put("name", name == null ? "" : name);
		thing.put("id", id == null ? "" : id);
		thing.put("at", at);
		thing.put("call", call);
		return thing;
	}

	/** Its own loss, no program's command (a field's: what it held is gone), a notice: {@code {event = "notice", why = "lost", name, id, at}}. */
	public void lostNotice(String name, String id, Map<String, Object> at) {
		Map<String, Object> lost = new java.util.LinkedHashMap<>();
		lost.put("event", "notice");
		lost.put("why", Buffered.LOST);
		lost.put("name", name == null ? "" : name);
		lost.put("id", id == null ? "" : id);
		lost.put("at", at);
		put(lost);
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
		put(drifted);
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
		Waiting first = events.pollFirst();
		if (first == null) {
			return null;
		}
		if (first.end()) {
			ends--;
		}
		changed.run();
		return first.event();
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
		ends = 0;
		lost = 0;
		changed.run();
		return true;
	}

	/** What it holds, to save: the events waiting, and how many were lost since the last kept. */
	public void save(Map<String, Object> saved) {
		saved.put("events", events.stream().map(Waiting::event).toList());
		saved.put("ends", events.stream().map(Waiting::end).toList()); // (which are ends: never dropped, at a load too)
		saved.put("lost", lost);
	}

	/** Takes back what {@link #save} kept. */
	@SuppressWarnings("unchecked")
	public void restore(Map<String, Object> saved) {
		events.clear();
		ends = 0;
		java.util.List<?> kinds = saved.get("ends") instanceof java.util.List<?> list ? list : java.util.List.of();
		if (saved.get("events") instanceof java.util.List<?> kept) {
			for (int i = 0; i < kept.size(); i++) {
				boolean end = i < kinds.size() && Boolean.TRUE.equals(kinds.get(i));
				if (kept.get(i) instanceof Map<?, ?> map && (end ? ends < endsLimit : events.size() - ends < window)) {
					events.addLast(new Waiting((Map<String, Object>) map, end));
					if (end) {
						ends++;
					}
				}
			}
		}
		lost = saved.get("lost") instanceof Long n ? n : 0;
		restored = true;
	}
}
