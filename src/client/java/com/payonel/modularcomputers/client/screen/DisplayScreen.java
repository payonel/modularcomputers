package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.client.render.TextGrid;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.Viewport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Shows a screen block's display in the player's view. It is sized from the screen's resolution
 * as soon as it opens (no placeholder box) and fills most of the window. It shows the same
 * text as the screen block's face; an off computer shows black.
 */
public final class DisplayScreen extends Screen {
	/** Share of the window, in each direction, that the display may fill. */
	public static final double FILL = 0.9;
	private static final int BLACK = 0xFF000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final BlockPos pos;
	private final Resolution resolution;
	private Viewport viewport = new Viewport(0, 0, 0, 0);

	public DisplayScreen(BlockPos pos, Resolution resolution) {
		super(Component.translatable("gui.modularcomputers.display"));
		this.pos = pos;
		this.resolution = resolution;
	}

	public BlockPos pos() {
		return pos;
	}

	public Viewport viewport() {
		return viewport;
	}

	@Override
	protected void init() {
		viewport = Viewport.fit(width, height, resolution.aspectRatio(), FILL);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Dim the world without blurring it.
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), BLACK);
		if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer) {
			extractText(graphics, computer.machine().display());
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void extractText(GuiGraphicsExtractor graphics, TextBuffer display) {
		if (display.isBlank()) {
			return;
		}
		TextGrid grid = TextGrid.of(viewport.width(), viewport.height(), resolution, font);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(viewport.x(), viewport.y());
		pose.scale(grid.fontScale(), grid.fontScale());
		for (int row = 0; row < resolution.rows(); row++) {
			for (int column = 0; column < resolution.columns(); column++) {
				char c = display.get(column, row);
				if (c != ' ') {
					graphics.text(font, String.valueOf(c), (int) grid.x(column, c, font), (int) grid.y(row), TEXT_COLOR, false);
				}
			}
		}
		pose.popMatrix();
	}

	@Override
	public void tick() {
		// Close when the screen block is gone or the player walked away.
		var player = minecraft.player;
		if (player == null || !(player.level().getBlockEntity(pos) instanceof AllInOneBlockEntity)
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
