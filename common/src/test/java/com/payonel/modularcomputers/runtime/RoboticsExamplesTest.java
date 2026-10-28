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
	/** How many blocks it breaks before one won't break (-1: all do). */
	private int bedrockAfter = -1;

	/** What the stand-in sees round it: stone ahead, a chest to its right, the floor below, air elsewhere. */
	private static java.util.Map<String, Object> space(String face) {
		return switch (face) {
			case "front", "down" -> java.util.Map.of("kind", "block", "name", "minecraft:stone");
			case "right" -> java.util.Map.of("kind", "block", "name", "minecraft:chest", "container", 27);
			default -> java.util.Map.of("kind", "air");
		};
	}

	/** Where the stand-in is from its origin: forward, right (its walks move it). */
	private long[] at = {0, 0};

	/** How many walks it makes before one is blocked (-1: none is). */
	private int wallAfter = -1;

	RoboticsExamplesTest() {
		robotics.moveWith(new RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + (long) forward + " " + (long) right + " " + (long) up);
				at = new long[] {at[0] + (long) forward, at[1] + (long) right};
				boolean wall = wallAfter == 0;
				wallAfter--;
				server.add(wall ? () -> robotics.blocked("blocked: minecraft:stone front") : robotics::arrived);
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

			@Override
			public Result scan(String face) {
				java.util.Map<String, Object> around = new java.util.LinkedHashMap<>();
				for (String each : RoboticsDevice.FACES) {
					around.put(each, space(each));
				}
				return Result.ok(face == null ? around : around.get(face));
			}

			@Override
			public String fly(double forward, double right, double up) {
				asked.add("fly " + (long) forward + " " + (long) right + " " + (long) up);
				server.add(robotics::arrived);
				return null;
			}

			@Override
			public String walkTo(double forward, double right, double up) {
				asked.add("walkTo " + (long) forward + " " + (long) right + " " + (long) up);
				at = new long[] {(long) forward, (long) right};
				server.add(robotics::arrived);
				return null;
			}

			@Override
			public Result position() {
				return Result.ok(java.util.Map.of("forward", at[0], "right", at[1], "up", 0L, "heading", 0L));
			}

			@Override
			public String land() {
				asked.add("land");
				server.add(robotics::landed);
				return null;
			}

			@Override
			public Result held(boolean off) {
				return Result.ok(java.util.Map.of("name", "minecraft:iron_pickaxe", "count", 1));
			}

			@Override
			public Result swing(String face) {
				asked.add("swing " + face);
				boolean stuck = bedrockAfter == 0;
				bedrockAfter--;
				server.add(stuck ? () -> robotics.failed("unbreakable") : () -> robotics.broke("minecraft:stone", 0));
				return Result.ok(RoboticsDevice.BREAKING);
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
		assertThat(read(root().resolve("out"))).isEqualTo("blocked: minecraft:stone front\n");
	}

	@Test
	void theFlyProgramFliesUpLooksRoundAndLandsBackOnItsLegs() {
		boot();
		file("fly.lua", example("robotics-fly"));
		type("lua /fly.lua 4 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("fly 0 0 4", "land");
		assertThat(read(root().resolve("out"))).contains("front\tblock\tminecraft:stone\n").endsWith("back on its legs\n");
	}

	@Test
	void theHomeProgramWalksOutSaysWhereAndWalksHome() {
		boot();
		file("home.lua", example("robotics-home"));
		type("lua /home.lua 3 2 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("walk 3 2 0", "walkTo 0 0 0");
		assertThat(read(root().resolve("out"))).isEqualTo("at 3 forward, 2 right, 0 up, facing 0\nat 0 forward, 0 right, 0 up, facing 0\n");
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
		assertThat(read(root().resolve("out"))).isEqualTo("side 3: blocked: minecraft:stone front\n");
	}

	@Test
	void theLookProgramSaysWhatsRoundItFromOneScan() {
		boot();
		file("look.lua", example("robotics-look"));
		type("lua /look.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(read(root().resolve("out"))).isEqualTo("front: block minecraft:stone\nback: air\nleft: air\n"
			+ "right: block minecraft:chest (27 slots)\nup: air\ndown: block minecraft:stone\n");
	}

	@Test
	void theDigProgramBreaksAndWalksInAgainAndAgain() {
		boot();
		file("dig.lua", example("robotics-dig"));
		type("lua /dig.lua 3 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("swing front", "walk 1 0 0", "swing front", "walk 1 0 0", "swing front", "walk 1 0 0");
		assertThat(read(root().resolve("out"))).isEqualTo("digging with minecraft:iron_pickaxe\ndug 3\n");
	}

	@Test
	void theDigProgramStopsAtWhatWontBreak() {
		bedrockAfter = 1;
		boot();
		file("dig.lua", example("robotics-dig"));
		type("lua /dig.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(read(root().resolve("out"))).isEqualTo("digging with minecraft:iron_pickaxe\nstopped after 1: unbreakable\n");
	}
}
