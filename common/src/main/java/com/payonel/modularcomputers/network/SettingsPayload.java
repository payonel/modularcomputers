package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.config.ModConfig;
import com.payonel.modularcomputers.config.PowerDraws;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.power.BatteryTier;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: the server's settings that clients show (how long each battery tier lasts
 * and how fast it recharges itself, for charge bars and tooltips; what devices draw, for a CPU's
 * and a disk's tooltips). Sent when a player joins and after a reload.
 *
 * @param draws every draw, in {@link PowerDraws#values}' order
 */
public record SettingsPayload(List<Double> batteryDays, List<Double> rechargeRates, List<Integer> draws) implements CustomPacketPayload {
	public static final Type<SettingsPayload> TYPE = new Type<>(ModularComputers.id("settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), SettingsPayload::batteryDays,
		ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), SettingsPayload::rechargeRates,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SettingsPayload::draws,
		SettingsPayload::new);

	@Override
	public Type<SettingsPayload> type() {
		return TYPE;
	}

	public static SettingsPayload current() {
		return new SettingsPayload(Arrays.stream(BatteryTier.values()).map(BatteryTier::days).toList(),
			Arrays.stream(BatteryTier.values()).map(BatteryTier::rechargeRate).toList(), ModConfig.draws().values());
	}

	/** Client side: use the server's settings. */
	public void apply() {
		BatteryTier[] tiers = BatteryTier.values();
		for (int i = 0; i < Math.min(tiers.length, batteryDays.size()); i++) {
			tiers[i].setDays(batteryDays.get(i));
		}
		for (int i = 0; i < Math.min(tiers.length, rechargeRates.size()); i++) {
			tiers[i].setRechargeRate(rechargeRates.get(i));
		}
		PowerDraws server = PowerDraws.of(draws, CpuTier.values().length, DiskTier.values().length);
		if (server != null) {
			ModConfig.useDraws(server);
		}
	}

	/** A player joined: they get the server's settings. */
	public static void joined(ServerPlayer player) {
		ModNetwork.send(player, current());
	}

	public static void sendToAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ModNetwork.send(player, current());
		}
	}
}
