package com.payonel.modularcomputers.item;

import com.payonel.modularcomputers.block.Bluetooth;
import com.payonel.modularcomputers.hint.HintedItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A Bluetooth card. Right-click a block holding one to link this card to it (the blocks do
 * that, see {@link Bluetooth#link}); sneak + right-click, anywhere, to reset it.
 */
public final class BluetoothCardItem extends HintedItem {
	public BluetoothCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return player.isSecondaryUseActive() ? Bluetooth.reset(player.getItemInHand(hand), level, player) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		return player != null && player.isSecondaryUseActive() ? Bluetooth.reset(context.getItemInHand(), context.getLevel(), player) : InteractionResult.PASS;
	}
}
