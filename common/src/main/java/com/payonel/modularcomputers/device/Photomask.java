package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.config.ModConfig;

/**
 * A photomask on the bus (user, 2026-10-12): wired to a running computer, it has power from the
 * bus and prints chips without a battery. It has no methods yet; programs will drive it later.
 * <p>
 * Its power, all settings read as they're loaded (so a reload reaches one already built): it idles
 * at its draw ({@code power.draw.photomask}: very little, like an idle CPU), and each turn of work
 * costs {@link #turnCost} more ({@link BusPower#charge}). A turn of its crank by hand charges its
 * own buffer by {@link #crank}, and the buffer holds {@link #buffer}.
 */
public final class Photomask implements Device {
	/** While it runs, it turns once this many ticks. */
	public static final int TICKS_PER_TURN = 5;
	/** Its buffer holds this many hand turns of its crank. */
	public static final int BUFFER_CRANKS = 4;

	private final BusPower power = new BusPower(() -> ModConfig.draws().photomask());

	/** Power units a turn of work costs ({@code power.cost.photomask.turn}). */
	public static int turnCost() {
		return ModConfig.costs().photomaskTurn();
	}

	/** Power units it idles for from one turn to the next ({@code power.draw.photomask} a tick). */
	public static int idle() {
		return ModConfig.draws().photomask() * TICKS_PER_TURN;
	}

	/** What a hand turn of the crank puts in its buffer: one turn of work, and the idle draw till the next. */
	public static int crank() {
		return turnCost() + idle();
	}

	/** Its buffer's size: a few cranks' worth, from the settings as they are now. */
	public static int buffer() {
		return BUFFER_CRANKS * crank();
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public DeviceType type() {
		return DeviceType.FABRICATOR;
	}
}
