package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.network.ScreenRowsPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Sends a screen's changed rows to the players who have its block loaded. */
final class ScreenRows {
	private ScreenRows() {
	}

	static void send(ServerLevel level, BlockEntity block, com.payonel.modularcomputers.device.Terminal terminal) {
		ScreenRowsPayload payload = ScreenRowsPayload.changes(block.getBlockPos(), terminal);
		if (payload == null) {
			return;
		}
		block.setChanged(); // saved with the block
		for (var player : PlayerLookup.tracking(block)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
