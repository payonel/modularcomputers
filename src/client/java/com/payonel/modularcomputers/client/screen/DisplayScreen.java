package com.payonel.modularcomputers.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.client.render.TextGrid;
import com.payonel.modularcomputers.display.DisplayLayout;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.Viewport;
import com.payonel.modularcomputers.network.TogglePowerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shows a screen block's display in the player's view. It is sized from the screen's resolution
 * as soon as it opens (no placeholder box) and fills most of the window. It shows the same
 * text as the screen block's face; an off computer shows black.
 * <p>
 * A power button sits right of the display, level with its top edge, and looks like the one on
 * the computer's back: lit red when off, green when on. (Once screens are separate blocks, it
 * shows only when the screen is connected to a case.)
 */
public final class DisplayScreen extends Screen {
	/** Share of the window, in each direction, that the display may fill. */
	public static final double FILL = 0.9;
	private static final int BLACK = 0xFF000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	/** Off and on button art side by side, 5x5 texels each; drawn at {@link #BUTTON_SCALE}x. */
	private static final Identifier BUTTON_TEXTURE = ModularComputers.id("textures/gui/power_button.png");
	private static final int BUTTON_TEXELS = 5;
	private static final int BUTTON_SCALE = 4;
	private static final int BUTTON_GAP = 4;

	private final BlockPos pos;
	private final Resolution resolution;
	private DisplayLayout layout = new DisplayLayout(new Viewport(0, 0, 0, 0), new Viewport(0, 0, 0, 0));

	public DisplayScreen(BlockPos pos, Resolution resolution) {
		super(Component.translatable("gui.modularcomputers.display"));
		this.pos = pos;
		this.resolution = resolution;
	}

	public BlockPos pos() {
		return pos;
	}

	public Viewport viewport() {
		return layout.display();
	}

	public Viewport powerButton() {
		return layout.powerButton();
	}

	@Override
	protected void init() {
		layout = DisplayLayout.of(width, height, resolution.aspectRatio(), FILL, BUTTON_TEXELS * BUTTON_SCALE, BUTTON_GAP);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Left or right click both press it.
		boolean leftOrRight = event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (leftOrRight && layout.powerButton().contains(event.x(), event.y())) {
			// The server toggles it and the button lights up when the new state syncs back.
			ClientPlayNetworking.send(new TogglePowerPayload(pos));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Dim the world without blurring it.
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Viewport display = layout.display();
		graphics.fill(display.x(), display.y(), display.right(), display.bottom(), BLACK);
		if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer) {
			extractText(graphics, computer.machine().display());
			extractPowerButton(graphics, computer.getBlockState().getValue(AllInOneBlock.RUNNING));
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void extractPowerButton(GuiGraphicsExtractor graphics, boolean running) {
		Viewport button = layout.powerButton();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(button.x(), button.y());
		pose.scale(BUTTON_SCALE, BUTTON_SCALE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, 0, 0, running ? BUTTON_TEXELS : 0, 0,
			BUTTON_TEXELS, BUTTON_TEXELS, 2 * BUTTON_TEXELS, BUTTON_TEXELS);
		pose.popMatrix();
	}

	private void extractText(GuiGraphicsExtractor graphics, TextBuffer display) {
		if (display.isBlank()) {
			return;
		}
		Viewport area = layout.display();
		TextGrid grid = TextGrid.of(area.width(), area.height(), resolution, font);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(area.x(), area.y());
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
