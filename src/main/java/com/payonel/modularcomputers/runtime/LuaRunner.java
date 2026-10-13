package com.payonel.modularcomputers.runtime;

import static com.payonel.lungolua.api.LuaApi.*;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checkinteger;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checklstring;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checkstack;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_error;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_loadbufferx;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_newstate;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_optnumber;
import static com.payonel.lungolua.lib.Linit.luaL_openselectedlibs;
import static com.payonel.lungolua.lib.Lualib.*;
import static com.payonel.lungolua.object.Lnames.lua_registerbudget;
import static com.payonel.lungolua.runtime.Ldo.lua_freeze;
import static com.payonel.lungolua.runtime.Ldo.lua_isfrozen;
import static com.payonel.lungolua.runtime.Ldo.lua_isyieldable;
import static com.payonel.lungolua.runtime.Ldo.lua_nofreeze;
import static com.payonel.lungolua.runtime.Ldo.lua_resume;
import static com.payonel.lungolua.runtime.Ldo.lua_thaw;
import static com.payonel.lungolua.runtime.Ldo.lua_yieldk;
import static com.payonel.lungolua.runtime.Lstate.lua_newthread;
import static com.payonel.lungolua.vm.Ldebug.lua_setbudget;

import com.payonel.lungolua.api.HostString;
import com.payonel.lungolua.api.lua_Budget;
import com.payonel.lungolua.api.lua_CFunction;
import com.payonel.lungolua.api.lua_ResumeResult;
import com.payonel.lungolua.api.lua_UserObject;
import com.payonel.lungolua.bytes.ByteView;
import com.payonel.lungolua.runtime.lua_State;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A Lua CPU's program (user design, 2026-10-12): the EEPROM's text as a Lua chunk, run by LungoLua
 * (the user's Lua engine) on the server thread, as a coroutine the host resumes each tick. No
 * thread of its own: the engine's budget freezes the program after {@link #STEPS_PER_TICK} steps (a
 * step is about a VM instruction) wherever it is, and the next tick thaws it, so even a loop that
 * never calls the board is preempted. A board call past the tick's share, a wait for an interrupt
 * and a halt yield to the host. {@code coroutine.yield} at the top yields a turn.
 * <p>
 * The program sees the motherboard API as the global {@code board}, as a Java program sees
 * {@link Board}: {@code list}, {@code status}, {@code type}, {@code methods}, {@code doc},
 * {@code invoke}, {@code waitForInterrupt}, {@code uptime}, {@code power}, {@code hardware},
 * {@code info}, {@code halt}. {@code invoke} answers as {@code pcall} does: {@code true} and the
 * method's values, or {@code false} and why it failed (a device never stops the machine). Of
 * Lua's libraries it has the base library without files ({@code dofile}, {@code loadfile}) or
 * standard output ({@code print}), and coroutine, string, table, math and utf8: no io, os,
 * package or debug, which reach the server's own files and processes.
 * <p>
 * Not saveable yet: the engine can persist a frozen state, but the machine doesn't keep one.
 */
public final class LuaRunner implements Runner {
	/** Steps the program runs each tick before its budget freezes it. */
	public static final long STEPS_PER_TICK = 100_000;
	/** The chunk's name in Lua's messages: {@code eeprom:3: ...}. */
	private static final String CHUNK = "=eeprom";
	private static final int LIBRARIES = LUA_GLIBK | LUA_COLIBK | LUA_TABLIBK | LUA_STRLIBK | LUA_MATHLIBK | LUA_UTF8LIBK;
	/** How deep tables nest between programs and devices. */
	private static final int MAX_DEPTH = 16;

	/** The budget's callback: freeze the program where it is, unless a call that can't freeze is in the way. */
	private static void tickSpent(lua_State user, lua_State spender) {
		if (lua_nofreeze(spender) == 0) {
			lua_freeze(spender);
		}
	}

	private static final lua_Budget TICK_SPENT = LuaRunner::tickSpent;

	static {
		lua_registerbudget("modularcomputers.tick", TICK_SPENT);
	}

	/** The EEPROM's text isn't a Lua chunk: Lua's own message says where. */
	public static final class LoadFailed extends Exception {
		LoadFailed(String message) {
			super(message);
		}
	}

	/** Lua's error, as the program left it. */
	public static final class LuaException extends RuntimeException {
		LuaException(String message) {
			super(message);
		}
	}

	/** A handle (an open file, say) held by a program: Lua userdata. */
	private record HandleObject(Handle handle) implements lua_UserObject {
	}

	/** A program asks the host for a wait: answered with the sources pending. */
	private record Wait(long until, List<String> sources) {
		List<String> ready(List<String> pending) {
			return sources.isEmpty() ? pending : pending.stream().filter(sources::contains).toList();
		}
	}

