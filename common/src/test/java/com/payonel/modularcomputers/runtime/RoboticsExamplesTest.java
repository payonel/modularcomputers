package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.machine.GolemLayout;
import com.payonel.modularcomputers.machine.Motherboard;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The robotics device's manual programs (docs/examples/robotics-*.lua), on a computer whose robotics
 * device drives a stand-in body: it does what it's asked a tick later (as a golem's legs report),
 * arriving, or blocked where a wall is.
 */
class RoboticsExamplesTest extends McosHarness {
	private final RoboticsDevice robotics = new RoboticsDevice();
	/** What the body was asked, in order. */
	private final List<String> asked = new ArrayList<>();
	/** How many walks it makes before one is blocked (-1: none is). */
	private int wallAfter = -1;

	RoboticsExamplesTest() {
		robotics.moveWith(new RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + (long) forward + " " + (long) right + " " + (long) up);
				boolean wall = wallAfter == 0;
				wallAfter--;
				server.add(wall ? () -> robotics.blocked("no way there") : robotics::arrived);
				return null;
			}

			@Override
			public String turn(double degrees) {
				asked.add("turn " + (long) degrees);
				server.add(robotics::turned);
				return null;
			}

			@Override
			public void stop() {
			}
		});
		devices.put(Motherboard.exp(GolemLayout.ROBOTICS_LANE), robotics); // (soldered, a golem's)
	}

	@Test
	void theGotoProgramWalksThroughTheFileAndSaysItArrived() {
		boot();
		file("goto.lua", example("robotics-goto"));
		type("lua /goto.lua 3 -1 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("walk 3 -1 0");
		assertThat(read(root().resolve("out"))).isEqualTo("arrived\n");
	}

	@Test
	void theGotoProgramSaysWhyItWasBlocked() {
		wallAfter = 0;
		boot();
		file("goto.lua", example("robotics-goto"));
		type("lua /goto.lua 5 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(read(root().resolve("out"))).isEqualTo("blocked: no way there\n");
	}

	@Test
	void theSquareProgramWalksFourSidesTurningRightAfterEach() {
		boot();
		file("square.lua", example("robotics-square"));
		type("lua /square.lua 2 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("walk 2 0 0", "turn 90", "walk 2 0 0", "turn 90", "walk 2 0 0", "turn 90", "walk 2 0 0", "turn 90");
		assertThat(read(root().resolve("out"))).isEqualTo("back where it started\n");
	}

	@Test
	void theSquareProgramStopsAtAWall() {
		wallAfter = 2;
		boot();
		file("square.lua", example("robotics-square"));
		type("lua /square.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(asked).hasSize(5).last().isEqualTo("walk 4 0 0");
		assertThat(Files.exists(root().resolve("out"))).isTrue();
		assertThat(read(root().resolve("out"))).isEqualTo("side 3: blocked (no way there)\n");
	}
}
