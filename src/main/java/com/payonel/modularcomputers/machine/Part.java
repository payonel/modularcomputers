package com.payonel.modularcomputers.machine;

/**
 * One part installed in a computer. Soldered parts are built in and can never be removed.
 * What {@code capacity} measures depends on the kind (speed, bytes); tiers will set real limits.
 */
public record Part(PartKind kind, boolean soldered, long capacity) {
	public static final long UNLIMITED = Long.MAX_VALUE;

	public static Part soldered(PartKind kind) {
		return new Part(kind, true, UNLIMITED);
	}

	public boolean isUnlimited() {
		return capacity == UNLIMITED;
	}
}
