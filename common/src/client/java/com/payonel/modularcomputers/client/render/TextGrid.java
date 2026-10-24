package com.payonel.modularcomputers.client.render;

import com.payonel.modularcomputers.display.Resolution;
import net.minecraft.client.gui.Font;

/**
 * Lays a text buffer's cells over a rectangle and scales Minecraft's font to fit one glyph per
 * cell (its tier's font: {@link TerminalFonts}).
 */
public record TextGrid(float cellWidth, float cellHeight, float fontScale) {
	/** Horizontal advance of a typical Minecraft font glyph, in font units. */
	private static final float GLYPH_ADVANCE = 6;

	public static TextGrid of(float width, float height, Resolution resolution, Font font) {
		return of(width, height, resolution, font, net.minecraft.network.chat.Style.EMPTY);
	}

	/** A grid for text in {@code style}'s font: scaled by its own glyphs' width (a wide one's, M's), so a narrower font's letters fill their cells too. */
	public static TextGrid of(float width, float height, Resolution resolution, Font font, net.minecraft.network.chat.Style style) {
		float advance = style.equals(net.minecraft.network.chat.Style.EMPTY) ? GLYPH_ADVANCE
			: Math.max(1, font.width(net.minecraft.util.FormattedCharSequence.codepoint('M', style)));
		float cellWidth = width / resolution.columns();
		float cellHeight = height / resolution.rows();
		float fontScale = Math.min(cellWidth / advance, cellHeight / font.lineHeight);
		return new TextGrid(cellWidth, cellHeight, fontScale);
	}

	/**
	 * Where to draw {@code c} so it sits centered in its cell, in font units (after scaling by
	 * {@link #fontScale}). Minecraft's font is proportional; centering keeps narrow glyphs
	 * like {@code l} from leaving gaps.
	 */
	public float x(int column, char c, Font font) {
		return x(column, c, font, net.minecraft.network.chat.Style.EMPTY);
	}

	/** {@link #x(int, char, Font)} for a glyph drawn in {@code style}'s font. */
	public float x(int column, char c, Font font, net.minecraft.network.chat.Style style) {
		float glyphWidth = font.width(net.minecraft.util.FormattedCharSequence.codepoint(c, style)) - 1; // width includes 1 unit of spacing
		return column * cellWidth / fontScale + (cellWidth / fontScale - glyphWidth) / 2;
	}

	/** Top edge of a cell, in font units. */
	public float y(int row) {
		return row * cellHeight / fontScale;
	}
}
