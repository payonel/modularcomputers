package com.payonel.modularcomputers.gametest;

/**
 * How many times the mouse has been grabbed (counted by {@code mixin.MouseGrabCounter}), so a
 * client test can check a screen change never went through "no screen", which grabs the mouse
 * and recenters the cursor. The test input can't move the system pointer under Xvfb, so the
 * cursor itself can't be watched.
 */
public final class MouseGrabs {
	public static int count;

	private MouseGrabs() {
	}
}
