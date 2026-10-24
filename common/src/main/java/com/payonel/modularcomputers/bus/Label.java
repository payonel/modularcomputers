package com.payonel.modularcomputers.bus;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where a device's label lives (its item's, the label's one home), read there and, when it can be
 * changed, written there: programs relabel a device through it, as OC's {@code setLabel} did.
 */
public record Label(Supplier<String> read, Consumer<String> write) {
	/** The longest label: a disk label's, roughly (ext2's is 16, FAT's 11; a little more room). */
	public static final int MAX = 32;
	/** No label, and none to give: a device with no item to keep one on. */
	public static final Label NONE = new Label(() -> null, null);

	public String get() {
		return read.get();
	}

	/** Sets it (null: none); false if it can't be set here. */
	public boolean set(String label) {
		if (write == null) {
			return false;
		}
		write.accept(label);
		return true;
	}

	/** Why {@code label} can't be one (too long, or not printable), or null. */
	public static String invalid(String label) {
		if (label.length() > MAX) {
			return "a label is at most " + MAX + " characters";
		} else if (!label.codePoints().allMatch(c -> c >= ' ' && c != 0x7F)) {
			return "a label is printable characters";
		}
		return null;
	}
}
