package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.runtime.Board;
import java.util.List;

/**
 * What the motherboard's firmware reads from the case it's in, as a real board's ACPI and SMBIOS
 * tables report the chassis, its batteries and its memory: the case's tier, its power, and the
 * RAM in its slots. The case fills it in; the board reports it to programs ({@link Board#power},
 * {@link Board#hardware}).
 */
public interface Chassis {
	/** A board on a bench: no case (tier 0), endless power, no load, no RAM sticks. */
	Chassis BENCH = new Chassis() {
		@Override
		public int tier() {
			return 0;
		}

		@Override
		public Board.PowerInfo power() {
			return new Board.PowerInfo(new Board.Store(0, 0, true), List.of(), 0);
		}

		@Override
		public List<Board.MemoryInfo> memory() {
			return List.of();
		}
	};

	/** The case's tier, 1 to 3. */
	int tier();

	Board.PowerInfo power();

	/** The RAM sticks in its slots. */
	List<Board.MemoryInfo> memory();
}