	private final lua_State main;
	private final lua_State program;
	private State state = State.RUNNING;
	private Throwable crash;
	private long answered;
	private boolean started;
	private boolean halted;
	/** During a turn: the real board, the calls left and the tick. */
	private Board board;
	private int calls;
	private long now;
	/** The program waits for an interrupt; what it got when it's over. */
	private Wait wait;
	private List<String> ready = List.of();

	/** Compiles {@code image}; it runs from the first {@link #tick}. */
	public LuaRunner(String image) throws LoadFailed {
		main = luaL_newstate();
		luaL_openselectedlibs(main, LIBRARIES, 0);
		for (String unsafe : List.of("dofile", "loadfile", "print")) {
			lua_pushnil(main);
			lua_setglobal(main, new HostString(unsafe));
		}
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
		lua_pushcfunction(main, this::waitForInterrupt);
		lua_setfield(main, -2, new HostString("waitForInterrupt"));
		lua_pushcfunction(main, this::halt);
		lua_setfield(main, -2, new HostString("halt"));
		lua_setglobal(main, new HostString("board"));

		program = lua_newthread(main);
		lua_setfield(main, LUA_REGISTRYINDEX, new HostString("modularcomputers.program")); // kept from the collector
		byte[] code = image.getBytes(StandardCharsets.UTF_8);
		if (luaL_loadbufferx(program, new ByteView(code), new HostString(CHUNK), new HostString("t")) != LUA_OK) {
			throw new LoadFailed(text(program, -1));
		}
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
				lua_ResumeResult result;
				if (wait != null) {
					List<String> pending = wait.ready(interrupts.get());
					if (pending.isEmpty() && now < wait.until()) {
						return state = State.WAITING;
					}
					ready = pending;
					wait = null;
					result = lua_resume(program, null, 0);
				} else if (lua_isfrozen(program) != 0) {
					lua_setbudget(program, STEPS_PER_TICK, TICK_SPENT); // a new tick's steps
					result = lua_thaw(program, null);
				} else {
					if (!started) {
						started = true;
						lua_setbudget(program, STEPS_PER_TICK, TICK_SPENT);
					}
					result = lua_resume(program, null, 0);
				}
				if (halted) {
					return state = State.HALTED;
				}
				if (result.status == LUA_YIELD) {
					if (wait != null) {
						continue; // it may be over already
					}
					return state = State.RUNNING; // out of calls, or the program gave up its turn
				}
				if (result.status != LUA_OK) {
					crash = new LuaException(text(program, -1));
					return state = State.CRASHED;
				}
				return state = lua_isfrozen(program) != 0 ? State.RUNNING : State.RETURNED;
			}
		} finally {
			this.board = null;
		}
	}

	@Override
	public void stop() {
		state = State.HALTED; // the state is left to the collector: closing it would run the program's finalizers
	}

	/** A board function that answers right away: one of the tick's calls. */
	private void function(String name, lua_CFunction body) {
		lua_pushcfunction(main, L -> call(L, body));
		lua_setfield(main, -2, new HostString(name));
	}

	/**
	 * Out of calls this tick, the program yields and makes the call when the host resumes it, as
	 * a Java program's call waits for the next tick. Where it can't yield (inside a sort's
	 * comparison, say), the call goes ahead.
	 */
	private int call(lua_State L, lua_CFunction body) {
		if (calls <= 0 && lua_isyieldable(L) != 0) {
			return lua_yieldk(L, 0, 0, (K, status, context) -> call(K, body));
		}
		calls--;
		answered++;
		return body.call(L);
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

	/** {@code waitForInterrupt([seconds], sources...)}: yields to the host until one is pending, or time's up. */
	private int waitForInterrupt(lua_State L) {
		double seconds = luaL_optnumber(L, 1, Double.POSITIVE_INFINITY);
		List<String> sources = new ArrayList<>();
		for (int i = 2; i <= lua_gettop(L); i++) {
			sources.add(string(L, i));
		}
		long ticks = Double.isInfinite(seconds) ? Long.MAX_VALUE / 2 : Math.max(0, Math.round(seconds * 20));
		wait = new Wait(now + ticks, List.copyOf(sources));
		return lua_yieldk(L, 0, 0, (K, status, context) -> push(K, ready));
	}

	/** {@code halt()}: the CPU stops; power stays on. It never returns. */
	private int halt(lua_State L) {
		halted = true;
		if (lua_isyieldable(L) != 0) {
			return lua_yieldk(L, 0, 0, (K, status, context) -> 0); // never resumed
		}
		return luaL_error(L, new HostString("halted")); // where it can't yield; the host stops it all the same
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
