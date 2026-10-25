package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.CraftingCard;
import com.payonel.modularcomputers.machine.Motherboard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The crafting card's manual programs (docs/examples/crafting-*.lua), on a computer whose card
 * works a stand-in bench: a chest in front holding some logs, each craft four planks.
 */
class CraftingExamplesTest extends McosHarness {
	private final CraftingCard card = new CraftingCard();
	/** What the bench was asked, in order. */
	private final List<String> asked = new ArrayList<>();
	/** Logs in the chest; how many planks the chest has room for. */
	private int logs = 5;
	private int room = Integer.MAX_VALUE;
	private int planks;

	CraftingExamplesTest() {
		card.benchIn(new CraftingCard.Bench() {
			@Override
			public boolean robotics() {
				return true;
			}

			@Override
			public Result list() {
				return Result.ok(List.of());
			}

			@Override
			public Result craft() {
				planks += 4;
				return Result.ok(Map.of("name", "minecraft:oak_planks", "count", 4));
			}

			@Override
			public Result move(int from, int to, int count) {
				return Result.ok(0);
			}

			@Override
			public Result equip(int slot) {
				return Result.ok(true);
			}

			@Override
			public Result pickup() {
				return Result.ok(0);
			}

			@Override
			public Result drop(String face, int slot, int count) {
				return Result.ok(0);
			}

			@Override
			public Result store(String face, int slot, int count, int to) {
				asked.add("store " + face + " " + slot);
				int left = Math.max(0, planks - room);
				planks = left;
				return left == 0 ? Result.ok(true, 0) : Result.ok(false, left);
			}

			@Override
			public Result take(String face, int slot, int count, int to) {
				asked.add("take " + face + " " + slot + " " + count + " " + to);
				int taken = Math.min(logs, count);
				logs -= taken;
				return Result.ok(taken);
			}
		});
		devices.put(Motherboard.exp(1), card);
		// (the card's crafts take their time on the server's ticks)
		ticks.add(card::tick);
	}

	@Test
	void thePlanksProgramTakesLogsCraftsAndStoresThemBack() {
		boot();
		file("planks.lua", example("crafting-planks"));
		type("lua /planks.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(asked).containsExactly("take front 0 64 1", "store front 0");
		assertThat(read(root().resolve("out"))).isEqualTo("made 20 minecraft:oak_planks, all in the chest\n");
	}

	@Test
	void thePlanksProgramSaysWhatsLeftWhenTheChestIsFull() {
		room = 12;
		boot();
		file("planks.lua", example("crafting-planks"));
		type("lua /planks.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "0\n");
		assertThat(read(root().resolve("out"))).isEqualTo("made 20 minecraft:oak_planks, 8 left: the chest is full\n");
	}

	@Test
	void thePlanksProgramSaysWhenTheChestHasNoLogs() {
		logs = 0;
		boot();
		file("planks.lua", example("crafting-planks"));
		type("lua /planks.lua > /out; echo $? > /status\r");
		untilFile(root().resolve("status"), "1\n");
		assertThat(read(root().resolve("out"))).isEqualTo("no logs in the chest\n");
	}
}
