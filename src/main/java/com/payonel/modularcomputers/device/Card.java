package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** An expansion card, in a computer's expansion slot or in an expansion hub. None work yet; they're on the bus. */
public final class Card implements Device {
	private final CardKind kind;
	/** Where its label is (its item's anvil name), read when asked: no copy of it here. */
	private java.util.function.Supplier<String> label = () -> null;

	public Card(CardKind kind) {
		this.kind = kind;
	}

	public CardKind kind() {
		return kind;
	}

	public void labelFrom(java.util.function.Supplier<String> label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label.get();
	}

	@Override
	public java.util.Map<String, Object> info() {
		return java.util.Map.of("kind", kind.name().toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public DeviceType type() {
		return kind.type();
	}
}
