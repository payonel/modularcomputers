package com.payonel.modularcomputers.display;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A VT100-family terminal's decoder: it turns a stream of text with escape sequences into cells
 * on a {@link TextBuffer}, as a VT102 does, or a modern terminal emulator (xterm and its kind),
 * whose common subset every pty program assumes:
 * <ul>
 * <li>controls: CR, LF (VT, FF), BS, TAB, BEL (ignored);</li>
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
 * <li>full reset (ESC c).</li>
 * </ul>
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

	private enum Parse { GROUND, ESCAPE, CHARSET, CSI, STRING }

	private final TextBuffer buffer;
	private final Consumer<String> reply;
	private final int columns;
	private final int rows;

	private Parse parse = Parse.GROUND;
	private final StringBuilder params = new StringBuilder();
	private boolean stringEscape;

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

	public boolean cursorVisible() {
		return cursorVisible;
	}

	public boolean newLineMode() {
		return newLineMode;
	}

	/** Decodes text onto the buffer. */
	public void write(String text) {
		for (int i = 0; i < text.length(); i++) {
			accept(text.charAt(i));
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
		saved = null;
		mainScreen = null;
		buffer.clear();
	}

	// ---- parsing --------------------------------------------------------------------------

	private void accept(char c) {
		switch (parse) {
			case GROUND -> ground(c);
			case ESCAPE -> escape(c);
			case CHARSET -> parse = Parse.GROUND; // ESC ( B and the like: character sets, ignored
			case CSI -> csi(c);
			case STRING -> string(c);
		}
	}

	private void ground(char c) {
		switch (c) {
			case '\u001B' -> parse = Parse.ESCAPE;
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
			case '\u0007' -> {
				// BEL: no bell yet
			}
			default -> {
				if (c >= ' ') {
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
			case ']', 'P', '_', '^', 'X' -> {
				stringEscape = false;
				parse = Parse.STRING; // OSC, DCS and the like: read to the end, ignored
			}
			case '(', ')', '*', '+' -> parse = Parse.CHARSET;
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
			String body = raw.replaceAll("[^0-9;:]", "");
			List<Integer> args = new ArrayList<>();
			for (String part : body.split("[;:]", -1)) {
				args.add(part.isEmpty() ? -1 : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(part.length() > 9 ? "999999999" : part)));
			}
			execute(c, privateMode, raw.matches(".*[ -/].*") && !privateMode, args);
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

	private void execute(char command, boolean privateMode, boolean intermediate, List<Integer> args) {
		if (intermediate) {
			if (command == 'q') {
				int style = argOrZero(args, 0);
				cursorStyle = style <= 6 ? style : 0; // DECSCUSR (CSI Ps SP q): the cursor's shape
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
			case 'u' -> restoreCursor();
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
				case 7 -> autowrap = on;
				case 25 -> cursorVisible = on;
				case 47, 1047, 1049 -> alternateScreen(on, mode == 1049);
				default -> {
					// not supported: ignored
				}
			}
		}
	}

	// ---- the screen -----------------------------------------------------------------------

	private void print(char c) {
		if (pendingWrap) {
			pendingWrap = false;
			carriageReturn();
			index();
		}
		buffer.set(x, y, c, foreground(), background());
		if (x < columns - 1) {
			x++;
		} else if (autowrap) {
			pendingWrap = true; // the next character wraps, as on a real VT100
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
		saved = new int[] {x, y, fgIndex, fgRgb, bgIndex, bgRgb, bold ? 1 : 0, reverse ? 1 : 0};
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
	}

	private void deleteChars(int n) {
		pendingWrap = false;
		for (int column = x; column < columns - n; column++) {
			copyCell(column + n, column);
		}
		erase(Math.max(x, columns - n), y, Math.min(n, columns - x));
	}

	private void copyCell(int from, int to) {
		buffer.set(to, y, buffer.get(from, y), buffer.foreground(from, y), buffer.background(from, y));
	}

	private void copyRow(int from, int to) {
		for (int column = 0; column < columns; column++) {
			buffer.set(column, to, buffer.get(column, from), buffer.foreground(column, from), buffer.background(column, from));
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
			return TextBuffer.DEFAULT_FOREGROUND; // text only
		}
		return depth.show(reverse ? baseBackground() : baseForeground(), TextBuffer.DEFAULT_FOREGROUND);
	}

	private int background() {
		if (depth == ColorDepth.MONOCHROME) {
			return TextBuffer.DEFAULT_BACKGROUND; // text only
		}
		return depth.show(reverse ? baseForeground() : baseBackground(), TextBuffer.DEFAULT_BACKGROUND);
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

	/** The size of {@link #state()}. */
	private static final int STATE = 19;

	/**
	 * Everything about it but the cells and {@link #pendingParams}, for saving and for a copy
	 * elsewhere to carry on exactly where this one is: the cursor, colors, modes, scroll region,
	 * the saved cursor, and how far it is through an escape sequence. With the cells, a decoder
	 * restored from this decodes what comes next exactly as this one does.
	 */
	public int[] state() {
		int flags = (bold ? 1 : 0) | (reverse ? 2 : 0) | (newLineMode ? 4 : 0) | (autowrap ? 8 : 0)
			| (cursorVisible ? 16 : 0) | (pendingWrap ? 32 : 0) | (stringEscape ? 64 : 0) | (cursorStyle << 8);
		int[] state = new int[STATE];
		int[] registers = {x, y, fgIndex, fgRgb, bgIndex, bgRgb, flags, top, bottom, parse.ordinal(), saved != null ? 1 : 0};
		System.arraycopy(registers, 0, state, 0, registers.length);
		if (saved != null) {
			System.arraycopy(saved, 0, state, registers.length, saved.length);
		}
		return state;
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
		if (state[7] >= 0 && state[7] < state[8] && state[8] < rows) {
			top = state[7];
			bottom = state[8];
		}
		parse = state[9] >= 0 && state[9] < Parse.values().length ? Parse.values()[state[9]] : Parse.GROUND;
		saved = state[10] != 0 ? java.util.Arrays.copyOfRange(state, 11, 19) : null;
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
		for (int row = 0; row < rows; row++) {
			out.append("\u001B[").append(row + 1).append(";1H");
			for (int column = 0; column < columns; column++) {
				int fg = buffer.foreground(column, row);
				int bg = buffer.background(column, row);
				if (fg != lastFg || bg != lastBg) {
					out.append(rgb(38, fg)).append(rgb(48, bg));
					lastFg = fg;
					lastBg = bg;
				}
				out.append(buffer.get(column, row));
			}
		}
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
