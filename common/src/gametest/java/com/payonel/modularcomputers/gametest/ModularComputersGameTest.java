package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/** Server game tests: run headless on a dedicated server, on each loader (GameTests; part of {@code testAll}). */
public class ModularComputersGameTest {
	@GameTest
	public void modIsLoadedAndInitialized(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ModularComputers.id("all_in_one")), "mod not loaded");
		helper.assertTrue(ModularComputers.isInitialized(), "common initializer did not run");
		helper.succeed();
	}

	@GameTest
	public void everyItemOfTheModIsInItsCreativeTab(GameTestHelper helper) {
		var listed = com.payonel.modularcomputers.ModCreativeTab.items();
		for (var item : com.payonel.modularcomputers.command.ModCommands.modItems()) {
			helper.assertTrue(listed.stream().filter(stack -> stack.is(item)).count() == 1, "once in the tab: " + item);
		}
		helper.succeed();
	}
}
