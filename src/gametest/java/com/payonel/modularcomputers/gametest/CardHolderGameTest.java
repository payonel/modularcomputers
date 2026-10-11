package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.item.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Server game tests for the blocks that hold cards: the expansion hub and the robotic piston. */
public class CardHolderGameTest {
	private static final BlockPos COMPUTER = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(COMPUTER, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(COMPUTER, AllInOneBlockEntity.class);
	}

	private static void cable(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		helper.getLevel().setBlock(absolute, CableBlock.withConnections(helper.getLevel(), absolute, ModBlocks.CABLE.defaultBlockState()),
			Block.UPDATE_ALL);
	}

	private static ExpansionHubBlockEntity hub(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.EXPANSION_HUB);
		return helper.getBlockEntity(pos, ExpansionHubBlockEntity.class);
	}

	private static RoboticPistonBlockEntity piston(GameTestHelper helper, BlockPos pos, Direction port) {
		helper.setBlock(pos, ModBlocks.ROBOTIC_PISTON.defaultBlockState().setValue(RoboticPistonBlock.FACING, port));
		return helper.getBlockEntity(pos, RoboticPistonBlockEntity.class);
	}

	private static List<String> locations(AllInOneBlockEntity computer) {
		return computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).map(entry -> entry.location().getString()).toList();
	}

	@GameTest
	public void aHubsCardsAreOnTheBusOfEveryComputerWiredToIt(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		cable(helper, new BlockPos(2, 1, 1));
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(3, 1, 1));
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		hub.cards().setItem(2, new ItemStack(ModItems.REDSTONE_CARD));
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.SCREEN.defaultBlockState().setValue(ScreenBlock.FACING, Direction.SOUTH));
		ScreenBlockEntity screen = helper.getBlockEntity(new BlockPos(4, 1, 1), ScreenBlockEntity.class);
		helper.succeedWhen(() -> {
			var cabled = computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).toList();
			helper.assertValueEqual(cabled.stream().map(entry -> entry.name().getString()).toList(),
				List.of("Audio Card", "Redstone Card", "Screen"), "the hub's cards, then the screen beyond it");
			helper.assertValueEqual(locations(computer), List.of("hub 1, 2m", "hub 3, 2m", "cable, 3m"), "where each is");
			helper.assertTrue(computer.machine().board().bus().devices().stream().anyMatch(device -> device.type() == DeviceType.REDSTONE),
				"the redstone card is on the bus");
			helper.assertTrue(computer.machine().board().bus().devices().contains(screen.display()), "the hub passes the connection on");
		});
	}

	@GameTest
	public void aHubCardCanBeUnpluggedOnItsOwn(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(2, 1, 1)); // right against the computer
		hub.cards().setItem(0, new ItemStack(ModItems.AUDIO_CARD));
		hub.cards().setItem(1, new ItemStack(ModItems.WLAN_CARD));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 1m", "hub 2, 1m"), "both cards"))
			.thenExecute(() -> {
				helper.assertTrue(computer.setConnected("cable:" + hub.id() + ":0", false), "unplug the audio card");
				var types = computer.machine().board().bus().devices().stream().map(device -> device.type()).toList();
				helper.assertTrue(!types.contains(DeviceType.AUDIO) && types.contains(DeviceType.NETWORK), "only the audio card leaves the bus");
				hub.cards().setItem(1, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 1m"), "a card taken out of the hub drops out"))
			.thenSucceed();
	}

	@GameTest
	public void aHubTakesCardsAndAPistonOnlyABluetoothCard(GameTestHelper helper) {
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(1, 1, 1));
		RoboticPistonBlockEntity piston = piston(helper, new BlockPos(3, 1, 1), Direction.DOWN);
		for (var card : ModItems.CARDS.keySet()) {
			helper.assertTrue(hub.cards().canPlaceItem(0, new ItemStack(card)), "a hub takes " + card);
			helper.assertValueEqual(piston.cards().canPlaceItem(0, new ItemStack(card)), card == ModItems.BLUETOOTH_CARD, "a piston taking " + card);
		}
		helper.assertTrue(!hub.cards().canPlaceItem(0, new ItemStack(ModItems.CPU)), "a hub takes only cards");
		helper.assertValueEqual(hub.cards().getContainerSize(), ExpansionHubBlockEntity.SLOTS, "four slots");
		helper.assertValueEqual(piston.cards().getContainerSize(), 1, "one slot");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPistonConnectsOnlyAtItsPort(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		cable(helper, new BlockPos(2, 1, 1));
		piston(helper, new BlockPos(3, 1, 1), Direction.EAST); // port away from the cable
		helper.assertBlockProperty(new BlockPos(2, 1, 1), CableBlock.EAST, false);
		helper.startSequence()
			.thenIdle(25)
			.thenExecute(() -> {
				helper.assertTrue(locations(computer).isEmpty(), "a piston's plates don't take a cable");
				piston(helper, new BlockPos(3, 1, 1), Direction.WEST);
				helper.assertBlockProperty(new BlockPos(2, 1, 1), CableBlock.EAST, true);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("cable, 2m"), "plugged in at its port"))
			.thenExecute(() -> helper.assertTrue(computer.machine().board().bus().devices().stream()
				.anyMatch(device -> device.type() == DeviceType.ACTUATOR), "the piston is on the bus"))
			.thenSucceed();
	}

	@GameTest
	public void hubsAndPistonsKeepTheirCardsAndDropThem(GameTestHelper helper) {
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(1, 1, 1));
		hub.cards().setItem(3, new ItemStack(ModItems.MOTION_SENSOR_CARD));
		var registries = helper.getLevel().registryAccess();
		var loaded = (ExpansionHubBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(hub.getBlockPos(), hub.getBlockState(),
			hub.saveWithFullMetadata(registries), registries);
		helper.assertTrue(loaded.cards().getItem(3).is(ModItems.MOTION_SENSOR_CARD), "the card after reload");
		helper.assertValueEqual(loaded.id(), hub.id(), "the hub keeps its id");
		helper.assertTrue(loaded.card(3).isPresent(), "and its device");
		RoboticPistonBlockEntity piston = piston(helper, new BlockPos(3, 1, 1), Direction.DOWN);
		piston.cards().setItem(0, new ItemStack(ModItems.BLUETOOTH_CARD));
		for (var block : List.of(hub, piston)) {
			var drops = Block.getDrops(block.getBlockState(), helper.getLevel(), block.getBlockPos(), block);
			helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.getBlockState().getBlock().asItem()), "drops itself, got " + drops);
		}
		helper.destroyBlock(new BlockPos(1, 1, 1));
		helper.destroyBlock(new BlockPos(3, 1, 1));
		helper.assertItemEntityPresent(ModItems.MOTION_SENSOR_CARD, new BlockPos(1, 1, 1), 2);
		helper.assertItemEntityPresent(ModItems.BLUETOOTH_CARD, new BlockPos(3, 1, 1), 2);
		helper.succeed();
	}
}
