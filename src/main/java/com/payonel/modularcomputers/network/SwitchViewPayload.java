package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ScreenBlock;
import com.payonel.modularcomputers.block.ScreenBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.MenuProvider;

/**
 * Client to server: the player pressed the view toggle on the all-in-one or screen block at
 * {@code pos}. The server opens the case view (a menu, since it has item slots: a screen's is
 * its pairing slot) or goes back to the screen view.
 */
public record SwitchViewPayload(BlockPos pos, boolean toCase) implements CustomPacketPayload {
	public static final Type<SwitchViewPayload> TYPE = new Type<>(ModularComputers.id("switch_view"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchViewPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SwitchViewPayload::pos,
		ByteBufCodecs.BOOL, SwitchViewPayload::toCase,
		SwitchViewPayload::new);

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (!player.isWithinBlockInteractionRange(payload.pos(), 1.0)) {
				return;
			}
			var block = player.level().getBlockEntity(payload.pos());
			OpenDisplayPayload display;
			if (block instanceof AllInOneBlockEntity computer) {
				display = computer.openDisplayPayload();
			} else if (block instanceof ScreenBlockEntity screen) {
				display = ScreenBlock.openDisplayPayload(screen);
			} else {
				return;
			}
			if (payload.toCase()) {
				player.openMenu((MenuProvider) block);
			} else {
				// Close the menu without telling the client: it swaps the case view straight for the
				// screen view. Closing it there first would pass through "no screen", which grabs the
				// mouse and recenters the cursor.
				player.doCloseContainer();
				ServerPlayNetworking.send(player, display);
			}
		});
	}

	@Override
	public Type<SwitchViewPayload> type() {
		return TYPE;
	}
}
