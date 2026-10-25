package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The robotics device: its actions reach its body, and how they went comes back as events. */
class RoboticsDeviceTest {
	private final RoboticsDevice device = new RoboticsDevice();
	/** What the body was asked, in order. */
	private final List<String> asked = new ArrayList<>();
	/** Why the body refuses to walk (null: it walks). */
	private String refuse;

	@BeforeEach
	void body() {
		device.moveWith(new RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + forward + " " + right + " " + up);
				return refuse;
			}

			@Override
			public String turn(double degrees) {
				asked.add("turn " + degrees);
				return null;
			}

			@Override
			public void stop() {
				asked.add("stop");
			}
		});
		device.accepting(true);
		asked.clear();
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private Object state() {
		return ((Map<?, ?>) device.get(args()).first()).get("state");
	}

	@Test
	void aWalkGoesToItsBodyAndEndsInAnEvent() {
		assertThat(device.walk(args(3L, -1L)).first()).isEqualTo(true);
		assertThat(asked).containsExactly("walk 3.0 -1.0 0.0");
		assertThat(state()).isEqualTo("walking");
		assertThat(device.interruptPending()).isFalse();
		device.arrived();
		assertThat(device.interruptPending()).isTrue();
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "arrived"));
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkItsBodyCantStartFailsWithWhy() {
		refuse = "no way there";
		var result = device.walk(args(5L));
		assertThat(result.error()).isEqualTo("no way there");
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkTooFarIsRefusedBeforeTheBodyHearsOfIt() {
		assertThat(device.walk(args(RoboticsDevice.MAX_WALK + 1)).error()).startsWith("too far");
		assertThat(asked).isEmpty();
	}

	@Test
	void turnsByNameOrDegrees() {
		device.turn(args("left"));
		device.turn(args("around"));
		device.turn(args(45L));
		assertThat(asked).containsExactly("turn -90.0", "turn 180.0", "turn 45.0");
		assertThat(device.turn(args("up")).error()).startsWith("not a way to turn");
	}

	@Test
	void stoppingAWalkSaysSoAndStoppingNothingDoesnt() {
		device.stop(args());
		assertThat(device.interruptPending()).as("nothing was under way").isFalse();
		device.walk(args(2L));
		device.stop(args());
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "stopped"));
	}

	@Test
	void blockedSaysWhy() {
		device.walk(args(2L));
		device.blocked("stuck");
		assertThat(device.pull(args()).first()).isEqualTo(Map.of("event", "blocked", "why", "stuck"));
	}

	@Test
	void setTakesAnActionTable() {
		device.set(args(Map.of("action", "walk", "forward", 4L)));
		device.set(args(Map.of("action", "turn", "way", "right")));
		device.set(args(Map.of("action", "stop")));
		assertThat(asked).containsExactly("walk 4.0 0.0 0.0", "turn 90.0", "stop");
		assertThat(device.set(args(Map.of("action", "jump"))).error()).startsWith("not an action");
	}

	@Test
	void withNoProgramTakingDataItStopsAndItsEventsGo() {
		device.walk(args(2L));
		device.arrived();
		device.accepting(false);
		assertThat(asked).endsWith("stop");
		assertThat(device.interruptPending()).isFalse();
		assertThat(state()).isEqualTo("idle");
	}

	@Test
	void aWalkUnderWayAtASaveEndsBlockedAsTheProgramGoesOn() {
		device.walk(args(2L));
		var saved = device.saved();
		RoboticsDevice loaded = new RoboticsDevice();
		loaded.restore(saved);
		assertThat(loaded.interruptPending()).as("not until a program takes data").isFalse();
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).isEqualTo(Map.of("event", "blocked", "why", RoboticsDevice.INTERRUPTED));
	}

	@Test
	void walkingCostsPowerByTheBlock() {
		device.power().supply(this); // (a running board pays)
		device.walked(2.5);
		assertThat(device.power().collect(0)).isEqualTo((long) Math.ceil(2.5 * RoboticsDevice.WALK_COST)); // (by the distance, a part of a block too)
	}
}
