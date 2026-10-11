package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.block.Bluetooth;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** While the player holds a linked Bluetooth card, the block holding the card it links to is outlined. */
public final class BluetoothTarget {
	private BluetoothTarget() {
	}

	/** The block at {@code pos} holds the card that the card in the player's hand links to. */
	public static boolean isTarget(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return false;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = client.player.getItemInHand(hand);
			UUID link = held.is(ModItems.BLUETOOTH_CARD) ? held.get(ModComponents.BLUETOOTH_LINK) : null;
			if (link != null && Bluetooth.holds(client.level, pos, link)) {
				return true;
			}
		}
		return false;
	}
}
