package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.device.TerminalKeyboard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: a key event at the terminal at {@code pos}, in its screen view: every key, its
 * press, repeats and release, with the modifiers held ({@link TerminalKeyboard}'s numbering). The
 * terminal decides what it sends down the line, by its mode; {@code text} is what a press typed
 * ("" none), for a terminal reporting it (kitty's associated text).
 */
public record TerminalKeyPayload(HostRef host, int key, int modifiers, int event, String text) implements CustomPacketPayload {
	/** The most a key's text takes on the wire: its code points' UTF-8. */
	private static final int TEXT_BYTES = TerminalKeyboard.MAX_TEXT * 4;

	public static final Type<TerminalKeyPayload> TYPE = new Type<>(ModularComputers.id("terminal_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalKeyPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, TerminalKeyPayload::host,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::key,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::modifiers,
		ByteBufCodecs.VAR_INT, TerminalKeyPayload::event,
		ByteBufCodecs.stringUtf8(TEXT_BYTES), TerminalKeyPayload::text,
		TerminalKeyPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(TerminalKeyPayload payload, ServerPlayer player) {
		// Trust nothing from the client: the player must be able to reach a terminal there.
		if (payload.event() >= TerminalKeyboard.PRESS && payload.event() <= TerminalKeyboard.RELEASE
			&& payload.key() > 0 && payload.host().reachable(player, 1.0)
			&& payload.host().find(player.level()) instanceof DisplayHost host) {
			host.terminal().key(payload.key(), payload.modifiers(), payload.event(),
				TerminalKeyboard.printable(payload.text()) ? payload.text() : "");
		}
	}

	@Override
	public Type<TerminalKeyPayload> type() {
		return TYPE;
	}
}
