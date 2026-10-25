package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.client.render.TextGrid;
import com.payonel.modularcomputers.display.DisplayLayout;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.ScrollTerminalPayload;
import com.payonel.modularcomputers.network.TerminalInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.client.ModNetworkClient;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.payonel.modularcomputers.network.HostRef;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shows a screen's display in the player's view: the all-in-one's or a screen block's. It is
 * sized from the screen's resolution as soon as it opens (no placeholder box) and fills most of
 * the window. It shows the same text as the block's face; an off computer shows black.
 * <p>
 * The display sits in a bezel like the screen surround on the computer's front. Right of the
 * bezel, from its top edge down: the power button, which looks like the one on the computer's
 * back, and the toggle to the case view (see {@link ComputerButtons}). A screen block has no
 * power button (that belongs to the computer's case); its toggle opens its radio slot.
 */
public final class DisplayScreen extends Screen {
	/** Share of the window, in each direction, that the display may fill. */
	public static final double FILL = 0.9;
	private static final int BLACK = 0xFF000000;
	/** The frame around the display, in GUI units, in the computer case's colors. */
	private static final int BEZEL = 6;
	/**
	 * The bezel's colors by era (tier), matching the blocks (scripts/art): body, lit edge, shade,
	 * recess shadow. Tier 1 beige, tier 2 platinum, tier 3 graphite.
	 */
	private record Bezel(int body, int light, int shade, int dark) {
		/** A bot golem's head: its iron (scripts/art/bot_golem_textures.py's). */
		static final Bezel GOLEM = new Bezel(0xFFC9C2B5, 0xFFE4DED2, 0xFF8A8377, 0xFF686258);

		static Bezel of(int tier) {
			return switch (tier) {
				case 2 -> new Bezel(0xFFC9C4B8, 0xFFDEDAD0, 0xFFA59F95, 0xFF8D887C);
				case 3 -> new Bezel(0xFF2A2C30, 0xFF40434A, 0xFF18191C, 0xFF141518);
				default -> new Bezel(0xFFCEC5A8, 0xFFE2DAC0, 0xFFACA387, 0xFF948B71);
			};
		}
	}

	/** The keys held down in this view (a press seen, no release yet): a press while held is a repeat. */
	private final java.util.Set<Integer> held = new java.util.HashSet<>();
	/** Lines a notch of the mouse wheel scrolls the terminal's glass. */
	private static final int WHEEL_LINES = 3;
	/** Whose terminal it shows: a block or an entity (a golem). */
	private final HostRef pos;
	/** The terminal's size as the layout was made for it: a program may change it while it shows. */
	private Resolution resolution;
	/** The computer whose power button shows, if any. */
	private final Optional<HostRef> computer;
	private final boolean caseView;
	private static final Viewport NONE = new Viewport(0, 0, 0, 0);
	private DisplayLayout layout = new DisplayLayout(NONE, NONE, NONE, NONE);
	private boolean powerHeld;

	public DisplayScreen(OpenDisplayPayload payload) {
		super(Component.translatable("gui.modularcomputers.display"));
		this.pos = payload.host();
		this.resolution = payload.resolution();
		this.computer = payload.computer();
		this.caseView = payload.caseView();
	}

	public HostRef host() {
		return pos;
	}

	/** The terminal's size, as the glass is laid out for it now. */
	public Resolution resolution() {
		return resolution;
	}

	public Viewport viewport() {
		return layout.display();
	}

	/** The display plus its frame. */
	public Viewport bezel() {
		return layout.bezel();
	}

	/** The power button, or an empty area when there's no computer to power. */
	public Viewport powerButton() {
		return computer.isPresent() ? layout.powerButton() : NONE;
	}

	/** The toggle to the case view (at the top when there's no power button), or an empty area when there's no case. */
	public Viewport viewToggle() {
		if (!caseView) {
			return NONE;
		}
		return computer.isPresent() ? layout.viewToggle() : layout.powerButton();
	}

	@Override
	protected void init() {
		layout = layout();
		// The terminal takes typing: SDL only sends typed characters while text input is on, as
		// for a focused text box.
		minecraft.onTextInputFocusChange(this, true);
	}

	private DisplayLayout layout() {
		return DisplayLayout.of(width, height, resolution.aspectRatio(), FILL, BEZEL, ComputerButtons.SIZE, ComputerButtons.GAP);
	}

	@Override
	public void removed() {
		minecraft.onTextInputFocusChange(this, false);
		super.removed();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		var hit = ComputerButtons.click(event, powerButton(), viewToggle(), ComputerButtons.Target.CASE, pos);
		powerHeld = hit == ComputerButtons.Hit.POWER;
		return hit != ComputerButtons.Hit.NONE || super.mouseClicked(event, doubleClick);
	}

	/**
	 * Typing goes to this terminal, and only this one: every key, its press, repeats (a press while
	 * held) and release ({@link #keyReleased}), with the modifiers, and the terminal sends what its
	 * mode says (by default a VT100's: the characters typed come through {@link #charTyped}). Escape
	 * still closes the view, Shift+PageUp/PageDown scroll the glass (the terminal's own), and
	 * Ctrl+Shift+V or Shift+Insert paste the clipboard (Linux terminals' keys: Ctrl+V stays ^V).
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return super.keyPressed(event);
		}
		if (event.hasShiftDown() && (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN)) {
			// the terminal's own: its glass scrolls over its scrollback, a screen at a time; nothing goes down the line
			int page = resolution.rows();
			ModNetworkClient.send(new ScrollTerminalPayload(pos, event.key() == InputConstants.KEY_PAGEUP ? page : -page));
			return true;
		}
		if (event.hasShiftDown() && (event.hasControlDown() && event.key() == InputConstants.KEY_V || event.key() == InputConstants.KEY_INSERT)) {
			String clipboard = minecraft.keyboardHandler.getClipboard();
			if (!clipboard.isEmpty()) {
				ModNetworkClient.send(new com.payonel.modularcomputers.network.PasteTerminalPayload(pos,
					clipboard.length() > com.payonel.modularcomputers.device.Terminal.MAX_PASTE
						? clipboard.substring(0, com.payonel.modularcomputers.device.Terminal.MAX_PASTE) : clipboard));
			}
			return true;
		}
		int code = TerminalKeys.code(event);
		if (code != 0) {
			// every key goes to the terminal (press, or repeat while it's held); it decides what to send
			int kind = held.add(event.key()) ? com.payonel.modularcomputers.device.TerminalKeyboard.PRESS
				: com.payonel.modularcomputers.device.TerminalKeyboard.REPEAT;
			ModNetworkClient.send(new com.payonel.modularcomputers.network.TerminalKeyPayload(pos, code, TerminalKeys.modifiers(event), kind));
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (held.remove(event.key())) {
			int code = TerminalKeys.code(event);
			if (code != 0) {
				ModNetworkClient.send(new com.payonel.modularcomputers.network.TerminalKeyPayload(pos, code, TerminalKeys.modifiers(event),
					com.payonel.modularcomputers.device.TerminalKeyboard.RELEASE));
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter()) {
			send(event.codepointAsString());
		}
		return true;
	}

	/** The wheel scrolls the terminal's glass over its scrollback, as in a terminal emulator. */
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int lines = (int) Math.signum(scrollY) * WHEEL_LINES;
		if (lines != 0) {
			ModNetworkClient.send(new ScrollTerminalPayload(pos, lines));
		}
		return true;
	}

	private void send(String keys) {
		ModNetworkClient.send(new TerminalInputPayload(pos, keys));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		powerHeld = false;
		return super.mouseReleased(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Dim the world without blurring it.
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		DisplayHost host = pos.find(minecraft.level, DisplayHost.class).orElse(null);
		// The bezel is the block's (a case's or the screen's own era); the glass and its glow, the terminal's.
		int tier = host instanceof AllInOneBlockEntity computer ? computer.tier().number()
			: host != null ? host.terminal().tier().number() : 1;
		if (host != null && !host.terminal().buffer().resolution().equals(resolution)) {
			resolution = host.terminal().buffer().resolution(); // a program resized it: the glass takes its new shape
			layout = layout();
		}
		extractBezel(graphics, host instanceof com.payonel.modularcomputers.entity.BotGolem ? Bezel.GOLEM : Bezel.of(tier));
		Viewport display = layout.display();
		var depth = host != null ? host.terminal().tier().depth() : com.payonel.modularcomputers.display.ColorDepth.TRUE_COLOR;
		graphics.fill(display.x(), display.y(), display.right(), display.bottom(), BLACK | depth.glass());
		if (host != null) {
			Viewport area = layout.display();
			var text = com.payonel.modularcomputers.client.render.TerminalFonts.forView(host.terminal().tier(), area.width(), area.height(),
				host.terminal().buffer().resolution(), font, minecraft.getWindow().getGuiScale());
			extractText(graphics, host.terminal().buffer(), host.terminal().shownCursor(), depth, text);
		}
		var machine = computer.flatMap(ref -> ref.find(minecraft.level, com.payonel.modularcomputers.block.BoardHolder.class));
		if (machine.isPresent()) {
			ComputerButtons.extract(graphics, powerButton(), viewToggle(), ComputerButtons.Target.CASE, machine.get().powerLight(), powerHeld);
		} else {
			ComputerButtons.extract(graphics, NONE, viewToggle(), ComputerButtons.Target.CASE, null, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * A frame like the screen surround on the computer's front: beige case with a lit outer
	 * edge, and a recessed inner edge (shadow top and left, light bottom and right).
	 */
	private void extractBezel(GuiGraphicsExtractor graphics, Bezel colors) {
		Viewport b = layout.bezel();
		Viewport d = layout.display();
		graphics.fill(b.x(), b.y(), b.right(), b.bottom(), colors.shade());
		graphics.fill(b.x(), b.y(), b.right() - 1, b.bottom() - 1, colors.light());
		graphics.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, colors.body());
		graphics.fill(d.x() - 1, d.y() - 1, d.right() + 1, d.bottom() + 1, colors.light());
		graphics.fill(d.x() - 1, d.y() - 1, d.right(), d.bottom(), colors.dark());
	}

	private void extractText(GuiGraphicsExtractor graphics, TextBuffer buffer, com.payonel.modularcomputers.device.Terminal.Cursor cursor,
		com.payonel.modularcomputers.display.ColorDepth depth, com.payonel.modularcomputers.client.render.TerminalFonts.ViewText text) {
		Style tierStyle = text.style();
		boolean lit = com.payonel.modularcomputers.client.render.CursorBlink.lit(cursor);
		if (buffer.isBlank() && !lit) {
			return;
		}
		// A block cursor is its cell in reverse video.
		boolean block = lit && cursor.block();
		java.util.function.BiFunction<Integer, Integer, Integer> background = (column, row) ->
			block && column == cursor.column() && row == cursor.row() ? depth.glow(buffer.foreground(column, row)) : depth.glow(buffer.background(column, row));
		java.util.function.BiFunction<Integer, Integer, Integer> foreground = (column, row) ->
			block && column == cursor.column() && row == cursor.row() ? buffer.background(column, row) : depth.glow(buffer.foreground(column, row));
		Resolution resolution = buffer.resolution();
		Viewport area = layout.display();
		TextGrid grid = text.grid();
		// Backgrounds first, in runs of equal color; the display is already black.
		for (int row = 0; row < resolution.rows(); row++) {
			int top = area.y() + Math.round(row * grid.cellHeight());
			int bottom = area.y() + Math.round((row + 1) * grid.cellHeight());
			int start = 0;
			for (int column = 1; column <= resolution.columns(); column++) {
				if (column == resolution.columns() || !background.apply(column, row).equals(background.apply(start, row))) {
					int color = background.apply(start, row);
					if (color != depth.glow(TextBuffer.DEFAULT_BACKGROUND) || (block && row == cursor.row() && start == cursor.column())) {
						graphics.fill(area.x() + Math.round(start * grid.cellWidth()), top,
							area.x() + Math.round(column * grid.cellWidth()), bottom, 0xFF000000 | color);
					}
					start = column;
				}
			}
		}
		// Its pictures (sixel), over the backgrounds, under the text.
		net.minecraft.resources.Identifier pixels = com.payonel.modularcomputers.client.render.PixelLayers.of(buffer);
		if (pixels != null) {
			graphics.blit(pixels, area.x(), area.y(), area.x() + Math.round(resolution.columns() * grid.cellWidth()),
				area.y() + Math.round(resolution.rows() * grid.cellHeight()), 0, 1, 0, 1);
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(area.x(), area.y());
		pose.scale(grid.fontScale(), grid.fontScale());
		for (int row = 0; row < resolution.rows(); row++) {
			for (int column = 0; column < resolution.columns(); column++) {
				char c = buffer.get(column, row);
				if (c != ' ') {
					Style style = tierStyle.withBold(TextBuffer.bold(buffer.foreground(column, row)));
					graphics.text(font, FormattedCharSequence.codepoint(c, style), (int) grid.x(column, c, font, style), (int) grid.y(row),
						0xFF000000 | foreground.apply(column, row), false);
				}
			}
		}
		pose.popMatrix();
		if (lit && !block && cursor.row() < resolution.rows() && cursor.column() < resolution.columns()) {
			// an underline along the cell's bottom, or a bar at its left, in the text's color
			int left = area.x() + Math.round(cursor.column() * grid.cellWidth());
			int top = area.y() + Math.round(cursor.row() * grid.cellHeight());
			int right = area.x() + Math.round((cursor.column() + 1) * grid.cellWidth());
			int bottom = area.y() + Math.round((cursor.row() + 1) * grid.cellHeight());
			int stroke = Math.max(1, Math.round((cursor.underline() ? grid.cellHeight() : grid.cellWidth())
				* com.payonel.modularcomputers.client.render.CursorBlink.STROKE));
			int color = 0xFF000000 | depth.glow(buffer.foreground(cursor.column(), cursor.row()));
			if (cursor.underline()) {
				graphics.fill(left, bottom - stroke, right, bottom, color);
			} else {
				graphics.fill(left, top, left + stroke, bottom, color);
			}
		}
	}

	@Override
	public void tick() {
		// Close when the screen block is gone or the player walked away.
		var player = minecraft.player;
		if (player == null || pos.find(player.level(), DisplayHost.class).isEmpty() || !pos.reachable(player, 1.0)) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		// Computers keep running while someone looks at them.
		return false;
	}
}
