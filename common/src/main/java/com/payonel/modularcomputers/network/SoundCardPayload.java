package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.device.SoundFormat;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what a Sound Card plays next (what was due this tick, whole units of its
 * format, as written: ADPCM stays compressed on the way), to the players near it. Where: its block,
 * and for stereo the block's left (a world direction's index, -1: none known), where its left
 * speaker is.
 */
public record SoundCardPayload(UUID card, BlockPos pos, int left, SoundFormat format, byte[] data) implements CustomPacketPayload {
	public static final Type<SoundCardPayload> TYPE = new Type<>(ModularComputers.id("sound_card"));

	private static final StreamCodec<RegistryFriendlyByteBuf, SoundFormat> FORMAT = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SoundFormat::rate,
		ByteBufCodecs.STRING_UTF8, SoundFormat::encoding,
		ByteBufCodecs.VAR_INT, SoundFormat::channels,
		ByteBufCodecs.VAR_INT, SoundFormat::block,
		SoundFormat::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, SoundCardPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, SoundCardPayload::card,
		BlockPos.STREAM_CODEC, SoundCardPayload::pos,
		ByteBufCodecs.VAR_INT, SoundCardPayload::left,
		FORMAT, SoundCardPayload::format,
		ByteBufCodecs.BYTE_ARRAY, SoundCardPayload::data,
		SoundCardPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
