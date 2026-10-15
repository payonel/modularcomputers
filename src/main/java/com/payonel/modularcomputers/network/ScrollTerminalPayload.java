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
 * Client to server: scroll the terminal at {@code pos}'s glass over its scrollback, {@code lines}
 * back (negative: on toward the screen), from Shift+PageUp/PageDown or the mouse wheel in its
 * screen view. The terminal does it itself, as a terminal emulator does: nothing goes down the line
 * to the program.
 */
public record ScrollTerminalPayload(BlockPos pos, int lines) implements CustomPacketPayload {
	public static final Type<ScrollTerminalPayload> TYPE = new Type<>(ModularComputers.id("scroll_terminal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ScrollTerminalPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ScrollTerminalPayload::pos,
		ByteBufCodecs.VAR_INT, ScrollTerminalPayload::lines,
		ScrollTerminalPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			// Trust nothing from the client: the player must be able to reach a terminal there.
			var player = context.player();
			if (player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				&& player.level().getBlockEntity(payload.pos()) instanceof DisplayHost host) {
				host.terminal().scrollView(payload.lines());
				host.terminal().flush(); // its glass to players now, not at the block's next tick
			}
		});
	}

	@Override
	public Type<ScrollTerminalPayload> type() {
		return TYPE;
	}
}
