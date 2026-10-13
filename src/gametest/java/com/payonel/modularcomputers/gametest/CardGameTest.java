package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.MotionSensorCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.WeatherCard;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Cards that work where they're installed (user design, 2026-10-13): redstone, weather, motion. */
public class CardGameTest {
	private static final BlockPos POS = new BlockPos(4, 2, 4);

	/** A running computer (its program halted: power stays on) with {@code card} in its first expansion slot. */
	@SuppressWarnings("unchecked")
	private static <T> T computerWith(GameTestHelper helper, Item card, Class<T> type) {
		helper.setBlock(POS, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(card));
		TestPrograms.flash(computer, board -> board.halt());
		computer.pressPower();
		return (T) computer.machine().board().bus().devices().stream().filter(type::isInstance).findFirst().orElseThrow();
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@GameTest(maxTicks = 40)
	public void aRedstoneCardSetsAndReadsRedstoneOnItsBlocksFaces(GameTestHelper helper) {
		RedstoneCard card = computerWith(helper, ModItems.REDSTONE_CARD, RedstoneCard.class);
		helper.setBlock(POS.east(), Blocks.REDSTONE_LAMP);
		helper.assertTrue(!card.setOutput(args("east", 15L)).failed(), "set east to 15");
		helper.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertBlockProperty(POS.east(), RedstoneLampBlock.LIT, true))
			.thenWaitUntil(() -> helper.assertValueEqual(card.getInput(args("west")).first(), 15L, "west reads 15"))
			.thenExecute(() -> {
				helper.assertTrue(card.interruptPending(), "a change is an interrupt");
				List<Map<?, ?>> events = new ArrayList<>();
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					events.add((Map<?, ?>) event);
				}
				helper.assertTrue(events.stream().anyMatch(e -> "west".equals(e.get("side")) && Long.valueOf(15).equals(e.get("new"))), "the change, as an event: " + events);
				card.setOutput(args("east", 0L));
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(POS.east(), RedstoneLampBlock.LIT, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void aWeatherCardReportsTheWeatherAndItsChanges(GameTestHelper helper) {
		WeatherCard card = computerWith(helper, ModItems.WEATHER_SENSOR_CARD, WeatherCard.class);
		// The test world's biome never rains (as a desert): it reports clear there, rain or not.
		helper.startSequence()
			.thenIdle(21)
			.thenExecute(() -> {
				helper.assertValueEqual(card.getWeather(args()).first(), "clear", "clear here");
				var none = net.minecraft.world.level.biome.Biome.Precipitation.NONE;
				var rain = net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
				var snow = net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
				helper.assertValueEqual(List.of(com.payonel.modularcomputers.block.CardWorld.weather(false, false, rain),
					com.payonel.modularcomputers.block.CardWorld.weather(true, false, rain), com.payonel.modularcomputers.block.CardWorld.weather(true, true, rain),
					com.payonel.modularcomputers.block.CardWorld.weather(true, false, snow), com.payonel.modularcomputers.block.CardWorld.weather(true, true, none)),
					List.of("clear", "rain", "thunder", "snow", "clear"), "the weather by biome");
				card.sense("rain"); // as the world would, where it rains
				Map<?, ?> event = (Map<?, ?>) card.pull(args()).first();
				helper.assertTrue(event != null && "clear".equals(event.get("old")) && "rain".equals(event.get("new")), "a change is an event: " + event);
				helper.assertValueEqual(card.getWeather(args()).first(), "rain", "and what it reports");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void aMotionSensorSeesWhatMovesWhereRelativeToItsBlock(GameTestHelper helper) {
		MotionSensorCard card = computerWith(helper, ModItems.MOTION_SENSOR_CARD, MotionSensorCard.class);
		helper.assertTrue(card.setRange(args(100L)).first().equals(32L), "range tops out at 32");
		card.setRange(args(6L));
		card.setMode(args("drops"));
		helper.spawn(EntityTypes.PIG, POS.north(3));
		ItemEntity drop = new ItemEntity(helper.getLevel(), helper.absolutePos(POS.east(2)).getX() + 0.5, helper.absolutePos(POS).getY() + 0.5,
			helper.absolutePos(POS).getZ() + 0.5, new ItemStack(Items.DIAMOND));
		drop.setDeltaMovement(0, 0, 0);
		drop.setNoGravity(true);
		helper.getLevel().addFreshEntity(drop);
		List<Map<?, ?>> seen = new ArrayList<>();
		helper.startSequence()
			.thenWaitUntil(() -> {
				Object event;
				while ((event = card.pull(args()).first()) != null) {
					seen.add((Map<?, ?>) event);
				}
				helper.assertTrue(!seen.isEmpty(), "saw something");
			})
			.thenExecute(() -> {
				helper.assertTrue(seen.stream().allMatch(e -> "drop".equals(e.get("kind"))), "drops only, in its mode: " + seen);
				Map<?, ?> diamond = seen.getFirst();
				helper.assertValueEqual(diamond.get("item"), "minecraft:diamond", "what dropped");
				helper.assertValueEqual(diamond.get("x"), 2.0, "two blocks east of it");
				card.setMode(args("blocks"));
				helper.setBlock(POS.south(2), Blocks.OAK_DOOR.defaultBlockState());
			})
			.thenIdle(2)
			.thenExecute(() -> {
				card.pull(args()); // (whatever placing it said)
				while (card.pull(args()).first() != null) {
				}
				helper.setBlock(POS.south(2), helper.getBlockState(POS.south(2)).setValue(BlockStateProperties.OPEN, true));
			})
			.thenWaitUntil(() -> {
				Map<?, ?> event = (Map<?, ?>) card.pull(args()).first();
				helper.assertTrue(event != null && "door".equals(event.get("kind")) && Boolean.TRUE.equals(event.get("open")) && Double.valueOf(2.0).equals(event.get("z")),
					"the door opening, two blocks south: " + event);
				while (card.pull(args()).first() != null) {
				}
				helper.setBlock(POS.west(3), Blocks.WATER);
			})
			.thenWaitUntil(() -> {
				Map<?, ?> event = (Map<?, ?>) card.pull(args()).first();
				helper.assertTrue(event != null && "fluid".equals(event.get("kind")) && "minecraft:water".equals(event.get("type")), "water flowing: " + event);
			})
			.thenSucceed();
	}
}
