package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.display.Display;

/** A block entity with a screen on its front: the all-in-one and the screen block. */
public interface DisplayHost {
	Display display();
}
