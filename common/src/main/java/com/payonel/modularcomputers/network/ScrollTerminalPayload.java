package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: scroll the terminal at {@code pos}'s glass over its scrollback, {@code lines}
 * back (negative: on toward the screen), from Shift+PageUp/PageDown or the mouse wheel in its
 * screen view. The terminal does it itself, as a terminal emulator does: nothing goes down the line
 * to the program.
 */
public record ScrollTerminalPayload(HostRef host, int lines) implements CustomPacketPayload {
	public static final Type<ScrollTerminalPayload> TYPE = new Type<>(ModularComputers.id("scroll_terminal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ScrollTerminalPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, ScrollTerminalPayload::host,
		ByteBufCodecs.VAR_INT, ScrollTerminalPayload::lines,
		ScrollTerminalPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(ScrollTerminalPayload payload, ServerPlayer player) {
		// Trust nothing from the client: the player must be able to reach a terminal there.
		if (payload.host().reachable(player, 1.0)
			&& payload.host().find(player.level()) instanceof DisplayHost host) {
			host.terminal().scrollView(payload.lines());
			host.terminal().flush(); // its glass to players now, not at the block's next tick
		}
	}

	@Override
	public Type<ScrollTerminalPayload> type() {
		return TYPE;
	}
}
