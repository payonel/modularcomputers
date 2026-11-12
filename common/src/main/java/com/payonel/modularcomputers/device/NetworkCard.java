package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A network card (user design, 2026-10-21; OpenComputers' idea, a real NIC's shape): a wired one
 * sends frames over the cables, a WLAN card through the air ({@link Ether}). Its address is its id
 * (a NIC's MAC, burned in: its item's serial number). A frame goes to one card by its address, or
 * to every card it reaches (a broadcast), and carries bytes, at most {@link #MTU}. Frames that come
 * wait in the card for the program, an event each (its interrupt), as a NIC's receive ring:
 * {@code {from, data, distance, broadcast}}.
 * <p>
 * <b>Nothing is dropped</b> (user, 2026-10-22: a device's handshake, never a silent drop): a frame
 * sent to one card is taken, or the sender hears why not, at once: a limit, {@code {why = "limit",
 * of = "frames", max}} (its ring is full, or no program takes data there now: not busy, the user's,
 * as no news of the sender's device ends it) or the network's {@code unreachable} (no card at that
 * address in reach, or it's off). The card never tries again itself: that's the software's choice (mcos's TCP does), as
 * lossless Ethernet's PAUSE frames tell a sender to hold off and leave the rest to it. A broadcast
 * is best effort: it says how many cards took it. In order, as frames go at once.
 * <p>
 * Ports aren't the card's (OpenComputers' modem opened ports itself): they're UDP's, the kernel's,
 * in the frame's data. The card knows only addresses, as a NIC does.
 * <p>
 * <b>Wake-on-LAN:</b> with a wake message set, the card listens while its computer is off (a real
 * NIC's standby power), and a frame for it holding the message anywhere in its data turns the
 * computer on (PCI's wake signal, PME#): a real NIC's "magic packet" is a fixed pattern (its MAC,
 * sixteen times), OpenComputers' a message the program chose; this is the second, matched as a
 * pattern-matching NIC does. The setting outlasts power off, as a NIC's does.
 */
public final class NetworkCard extends Card {
	/** How far a tier 1 WLAN card reaches at most, in blocks (OpenComputers' tier 1 wireless card's). */
	public static final int TIER1_RANGE = 16;
	/** And a tier 2 (OpenComputers' tier 2's, its default maxWirelessRange). */
	public static final int TIER2_RANGE = 400;
	/** The most data a frame carries, in bytes (OpenComputers' packet size). */
	public static final int MTU = 8192;
	/** What a wired card draws a tick, at rest. */
	public static final int WIRED_DRAW = 2;
	/** What a WLAN card draws a tick, at rest (its radio listening). */
	public static final int WIRELESS_DRAW = 5;
	/** What sending a frame costs a wired card. */
	public static final int WIRED_SEND_COST = PowerUnits.CPU / 10;
	/**
	 * What sending a frame costs a WLAN card for each block its signal reaches (OpenComputers'
	 * wirelessCostPerRange: a farther signal costs more): a tier 1 at its strongest, a tick of a
	 * running CPU.
	 */
	public static final double WIRELESS_SEND_COST_PER_BLOCK = (double) PowerUnits.CPU / TIER1_RANGE;
	/** The longest wake message, in bytes. */
	public static final int MAX_WAKE = 256;
	private final InterruptLine line = new InterruptLine();
	private final InterruptLine wake = new InterruptLine();
	private final BusPower power;
	private final EventQueue frames = new EventQueue(com.payonel.modularcomputers.bus.DeviceType.NETWORK, line, this::changed, this::isAccepting);
	private volatile Ether.Place place;
	/** The blocks its cables reach (a wired card's), by meters along them. */
	private volatile Map<BlockPos, Integer> reach = Map.of();
	/** What wakes its computer, or null: it doesn't. */
	private String wakeMessage;
	/**
	 * Its traffic, its frames in and out (its /dev file's: as tcpdump shows a card's), kept as a
	 * device's events are: a window, its oldest going past {@link EventQueue#window}, the next
	 * entry saying how many as lost (the user's). Not saved, and its news isn't held pending: its
	 * frames' interrupt isn't held up by a log nobody reads.
	 */
	private final EventQueue traffic = new EventQueue(com.payonel.modularcomputers.bus.DeviceType.NETWORK, line, () -> { }, this::isAccepting);
	/** How far a WLAN card's signal reaches now, in blocks: at most its tier's, as set (OpenComputers' strength). */
	private int strength;

	public NetworkCard(CardKind kind) {
		super(kind);
		power = new BusPower(kind == CardKind.NETWORK ? WIRED_DRAW : WIRELESS_DRAW);
		strength = maxRange();
	}

	public boolean wireless() {
		return kind() != CardKind.NETWORK;
	}

	/** How far its signal can reach at most, in blocks (a WLAN card's tier; 0 wired). */
	public int maxRange() {
		return switch (kind()) {
			case TIER1_WLAN -> TIER1_RANGE;
			case TIER2_WLAN -> TIER2_RANGE;
			default -> 0;
		};
	}

	/** How far its signal reaches now (a WLAN card's). */
	public int range() {
		return strength;
	}

	/** Its address: its id, or null before it has one. */
	public String address() {
		return id() == null ? null : id().toString();
	}

	/** Where it is now, said by its block each tick ({@code world}: its level, any key). */
	public void place(Object world, BlockPos pos, long now) {
		place = new Ether.Place(world, pos.immutable(), now);
		Ether.join(this);
	}

	Ether.Place place() {
		return place;
	}

	/** The blocks its cables reach, by meters along them (a wired card's: its block works it out). */
	public void reach(Map<BlockPos, Integer> reach) {
		this.reach = Map.copyOf(reach);
	}

	Map<BlockPos, Integer> reach() {
		return reach;
	}

	/** Why a frame wasn't taken: a ring that's full, or no program taking data (its failure: a limit of frames). */
	public static final String FULL = "limit";
	/** Why a frame wasn't taken: no card there, in reach and on (its failure: the network's, unreachable). */
	public static final String UNREACHABLE = "unreachable";

	/**
	 * A frame came for it: it waits for the program while it has power and there's room (null:
	 * taken), else {@link #FULL}; while it has none, it's a wake if it holds the wake message (taken:
	 * its radio listens for that), else {@link #UNREACHABLE}.
	 */
	String hear(String from, String data, double distance, boolean broadcast) {
		if (power.powered()) {
			Map<String, Object> frame = new LinkedHashMap<>();
			frame.put("from", from);
			frame.put("data", data);
			frame.put("distance", distance);
			if (broadcast) {
				frame.put("broadcast", true);
			}
			String refused = frames.offer(frame) ? null : FULL;
			arrived(from, broadcast, distance, data.length(), refused);
			return refused;
		}
		if (wakeMessage == null) {
			return UNREACHABLE;
		}
		if (data.contains(wakeMessage)) {
			wake.raise();
		}
		return null;
	}

	@Method(doc = "address(): its address (its id), which other cards send to")
	public Result address(Arguments args) {
		return Result.ok(address());
	}

	@Method(doc = "send(address, data): a frame to the card at that address (bytes, at most 8192); true when it took it, else false and why: {why = \"limit\", of = \"frames\", max} (its ring is full, or no program takes data there: try again), or {why = \"network\", reason = \"unreachable\", message = \"no card in reach\"} (no card there in reach, or it's off). It never tries again itself")
	public Result send(Arguments args) {
		String to = args.checkString(0);
		String data = checked(args.checkString(1));
		String refused = Ether.send(this, to.startsWith("id:") ? to.substring("id:".length()) : to, data);
		if (refused == null) {
			return Result.ok(true);
		}
		return Result.fail(failure(refused));
	}

	/** A frame's refusal (FULL, UNREACHABLE) as its failure: send's answer and its log's alike. */
	static com.payonel.modularcomputers.runtime.Failure failure(String refused) {
		return FULL.equals(refused) ? com.payonel.modularcomputers.runtime.Failure.limit("frames", RingDepths.of(com.payonel.modularcomputers.bus.DeviceType.NETWORK)) : com.payonel.modularcomputers.runtime.Failure.network(UNREACHABLE, null);
	}

	@Method(doc = "broadcast(data): a frame to every card it reaches (bytes, at most 8192), as best it can: how many took it")
	public Result broadcast(Arguments args) {
		String data = checked(args.checkString(0));
		int took = Ether.broadcast(this, data);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("event", "broadcast"); // (the method that caused it)
		entry.put("bytes", (long) data.length());
		entry.put("took", (long) took); // (best effort: ok, however many)
		entry.put("ok", true);
		logged(entry);
		return Result.ok((long) took);
	}

	/** What a send's entry says of how far when no card was reached: none to measure (the shape keeps its field). */
	public static final double NO_DISTANCE = -1;
	/** Its traffic log's own news: a frame came (no method of its own caused it). */
	public static final String ARRIVED = "arrived";

	/**
	 * A frame it sent to one card, in its traffic log, named after the method that caused it: {@code
	 * {event = "send", address, distance, bytes, took, ok}}, refused with its failure's fields flat
	 * (send's own answer); {@code distance} {@link #NO_DISTANCE} when no card was reached.
	 */
	void sent(String address, Double distance, int bytes, String refused) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("event", "send");
		entry.put("address", address);
		entry.put("distance", distance != null ? distance : NO_DISTANCE);
		entry.put("bytes", (long) bytes);
		entry.put("took", refused == null ? 1L : 0L);
		outcome(entry, refused);
		logged(entry);
	}

	/** A frame that came, its own news: {@code {event = "arrived", address, broadcast, distance, bytes, ok}}, refused with its failure's fields flat. */
	private void arrived(String from, boolean broadcast, double distance, int bytes, String refused) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("event", ARRIVED);
		entry.put("address", from);
		entry.put("broadcast", broadcast);
		entry.put("distance", distance);
		entry.put("bytes", (long) bytes);
		outcome(entry, refused);
		logged(entry);
	}

	/** How a frame went: ok, or not and why, its failure's own fields. */
	private static void outcome(Map<String, Object> entry, String refused) {
		entry.put("ok", refused == null);
		if (refused != null) {
			com.payonel.modularcomputers.runtime.Failure failed = failure(refused);
			entry.put("why", failed.why());
			entry.putAll(failed.fields());
		}
	}

	/** An entry in its traffic log. */
	private void logged(Map<String, Object> entry) {
		traffic.record(entry); // (its line raised once: not held pending, its frames' interrupt not held up by a log nobody reads)
	}

	@Method(doc = "pull(): its traffic log's oldest frame: a send, {event = \"send\", address, distance (-1: no card reached), bytes, took (1 or 0), ok, and why and its fields when refused: limit or network}; a broadcast, {event = \"broadcast\", bytes, took (how many cards), ok}; or one that came, {event = \"arrived\", address, broadcast, distance, bytes, ok, and why and its fields when refused}; lost (how many went before it, the log full) when some did; or nil")
	public Result pull(Arguments args) {
		return Result.ok(traffic.pull());
	}

	/** A frame's data, if it fits; sending it costs power. */
	private String checked(String data) {
		if (data.length() > MTU) {
			throw new Arguments.ArgumentError(com.payonel.modularcomputers.runtime.Failure.limit("data", MTU));
		}
		power.charge(wireless() ? (int) Math.ceil(strength * WIRELESS_SEND_COST_PER_BLOCK) : WIRED_SEND_COST);
		return data;
	}

	@Method(doc = "getStrength(): how far its signal reaches, in blocks (a WLAN card's; OpenComputers')")
	public Result getStrength(Arguments args) {
		return wireless() ? Result.ok((long) strength) : Result.fail(com.payonel.modularcomputers.runtime.Failure.denied("wired", ""));
	}

	@Method(doc = "setStrength(blocks): how far its signal reaches, up to its tier's (16, 400); a send costs more the farther it reaches; returns what it's set to")
	public Result setStrength(Arguments args) {
		if (!wireless()) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.denied("wired", ""));
		}
		strength = (int) Math.clamp(args.checkInteger(0), 0, maxRange());
		changed(); // (it's kept with the card's state)
		return Result.ok((long) strength);
	}

	@Method(doc = "receive(): the oldest frame that came, as {from (its sender's address), data, distance (meters), broadcast (true when it was)}, or nil")
	public Result receive(Arguments args) {
		return Result.ok(frames.pull());
	}

	@Method(doc = "getWake(): the message that turns its computer on while it's off (in a frame's data, anywhere), or nil: none")
	public Result getWake(Arguments args) {
		return Result.ok(wakeMessage);
	}

	@Method(doc = "setWake(message): sets the message that turns its computer on (nil or empty: none, it doesn't); returns the old one")
	public Result setWake(Arguments args) {
		String message = args.optString(0, null);
		if (message != null && message.length() > MAX_WAKE) {
			throw new Arguments.ArgumentError(com.payonel.modularcomputers.runtime.Failure.limit("wake_message", MAX_WAKE));
		}
		String old = wakeMessage;
		wakeMessage = message == null || message.isEmpty() ? null : message;
		changed();
		return Result.ok(old);
	}

	@Override
	public Map<String, Object> info() {
		Map<String, Object> info = new LinkedHashMap<>(super.info());
		info.put("wireless", wireless());
		return info;
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public String streamMethod() {
		return "receive";
	}

	/** Its frames waiting, and its wake message. */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		frames.save(saved);
		if (wakeMessage != null) {
			saved.put("wake", wakeMessage);
		}
		if (wireless() && strength != maxRange()) {
			saved.put("strength", (long) strength);
		}
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		frames.restore(saved);
		wakeMessage = saved.get("wake") instanceof String message ? message : null;
		strength = saved.get("strength") instanceof Number set ? (int) Math.clamp(set.longValue(), 0, maxRange()) : maxRange();
	}

	/** It stopped taking data: its frames go. */
	@Override
	protected void dropData() {
		frames.clear();
		traffic.clear();
	}

	@Override
	public boolean interruptPending() {
		return frames.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	public InterruptLine wakeLine() {
		return wake;
	}

	@Override
	public void powerOff() {
		frames.clear();
		traffic.clear();
	}
}
