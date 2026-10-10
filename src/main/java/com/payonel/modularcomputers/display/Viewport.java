package com.payonel.modularcomputers.display;

/** A rectangle in screen units. */
public record Viewport(int x, int y, int width, int height) {
	/**
	 * The largest rectangle with the given aspect ratio that fits in {@code fill} (0..1] of the
	 * area in both directions, centered in the area.
	 */
	public static Viewport fit(int areaWidth, int areaHeight, double aspectRatio, double fill) {
		int maxWidth = (int) (areaWidth * fill);
		int maxHeight = (int) (areaHeight * fill);
		int width;
		int height;
		if ((double) maxWidth / maxHeight > aspectRatio) {
			height = maxHeight;
			width = (int) Math.round(height * aspectRatio);
		} else {
			width = maxWidth;
			height = (int) Math.round(width / aspectRatio);
		}
		return new Viewport((areaWidth - width) / 2, (areaHeight - height) / 2, width, height);
	}

	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}
}
