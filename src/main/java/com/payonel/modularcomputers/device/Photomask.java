package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/**
 * A photomask on the bus (user, 2026-10-12): wired to a running computer, it has power from the
 * bus and prints chips without a battery. It has no methods yet; programs will drive it later.
 */
public final class Photomask implements Device {
	/** What it draws standing by, each tick it has power; each turn of its work costs more ({@link BusPower#charge}). */
	public static final int STANDBY = 2;
	private final BusPower power = new BusPower(STANDBY);

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public DeviceType type() {
		return DeviceType.FABRICATOR;
	}
}
