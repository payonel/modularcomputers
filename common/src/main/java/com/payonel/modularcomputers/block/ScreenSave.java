package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import com.payonel.modularcomputers.display.TextBuffer;
import com.payonel.modularcomputers.display.VtDecoder;
import java.util.List;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Saves and loads a decoded screen (a terminal's, or the console's copy) under a name: its cells
 * and everything its decoder needs to carry on exactly where it was (the cursor, colors, modes,
 * a half-received escape sequence, the main screen behind the alternate one).
 */
public final class ScreenSave {
	private ScreenSave() {
	}

	public static void save(ValueOutput output, String name, TextBuffer buffer, VtDecoder decoder) {
		save(output, name, new VtDecoder.Screen(buffer.rows(), buffer.foregrounds(), buffer.backgrounds()), decoder);
	}

	/** A terminal: its screen as it is, even while its glass is scrolled back (the scrollback isn't saved). */
	public static void save(ValueOutput output, String name, com.payonel.modularcomputers.device.Terminal terminal) {
		save(output, name, terminal.screen(), terminal.decoder());
		output.putIntArray(name + "_tiles", terminal.tiles()); // (its pictures)
		output.putBoolean(name + "_cursor_shown", terminal.cursor().shown()); // (for players' copies: whether it shows)
	}

	/** A terminal: {@link #load(ValueInput, String, TextBuffer, VtDecoder)}, and its cursor as players see it. */
	public static void load(ValueInput input, String name, com.payonel.modularcomputers.device.Terminal terminal) {
		load(input, name, terminal.buffer(), terminal.decoder());
		input.getIntArray(name + "_tiles").ifPresent(tiles -> com.payonel.modularcomputers.display.Tiles.screen(tiles, terminal.buffer()));
		VtDecoder decoder = terminal.decoder();
		terminal.showCursor(new com.payonel.modularcomputers.device.Terminal.Cursor(
			Math.min(decoder.column(), terminal.buffer().resolution().columns() - 1), decoder.row(),
			input.getBooleanOr(name + "_cursor_shown", false), decoder.cursorStyle()));
	}

	private static void save(ValueOutput output, String name, VtDecoder.Screen screen, VtDecoder decoder) {
		int columns = screen.rows().isEmpty() ? 0 : com.payonel.modularcomputers.display.TextBuffer.cells(screen.rows().getFirst()).count();
		output.putIntArray(name + "_size", new int[] {columns, screen.rows().size()});
		output.store(name, Codec.STRING.listOf(), screen.rows());
		output.putIntArray(name + "_foreground", screen.foregrounds());
		output.putIntArray(name + "_background", screen.backgrounds());
		output.putIntArray(name + "_state", decoder.state());
		output.putString(name + "_params", decoder.pendingParams());
		VtDecoder.Screen main = decoder.mainScreen();
		if (main != null) {
			output.store(name + "_main", Codec.STRING.listOf(), main.rows());
			output.putIntArray(name + "_main_foreground", main.foregrounds());
			output.putIntArray(name + "_main_background", main.backgrounds());
		}
	}

	public static void load(ValueInput input, String name, TextBuffer buffer, VtDecoder decoder) {
		input.getIntArray(name + "_size").filter(size -> size.length == 2 && size[0] > 0 && size[1] > 0)
			.ifPresent(size -> decoder.resize(new com.payonel.modularcomputers.display.Resolution(size[0], size[1]))); // first: the cells are this size
		input.read(name, Codec.STRING.listOf()).ifPresent(buffer::setRows);
		input.getIntArray(name + "_foreground").ifPresent(foreground ->
			input.getIntArray(name + "_background").ifPresent(background -> buffer.setColors(foreground, background)));
		VtDecoder.Screen main = input.read(name + "_main", Codec.STRING.listOf())
			.map(rows -> new VtDecoder.Screen(List.copyOf(rows),
				input.getIntArray(name + "_main_foreground").orElse(new int[0]),
				input.getIntArray(name + "_main_background").orElse(new int[0])))
			.orElse(null);
		input.getIntArray(name + "_state").ifPresent(state ->
			decoder.restore(state, input.getStringOr(name + "_params", ""), main));
	}
}
