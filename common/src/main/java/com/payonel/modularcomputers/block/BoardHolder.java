package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.board.BoardAssembly;

/**
 * Something holding a computer's board (a case, a golem), as its views see it on either side: its
 * board (its parts, bus entries and status, synced to clients) and its power button's light.
 */
public interface BoardHolder extends ViewHost {
	BoardAssembly board();

	/** Its power button's light, as clients see it. */
	PowerLight powerLight();

	@Override
	default boolean showsScreen() {
		return board().screenInSlot();
	}
}
