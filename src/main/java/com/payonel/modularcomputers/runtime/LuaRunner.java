package com.payonel.modularcomputers.runtime;

import static com.payonel.lungolua.api.LuaApi.*;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checklstring;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checkstack;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_error;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_loadbufferx;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_newstate;
import static com.payonel.lungolua.lib.Linit.luaL_openselectedlibs;
import static com.payonel.lungolua.lib.Lualib.*;
import static com.payonel.lungolua.runtime.Ldo.lua_resume;
import static com.payonel.lungolua.runtime.Lstate.lua_newthread;

import com.payonel.lungolua.api.HostString;
import com.payonel.lungolua.api.lua_CFunction;
import com.payonel.lungolua.api.lua_ResumeResult;
import com.payonel.lungolua.api.lua_UserObject;
import com.payonel.lungolua.bytes.ByteView;
import com.payonel.lungolua.runtime.lua_State;
import com.payonel.modularcomputers.machine.LuaScripts;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Lua CPU's host (user design, 2026-10-13): a plain runner for a LungoLua VM, nothing more. It
 * runs the CPU's firmware, {@code lua/cpu/machine.lua}, as the kernel coroutine, resuming it once a
 * tick, and gives it a {@code host} table of direct board calls. Everything else is the firmware's:
 * the sandbox the EEPROM's code runs in, its budget and freezes, and its syscalls, which reach the
 * host as the kernel's yields:
 * <ul>
 * <li>{@code "tick"}: done for this tick;</li>
 * <li>{@code "wait", ticks, sources...}: wait for an interrupt from one of the sources (any, when
 * none) for {@code ticks} ({@code -1}: no limit); answered with those pending;</li>
 * <li>{@code "halt"}: the CPU halts, and the kernel is never resumed.</li>
 * </ul>
 * The kernel ends with {@code "returned"} (the EEPROM's code returned: a panic) or {@code "abort",
 * why} (its text didn't load), or an error (the EEPROM's code crashed).
 * <p>
 * The board calls answer right away, on the server thread, and never call back into Lua; each one
 * is one of the tick's calls, and {@code host.calls()} tells the firmware how many are left. Values
 * cross as {@link Values}: strings as bytes (ISO-8859-1), handles as userdata, the board's records
 * as tables.
 * <p>
 * Only the host can persist the VM (LungoLua's Styx persists from the main thread), so saving a
 * running machine will be done here. Not yet: power-off drops the VM.
 */
public final class LuaRunner implements Runner {
	/** The chunk's name in Lua's messages: {@code machine:3: ...}. */
	private static final String FIRMWARE = "cpu/machine";
	/** What the VM opens: the firmware needs debug (budgets, freezing); it hands out what it chooses. */
	private static final int LIBRARIES = LUA_GLIBK | LUA_COLIBK | LUA_TABLIBK | LUA_STRLIBK | LUA_MATHLIBK | LUA_UTF8LIBK | LUA_DBLIBK;
	/** How deep tables nest between programs and devices. */
	private static final int MAX_DEPTH = 16;

	/** Lua's error, as the program left it. */
	public static final class LuaException extends RuntimeException {
		LuaException(String message) {
			super(message);
		}
	}

	/** The EEPROM's text didn't load: the abort screen says why. */
	public static final class Aborted extends RuntimeException {
		Aborted(String why) {
			super(why);
		}
	}

	/** A handle (an open file, say) held by a program: Lua userdata. */
	private record HandleObject(Handle handle) implements lua_UserObject {
	}

	/** The firmware asked to wait for an interrupt: answered with the sources pending. */
	private record Wait(long until, List<String> sources) {
		List<String> ready(List<String> pending) {
			return sources.isEmpty() ? pending : pending.stream().filter(sources::contains).toList();
		}
	}

	private final lua_State main;
	private final lua_State kernel;
	private final String image;
	private State state = State.RUNNING;
	private Throwable crash;
	private long answered;
	private boolean started;
	/** During a turn: the real board, the calls left and the tick. */
	private Board board;
	private int calls;
	private long now;
	/** The firmware's wait, while it waits. */
	private Wait wait;

	/** A CPU about to run {@code image}, the EEPROM's text; the firmware loads it at the first {@link #tick}. */
	public LuaRunner(String image) {
		this.image = image;
		main = luaL_newstate();
		luaL_openselectedlibs(main, LIBRARIES, 0);
		lua_createtable(main, 0, 12);
		function("list", L -> push(L, board.list()));
		function("status", L -> push(L, board.status()));
		function("type", L -> push(L, board.type(string(L, 1))));
		function("methods", L -> push(L, board.methods(string(L, 1))));
		function("doc", L -> push(L, board.doc(string(L, 1), string(L, 2))));
		function("info", L -> push(L, board.info(string(L, 1))));
		function("uptime", L -> push(L, board.uptime()));
		function("power", L -> push(L, board.power()));
		function("hardware", L -> push(L, board.hardware()));
		function("invoke", this::invoke);
		lua_pushcfunction(main, L -> push(L, (long) calls)); // not a call itself
		lua_setfield(main, -2, new HostString("calls"));
		lua_setglobal(main, new HostString("host"));

		kernel = lua_newthread(main);
		lua_setfield(main, LUA_REGISTRYINDEX, new HostString("modularcomputers.kernel")); // kept from the collector
		byte[] firmware = LuaScripts.read(FIRMWARE).getBytes(StandardCharsets.ISO_8859_1);
		if (luaL_loadbufferx(kernel, new ByteView(firmware), new HostString("=machine"), new HostString("t")) != LUA_OK) {
			throw new IllegalStateException("the CPU's firmware doesn't load: " + text(kernel, -1)); // a bug in the mod
		}
	}

	/** Whether {@code source} compiles as Lua: null, or Lua's message. */
	public static String check(String source, String name) {
		lua_State L = luaL_newstate();
		byte[] code = source.getBytes(StandardCharsets.ISO_8859_1);
		return luaL_loadbufferx(L, new ByteView(code), new HostString("=" + name), new HostString("t")) == LUA_OK ? null : text(L, -1);
	}

	@Override
	public State state() {
		return state;
	}

	@Override
	public long answered() {
		return answered;
	}

	@Override
	public Throwable crash() {
		return crash;
	}

	@Override
	public State tick(Board board, int calls, long now, Supplier<List<String>> interrupts) {
		if (state != State.RUNNING && state != State.WAITING) {
			return state;
		}
		this.board = board;
		this.calls = calls;
		this.now = now;
		try {
			while (true) {
				int answers = 0;
				if (!started) {
					started = true;
					push(kernel, image); // the EEPROM's text, as the CPU read it at power-on
					answers = 1;
				} else if (wait != null) {
					List<String> pending = wait.ready(interrupts.get());
					if (pending.isEmpty() && now < wait.until()) {
						return state = State.WAITING;
					}
					wait = null;
					push(kernel, pending);
					answers = 1;
				}
				lua_ResumeResult result = lua_resume(kernel, null, answers);
				List<Object> said = new ArrayList<>();
				int top = lua_gettop(kernel);
				if (result.status == LUA_YIELD || result.status == LUA_OK) {
					for (int i = top - result.nres + 1; i <= top; i++) {
						said.add(toJava(kernel, i, 0));
					}
					lua_settop(kernel, top - result.nres);
				}
				Object what = said.isEmpty() ? null : said.getFirst();
				if (result.status == LUA_YIELD) {
					if ("tick".equals(what)) {
						return state = State.RUNNING;
					} else if ("halt".equals(what)) {
						return state = State.HALTED;
					} else if ("wait".equals(what)) {
						long ticks = said.size() > 1 && said.get(1) instanceof Long l ? l : -1;
						List<String> sources = said.stream().skip(2).map(String::valueOf).toList();
						wait = new Wait(ticks < 0 ? Long.MAX_VALUE : now + ticks, sources);
						continue; // it may be over already
					}
					crash = new LuaException("the firmware asked for '" + what + "'");
					return state = State.CRASHED;
				}
				if (result.status == LUA_OK) {
					if ("abort".equals(what)) {
						crash = new Aborted(String.valueOf(said.size() > 1 ? said.get(1) : "?"));
						return state = State.CRASHED;
					}
					return state = State.RETURNED;
				}
				crash = new LuaException(text(kernel, -1));
				return state = State.CRASHED;
			}
		} finally {
			this.board = null;
		}
	}

	@Override
	public void stop() {
		state = State.HALTED; // the VM is left to the collector: closing it would run the program's finalizers
	}

	/** A board call: answers right away, and is one of the tick's calls (the firmware checks how many are left). */
	private void function(String name, lua_CFunction body) {
		lua_pushcfunction(main, L -> {
			calls--;
			answered++;
			return body.call(L);
		});
		lua_setfield(main, -2, new HostString(name));
	}

	/** {@code invoke(address or handle, method, ...)}: {@code true} and its values, or {@code false} and why not. */
	private int invoke(lua_State L) {
		int top = lua_gettop(L);
		Object target = lua_type(L, 1) == LUA_TUSERDATA && lua_touserobject(L, 1) instanceof HandleObject h ? h.handle() : string(L, 1);
		String method = string(L, 2);
		Object[] args = new Object[Math.max(0, top - 2)];
		for (int i = 0; i < args.length; i++) {
			args[i] = toJava(L, i + 3, 0);
		}
		Result result = target instanceof Handle handle ? board.invoke(handle, method, args) : board.invoke((String) target, method, args);
		if (result.failed()) {
			lua_pushboolean(L, 0);
			push(L, result.error());
			return 2;
		}
		luaL_checkstack(L, result.values().size() + 1, new HostString("too many results"));
		lua_pushboolean(L, 1);
		for (Object value : result.values()) {
			push(L, value);
		}
		return 1 + result.values().size();
	}

	private static String string(lua_State L, int arg) {
		ByteView bytes = luaL_checklstring(L, arg);
		return new String(bytes.array(), bytes.offset(), bytes.size(), StandardCharsets.ISO_8859_1);
	}

	/** A value on the stack as text, for error messages. */
	private static String text(lua_State L, int idx) {
		ByteView bytes = lua_type(L, idx) == LUA_TSTRING || lua_type(L, idx) == LUA_TNUMBER ? lua_tolstring(L, idx) : null;
		return bytes == null ? "(error object is a " + typeName(L, idx) + " value)"
			: new String(bytes.array(), bytes.offset(), bytes.size(), StandardCharsets.UTF_8);
	}

	private static String typeName(lua_State L, int idx) {
		return switch (lua_type(L, idx)) {
			case LUA_TNIL -> "nil";
			case LUA_TBOOLEAN -> "boolean";
			case LUA_TTABLE -> "table";
			case LUA_TUSERDATA -> "userdata";
			default -> "function";
		};
	}

	/** A Lua value as the runtime's ({@link Values}): a sequence becomes a list, other tables maps. */
	private static Object toJava(lua_State L, int idx, int depth) {
		return switch (lua_type(L, idx)) {
			case LUA_TNIL -> null;
			case LUA_TBOOLEAN -> lua_toboolean(L, idx) != 0;
			case LUA_TNUMBER -> lua_isinteger(L, idx) != 0 ? (Object) lua_tointegerx(L, idx).getAsLong() : (Object) lua_tonumberx(L, idx).getAsDouble();
			case LUA_TSTRING -> {
				ByteView bytes = lua_tolstring(L, idx);
				yield new String(bytes.array(), bytes.offset(), bytes.size(), StandardCharsets.ISO_8859_1);
			}
			case LUA_TTABLE -> table(L, idx, depth);
			case LUA_TUSERDATA -> {
				if (lua_touserobject(L, idx) instanceof HandleObject h) {
					yield h.handle();
				}
				luaL_error(L, new HostString("a device can't take this userdata"));
				yield null;
			}
			default -> {
				luaL_error(L, new HostString("a device can't take a " + typeName(L, idx)));
				yield null;
			}
		};
	}

	private static Object table(lua_State L, int idx, int depth) {
		if (depth >= MAX_DEPTH) {
			luaL_error(L, new HostString("tables nest too deep for a device"));
		}
		int at = lua_absindex(L, idx);
		luaL_checkstack(L, 3, new HostString("tables nest too deep for a device"));
		Map<Object, Object> map = new LinkedHashMap<>();
		lua_pushnil(L);
		while (lua_next(L, at) != 0) {
			map.put(toJava(L, -2, depth + 1), toJava(L, -1, depth + 1));
			lua_pop(L, 1);
		}
		for (long i = 1; i <= map.size(); i++) {
			if (!map.containsKey(i)) {
				return map;
			}
		}
		return new ArrayList<>(map.values()); // 1..n: a sequence
	}

	/** Pushes the board's answer: one value. */
	private static int push(lua_State L, Object value) {
		pushValue(L, value, 0);
		return 1;
	}

	private static void pushValue(lua_State L, Object value, int depth) {
		luaL_checkstack(L, 3, new HostString("a device's answer nests too deep"));
		switch (value) {
			case null -> lua_pushnil(L);
			case Boolean b -> lua_pushboolean(L, b ? 1 : 0);
			case Long l -> lua_pushinteger(L, l);
			case Integer i -> lua_pushinteger(L, i);
			case Double d -> lua_pushnumber(L, d);
			case Float f -> lua_pushnumber(L, f);
			case String s -> {
				byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
				lua_pushlstring(L, new ByteView(bytes), 0, bytes.length);
			}
			case Optional<?> optional -> pushValue(L, optional.orElse(null), depth);
			case Handle handle -> lua_newuserobject(L, new HandleObject(handle), 0, 0);
			case Enum<?> e -> pushValue(L, e.name().toLowerCase(java.util.Locale.ROOT), depth);
			case List<?> list -> {
				lua_createtable(L, list.size(), 0);
				for (int i = 0; i < list.size(); i++) {
					pushValue(L, list.get(i), depth + 1);
					lua_rawseti(L, -2, i + 1);
				}
			}
			case Map<?, ?> map -> {
				lua_createtable(L, 0, map.size());
				for (var entry : map.entrySet()) {
					pushValue(L, entry.getKey(), depth + 1);
					pushValue(L, entry.getValue(), depth + 1);
					lua_rawset(L, -3);
				}
			}
			case Record record -> pushValue(L, fields(record), depth);
			default -> pushValue(L, value.toString(), depth);
		}
	}

	/** A record's components, by name: what the board reports ({@code DeviceInfo}, {@code PowerInfo}, ...) as a table. */
	private static Map<String, Object> fields(Record record) {
		Map<String, Object> fields = new LinkedHashMap<>();
		for (RecordComponent component : record.getClass().getRecordComponents()) {
			try {
				fields.put(component.getName(), component.getAccessor().invoke(record));
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		return fields;
	}
}
