package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.device.Terminal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: what the player's mouse did over the terminal at {@code host}'s glass, in its
 * screen view: a press, a release, a move to another cell, or a notch of the wheel, at a cell (from
 * 0), with the modifiers held. The terminal reports it to its program as xterm does, when it asked
 * ({@link Terminal#mouse}); else the wheel scrolls its glass back.
 */
public record TerminalMousePayload(HostRef host, int action, int button, int column, int row, int modifiers) implements CustomPacketPayload {
	public static final Type<TerminalMousePayload> TYPE = new Type<>(ModularComputers.id("terminal_mouse"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalMousePayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, TerminalMousePayload::host,
		ByteBufCodecs.VAR_INT, TerminalMousePayload::action,
		ByteBufCodecs.VAR_INT, TerminalMousePayload::button,
		ByteBufCodecs.VAR_INT, TerminalMousePayload::column,
		ByteBufCodecs.VAR_INT, TerminalMousePayload::row,
		ByteBufCodecs.VAR_INT, TerminalMousePayload::modifiers,
		TerminalMousePayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(TerminalMousePayload payload, ServerPlayer player) {
		// Trust nothing from the client: a terminal there it can reach, an action and a cell that are ones.
		if (payload.action() < 0 || payload.action() >= Terminal.MouseAction.values().length || payload.column() < 0 || payload.row() < 0
			|| !payload.host().reachable(player, 1.0) || !(payload.host().find(player.level()) instanceof DisplayHost host)) {
			return;
		}
		var size = host.terminal().buffer().resolution();
		if (payload.column() >= size.columns() || payload.row() >= size.rows()) {
			return;
		}
		host.terminal().mouse(Terminal.MouseAction.values()[payload.action()], payload.button(), payload.column(), payload.row(), payload.modifiers());
	}

	@Override
	public Type<TerminalMousePayload> type() {
		return TYPE;
	}
}
