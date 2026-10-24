package com.payonel.modularcomputers.machine;

/**
 * Which of the board's part slots a computer has (every computer's board is the same one, with
 * features left out of the cheaper ones: see {@link CaseTier}): its battery bays, RAM, SATA and
 * expansion slots, the first of each kind up to its count, whether its first expansion slot also
 * takes a screen, and how much its power buffer holds.
 */
public interface BoardLayout {
	/** Battery bays. */
	int bays();

	int ram();

	int sata();

	int expansion();

	/** Its first expansion slot also takes a screen. */
	boolean screenSlot();

	/** How much its power buffer holds, in power units. */
	int bufferCapacity();

	/** Its tier, as the board's firmware reads it (SMBIOS's chassis). */
	int number();

	/** An expansion lane with a device soldered on (a golem's robotics device): no slot for a part. */
	default boolean soldered(int lane) {
		return false;
	}

	/** Whether its screen slot takes a screen of {@code tier}. */
	default boolean takesScreen(com.payonel.modularcomputers.device.TerminalTier tier) {
		return true;
	}
}
