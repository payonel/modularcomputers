package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
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

	/**
	 * Puts parts into the case's slots, as the case view would, a charged battery to run on, and a
	 * screen in the gold slot to show on.
	 */
	private static AllInOneBlockEntity install(AllInOneBlockEntity computer, boolean cpu, boolean eeprom) {
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, cpu ? new ItemStack(ModItems.TIER3_CPU) : ItemStack.EMPTY);
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, eeprom ? new ItemStack(ModItems.EEPROM) : ItemStack.EMPTY);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		if (eeprom) {
			TestPrograms.flash(computer, TestPrograms.HELLO);
		}
		return computer;
	}

	@GameTest(maxTicks = 100)
	public void aNewEepromIsLuaThatALuaCpuRuns(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM)); // as crafted: its Lua, not a test's program
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower(); // the Lua CPU runs on its own thread; its calls are carried out on the server thread
		helper.succeedWhen(() -> {
			helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), "boot: no bootable disk", "the EEPROM's bootloader, with no disk");
			helper.assertTrue(computer.machine().isPowered() && computer.machine().runner().isEmpty(), "then halted, power on");
		});
	}

	@GameTest(maxTicks = 100)
	public void thePowerLightIsGreenWhileItRunsAndAmberOnceItsHalted(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		ItemStack eeprom = new ItemStack(ModItems.EEPROM);
		eeprom.set(ModComponents.EEPROM_IMAGE, "board.waitForInterrupt(0.5, \"nothing\") board.halt()");
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, eeprom);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		helper.startSequence()
			.thenIdle(2)
			.thenExecute(() -> {
				helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true);
				helper.assertBlockProperty(POS, AllInOneBlock.HALTED, false); // green: it runs
			})
			.thenWaitUntil(() -> helper.assertBlockProperty(POS, AllInOneBlock.HALTED, true)) // amber: halted, power on
			.thenExecute(() -> {
				helper.assertBlockProperty(POS, AllInOneBlock.POWERED, true);
				computer.pressPower(); // off
			})
			.thenWaitUntil(() -> {
				helper.assertBlockProperty(POS, AllInOneBlock.POWERED, false);
				helper.assertBlockProperty(POS, AllInOneBlock.HALTED, false);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void takingRamOutFromUnderARunningProgramAbortsIt(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		ItemStack eeprom = new ItemStack(ModItems.EEPROM);
		eeprom.set(ModComponents.EEPROM_IMAGE, "board.waitForInterrupt(nil, \"nothing\")");
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, eeprom);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.machine().runner().isPresent(), "it runs"))
			.thenExecute(() -> computer.parts().setItem(CaseMenu.RAM_SLOTS, ItemStack.EMPTY))
			.thenWaitUntil(() -> {
				helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), "ABRT: " + Cpu.RAM_CHANGED, "the CPU aborts");
				helper.assertTrue(computer.machine().isPowered() && computer.machine().runner().isEmpty(), "power stays on, the CPU stopped");
				helper.assertBlockProperty(POS, AllInOneBlock.HALTED, true);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aProgramFlashesTheEepromAndTheNextBootRunsTheNewScript(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		ItemStack chip = new ItemStack(ModItems.EEPROM);
		chip.set(ModComponents.EEPROM_IMAGE, """
			board.invoke("eeprom", "set", 'board.invoke("console", "write", "second script") board.halt()')
			board.invoke("console", "write", "first script")
			board.halt()
			""");
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, chip);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), "first script", "the first boot runs what the chip held"))
			.thenExecute(() -> {
				helper.assertTrue(computer.parts().getItem(CaseMenu.EEPROM_SLOT).get(ModComponents.EEPROM_IMAGE).contains("second script"),
					"the program's write is on the item");
				computer.pressPower(); // off
				computer.pressPower(); // on: the CPU reads the EEPROM again
			})
			.thenWaitUntil(() -> helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), "second script", "the reboot runs the new script"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void aRunningLuaComputerIsSavedAndGoesOnWhereItWasAfterALoad(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		ItemStack eeprom = new ItemStack(ModItems.EEPROM);
		eeprom.set(ModComponents.EEPROM_IMAGE, """
			local total = 0
			for i = 1, 6 do
			  total = total + i
			  board.invoke("console", "write", tostring(i))
			  board.waitForInterrupt(0.5, "nothing")
			end
			board.invoke("console", "write", " total " .. total)
			board.halt()
			""");
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, eeprom);
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		var registries = helper.getLevel().registryAccess();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.terminal().buffer().row(0).strip().startsWith("12"), "it's counting"))
			.thenExecute(() -> {
				// Saved as the world is (its VM, by Styx), then gone, and loaded again.
				net.minecraft.nbt.CompoundTag saved = computer.saveWithoutMetadata(registries);
				helper.assertTrue(saved.contains("program"), "the running program is saved");
				helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
				AllInOneBlockEntity again = place(helper);
				again.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
			})
			.thenWaitUntil(() -> {
				AllInOneBlockEntity again = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
				helper.assertTrue(again.terminal().buffer().row(0).strip().endsWith("6 total 21"), "it goes on: " + again.terminal().buffer().row(0).strip());
				helper.assertTrue(again.machine().isPowered() && again.machine().runner().isEmpty(), "and halts");
			})
			.thenSucceed();
	}

	@GameTest
	public void theJavaCpuRunsTheJavaEepromAndNotLua(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.JAVA_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.pressPower();
		helper.assertValueEqual(computer.terminal().buffer().row(0).strip(), Cpu.ABORT + Cpu.EXEC_FORMAT, "Lua on the Java CPU");
		computer.pressPower();
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.JAVA_EEPROM));
		computer.pressPower();
		helper.assertTrue(computer.machine().runner().isPresent(), "the Java EEPROM's program runs");
		helper.assertTrue(!computer.terminal().buffer().row(0).startsWith(Cpu.ABORT), "no abort");
		helper.succeed();
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
		helper.assertTrue(machine.board().bus().devices().isEmpty() && computer.busEntries().isEmpty(), "no CPU, no bus");
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		helper.assertTrue(machine.board().bus().devices().isEmpty(), "with a CPU and nothing else: an empty bus (no built-in terminal)");
		helper.assertBlockProperty(POS, AllInOneBlock.SCREEN, false);
		TestPrograms.withScreen(computer);
		helper.assertValueEqual(machine.board().bus().devices().stream().map(device -> device.type()).toList(),
			List.of(DeviceType.TERMINAL), "a screen in the gold slot is its terminal");
		helper.assertBlockProperty(POS, AllInOneBlock.SCREEN, true);
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
		var parts = new java.util.ArrayList<>(List.of(ModItems.TIER3_CPU, ModItems.EEPROM, ModItems.TIER1_RAM, ModItems.TIER1_HARD_DISK));
		parts.addAll(ModItems.CARDS.keySet());
		parts.addAll(ModItems.BATTERIES);
		for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
			for (var part : parts) {
				boolean placed = menu.slots.get(slot).mayPlace(new ItemStack(part));
				// (A slot the tier doesn't have takes nothing: tier 3 has 3 SATA slots of the board's 4.)
				helper.assertValueEqual(placed, menu.slots.get(slot).isActive() && (takes(slot) == part || slot >= CaseMenu.EXP_SLOTS && ModItems.CARDS.containsKey(part)
					|| CaseMenu.isBatteryBay(slot) && ModItems.BATTERIES.contains(part)),
					"slot " + slot + " taking " + part);
			}
			helper.assertValueEqual(menu.slots.get(slot).getMaxStackSize(), 1, "slot " + slot + " holds one");
		}
		helper.succeed();
	}

	/** The one part each non-expansion slot takes. */
	private static net.minecraft.world.item.Item takes(int slot) {
		if (slot == CaseMenu.CPU_SLOT) {
			return ModItems.TIER3_CPU;
		} else if (slot == CaseMenu.EEPROM_SLOT) {
			return ModItems.EEPROM;
		} else if (CaseMenu.isBatteryBay(slot)) {
			return null; // any battery
		} else if (slot < CaseMenu.SATA_SLOTS) {
			return ModItems.TIER1_RAM;
		}
		return slot < CaseMenu.EXP_SLOTS ? ModItems.TIER1_HARD_DISK : null;
	}

	@GameTest
	public void theEepromIsOnTheBusAndEveryCardKindIsADeviceButNotTheBatteries(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.REDSTONE_CARD));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.BLUETOOTH_CARD));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 2, new ItemStack(ModItems.WEATHER_SENSOR_CARD));
		helper.assertValueEqual(busKeys(computer), List.of("eeprom", "exp:0", "exp:1", "exp:2"), "default order: no battery");
		helper.assertValueEqual(busTypes(computer), List.of(DeviceType.EEPROM,
			DeviceType.REDSTONE, DeviceType.NETWORK, DeviceType.WEATHER), "each reports its class");
		var entries = computer.busEntries();
		helper.assertValueEqual(entries.get(0).location().getString(), "ROM", "the EEPROM sits in the ROM socket");
		helper.assertValueEqual(entries.get(0).slot(), CaseMenu.EEPROM_SLOT, "its row goes with its slot");
		helper.assertValueEqual(entries.get(1).name().getString(), "Redstone Card", "card names");
		var bus = computer.machine().board().bus();
		for (String address : List.of("eeprom", "exp:0")) {
			String id = bus.id(address);
			helper.assertTrue(id != null && id.startsWith("id:") && bus.device(id).equals(bus.device(address)), "every part has an id it answers to: " + address);
		}
		helper.assertTrue(computer.machine().board().eeprom().isPresent(), "the CPU still finds the EEPROM in its slot");
		helper.succeed();
	}

	private static List<DeviceType> busTypes(AllInOneBlockEntity computer) {
		return computer.machine().board().bus().devices().stream().map(device -> device.type()).toList();
	}

	/** The bus order's keys. */
	static List<String> busKeys(AllInOneBlockEntity computer) {
		return computer.busEntries().stream().map(AllInOneBlockEntity.BusEntry::key).toList();
	}

	@GameTest
	public void drivesAndCardsJoinTheBusInTheirSlotsOrder(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.SATA_SLOTS + 1, new ItemStack(ModItems.TIER1_HARD_DISK));
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.WLAN_CARD));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER1_RAM)); // RAM is not a bus device
		helper.assertValueEqual(busKeys(computer), List.of("sata:1", "exp:0"), "attached in default order");
		helper.assertValueEqual(busTypes(computer), List.of(DeviceType.STORAGE, DeviceType.NETWORK), "bus");

		helper.assertTrue(!computer.moveDevice("exp:0", true) && !computer.moveDevice("sata:1", false), "the case's devices don't move");
		helper.assertValueEqual(computer.busEntries().stream().map(AllInOneBlockEntity.BusEntry::position).toList(), List.of(3, 6),
			"numbered by fixed place, with gaps for empty slots");

		java.util.UUID serial = computer.parts().getItem(CaseMenu.EXP_SLOTS).get(com.payonel.modularcomputers.item.ModComponents.SERIAL);
		helper.assertTrue(serial != null, "a card gets a serial number in a computer");
		helper.assertValueEqual(computer.machine().board().bus().device("id:" + serial), computer.machine().board().bus().device("exp:0"),
			"a card answers to its id as well as its slot's path");

		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		helper.assertValueEqual(busKeys(computer), List.of("sata:0", "sata:1", "exp:0"), "a new drive goes in its slot's place");
		computer.parts().setItem(CaseMenu.EXP_SLOTS, ItemStack.EMPTY);
		helper.assertValueEqual(busKeys(computer), List.of("sata:0", "sata:1"), "a removed card drops out");
		helper.succeed();
	}

	@GameTest
	public void busEntriesNameEachDeviceAndWhereItIs(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.SATA_SLOTS + 2, new ItemStack(ModItems.TIER1_HARD_DISK));
		computer.parts().setItem(CaseMenu.EXP_SLOTS + 3, new ItemStack(ModItems.WLAN_CARD));
		var entries = computer.busEntries();
		TestPrograms.withScreen(computer);
		entries = computer.busEntries();
		helper.assertValueEqual(entries.get(0).name().getString(), "Tier 1 Hard Disk", "drive name");
		helper.assertValueEqual(entries.get(0).location().getString(), "SATA 3", "drive location");
		helper.assertValueEqual(entries.get(1).name().getString(), "Tier 3 Screen", "a screen in the gold slot");
		helper.assertValueEqual(entries.get(1).location().getString(), "EXP 1", "its place");
		helper.assertValueEqual(entries.get(2).location().getString(), "EXP 4", "card location");
		helper.succeed();
	}

	@GameTest
	public void theCaseOrderIsItsSlotsOrderAfterAReloadToo(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModItems.WLAN_CARD));
		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		var registries = helper.getLevel().registryAccess();
		var loaded = (AllInOneBlockEntity) BlockEntity.loadStatic(computer.getBlockPos(), computer.getBlockState(),
			computer.saveWithFullMetadata(registries), registries);
		helper.assertValueEqual(busKeys(loaded), List.of("sata:0", "exp:0"), "bus order after reload");
		helper.assertValueEqual(busTypes(loaded).get(0), DeviceType.STORAGE, "the machine's bus after reload");
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
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
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
		ServerPlayer player = GameTests.serverPlayer(helper);
		helper.useBlock(POS, player, backClick(helper, 7.5, 4.5));
		helper.assertTrue(player.containerMenu instanceof CaseMenu menu && menu.pos().equals(helper.absolutePos(POS)),
			"the case window should open this computer's case view, got " + player.containerMenu);
		helper.succeed();
	}

	@GameTest
	public void itemsStackExceptThoseThatWillHoldData(GameTestHelper helper) {
		for (var item : ModCommands.modItems()) {
			boolean holdsData = ModItems.EEPROMS.contains(item) || ModItems.HARD_DISKS.contains(item);
			helper.assertValueEqual(new ItemStack(item).getMaxStackSize(), holdsData ? 1 : 64, "how many " + item + " stack");
		}
		helper.assertTrue(ItemStack.isSameItemSameComponents(ModItems.TIER1_BATTERY.charged(), ModItems.TIER1_BATTERY.charged()),
			"batteries of the same charge stack");
		helper.assertTrue(!ItemStack.isSameItemSameComponents(ModItems.TIER1_BATTERY.charged(), new ItemStack(ModItems.TIER1_BATTERY)),
			"not with a different charge");
		AllInOneBlockEntity computer = place(helper);
		ItemStack two = new ItemStack(ModItems.TIER1_RAM, 2);
		computer.parts().setItem(CaseMenu.RAM_SLOTS, two.copyWithCount(1));
		helper.assertValueEqual(computer.parts().getMaxStackSize(), 1, "each part slot holds one");
		helper.succeed();
	}

	@GameTest
	public void giveAllGivesEveryModItem(GameTestHelper helper) {
		ServerPlayer player = GameTests.serverPlayer(helper);
		int given = ModCommands.giveAll(player);
		helper.assertValueEqual(given, ModCommands.modItems().size(), "items given");
		for (var item : List.of(ModBlocks.ALL_IN_ONE.asItem(), ModItems.TIER3_CPU, ModItems.EEPROM)) {
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
		helper.assertValueEqual(reloaded.machine().uptime(), computer.machine().uptime(), "its clock goes on from where it was");
		helper.assertTrue(reloaded.parts().getItem(CaseMenu.CPU_SLOT).is(ModItems.TIER3_CPU), "CPU after reload");
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

	/** What a cell of a changed row costs on the wire, at most: its character and its two colors (3 bytes each), and a little. */
	private static final int BYTES_PER_CELL = 8;

	@GameTest
	public void aChangedRowGoesToPlayersAloneNotTheWholeBlock(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), true, true);
		computer.pressPower();
		var registries = helper.getLevel().registryAccess();
		int whole;
		try {
			var bytes = new java.io.ByteArrayOutputStream();
			net.minecraft.nbt.NbtIo.write(computer.getUpdateTag(registries), new java.io.DataOutputStream(bytes));
			whole = bytes.size();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		computer.terminal().buffer().takeDirtyRows(); // sent already
		computer.terminal().write("a");
		var payload = com.payonel.modularcomputers.network.ScreenRowsPayload.changes(computer.getBlockPos(), computer.terminal());
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
		com.payonel.modularcomputers.network.ScreenRowsPayload.CODEC.encode(buf, payload);
		int rows = buf.readableBytes();
		com.payonel.modularcomputers.ModularComputers.LOGGER.info("A typed key: {} bytes as changed rows, {} bytes as the whole block", rows, whole);
		helper.assertValueEqual(payload.rows().size(), 1, "one row changed");
		int columns = computer.terminal().buffer().resolution().columns();
		helper.assertTrue(rows < columns * BYTES_PER_CELL && rows * 10 < whole, "a row is small: " + rows + " bytes (" + columns + " columns), the whole block " + whole);
		helper.succeed();
	}

	@GameTest
	public void theBusHoldsWhatItsCpuTierAllows(GameTestHelper helper) {
		AllInOneBlockEntity computer = install(place(helper), false, true);
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			computer.parts().setItem(CaseMenu.SATA_SLOTS + lane, new ItemStack(ModItems.TIER1_HARD_DISK));
			computer.parts().setItem(CaseMenu.EXP_SLOTS + lane, new ItemStack(ModItems.WLAN_CARD));
		}
		computer.parts().setItem(CaseMenu.UPS_SLOT + 1, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.UPS_SLOT + 2, ModItems.TIER1_BATTERY.charged());
		helper.assertTrue(computer.machine().board().bus().devices().isEmpty(), "no CPU, no bus");
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		var bus = computer.machine().board().bus();
		helper.assertValueEqual(bus.status().capacity(), 4, "a tier 1 CPU's bus");
		helper.assertValueEqual(bus.devices().size(), 4, "the case's own count too: only the first 4 of its 9 fit");
		helper.assertValueEqual(computer.busEntries().size(), 4, "and the bus order lists only those");
		helper.assertTrue(bus.status().overflow(), "and it says so");
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		helper.assertValueEqual(bus.status().capacity(), 64, "a tier 3 CPU's bus");
		helper.assertValueEqual(bus.devices().size(), 9, "everything fits (ROM, 4 disks, 4 cards; batteries aren't on the bus)");
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
	public void breakingKeepsItsPartsInsideItsItem(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper);
		install(computer, true, true);
		var drops = net.minecraft.world.level.block.Block.getDrops(computer.getBlockState(), helper.getLevel(), computer.getBlockPos(), computer);
		helper.assertValueEqual(drops.size(), 1, "one item");
		var inside = drops.getFirst().getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
			net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItemCopyStream().map(ItemStack::getItem).toList();
		helper.assertTrue(inside.contains(ModItems.TIER3_CPU) && inside.contains(ModItems.EEPROM), "with its parts in it, got " + inside);
		helper.destroyBlock(POS);
		helper.assertItemEntityNotPresent(ModItems.TIER3_CPU, POS, 2);
		helper.assertItemEntityNotPresent(ModItems.EEPROM, POS, 2);
		helper.succeed();
	}
}
