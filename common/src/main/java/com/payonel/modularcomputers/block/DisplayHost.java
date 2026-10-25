package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.Terminal;

/** A block entity with a screen on its front: a case (a screen in its gold slot) and the screen block. */
public interface DisplayHost {
	Terminal terminal();

	/** Its front shows the terminal now (a case shows one only with a screen in its gold slot). */
	default boolean showsScreen() {
		return true;
	}
}
