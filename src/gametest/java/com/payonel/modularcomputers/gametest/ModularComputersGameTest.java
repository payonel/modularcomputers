package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** Server game tests: run headless on a dedicated server by {@code ./gradlew runGameTest} (part of {@code build}). */
public class ModularComputersGameTest {
	@GameTest
	public void modIsLoadedAndInitialized(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded(ModularComputers.MOD_ID), "mod not loaded");
		helper.assertTrue(ModularComputers.isInitialized(), "common initializer did not run");
		helper.succeed();
	}
}
