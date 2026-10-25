package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.FieldCard;
import com.payonel.modularcomputers.machine.Motherboard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The energy field card's manual programs (docs/examples/field-*.lua), on a computer whose card
 * works a stand-in projector: a pig and a player near, each move arriving a tick later, or blocked
 * where a ceiling is.
 */
class FieldExamplesTest extends McosHarness {
	private final FieldCard card = new FieldCard();
	/** What the projector was asked, in order. */
	private final List<String> asked = new ArrayList<>();
	private List<Object> near = List.of(Map.of("id", "a", "name", "minecraft:pig", "forward", 1.0, "right", 0.0, "up", 0.0, "distance", 1.0),
		Map.of("id", "b", "name", "minecraft:player", "forward", 2.0, "right", 1.0, "up", 0.0, "distance", 2.2));
	private boolean ceiling;

	FieldExamplesTest() {
		card.projectorIn(new FieldCard.Projector() {
			@Override
			public Result scan(double range) {
				return Result.ok(near);
			}

			@Override
			public Result capture(String id, double[] at) {
				asked.add("capture " + id);
				return Result.ok(id.equals("a") ? "minecraft:pig" : "minecraft:player");
			}

			@Override
			public Result move(double forward, double right, double up) {
				asked.add("move " + (long) forward + " " + (long) right + " " + (long) up);
				boolean stopped = ceiling;
				server.add(stopped ? () -> card.blocked("something's in the way") : card::arrived);
				return Result.ok(true);
			}

			@Override
			public void release() {
				asked.add("release");
			}

			@Override
			public Map<String, Object> where() {
				return null;
			}

			@Override
			public String check() {
				return null;
			}
		});
		devices.put(Motherboard.exp(1), card);
	}

	@Test
	void theLiftProgramCarriesThePlayerUpAndOver() {
		boot();
		file("lift.lua", example("field-lift"));
		type("lua /lift.lua 2 > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("capture b", "move 0 0 8", "move 2 0 0", "release");
		assertThat(read(root().resolve("out"))).isEqualTo("lifted minecraft:player 2 floor(s)\n");
	}

	@Test
	void theLiftProgramLetsGoWhenStopped() {
		ceiling = true;
		boot();
		file("lift.lua", example("field-lift"));
		type("lua /lift.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(asked).containsExactly("capture b", "move 0 0 4", "release");
		assertThat(read(root().resolve("out"))).isEqualTo("blocked: something's in the way\n");
	}

	@Test
	void theLiftProgramSaysWhenNoOnesNear() {
		near = List.of();
		boot();
		file("lift.lua", example("field-lift"));
		type("lua /lift.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(read(root().resolve("out"))).isEqualTo("no one near\n");
	}
}
