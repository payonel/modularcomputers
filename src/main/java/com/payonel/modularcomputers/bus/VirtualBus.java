package com.payonel.modularcomputers.bus;

import com.payonel.modularcomputers.console.ConsoleSplitter;

/**
 * Devices the motherboard always provides, found by fixed name rather than by walking the
 * physical bus (like Linux's virtual devices or UEFI's ConOut). None of them are hardware.
 */
public record VirtualBus(ConsoleSplitter console) {
}
