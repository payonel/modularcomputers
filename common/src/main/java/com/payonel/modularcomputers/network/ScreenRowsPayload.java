package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.TextBuffer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the rows of a screen that changed, as the terminal decoded them, its size (a
 * program may change it: then every row comes), and where its cursor is (and whether it shows:
 * players draw it blinking, as a terminal does). The server
 * is the only decoder; players' copies just take the cells (their glass shows what the device
 * shows). The first view of a screen comes with its block's data; after that only changes come
 * this way, instead of the whole block.
 */
public record ScreenRowsPayload(HostRef host, int columns, int lines, List<Row> rows, Terminal.Cursor cursor) implements CustomPacketPayload {
	public static final Type<ScreenRowsPayload> TYPE = new Type<>(ModularComputers.id("screen_rows"));

	/** One row: its index, characters, each cell's colors, and its cells' pictures (sixel), as {@link com.payonel.modularcomputers.display.Tiles#row} encodes them. */
	public record Row(int index, String text, int[] foregrounds, int[] backgrounds, int[] tiles) {
		private static final StreamCodec<ByteBuf, int[]> INTS = new StreamCodec<>() {
			@Override
			public int[] decode(ByteBuf buf) {
				int[] values = new int[ByteBufCodecs.VAR_INT.decode(buf)];
				for (int i = 0; i < values.length; i++) {
					values[i] = buf.readInt();
				}
				return values;
			}

			@Override
			public void encode(ByteBuf buf, int[] values) {
				ByteBufCodecs.VAR_INT.encode(buf, values.length);
				for (int value : values) {
					buf.writeInt(value);
				}
			}
		};

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

		/** Foregrounds: their colors, then which are bold (a foreground's attribute, in its top byte: TextBuffer.BOLD). */
		private static final StreamCodec<ByteBuf, int[]> FOREGROUNDS = new StreamCodec<>() {
			@Override
			public int[] decode(ByteBuf buf) {
				int[] colors = COLORS.decode(buf);
				BitSet bold = BitSet.valueOf(ByteBufCodecs.BYTE_ARRAY.decode(buf));
				for (int i = bold.nextSetBit(0); i >= 0 && i < colors.length; i = bold.nextSetBit(i + 1)) {
					colors[i] |= com.payonel.modularcomputers.display.TextBuffer.BOLD;
				}
				return colors;
			}

			@Override
			public void encode(ByteBuf buf, int[] foregrounds) {
				int[] colors = new int[foregrounds.length];
				BitSet bold = new BitSet();
				for (int i = 0; i < foregrounds.length; i++) {
					colors[i] = com.payonel.modularcomputers.display.TextBuffer.color(foregrounds[i]);
					bold.set(i, com.payonel.modularcomputers.display.TextBuffer.bold(foregrounds[i]));
				}
				COLORS.encode(buf, colors);
				ByteBufCodecs.BYTE_ARRAY.encode(buf, bold.toByteArray());
			}
		};

		static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Row::index,
			ByteBufCodecs.STRING_UTF8, Row::text,
			FOREGROUNDS, Row::foregrounds,
			COLORS, Row::backgrounds,
			INTS, Row::tiles,
			Row::new);
	}

	private static final StreamCodec<ByteBuf, Terminal.Cursor> CURSOR = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Terminal.Cursor::column,
		ByteBufCodecs.VAR_INT, Terminal.Cursor::row,
		ByteBufCodecs.BOOL, Terminal.Cursor::shown,
		ByteBufCodecs.VAR_INT, Terminal.Cursor::style,
		Terminal.Cursor::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenRowsPayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, ScreenRowsPayload::host,
		ByteBufCodecs.VAR_INT, ScreenRowsPayload::columns,
		ByteBufCodecs.VAR_INT, ScreenRowsPayload::lines,
		Row.CODEC.apply(ByteBufCodecs.list()), ScreenRowsPayload::rows,
		CURSOR, ScreenRowsPayload::cursor,
		ScreenRowsPayload::new);

	/** The rows of a terminal's glass that changed since last time, and its cursor; null if nothing changed. */
	public static ScreenRowsPayload changes(HostRef host, Terminal terminal) {
		TextBuffer buffer = terminal.buffer();
		BitSet dirty = buffer.takeDirtyRows();
		Terminal.Cursor cursor = terminal.cursor();
		if (dirty.isEmpty() && cursor.equals(terminal.shownCursor())) {
			return null;
		}
		terminal.showCursor(cursor);
		List<Row> rows = new ArrayList<>();
		for (int row = dirty.nextSetBit(0); row >= 0 && row < buffer.resolution().rows(); row = dirty.nextSetBit(row + 1)) {
			rows.add(new Row(row, buffer.row(row), buffer.rowForegrounds(row), buffer.rowBackgrounds(row),
				com.payonel.modularcomputers.display.Tiles.row(buffer.rowTiles(row))));
		}
		return new ScreenRowsPayload(host, buffer.resolution().columns(), buffer.resolution().rows(), rows, cursor);
	}

	/** Puts the rows and the cursor into a player's copy. */
	public void apply(Terminal terminal) {
		if (columns > 0 && lines > 0) {
			terminal.decoder().resize(new com.payonel.modularcomputers.display.Resolution(columns, lines)); // (the server's: no limit here)
		}
		for (Row row : rows) {
			terminal.buffer().setRow(row.index(), row.text(), row.foregrounds(), row.backgrounds());
			if (row.tiles().length > 0) {
				terminal.buffer().setRowTiles(row.index(), com.payonel.modularcomputers.display.Tiles.row(row.tiles(), terminal.buffer().resolution().columns()));
			}
		}
		terminal.showCursor(cursor);
	}

	@Override
	public Type<ScreenRowsPayload> type() {
		return TYPE;
	}
}
