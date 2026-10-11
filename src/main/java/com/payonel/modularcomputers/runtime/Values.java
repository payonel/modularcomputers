package com.payonel.modularcomputers.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values that pass between programs and devices, as Lua has them: nil (null), booleans,
 * integers ({@code Long}) and floats ({@code Double}) as distinct numbers (like Lua 5.3+),
 * strings, and tables: a {@code List} for a sequence, a {@code Map} otherwise. Opaque handles
 * (Lua userdata) come later.
 */
public final class Values {
	private Values() {
	}

	/** The same value as one of the types above; smaller Java numbers widen. */
	public static Object normalize(Object value) {
		return switch (value) {
			case null -> null;
			case Boolean b -> b;
			case Long l -> l;
			case Integer i -> (long) i;
			case Short s -> (long) s;
			case Byte b -> (long) b;
			case Double d -> d;
			case Float f -> (double) f;
			case String s -> s;
			case Character c -> String.valueOf(c);
			case List<?> list -> {
				List<Object> copy = new ArrayList<>();
				list.forEach(item -> copy.add(normalize(item)));
				yield Collections.unmodifiableList(copy); // a sequence can hold nils
			}
			case Map<?, ?> map -> {
				Map<Object, Object> copy = new LinkedHashMap<>();
				map.forEach((key, item) -> copy.put(normalize(key), normalize(item)));
				yield copy;
			}
			default -> throw new IllegalArgumentException("not a value a program can hold: " + value.getClass().getSimpleName());
		};
	}

	/** The value's type, by Lua's names. */
	public static String typeName(Object value) {
		return switch (value) {
			case null -> "nil";
			case Boolean b -> "boolean";
			case Long l -> "number";
			case Double d -> "number";
			case String s -> "string";
			case List<?> list -> "table";
			case Map<?, ?> map -> "table";
			default -> "userdata";
		};
	}
}
