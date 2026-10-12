package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.display.TextBuffer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the rows of a screen that changed, as the terminal decoded them. The server
 * is the only decoder; players' copies just take the cells (their glass shows what the device
 * shows). The first view of a screen comes with its block's data; after that only changes come
 * this way, instead of the whole block.
 */
public record ScreenRowsPayload(BlockPos pos, List<Row> rows) implements CustomPacketPayload {
	public static final Type<ScreenRowsPayload> TYPE = new Type<>(ModularComputers.id("screen_rows"));

	/** One row: its index, characters, and each cell's colors. */
	public record Row(int index, String text, int[] foregrounds, int[] backgrounds) {
		private static final StreamCodec<ByteBuf, int[]> COLORS = new StreamCodec<>() {
			@Override
			public int[] decode(ByteBuf buf) {
				int[] colors = new int[ByteBufCodecs.VAR_INT.decode(buf)];
				for (int i = 0; i < colors.length; i++) {
					colors[i] = buf.readMedium();
				}
				return colors;
			}

			@Override
			public void encode(ByteBuf buf, int[] colors) {
				ByteBufCodecs.VAR_INT.encode(buf, colors.length);
				for (int color : colors) {
					buf.writeMedium(color); // 0xRRGGBB: three bytes
				}
			}
		};

		static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Row::index,
			ByteBufCodecs.STRING_UTF8, Row::text,
			COLORS, Row::foregrounds,
			COLORS, Row::backgrounds,
			Row::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenRowsPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ScreenRowsPayload::pos,
		Row.CODEC.apply(ByteBufCodecs.list()), ScreenRowsPayload::rows,
		ScreenRowsPayload::new);

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** The rows of {@code buffer} that changed since last time, or null if none did. */
	public static ScreenRowsPayload changes(BlockPos pos, TextBuffer buffer) {
		BitSet dirty = buffer.takeDirtyRows();
		if (dirty.isEmpty()) {
			return null;
		}
		List<Row> rows = new ArrayList<>();
		for (int row = dirty.nextSetBit(0); row >= 0 && row < buffer.resolution().rows(); row = dirty.nextSetBit(row + 1)) {
			rows.add(new Row(row, buffer.row(row), buffer.rowForegrounds(row), buffer.rowBackgrounds(row)));
		}
		return new ScreenRowsPayload(pos, rows);
	}

	/** Puts the rows into a player's copy. */
	public void apply(TextBuffer buffer) {
		for (Row row : rows) {
			buffer.setRow(row.index(), row.text(), row.foregrounds(), row.backgrounds());
		}
	}

	@Override
	public Type<ScreenRowsPayload> type() {
		return TYPE;
	}
}
