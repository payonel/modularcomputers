package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;

/**
 * What a key sends down the line to a terminal, as a VT100/xterm keyboard does: Return a CR,
 * Backspace a DEL, Ctrl+letter a control character, the arrows and editing keys their escape
 * sequences. Printable characters come from typing itself (with Shift, layouts and all), not
 * from here.
 */
public final class TerminalKeys {
	private static final String ESC = "\u001B";

	private TerminalKeys() {
	}

	/** The sequence for a key that doesn't type a character, or empty. */
	public static Optional<String> sequence(int key, boolean control) {
		if (control && key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
			return Optional.of(String.valueOf((char) (key - InputConstants.KEY_A + 1))); // Ctrl+A is 0x01
		}
		return Optional.ofNullable(switch (key) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "\r";
			case InputConstants.KEY_BACKSPACE -> "\u007F";
			case InputConstants.KEY_TAB -> "\t";
			case InputConstants.KEY_UP -> ESC + "[A";
			case InputConstants.KEY_DOWN -> ESC + "[B";
			case InputConstants.KEY_RIGHT -> ESC + "[C";
			case InputConstants.KEY_LEFT -> ESC + "[D";
			case InputConstants.KEY_HOME -> ESC + "[H";
			case InputConstants.KEY_END -> ESC + "[F";
			case InputConstants.KEY_INSERT -> ESC + "[2~";
			case InputConstants.KEY_DELETE -> ESC + "[3~";
			case InputConstants.KEY_PAGEUP -> ESC + "[5~";
			case InputConstants.KEY_PAGEDOWN -> ESC + "[6~";
			default -> null;
		});
	}
}
