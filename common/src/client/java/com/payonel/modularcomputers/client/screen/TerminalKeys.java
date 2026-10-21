package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.device.TerminalKeyboard;
import net.minecraft.client.input.KeyEvent;

/**
 * The player's keyboard, as a terminal's keyboard reports it: each key by the kitty keyboard
 * protocol's number ({@link TerminalKeyboard}): a key that types text by its character as the
 * layout has it (SDL's keycode), the others by kitty's codes, from the physical key (SDL's
 * scancode); and the modifiers held, as kitty's bits. The terminal decides what goes down the line.
 */
public final class TerminalKeys {
	/** SDL marks a keycode that isn't a character with this bit. */
	private static final int SDL_SCANCODE_MASK = 1 << 30;
	/** Keypad keys SDL has and InputConstants doesn't name (USB HID usages). */
	private static final int KEY_NUMPAD_DIVIDE = 84;
	private static final int KEY_NUMPAD_SUBTRACT = 86;
	private static final int KEY_NUMPAD_DECIMAL = 99;
	private static final int KEY_APPLICATION = 101;

	private TerminalKeys() {
	}

	/** The key's number for the terminal, or 0 if it has none. */
	public static int code(KeyEvent event) {
		int key = event.key();
		if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
			return TerminalKeyboard.F1 + key - InputConstants.KEY_F1;
		}
		if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) {
			return TerminalKeyboard.F1 + 12 + key - InputConstants.KEY_F13;
		}
		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return TerminalKeyboard.KP_0 + 1 + key - InputConstants.KEY_NUMPAD1;
		}
		int functional = switch (key) {
			case InputConstants.KEY_ESCAPE -> TerminalKeyboard.ESCAPE;
			case InputConstants.KEY_RETURN -> TerminalKeyboard.ENTER;
			case InputConstants.KEY_TAB -> TerminalKeyboard.TAB;
			case InputConstants.KEY_BACKSPACE -> TerminalKeyboard.BACKSPACE;
			case InputConstants.KEY_INSERT -> TerminalKeyboard.INSERT;
			case InputConstants.KEY_DELETE -> TerminalKeyboard.DELETE;
			case InputConstants.KEY_LEFT -> TerminalKeyboard.LEFT;
			case InputConstants.KEY_RIGHT -> TerminalKeyboard.RIGHT;
			case InputConstants.KEY_UP -> TerminalKeyboard.UP;
			case InputConstants.KEY_DOWN -> TerminalKeyboard.DOWN;
			case InputConstants.KEY_PAGEUP -> TerminalKeyboard.PAGE_UP;
			case InputConstants.KEY_PAGEDOWN -> TerminalKeyboard.PAGE_DOWN;
			case InputConstants.KEY_HOME -> TerminalKeyboard.HOME;
			case InputConstants.KEY_END -> TerminalKeyboard.END;
			case InputConstants.KEY_CAPSLOCK -> TerminalKeyboard.CAPS_LOCK;
			case InputConstants.KEY_SCROLLLOCK -> TerminalKeyboard.SCROLL_LOCK;
			case InputConstants.KEY_NUMLOCK -> TerminalKeyboard.NUM_LOCK;
			case InputConstants.KEY_PRINTSCREEN -> TerminalKeyboard.PRINT_SCREEN;
			case InputConstants.KEY_PAUSE -> TerminalKeyboard.PAUSE;
			case KEY_APPLICATION -> TerminalKeyboard.MENU;
			case InputConstants.KEY_NUMPAD0 -> TerminalKeyboard.KP_0;
			case KEY_NUMPAD_DECIMAL -> TerminalKeyboard.KP_DECIMAL;
			case KEY_NUMPAD_DIVIDE -> TerminalKeyboard.KP_DIVIDE;
			case InputConstants.KEY_MULTIPLY -> TerminalKeyboard.KP_MULTIPLY;
			case KEY_NUMPAD_SUBTRACT -> TerminalKeyboard.KP_SUBTRACT;
			case InputConstants.KEY_ADD -> TerminalKeyboard.KP_ADD;
			case InputConstants.KEY_NUMPADENTER -> TerminalKeyboard.KP_ENTER;
			case InputConstants.KEY_NUMPADEQUALS -> TerminalKeyboard.KP_EQUAL;
			case InputConstants.KEY_LSHIFT -> TerminalKeyboard.LEFT_SHIFT;
			case InputConstants.KEY_LCONTROL -> TerminalKeyboard.LEFT_CONTROL;
			case InputConstants.KEY_LALT -> TerminalKeyboard.LEFT_ALT;
			case InputConstants.KEY_LGUI -> TerminalKeyboard.LEFT_SUPER;
			case InputConstants.KEY_RSHIFT -> TerminalKeyboard.RIGHT_SHIFT;
			case InputConstants.KEY_RCONTROL -> TerminalKeyboard.RIGHT_CONTROL;
			case InputConstants.KEY_RALT -> TerminalKeyboard.RIGHT_ALT;
			case InputConstants.KEY_RGUI -> TerminalKeyboard.RIGHT_SUPER;
			default -> 0;
		};
		if (functional != 0) {
			return functional;
		}
		int keycode = event.keycode();
		return keycode > 0 && (keycode & SDL_SCANCODE_MASK) == 0 ? keycode : 0; // a key that types: its character
	}

	/** The modifiers held, as the terminal's keyboard reports them. */
	public static int modifiers(KeyEvent event) {
		int held = event.modifiers();
		return ((held & InputConstants.MOD_SHIFT) != 0 ? TerminalKeyboard.SHIFT : 0)
			| ((held & InputConstants.MOD_ALT) != 0 ? TerminalKeyboard.ALT : 0)
			| ((held & InputConstants.MOD_CONTROL) != 0 ? TerminalKeyboard.CONTROL : 0)
			| ((held & InputConstants.MOD_SUPER) != 0 ? TerminalKeyboard.SUPER : 0)
			| ((held & InputConstants.MOD_CAPS_LOCK) != 0 ? TerminalKeyboard.CAPS_LOCK_ON : 0)
			| ((held & InputConstants.MOD_NUM_LOCK) != 0 ? TerminalKeyboard.NUM_LOCK_ON : 0);
	}
}
