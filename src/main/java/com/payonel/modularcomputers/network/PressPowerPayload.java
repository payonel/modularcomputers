package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.Cabling;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed the power button in a GUI view of the computer, or of a
 * screen connected to one, at {@code pos}.
 */
public record PressPowerPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<PressPowerPayload> TYPE = new Type<>(ModularComputers.id("press_power"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PressPowerPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PressPowerPayload::pos,
		PressPowerPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			// Trust nothing from the client: the player must be able to reach a computer there.
			// A screen block's button presses the computer it shows.
			var player = context.player();
			if (!player.isWithinBlockInteractionRange(payload.pos(), 1.0)) {
				return;
			}
			if (player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
				computer.pressPower();
			} else if (player.level().getBlockEntity(payload.pos()) instanceof ScreenBlockEntity) {
				Cabling.computerFor(player.level(), payload.pos()).ifPresent(AllInOneBlockEntity::pressPower);
			}
		});
	}

	@Override
	public Type<PressPowerPayload> type() {
		return TYPE;
	}
}
