package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player pressed the power button in the display of the computer at {@code pos}. */
public record TogglePowerPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<TogglePowerPayload> TYPE = new Type<>(ModularComputers.id("toggle_power"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TogglePowerPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TogglePowerPayload::pos,
		TogglePowerPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			// Trust nothing from the client: the player must be able to reach a computer there.
			var player = context.player();
			if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
				computer.togglePower();
			}
		});
	}

	@Override
	public Type<TogglePowerPayload> type() {
		return TYPE;
	}
}
