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

/** Client to server: forget the offline external device {@code key} in the computer's bus order. */
public record ForgetDevicePayload(BlockPos pos, String key) implements CustomPacketPayload {
	public static final Type<ForgetDevicePayload> TYPE = new Type<>(ModularComputers.id("forget_device"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ForgetDevicePayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ForgetDevicePayload::pos,
		ByteBufCodecs.stringUtf8(128), ForgetDevicePayload::key,
		ForgetDevicePayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
				computer.forgetDevice(payload.key());
			}
		});
	}

	@Override
	public Type<ForgetDevicePayload> type() {
		return TYPE;
	}
}
