package com.payonel.modularcomputers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Marks a block's chunk changed (Minecraft writes only chunks marked changed, at a save or an
 * unload) without ever waiting for the chunk. The game's own {@code blockEntityChanged} asks for
 * the chunk, which, while that chunk is still loading, waits for its load to finish: called from
 * the load itself (a block entity's {@code setLevel}, putting its devices back on the bus, and a
 * device's data changing as it does), the server thread waited on itself forever (a world with a
 * running computer never loaded, 2026-10-21). A chunk that isn't fully loaded yet holds what was
 * just read from the save: there's nothing to mark.
 */
public final class ChunkMarks {
	private ChunkMarks() {
	}

	/** Marks the chunk holding {@code pos} changed, if it's loaded now (server side; elsewhere, nothing). */
	public static void changed(Level level, BlockPos pos) {
		if (level == null || level.isClientSide()) {
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		if (chunk != null) {
			chunk.markUnsaved();
		}
	}
}
