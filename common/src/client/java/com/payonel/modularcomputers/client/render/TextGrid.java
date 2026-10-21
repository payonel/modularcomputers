package com.payonel.modularcomputers.client.render;

import com.payonel.modularcomputers.display.Resolution;
import net.minecraft.client.gui.Font;

/**
 * Lays a text buffer's cells over a rectangle and scales Minecraft's font to fit one glyph per
 * cell. A bitmap font that matches the cell size will replace Minecraft's font later.
 */
public record TextGrid(float cellWidth, float cellHeight, float fontScale) {
	/** Horizontal advance of a typical Minecraft font glyph, in font units. */
	private static final float GLYPH_ADVANCE = 6;

	public static TextGrid of(float width, float height, Resolution resolution, Font font) {
		float cellWidth = width / resolution.columns();
		float cellHeight = height / resolution.rows();
		float fontScale = Math.min(cellWidth / GLYPH_ADVANCE, cellHeight / font.lineHeight);
		return new TextGrid(cellWidth, cellHeight, fontScale);
	}

	/**
	 * Where to draw {@code c} so it sits centered in its cell, in font units (after scaling by
	 * {@link #fontScale}). Minecraft's font is proportional; centering keeps narrow glyphs
	 * like {@code l} from leaving gaps.
	 */
	public float x(int column, char c, Font font) {
		float glyphWidth = font.width(String.valueOf(c)) - 1; // width includes 1 unit of spacing
		return column * cellWidth / fontScale + (cellWidth / fontScale - glyphWidth) / 2;
	}

	/** Top edge of a cell, in font units. */
	public float y(int row) {
		return row * cellHeight / fontScale;
	}
}
