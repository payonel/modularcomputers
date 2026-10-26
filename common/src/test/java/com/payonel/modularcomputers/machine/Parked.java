package com.payonel.modularcomputers.machine;

/** Whether a test's program has parked: waiting for good on {@link Program#PARK}, as the tests' programs end (or it's stopped). */
public final class Parked {
	private Parked() {
	}

	public static boolean parked(Machine machine) {
		return machine.runner().map(runner -> runner.waitingFor().contains(Program.PARK)).orElse(true);
	}
}
