package com.payonel.modularcomputers.client.render;

import com.payonel.modularcomputers.device.TerminalTier;
import net.minecraft.network.chat.FontDescription;
import com.payonel.modularcomputers.display.Resolution;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The font each tier's glass draws text in (user's pick, 2026-10-24): tiers 1 and 2, whose cells
 * are big (50 and 80 columns), Unifont (Minecraft's {@code uniform}: 8 by 16 glyphs, twice the
 * default font's detail, so it doesn't look blocky magnified); tier 3, whose cells are small, the
 * default font, crisp at that size.
 */
public final class TerminalFonts {
	/** Minecraft's Unifont. */
	private static final FontDescription UNIFORM = new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));
	private static final Style FINE = Style.EMPTY.withFont(UNIFORM);

	private TerminalFonts() {
	}

	/** A texel's size in font units: the default font's glyphs are drawn at 1 a texel, Unifont's at half (16 high in a line of 9 or so). */
	private static final float DEFAULT_TEXEL = 1;
	private static final float UNIFONT_TEXEL = 0.5f;

	/** A view's text: the font it's drawn in, and its grid. */
	public record ViewText(Style style, TextGrid grid) {
	}

	/** The style a tier's text is drawn in on a block's face (bold added per cell): tier 3's cells are small there. */
	public static Style of(TerminalTier tier) {
		return tier == TerminalTier.TIER_3 ? Style.EMPTY : FINE;
	}

	/**
	 * A tier's text in a screen's view, crisp (the user's: tier 3's was blocky, its default font
	 * magnified by a fraction, its texels one pixel here and two there): drawn at a whole number of
	 * screen pixels a texel ({@code guiScale}: the window's pixels a GUI unit), which glyphs placed
	 * on whole font units keep. Tiers 1 and 2 in Unifont; tier 3 in Unifont too when its texels can
	 * be a pixel or more (its detail is then on screen), else the default font, as small as it
	 * can be crisp; cells too small even for that, as they fit.
	 */
	public static ViewText forView(TerminalTier tier, float width, float height, Resolution resolution, Font font, double guiScale) {
		TextGrid fine = TextGrid.of(width, height, resolution, font, FINE);
		if (tier != TerminalTier.TIER_3 || fine.fontScale() * UNIFONT_TEXEL * guiScale >= 1) {
			return new ViewText(FINE, snapped(fine, UNIFONT_TEXEL, guiScale));
		}
		return new ViewText(Style.EMPTY, snapped(TextGrid.of(width, height, resolution, font, Style.EMPTY), DEFAULT_TEXEL, guiScale));
	}

	/** {@code grid} with its font scaled down to a whole number of pixels a texel, when it's a pixel or more. */
	private static TextGrid snapped(TextGrid grid, float texel, double guiScale) {
		double pixels = grid.fontScale() * texel * guiScale;
		if (pixels < 1) {
			return grid;
		}
		return new TextGrid(grid.cellWidth(), grid.cellHeight(), (float) (Math.floor(pixels) / (texel * guiScale)));
	}
}
