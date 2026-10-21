package com.payonel.modularcomputers.bus;

import com.payonel.modularcomputers.console.ConsoleSplitter;
import java.util.Optional;

/**
 * Devices the motherboard always provides, found by fixed name rather than by walking the
 * physical bus (like Linux's virtual devices or UEFI's ConOut). None of them are hardware.
 */
public record VirtualBus(ConsoleSplitter console) {
	/** The console's fixed name. */
	public static final String CONSOLE = "console";

	/** A virtual device by name. */
	public Optional<Object> device(String name) {
		return CONSOLE.equals(name) ? Optional.of(console) : Optional.empty();
	}
}
