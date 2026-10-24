package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.bus.Device;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Saves and loads what a device holds that must outlast a save or an unload ({@link Device#saved}:
 * data waiting for the program, settings a program made), under a name, in the block it's in.
 * The values are the runtime's ({@code Values}), each kept with its type, so a 2 comes back an
 * integer and 2.0 a float. A handle can't be kept: it comes back nil.
 */
public final class DeviceSave {
	private DeviceSave() {
	}

	public static void save(ValueOutput output, String name, Device device) {
		Map<String, Object> saved = device.saved();
		if (!saved.isEmpty()) {
			output.store(name, CompoundTag.CODEC, tag(saved));
		}
	}

	public static void load(ValueInput input, String name, Device device) {
		input.read(name, CompoundTag.CODEC).ifPresent(tag -> {
			if (value(tag) instanceof Map<?, ?> map) {
				Map<String, Object> saved = new LinkedHashMap<>();
				map.forEach((key, value) -> saved.put(String.valueOf(key), value));
				device.restore(saved);
			}
		});
	}

	static CompoundTag tag(Object value) {
		CompoundTag tag = new CompoundTag();
		switch (value) {
			case Boolean b -> tag.putBoolean("b", b);
			case Long l -> tag.putLong("l", l);
			case Integer i -> tag.putLong("l", i);
			case Double d -> tag.putDouble("d", d);
			case Float f -> tag.putDouble("d", f);
			case String s -> tag.putString("s", s);
			case List<?> list -> {
				ListTag items = new ListTag();
				list.forEach(item -> items.add(tag(item)));
				tag.put("list", items);
			}
			case Map<?, ?> map -> {
				ListTag entries = new ListTag();
				map.forEach((k, v) -> {
					CompoundTag entry = new CompoundTag();
					entry.put("k", tag(k));
					entry.put("v", tag(v));
					entries.add(entry);
				});
				tag.put("map", entries);
			}
			case null, default -> {
				// nil (and what can't be kept)
			}
		}
		return tag;
	}

	static Object value(CompoundTag tag) {
		if (tag.contains("b")) {
			return tag.getBooleanOr("b", false);
		} else if (tag.contains("l")) {
			return tag.getLongOr("l", 0);
		} else if (tag.contains("d")) {
			return tag.getDoubleOr("d", 0);
		} else if (tag.contains("s")) {
			return tag.getStringOr("s", "");
		} else if (tag.contains("list")) {
			List<Object> list = new ArrayList<>();
			for (Tag item : tag.getListOrEmpty("list")) {
				list.add(item instanceof CompoundTag c ? value(c) : null);
			}
			return list;
		} else if (tag.contains("map")) {
			Map<Object, Object> map = new LinkedHashMap<>();
			for (Tag item : tag.getListOrEmpty("map")) {
				if (item instanceof CompoundTag entry) {
					Object key = value(entry.getCompoundOrEmpty("k"));
					if (key != null) {
						map.put(key, value(entry.getCompoundOrEmpty("v")));
					}
				}
			}
			return map;
		}
		return null;
	}
}
