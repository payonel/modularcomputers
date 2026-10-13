package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/**
 * An expansion card, in a computer's expansion slot or in an expansion hub. A card of a kind that
 * works is one of its subclasses ({@link Cards#make}); it works where it's installed (user,
 * 2026-10-11): a redstone card in a hub sets and reads redstone at the hub.
 */
public class Card implements Device {
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

	/** Its block, told when what it saves changes ({@link Device#onChange}). */
	private Runnable changed = () -> {
	};

	@Override
	public void onChange(Runnable changed) {
		this.changed = changed;
	}

	/** What it saves changed: its block marks itself. */
	protected void changed() {
		changed.run();
	}
}
