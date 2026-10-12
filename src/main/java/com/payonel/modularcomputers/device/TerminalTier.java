package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.display.ColorDepth;

/**
 * What a terminal tier shows (user design, 2026-10-11). Every tier is the same VT decoder; they
 * differ in colors, as real terminals did: tier 1 is text only (a VT100), tier 2 has the 16 colors
 * (a VT220-class color terminal), tier 3 every color (pixel graphics are planned for it). Each
 * answers Device Attributes ({@code ESC[c}) as its kind, so a program can tell what it's talking to.
 */
public enum TerminalTier {
	TIER_1(ColorDepth.MONOCHROME, "\u001B[?1;0c"),
	TIER_2(ColorDepth.SIXTEEN, "\u001B[?62;22c"),
	TIER_3(ColorDepth.TRUE_COLOR, "\u001B[?63;22c");

	private final ColorDepth depth;
	private final String attributes;

	TerminalTier(ColorDepth depth, String attributes) {
		this.depth = depth;
		this.attributes = attributes;
	}

	public ColorDepth depth() {
		return depth;
	}

	/** Its answer to Device Attributes. */
	public String attributes() {
		return attributes;
	}
}
