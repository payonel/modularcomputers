package com.payonel.modularcomputers.device;

/**
 * A terminal's keyboard: what a key event sends down the line, by the terminal's mode (user,
 * 2026-10-16: "all key codes, up and down, control keys too").
 * <p>
 * Keys are numbered as the kitty keyboard protocol numbers them: a key that types text by its
 * character (unshifted, as the layout has it: 'a' is 97), the others by kitty's private-use codes
 * (Escape 27, Enter 13, Tab 9, Backspace 127, and {@link #INSERT}...). Modifiers are kitty's bits
 * ({@link #SHIFT}...), events {@link #PRESS}, {@link #REPEAT}, {@link #RELEASE}.
 * <p>
 * By default the terminal is a VT100's keyboard (legacy, as xterm's): text keys send their
 * characters (those come typed, from the layout, not from here), Ctrl+letter a control character,
 * the others their escape sequences ({@code CSI A}, or {@code ESC O A} in cursor-keys application
 * mode; {@code CSI 1;5A} with modifiers), and nothing on release. A program asks for more with the
 * kitty protocol's escape sequences, as modern terminal emulators take them ({@code CSI > flags u};
 * {@link com.payonel.modularcomputers.display.VtDecoder#keyboardFlags}): 1 disambiguates (Escape,
 * and Ctrl or Alt with a text key, as {@code CSI code;mods u}), 2 reports repeats and releases
 * ({@code :2}, {@code :3}), 8 reports every key as an escape code (text keys, modifiers and locks
 * too; typed text is then not sent), 16 (with 8) adds the text a press typed to its code, as
 * {@code CSI code;mods;text u}, text a code point each, colon-separated (the screen sends a key with
 * the text it typed: {@link #encode}'s {@code text}). Flag 4 (alternate keys) is taken and not
 * reported.
 */
public final class TerminalKeyboard {
	public static final int PRESS = 1;
	public static final int REPEAT = 2;
	public static final int RELEASE = 3;

	public static final int SHIFT = 1;
	public static final int ALT = 2;
	public static final int CONTROL = 4;
	public static final int SUPER = 8;
	public static final int CAPS_LOCK_ON = 64;
	public static final int NUM_LOCK_ON = 128;

	public static final int ESCAPE = 27;
	public static final int ENTER = 13;
	public static final int TAB = 9;
	public static final int BACKSPACE = 127;
	public static final int INSERT = 57348;
	public static final int DELETE = 57349;
	public static final int LEFT = 57350;
	public static final int RIGHT = 57351;
	public static final int UP = 57352;
	public static final int DOWN = 57353;
	public static final int PAGE_UP = 57354;
	public static final int PAGE_DOWN = 57355;
	public static final int HOME = 57356;
	public static final int END = 57357;
	public static final int CAPS_LOCK = 57358;
	public static final int SCROLL_LOCK = 57359;
	public static final int NUM_LOCK = 57360;
	public static final int PRINT_SCREEN = 57361;
	public static final int PAUSE = 57362;
	public static final int MENU = 57363;
	/** F1; F2..F35 follow it. */
	public static final int F1 = 57364;
	/** The keypad's 0; 1..9 follow it, then decimal, divide, multiply, subtract, add, enter, equal. */
	public static final int KP_0 = 57399;
	public static final int KP_DECIMAL = 57409;
	public static final int KP_DIVIDE = 57410;
	public static final int KP_MULTIPLY = 57411;
	public static final int KP_SUBTRACT = 57412;
	public static final int KP_ADD = 57413;
	public static final int KP_ENTER = 57414;
	public static final int KP_EQUAL = 57415;
	public static final int LEFT_SHIFT = 57441;
	public static final int LEFT_CONTROL = 57442;
	public static final int LEFT_ALT = 57443;
	public static final int LEFT_SUPER = 57444;
	public static final int RIGHT_SHIFT = 57447;
	public static final int RIGHT_CONTROL = 57448;
	public static final int RIGHT_ALT = 57449;
	public static final int RIGHT_SUPER = 57450;

	/** Where kitty's private-use key codes start: below it, a key is the text it types. */
	private static final int FUNCTIONAL = 57344;
	private static final int DISAMBIGUATE = 1;
	private static final int EVENT_TYPES = 2;
	private static final int ALL_KEYS = 8;
	private static final int ASSOCIATED_TEXT = 16;
	/** The most code points a key's text may carry (a key types one; a dead key's composition, two). */
	public static final int MAX_TEXT = 4;
	private static final String ESC = "\u001B";
	private static final String CSI = ESC + "[";
	private static final String SS3 = ESC + "O";

	private TerminalKeyboard() {
	}

	/** Whether typed text goes down the line (not when every key is reported as an escape code). */
	public static boolean sendsText(int flags) {
		return (flags & ALL_KEYS) == 0;
	}

	/** As {@link #encode(int, int, int, int, boolean, String)}, for a key that typed no text. */
	public static String encode(int key, int modifiers, int event, int flags, boolean cursorKeysApplication) {
		return encode(key, modifiers, event, flags, cursorKeysApplication, "");
	}

