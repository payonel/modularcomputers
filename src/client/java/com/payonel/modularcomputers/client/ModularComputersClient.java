package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.hint.ClientRecipes;
import com.payonel.modularcomputers.client.hint.HintComponent;
import com.payonel.modularcomputers.client.render.DisplayRenderer;
import com.payonel.modularcomputers.client.render.OutlineRenderer;
import com.payonel.modularcomputers.client.screen.CardHolderScreen;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.hint.HintTooltip;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.ScreenRowsPayload;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.network.SettingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class ModularComputersClient implements ClientModInitializer {
	public static final String CLIENT_INIT_MARKER = "Modular Computers client initialized";

	private static volatile boolean initialized;

	public static boolean isInitialized() {
		return initialized;
	}

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.ALL_IN_ONE_ENTITY, context -> new DisplayRenderer<>(context, DisplayRenderer.ALL_IN_ONE));
		BlockEntityRenderers.register(ModBlocks.SCREEN_ENTITY, context -> new DisplayRenderer<>(context, DisplayRenderer.SCREEN));
		BlockEntityRenderers.register(ModBlocks.EXPANSION_HUB_ENTITY, context -> new OutlineRenderer<>());
		BlockEntityRenderers.register(ModBlocks.ROBOTIC_PISTON_ENTITY, context -> new OutlineRenderer<>());
		BlockEntityRenderers.register(ModBlocks.PAINT_GUN_ENTITY, context -> new OutlineRenderer<>());
		BlockEntityRenderers.register(ModBlocks.PHOTOMASK_ENTITY, context -> new com.payonel.modularcomputers.client.render.PhotomaskRenderer());
		MenuScreens.register(ModMenus.CASE, CaseScreen::new);
		MenuScreens.register(ModMenus.EXPANSION_HUB, CardHolderScreen::new);
		MenuScreens.register(ModMenus.ROBOTIC_PISTON, CardHolderScreen::new);
		MenuScreens.register(ModMenus.SCREEN, CardHolderScreen::new);
		MenuScreens.register(ModMenus.PAINT_GUN, CardHolderScreen::new);
		MenuScreens.register(ModMenus.PHOTOMASK, com.payonel.modularcomputers.client.screen.PhotomaskScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(OpenDisplayPayload.TYPE, (payload, context) -> {
			var client = context.client();
			client.gui.setScreen(new DisplayScreen(payload));
			if (client.player.containerMenu != client.player.inventoryMenu) {
				// Coming from the case view (or a screen's slot): the server has already closed the menu.
				client.player.containerMenu = client.player.inventoryMenu;
			}
		});
		// The bulb's outlines belong to this visit to a world.
		ClientRecipes.init();
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof HintTooltip hint ? new HintComponent(hint.stack()) : null);
		ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) -> payload.apply());
		ClientPlayNetworking.registerGlobalReceiver(ScreenRowsPayload.TYPE, (payload, context) -> {
			if (context.client().level != null && context.client().level.getBlockEntity(payload.pos()) instanceof DisplayHost host) {
				payload.apply(host.terminal());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Identify.clear();
			ModConfig.load(); // back to this game's own settings, after a server's
		});
		initialized = true;
		ModularComputers.LOGGER.info(CLIENT_INIT_MARKER);
	}
}
