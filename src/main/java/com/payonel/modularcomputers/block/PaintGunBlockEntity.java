package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.PaintGun;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/** A paint gun: its bus device, and one radio slot for a Bluetooth card. */
public final class PaintGunBlockEntity extends CardHolderBlockEntity {
	private final PaintGun device = new PaintGun();

	public PaintGunBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.PAINT_GUN_ENTITY, pos, state, 1);
	}

	public PaintGun device() {
		return device;
	}

	@Override
	public int radioSlot() {
		return 0;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.paintGun(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition));
	}
}
