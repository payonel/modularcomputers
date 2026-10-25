package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.Resolution;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the display of the screen {@code host} names. Carries the resolution so the
 * client can size the display correctly from the first frame, the computer whose power button
 * to show (none for a screen that isn't connected to one), and whether there's a case view to
 * switch to (a case has one; a screen block doesn't).
 */
public record OpenDisplayPayload(HostRef host, Resolution resolution, Optional<HostRef> computer, boolean caseView) implements CustomPacketPayload {
	public static final Type<OpenDisplayPayload> TYPE = new Type<>(ModularComputers.id("open_display"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDisplayPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, OpenDisplayPayload::host,
		ByteBufCodecs.VAR_INT, payload -> payload.resolution().columns(),
		ByteBufCodecs.VAR_INT, payload -> payload.resolution().rows(),
		ByteBufCodecs.optional(HostRef.STREAM_CODEC), OpenDisplayPayload::computer,
		ByteBufCodecs.BOOL, OpenDisplayPayload::caseView,
		(host, columns, rows, computer, caseView) -> new OpenDisplayPayload(host, new Resolution(columns, rows), computer, caseView));

	@Override
	public Type<OpenDisplayPayload> type() {
		return TYPE;
	}
}
