package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.DiskStationBlock;
import com.payonel.modularcomputers.block.DiskStationBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.device.DiskStation;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.menu.DiskStationMenu;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

/**
 * The Disk Station in the world (user design): its actions on its disk's files (on the host), power
 * from its battery, its GUI's buttons, a wired computer's bus holding it and its disks (the source
 * only read), and its recipe using a disk up (its files go).
 */
public class DiskStationGameTest {
	private static final BlockPos POS = new BlockPos(3, 1, 3);
	/** How long the test computer's program waits at a time (it waits on, so its devices take data). */
	private static final double WAIT_SECONDS = 1000;

	/** A station facing south, with a tier 1 disk to work on and, when {@code battery}, a charged battery. */
	private static DiskStationBlockEntity station(GameTestHelper helper, boolean battery) {
		helper.setBlock(POS, ModBlocks.DISK_STATION.defaultBlockState().setValue(DiskStationBlock.FACING, Direction.SOUTH));
		DiskStationBlockEntity station = helper.getBlockEntity(POS, DiskStationBlockEntity.class);
		station.cards().setItem(DiskStationBlockEntity.TARGET, new ItemStack(ModItems.TIER1_HARD_DISK));
		if (battery) {
			station.cards().setItem(DiskStationBlockEntity.BATTERY, ModItems.TIER1_BATTERY.charged());
		}
		return station;
	}

	private static Path folder(GameTestHelper helper, DiskStationBlockEntity station, int slot) {
		UUID serial = station.cards().getItem(slot).get(ModComponents.SERIAL);
		return AllInOneBlockEntity.diskFolder(helper.getLevel().getServer(), serial);
	}

	private static List<String> files(Path folder) {
		try (var paths = Files.list(folder)) {
			return paths.map(path -> path.getFileName().toString()).sorted().toList();
		} catch (java.io.IOException e) {
			return List.of();
		}
	}

	@GameTest
	public void itsActionsChangeItsDisksFilesOnTheHost(GameTestHelper helper) {
		DiskStationBlockEntity station = station(helper, true);
		Path folder = folder(helper, station, DiskStationBlockEntity.TARGET);
		helper.assertTrue(files(folder).contains("init.lua"), "a new disk comes with mcos: " + files(folder));
		station.act("format");
		helper.assertValueEqual(files(folder), List.of(), "formatted: empty, on the host too");
		station.act("install");
		helper.assertTrue(files(folder).contains("init.lua") && files(folder).contains("bin"), "mcos installed");
		station.act("wipe");
		helper.assertTrue(!Files.exists(folder), "wiped: no filesystem (a raw disk)");
		helper.assertValueEqual(station.disk(DiskStationBlockEntity.TARGET).getMode(new com.payonel.modularcomputers.runtime.Arguments(List.of())).first(),
			"raw", "raw");
		helper.succeed();
	}

	@GameTest
	public void withoutPowerItDoesNothing(GameTestHelper helper) {
		DiskStationBlockEntity station = station(helper, false);
		Path folder = folder(helper, station, DiskStationBlockEntity.TARGET);
		station.act("format");
		helper.assertTrue(files(folder).contains("init.lua"), "nothing done");
		DiskStationMenu menu = (DiskStationMenu) station.createMenu(0, GameTests.serverPlayer(helper).getInventory(), GameTests.serverPlayer(helper));
		helper.assertValueEqual(menu.outcome(), DiskStationBlockEntity.Outcome.NO_POWER, "it says why");
		helper.succeed();
	}

	@GameTest
	public void itsGuiButtonsAreItsActions(GameTestHelper helper) {
		DiskStationBlockEntity station = station(helper, true);
		var player = GameTests.serverPlayer(helper);
		DiskStationMenu menu = (DiskStationMenu) station.createMenu(0, player.getInventory(), player);
		menu.clickMenuButton(player, DiskStationMenu.ACTIONS.indexOf("format"));
		helper.assertValueEqual(menu.outcome(), DiskStationBlockEntity.Outcome.DONE, "formatted");
		helper.assertValueEqual(files(folder(helper, station, DiskStationBlockEntity.TARGET)), List.of(), "empty");
		menu.clickMenuButton(player, DiskStationMenu.ACTIONS.indexOf("clone"));
		helper.assertValueEqual(menu.outcome(), DiskStationBlockEntity.Outcome.NO_SOURCE, "no source to clone");
		menu.removed(player); // closing it leaves its disks in it
		helper.assertTrue(station.cards().getItem(DiskStationBlockEntity.TARGET).is(ModItems.TIER1_HARD_DISK), "still in it");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aWiredComputerHasItAndItsDisksOnItsBusTheSourceOnlyRead(GameTestHelper helper) {
		DiskStationBlockEntity station = station(helper, false);
		station.cards().setItem(DiskStationBlockEntity.SOURCE, new ItemStack(ModItems.TIER1_HARD_DISK));
		BlockPos casePos = POS.west(2);
		helper.setBlock(casePos, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(casePos, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(WAIT_SECONDS, "nothing");
			}
		});
		BlockPos cable = helper.absolutePos(POS.west());
		helper.getLevel().setBlock(cable, CableBlock.withConnections(helper.getLevel(), cable, ModBlocks.CABLE.defaultBlockState()), Block.UPDATE_ALL);
		computer.pressPower();
		helper.succeedWhen(() -> {
			var devices = computer.machine().board().bus().devices();
			helper.assertTrue(devices.contains(station.device()), "the station on its bus");
			helper.assertTrue(devices.contains(station.disk(DiskStationBlockEntity.TARGET)), "its disk");
			helper.assertTrue(devices.contains(station.disk(DiskStationBlockEntity.SOURCE)), "its source");
			helper.assertTrue(station.disk(DiskStationBlockEntity.SOURCE).isReadOnly(), "only read");
			helper.assertTrue(station.device().power().powered(), "powered by the bus");
		});
	}

	@GameTest
	public void craftingItUsesItsDiskUpAndTheDisksFilesGo(GameTestHelper helper) throws Exception {
		ItemStack used = new ItemStack(ModItems.TIER1_HARD_DISK);
		UUID serial = UUID.randomUUID();
		used.set(ModComponents.SERIAL, serial);
		Path folder = AllInOneBlockEntity.diskFolder(helper.getLevel().getServer(), serial);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("notes.txt"), "old");
		List<ItemStack> cells = new ArrayList<>();
		for (String row : List.of("QQQ", "mhm", "FPF")) {
			for (char cell : row.toCharArray()) {
				cells.add(switch (cell) {
					case 'Q' -> new ItemStack(ModItems.CHIP);
					case 'm' -> new ItemStack(net.minecraft.world.item.Items.EMERALD);
					case 'h' -> used;
					case 'F' -> new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK);
					default -> new ItemStack(net.minecraft.world.item.Items.PISTON);
				});
			}
		}
		CraftingInput input = CraftingInput.of(3, 3, cells);
		ItemStack made = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.is(ModBlocks.DISK_STATION.asItem()), "a Disk Station");
		helper.assertTrue(Files.exists(folder), "a preview of the craft deletes nothing");
		made.getItem().onCraftedBy(made, GameTests.serverPlayer(helper)); // the player takes it
		helper.assertTrue(!Files.exists(folder), "the disk it used lost its files");
		helper.assertTrue(!made.has(ModComponents.WIPED), "and it carries nothing of them");
		helper.succeed();
	}
}
