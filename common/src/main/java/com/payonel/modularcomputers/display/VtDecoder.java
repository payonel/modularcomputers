package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A VT100-family terminal's decoder: it turns a stream of text with escape sequences into cells
 * on a {@link TextBuffer}, as a VT102 does, or a modern terminal emulator (xterm and its kind),
 * whose common subset every pty program assumes:
 * <ul>
 * <li>controls: CR, LF (VT, FF), BS, TAB, BEL (its terminal's bell: {@link #onBell});</li>
 * <li>cursor: CUP/HVP, CUU/CUD/CUF/CUB, CNL/CPL, CHA, VPA, save and restore (ESC 7/8, CSI s/u),
 * index, next line and reverse index (ESC D/E/M), and xterm's deferred wrap at the right edge;</li>
 * <li>erasing and editing: ED, EL, IL, DL, ICH, DCH, ECH, SU, SD, and a scroll region
 * (DECSTBM);</li>
 * <li>SGR: reset, bold (brightens the 8 basic colors), reverse, the 16 colors, 256 colors
 * ({@code 38;5;n}) and 24-bit ({@code 38;2;r;g;b}), and the defaults (39, 49);</li>
 * <li>modes: new line mode (LNM, {@code CSI 20 h}: LF also returns the carriage), autowrap
 * ({@code ?7}), cursor visible ({@code ?25}), the alternate screen ({@code ?1049}, {@code ?47},
 * {@code ?1047});</li>
 * <li>reports, answered through {@code reply}: device status ({@code 5n}), cursor position
 * ({@code 6n}) and device attributes ({@code c}, a VT102);</li>
 * <li>xterm's size operations: resize the text area ({@code CSI 8;rows;cols t}, up to the
 * largest its terminal allows), report its size ({@code 18t}: {@code CSI 8;rows;cols t}) and the
 * largest ({@code 19t}: {@code CSI 9;rows;cols t});</li>
 * <li>full reset (ESC c).</li>
 * </ul>
 * <li>UTF-8 (every tier, as every terminal emulator today): its bytes decoded into characters, a
 * bad byte shown as U+FFFD; a wide character ({@link CharWidth}) takes two cells, a combining
 * mark joins the cell before, each character its own width (wcwidth's, as xterm's);</li>
 * <li>character sets: G0 and G1 designated ({@code ESC ( 0}, {@code ESC ) B}...), SO and SI
 * shifting between them; DEC's special graphics (the line-drawing set) and the UK set;</li>
 * Anything else is parsed and ignored, as real terminals do with sequences they don't know.
 * Erasing fills with the current background (xterm's "background color erase").
 */
public final class VtDecoder {
	/** The 16 basic colors (xterm's). */
	private static final int[] PALETTE = {
		0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
		0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
	};
	private static final int TAB = 8;
	/** The character sets G0 and G1 can hold. */
	private static final int ASCII = 0;
	private static final int DEC_GRAPHICS = 1;
	private static final int UK = 2;
	/** DEC's special graphics, for 0x5F to 0x7E (the VT100's line-drawing set, as Unicode). */
	private static final int[] SPECIAL_GRAPHICS = {
		0x00A0, 0x25C6, 0x2592, 0x2409, 0x240C, 0x240D, 0x240A, 0x00B0, 0x00B1, 0x2424, 0x240B, 0x2518, 0x2510, 0x250C, 0x2514, 0x253C,
		0x23BA, 0x23BB, 0x2500, 0x23BC, 0x23BD, 0x251C, 0x2524, 0x2534, 0x252C, 0x2502, 0x2264, 0x2265, 0x03C0, 0x2260, 0x00A3, 0x00B7,
	};
	private static final int SPECIAL_FIRST = 0x5F;
	private static final int POUND = 0x00A3;
	private static final int REPLACEMENT = 0xFFFD;

	private enum Parse { GROUND, ESCAPE, CHARSET, CSI, STRING, DCS, SIXEL }

	private final TextBuffer buffer;
	private final Consumer<String> reply;
	private int columns;
	private int rows;
	/** The largest it may be made (xterm's resize sequence): its first size, unless its terminal says. */
	private java.util.function.Supplier<Resolution> largest;
	/** Told when its size changes. */
	private Runnable resized = () -> {
	};

	private Parse parse = Parse.GROUND;
	private final StringBuilder params = new StringBuilder();
	private boolean stringEscape;
	/** A sixel picture being decoded (tier 3's graphics), or null. */
	private Sixel sixel;
	/**
	 * DEC's sixel display mode (DECSDM, {@code CSI ?80 h}): a picture doesn't scroll the screen
	 * (what's past the bottom is dropped) and the cursor stays where it was. (xterm's also puts the
	 * picture at the top left; this one draws it at the cursor.)
	 */
	private boolean sixelDisplayMode;
	/** xterm's bracketed paste ({@code CSI ?2004 h}): a paste comes wrapped in {@code ESC[200~} and {@code ESC[201~}. */
	private boolean bracketedPaste;
	/**
	 * xterm's mouse tracking, what a program asked for ({@code CSI ?n h}): none (0), presses only
	 * (X10's, 9), presses and releases (1000), and drags (1002), and every motion (1003); and whether
	 * reports come in SGR's encoding (1006), not X10's bytes.
	 */
	private int mouseTracking;
	private boolean mouseSgr;
	public static final int MOUSE_OFF = 0;
	public static final int MOUSE_X10 = 9;
	public static final int MOUSE_NORMAL = 1000;
	public static final int MOUSE_BUTTON_EVENT = 1002;
	public static final int MOUSE_ANY_EVENT = 1003;
	private static final int MOUSE_SGR = 1006;
	/** The tracking modes, in the order {@link #state()} numbers them. */
	private static final int[] MOUSE_MODES = {MOUSE_OFF, MOUSE_X10, MOUSE_NORMAL, MOUSE_BUTTON_EVENT, MOUSE_ANY_EVENT};

	/** G0 and G1 (ASCII, DEC_GRAPHICS, UK), which SO and SI shift between, and the one being designated. */
	private final int[] charsets = {ASCII, ASCII};
	private boolean shiftOut;
	private int designating;
	/** A UTF-8 character being decoded: its bytes still to come, its length, and its bits so far. */
	private int utf8Left;
	private int utf8Length;
	private int utf8Code;

	private int x;
	private int y;
	private boolean pendingWrap;
	/** The current colors as set by SGR: an index into the 16 colors, or -1 with {@link #fgRgb}. */
	private int fgIndex = -1;
	private int fgRgb = TextBuffer.DEFAULT_FOREGROUND;
	private int bgIndex = -1;
	private int bgRgb = TextBuffer.DEFAULT_BACKGROUND;
	private boolean bold;
	private boolean reverse;
	private int top;
	private int bottom;
	private boolean newLineMode;
	private boolean autowrap = true;
	private boolean cursorVisible = true;
	/** The cursor's shape, as DECSCUSR ({@code CSI Ps SP q}) sets it: 0 or 1 a blinking block (the default), 2 a steady block, 3/4 an underline, 5/6 a bar (odd ones blink). */
	private int cursorStyle;
	/** DECCKM ({@code CSI ?1h}): the cursor keys send {@code ESC O A}... (application mode), not {@code CSI A}. */
	private boolean cursorKeysApplication;
	/**
	 * The kitty keyboard protocol's flags (what keys report: 1 disambiguate, 2 event types, 4
	 * alternate keys, 8 all keys as escape codes, 16 associated text), set by {@code CSI > f u}
	 * (pushed), {@code CSI < n u} (popped), {@code CSI = f ; m u}; asked by {@code CSI ? u}.
	 */
	private int keyboardFlags;
	private final java.util.ArrayDeque<Integer> keyboardStack = new java.util.ArrayDeque<>();
	/** How many pushed keyboard modes are kept (kitty's stack, bounded). */
	private static final int KEYBOARD_STACK = 16;
	/** The kitty flags this terminal acts on (1, 2 and 8; 4 and 16 are acknowledged, not reported). */
	public static final int KEYBOARD_FLAGS = 31;
	/** The CSI sequence being run's private prefix ('?', '>', '<', '='), or 0. */
	private char prefix;
	private int[] saved;
	/** The main screen while the alternate one shows: characters, then foregrounds, backgrounds. */
	private Screen mainScreen;

	/**
	 * Where lines go that scroll off the top of the main screen: the terminal's scrollback (its
	 * saved lines, as xterm calls them). The alternate screen saves none, and ED 3 ({@code ESC[3J})
	 * clears them.
	 */
	public interface History {
		History NONE = new History() {
			@Override
			public void scrolledOff(String text, int[] foregrounds, int[] backgrounds) {
			}

			@Override
			public void clear() {
			}
		};

		void scrolledOff(String text, int[] foregrounds, int[] backgrounds);

		void clear();
	}

	private History history = History.NONE;

	public void setHistory(History history) {
		this.history = history;
	}

	/** A screen's cells, as saved: the main screen while the alternate one shows. */
	public record Screen(List<String> rows, int[] foregrounds, int[] backgrounds) {
	}

	/** What it shows of the colors it's sent. */
	private final ColorDepth depth;
	/** Its answer to Device Attributes ({@code ESC[c}): what kind of terminal it is. */
	private final String attributes;

	/** A full-color decoder that answers Device Attributes as a VT102. */
	/** What a BEL does: its terminal's bell. */
	private Runnable bell = () -> {
	};

	/** What rings when a BEL comes (its terminal's bell). */
	public void onBell(Runnable bell) {
		this.bell = bell;
	}

	public VtDecoder(TextBuffer buffer, Consumer<String> reply) {
		this(buffer, reply, ColorDepth.TRUE_COLOR, "\u001B[?6c");
	}

	/**
	 * Decodes onto {@code buffer}; {@code reply} gets what the terminal sends back (reports). It
	 * shows colors at {@code depth}, and answers Device Attributes with {@code attributes}.
	 */
	public VtDecoder(TextBuffer buffer, Consumer<String> reply, ColorDepth depth, String attributes) {
		this.depth = depth;
		this.attributes = attributes;
		this.buffer = buffer;
		this.reply = reply;
		this.columns = buffer.resolution().columns();
		this.rows = buffer.resolution().rows();
		this.bottom = rows - 1;
		Resolution first = buffer.resolution();
		this.largest = () -> first;
	}

	/** How large a program may make it ({@code CSI 8;rows;cols t}), and who to tell when its size changes. */
	public void sizing(java.util.function.Supplier<Resolution> largest, Runnable resized) {
		this.largest = largest;
		this.resized = resized;
	}

	/** The largest it may be made now. */
	public Resolution largest() {
		return largest.get();
	}

	/**
	 * Another size, as xterm's: nothing is wrapped again (cells that still fit stay, new ones are
	 * blank). Fewer rows: the lines above the cursor go off the top first (into the scrollback, on
	 * the main screen), so the cursor stays where it was on the text. The scroll region is the
	 * whole screen again. The main screen behind the alternate one is made the same size.
	 */
	public void resize(Resolution size) {
		if (size.columns() == columns && size.rows() == rows) {
			return;
		}
		Resolution old = buffer.resolution();
		if (size.rows() < rows && y >= size.rows()) {
			int n = y - size.rows() + 1;
			scrollUp(0, rows - 1, n);
			y -= n;
		}
		buffer.resize(size);
		columns = size.columns();
		rows = size.rows();
		top = 0;
		bottom = rows - 1;
		moveTo(x, y);
		if (mainScreen != null) {
			mainScreen = resized(mainScreen, old, size);
		}
		resized.run();
	}

	/** A saved screen made another size, as {@link TextBuffer#resize} makes one. */
	private static Screen resized(Screen screen, Resolution from, Resolution to) {
		List<String> rows = new java.util.ArrayList<>();
		int[] foregrounds = new int[to.columns() * to.rows()];
		int[] backgrounds = new int[to.columns() * to.rows()];
		java.util.Arrays.fill(foregrounds, TextBuffer.DEFAULT_FOREGROUND);
		java.util.Arrays.fill(backgrounds, TextBuffer.DEFAULT_BACKGROUND);
		int columns = Math.min(from.columns(), to.columns());
		for (int row = 0; row < to.rows(); row++) {
			String text = row < screen.rows().size() ? screen.rows().get(row) : "";
			text = TextBuffer.fit(text, to.columns());
			rows.add(text);
			if (row < from.rows() && (row + 1) * from.columns() <= screen.foregrounds().length && (row + 1) * from.columns() <= screen.backgrounds().length) {
				System.arraycopy(screen.foregrounds(), row * from.columns(), foregrounds, row * to.columns(), columns);
				System.arraycopy(screen.backgrounds(), row * from.columns(), backgrounds, row * to.columns(), columns);
			}
		}
		return new Screen(rows, foregrounds, backgrounds);
	}

	public int column() {
		return x;
	}

	public int row() {
		return y;
	}

	public int cursorStyle() {
		return cursorStyle;
	}

	public boolean cursorKeysApplication() {
		return cursorKeysApplication;
	}

	/** The kitty keyboard protocol's flags in effect (0: legacy, as a VT100's keyboard). */
	public int keyboardFlags() {
		return keyboardFlags;
	}

	public boolean cursorVisible() {
		return cursorVisible;
	}

	public boolean newLineMode() {
		return newLineMode;
	}

	/**
	 * Decodes text onto the buffer: bytes (as chars 0 to 255, a program's output), UTF-8; a char
	 * past 255 (a Java string's own) is taken as the character it is.
	 */
	public void write(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c > 0xFF) {
				int cp = text.codePointAt(i);
				i += Character.charCount(cp) - 1;
				flushUtf8();
				accept(cp);
			} else {
				decode(c);
			}
		}
	}

	/** The smallest character each UTF-8 length may hold (shorter is overlong: refused). */
	private static final int[] UTF8_MINIMUM = {0, 0, 0x80, 0x800, 0x10000};

	/** A byte of UTF-8. */
	private void decode(int b) {
		if (utf8Left > 0) {
			if ((b & 0xC0) == 0x80) {
				utf8Code = utf8Code << 6 | b & 0x3F;
				if (--utf8Left == 0) {
					int cp = utf8Code;
					boolean bad = cp < UTF8_MINIMUM[utf8Length] || cp > Character.MAX_CODE_POINT || cp >= 0xD800 && cp <= 0xDFFF;
					accept(bad ? REPLACEMENT : cp);
				}
				return;
			}
			flushUtf8(); // (cut short: what came is one bad character, and this byte starts afresh)
		}
		if (b < 0x80) {
			accept(b);
		} else if (b >= 0xC2 && b <= 0xDF) {
			start(2, b & 0x1F);
		} else if (b >= 0xE0 && b <= 0xEF) {
			start(3, b & 0x0F);
		} else if (b >= 0xF0 && b <= 0xF4) {
			start(4, b & 0x07);
		} else {
			accept(REPLACEMENT); // (a continuation alone, or a byte UTF-8 never has)
		}
	}

	private void start(int length, int bits) {
		utf8Length = length;
		utf8Left = length - 1;
		utf8Code = bits;
	}

	/** A character left half decoded: shown as U+FFFD. */
	private void flushUtf8() {
		if (utf8Left > 0) {
			utf8Left = 0;
			accept(REPLACEMENT);
		}
	}

	/** Back to power-on state: blank screen, cursor home, default colors and modes (ESC c). */
	public void reset() {
		parse = Parse.GROUND;
		params.setLength(0);
		x = 0;
		y = 0;
		pendingWrap = false;
		resetAttributes();
		top = 0;
		bottom = rows - 1;
		newLineMode = false;
		autowrap = true;
		cursorVisible = true;
		cursorStyle = 0;
		cursorKeysApplication = false;
		keyboardFlags = 0;
		keyboardStack.clear();
		mouseTracking = MOUSE_OFF;
		mouseSgr = false;
		saved = null;
		mainScreen = null;
		charsets[0] = ASCII;
		charsets[1] = ASCII;
		shiftOut = false;
		utf8Left = 0;
		buffer.clear();
	}

	// ---- parsing --------------------------------------------------------------------------

	/** A character, decoded: printed, or part of a control or an escape sequence. */
	private void accept(int cp) {
		if (cp > 0x7F && parse != Parse.GROUND) {
			// not ASCII in an escape sequence: it ends it (xterm's); inside a string (OSC, DCS), it's the string's
			if (parse == Parse.ESCAPE || parse == Parse.CSI || parse == Parse.CHARSET) {
				parse = Parse.GROUND;
			}
			return;
		}
		if (parse == Parse.GROUND) {
			ground(cp);
			return;
		}
		char c = (char) cp;
		switch (parse) {
			case GROUND -> ground(c);
			case ESCAPE -> escape(c);
			case CHARSET -> designate(c);
			case CSI -> csi(c);
			case STRING -> string(c);
			case DCS -> dcs(c);
			case SIXEL -> sixel(c);
		}
	}

	/**
	 * A DCS's parameters, then its final character: {@code q} is sixel graphics, drawn by a
	 * terminal that shows every color (tier 3, as a VT340's); any other, or sixel on a terminal
	 * without them, is read to its end and ignored.
	 */
	private void dcs(char c) {
		if (c >= '0' && c <= '9' || c == ';') {
			if (params.length() < 64) {
				params.append(c);
			}
			return;
		}
		if (c == 'q' && depth == ColorDepth.TRUE_COLOR) {
			String[] args = params.toString().split(";", -1);
			int p2 = args.length > 1 && args[1].equals("1") ? 1 : 0; // (P2 1: transparent where it draws nothing)
			sixel = new Sixel(p2, (columns - x) * TextBuffer.TILE_WIDTH, rows * TextBuffer.TILE_HEIGHT);
			stringEscape = false;
			parse = Parse.SIXEL;
			return;
		}
		stringEscape = false;
		parse = Parse.STRING;
		string(c);
	}

	/** A sixel picture's data, to ST (ESC \\): then it's drawn at the cursor. */
	private void sixel(char c) {
		if (stringEscape) {
			stringEscape = false;
			placeSixel();
			parse = Parse.GROUND;
			if (c != '\\') {
				escape(c); // (another escape sequence ended it)
			}
			return;
		}
		if (c == '\u001B') {
			stringEscape = true;
		} else if (c == '\u0018' || c == '\u001A') {
			sixel = null; // CAN, SUB: dropped
			parse = Parse.GROUND;
		} else {
			sixel.accept(c);
		}
	}

	/**
	 * Draws the picture just decoded at the cursor, over the cells it covers (as their tiles, mixed
	 * with what tiles they had where it's transparent); a picture past the bottom scrolls the screen
	 * up, as a VT340's does. The cursor goes to the line below it, at the column it started from.
	 */
	private void placeSixel() {
		Sixel picture = sixel;
		sixel = null;
		int[] pixels = picture.finish();
		int width = picture.width();
		int height = picture.height();
		if (width == 0 || height == 0) {
			return;
		}
		pendingWrap = false;
		int wide = Math.min(Math.ceilDiv(width, TextBuffer.TILE_WIDTH), columns - x);
		int high = Math.min(Math.ceilDiv(height, TextBuffer.TILE_HEIGHT), sixelDisplayMode ? rows - y : rows);
		if (y + high > rows) {
			int n = y + high - rows;
			scrollUp(0, rows - 1, n);
			y -= n;
		}
		for (int cy = 0; cy < high; cy++) {
			for (int cx = 0; cx < wide; cx++) {
				int[] under = buffer.tile(x + cx, y + cy);
				int[] tile = under != null ? under.clone() : new int[TextBuffer.TILE_PIXELS];
				for (int ty = 0; ty < TextBuffer.TILE_HEIGHT; ty++) {
					int py = cy * TextBuffer.TILE_HEIGHT + ty;
					for (int tx = 0; tx < TextBuffer.TILE_WIDTH; tx++) {
						int px = cx * TextBuffer.TILE_WIDTH + tx;
						if (px < width && py < height && (pixels[py * width + px] >>> 24) != 0) {
							tile[ty * TextBuffer.TILE_WIDTH + tx] = pixels[py * width + px];
						}
					}
				}
				buffer.setTile(x + cx, y + cy, tile);
			}
		}
		if (sixelDisplayMode) {
			return; // (the cursor stays)
		} else if (y + high >= rows) {
			scrollUp(0, rows - 1, 1);
			y = rows - 1;
		} else {
			y += high;
		}
	}

	/** ESC ( x and the like: G0 (or G1) is now the set {@code c} names (DEC graphics, the UK's, else ASCII). */
	private void designate(char c) {
		parse = Parse.GROUND;
		if (designating < charsets.length) {
			charsets[designating] = c == '0' ? DEC_GRAPHICS : c == 'A' ? UK : ASCII;
		}
	}

	private void ground(int c) {
		switch (c) {
			case '\u001B' -> parse = Parse.ESCAPE;
			case '\u000E' -> shiftOut = true; // SO: G1
			case '\u000F' -> shiftOut = false; // SI: G0
			case '\r' -> carriageReturn();
			case '\n', '\u000B', '\u000C' -> {
				index();
				if (newLineMode) {
					carriageReturn();
				}
			}
			case '\b' -> {
				pendingWrap = false;
				x = Math.max(0, x - 1);
			}
			case '\t' -> {
				pendingWrap = false;
				x = Math.min(columns - 1, (x / TAB + 1) * TAB);
			}
			case '\u0007' -> bell.run();
			default -> {
				if (c >= ' ' && c != 0x7F && !(c >= 0x80 && c <= 0x9F)) { // (DEL and C1 controls show nothing)
					print(c);
				}
			}
		}
	}

	private void escape(char c) {
		parse = Parse.GROUND;
		switch (c) {
			case '[' -> {
				params.setLength(0);
				parse = Parse.CSI;
			}
			case 'P' -> {
				params.setLength(0);
				parse = Parse.DCS; // a device control string: its parameters, then what it is
			}
			case ']', '_', '^', 'X' -> {
				stringEscape = false;
				parse = Parse.STRING; // OSC and the like: read to the end, ignored
			}
			case '(', ')', '*', '+' -> {
				designating = c == '(' ? 0 : c == ')' ? 1 : charsets.length; // (G2 and G3: read, unused)
				parse = Parse.CHARSET;
			}
			case '7' -> saveCursor();
			case '8' -> restoreCursor();
			case 'D' -> index();
			case 'E' -> {
				index();
				carriageReturn();
			}
			case 'M' -> reverseIndex();
			case 'c' -> reset();
			default -> {
				// ESC = > and others: ignored
			}
		}
	}

	private void string(char c) {
		if (c == '\u0007' || stringEscape && c == '\\') {
			parse = Parse.GROUND;
		}
		stringEscape = c == '\u001B';
	}

	private void csi(char c) {
		if (c >= 0x40 && c <= 0x7E) {
			parse = Parse.GROUND;
			String raw = params.toString();
			boolean privateMode = !raw.isEmpty() && "?<=>".indexOf(raw.charAt(0)) >= 0;
			prefix = privateMode ? raw.charAt(0) : 0;
			String body = raw.replaceAll("[^0-9;:]", "");
			List<Integer> args = new ArrayList<>();
			for (String part : body.split("[;:]", -1)) {
				args.add(part.isEmpty() ? -1 : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(part.length() > 9 ? "999999999" : part)));
			}
			char intermediate = 0;
			for (int i = 0; i < raw.length() && !privateMode; i++) {
				if (raw.charAt(i) >= ' ' && raw.charAt(i) <= '/') {
					intermediate = raw.charAt(i);
				}
			}
			execute(c, privateMode, intermediate, args);
		} else if (c == '\u001B') {
			parse = Parse.ESCAPE; // a broken sequence: start over
		} else if (params.length() < 64) {
			params.append(c);
		}
	}

	private static int arg(List<Integer> args, int index, int fallback) {
		return index < args.size() && args.get(index) > 0 ? args.get(index) : fallback;
	}

	private static int argOrZero(List<Integer> args, int index) {
		return index < args.size() && args.get(index) >= 0 ? args.get(index) : 0;
	}

	private void execute(char command, boolean privateMode, char intermediate, List<Integer> args) {
		if (intermediate != 0) {
			if (intermediate == ' ' && command == 'q') {
				int style = argOrZero(args, 0);
				cursorStyle = style <= 6 ? style : 0; // DECSCUSR (CSI Ps SP q): the cursor's shape
			} else if (intermediate == '$' && command == 'v') {
				// DECCRA (a VT420's): CSI top;left;bottom;right;page;toTop;toLeft;page $v copies a rectangle
				copyRectangle(arg(args, 0, 1) - 1, arg(args, 1, 1) - 1, arg(args, 2, rows) - 1, arg(args, 3, columns) - 1,
					arg(args, 5, 1) - 1, arg(args, 6, 1) - 1);
			} else if (intermediate == '$' && command == 'x') {
				// DECFRA: CSI char;top;left;bottom;right $x fills a rectangle with a character, in the current colors
				fillRectangle(argOrZero(args, 0), arg(args, 1, 1) - 1, arg(args, 2, 1) - 1, arg(args, 3, rows) - 1, arg(args, 4, columns) - 1);
			}
			return; // (other sequences with intermediates are ignored)
		}
		int n = arg(args, 0, 1);
		switch (command) {
			case 'A' -> moveTo(x, Math.max(y - n, y >= top ? top : 0));
			case 'B' -> moveTo(x, Math.min(y + n, y <= bottom ? bottom : rows - 1));
			case 'C' -> moveTo(x + n, y);
			case 'D' -> moveTo(x - n, y);
			case 'E' -> moveTo(0, Math.min(y + n, bottom));
			case 'F' -> moveTo(0, Math.max(y - n, top));
			case 'G', '`' -> moveTo(n - 1, y);
			case 'd' -> moveTo(x, n - 1);
			case 'H', 'f' -> moveTo(arg(args, 1, 1) - 1, n - 1);
			case 'J' -> eraseDisplay(argOrZero(args, 0));
			case 'K' -> eraseLine(argOrZero(args, 0));
			case 'L' -> insertLines(n);
			case 'M' -> deleteLines(n);
			case '@' -> insertChars(n);
			case 'P' -> deleteChars(n);
			case 'X' -> erase(x, y, Math.min(n, columns - x));
			case 'S' -> scrollUp(top, bottom, n);
			case 'T' -> scrollDown(top, bottom, n);
			case 'm' -> sgr(args);
			case 'r' -> {
				int newTop = arg(args, 0, 1) - 1;
				int newBottom = arg(args, 1, rows) - 1;
				if (newTop < newBottom && newBottom < rows) {
					top = newTop;
					bottom = newBottom;
					moveTo(0, 0);
				}
			}
			case 's' -> saveCursor();
			case 't' -> {
				// xterm's window operations, those about text: resize (8), report the size (18),
				// report the largest it can be (19); the rest are a window manager's
				if (privateMode) {
					return;
				}
				switch (argOrZero(args, 0)) {
					case 8 -> {
						// CSI 8;rows;cols t: an omitted one stays as it is, 0 is the largest (xterm's)
						Resolution most = largest.get();
						int wantRows = args.size() > 1 && args.get(1) >= 0 ? (args.get(1) == 0 ? most.rows() : args.get(1)) : rows;
						int wantColumns = args.size() > 2 && args.get(2) >= 0 ? (args.get(2) == 0 ? most.columns() : args.get(2)) : columns;
						resize(new Resolution(Math.clamp(wantColumns, 1, most.columns()), Math.clamp(wantRows, 1, most.rows())));
					}
					case 18 -> reply.accept("\u001B[8;" + rows + ";" + columns + "t");
					case 19 -> reply.accept("\u001B[9;" + largest.get().rows() + ";" + largest.get().columns() + "t");
					default -> {
					}
				}
			}
			case 'u' -> {
				switch (prefix) {
					case '>' -> {
						if (keyboardStack.size() >= KEYBOARD_STACK) {
							keyboardStack.removeLast();
						}
						keyboardStack.push(keyboardFlags);
						keyboardFlags = argOrZero(args, 0) & KEYBOARD_FLAGS;
					}
					case '<' -> {
						for (int i = 0; i < arg(args, 0, 1); i++) {
							keyboardFlags = keyboardStack.isEmpty() ? 0 : keyboardStack.pop();
						}
					}
					case '=' -> {
						int flags = argOrZero(args, 0) & KEYBOARD_FLAGS;
						keyboardFlags = switch (arg(args, 1, 1)) {
							case 2 -> keyboardFlags | flags;
							case 3 -> keyboardFlags & ~flags;
							default -> flags;
						};
					}
					case '?' -> reply.accept("\u001B[?" + keyboardFlags + "u");
					default -> restoreCursor();
				}
			}
			case 'h', 'l' -> setModes(privateMode, command == 'h', args);
			case 'n' -> {
				if (!privateMode && argOrZero(args, 0) == 5) {
					reply.accept("\u001B[0n");
				} else if (!privateMode && argOrZero(args, 0) == 6) {
					reply.accept("\u001B[" + (y + 1) + ";" + (x + 1) + "R");
				}
			}
			case 'c' -> {
				if (!privateMode && argOrZero(args, 0) == 0) {
					reply.accept(attributes); // what kind of terminal it is
				}
			}
			default -> {
				// not supported: ignored
			}
		}
	}

	private void setModes(boolean privateMode, boolean on, List<Integer> args) {
		for (int mode : args) {
			if (!privateMode) {
				if (mode == 20) {
					newLineMode = on;
				}
				continue;
			}
			switch (mode) {
				case 1 -> cursorKeysApplication = on;
				case 7 -> autowrap = on;
				case 25 -> cursorVisible = on;
				case 80 -> sixelDisplayMode = on;
				case 2004 -> bracketedPaste = on;
				case MOUSE_X10, MOUSE_NORMAL, MOUSE_BUTTON_EVENT, MOUSE_ANY_EVENT -> {
					if (on) {
						mouseTracking = mode;
					} else if (mouseTracking == mode) {
						mouseTracking = MOUSE_OFF; // (as xterm: resetting another mode leaves this one)
					}
				}
				case MOUSE_SGR -> mouseSgr = on;
				case 47, 1047, 1049 -> alternateScreen(on, mode == 1049);
				default -> {
					// not supported: ignored
				}
			}
		}
	}

	// ---- the screen -----------------------------------------------------------------------

	/**
	 * Prints a character at the cursor, in the shifted-in set (a DEC graphics character as its
	 * line-drawing one). A combining mark joins the cell before; a wide character takes two cells,
	 * wrapping first when only one is left (or, with autowrap off, put in the last two).
	 */
	private void print(int c) {
		int set = charsets[shiftOut ? 1 : 0];
		if (set == DEC_GRAPHICS && c >= SPECIAL_FIRST && c < SPECIAL_FIRST + SPECIAL_GRAPHICS.length) {
			c = SPECIAL_GRAPHICS[c - SPECIAL_FIRST];
		} else if (set == UK && c == '#') {
			c = POUND;
		}
		int width = CharWidth.of(c);
		if (width == 0) {
			int under = pendingWrap ? x : x - 1; // (the character just printed)
			if (under >= 0) {
				if (buffer.get(under, y) == TextBuffer.WIDE && under > 0) {
					under--;
				}
				buffer.addMark(under, y, c);
			}
			return;
		}
		if (width == 2 && columns < 2) {
			c = REPLACEMENT; // (nowhere two cells wide)
			width = 1;
		}
		if (pendingWrap || width == 2 && x == columns - 1 && autowrap) {
			pendingWrap = false;
			carriageReturn();
			index();
		}
		if (width == 2 && x == columns - 1) {
			x = columns - 2; // (no autowrap: the last two cells)
		}
		buffer.set(x, y, c, foreground(), background());
		if (width == 2) {
			buffer.set(x + 1, y, TextBuffer.WIDE, foreground(), background());
		}
		buffer.mend(x, y);
		buffer.mend(x + width - 1, y);
		if (x + width <= columns - 1) {
			x += width;
		} else {
			x = columns - 1;
			if (autowrap) {
				pendingWrap = true; // the next character wraps, as on a real VT100
			}
		}
	}

	private void carriageReturn() {
		pendingWrap = false;
		x = 0;
	}

	/** LF: down a line, scrolling the region at its bottom. */
	private void index() {
		pendingWrap = false;
		if (y == bottom) {
			scrollUp(top, bottom, 1);
		} else if (y < rows - 1) {
			y++;
		}
	}

	private void reverseIndex() {
		pendingWrap = false;
		if (y == top) {
			scrollDown(top, bottom, 1);
		} else if (y > 0) {
			y--;
		}
	}

	private void moveTo(int column, int row) {
		pendingWrap = false;
		x = Math.max(0, Math.min(columns - 1, column));
		y = Math.max(0, Math.min(rows - 1, row));
	}

	private void saveCursor() {
		saved = new int[] {x, y, fgIndex, fgRgb, bgIndex, bgRgb, bold ? 1 : 0, reverse ? 1 : 0, packedCharsets()}; // (DECSC saves the sets too)
	}

	/** G0, G1 and the shift, in an int (saves). */
	private int packedCharsets() {
		return charsets[0] | charsets[1] << 2 | (shiftOut ? 1 << 4 : 0);
	}

	private void unpackCharsets(int packed) {
		charsets[0] = Math.min(packed & 3, UK);
		charsets[1] = Math.min(packed >> 2 & 3, UK);
		shiftOut = (packed & 1 << 4) != 0;
	}

	private void restoreCursor() {
		if (saved == null) {
			moveTo(0, 0);
			resetAttributes();
			return;
		}
		moveTo(saved[0], saved[1]);
		fgIndex = saved[2];
		fgRgb = saved[3];
		bgIndex = saved[4];
		bgRgb = saved[5];
		bold = saved[6] != 0;
		reverse = saved[7] != 0;
		unpackCharsets(saved[8]);
	}

	private void alternateScreen(boolean on, boolean saveCursor) {
		if (on == (mainScreen != null)) {
			return;
		}
		if (on) {
			if (saveCursor) {
				saveCursor();
			}
			mainScreen = new Screen(buffer.rows(), buffer.foregrounds(), buffer.backgrounds());
			buffer.fill(0, 0, columns, rows, foreground(), background());
		} else {
			buffer.setRows(mainScreen.rows());
			buffer.setColors(mainScreen.foregrounds(), mainScreen.backgrounds());
			mainScreen = null;
			if (saveCursor) {
				restoreCursor();
			}
		}
	}

	private void erase(int column, int row, int count) {
		buffer.fill(column, row, count, 1, foreground(), blankBackground());
	}

	private void eraseLine(int mode) {
		pendingWrap = false;
		switch (mode) {
			case 0 -> erase(x, y, columns - x);
			case 1 -> erase(0, y, x + 1);
			case 2 -> erase(0, y, columns);
			default -> {
			}
		}
	}

	private void eraseDisplay(int mode) {
		pendingWrap = false;
		switch (mode) {
			case 0 -> {
				erase(x, y, columns - x);
				buffer.fill(0, y + 1, columns, rows - y - 1, foreground(), blankBackground());
			}
			case 1 -> {
				buffer.fill(0, 0, columns, y, foreground(), blankBackground());
				erase(0, y, x + 1);
			}
			case 2 -> buffer.fill(0, 0, columns, rows, foreground(), blankBackground());
			case 3 -> history.clear(); // (xterm's E3: the saved lines, not the screen)
			default -> {
			}
		}
	}

	private void insertLines(int n) {
		if (y >= top && y <= bottom) {
			scrollDown(y, bottom, n);
			carriageReturn();
		}
	}

	private void deleteLines(int n) {
		if (y >= top && y <= bottom) {
			scrollUp(y, bottom, n);
			carriageReturn();
		}
	}

	private void insertChars(int n) {
		pendingWrap = false;
		for (int column = columns - 1; column >= x + n; column--) {
			copyCell(column - n, column);
		}
		erase(x, y, Math.min(n, columns - x));
		buffer.mendRow(y); // (a wide character the move split)
	}

	private void deleteChars(int n) {
		pendingWrap = false;
		for (int column = x; column < columns - n; column++) {
			copyCell(column + n, column);
		}
		erase(Math.max(x, columns - n), y, Math.min(n, columns - x));
		buffer.mendRow(y);
	}

	private void copyCell(int from, int to) {
		buffer.copy(from, y, to, y); // (its picture's tile too)
	}

	private void copyRow(int from, int to) {
		for (int column = 0; column < columns; column++) {
			buffer.copy(column, from, column, to);
		}
	}

	/**
	 * DECCRA: the cells from {@code top,left} to {@code bottom,right} (from 0, inclusive; clipped to
	 * the screen) copied to {@code toTop,toLeft}, as they were before the copy (overlapping is fine);
	 * the cursor stays.
	 */
	private void copyRectangle(int top, int left, int bottom, int right, int toTop, int toLeft) {
		bottom = Math.min(bottom, rows - 1);
		right = Math.min(right, columns - 1);
		int height = Math.min(bottom - top + 1, rows - toTop);
		int width = Math.min(right - left + 1, columns - toLeft);
		if (height <= 0 || width <= 0 || top < 0 || left < 0) {
			return;
		}
		boolean down = toTop > top;
		boolean rightward = toLeft > left;
		for (int i = 0; i < height; i++) {
			int row = down ? height - 1 - i : i;
			for (int j = 0; j < width; j++) {
				int column = rightward ? width - 1 - j : j;
				buffer.copy(left + column, top + row, toLeft + column, toTop + row);
			}
			buffer.mend(toLeft, toTop + row);
			buffer.mend(toLeft + width - 1, toTop + row);
		}
	}

	/** DECFRA: the cells from {@code top,left} to {@code bottom,right} filled with {@code c} (a wide one in pairs of cells), in the current colors. */
	private void fillRectangle(int c, int top, int left, int bottom, int right) {
		bottom = Math.min(bottom, rows - 1);
		right = Math.min(right, columns - 1);
		int width = CharWidth.of(c);
		if (c < ' ' || c == 0x7F || c >= 0x80 && c < 0xA0 || width == 0 || top < 0 || left < 0) {
			return; // (DECFRA takes printable characters only)
		}
		for (int row = top; row <= bottom; row++) {
			for (int column = left; column <= right; column++) {
				if (width == 2 && column < right) {
					buffer.set(column, row, c, foreground(), background());
					buffer.set(++column, row, TextBuffer.WIDE, foreground(), background());
				} else {
					buffer.set(column, row, width == 2 ? ' ' : c, foreground(), background());
				}
			}
			buffer.mend(left, row);
			buffer.mend(right, row);
		}
	}

	/** Moves rows {@code first..last} up by {@code n}, blanking at the bottom. */
	private void scrollUp(int first, int last, int n) {
		n = Math.min(n, last - first + 1);
		if (first == 0 && last == rows - 1 && mainScreen == null) {
			for (int row = 0; row < n; row++) {
				history.scrolledOff(buffer.row(row), buffer.rowForegrounds(row), buffer.rowBackgrounds(row));
			}
		}
		for (int row = first; row <= last - n; row++) {
			copyRow(row + n, row);
		}
		buffer.fill(0, last - n + 1, columns, n, foreground(), blankBackground());
	}

	/** Moves rows {@code first..last} down by {@code n}, blanking at the top. */
	private void scrollDown(int first, int last, int n) {
		n = Math.min(n, last - first + 1);
		for (int row = last; row >= first + n; row--) {
			copyRow(row - n, row);
		}
		buffer.fill(0, first, columns, n, foreground(), blankBackground());
	}

	// ---- colors ---------------------------------------------------------------------------

	private void resetAttributes() {
		fgIndex = -1;
		fgRgb = TextBuffer.DEFAULT_FOREGROUND;
		bgIndex = -1;
		bgRgb = TextBuffer.DEFAULT_BACKGROUND;
		bold = false;
		reverse = false;
	}

	private int baseForeground() {
		if (fgIndex >= 0) {
			return PALETTE[bold && fgIndex < 8 ? fgIndex + 8 : fgIndex];
		}
		return fgRgb;
	}

	private int baseBackground() {
		return bgIndex >= 0 ? PALETTE[bgIndex] : bgRgb;
	}

	private int foreground() {
		if (depth == ColorDepth.MONOCHROME) {
			// A VT102's: no colors, but its intensities: bold brighter, reverse video dark on lit.
			return reverse ? TextBuffer.DEFAULT_BACKGROUND : intensity();
		}
		return depth.show(reverse ? baseBackground() : baseForeground(), TextBuffer.DEFAULT_FOREGROUND) | (bold ? TextBuffer.BOLD : 0);
	}

	private int background() {
		if (depth == ColorDepth.MONOCHROME) {
			return reverse ? intensity() : TextBuffer.DEFAULT_BACKGROUND;
		}
		return depth.show(reverse ? baseForeground() : baseBackground(), TextBuffer.DEFAULT_BACKGROUND);
	}

	/** A monochrome terminal's lit text: full for bold, a little less otherwise. */
	private int intensity() {
		return bold ? TextBuffer.DEFAULT_FOREGROUND : ColorDepth.NORMAL_INTENSITY;
	}

	/** Erased cells take the background without reverse video's swap, as xterm does. */
	private int blankBackground() {
		return depth.show(baseBackground(), TextBuffer.DEFAULT_BACKGROUND);
	}

	/** The nearest of the 16 basic colors to {@code rgb}. */
	static int nearestBasic(int rgb) {
		int best = PALETTE[0];
		long bestDistance = Long.MAX_VALUE;
		for (int color : PALETTE) {
			long dr = (color >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
			long dg = (color >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
			long db = (color & 0xFF) - (rgb & 0xFF);
			long distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db; // weighted, roughly as eyes see it
			if (distance < bestDistance) {
				bestDistance = distance;
				best = color;
			}
		}
		return best;
	}

	private void sgr(List<Integer> args) {
		for (int i = 0; i < args.size(); i++) {
			int code = Math.max(0, args.get(i));
			switch (code) {
				case 0 -> resetAttributes();
				case 1 -> bold = true;
				case 22 -> bold = false;
				case 7 -> reverse = true;
				case 27 -> reverse = false;
				case 39 -> {
					fgIndex = -1;
					fgRgb = TextBuffer.DEFAULT_FOREGROUND;
				}
				case 49 -> {
					bgIndex = -1;
					bgRgb = TextBuffer.DEFAULT_BACKGROUND;
				}
				case 38, 48 -> {
					int[] color = extendedColor(args, i);
					if (color != null) {
						if (code == 38) {
							fgIndex = color[0];
							fgRgb = color[1];
						} else {
							bgIndex = color[0];
							bgRgb = color[1];
						}
					}
					i += color == null ? args.size() : color[2];
				}
				default -> {
					if (code >= 30 && code <= 37) {
						fgIndex = code - 30;
					} else if (code >= 90 && code <= 97) {
						fgIndex = code - 90 + 8;
					} else if (code >= 40 && code <= 47) {
						bgIndex = code - 40;
					} else if (code >= 100 && code <= 107) {
						bgIndex = code - 100 + 8;
					}
					// others (italic, underline, blink...): ignored
				}
			}
		}
	}

	/** {@code 38;5;n} or {@code 38;2;r;g;b} at {@code i}: {palette index or -1, rgb, arguments used}, or null. */
	private static int[] extendedColor(List<Integer> args, int i) {
		int kind = i + 1 < args.size() ? args.get(i + 1) : -1;
		if (kind == 5 && i + 2 < args.size()) {
			int n = Math.max(0, Math.min(255, args.get(i + 2)));
			return n < 16 ? new int[] {n, PALETTE[n], 2} : new int[] {-1, color256(n), 2};
		}
		if (kind == 2 && i + 4 < args.size()) {
			int r = clamp(args.get(i + 2));
			int g = clamp(args.get(i + 3));
			int b = clamp(args.get(i + 4));
			return new int[] {-1, r << 16 | g << 8 | b, 4};
		}
		return null;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** xterm's 256 colors past the first 16: a 6x6x6 cube, then 24 grays. */
	static int color256(int n) {
		if (n >= 232) {
			int gray = 8 + (n - 232) * 10;
			return gray << 16 | gray << 8 | gray;
		}
		int cube = n - 16;
		int[] levels = {0, 95, 135, 175, 215, 255};
		return levels[cube / 36] << 16 | levels[cube / 6 % 6] << 8 | levels[cube % 6];
	}

	// ---- saving and redrawing -------------------------------------------------------------

	/** Where DECSDM is kept in {@link #state()}'s flags (past the keyboard's). */
	private static final int SIXEL_DISPLAY_BIT = 20;
	/** And bracketed paste. */
	private static final int BRACKETED_PASTE_BIT = 21;

	/** And the mouse's tracking mode (3 bits: its place in MOUSE_MODES) and SGR encoding. */
	private static final int MOUSE_MODE_BIT = 22;
	private static final int MOUSE_MODE_MASK = 7;
	private static final int MOUSE_SGR_BIT = 25;

	/** The mouse tracking a program asked for: {@link #MOUSE_OFF}, {@link #MOUSE_X10}, {@link #MOUSE_NORMAL}, {@link #MOUSE_BUTTON_EVENT} or {@link #MOUSE_ANY_EVENT}. */
	public int mouseTracking() {
		return mouseTracking;
	}

	/** Whether mouse reports come in SGR's encoding (mode 1006). */
	public boolean mouseSgr() {
		return mouseSgr;
	}

	/** Whether a paste comes wrapped (bracketed paste, xterm's mode 2004). */
	public boolean bracketedPaste() {
		return bracketedPaste;
	}

	/** The size of {@link #state()}. */
	private static final int STATE = 23;
	/** Where the saved cursor starts in {@link #state()}, and how long it is. */
	private static final int SAVED_AT = 14;
	private static final int SAVED_SIZE = 9;

	/**
	 * Everything about it but the cells and {@link #pendingParams}, for saving and for a copy
	 * elsewhere to carry on exactly where this one is: the cursor, colors, modes, scroll region,
	 * the saved cursor, and how far it is through an escape sequence. With the cells, a decoder
	 * restored from this decodes what comes next exactly as this one does.
	 */
	public int[] state() {
		int flags = (bold ? 1 : 0) | (reverse ? 2 : 0) | (newLineMode ? 4 : 0) | (autowrap ? 8 : 0)
			| (cursorVisible ? 16 : 0) | (pendingWrap ? 32 : 0) | (stringEscape ? 64 : 0) | (cursorStyle << 8)
			| (cursorKeysApplication ? 1 << 11 : 0) | (keyboardFlags << 12) | (sixelDisplayMode ? 1 << SIXEL_DISPLAY_BIT : 0)
			| (bracketedPaste ? 1 << BRACKETED_PASTE_BIT : 0) | mouseModeIndex() << MOUSE_MODE_BIT | (mouseSgr ? 1 << MOUSE_SGR_BIT : 0);
		int[] state = new int[STATE];
		// (a picture half decoded isn't kept: the rest of it is read and ignored)
		Parse saving = parse == Parse.DCS || parse == Parse.SIXEL ? Parse.STRING : parse;
		int utf8 = utf8Left << 28 | utf8Length << 24 | utf8Code;
		int[] registers = {x, y, fgIndex, fgRgb, bgIndex, bgRgb, flags, top, bottom, saving.ordinal(), saved != null ? 1 : 0,
			packedCharsets(), designating, utf8};
		System.arraycopy(registers, 0, state, 0, registers.length);
		if (saved != null) {
			System.arraycopy(saved, 0, state, registers.length, saved.length);
		}
		return state;
	}

	private int mouseModeIndex() {
		for (int i = 0; i < MOUSE_MODES.length; i++) {
			if (MOUSE_MODES[i] == mouseTracking) {
				return i;
			}
		}
		return 0;
	}

	/** The parameters of an escape sequence it's in the middle of, for saving with {@link #state()}. */
	public String pendingParams() {
		return params.toString();
	}

	/** The main screen while the alternate one shows (it comes back when the alternate one ends), or null. */
	public Screen mainScreen() {
		return mainScreen;
	}

	/** Restores {@link #state()}, {@link #pendingParams()} and {@link #mainScreen()}; a state that doesn't fit is ignored. */
	public void restore(int[] state, String pendingParams, Screen main) {
		if (state.length != STATE) {
			return;
		}
		moveTo(state[0], state[1]);
		fgIndex = state[2] >= -1 && state[2] < 16 ? state[2] : -1;
		fgRgb = state[3];
		bgIndex = state[4] >= -1 && state[4] < 16 ? state[4] : -1;
		bgRgb = state[5];
		int flags = state[6];
		bold = (flags & 1) != 0;
		reverse = (flags & 2) != 0;
		newLineMode = (flags & 4) != 0;
		autowrap = (flags & 8) != 0;
		cursorVisible = (flags & 16) != 0;
		pendingWrap = (flags & 32) != 0;
		stringEscape = (flags & 64) != 0;
		cursorStyle = Math.min((flags >> 8) & 7, 6);
		cursorKeysApplication = (flags & (1 << 11)) != 0;
		keyboardFlags = (flags >> 12) & KEYBOARD_FLAGS;
		sixelDisplayMode = (flags & 1 << SIXEL_DISPLAY_BIT) != 0;
		bracketedPaste = (flags & 1 << BRACKETED_PASTE_BIT) != 0;
		int mouse = flags >> MOUSE_MODE_BIT & MOUSE_MODE_MASK;
		mouseTracking = mouse < MOUSE_MODES.length ? MOUSE_MODES[mouse] : MOUSE_OFF;
		mouseSgr = (flags & 1 << MOUSE_SGR_BIT) != 0;
		if (state[7] >= 0 && state[7] < state[8] && state[8] < rows) {
			top = state[7];
			bottom = state[8];
		}
		parse = state[9] >= 0 && state[9] < Parse.values().length ? Parse.values()[state[9]] : Parse.GROUND;
		unpackCharsets(state[11]);
		designating = Math.clamp(state[12], 0, charsets.length);
		utf8Left = state[13] >>> 28 & 7;
		utf8Length = Math.clamp(state[13] >>> 24 & 0xF, 0, UTF8_MINIMUM.length - 1);
		utf8Code = state[13] & 0xFFFFFF;
		saved = state[10] != 0 ? java.util.Arrays.copyOfRange(state, SAVED_AT, SAVED_AT + SAVED_SIZE) : null;
		params.setLength(0);
		params.append(pendingParams);
		mainScreen = main;
	}

	/**
	 * Escape sequences that paint what this decoder shows onto another terminal, as tmux or screen
	 * redraws a terminal that attaches: every cell in its colors, then the cursor and the current
	 * colors. Sent raw (no newline translation).
	 */
	public String redraw() {
		StringBuilder out = new StringBuilder("\u001B[0m\u001B[?7l\u001B[2J");
		int lastFg = Integer.MIN_VALUE;
		int lastBg = Integer.MIN_VALUE;
		boolean lastBold = false;
		for (int row = 0; row < rows; row++) {
			out.append("\u001B[").append(row + 1).append(";1H");
			for (int column = 0; column < columns; column++) {
				int fg = buffer.foreground(column, row);
				int bg = buffer.background(column, row);
				if (fg != lastFg || bg != lastBg) {
					boolean boldNow = TextBuffer.bold(fg);
					if (boldNow != lastBold) {
						out.append(boldNow ? "\u001B[1m" : "\u001B[22m");
						lastBold = boldNow;
					}
					out.append(rgb(38, TextBuffer.color(fg))).append(rgb(48, bg));
					lastFg = fg;
					lastBg = bg;
				}
				int c = buffer.get(column, row);
				if (c != TextBuffer.WIDE) {
					String glyph = Character.toString(c) + buffer.marks(column, row);
					out.append(new String(glyph.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.ISO_8859_1));
				}
			}
		}
		// Its pictures, a cell's tile a sixel each, drawn where they are (DECSDM: no scrolling, the cursor stays).
		boolean pictures = false;
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int[] tile = buffer.tile(column, row);
				if (tile != null) {
					out.append(pictures ? "" : "\u001B[?80h").append("\u001B[").append(row + 1).append(';').append(column + 1).append('H')
						.append(Sixel.encode(tile, TextBuffer.TILE_WIDTH));
					pictures = true;
				}
			}
		}
		out.append(pictures && !sixelDisplayMode ? "\u001B[?80l" : "");
		out.append(autowrap ? "\u001B[?7h" : "");
		out.append("\u001B[").append(top + 1).append(';').append(bottom + 1).append('r');
		out.append("\u001B[").append(y + 1).append(';').append(x + 1).append('H');
		out.append(cursorVisible ? "\u001B[?25h" : "\u001B[?25l");
		out.append(newLineMode ? "\u001B[20h" : "\u001B[20l");
		out.append("\u001B[0m").append(attributes());
		return out.toString();
	}

	/** SGR for the current colors. */
	private String attributes() {
		StringBuilder out = new StringBuilder();
		if (bold) {
			out.append("\u001B[1m");
		}
		if (reverse) {
			out.append("\u001B[7m");
		}
		out.append(fgIndex >= 0 ? "\u001B[38;5;" + fgIndex + "m" : rgb(38, fgRgb));
		out.append(bgIndex >= 0 ? "\u001B[48;5;" + bgIndex + "m" : rgb(48, bgRgb));
		return out.toString();
	}

	private static String rgb(int code, int color) {
		return "\u001B[" + code + ";2;" + (color >> 16 & 0xFF) + ";" + (color >> 8 & 0xFF) + ";" + (color & 0xFF) + "m";
	}
}
