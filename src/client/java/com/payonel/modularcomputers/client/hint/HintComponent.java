package com.payonel.modularcomputers.client.hint;

import com.payonel.modularcomputers.hint.Hint;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a mod item's {@link Hint} in its tooltip, under the name: lines of text, rows of item
 * icons with labels, and a crafting grid. With Shift held it shows the details; it's made
 * afresh every frame, so pressing Shift switches at once.
 */
public final class HintComponent implements ClientTooltipComponent {
	/** For tests, which can't hold the real Shift key: true or false forces the details on or off. */
	public static volatile Boolean detailsOverride;

	private static final int LINE = 10;
	private static final int SLOT = 18;
	private static final int ICON_GAP = 4;
	private static final int ARROW = 22;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FACE = 0xFF8B8B8B;
	private static final int ARROW_COLOR = 0xFFA0A0A0;
	/** How long each of a cell's choices shows, when a tag fits it. */
	private static final long CYCLE_MILLIS = 1000;

	private final List<Hint.Element> elements;

	public HintComponent(ItemStack stack) {
		Boolean override = detailsOverride;
		this.elements = Hint.of(stack, override != null ? override : Minecraft.getInstance().hasShiftDown());
	}

	public List<Hint.Element> elements() {
		return elements;
	}

	@Override
	public int getHeight(Font font) {
		return elements.stream().mapToInt(this::height).sum();
	}

	@Override
	public int getWidth(Font font) {
		return elements.stream().mapToInt(element -> width(element, font)).max().orElse(0);
	}

	private int height(Hint.Element element) {
		return switch (element) {
			case Hint.Text text -> LINE;
			case Hint.Icons icons -> 16 + 2 + LINE + 2;
			case Hint.Crafting crafting -> 3 * SLOT + 2 + (ClientRecipes.of(crafting.item()).isEmpty() ? LINE : 0) + 2;
		};
	}

	private static int width(Hint.Element element, Font font) {
		return switch (element) {
			case Hint.Text text -> font.width(text.text());
			case Hint.Icons icons -> icons.icons().stream().mapToInt(icon -> iconWidth(icon, font) + ICON_GAP).sum() - ICON_GAP;
			case Hint.Crafting crafting -> 3 * SLOT + ARROW + SLOT;
		};
	}

	private static int iconWidth(Hint.Icon icon, Font font) {
		return Math.max(16, font.width(icon.label()));
	}

	@Override
	public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		for (Hint.Element element : elements) {
			switch (element) {
				case Hint.Text text -> graphics.text(font, text.text(), x, y, -1, true);
				case Hint.Icons icons -> {
					int ix = x;
					for (Hint.Icon icon : icons.icons()) {
						int w = iconWidth(icon, font);
						graphics.text(font, icon.label(), ix + (w - font.width(icon.label())) / 2, y + 18, 0xFFA8A8A8, true);
						ix += w + ICON_GAP;
					}
				}
				case Hint.Crafting crafting -> {
					if (ClientRecipes.of(crafting.item()).isEmpty()) {
						Component note = Component.translatable("hint.modularcomputers.no_recipe").withStyle(ChatFormatting.DARK_GRAY);
						graphics.text(font, note, x, y + 3 * SLOT + 2, -1, true);
					}
				}
			}
			y += height(element);
		}
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
		for (Hint.Element element : elements) {
			switch (element) {
				case Hint.Text text -> {
				}
				case Hint.Icons icons -> {
					int ix = x;
					for (Hint.Icon icon : icons.icons()) {
						int w = iconWidth(icon, font);
						graphics.item(icon.stack(), ix + (w - 16) / 2, y);
						ix += w + ICON_GAP;
					}
				}
				case Hint.Crafting crafting -> extractGrid(graphics, crafting, x, y);
			}
			y += height(element);
		}
	}

	/** A 3x3 crafting grid, an arrow and the result, like the crafting table's. */
	private static void extractGrid(GuiGraphicsExtractor graphics, Hint.Crafting crafting, int x, int y) {
		ClientRecipes.Grid grid = ClientRecipes.of(crafting.item()).orElse(null);
		long tick = System.currentTimeMillis() / CYCLE_MILLIS;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				int sx = x + column * SLOT;
				int sy = y + row * SLOT;
				slot(graphics, sx, sy);
				List<ItemStack> choices = grid != null ? grid.cell(column, row) : List.of();
				if (!choices.isEmpty()) {
					graphics.item(choices.get((int) (tick % choices.size())), sx + 1, sy + 1);
				}
			}
		}
		int ax = x + 3 * SLOT + 4;
		int ay = y + SLOT + SLOT / 2;
		graphics.fill(ax, ay - 1, ax + 10, ay + 1, ARROW_COLOR);
		for (int i = 0; i < 4; i++) {
			graphics.fill(ax + 10 + i, ay - 4 + i, ax + 11 + i, ay + 4 - i, ARROW_COLOR);
		}
		int rx = x + 3 * SLOT + ARROW;
		int ry = y + SLOT;
		slot(graphics, rx, ry);
		if (grid != null) {
			graphics.item(new ItemStack(crafting.item()), rx + 1, ry + 1);
		}
	}

	/** An inventory slot: dark top and left, light bottom and right, gray face. */
	private static void slot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_LIGHT);
		graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, SLOT_DARK);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_FACE);
	}
}
