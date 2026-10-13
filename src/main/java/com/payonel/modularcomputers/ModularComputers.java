package com.payonel.modularcomputers;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.item.BatteryChargingRecipe;
import com.payonel.modularcomputers.item.BatteryUpgradeRecipe;
import com.payonel.modularcomputers.item.ClearIdRecipe;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.ConnectDevicePayload;
import com.payonel.modularcomputers.network.ForgetDevicePayload;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.PressPowerPayload;
import com.payonel.modularcomputers.network.TerminalInputPayload;
import com.payonel.modularcomputers.network.ScreenRowsPayload;
import com.payonel.modularcomputers.network.ReorderBusPayload;
import com.payonel.modularcomputers.network.SettingsPayload;
import com.payonel.modularcomputers.network.SwitchViewPayload;
import com.payonel.modularcomputers.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModularComputers implements ModInitializer {
	public static final String MOD_ID = "modularcomputers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Logged once common init finishes; the headless test scripts look for this line. */
	public static final String INIT_MARKER = "Modular Computers initialized";

	private static volatile boolean initialized;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	@Override
	public void onInitialize() {
		ModConfig.load();
		ModSounds.init();
		ModComponents.init();
		ModItems.init();
		BatteryChargingRecipe.init();
		ClearIdRecipe.init();
		BatteryUpgradeRecipe.init();
		// Clients get the shaped recipes, so tooltips can show how to craft each item.
		RecipeSynchronization.synchronizeRecipeSerializer(ShapedRecipe.SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(BatteryUpgradeRecipe.SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(net.minecraft.world.item.crafting.BlastingRecipe.SERIALIZER);
		ModBlocks.init();
		// A world save asks every running computer for its state at once; each saves itself, in
		// parallel, and each case's save takes its computer's bytes (LuaRunner.prepareAll).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) ->
			com.payonel.modularcomputers.runtime.LuaRunner.prepareAll());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) ->
			com.payonel.modularcomputers.runtime.LuaRunner.releaseAll());
		ModMenus.init();
		ModCommands.init();
		ModCreativeTab.init();
		OpenDisplayPayload.register();
		PressPowerPayload.register();
		TerminalInputPayload.register();
		ScreenRowsPayload.register();
		SwitchViewPayload.register();
		ReorderBusPayload.register();
		ConnectDevicePayload.register();
		ForgetDevicePayload.register();
		SettingsPayload.register();
		initialized = true;
		LOGGER.info("{} (version {})", INIT_MARKER, version());
	}
}
