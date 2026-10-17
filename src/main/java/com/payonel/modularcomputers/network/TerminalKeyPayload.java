package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.device.TerminalKeyboard;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a key event at the terminal at {@code pos}, in its screen view: every key, its
 * press, repeats and release, with the modifiers held ({@link TerminalKeyboard}'s numbering). The
 * terminal decides what it sends down the line, by its mode.
 */
public record TerminalKeyPayload(BlockPos pos, int key, int modifiers, int event) implements CustomPacketPayload {
	public static final Type<TerminalKeyPayload> TYPE = new Type<>(ModularComputers.id("terminal_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalKeyPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TerminalKeyPayload::pos,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::key,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::modifiers,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::event,
		TerminalKeyPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			// Trust nothing from the client: the player must be able to reach a terminal there.
			var player = context.player();
			if (payload.event() >= TerminalKeyboard.PRESS && payload.event() <= TerminalKeyboard.RELEASE
				&& payload.key() > 0 && player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof DisplayHost host) {
				host.terminal().key(payload.key(), payload.modifiers(), payload.event());
			}
		});
	}

	@Override
	public Type<TerminalKeyPayload> type() {
		return TYPE;
	}
}
