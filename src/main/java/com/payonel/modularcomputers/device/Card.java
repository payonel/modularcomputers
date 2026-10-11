package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** An expansion card, in a computer's expansion slot or in an expansion hub. None work yet; they're on the bus. */
public final class Card implements Device {
	private final CardKind kind;

	public Card(CardKind kind) {
		this.kind = kind;
	}

	public CardKind kind() {
		return kind;
	}

	@Override
	public DeviceType type() {
		return kind.type();
	}
}
