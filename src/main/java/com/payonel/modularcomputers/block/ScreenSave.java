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
final class ScreenSave {
	private ScreenSave() {
	}

	static void save(ValueOutput output, String name, TextBuffer buffer, VtDecoder decoder) {
		output.store(name, Codec.STRING.listOf(), buffer.rows());
		output.putIntArray(name + "_foreground", buffer.foregrounds());
		output.putIntArray(name + "_background", buffer.backgrounds());
		output.putIntArray(name + "_state", decoder.state());
		output.putString(name + "_params", decoder.pendingParams());
		VtDecoder.Screen main = decoder.mainScreen();
		if (main != null) {
			output.store(name + "_main", Codec.STRING.listOf(), main.rows());
			output.putIntArray(name + "_main_foreground", main.foregrounds());
			output.putIntArray(name + "_main_background", main.backgrounds());
		}
	}

	static void load(ValueInput input, String name, TextBuffer buffer, VtDecoder decoder) {
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
