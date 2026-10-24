package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.machine.Motherboard;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The manual's programs (docs/examples), each run on a computer with the devices it uses, as a
 * player would copy one: every example in the manual is run here (or, for a device only a world
 * makes, by a game test), so none can drift from what the devices do.
 */
class ExamplesTest extends McosHarness {
	@Test
	void theManualsBlinkProgramTurnsAFaceOnAndOffForever() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		boot();
		file("blink.lua", example("redstone-blink"));
		type("lua /blink.lua &\r");
		settle(() -> card.output(0) == 15);
		settle(() -> card.output(0) == 0);
		settle(() -> card.output(0) == 15); // (and again: it never ends)
	}

	@Test
	void theManualsMirrorProgramCopiesTheWestInputToTheEastOutputAsItChanges() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), card);
		boot();
		file("mirror.lua", example("redstone-mirror"));
		type("lua /mirror.lua &\r");
		untilIdle();
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		settle(() -> card.output(2) == 9); // east: 9 goes out
		server.add(() -> card.sense(3, 4));
		settle(() -> card.output(2) == 4);
	}

	@Test
	void theManualsMotionAlarmProgramSoundsWhenSomethingMoves() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(0), motion);
		devices.put(Motherboard.exp(1), card);
		boot();
		// (a long pulse, so the alarm is still on when the test looks)
		file("alarm.lua", example("motion-alarm").replace("local PULSE = 1 ", "local PULSE = 600 "));
		type("lua /alarm.lua > /tmp/out &\r");
		untilIdle();
		assertThat(card.output(0)).as("quiet until something moves").isZero();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		settle(() -> card.output(0) == 15);
	}
}
