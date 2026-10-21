package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every move costs a push's power, moved or not; extending an arm that's out already is free (user, 2026-10-13). */
class RoboticPistonTest {
	private final RoboticPiston piston = new RoboticPiston();

	RoboticPistonTest() {
		piston.power().supply(this);
		piston.power().collect(0);
	}

	private long owed() {
		return piston.power().collect(0);
	}

	@Test
	void extendingAnArmThatsOutIsFree() {
		piston.moveWith((side, motion) -> RoboticPiston.Moved.ALREADY_OUT);
		assertThat(piston.extend(new Arguments(List.of("up"))).first()).isEqualTo(true);
		assertThat(owed()).isZero();
	}

	@Test
	void aMoveAndAFailedPushCost() {
		piston.moveWith((side, motion) -> RoboticPiston.Moved.DONE);
		piston.extend(new Arguments(List.of("up")));
		assertThat(owed()).isEqualTo(RoboticPiston.PUSH_COST);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.CANNOT_PUSH);
		assertThat(piston.push(new Arguments(List.of("up"))).error()).isEqualTo(RoboticPiston.CANNOT_PUSH);
		assertThat(owed()).isEqualTo(RoboticPiston.PUSH_COST);
	}
}
