package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.client.render.TextGrid;
import com.payonel.modularcomputers.display.DisplayLayout;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Shows a screen's display in the player's view: the all-in-one's or a screen block's. It is
 * sized from the screen's resolution as soon as it opens (no placeholder box) and fills most of
 * the window. It shows the same text as the block's face; an off computer shows black.
 * <p>
 * The display sits in a bezel like the screen surround on the computer's front. Right of the
 * bezel, from its top edge down: the power button, which looks like the one on the computer's
 * back, and the toggle to the case view (see {@link ComputerButtons}). A screen block has
 * neither: the power button belongs to the computer's case.
 */
public final class DisplayScreen extends Screen {
	/** Share of the window, in each direction, that the display may fill. */
	public static final double FILL = 0.9;
	private static final int BLACK = 0xFF000000;
	/** The frame around the display, in GUI units, in the computer case's colors. */
	private static final int BEZEL = 6;
	private static final int CASE = 0xFFCEC5A8;
	private static final int CASE_LIGHT = 0xFFE2DAC0;
	private static final int CASE_SHADE = 0xFFACA387;
	private static final int CASE_DARK = 0xFF948B71;

	private final BlockPos pos;
	private final Resolution resolution;
	/** The computer whose power button shows, if any. */
	private final Optional<BlockPos> computer;
	private final boolean caseView;
	private static final Viewport NONE = new Viewport(0, 0, 0, 0);
	private DisplayLayout layout = new DisplayLayout(NONE, NONE, NONE, NONE);
	private boolean powerHeld;

	public DisplayScreen(OpenDisplayPayload payload) {
		super(Component.translatable("gui.modularcomputers.display"));
		this.pos = payload.pos();
		this.resolution = payload.resolution();
		this.computer = payload.computer();
		this.caseView = payload.caseView();
	}

	public BlockPos pos() {
		return pos;
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

	/** The toggle to the case view, or an empty area when there's no case. */
	public Viewport viewToggle() {
		return caseView ? layout.viewToggle() : NONE;
	}

	@Override
	protected void init() {
		layout = DisplayLayout.of(width, height, resolution.aspectRatio(), FILL, BEZEL, ComputerButtons.SIZE, ComputerButtons.GAP);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		var hit = ComputerButtons.click(event, powerButton(), viewToggle(), ComputerButtons.Target.CASE, pos);
		powerHeld = hit == ComputerButtons.Hit.POWER;
		return hit != ComputerButtons.Hit.NONE || super.mouseClicked(event, doubleClick);
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
		extractBezel(graphics);
		Viewport display = layout.display();
		graphics.fill(display.x(), display.y(), display.right(), display.bottom(), BLACK);
		if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DisplayHost host) {
			extractText(graphics, host.display().buffer());
		}
		if (minecraft.level != null && computer.isPresent() && minecraft.level.getBlockEntity(computer.get()) instanceof AllInOneBlockEntity machine) {
			ComputerButtons.extract(graphics, powerButton(), viewToggle(), ComputerButtons.Target.CASE, machine.getBlockState(), powerHeld);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * A frame like the screen surround on the computer's front: beige case with a lit outer
	 * edge, and a recessed inner edge (shadow top and left, light bottom and right).
	 */
	private void extractBezel(GuiGraphicsExtractor graphics) {
		Viewport b = layout.bezel();
		Viewport d = layout.display();
		graphics.fill(b.x(), b.y(), b.right(), b.bottom(), CASE_SHADE);
		graphics.fill(b.x(), b.y(), b.right() - 1, b.bottom() - 1, CASE_LIGHT);
		graphics.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, CASE);
		graphics.fill(d.x() - 1, d.y() - 1, d.right() + 1, d.bottom() + 1, CASE_LIGHT);
		graphics.fill(d.x() - 1, d.y() - 1, d.right(), d.bottom(), CASE_DARK);
	}

	private void extractText(GuiGraphicsExtractor graphics, TextBuffer buffer) {
		if (buffer.isBlank()) {
			return;
		}
		Viewport area = layout.display();
		TextGrid grid = TextGrid.of(area.width(), area.height(), resolution, font);
		// Backgrounds first, in runs of equal color; the display is already black.
		for (int row = 0; row < resolution.rows(); row++) {
			int top = area.y() + Math.round(row * grid.cellHeight());
			int bottom = area.y() + Math.round((row + 1) * grid.cellHeight());
			int start = 0;
			for (int column = 1; column <= resolution.columns(); column++) {
				if (column == resolution.columns() || buffer.background(column, row) != buffer.background(start, row)) {
					int background = buffer.background(start, row);
					if (background != TextBuffer.DEFAULT_BACKGROUND) {
						graphics.fill(area.x() + Math.round(start * grid.cellWidth()), top,
							area.x() + Math.round(column * grid.cellWidth()), bottom, 0xFF000000 | background);
					}
					start = column;
				}
			}
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(area.x(), area.y());
		pose.scale(grid.fontScale(), grid.fontScale());
		for (int row = 0; row < resolution.rows(); row++) {
			for (int column = 0; column < resolution.columns(); column++) {
				char c = buffer.get(column, row);
				if (c != ' ') {
					graphics.text(font, String.valueOf(c), (int) grid.x(column, c, font), (int) grid.y(row),
						0xFF000000 | buffer.foreground(column, row), false);
				}
			}
		}
		pose.popMatrix();
	}

	@Override
	public void tick() {
		// Close when the screen block is gone or the player walked away.
		var player = minecraft.player;
		if (player == null || !(player.level().getBlockEntity(pos) instanceof DisplayHost)
			|| !player.isWithinBlockInteractionRange(pos, 1.0)) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		// Computers keep running while someone looks at them.
		return false;
	}
}
