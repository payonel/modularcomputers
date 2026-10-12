package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server game tests for the all-in-one computer block. */
public class AllInOneGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.ALL_IN_ONE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(POS, AllInOneBlockEntity.class);
	}

	/** Puts parts into the case's slots, as the case view would, and a charged battery to run on. */
	private static AllInOneBlockEntity install(AllInOneBlockEntity computer, boolean cpu, boolean eeprom) {
		computer.parts().setItem(CaseMenu.CPU_SLOT, cpu ? new ItemStack(ModItems.CPU) : ItemStack.EMPTY);
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, eeprom ? new ItemStack(ModItems.EEPROM) : ItemStack.EMPTY);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		if (eeprom) {
			TestPrograms.flash(computer, TestPrograms.HELLO);
		}
		return computer;
	}

	@GameTest
	public void placedComputerIsOffAndEmptyWithItsDevicesOnTheBus(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		Machine machine = computer.machine();
		helper.assertTrue(!machine.isPowered(), "a new computer should be off");
		helper.assertTrue(computer.terminal().buffer().isBlank(), "an off computer's display should be blank");
		helper.assertTrue(computer.parts().isEmpty(), "a new computer has no CPU or EEPROM");
		helper.assertTrue(machine.board().cpu().isEmpty() && machine.board().eeprom().isEmpty(), "nothing installed");
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false);
		helper.assertValueEqual(machine.board().bus().devices().stream().map(device -> device.type()).toList(),
			List.of(DeviceType.TERMINAL), "physical bus: just the built-in terminal");
		helper.succeed();
	}

	@GameTest
	public void slotsInstallPartsAndTheCpuShowsInTheBlockState(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		helper.assertTrue(computer.machine().board().cpu().isPresent(), "CPU slot should install a CPU");
		helper.assertTrue(computer.machine().board().eeprom().isPresent(), "EEPROM slot should install an EEPROM");
		helper.assertBlockProperty(POS, AllInOneBlock.READY, true);
		install(computer, false, true);
		helper.assertTrue(computer.machine().board().cpu().isEmpty(), "emptying the slot removes the CPU");
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false);
		helper.succeed();
	}

	@GameTest
	public void slotsOnlyTakeTheirOwnPart(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CaseMenu menu = (CaseMenu) computer.createMenu(0, player.getInventory(), player);
		var parts = new java.util.ArrayList<>(List.of(ModItems.CPU, ModItems.EEPROM, ModItems.RAM, ModItems.HARD_DISK));
		parts.addAll(ModItems.CARDS.keySet());
		parts.addAll(ModItems.BATTERIES);
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			for (var part : parts) {
				boolean placed = menu.slots.get(slot).mayPlace(new ItemStack(part));
				helper.assertValueEqual(placed, takes(slot) == part || slot >= CaseMenu.EXP_SLOTS && ModItems.CARDS.containsKey(part)
					|| CaseMenu.isBatteryBay(slot) && ModItems.BATTERIES.contains(part),
					"slot " + slot + " taking " + part);
			}
			helper.assertValueEqual(menu.slots.get(slot).getMaxStackSize(), 1, "slot " + slot + " holds one");
		}
		helper.succeed();
	}

	/** The one part each non-expansion slot takes. */
	private static net.minecraft.world.item.Item takes(int slot) {
		if (slot == CaseMenu.CPU_SLOT) {
			return ModItems.CPU;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return ModItems.EEPROM;
		} else if (CaseMenu.isBatteryBay(slot)) {
			return null; // any battery
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return ModItems.RAM;
		}
		return slot < CaseMenu.EXP_SLOTS ? ModItems.HARD_DISK : null;
	}

	@GameTest
	public void theEepromAndUpsBatteryAreOnTheBusAndEveryCardKindIsADevice(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.REDSTONE_CARD));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.BLUETOOTH_CARD));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 2, new ItemStack(ModItems.WEATHER_SENSOR_CARD));
		helper.assertValueEqual(busKeys(computer), List.of("terminal", "eeprom", "ups:0", "exp:0", "exp:1", "exp:2"), "default order");
		helper.assertValueEqual(busTypes(computer), List.of(DeviceType.TERMINAL, DeviceType.FIRMWARE, DeviceType.POWER,
			DeviceType.REDSTONE, DeviceType.NETWORK, DeviceType.SENSOR), "each reports its class");
		var entries = computer.busEntries();
		helper.assertValueEqual(entries.get(1).location().getString(), "ROM", "the EEPROM sits in the ROM socket");
		helper.assertValueEqual(entries.get(1).slot(), CaseMenu.EEPROM_SLOT, "its row goes with its slot");
		helper.assertValueEqual(entries.get(2).location().getString(), "UPS 1", "the first bay");
		helper.assertValueEqual(entries.get(3).name().getString(), "Redstone Card", "card names");
		var bus = computer.machine().board().bus();
		for (String address : List.of("eeprom", "ups:0", "exp:0")) {
			String id = bus.id(address);
			helper.assertTrue(id != null && id.startsWith("id:") && bus.device(id).equals(bus.device(address)), "every part has an id it answers to: " + address);
		}
		helper.assertTrue(computer.machine().board().eeprom().isPresent(), "the CPU still finds the EEPROM in its slot");
		helper.succeed();
	}

	private static List<DeviceType> busTypes(AllInOneBlockEntity computer) {
		return computer.machine().board().bus().devices().stream().map(device -> device.type()).toList();
	}

	/** The bus order's keys, the built-in terminal's id shown as "terminal". */
	static List<String> busKeys(AllInOneBlockEntity computer) {
		String terminal = computer.machine().board().address(Motherboard.TERMINAL);
		return computer.busEntries().stream().map(AllInOneBlockEntity.BusEntry::key).map(key -> key.equals(terminal) ? "terminal" : key).toList();
	}

	@GameTest
	public void drivesAndCardsJoinTheBusInTheirSlotsOrder(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.SATA_SLOTS + 1, new ItemStack(ModItems.HARD_DISK));
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.WLAN_CARD));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.RAM)); // RAM is not a bus device
		helper.assertValueEqual(busKeys(computer), List.of("terminal", "sata:1", "exp:0"), "attached in default order");
		helper.assertValueEqual(busTypes(computer), List.of(DeviceType.TERMINAL, DeviceType.STORAGE, DeviceType.NETWORK), "bus");

		helper.assertTrue(!computer.moveDevice(2, true) && !computer.moveDevice(1, false), "the case's devices don't move");
		helper.assertValueEqual(computer.busEntries().stream().map(AllInOneBlockEntity.BusEntry::position).toList(), List.of(1, 7, 10),
			"numbered by fixed place, with gaps for empty slots");

		java.util.UUID serial = computer.parts().getItem(CaseMenu.EXP_SLOTS).get(com.payonel.modularcomputers.item.ModComponents.SERIAL);
		helper.assertTrue(serial != null, "a card gets a serial number in a computer");
		helper.assertValueEqual(computer.machine().board().bus().device("id:" + serial), computer.machine().board().bus().device("exp:0"),
			"a card answers to its id as well as its slot's path");

		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.HARD_DISK));
		helper.assertValueEqual(busKeys(computer), List.of("terminal", "sata:0", "sata:1", "exp:0"), "a new drive goes in its slot's place");
		computer.parts().setItem(CaseMenu.EXP_SLOTS, ItemStack.EMPTY);
		helper.assertValueEqual(busKeys(computer), List.of("terminal", "sata:0", "sata:1"), "a removed card drops out");
		helper.succeed();
	}

	@GameTest
	public void busEntriesNameEachDeviceAndWhereItIs(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.SATA_SLOTS + 2, new ItemStack(ModItems.HARD_DISK));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 3, new ItemStack(ModItems.WLAN_CARD));
		var entries = computer.busEntries();
		helper.assertValueEqual(entries.get(0).name().getString(), "Terminal", "built-in name");
		helper.assertValueEqual(entries.get(0).location().getString(), "built-in", "built-in location");
		helper.assertValueEqual(entries.get(1).name().getString(), "Hard Disk", "drive name");
		helper.assertValueEqual(entries.get(1).location().getString(), "SATA 3", "drive location");
		helper.assertValueEqual(entries.get(2).location().getString(), "EXP 4", "card location");
		helper.succeed();
	}

	@GameTest
	public void theCaseOrderIsItsSlotsOrderAfterAReloadToo(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.WLAN_CARD));
		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.HARD_DISK));
		var registries = helper.getLevel().registryAccess();
		var loaded = (AllInOneBlockEntity) BlockEntity.loadStatic(computer.getBlockPos(), computer.getBlockState(),
			computer.saveWithFullMetadata(registries), registries);
		helper.assertValueEqual(busKeys(loaded), List.of("terminal", "sata:0", "exp:0"), "bus order after reload");
		helper.assertValueEqual(busTypes(loaded).get(1), DeviceType.STORAGE, "the machine's bus after reload");
		helper.succeed();
	}

	@GameTest
	public void withoutCpuPressingPowerIsRefusedWithABriefGreenFlash(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), false, true);
		helper.assertValueEqual(computer.pressPower(), Machine.PowerPress.NO_CPU, "press result");
		helper.assertTrue(!computer.machine().isPowered(), "a computer without a CPU stays off");
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false);
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true); // the attempt shows faded green
		helper.assertTrue(computer.terminal().buffer().isBlank(), "nothing runs without a CPU");
		helper.runAfterDelay(AllInOneBlock.ATTEMPT_TICKS + 1, () -> {
			helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false); // back to faded red
			helper.assertTrue(!computer.machine().isPowered(), "still off");
			helper.succeed();
		});
	}

	@GameTest
	public void pullingTheCpuFromARunningComputerTurnsItOff(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		computer.pressPower();
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true);
		computer.parts().setItem(CaseMenu.CPU_SLOT, ItemStack.EMPTY);
		helper.assertTrue(!computer.machine().isPowered(), "pulling the CPU should cut power");
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false);
		helper.assertBlockProperty(POS, AllInOneBlock.READY, false);
		helper.assertTrue(computer.terminal().buffer().isBlank(), "the display goes dark");
		helper.succeed();
	}

	@GameTest
	public void insertingACpuDoesNotTurnTheComputerOn(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), false, true);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.CPU));
		helper.assertTrue(!computer.machine().isPowered(), "a new CPU waits for the power button");
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false);
		helper.assertBlockProperty(POS, AllInOneBlock.READY, true);
		helper.succeed();
	}

	@GameTest
	public void cpuWithoutEepromShowsTheAbortScreen(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, false);
		computer.pressPower();
		TextBuffer screen = computer.terminal().buffer();
		helper.assertValueEqual(screen.row(0).strip(), Cpu.ABORT + Cpu.NO_EEPROM, "abort message");
		helper.assertValueEqual(screen.background(10, 10), Cpu.ABORT_BACKGROUND, "abort screen background");
		helper.succeed();
	}

	@GameTest
	public void sneakRightClickTogglesPower(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		Machine machine = computer.machine();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);

		helper.useBlock(POS, player);
		helper.assertTrue(machine.isPowered(), "sneak + right-click should turn the computer on");
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true);
		helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), TestPrograms.GREETING, "first display row");

		helper.useBlock(POS, player);
		helper.assertTrue(!machine.isPowered(), "a second sneak + right-click should turn it off");
		helper.assertTrue(computer.terminal().buffer().isBlank(), "turning off should blank the display");
		helper.succeed();
	}

	/** A right-click on the computer's back face at texel (u, v), as seen from behind. Placed facing south. */
	private static BlockHitResult backClick(GameTestHelper helper, double u, double v) {
		// Facing south, the back faces north; from behind (looking south) right is west (-X).
		BlockPos abs = helper.absolutePos(POS);
		Vec3 location = new Vec3(abs.getX() + 1 - u / 16, abs.getY() + 1 - v / 16, abs.getZ());
		return new BlockHitResult(location, Direction.NORTH, abs, false);
	}

	@GameTest
	public void backPowerButtonTogglesWithoutSneaking(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		helper.useBlock(POS, player, backClick(helper, 11.5, 11.5));
		helper.assertTrue(computer.machine().isPowered(), "the back power button should turn the computer on");
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true);
		helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), TestPrograms.GREETING, "first display row");

		helper.useBlock(POS, player, backClick(helper, 11.5, 11.5));
		helper.assertTrue(!computer.machine().isPowered(), "pressing it again should turn the computer off");
		helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false);
		helper.succeed();
	}

	@GameTest
	public void restOfTheBackDoesNotTogglePower(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.useBlock(POS, player, backClick(helper, 4.5, 11.5));
		helper.useBlock(POS, player, backClick(helper, 7.5, 4.5));
		helper.assertTrue(!computer.machine().isPowered(), "only the power button should toggle power");
		helper.succeed();
	}

	@GameTest
	public void caseWindowOnTheBackOpensTheCaseView(GameTestHelper helper) {
		place(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.useBlock(POS, player, backClick(helper, 7.5, 4.5));
		helper.assertTrue(player.containerMenu instanceof CaseMenu menu && menu.pos().equals(helper.absolutePos(POS)),
			"the case window should open this computer's case view, got " + player.containerMenu);
		helper.succeed();
	}

	@GameTest
	public void giveAllGivesEveryModItem(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int given = ModCommands.giveAll(player);
		helper.assertValueEqual(given, ModCommands.modItems().size(), "items given");
		for (var item : List.of(ModBlocks.ALL_IN_ONE.asItem(), ModItems.CPU, ModItems.EEPROM)) {
			helper.assertTrue(player.getInventory().contains(new ItemStack(item)), "should have " + item);
		}
		var hotbar = java.util.stream.IntStream.range(0, ModCommands.hotbarFirst().size())
			.mapToObj(slot -> player.getInventory().getItem(slot).getItem()).toList();
		helper.assertValueEqual(hotbar, ModCommands.hotbarFirst(), "the computer's essentials first, in the hotbar");
		helper.succeed();
	}

	@GameTest
	public void plainRightClickDoesNotTogglePower(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		helper.useBlock(POS, helper.makeMockPlayer(GameType.SURVIVAL));
		helper.assertTrue(!computer.machine().isPowered(), "right-click without sneaking should only open the display");
		helper.succeed();
	}

	@GameTest
	public void stateSurvivesSaveAndLoad(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, false);
		computer.pressPower(); // abort screen: colored cells and a moved cursor
		var registries = helper.getLevel().registryAccess();
		BlockState state = computer.getBlockState();
		BlockEntity loaded = BlockEntity.loadStatic(computer.getBlockPos(), state, computer.saveWithFullMetadata(registries), registries);
		helper.assertTrue(loaded instanceof AllInOneBlockEntity, "should reload as an all-in-one");
		AllInOneBlockEntity reloaded = (AllInOneBlockEntity) loaded;
		helper.assertTrue(reloaded.machine().isPowered(), "a powered computer should still be powered after reload");
		helper.assertTrue(reloaded.parts().getItem(CaseMenu.CPU_SLOT).is(ModItems.CPU), "CPU after reload");
		helper.assertTrue(reloaded.machine().board().cpu().isPresent(), "CPU installed after reload");
		helper.assertValueEqual(reloaded.terminal().buffer().rows(), computer.terminal().buffer().rows(), "display text after reload");
		helper.assertValueEqual(reloaded.terminal().buffer().background(3, 3), Cpu.ABORT_BACKGROUND, "display colors after reload");
		var console = reloaded.machine().board().virtualBus().console();
		helper.assertValueEqual(console.decoder().row(), 1, "console cursor after reload");
		helper.assertValueEqual(java.util.Arrays.toString(console.decoder().state()),
			java.util.Arrays.toString(computer.machine().board().virtualBus().console().decoder().state()), "console colors after reload");
		helper.assertValueEqual(java.util.Arrays.toString(reloaded.terminal().decoder().state()),
			java.util.Arrays.toString(computer.terminal().decoder().state()), "the terminal's cursor and colors after reload");
		helper.succeed();
	}

	@GameTest
	public void dropsItself(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		var drops = Block.getDrops(computer.getBlockState(), helper.getLevel(), helper.absolutePos(POS), computer);
		helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.ALL_IN_ONE.asItem()),
			"should drop one all-in-one computer, got " + drops);
		helper.succeed();
	}

	@GameTest
	public void breakingDropsItsParts(GameTestHelper helper) {
		install(place(helper), true, true);
		helper.destroyBlock(POS);
		helper.assertItemEntityPresent(ModItems.CPU, POS, 2);
		helper.assertItemEntityPresent(ModItems.EEPROM, POS, 2);
		helper.succeed();
	}
}
