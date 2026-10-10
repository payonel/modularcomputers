package com.payonel.modularcomputers.machine;

import java.util.List;
import java.util.Optional;

/** The parts that make up the machine itself (not bus devices). */
public record Hardware(List<Part> parts) {
	public Hardware {
		parts = List.copyOf(parts);
	}

	/** The all-in-one computer: CPU and memory soldered in, no limits. */
	public static Hardware allInOne() {
		return new Hardware(List.of(
			Part.soldered(PartKind.CPU),
			Part.soldered(PartKind.MEMORY)));
	}

	public Optional<Part> find(PartKind kind) {
		return parts.stream().filter(part -> part.kind() == kind).findFirst();
	}

	public boolean has(PartKind kind) {
		return find(kind).isPresent();
	}
}
