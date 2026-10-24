package com.payonel.modularcomputers.board;

import com.payonel.modularcomputers.device.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What holds a {@link BoardAssembly}: a case's block entity, a golem. The board is the same whatever
 * holds it; this says where it is, which way it faces, and how its holder is saved and shown.
 */
public interface BoardHost {
	/** Its world (null before it has one). */
	Level level();

	/** Where it is now: its sounds, its cards' world, its operator card's commands. */
	BlockPos pos();

	/** Which way its front faces (its cards' relative faces). */
	Direction front();

	/**
	 * Marks it changed, so the next save writes it, cheaply and often (a device's data changed).
	 * Never waits for a chunk: called while one loads ({@code ChunkMarks}).
	 */
	void markChanged();

	/** Marks it changed after a change of its own (parts, power, the bus order). */
	void setChanged();

	/** Sends its data (parts, bus order, power) to clients. */
	void syncToClients();

	/**
	 * Shows whether it could run, is on, halted, and shows a screen (a case's power button and
	 * front); whether that changed (and was sent to clients).
	 */
	boolean showPowerState(boolean ready, boolean powered, boolean halted, boolean screen);

	/** Sends its terminal's changed rows to the players near it. */
	void syncDisplay(Terminal display);

	/** What its redstone cards put out changed: its neighbours look again. */
	void redstoneChanged();

	/** Its Bluetooth cards changed (or its board did): what links to it looks again. */
	void bluetoothChanged();

	/** Whether a player can still use its parts view (still there, in reach). */
	boolean stillValid(Player player);

	/** How a log names it: which board, where. */
	String describe();
}
