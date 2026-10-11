package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.power.BatteryTier;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: the server's settings that clients show (how long each battery tier lasts,
 * for charge bars and tooltips). Sent when a player joins and after a reload.
 */
public record SettingsPayload(List<Double> batteryDays) implements CustomPacketPayload {
	public static final Type<SettingsPayload> TYPE = new Type<>(ModularComputers.id("settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), SettingsPayload::batteryDays,
		SettingsPayload::new);

	@Override
	public Type<SettingsPayload> type() {
		return TYPE;
	}

	public static SettingsPayload current() {
		return new SettingsPayload(Arrays.stream(BatteryTier.values()).map(BatteryTier::days).toList());
	}

	/** Client side: use the server's settings. */
	public void apply() {
		BatteryTier[] tiers = BatteryTier.values();
		for (int i = 0; i < Math.min(tiers.length, batteryDays.size()); i++) {
			tiers[i].setDays(batteryDays.get(i));
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(current()));
	}

	public static void sendToAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, current());
		}
	}
}
