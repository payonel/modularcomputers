package com.payonel.modularcomputers.neoforge;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.network.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The mod on NeoForge: starts the common code and wires NeoForge's events to it. The common code
 * registers with the game's registries directly, so it runs during NeoForge's registration (which
 * keeps every registry open until all its RegisterEvents are done), once, at the first of them.
 */
@Mod(ModularComputers.MOD_ID)
public final class ModularComputersNeoForge {
	/** Packets' version: a client and server with different ones don't connect. */
	private static final String NETWORK_VERSION = "1";

	public ModularComputersNeoForge(IEventBus modBus) {
		modBus.addListener(RegisterEvent.class, event -> {
			if (event.getRegistryKey().equals(Registries.BLOCK)) {
				ModularComputers.init();
			}
		});
		modBus.addListener(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class,
			event -> NeoForgePlatform.ATTRIBUTES.forEach((type, attributes) -> event.put(type, attributes.get())));
		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
			PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
			ModNetwork.TO_SERVER.forEach(packet -> register(registrar, packet));
			ModNetwork.TO_CLIENT.forEach(packet -> register(registrar, packet));
		});
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ModularComputers.joined(player);
			}
		});
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> ModCommands.register(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> event.sendRecipes(ModularComputers.clientRecipeTypes()));
	}

	private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, ModNetwork.ToServer<T> packet) {
		registrar.playToServer(packet.type(), packet.codec(), (payload, context) -> packet.handler().accept(payload, (ServerPlayer) context.player()));
	}

	/** Its client handler is the client's to register (ModularComputersNeoForgeClient). */
	private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, ModNetwork.ToClient<T> packet) {
		registrar.playToClient(packet.type(), packet.codec());
	}
}
