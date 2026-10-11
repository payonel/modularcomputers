package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.render.DisplayRenderer;
import com.payonel.modularcomputers.client.screen.CaseScreen;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
		MenuScreens.register(ModMenus.CASE, CaseScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(OpenDisplayPayload.TYPE, (payload, context) -> {
			var client = context.client();
			client.gui.setScreen(new DisplayScreen(payload));
			if (client.player.containerMenu instanceof CaseMenu) {
				// Coming from the case view: the server has already closed the menu.
				client.player.containerMenu = client.player.inventoryMenu;
			}
		});
		initialized = true;
		ModularComputers.LOGGER.info(CLIENT_INIT_MARKER);
	}
}
