package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: move the device {@code key} up or down the computer's bus order. */
public record ReorderBusPayload(BlockPos pos, String key, boolean up) implements CustomPacketPayload {
	public static final Type<ReorderBusPayload> TYPE = new Type<>(ModularComputers.id("reorder_bus"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReorderBusPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ReorderBusPayload::pos,
		ByteBufCodecs.STRING_UTF8, ReorderBusPayload::key,
		ByteBufCodecs.BOOL, ReorderBusPayload::up,
		ReorderBusPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
				computer.moveDevice(payload.key(), payload.up());
			}
		});
	}

	@Override
	public Type<ReorderBusPayload> type() {
		return TYPE;
	}
}
