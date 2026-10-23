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
 * sent to one card is taken, or the sender hears why not, at once: {@code "busy"} (its ring is full,
 * or no program takes data there now) or {@code "unreachable"} (no card at that address in reach, or
 * it's off). The card never tries again itself: that's the software's choice (mcos's TCP does), as
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
	/** How far a WLAN card reaches, in blocks. */
	public static final int RANGE = 64;
	/** The most data a frame carries, in bytes (OpenComputers' packet size). */
	public static final int MTU = 8192;
	/** What a wired card draws a tick, at rest. */
	public static final int WIRED_DRAW = 2;
	/** What a WLAN card draws a tick, at rest (its radio listening). */
	public static final int WIRELESS_DRAW = 5;
	/** What sending a frame costs a wired card. */
	public static final int WIRED_SEND_COST = PowerUnits.CPU / 10;
	/** What sending a frame costs a WLAN card: a tick of a running CPU. */
	public static final int WIRELESS_SEND_COST = PowerUnits.CPU;
	/** The longest wake message, in bytes. */
	public static final int MAX_WAKE = 256;
	private final InterruptLine line = new InterruptLine();
	private final InterruptLine wake = new InterruptLine();
	private final BusPower power;
	private final EventQueue frames = new EventQueue(line, this::changed, this::isAccepting);
	private volatile Ether.Place place;
	/** The blocks its cables reach (a wired card's), by meters along them. */
	private volatile Map<BlockPos, Integer> reach = Map.of();
	/** What wakes its computer, or null: it doesn't. */
	private String wakeMessage;

	public NetworkCard(CardKind kind) {
		super(kind);
		power = new BusPower(kind == CardKind.WLAN ? WIRELESS_DRAW : WIRED_DRAW);
	}

	public boolean wireless() {
		return kind() == CardKind.WLAN;
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

	/** Why a frame wasn't taken: a ring that's full, or no program taking data. */
	public static final String BUSY = "busy";
	/** Why a frame wasn't taken: no card there, in reach and on. */
	public static final String UNREACHABLE = "unreachable";

	/**
	 * A frame came for it: it waits for the program while it has power and there's room (null:
	 * taken), else {@link #BUSY}; while it has none, it's a wake if it holds the wake message (taken:
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
			return frames.offer(frame) ? null : BUSY;
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

	@Method(doc = "send(address, data): a frame to the card at that address (bytes, at most 8192); true when it took it, else false and why: \"busy\" (its ring is full, or no program takes data there: try again) or \"unreachable\" (no card there in reach, or it's off). It never tries again itself")
	public Result send(Arguments args) {
		String to = args.checkString(0);
		String data = checked(args.checkString(1));
		String refused = Ether.send(this, to.startsWith("id:") ? to.substring("id:".length()) : to, data);
		return refused == null ? Result.ok(true) : Result.ok(false, refused);
	}

	@Method(doc = "broadcast(data): a frame to every card it reaches (bytes, at most 8192), as best it can: how many took it")
	public Result broadcast(Arguments args) {
		return Result.ok((long) Ether.broadcast(this, checked(args.checkString(0))));
	}

	/** A frame's data, if it fits; sending it costs power. */
	private String checked(String data) {
		if (data.length() > MTU) {
			throw new Arguments.ArgumentError("frame too long: " + data.length() + " bytes, at most " + MTU);
		}
		power.charge(wireless() ? WIRELESS_SEND_COST : WIRED_SEND_COST);
		return data;
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
			throw new Arguments.ArgumentError("wake message too long: at most " + MAX_WAKE + " bytes");
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
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		frames.restore(saved);
		wakeMessage = saved.get("wake") instanceof String message ? message : null;
	}

	/** It stopped taking data: its frames go. */
	@Override
	protected void dropData() {
		frames.clear();
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
	}
}
