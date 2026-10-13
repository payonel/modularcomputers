package com.payonel.modularcomputers.client.screen;

import com.payonel.modularcomputers.power.ChargeMeter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A small charge meter drawn with fills ({@link ChargeMeter}'s bars and colors): the photomask's. */
public final class ChargeBars {
	public static final int BAR = 2;
	public static final int GAP = 1;
	public static final int HEIGHT = 5;
	public static final int WIDTH = ChargeMeter.SEGMENTS * (BAR + GAP) - GAP + 2;
	private static final int FRAME = 0xFF1E2024;
	private static final int OFF = 0xFF3C3F46;

	private ChargeBars() {
	}

	/** The meter, {@link #WIDTH} by {@link #HEIGHT} at {@code x, y}, for a charge of 0..100. */
	public static void extract(GuiGraphicsExtractor graphics, int x, int y, int percent) {
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, FRAME);
		int lit = ChargeMeter.litBars(percent);
		int color = switch (ChargeMeter.level(lit)) {
			case LOW -> 0xFFD8402E;
			case MEDIUM -> 0xFFE6C23A;
			case HIGH, OFF -> 0xFF4CC94A;
		};
		for (int i = 0; i < ChargeMeter.SEGMENTS; i++) {
			int bx = x + 1 + i * (BAR + GAP);
			graphics.fill(bx, y + 1, bx + BAR, y + HEIGHT - 1, i < lit ? color : OFF);
		}
	}
}
