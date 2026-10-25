package com.payonel.modularcomputers.machine;

import java.util.List;
import java.util.Optional;

/** The parts soldered onto a motherboard (not bus devices, not socketed parts). */
public record Hardware(List<Part> parts) {
	public Hardware {
		parts = List.copyOf(parts);
	}

	/** Every computer's board: memory soldered in, no limit (the RAM installed sets what a program gets). The CPU goes in a socket. */
	public static Hardware computer() {
		return new Hardware(List.of(Part.soldered(PartKind.MEMORY)));
	}

	public Optional<Part> find(PartKind kind) {
		return parts.stream().filter(part -> part.kind() == kind).findFirst();
	}

	public boolean has(PartKind kind) {
		return find(kind).isPresent();
	}
}
