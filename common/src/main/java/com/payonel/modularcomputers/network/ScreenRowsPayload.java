package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.TextBuffer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the rows of a screen that changed, as the terminal decoded them, and where its
 * cursor is (and whether it shows: players draw it blinking, as a terminal does). The server
 * is the only decoder; players' copies just take the cells (their glass shows what the device
 * shows). The first view of a screen comes with its block's data; after that only changes come
 * this way, instead of the whole block.
 */
public record ScreenRowsPayload(BlockPos pos, List<Row> rows, Terminal.Cursor cursor) implements CustomPacketPayload {
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

	private static final StreamCodec<ByteBuf, Terminal.Cursor> CURSOR = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Terminal.Cursor::column,
		ByteBufCodecs.VAR_INT, Terminal.Cursor::row,
		ByteBufCodecs.BOOL, Terminal.Cursor::shown,
		ByteBufCodecs.VAR_INT, Terminal.Cursor::style,
		Terminal.Cursor::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenRowsPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ScreenRowsPayload::pos,
		Row.CODEC.apply(ByteBufCodecs.list()), ScreenRowsPayload::rows,
		CURSOR, ScreenRowsPayload::cursor,
		ScreenRowsPayload::new);

	/** The rows of a terminal's glass that changed since last time, and its cursor; null if nothing changed. */
	public static ScreenRowsPayload changes(BlockPos pos, Terminal terminal) {
		TextBuffer buffer = terminal.buffer();
		BitSet dirty = buffer.takeDirtyRows();
		Terminal.Cursor cursor = terminal.cursor();
		if (dirty.isEmpty() && cursor.equals(terminal.shownCursor())) {
			return null;
		}
		terminal.showCursor(cursor);
		List<Row> rows = new ArrayList<>();
		for (int row = dirty.nextSetBit(0); row >= 0 && row < buffer.resolution().rows(); row = dirty.nextSetBit(row + 1)) {
			rows.add(new Row(row, buffer.row(row), buffer.rowForegrounds(row), buffer.rowBackgrounds(row)));
		}
		return new ScreenRowsPayload(pos, rows, cursor);
	}

	/** Puts the rows and the cursor into a player's copy. */
	public void apply(Terminal terminal) {
		for (Row row : rows) {
			terminal.buffer().setRow(row.index(), row.text(), row.foregrounds(), row.backgrounds());
		}
		terminal.showCursor(cursor);
	}

	@Override
	public Type<ScreenRowsPayload> type() {
		return TYPE;
	}
}
