package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ExpansionHubBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.block.RoboticPistonBlock;
import com.payonel.modularcomputers.block.RoboticPistonBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.item.ModComponents;
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
		AllInOneBlockEntity computer = helper.getBlockEntity(COMPUTER, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU)); // no CPU, no bus
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		return computer;
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
		helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.TIER3_SCREEN.defaultBlockState().setValue(ScreenBlock.FACING, Direction.SOUTH));
		ScreenBlockEntity screen = helper.getBlockEntity(new BlockPos(4, 1, 1), ScreenBlockEntity.class);
		helper.succeedWhen(() -> {
			var cabled = computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).toList();
			helper.assertValueEqual(cabled.stream().map(entry -> entry.name().getString()).toList(),
				List.of("Audio Card", "Redstone Card", "Tier 3 Screen"), "the hub's cards, then the screen beyond it");
			helper.assertValueEqual(locations(computer), List.of("hub 1, 2m", "hub 3, 2m", "cable, 3m"), "where each is");
			helper.assertTrue(computer.machine().board().bus().devices().stream().anyMatch(device -> device.type() == DeviceType.REDSTONE),
				"the redstone card is on the bus");
			helper.assertTrue(computer.machine().board().bus().devices().contains(screen.terminal()), "the hub passes the connection on");
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
				helper.assertTrue(computer.setConnected("id:" + hub.cards().getItem(0).get(ModComponents.SERIAL), false), "unplug the audio card");
				var types = computer.machine().board().bus().devices().stream().map(device -> device.type()).toList();
				helper.assertTrue(!types.contains(DeviceType.AUDIO) && types.contains(DeviceType.NETWORK), "only the audio card leaves the bus");
				hub.cards().setItem(1, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 1m", "offline"), "a card taken out of the hub goes offline"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aHubCardKeepsItsPlaceWhenItMovesToAnotherSlot(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(2, 1, 1));
		hub.cards().setItem(0, new ItemStack(ModItems.WLAN_CARD));
		hub.cards().setItem(1, new ItemStack(ModItems.BLUETOOTH_CARD));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(names(computer), List.of("WLAN Card", "Bluetooth Card"), "both cards"))
			.thenExecute(() -> {
				helper.assertTrue(hub.cards().getItem(0).has(ModComponents.SERIAL), "a card gets a serial number in a hub");
				hub.cards().removeItem(1, 1); // the Bluetooth card comes out: missing
			})
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("hub 1, 1m", "offline"), "the Bluetooth card is offline"))
			.thenExecute(() -> hub.cards().setItem(1, hub.cards().removeItem(0, 1))) // the WLAN card moves into its slot
			.thenWaitUntil(() -> helper.assertValueEqual(locations(computer), List.of("hub 2, 1m", "offline"), "the WLAN card moved"))
			.thenExecute(() -> helper.assertValueEqual(names(computer), List.of("WLAN Card", "Bluetooth Card"),
				"one WLAN row, in its place, and the Bluetooth card still offline"))
			.thenSucceed();
	}

	private static List<String> names(AllInOneBlockEntity computer) {
		return computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).map(entry -> entry.name().getString()).toList();
	}

		@GameTest
	public void aHubTakesCardsAndAPistonOnlyABluetoothCard(GameTestHelper helper) {
		ExpansionHubBlockEntity hub = hub(helper, new BlockPos(1, 1, 1));
		RoboticPistonBlockEntity piston = piston(helper, new BlockPos(3, 1, 1), Direction.DOWN);
		for (var card : ModItems.CARDS.keySet()) {
			helper.assertValueEqual(hub.cards().canPlaceItem(0, new ItemStack(card)), card != ModItems.BLUETOOTH_CARD,
				"a hub's expansion slot taking " + card + " (Bluetooth goes in the radio slot)");
			helper.assertValueEqual(piston.cards().canPlaceItem(0, new ItemStack(card)), card == ModItems.BLUETOOTH_CARD, "a piston taking " + card);
		}
		helper.assertTrue(!hub.cards().canPlaceItem(0, new ItemStack(ModItems.TIER3_CPU)), "a hub takes only cards");
		// The menu the player clicks in follows the same rule.
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var menu = hub.createMenu(0, player.getInventory(), player);
		for (int slot = 0; slot <= ExpansionHubBlockEntity.SLOTS; slot++) {
			for (var card : ModItems.CARDS.keySet()) {
				helper.assertValueEqual(menu.slots.get(slot).mayPlace(new ItemStack(card)), hub.cards().canPlaceItem(slot, new ItemStack(card)),
					"the menu's slot " + slot + " taking " + card);
			}
		}
		helper.assertValueEqual(hub.cards().getContainerSize(), ExpansionHubBlockEntity.SLOTS + 1, "four slots and the radio slot");
		helper.assertTrue(!hub.cards().canPlaceItem(ExpansionHubBlockEntity.RADIO_SLOT, new ItemStack(ModItems.AUDIO_CARD)), "the radio slot takes only Bluetooth");
		helper.assertTrue(hub.cards().canPlaceItem(ExpansionHubBlockEntity.RADIO_SLOT, new ItemStack(ModItems.BLUETOOTH_CARD)), "the radio slot takes Bluetooth");
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
	public void hubsAndPistonsKeepTheirCardsInsideWhenBroken(GameTestHelper helper) {
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
			helper.assertTrue(drops.getFirst().has(net.minecraft.core.component.DataComponents.CONTAINER), "with its card inside");
		}
		helper.destroyBlock(new BlockPos(1, 1, 1));
		helper.destroyBlock(new BlockPos(3, 1, 1));
		helper.assertItemEntityNotPresent(ModItems.MOTION_SENSOR_CARD, new BlockPos(1, 1, 1), 2);
		helper.assertItemEntityNotPresent(ModItems.BLUETOOTH_CARD, new BlockPos(3, 1, 1), 2);
		helper.succeed();
	}

	@GameTest
	public void aPaintGunIsOnTheBusAndTakesNoCableAtItsNozzle(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		cable(helper, new BlockPos(2, 1, 1));
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.PAINT_GUN.defaultBlockState().setValue(com.payonel.modularcomputers.block.PaintGunBlock.FACING, Direction.WEST));
		helper.assertBlockProperty(new BlockPos(2, 1, 1), CableBlock.EAST, false); // the nozzle faces the cable
		helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.PAINT_GUN.defaultBlockState().setValue(com.payonel.modularcomputers.block.PaintGunBlock.FACING, Direction.UP));
		helper.assertBlockProperty(new BlockPos(2, 1, 1), CableBlock.EAST, true);
		helper.succeedWhen(() -> {
			var entries = computer.busEntries().stream().filter(AllInOneBlockEntity.BusEntry::cabled).toList();
			helper.assertValueEqual(entries.stream().map(entry -> entry.name().getString()).toList(), List.of("Paint Gun"), "the paint gun is listed");
			helper.assertTrue(computer.machine().board().bus().devices().stream().anyMatch(device -> device.type() == DeviceType.ACTUATOR), "on the bus");
		});
	}

	@GameTest
	public void aScreensBackOpensItsRadioSlotAndItsFrontTheDisplay(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.TIER3_SCREEN.defaultBlockState().setValue(ScreenBlock.FACING, Direction.SOUTH));
		var player = helper.makeMockServerPlayerInLevel();
		BlockPos screen = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.useBlock(new BlockPos(1, 1, 1), player, new net.minecraft.world.phys.BlockHitResult(
			net.minecraft.world.phys.Vec3.atCenterOf(screen).add(0, 0, -0.5), Direction.NORTH, screen, false));
		helper.assertTrue(player.containerMenu instanceof com.payonel.modularcomputers.menu.CardHolderMenu menu && menu.cardSlots() == 1,
			"the back opens the radio slot, got " + player.containerMenu);
		player.closeContainer();
		helper.useBlock(new BlockPos(1, 1, 1), player, new net.minecraft.world.phys.BlockHitResult(
			net.minecraft.world.phys.Vec3.atCenterOf(screen).add(0, 0, 0.5), Direction.SOUTH, screen, false));
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "the glass opens the display, not the slot");
		helper.succeed();
	}
}
