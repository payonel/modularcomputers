package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
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
		piston.moveWith((side, motion) -> RoboticPiston.Moved.ALREADY);
		assertThat(piston.extend(new Arguments(List.of("up"))).first()).isEqualTo(true);
		assertThat(owed()).isZero();
	}

	/** An arm already where it's asked to be is no failure (the user's: a lit lamp lit again): true, and its end ok. */
	@Test
	void retractingAnArmThatsInIsDoneAtOnceNotAFailure() {
		piston.accepting(true);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.ALREADY);
		assertThat(piston.retract(new Arguments(List.of("up"))).first()).isEqualTo(true);
		assertThat(piston.pull(new Arguments(List.of())).first()).isEqualTo(Map.of("event", "retract", "ok", true));
	}

	@Test
	void aMoveAndAFailedPushCost() {
		piston.moveWith((side, motion) -> RoboticPiston.Moved.DONE);
		piston.extend(new Arguments(List.of("up")));
		assertThat(owed()).isEqualTo(RoboticPiston.PUSH_COST);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.CANNOT_PUSH);
		assertThat(piston.push(new Arguments(List.of("up"))).first()).as("a command: true, its end says how").isEqualTo(true);
		assertThat(owed()).isEqualTo(RoboticPiston.PUSH_COST);
	}

	/** Its cable port's side is blocked too (the user's): by what's on it, its cable; its end at once, the call true. */
	@Test
	void itsCableSideIsBlockedByItsCable() {
		piston.accepting(true);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.CABLE);
		piston.blockedBy(side -> null, side -> "modularcomputers:cable");
		assertThat(piston.push(new Arguments(List.of("down"))).first()).isEqualTo(true);
		assertThat(piston.pull(new Arguments(List.of())).first())
			.isEqualTo(Map.of("event", "push", "ok", false, "why", "blocked", "block", "modularcomputers:cable", "face", "down"));
	}

	/** A push that can't move is its end, blocked (the user's: one shape): the block that won't move, and its face, the world's way. */
	@Test
	void aBlockedPushEndsBlockedWithTheBlockAndItsFace() {
		piston.accepting(true);
		piston.moveWith((side, motion) -> RoboticPiston.Moved.CANNOT_PUSH);
		piston.blockedBy(side -> "minecraft:obsidian", side -> "modularcomputers:cable");
		piston.extend(new Arguments(List.of("up")));
		assertThat(piston.pull(new Arguments(List.of())).first())
			.isEqualTo(Map.of("event", "extend", "ok", false, "why", "blocked", "block", "minecraft:obsidian", "face", "up"));
		piston.blockedBy(side -> null, side -> "modularcomputers:cable"); // (more than it moves: no one block)
		piston.push(new Arguments(List.of("north")));
		assertThat(piston.pull(new Arguments(List.of())).first()).isEqualTo(Map.of("event", "push", "ok", false, "why", "blocked", "block", "", "face", "north"));
	}
}
