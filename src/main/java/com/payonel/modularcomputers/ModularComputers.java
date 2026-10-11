package com.payonel.modularcomputers;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.BatteryChargingRecipe;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.ConnectDevicePayload;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.ReorderBusPayload;
import com.payonel.modularcomputers.network.SwitchViewPayload;
import com.payonel.modularcomputers.sound.ModSounds;
import com.payonel.modularcomputers.network.PressPowerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
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
		ModSounds.init();
		ModComponents.init();
		ModItems.init();
		BatteryChargingRecipe.init();
		ModBlocks.init();
		ModMenus.init();
		ModCommands.init();
		ModCreativeTab.init();
		OpenDisplayPayload.register();
		PressPowerPayload.register();
		SwitchViewPayload.register();
		ReorderBusPayload.register();
		ConnectDevicePayload.register();
		initialized = true;
		LOGGER.info("{} (version {})", INIT_MARKER, version());
	}
}
