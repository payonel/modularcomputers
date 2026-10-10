package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.client.render.AllInOneRenderer;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class ModularComputersClient implements ClientModInitializer {
	public static final String CLIENT_INIT_MARKER = "Modular Computers client initialized";

	private static volatile boolean initialized;

	public static boolean isInitialized() {
		return initialized;
	}

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.ALL_IN_ONE_ENTITY, AllInOneRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(OpenDisplayPayload.TYPE, (payload, context) ->
			context.client().gui.setScreen(new DisplayScreen(payload.pos(), payload.resolution())));
		initialized = true;
		ModularComputers.LOGGER.info(CLIENT_INIT_MARKER);
	}
}
