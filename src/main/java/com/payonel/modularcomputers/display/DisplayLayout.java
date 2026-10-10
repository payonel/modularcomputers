package com.payonel.modularcomputers.display;

/**
 * Where the display GUI puts the display and its power button. The display fills
 * {@code fill} of the area, centered, leaving room on both sides so the power button fits to
 * the right of the display, level with its top edge, without covering it.
 */
public record DisplayLayout(Viewport display, Viewport powerButton) {
	public static DisplayLayout of(int areaWidth, int areaHeight, double aspectRatio, double fill, int buttonSize, int gap) {
		int sideRoom = buttonSize + gap;
		Viewport fitted = Viewport.fit(areaWidth - 2 * sideRoom, areaHeight, aspectRatio, fill);
		Viewport display = new Viewport(fitted.x() + sideRoom, fitted.y(), fitted.width(), fitted.height());
		Viewport button = new Viewport(display.right() + gap, display.y(), buttonSize, buttonSize);
		return new DisplayLayout(display, button);
	}
}
