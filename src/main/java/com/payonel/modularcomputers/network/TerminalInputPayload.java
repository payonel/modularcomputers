package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: text the player typed at the terminal at {@code pos}, in its screen view (as
 * the keyboard's layout makes it). Only the terminal the player has open gets it. Keys themselves
 * (presses, repeats, releases) come as {@link TerminalKeyPayload}.
 */
public record TerminalInputPayload(BlockPos pos, String keys) implements CustomPacketPayload {
	public static final Type<TerminalInputPayload> TYPE = new Type<>(ModularComputers.id("terminal_input"));
	/** Longer than any one key sends. */
	public static final int MAX_LENGTH = 16;

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalInputPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TerminalInputPayload::pos,
		ByteBufCodecs.stringUtf8(MAX_LENGTH), TerminalInputPayload::keys,
		TerminalInputPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			// Trust nothing from the client: the player must be able to reach a terminal there.
			var player = context.player();
			if (!payload.keys().isEmpty() && player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof DisplayHost host) {
				host.terminal().type(payload.keys());
			}
		});
	}

	@Override
	public Type<TerminalInputPayload> type() {
		return TYPE;
	}
}
