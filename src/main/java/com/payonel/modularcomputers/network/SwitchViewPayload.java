package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed the view toggle on the all-in-one at {@code pos}. The
 * server opens the case view (a menu, since it has item slots) or goes back to the screen view.
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
			if (!player.isWithinBlockInteractionRange(payload.pos(), 1.0)
				|| !(player.level().getBlockEntity(payload.pos()) instanceof AllInOneBlockEntity computer)) {
				return;
			}
			if (payload.toCase()) {
				player.openMenu(computer);
			} else {
				// Close the menu without telling the client: it swaps the case view straight for the
				// screen view. Closing it there first would pass through "no screen", which grabs the
				// mouse and recenters the cursor.
				player.doCloseContainer();
				ServerPlayNetworking.send(player, computer.openDisplayPayload());
			}
		});
	}

	@Override
	public Type<SwitchViewPayload> type() {
		return TYPE;
	}
}
