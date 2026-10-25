package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: move the device {@code key} up or down the computer's bus order. */
public record ReorderBusPayload(BlockPos pos, String key, boolean up) implements CustomPacketPayload {
	public static final Type<ReorderBusPayload> TYPE = new Type<>(ModularComputers.id("reorder_bus"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReorderBusPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ReorderBusPayload::pos,
		ByteBufCodecs.STRING_UTF8, ReorderBusPayload::key,
		ByteBufCodecs.BOOL, ReorderBusPayload::up,
		ReorderBusPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(ReorderBusPayload payload, ServerPlayer player) {
		if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
			&& player.level().getBlockEntity(payload.pos()) instanceof CaseBlockEntity computer) {
			computer.moveDevice(payload.key(), payload.up());
		}
	}

	@Override
	public Type<ReorderBusPayload> type() {
		return TYPE;
	}
}
