package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A charging pad on the bus (user design, 2026-10-24): it charges a robot standing on it, from the
 * bus's power while a running computer has it on its bus, else from its own buffer (its battery
 * fills it). It reports a robot coming onto it and leaving (its interrupt): {@code docked} (with the
 * robot's board id) and {@code undocked}. Its pad ({@link Pad}: its block) says what's on it.
 */
public final class ChargingPad implements Device {
	/** What it says about itself, now: from its block. */
	public interface Pad {
		/** The board id of the robot on it, or null: none. */
		String robot();

		/** Whether it's charging now (a robot on it, with power to give). */
		boolean charging();

		/** Its buffer's level and capacity, in power units. */
		int buffer();

		int capacity();
	}

	private final InterruptLine line = new InterruptLine();
	private final BusPower power = new BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed, () -> this.accepting);
	private final Pad pad;
	private boolean accepting;
	private Runnable onChange = () -> {
	};

	public ChargingPad(Pad pad) {
		this.pad = pad;
	}

	@Override
	public DeviceType type() {
		return DeviceType.CHARGER;
	}

	@Method(doc = "get(): {charging, robot (its board's id, or nil), buffer, capacity (power units)}")
	public Result get(Arguments args) {
		Map<String, Object> now = new LinkedHashMap<>();
		now.put("charging", pad.charging());
		if (pad.robot() != null) {
			now.put("robot", pad.robot());
		}
		now.put("buffer", (long) pad.buffer());
		now.put("capacity", (long) pad.capacity());
		return Result.ok(now);
	}

	@Method(doc = "pull(): its oldest event, {event = \"docked\", robot} or {event = \"undocked\", robot}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** A robot came onto it ({@code robot}: its board's id). */
	public void docked(String robot) {
		event("docked", robot);
	}

	/** The robot left it. */
	public void undocked(String robot) {
		event("undocked", robot);
	}

	private void event(String name, String robot) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", name);
		event.put("robot", robot);
		events.add(event);
	}

	/** Pays {@code units} from the bus's power, while a running computer supplies it: whether it did. */
	public boolean payFromBus(int units) {
		if (!power.powered()) {
			return false;
		}
		power.charge(units);
		return true;
	}

	private void changed() {
		onChange.run();
	}

	@Override
	public void onChange(Runnable changed) {
		this.onChange = changed;
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting) {
			events.clear();
		}
	}

	@Override
	public void powerOff() {
		accepting(false);
	}

	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
	}
}
