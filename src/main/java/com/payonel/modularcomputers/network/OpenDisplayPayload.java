package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.Resolution;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the display of the screen at {@code pos}. Carries the resolution so the
 * client can size the display correctly from the first frame.
 */
public record OpenDisplayPayload(BlockPos pos, Resolution resolution) implements CustomPacketPayload {
	public static final Type<OpenDisplayPayload> TYPE = new Type<>(ModularComputers.id("open_display"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDisplayPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, OpenDisplayPayload::pos,
		ByteBufCodecs.VAR_INT, payload -> payload.resolution().columns(),
		ByteBufCodecs.VAR_INT, payload -> payload.resolution().rows(),
		(pos, columns, rows) -> new OpenDisplayPayload(pos, new Resolution(columns, rows)));

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	@Override
	public Type<OpenDisplayPayload> type() {
		return TYPE;
	}
}
