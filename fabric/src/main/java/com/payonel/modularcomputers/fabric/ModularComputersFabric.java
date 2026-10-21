package com.payonel.modularcomputers.fabric;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.command.ModCommands;
import com.payonel.modularcomputers.network.ModNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The mod on Fabric: starts the common code and wires Fabric's events to it. */
public final class ModularComputersFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ModularComputers.init();
		ModularComputers.clientRecipeSerializers().forEach(RecipeSynchronization::synchronizeRecipeSerializer);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ModularComputers.joined(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ModCommands.register(dispatcher));
		ModNetwork.TO_SERVER.forEach(ModularComputersFabric::register);
		ModNetwork.TO_CLIENT.forEach(ModularComputersFabric::register);
	}

	private static <T extends CustomPacketPayload> void register(ModNetwork.ToClient<T> packet) {
		PayloadTypeRegistry.clientboundPlay().register(packet.type(), packet.codec());
	}

	private static <T extends CustomPacketPayload> void register(ModNetwork.ToServer<T> packet) {
		PayloadTypeRegistry.serverboundPlay().register(packet.type(), packet.codec());
		ServerPlayNetworking.registerGlobalReceiver(packet.type(), (payload, context) -> packet.handler().accept(payload, context.player()));
	}
}
