package com.payonel.modularcomputers.client.render;

import com.payonel.modularcomputers.device.Terminal;

/**
 * How a terminal's cursor is drawn, as a terminal draws it: a block is its cell in reverse video
 * (the background in the text's color, the character in the background's), an underline or a bar
 * a thin stroke in the text's color; a blinking one is lit {@link #ON_MILLIS}, dark
 * {@link #OFF_MILLIS} (xterm's cursorOnTime and cursorOffTime).
 */
public final class CursorBlink {
	public static final long ON_MILLIS = 600;
	public static final long OFF_MILLIS = 300;
	/** An underline's height, and a bar's width, as a share of the cell. */
	public static final float STROKE = 0.125f;

	private CursorBlink() {
	}

	/** Whether the cursor is drawn now: it shows, and a blinking one is in its lit part. */
	public static boolean lit(Terminal.Cursor cursor) {
		if (!cursor.shown()) {
			return false;
		}
		return !cursor.blinks() || System.currentTimeMillis() % (ON_MILLIS + OFF_MILLIS) < ON_MILLIS;
	}
}