	/**
	 * Whether a key's text is fit to send: at most {@link #MAX_TEXT} code points, none a control
	 * character (C0, DEL, C1), as typed text is.
	 */
	public static boolean printable(String text) {
		if (text.codePointCount(0, text.length()) > MAX_TEXT) {
			return false;
		}
		return text.codePoints().noneMatch(c -> c < ' ' || c == 0x7F || c >= 0x80 && c < 0xA0);
	}

	/**
	 * What a key event sends, by the keyboard flags ({@code flags}) and cursor-keys mode in effect,
	 * and {@code text}, what the key typed ("" none: only a press or repeat types); null when it sends
	 * nothing (a release in legacy mode, a text key whose text comes typed).
	 */
	public static String encode(int key, int modifiers, int event, int flags, boolean cursorKeysApplication, String text) {
		boolean all = (flags & ALL_KEYS) != 0;
		boolean events = (flags & EVENT_TYPES) != 0;
		boolean disambiguate = (flags & DISAMBIGUATE) != 0 || all;
		if (!events) {
			if (event == RELEASE) {
				return null;
			}
			event = PRESS;
		}
		int mods = modifiers & (SHIFT | ALT | CONTROL | SUPER | (all ? CAPS_LOCK_ON | NUM_LOCK_ON : 0));
		String suffix = event != PRESS ? ";" + (mods + 1) + ":" + event : mods != 0 ? ";" + (mods + 1) : "";

		// keys with a letter form: arrows, Home, End, F1, F2, F4 (CSI 1;mods X, or the plain form)
		char letter = switch (key) {
			case UP -> 'A';
			case DOWN -> 'B';
			case RIGHT -> 'C';
			case LEFT -> 'D';
			case HOME -> 'H';
			case END -> 'F';
			case F1 -> 'P';
			case F1 + 1 -> 'Q';
			case F1 + 3 -> 'S';
			default -> 0;
		};
		if (key == F1 + 2 && flags == 0 && suffix.isEmpty()) {
			return SS3 + "R"; // F3, as a VT100 sends it (kitty's is CSI 13~: R is a cursor report)
		}
		if (letter != 0) {
			if (!suffix.isEmpty()) {
				return CSI + "1" + suffix + letter;
			}
			boolean function = key >= F1;
			return (function || cursorKeysApplication ? SS3 : CSI) + letter;
		}
		// keys with a tilde form
		int tilde = switch (key) {
			case INSERT -> 2;
			case DELETE -> 3;
			case PAGE_UP -> 5;
			case PAGE_DOWN -> 6;
			case F1 + 2 -> 13;
			case F1 + 4 -> 15;
			case F1 + 5 -> 17;
			case F1 + 6 -> 18;
			case F1 + 7 -> 19;
			case F1 + 8 -> 20;
			case F1 + 9 -> 21;
			case F1 + 10 -> 23;
			case F1 + 11 -> 24;
			default -> 0;
		};
		if (tilde != 0) {
			return CSI + tilde + suffix + "~";
		}
		String csiU = CSI + key + suffix + "u";
		if (all && (flags & ASSOCIATED_TEXT) != 0 && event != RELEASE && !text.isEmpty() && printable(text)) {
			StringBuilder codes = new StringBuilder();
			text.codePoints().forEach(c -> codes.append(codes.isEmpty() ? "" : ":").append(c));
			csiU = CSI + key + (suffix.isEmpty() ? ";1" : suffix) + ";" + codes + "u"; // (the text's field: kitty's third)
		}
		if (key == ESCAPE) {
			return disambiguate ? csiU : (mods & ALT) != 0 ? ESC + ESC : ESC;
		}
		if (key == ENTER || key == TAB || key == BACKSPACE || key == KP_ENTER) {
			if (all) {
				return csiU;
			}
			if (event == RELEASE) {
				return null; // (reported only with every key)
			}
			if (disambiguate && (mods & (CONTROL | ALT | SUPER)) != 0) {
				return CSI + (key == KP_ENTER ? ENTER : key) + suffix + "u";
			}
			String plain = switch (key) {
				case TAB -> (mods & SHIFT) != 0 ? CSI + "Z" : "\t";
				case BACKSPACE -> (mods & CONTROL) != 0 ? "\b" : "\u007F";
				default -> "\r";
			};
			return (mods & ALT) != 0 ? ESC + plain : plain;
		}
		if (key < FUNCTIONAL) {
			// a text key: its text comes typed; reported here only as escape codes, or as Ctrl's control character
			if (all) {
				return csiU;
			}
			if (event == RELEASE) {
				return null;
			}
			if (disambiguate && (mods & (CONTROL | ALT | SUPER)) != 0) {
				return csiU;
			}
			if ((mods & CONTROL) != 0) {
				return control(key);
			}
			return null;
		}
		// the rest (modifiers, locks, the keypad, F13 on): only as escape codes
		return all ? csiU : null;
	}

	/** Ctrl with a key, as a VT100 sends it: a letter's control character, and the few others; null if none. */
	private static String control(int key) {
		if (key >= 'a' && key <= 'z') {
			return String.valueOf((char) (key - 'a' + 1));
		}
		return switch (key) {
			case ' ', '@', '2' -> "\u0000";
			case '[', '3' -> ESC;
			case '\\', '4' -> "\u001C";
			case ']', '5' -> "\u001D";
			case '^', '6' -> "\u001E";
			case '_', '-', '/', '7' -> "\u001F";
			case '?', '8' -> "\u007F";
			default -> null;
		};
	}
}
