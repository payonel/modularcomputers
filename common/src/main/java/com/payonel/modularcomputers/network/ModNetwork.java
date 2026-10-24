package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.platform.Platform;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's packets. Each loader registers these lists with its own networking (the plain game
 * has no registry of payloads); sending is the game's own packet, the same on every loader. The
 * client's handlers are in the client code ({@code ModNetworkClient}).
 */
public final class ModNetwork {
	/** A packet from a client, handled on the server's thread. */
	public record ToServer<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
		StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
	}

	/** A packet to clients. */
	public record ToClient<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
		StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
	}

	public static final List<ToServer<?>> TO_SERVER = List.of(
		new ToServer<>(PressPowerPayload.TYPE, PressPowerPayload.CODEC, PressPowerPayload::handle),
		new ToServer<>(TerminalInputPayload.TYPE, TerminalInputPayload.CODEC, TerminalInputPayload::handle),
		new ToServer<>(PasteTerminalPayload.TYPE, PasteTerminalPayload.CODEC, PasteTerminalPayload::handle),
		new ToServer<>(ScrollTerminalPayload.TYPE, ScrollTerminalPayload.CODEC, ScrollTerminalPayload::handle),
		new ToServer<>(TerminalKeyPayload.TYPE, TerminalKeyPayload.CODEC, TerminalKeyPayload::handle),
		new ToServer<>(SwitchViewPayload.TYPE, SwitchViewPayload.CODEC, SwitchViewPayload::handle),
		new ToServer<>(ReorderBusPayload.TYPE, ReorderBusPayload.CODEC, ReorderBusPayload::handle),
		new ToServer<>(ConnectDevicePayload.TYPE, ConnectDevicePayload.CODEC, ConnectDevicePayload::handle),
		new ToServer<>(ForgetDevicePayload.TYPE, ForgetDevicePayload.CODEC, ForgetDevicePayload::handle));

	public static final List<ToClient<?>> TO_CLIENT = List.of(
		new ToClient<>(OpenDisplayPayload.TYPE, OpenDisplayPayload.CODEC),
		new ToClient<>(ScreenRowsPayload.TYPE, ScreenRowsPayload.CODEC),
		new ToClient<>(SettingsPayload.TYPE, SettingsPayload.CODEC),
		new ToClient<>(SoundCardPayload.TYPE, SoundCardPayload.CODEC),
		new ToClient<>(BoardStatePayload.TYPE, BoardStatePayload.CODEC));

	private ModNetwork() {
	}

	/** Sends a packet to one player, if their client takes it (a game test's stand-in player doesn't). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (Platform.INSTANCE.canReceive(player, payload.type())) {
			player.connection.send(new ClientboundCustomPayloadPacket(payload));
		}
	}
}
