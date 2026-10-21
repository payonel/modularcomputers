package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.runtime.Runner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Disks that come with mcos, booting them, and wiping a disk by crafting it alone (user design, 2026-10-13). */
public class DiskGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.TIER1_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(POS, AllInOneBlockEntity.class);
		TestPrograms.withScreen(computer);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER1_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM)); // no RAM, no boot
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		return computer;
	}

	@GameTest
	public void aTier1DiskComesWithMcosItsFilesMadeWhenItsFolderIs(GameTestHelper helper) {
		helper.assertValueEqual(new ItemStack(ModItems.TIER1_HARD_DISK).get(ModComponents.DISK_IMAGE), "mcos", "a new tier 1 disk comes with mcos");
		helper.assertTrue(!new ItemStack(ModItems.TIER2_HARD_DISK).has(ModComponents.DISK_IMAGE), "a tier 2 comes blank");
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		ItemStack disk = computer.parts().getItem(CaseMenu.SATA_SLOTS);
		UUID id = disk.get(ModComponents.SERIAL);
		Path folder = AllInOneBlockEntity.diskFolder(helper.getLevel().getServer(), id);
		helper.assertTrue(Files.isRegularFile(folder.resolve("init.lua")) && Files.isRegularFile(folder.resolve("bin/sh")), "its files, in its folder");
		helper.assertTrue(!disk.has(ModComponents.DISK_IMAGE), "then the mark goes");
		helper.succeed();
	}

	@GameTest(maxTicks = 400) // (mcos reads its kernel, modules, interpreter and shell off the disk: a board call a read)
	public void aNewEepromBootsMcosFromATier1Disk(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper);
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		computer.pressPower();
		// Booted, and waiting at the shell for a key (not what mcos says: tests don't pin the OS).
		helper.succeedWhen(() -> {
			helper.assertTrue(computer.machine().runner().map(runner -> runner.state() == Runner.State.WAITING).orElse(false), "waiting for a key");
			helper.assertTrue(!computer.terminal().buffer().row(0).startsWith(Cpu.ABORT), "no abort: " + computer.terminal().buffer().row(0));
		});
	}

	@GameTest
	public void aDiskCraftedAloneIsWipedAndItsFilesGoWhenItsTaken(GameTestHelper helper) throws Exception {
		ItemStack used = new ItemStack(ModItems.TIER2_HARD_DISK);
		UUID id = UUID.randomUUID();
		used.set(ModComponents.SERIAL, id);
		Path folder = AllInOneBlockEntity.diskFolder(helper.getLevel().getServer(), id);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("notes.txt"), "old");
		CraftingInput input = CraftingInput.of(1, 1, List.of(used));
		ItemStack wiped = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(wiped.is(ModItems.TIER2_HARD_DISK) && !wiped.has(ModComponents.SERIAL), "a blank disk: a new device next time");
		helper.assertTrue(Files.exists(folder), "a preview of the craft deletes nothing");
		wiped.getItem().onCraftedBy(wiped, GameTests.serverPlayer(helper)); // the player takes it
		helper.assertTrue(!Files.exists(folder), "its old files are gone from the world save");
		helper.assertTrue(!wiped.has(ModComponents.WIPED), "and it carries nothing of them");

		ItemStack fresh = new ItemStack(ModItems.TIER1_HARD_DISK);
		CraftingInput again = CraftingInput.of(1, 1, List.of(fresh));
		ItemStack blank = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, again, helper.getLevel())
			.map(recipe -> recipe.value().assemble(again)).orElse(ItemStack.EMPTY);
		helper.assertTrue(blank.is(ModItems.TIER1_HARD_DISK) && !blank.has(ModComponents.DISK_IMAGE), "a new mcos disk crafted alone: blank");
		helper.succeed();
	}
}
