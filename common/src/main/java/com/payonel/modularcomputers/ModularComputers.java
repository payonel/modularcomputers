package com.payonel.modularcomputers;

import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.item.BatteryChargingRecipe;
import com.payonel.modularcomputers.item.BatteryUpgradeRecipe;
import com.payonel.modularcomputers.item.ClearIdRecipe;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.SettingsPayload;
import com.payonel.modularcomputers.platform.Platform;
import com.payonel.modularcomputers.runtime.LuaRunner;
import com.payonel.modularcomputers.sound.ModSounds;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod, for every loader: each loader's entry point calls {@link #init} while the game's
 * registries take entries, and calls the hooks below from its own events (the save hooks come
 * from a mixin, MinecraftServerSaves, the same on every loader).
 */
public final class ModularComputers {
	public static final String MOD_ID = "modularcomputers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Logged once common init finishes; the headless test scripts look for this line. */
	public static final String INIT_MARKER = "Modular Computers initialized";

	private static volatile boolean initialized;

	private ModularComputers() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static String version() {
		return Platform.INSTANCE.version();
	}

	/**
	 * The recipes clients get, so tooltips can show how to craft each item (ClientRecipes): Fabric
	 * sends recipes by serializer, NeoForge by type ({@link #clientRecipeTypes}). (Methods: naming the
	 * mod's serializer registers it.)
	 */
	public static List<RecipeSerializer<?>> clientRecipeSerializers() {
		return List.of(ShapedRecipe.SERIALIZER, BatteryUpgradeRecipe.SERIALIZER, BlastingRecipe.SERIALIZER);
	}

	/** The types of {@link #clientRecipeSerializers}' recipes. */
	public static List<RecipeType<?>> clientRecipeTypes() {
		return List.of(RecipeType.CRAFTING, RecipeType.BLASTING);
	}

	/** Registers everything the mod adds (blocks, items, menus...), once. */
	public static void init() {
		ModConfig.load();
		ModSounds.init();
		ModComponents.init();
		ModItems.init();
		BatteryChargingRecipe.init();
		ClearIdRecipe.init();
		BatteryUpgradeRecipe.init();
		ModBlocks.init();
		ModMenus.init();
		ModCreativeTab.init();
		initialized = true;
		LOGGER.info("{} (version {})", INIT_MARKER, version());
	}

	/** A player joined the server. */
	public static void joined(ServerPlayer player) {
		SettingsPayload.joined(player);
	}

	/**
	 * The world is about to be saved: every running computer is asked for its state at once; each
	 * saves itself, in parallel, and each case's save takes its computer's bytes (LuaRunner.prepareAll).
	 */
	public static void saving() {
		LuaRunner.prepareAll();
	}

	/** The world was saved. */
	public static void saved() {
		LuaRunner.releaseAll();
	}
}
