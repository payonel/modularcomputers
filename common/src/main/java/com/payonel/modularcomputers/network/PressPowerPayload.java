package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: the player pressed the power button in a GUI view of the computer at {@code pos}. */
public record PressPowerPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<PressPowerPayload> TYPE = new Type<>(ModularComputers.id("press_power"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PressPowerPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PressPowerPayload::pos,
		PressPowerPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(PressPowerPayload payload, ServerPlayer player) {
		// Trust nothing from the client: the player must be able to reach a computer there.
		// Trust nothing from the client: the player must be able to reach a computer there.
		if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
			&& player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer) {
			computer.pressPower();
		}
	}

	@Override
	public Type<PressPowerPayload> type() {
		return TYPE;
	}
}
