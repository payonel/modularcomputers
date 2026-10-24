package com.payonel.modularcomputers.client;

import com.payonel.modularcomputers.block.DisplayHost;
import com.payonel.modularcomputers.client.screen.DisplayScreen;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.ScreenRowsPayload;
import com.payonel.modularcomputers.network.SettingsPayload;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client's side of the mod's packets (ModNetwork): what each one from the server does, on the
 * client's thread (each loader registers these with its networking), and sending to the server.
 */
public final class ModNetworkClient {
	/** What a packet from the server does. */
	public record Handler<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
	}

	public static final List<Handler<?>> HANDLERS = List.of(
		new Handler<>(OpenDisplayPayload.TYPE, ModNetworkClient::openDisplay),
		new Handler<>(SettingsPayload.TYPE, SettingsPayload::apply),
		new Handler<>(ScreenRowsPayload.TYPE, ModNetworkClient::screenRows),
		new Handler<>(com.payonel.modularcomputers.network.SoundCardPayload.TYPE, com.payonel.modularcomputers.client.sound.SoundCardSpeakers::receive));

	private ModNetworkClient() {
	}

	/** Sends a packet to the server. */
	public static void send(CustomPacketPayload payload) {
		Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
	}

	private static void openDisplay(OpenDisplayPayload payload) {
		var client = Minecraft.getInstance();
		client.gui.setScreen(new DisplayScreen(payload));
		if (client.player.containerMenu != client.player.inventoryMenu) {
			// Coming from the case view (or a screen's slot): the server has already closed the menu.
			client.player.containerMenu = client.player.inventoryMenu;
		}
	}

	private static void screenRows(ScreenRowsPayload payload) {
		var level = Minecraft.getInstance().level;
		if (level != null && level.getBlockEntity(payload.pos()) instanceof DisplayHost host) {
			payload.apply(host.terminal());
		}
	}
}
