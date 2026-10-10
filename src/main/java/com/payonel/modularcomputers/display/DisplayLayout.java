package com.payonel.modularcomputers.display;

/**
 * Where the screen view puts the display and its buttons. The display fills {@code fill} of
 * the area, centered, leaving room on both sides so the button column fits to the right of
 * the display without covering it: the power button level with the display's top edge, and
 * the view toggle below it.
 */
public record DisplayLayout(Viewport display, Viewport powerButton, Viewport viewToggle) {
	public static DisplayLayout of(int areaWidth, int areaHeight, double aspectRatio, double fill, int buttonSize, int gap) {
		int sideRoom = buttonSize + gap;
		Viewport fitted = Viewport.fit(areaWidth - 2 * sideRoom, areaHeight, aspectRatio, fill);
		Viewport display = new Viewport(fitted.x() + sideRoom, fitted.y(), fitted.width(), fitted.height());
		Viewport power = new Viewport(display.right() + gap, display.y(), buttonSize, buttonSize);
		Viewport toggle = new Viewport(power.x(), power.bottom() + gap, buttonSize, buttonSize);
		return new DisplayLayout(display, power, toggle);
	}
}
