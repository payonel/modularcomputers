package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.network.ModNetwork;
import com.payonel.modularcomputers.network.ScreenRowsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Sends a screen's changed rows to the players who have its block loaded. */
final class ScreenRows {
	private ScreenRows() {
	}

	static void send(ServerLevel level, BlockEntity block, com.payonel.modularcomputers.device.Terminal terminal) {
		ScreenRowsPayload payload = ScreenRowsPayload.changes(com.payonel.modularcomputers.network.HostRef.of(block.getBlockPos()), terminal);
		if (payload == null) {
			return;
		}
		block.setChanged(); // saved with the block
		for (var player : level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(block.getBlockPos()), false)) {
			ModNetwork.send(player, payload);
		}
	}
}
