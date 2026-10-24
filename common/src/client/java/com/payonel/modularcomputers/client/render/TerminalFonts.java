package com.payonel.modularcomputers.client.render;

import com.payonel.modularcomputers.device.TerminalTier;
import net.minecraft.network.chat.FontDescription;
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

	/** The style a tier's text is drawn in (bold added per cell). */
	public static Style of(TerminalTier tier) {
		return tier == TerminalTier.TIER_3 ? Style.EMPTY : FINE;
	}
}
