package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Server game tests for screens and cables: cables join each other and the sides of screens
 * and computers, and a computer finds the screens wired to it, puts them on its bus and shows
 * its console on them.
 */
public class CableGameTest {
	private static final BlockPos COMPUTER = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity computer(GameTestHelper helper, BlockPos pos, boolean parts) {
		helper.setBlock(pos, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(pos, AllInOneBlockEntity.class);
		if (parts) {
			computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.CPU));
			computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
			computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		}
		return computer;
	}

	private static ScreenBlockEntity screen(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModBlocks.SCREEN.defaultBlockState().setValue(ScreenBlock.FACING, facing));
		return helper.getBlockEntity(pos, ScreenBlockEntity.class);
	}

	/** Places a cable the way a player would: it joins its neighbors. */
	private static void cable(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		helper.getLevel().setBlock(absolute, CableBlock.withConnections(helper.getLevel(), absolute, ModBlocks.CABLE.defaultBlockState()),
			Block.UPDATE_ALL);
	}

	/** Computer, two cable blocks, then a screen facing south. */
	private static ScreenBlockEntity wiredScreen(GameTestHelper helper) {
		cable(helper, new BlockPos(2, 1, 1));
		cable(helper, new BlockPos(3, 1, 1));
		return screen(helper, new BlockPos(4, 1, 1), Direction.SOUTH);
	}

	private static Optional<AllInOneBlockEntity.BusEntry> entryFor(AllInOneBlockEntity computer, ScreenBlockEntity screen) {
		return computer.busEntries().stream().filter(entry -> entry.key().equals("id:" + screen.id())).findFirst();
	}

	private static boolean onBus(AllInOneBlockEntity computer, ScreenBlockEntity screen) {
		return computer.machine().board().bus().devices().contains(screen.display());
	}

	private static boolean showsText(Display display) {
		return display.buffer().row(0).contains("hello");
	}

	@GameTest
	public void cablesJoinCablesAndTheSidesOfScreensAndComputers(GameTestHelper helper) {
		computer(helper, COMPUTER, false);
		cable(helper, new BlockPos(2, 1, 1));
		helper.assertBlockProperty(new BlockPos(2, 1, 1), PipeBlock.WEST, true);
		cable(helper, new BlockPos(1, 1, 2)); // in front of the computer: the keyboard, no port
		helper.assertBlockProperty(new BlockPos(1, 1, 2), PipeBlock.NORTH, false);
		cable(helper, new BlockPos(3, 1, 1));
		helper.assertBlockProperty(new BlockPos(2, 1, 1), PipeBlock.EAST, true);
		helper.assertBlockProperty(new BlockPos(3, 1, 1), PipeBlock.WEST, true);
		screen(helper, new BlockPos(4, 1, 1), Direction.WEST); // its glass faces the cable
		helper.assertBlockProperty(new BlockPos(3, 1, 1), PipeBlock.EAST, false);
		screen(helper, new BlockPos(4, 1, 1), Direction.SOUTH);
		helper.assertBlockProperty(new BlockPos(3, 1, 1), PipeBlock.EAST, true);
		helper.setBlock(new BlockPos(4, 1, 1), net.minecraft.world.level.block.Blocks.AIR);
		helper.assertBlockProperty(new BlockPos(3, 1, 1), PipeBlock.EAST, false);
		helper.succeed();
	}

	@GameTest
	public void aComputerListsTheScreensWiredToItAndShowsHelloWorldOnThem(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		ScreenBlockEntity screen = wiredScreen(helper);
		helper.succeedWhen(() -> {
			var entry = entryFor(computer, screen).orElseThrow(() -> helper.assertionException("the screen should be in the bus order"));
			helper.assertValueEqual(entry.name().getString(), "Screen", "cabled device name");
			helper.assertValueEqual(entry.location().getString(), "cable, 3m", "cabled device location");
			helper.assertValueEqual(entry.device(), Optional.of(helper.absolutePos(new BlockPos(4, 1, 1))), "where the bulb points");
			helper.assertTrue(entry.connected(), "new cabled devices start connected");
			helper.assertTrue(onBus(computer, screen), "the screen should be on the machine's bus");
			if (!computer.machine().isPowered()) {
				computer.pressPower();
			}
			helper.assertTrue(showsText(computer.display()), "the built-in display shows hello world");
			helper.assertTrue(showsText(screen.display()), "so does the wired screen");
		});
	}

	@GameTest
	public void aScreenWiredToARunningComputerShowsTheConsole(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		computer.pressPower();
		ScreenBlockEntity screen = wiredScreen(helper);
		helper.succeedWhen(() -> helper.assertTrue(showsText(screen.display()), "a screen plugged into a running computer shows its console"));
	}

	@GameTest
	public void disconnectingAScreenTakesItOffTheBusAndBlanksIt(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		computer.pressPower();
		ScreenBlockEntity screen = wiredScreen(helper);
		String key = "id:" + screen.id();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(showsText(screen.display()), "connected first"))
			.thenExecute(() -> {
				helper.assertTrue(computer.setConnected(key, false), "the plug comes out");
				helper.assertTrue(!onBus(computer, screen), "a disconnected screen is off the bus");
				helper.assertTrue(screen.display().buffer().isBlank(), "and goes blank");
				var entry = entryFor(computer, screen).orElseThrow(() -> helper.assertionException("still listed"));
				helper.assertTrue(!entry.connected(), "listed as disconnected");
				helper.assertTrue(!computer.setConnected("edp", false), "built-ins can't be unplugged");
				helper.assertTrue(computer.setConnected(key, true), "plugged back in");
				helper.assertTrue(showsText(screen.display()), "shows the console again");
			})
			.thenSucceed();
	}

	@GameTest
	public void aScreenThatGoesAwayStaysListedOfflineInItsPlaceUntilForgotten(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, false);
		ScreenBlockEntity screen = wiredScreen(helper);
		String key = "id:" + screen.id();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(entryFor(computer, screen).filter(AllInOneBlockEntity.BusEntry::online).isPresent(), "found"))
			.thenExecute(() -> {
				helper.assertTrue(!computer.forgetDevice(key), "an online device can't be forgotten");
				helper.destroyBlock(new BlockPos(3, 1, 1)); // cut the cable
			})
			.thenWaitUntil(() -> helper.assertTrue(entryFor(computer, screen).filter(entry -> !entry.online()).isPresent(), "offline, still listed"))
			.thenExecute(() -> {
				helper.assertValueEqual(entryFor(computer, screen).get().location().getString(), "offline", "its row says so");
				helper.assertTrue(!onBus(computer, screen), "not on the bus");
				cable(helper, new BlockPos(3, 1, 1));
			})
			.thenWaitUntil(() -> helper.assertTrue(entryFor(computer, screen).filter(AllInOneBlockEntity.BusEntry::online).isPresent(), "back online"))
			.thenExecute(() -> helper.destroyBlock(new BlockPos(3, 1, 1)))
			.thenWaitUntil(() -> helper.assertTrue(entryFor(computer, screen).filter(entry -> !entry.online()).isPresent(), "offline again"))
			.thenExecute(() -> {
				helper.assertTrue(computer.forgetDevice(key), "forgotten");
				helper.assertTrue(entryFor(computer, screen).isEmpty(), "no longer listed");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aFullBusMakesNewDevicesWaitAndSaysSo(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, false);
		computer.machine().board().setBusCapacity(BusOrder.INTERNAL_PLACES + 1); // room for one external device
		ScreenBlockEntity first = wiredScreen(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, first), "the first screen joins"))
			.thenExecute(() -> screen(helper, COMPUTER.above(), Direction.SOUTH)) // on top of the computer: its own way in
			.thenWaitUntil(() -> helper.assertTrue(computer.busStatus().overflow(), "the bus overflows"))
			.thenExecute(() -> {
				BusStatus status = computer.machine().board().bus().status();
				helper.assertValueEqual(status, new BusStatus(BusOrder.INTERNAL_PLACES + 1, 3, 1, 0, 1), "the bus reports it to programs too");
				helper.destroyBlock(new BlockPos(3, 1, 1)); // cut the cable: the first screen goes missing
			})
			.thenWaitUntil(() -> helper.assertTrue(!computer.busStatus().overflow(), "no longer full"))
			.thenExecute(() -> {
				helper.assertTrue(!onBus(computer, first), "the first screen is gone");
				helper.assertTrue(entryFor(computer, first).isEmpty(), "and, missing, it made room");
			})
			.thenSucceed();
	}

		@GameTest
	public void oneScreenCanBeWiredToTwoComputers(GameTestHelper helper) {
		AllInOneBlockEntity first = computer(helper, COMPUTER, true);
		cable(helper, new BlockPos(2, 1, 1));
		ScreenBlockEntity screen = screen(helper, new BlockPos(3, 1, 1), Direction.SOUTH);
		cable(helper, new BlockPos(4, 1, 1));
		AllInOneBlockEntity second = computer(helper, new BlockPos(5, 1, 1), false);
		helper.succeedWhen(() -> {
			helper.assertTrue(onBus(first, screen) && onBus(second, screen), "both computers see the screen");
			helper.assertTrue(!first.machine().board().bus().devices().contains(second.display()), "a computer's built-in display is its own");
			if (!first.machine().isPowered()) {
				first.pressPower();
			}
			helper.assertTrue(showsText(screen.display()), "a running computer shows its console on the shared screen");
		});
	}

	@GameTest
	public void screensPassTheConnectionOnLikeACable(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		ScreenBlockEntity near = screen(helper, new BlockPos(2, 1, 1), Direction.SOUTH); // against the computer's side
		ScreenBlockEntity next = screen(helper, new BlockPos(3, 1, 1), Direction.SOUTH);
		cable(helper, new BlockPos(4, 1, 1));
		ScreenBlockEntity far = screen(helper, new BlockPos(5, 1, 1), Direction.SOUTH);
		helper.assertBlockProperty(new BlockPos(4, 1, 1), PipeBlock.WEST, true);
		helper.succeedWhen(() -> {
			for (ScreenBlockEntity screen : List.of(near, next, far)) {
				helper.assertTrue(onBus(computer, screen), "every screen in the row is on the bus");
			}
			helper.assertValueEqual(entryFor(computer, near).orElseThrow().location().getString(), "cable, 1m", "right against the computer");
			helper.assertValueEqual(entryFor(computer, next).orElseThrow().location().getString(), "cable, 2m", "one screen between");
			helper.assertValueEqual(entryFor(computer, far).orElseThrow().location().getString(), "cable, 4m", "two screens and a cable");
			if (!computer.machine().isPowered()) {
				computer.pressPower();
			}
			helper.assertTrue(showsText(far.display()), "the console reaches the end of the row");
		});
	}

	@GameTest(maxTicks = 100)
	public void aDisconnectedScreenStillPassesTheConnectionOn(GameTestHelper helper) {
		// Disconnecting is one computer's choice; the screen is still physically there for others.
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		ScreenBlockEntity near = screen(helper, new BlockPos(2, 1, 1), Direction.SOUTH);
		ScreenBlockEntity far = screen(helper, new BlockPos(3, 1, 1), Direction.SOUTH);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(onBus(computer, far), "found through the near screen"))
			.thenExecute(() -> computer.setConnected("id:" + near.id(), false))
			.thenIdle(25) // a scan
			.thenExecute(() -> {
				helper.assertTrue(!onBus(computer, near), "the near screen is unplugged");
				helper.assertTrue(onBus(computer, far), "but still carries the connection to the far one");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aScreensGlassDoesNotConnect(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, false);
		ScreenBlockEntity facing = screen(helper, new BlockPos(2, 1, 1), Direction.WEST); // glass against the computer
		helper.startSequence()
			.thenIdle(45) // a couple of scans
			.thenExecute(() -> helper.assertTrue(entryFor(computer, facing).isEmpty(), "a screen's glass has no port"))
			.thenSucceed();
	}

	@GameTest
	public void cabledDevicesAndDisconnectionsSurviveSaveAndLoad(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, COMPUTER, true);
		ScreenBlockEntity screen = wiredScreen(helper);
		String key = "id:" + screen.id();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(entryFor(computer, screen).isPresent(), "found"))
			.thenExecute(() -> {
				computer.pressPower();
				computer.setConnected(key, false);
				var registries = helper.getLevel().registryAccess();
				var loaded = (AllInOneBlockEntity) BlockEntity.loadStatic(computer.getBlockPos(), computer.getBlockState(),
					computer.saveWithFullMetadata(registries), registries);
				var entry = loaded.busEntries().stream().filter(e -> e.key().equals(key)).findFirst()
					.orElseThrow(() -> helper.assertionException("the screen's row after reload"));
				helper.assertTrue(!entry.connected(), "still disconnected after reload");
				var console = loaded.machine().board().virtualBus().console();
				helper.assertTrue(console.screen().row(0).contains("hello"), "the console remembers what it showed");

				var reloadedScreen = (ScreenBlockEntity) BlockEntity.loadStatic(screen.getBlockPos(), screen.getBlockState(),
					screen.saveWithFullMetadata(registries), registries);
				helper.assertValueEqual(reloadedScreen.id(), screen.id(), "a screen keeps its id");
			})
			.thenSucceed();
	}

	@GameTest
	public void screensAndCablesDropThemselves(GameTestHelper helper) {
		ScreenBlockEntity screen = screen(helper, new BlockPos(1, 1, 1), Direction.SOUTH);
		var drops = Block.getDrops(screen.getBlockState(), helper.getLevel(), screen.getBlockPos(), screen);
		helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.SCREEN.asItem()), "a screen drops itself, got " + drops);
		cable(helper, new BlockPos(2, 1, 1));
		var cableDrops = Block.getDrops(helper.getBlockState(new BlockPos(2, 1, 1)), helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), null);
		helper.assertTrue(cableDrops.size() == 1 && cableDrops.getFirst().is(ModBlocks.CABLE.asItem()), "a cable drops itself, got " + cableDrops);
		helper.succeed();
	}
}
