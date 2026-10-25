package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.ChargingPad;
import com.payonel.modularcomputers.machine.Motherboard;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** The charging pad's manual program (docs/examples/charger-log.lua), on a computer with a pad that robots come to. */
class ChargerExamplesTest extends McosHarness {
	/** What the pad says about itself: the robot on it, and its buffer. */
	private volatile String robot;
	private final ChargingPad pad = new ChargingPad(new ChargingPad.Pad() {
		@Override
		public String robot() {
			return robot;
		}

		@Override
		public boolean charging() {
			return robot != null;
		}

		@Override
		public int buffer() {
			return 1200;
		}

		@Override
		public int capacity() {
			return 26400;
		}
	});

	@Test
	void theLogProgramKeepsEveryRobotThatComesAndGoes() {
		devices.put(Motherboard.exp(1), pad); // (a pad's cabled; here it's at a slot)
		boot();
		file("log.lua", example("charger-log"));
		type("lua /log.lua /tmp/log &\r");
		untilIdle();
		server.add(() -> {
			robot = "golem-a";
			pad.docked("golem-a");
		});
		settle(() -> server.isEmpty());
		untilIdle();
		server.add(() -> {
			robot = null;
			pad.undocked("golem-a");
		});
		settle(() -> server.isEmpty());
		untilIdle();
		type("cp /tmp/log /log\r");
		untilFile(root().resolve("log"), "docked golem-a (charging golem-a 1200/26400)\nundocked golem-a (idle - 1200/26400)\n");
		assertThat(Files.exists(root().resolve("log"))).isTrue();
	}
}
