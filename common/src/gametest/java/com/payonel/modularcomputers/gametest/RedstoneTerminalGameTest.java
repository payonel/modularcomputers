package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RedstoneTerminalBlockEntity;
import com.payonel.modularcomputers.device.RedstoneTerminal;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

/** The redstone terminal (user design, 2026-10-20): on a computer's cable, its sides cable or redstone. */
public class RedstoneTerminalGameTest {
	private static final BlockPos COMPUTER = new BlockPos(1, 1, 3);
	private static final BlockPos CABLE = new BlockPos(2, 1, 3);
	/** West of it the cable; east, a lamp; north, where a signal comes in. */
	private static final BlockPos TERMINAL = new BlockPos(3, 1, 3);
	private static final double WAIT_SECONDS = 1000;

	/** A running computer (its program waits on: its devices take data only while one runs), cabled to a terminal. */
	private static AllInOneBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(COMPUTER, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(COMPUTER, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(WAIT_SECONDS, "nothing");
			}
		});
		helper.setBlock(TERMINAL, ModBlocks.REDSTONE_TERMINAL);
		BlockPos cable = helper.absolutePos(CABLE);
		helper.getLevel().setBlock(cable, CableBlock.withConnections(helper.getLevel(), cable, ModBlocks.CABLE.defaultBlockState()), Block.UPDATE_ALL);
		computer.pressPower();
		return computer;
	}

	private static RedstoneTerminalBlockEntity terminal(GameTestHelper helper) {
		return helper.getBlockEntity(TERMINAL, RedstoneTerminalBlockEntity.class);
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> sides(RedstoneTerminal device) {
		return (Map<String, Map<String, Object>>) device.get(args()).first();
	}

	private static boolean onBus(AllInOneBlockEntity computer, RedstoneTerminal device) {
		return computer.machine().board().bus().devices().contains(device);
	}

	@GameTest(maxTicks = 60)
	public void aTerminalOnAComputersCablePutsOutAndReadsRedstoneOnItsOtherSides(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		RedstoneTerminal device = terminal(helper).device();
		helper.setBlock(TERMINAL.east(), Blocks.REDSTONE_LAMP);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, device), "on the computer's bus, by its cable"))
			.thenExecute(() -> {
				helper.assertValueEqual(sides(device).get("west").get("mode"), "cable", "the side against the cable");
				helper.assertValueEqual(sides(device).get("east").get("mode"), "redstone", "the others");
				device.set(args(Map.of("east", Map.of("level", 15L))));
				helper.assertValueEqual(sides(device).get("east").get("powered"), true, "it drives east");
				helper.setBlock(TERMINAL.north(), Blocks.REDSTONE_BLOCK);
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(TERMINAL.east(), RedstoneLampBlock.LIT, true))
			.thenWaitUntil(() -> helper.assertValueEqual(sides(device).get("north").get("level"), 15L, "north reads 15"))
			.thenExecute(() -> {
				Object event = device.pull(args()).first();
				helper.assertTrue(event instanceof Map<?, ?> map && "north".equals(map.get("side")) && Long.valueOf(15).equals(map.get("new")),
					"a change coming in is an event: " + event);
				device.set(args(Map.of("east", Map.of("level", 0L))));
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(TERMINAL.east(), RedstoneLampBlock.LIT, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void closingACableSidesPortLetsGoOfTheCableAndTheNetwork(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		RedstoneTerminal device = terminal(helper).device();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, device), "on the bus"))
			.thenExecute(() -> {
				device.set(args(Map.of("west", Map.of("mode", "redstone"))));
				helper.assertBlockProperty(CABLE, CableBlock.EAST, false);
				helper.assertValueEqual(sides(device).get("west").get("mode"), "redstone", "closed: a redstone side");
			})
			.thenWaitUntil(() -> helper.assertTrue(!onBus(computer, device), "off the computer's network"))
			.thenExecute(() -> {
				device.set(args(Map.of("west", Map.of("mode", "cable"))));
				helper.assertBlockProperty(CABLE, CableBlock.EAST, true);
			})
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, device), "back on it"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void whatItPutsOutHoldsWhenTheComputerGoesOff(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		RedstoneTerminal device = terminal(helper).device();
		helper.setBlock(TERMINAL.east(), Blocks.REDSTONE_LAMP);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, device), "on the bus"))
			.thenExecute(() -> {
				device.set(args(Map.of("east", Map.of("level", 15L))));
				computer.pressPower();
			})
			.thenIdle(10)
			.thenExecute(() -> {
				helper.assertTrue(!computer.machine().isPowered(), "the computer's off");
				helper.assertBlockProperty(TERMINAL.east(), RedstoneLampBlock.LIT, true);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void itsBatteryPaysForItsChanges(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		RedstoneTerminalBlockEntity terminal = terminal(helper);
		helper.assertTrue(!terminal.accepts(RedstoneTerminalBlockEntity.RADIO_SLOT, ModItems.TIER1_BATTERY.charged()), "the radio slot takes no battery");
		helper.assertTrue(terminal.accepts(RedstoneTerminalBlockEntity.BATTERY_SLOT, ModItems.TIER1_BATTERY.charged()), "the battery slot does");
		helper.assertTrue(!terminal.accepts(RedstoneTerminalBlockEntity.BATTERY_SLOT, new ItemStack(ModItems.AUDIO_CARD)), "and nothing else");
		terminal.cards().setItem(RedstoneTerminalBlockEntity.BATTERY_SLOT, ModItems.TIER1_BATTERY.charged());
		int full = BatteryItem.charge(terminal.cards().getItem(RedstoneTerminalBlockEntity.BATTERY_SLOT)).units();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, terminal.device()), "on the bus"))
			.thenExecute(() -> {
				terminal.device().set(args(Map.of("east", Map.of("level", 3L))));
				int left = BatteryItem.charge(terminal.cards().getItem(RedstoneTerminalBlockEntity.BATTERY_SLOT)).units();
				helper.assertValueEqual(left, full - RedstoneTerminal.CHANGE_COST, "the change, paid from its battery");
			})
			.thenSucceed();
	}

	@GameTest
	public void itsOutputsAndClosedPortsOutlastASave(GameTestHelper helper) {
		helper.setBlock(TERMINAL, ModBlocks.REDSTONE_TERMINAL);
		RedstoneTerminalBlockEntity terminal = terminal(helper);
		terminal.device().set(args(Map.of("north", Map.of("level", 5L), "west", Map.of("mode", "redstone"))));
		var registries = helper.getLevel().registryAccess();
		net.minecraft.nbt.CompoundTag saved = terminal.saveWithoutMetadata(registries);
		helper.setBlock(TERMINAL, Blocks.AIR);
		helper.setBlock(TERMINAL, ModBlocks.REDSTONE_TERMINAL);
		RedstoneTerminalBlockEntity again = terminal(helper);
		again.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
		helper.assertValueEqual(again.device().output(0), 5, "what it put out north");
		helper.assertTrue(!again.ports().open(Direction.WEST), "its closed port");
		helper.succeed();
	}
}
