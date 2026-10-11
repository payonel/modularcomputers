package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A robotic piston: a device computers will use to push on any of its faces but the one with
 * the cable port. Instead of a cable it can take a Bluetooth card, in its one slot. It does
 * nothing yet.
 */
public final class RoboticPistonBlockEntity extends CardHolderBlockEntity {
	private final RoboticPiston device = new RoboticPiston();

	public RoboticPistonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ROBOTIC_PISTON_ENTITY, pos, state, 1);
	}

	@Override
	public boolean accepts(ItemStack stack) {
		return stack.is(ModItems.BLUETOOTH_CARD);
	}

	/** The piston as a bus device. */
	public RoboticPiston device() {
		return device;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.piston(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}
}
