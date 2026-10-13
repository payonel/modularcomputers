package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A robotic piston: a device computers use to push on any of its faces but the one with the cable
 * port ({@link RoboticPistonBlock#push}). Instead of a cable it can take a Bluetooth card, in its one
 * slot. Its arms go back on their own ({@link #serverTick}).
 */
public final class RoboticPistonBlockEntity extends CardHolderBlockEntity {
	private final RoboticPiston device = new RoboticPiston();
	/** When each arm that's out goes back, by side (game time). */
	private final java.util.EnumMap<net.minecraft.core.Direction, Long> armsOut = new java.util.EnumMap<>(net.minecraft.core.Direction.class);

	public RoboticPistonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ROBOTIC_PISTON_ENTITY, pos, state, 1);
		// Device calls come on the server thread: it pushes in the world right then.
		device.pushWith(side -> {
			net.minecraft.core.Direction direction = net.minecraft.core.Direction.byName(side);
			if (level == null || level.isClientSide() || direction == null || !RoboticPistonBlock.push(level, worldPosition, direction)) {
				return false;
			}
			armsOut.put(direction, level.getGameTime() + RoboticPistonBlock.ARM_TICKS);
			return true;
		});
	}

	/** Server, every tick: arms whose push is over go back (an arm found out with no time, after a load, goes back too). */
	void serverTick() {
		long now = level.getGameTime();
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
			boolean out = level.getBlockState(worldPosition.relative(side)).is(ModBlocks.ROBOTIC_PISTON_ARM);
			if (out && !armsOut.containsKey(side)) {
				armsOut.put(side, now + RoboticPistonBlock.ARM_TICKS);
			}
		}
		armsOut.entrySet().removeIf(arm -> {
			if (now < arm.getValue()) {
				return false;
			}
			RoboticPistonBlock.retract(level, worldPosition, arm.getKey());
			return true;
		});
	}

	/** Its one slot is the radio slot: a Bluetooth card there links it to its computer instead of a cable. */
	@Override
	public int radioSlot() {
		return 0;
	}

	/** The piston as a bus device. */
	public RoboticPiston device() {
		return device;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.piston(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition), getBlockState().getBlock());
	}
}
