package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * A terminal's bell, at its block: a BEL (\a) rings it, as a terminal beeps its speaker. Bells
 * closer together than {@link #SUPPRESS_TICKS} ring once (xterm's bellSuppressTime: a program
 * writing "\a\a\a" gets one beep, not a buzz).
 */
public final class TerminalBell {
	/** How soon after a bell another rings: xterm's 200 ms. */
	static final int SUPPRESS_TICKS = 4;
	private static final float VOLUME = 0.5f;
	private long rang = -SUPPRESS_TICKS; // (game time starts at 0: the first bell rings)

	/** A BEL, at the terminal's block. */
	public void ring(Level level, BlockPos pos) {
		if (level == null || level.isClientSide()) {
			return;
		}
		long now = level.getGameTime();
		if (now - rang < SUPPRESS_TICKS) {
			return;
		}
		rang = now;
		level.playSound(null, pos, ModSounds.BELL, SoundSource.BLOCKS, VOLUME, 1.0f);
	}
}
