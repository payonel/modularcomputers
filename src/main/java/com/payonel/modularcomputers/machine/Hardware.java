package com.payonel.modularcomputers.machine;

import java.util.List;
import java.util.Optional;

/** The parts installed in one computer. */
public record Hardware(List<Part> parts) {
	public Hardware {
		parts = List.copyOf(parts);
	}

	/** The all-in-one computer: CPU, memory and disk soldered in, no limits, no EEPROM yet. */
	public static Hardware allInOne() {
		return new Hardware(List.of(
			Part.soldered(PartKind.CPU),
			Part.soldered(PartKind.MEMORY),
			Part.soldered(PartKind.STORAGE)));
	}

	public Optional<Part> find(PartKind kind) {
		return parts.stream().filter(part -> part.kind() == kind).findFirst();
	}

	public boolean has(PartKind kind) {
		return find(kind).isPresent();
	}
}
