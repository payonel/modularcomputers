package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: plug the cabled device {@code key} into the computer, or unplug it. */
public record ConnectDevicePayload(BlockPos pos, String key, boolean connected) implements CustomPacketPayload {
	public static final Type<ConnectDevicePayload> TYPE = new Type<>(ModularComputers.id("connect_device"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConnectDevicePayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ConnectDevicePayload::pos,
		ByteBufCodecs.stringUtf8(128), ConnectDevicePayload::key,
		ByteBufCodecs.BOOL, ConnectDevicePayload::connected,
		ConnectDevicePayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(ConnectDevicePayload payload, ServerPlayer player) {
		if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
			&& player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
			computer.setConnected(payload.key(), payload.connected());
		}
	}

	@Override
	public Type<ConnectDevicePayload> type() {
		return TYPE;
	}
}
