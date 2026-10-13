package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.display.ColorDepth;

/**
 * What a terminal tier shows (user design, 2026-10-11), each modeled on a real DEC terminal
 * (2026-10-12). Every tier is the same VT decoder; they differ in colors, as real terminals did:
 * tier 1 is a VT102, text only, on green phosphor; tier 2 a VT525, DEC's color text terminal with
 * the 16 ANSI colors; tier 3 every color (sixel graphics are planned for it, as on a VT340). Each
 * answers Device Attributes ({@code ESC[c}) as its model did, so a program can tell what it's
 * talking to: {@code ?6c} a VT102, {@code ?65;...;22c} a VT500-family terminal with ANSI color.
 */
public enum TerminalTier {
	TIER_1(ColorDepth.MONOCHROME, "\u001B[?6c"),
	TIER_2(ColorDepth.SIXTEEN, "\u001B[?65;22c"),
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

	/** 1, 2 or 3. */
	public int number() {
		return ordinal() + 1;
	}
}
