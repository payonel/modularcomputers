package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;

/** A storage device of a tier: its size and its power draw. It holds no data yet. Bus-powered. */
public final class Disk implements Device {
	private final DiskTier tier;
	private final BusPower power;
	private String label;

	public Disk(DiskTier tier) {
		this.tier = tier;
		this.power = new BusPower(tier.draw());
	}

	public DiskTier tier() {
		return tier;
	}

	@Method(doc = "getCapacity(): how many bytes it holds")
	public Result getCapacity(Arguments args) {
		return Result.ok(tier.bytes());
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}

	@Override
	public java.util.Map<String, Object> info() {
		return java.util.Map.of("tier", (long) tier.number(), "capacity", (long) tier.bytes());
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public DeviceType type() {
		return DeviceType.STORAGE;
	}
}
