package com.payonel.modularcomputers.display;

/**
 * A list of {@code total} rows shown {@code visible} at a time. It scrolls only when the rows
 * don't fit; the scrollbar's thumb is as long as the visible share of the list.
 */
public record ScrollWindow(int total, int visible) {
	private static final int MIN_THUMB = 6;

	public boolean scrolls() {
		return total > visible;
	}

	public int maxOffset() {
		return Math.max(0, total - visible);
	}

	/** Keeps a scroll offset (the first visible row) in range. */
	public int clamp(int offset) {
		return Math.max(0, Math.min(maxOffset(), offset));
	}

	/** How many rows to draw from {@code offset}. */
	public int rowsShown(int offset) {
		return Math.min(visible, total - clamp(offset));
	}

	public int thumbLength(int track) {
		if (!scrolls()) {
			return track;
		}
		return Math.max(MIN_THUMB, track * visible / total);
	}

	/**
	 * The offset that puts the thumb's middle at {@code position} along a track of {@code track}
	 * pixels: clicking or dragging on the track moves it there.
	 */
	public int offsetAt(int position, int track) {
		if (!scrolls()) {
			return 0;
		}
		int room = track - thumbLength(track);
		int start = position - thumbLength(track) / 2;
		return clamp(room <= 0 ? 0 : Math.round((float) start * maxOffset() / room));
	}

	/** The thumb's start within a track of {@code track} pixels. */
	public int thumbStart(int offset, int track) {
		if (!scrolls()) {
			return 0;
		}
		return (track - thumbLength(track)) * clamp(offset) / maxOffset();
	}
}
