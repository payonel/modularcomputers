package com.payonel.modularcomputers.fabric.client;

import com.payonel.modularcomputers.client.ModNetworkClient;
import com.payonel.modularcomputers.client.ModularComputersClient;
import com.payonel.modularcomputers.client.hint.ClientRecipes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The mod's client on Fabric: starts the common client and wires Fabric's events to it. */
public final class ModularComputersFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModularComputersClient.renderers(new ModularComputersClient.Renderers() {
			@Override
			public <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type,
				BlockEntityRendererProvider<T, S> provider) {
				BlockEntityRenderers.register(type, provider);
			}
		});
		ModularComputersClient.screens(new ModularComputersClient.Screens() {
			@Override
			public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
				ModularComputersClient.ScreenFactory<M, U> factory) {
				MenuScreens.register(type, factory::create);
			}
		});
		ModNetworkClient.HANDLERS.forEach(ModularComputersFabricClient::register);
		ClientTooltipComponentCallback.EVENT.register(ModularComputersClient::tooltip);
		ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> ClientRecipes.received(recipes.recipes()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ModularComputersClient.disconnected());
		ModularComputersClient.init();
	}

	private static <T extends CustomPacketPayload> void register(ModNetworkClient.Handler<T> packet) {
		ClientPlayNetworking.registerGlobalReceiver(packet.type(), (payload, context) -> packet.handler().accept(payload));
	}
}
