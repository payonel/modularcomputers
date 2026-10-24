package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: the player pressed the power button in a GUI view of the computer at {@code pos}. */
public record PressPowerPayload(HostRef host) implements CustomPacketPayload {
	public static final Type<PressPowerPayload> TYPE = new Type<>(ModularComputers.id("press_power"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PressPowerPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, PressPowerPayload::host,
		PressPowerPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(PressPowerPayload payload, ServerPlayer player) {
		// Trust nothing from the client: the player must be able to reach a computer there.
		if (payload.host().reachable(player, 1.0)) {
			payload.host().find(player.level(), com.payonel.modularcomputers.block.ViewHost.class).ifPresent(computer -> computer.pressPowerButton());
		}
	}

	@Override
	public Type<PressPowerPayload> type() {
		return TYPE;
	}
}
