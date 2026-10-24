package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: the player pressed the view toggle on the computer or screen
 * {@code host} names. The server opens the case view (a menu, since it has item slots: a screen's is
 * its radio slot) or goes back to the screen view.
 */
public record SwitchViewPayload(HostRef host, boolean toCase) implements CustomPacketPayload {
	public static final Type<SwitchViewPayload> TYPE = new Type<>(ModularComputers.id("switch_view"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchViewPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, SwitchViewPayload::host,
		ByteBufCodecs.BOOL, SwitchViewPayload::toCase,
		SwitchViewPayload::new);

	/** On the server's thread, from {@code player}'s client ({@link ModNetwork}). */
	public static void handle(SwitchViewPayload payload, ServerPlayer player) {
		if (!payload.host().reachable(player, 1.0)) {
			return;
		}
		var found = payload.host().find(player.level(), com.payonel.modularcomputers.block.ViewHost.class);
		if (found.isEmpty()) {
			return;
		}
		var host = found.get();
		if (payload.toCase()) {
			host.openParts(player);
		} else if (!host.showsScreen()) {
			return; // no screen in the slot: no screen view
		} else {
			// Close the menu without telling the client: it swaps the case view straight for the
			// screen view. Closing it there first would pass through "no screen", which grabs the
			// mouse and recenters the cursor.
			player.doCloseContainer();
			ModNetwork.send(player, host.openDisplayPayload());
		}
	}

	@Override
	public Type<SwitchViewPayload> type() {
		return TYPE;
	}
}
