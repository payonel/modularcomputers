package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.machine.Program;

/**
 * Programs the tests flash onto a computer's EEPROM, so they never depend on the mod's own
 * stand-in firmware, which changes as it's used to try out other things.
 */
public final class TestPrograms {
	public static final String GREETING = "hello world";
	/**
	 * Sizes the console 50 by 16 (xterm's resize sequence): large enough to see on a block's face,
	 * where a tier 3 terminal's 160 by 50, the size it powers on at, makes text a few specks.
	 */
	public static final String READABLE = "\u001B[8;16;50t";
	/** Prints the greeting to the console (sized {@link #READABLE}), then parks. */
	public static final Program HELLO = board -> {
		board.invoke("console", "write", READABLE + GREETING + "\r\n");
		com.payonel.modularcomputers.machine.Program.park(board);
	};

	/**
	 * Writes the greeting (the console sized {@link #READABLE}), then echoes whatever is typed at the console's terminals back to the
	 * console, forever (Enter starts a new line). Its cursor is a steady block (DECSCUSR 2), so a
	 * screenshot always finds it lit.
	 */
	public static final Program ECHO = board -> {
		board.invoke("console", "write", READABLE + "\u001B[2 q" + GREETING + "\r\n");
		board.list();
		while (true) {
			for (String source : board.waitForInterrupt(3600)) {
				if (source.equals("bus")) {
					board.list();
					continue;
				}
				Object key;
				while ((key = board.invoke(source, "read").first()) != null) {
					board.invoke("console", "write", key.equals("\r") ? "\r\n" : key);
				}
			}
		}
	};

	/** Keeps the CPU busy forever (never waits): it draws a full CPU's power. */
	public static final Program BUSY = board -> {
		while (true) {
			board.uptime();
		}
	};

	private TestPrograms() {
	}

	/** Whether its program has parked (waiting for good on {@link Program#PARK}, as the tests' programs end), the power on. */
	public static boolean parked(com.payonel.modularcomputers.machine.Machine machine) {
		return machine.isPowered() && machine.runner().map(runner -> runner.waitingFor().contains(Program.PARK)).orElse(false);
	}

	/** Puts a tier 3 screen in the computer's gold slot: its terminal, on its front. */
	public static void withScreen(CaseBlockEntity computer) {
		computer.parts().setItem(com.payonel.modularcomputers.menu.CaseMenu.EXP_SLOTS,
			new net.minecraft.world.item.ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER3_SCREEN));
	}

	/** Reflashes the EEPROM in the computer's slot. */
	public static void flash(CaseBlockEntity computer, Program program) {
		computer.machine().board().eeprom().orElseThrow().flash(program);
	}
}
