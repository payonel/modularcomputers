package com.payonel.modularcomputers.display;

/**
 * Where the screen view puts the display, its bezel and its buttons. The bezel (the display
 * plus a {@code border} frame) fills {@code fill} of the area, centered, leaving room on both
 * sides so the button column fits to the right of the bezel without covering it: the power
 * button level with the bezel's top edge, and the view toggle below it.
 */
public record DisplayLayout(Viewport bezel, Viewport display, Viewport powerButton, Viewport viewToggle) {
	public static DisplayLayout of(int areaWidth, int areaHeight, double aspectRatio, double fill, int border, int buttonSize, int gap) {
		int sideRoom = buttonSize + gap;
		int maxWidth = (int) ((areaWidth - 2 * sideRoom) * fill) - 2 * border;
		int maxHeight = (int) (areaHeight * fill) - 2 * border;
		Viewport fitted = Viewport.fit(Math.max(1, maxWidth), Math.max(1, maxHeight), aspectRatio, 1.0);
		Viewport display = new Viewport((areaWidth - fitted.width()) / 2, (areaHeight - fitted.height()) / 2, fitted.width(), fitted.height());
		Viewport bezel = new Viewport(display.x() - border, display.y() - border, display.width() + 2 * border, display.height() + 2 * border);
		Viewport power = new Viewport(bezel.right() + gap, bezel.y(), buttonSize, buttonSize);
		Viewport toggle = new Viewport(power.x(), power.bottom() + gap, buttonSize, buttonSize);
		return new DisplayLayout(bezel, display, power, toggle);
	}
}
