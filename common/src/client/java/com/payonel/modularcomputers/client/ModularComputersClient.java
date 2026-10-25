package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.hint.ClientRecipes;
import com.payonel.modularcomputers.client.hint.HintComponent;
import com.payonel.modularcomputers.client.render.DisplayRenderer;
import com.payonel.modularcomputers.client.render.OutlineRenderer;
import com.payonel.modularcomputers.client.render.PhotomaskRenderer;
import com.payonel.modularcomputers.client.screen.CardHolderScreen;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.PhotomaskScreen;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.hint.HintTooltip;
import com.payonel.modularcomputers.menu.ModMenus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The mod's client, for every loader: each loader's client entry point calls {@link #init}, hands
 * {@link #renderers} and {@link #screens} its way of registering (the plain game's registration
 * methods are private), and calls the hooks below from its own events.
 */
public final class ModularComputersClient {
	public static final String CLIENT_INIT_MARKER = "Modular Computers client initialized";

	private static volatile boolean initialized;

	private ModularComputersClient() {
	}

	public static boolean isInitialized() {
		return initialized;
	}

	/** Registers a block entity's renderer. */
	public interface Renderers {
		<T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type,
			BlockEntityRendererProvider<T, S> provider);

		/** An entity's renderer. */
		<T extends net.minecraft.world.entity.Entity> void entity(net.minecraft.world.entity.EntityType<? extends T> type,
			net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider);
	}

	/** Makes a menu's screen. */
	public interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
		U create(M menu, Inventory inventory, Component title);
	}

	/** Registers the screen a menu type opens. */
	public interface Screens {
		<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, ScreenFactory<M, U> factory);
	}

	public static void init() {
		initialized = true;
		ModularComputers.LOGGER.info(CLIENT_INIT_MARKER);
	}

	public static void renderers(Renderers renderers) {
		renderers.register(ModBlocks.CASE_ENTITY, context -> new DisplayRenderer<>(context, DisplayRenderer.CASE));
		renderers.register(ModBlocks.SCREEN_ENTITY, context -> new DisplayRenderer<>(context, DisplayRenderer.SCREEN));
		renderers.register(ModBlocks.EXPANSION_HUB_ENTITY, context -> new OutlineRenderer<>());
		renderers.register(ModBlocks.ROBOTIC_PISTON_ENTITY, context -> new OutlineRenderer<>());
		renderers.register(ModBlocks.PHOTOMASK_ENTITY, context -> new PhotomaskRenderer());
		renderers.entity(com.payonel.modularcomputers.entity.ModEntities.BOT_GOLEM, com.payonel.modularcomputers.client.entity.BotGolemRenderer::new);
		renderers.entity(com.payonel.modularcomputers.entity.ModEntities.ENERGY_FIELD, com.payonel.modularcomputers.client.entity.EnergyFieldRenderer::new);
		renderers.entity(com.payonel.modularcomputers.entity.ModEntities.GOLEM_LEGS, com.payonel.modularcomputers.client.entity.GolemLegsRenderer::new);
		com.payonel.modularcomputers.entity.EnergyField.clientHum = com.payonel.modularcomputers.client.sound.FieldHum::start;
	}

	public static void screens(Screens screens) {
		screens.register(ModMenus.CASE, CaseScreen::new);
		screens.register(ModMenus.GOLEM, CaseScreen::new); // (its art by its board: a golem's)
		screens.register(ModMenus.CHARGING_PAD, com.payonel.modularcomputers.client.screen.ChargingPadScreen::new);
		screens.register(ModMenus.EXPANSION_HUB, CardHolderScreen::new);
		screens.register(ModMenus.ROBOTIC_PISTON, CardHolderScreen::new);
		screens.register(ModMenus.SCREEN, CardHolderScreen::new);
		screens.register(ModMenus.PHOTOMASK, PhotomaskScreen::new);
		screens.register(ModMenus.DISK_STATION, com.payonel.modularcomputers.client.screen.DiskStationScreen::new);
	}

	/** How a tooltip's extra content draws: a hint's crafting grid; null for anything else's. */
	public static ClientTooltipComponent tooltip(TooltipComponent data) {
		return data instanceof HintTooltip hint ? new HintComponent(hint.stack()) : null;
	}

	/**
	 * Left a world or server: what was this visit's goes, on the client's thread (a loader may tell
	 * of the disconnect on the network's: Fabric's does, and textures and sounds are the render
	 * thread's).
	 */
	public static void disconnected() {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (!client.isSameThread()) {
			client.execute(ModularComputersClient::disconnected);
			return;
		}
		Identify.clear(); // the bulb's outlines belong to this visit to a world
		ClientRecipes.clear();
		com.payonel.modularcomputers.client.sound.SoundCardSpeakers.clear(); // (its sounds were this world's)
		com.payonel.modularcomputers.client.render.PixelLayers.clear(); // (and its screens' pictures)
		ModConfig.load(); // back to this game's own settings, after a server's
	}
}
