package com.payonel.modularcomputers.neoforge.client;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.client.ModNetworkClient;
import com.payonel.modularcomputers.client.ModularComputersClient;
import com.payonel.modularcomputers.client.hint.ClientRecipes;
import com.payonel.modularcomputers.hint.HintTooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The mod's client on NeoForge: starts the common client and wires NeoForge's events to it. */
@Mod(value = ModularComputers.MOD_ID, dist = Dist.CLIENT)
public final class ModularComputersNeoForgeClient {
	public ModularComputersNeoForgeClient(IEventBus modBus) {
		modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> ModularComputersClient.renderers(new ModularComputersClient.Renderers() {
			@Override
			public <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type,
				BlockEntityRendererProvider<T, S> provider) {
				event.registerBlockEntityRenderer(type, provider);
			}

			@Override
			public <T extends net.minecraft.world.entity.Entity> void entity(net.minecraft.world.entity.EntityType<? extends T> type,
				net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider) {
				event.registerEntityRenderer(type, provider);
			}
		}));
		modBus.addListener(RegisterMenuScreensEvent.class, event -> ModularComputersClient.screens(new ModularComputersClient.Screens() {
			@Override
			public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
				ModularComputersClient.ScreenFactory<M, U> factory) {
				event.register(type, factory::create);
			}
		}));
		modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> ModNetworkClient.HANDLERS.forEach(packet -> register(event, packet)));
		modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class,
			event -> event.register(HintTooltip.class, ModularComputersClient::tooltip));
		modBus.addListener(FMLClientSetupEvent.class, event -> ModularComputersClient.init());
		NeoForge.EVENT_BUS.addListener(RecipesReceivedEvent.class, event -> ClientRecipes.received(event.getRecipeMap().values()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ModularComputersClient.disconnected());
	}

	private static <T extends CustomPacketPayload> void register(RegisterClientPayloadHandlersEvent event, ModNetworkClient.Handler<T> packet) {
		event.register(packet.type(), (payload, context) -> packet.handler().accept(payload));
	}
}
