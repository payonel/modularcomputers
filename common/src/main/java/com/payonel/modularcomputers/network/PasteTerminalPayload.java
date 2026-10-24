package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.device.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: what the player pasted into the terminal at {@code pos} (their clipboard,
 * Ctrl+Shift+V or Shift+Insert in its screen view): the terminal sends it as pasted text
 * ({@link Terminal#paste}). At most {@link Terminal#MAX_PASTE} characters.
 */
public record PasteTerminalPayload(BlockPos pos, String text) implements CustomPacketPayload {
	public static final Type<PasteTerminalPayload> TYPE = new Type<>(ModularComputers.id("paste_terminal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PasteTerminalPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PasteTerminalPayload::pos,
		ByteBufCodecs.stringUtf8(Terminal.MAX_PASTE), PasteTerminalPayload::text,
		PasteTerminalPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(PasteTerminalPayload payload, ServerPlayer player) {
		// Trust nothing from the client: the player must be able to reach a terminal there.
		if (player.isWithinBlockInteractionRange(payload.pos(), 1.0) && player.level().getBlockEntity(payload.pos()) instanceof DisplayHost host) {
			host.terminal().paste(payload.text());
		}
	}

	@Override
	public Type<PasteTerminalPayload> type() {
		return TYPE;
	}
}
