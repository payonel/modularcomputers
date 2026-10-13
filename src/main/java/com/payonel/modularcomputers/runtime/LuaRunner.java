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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Lua CPU's host (user design, 2026-10-13): a plain runner for a LungoLua VM, on the computer's
 * own thread (a low-priority platform thread). It runs the CPU's firmware, {@code lua/cpu/machine.lua},
 * as the kernel coroutine and lets it run freely, not once a tick; everything else (the sandbox, the
 * budget and freezes, syscalls) is the firmware's. The kernel's yields are its requests:
 * <ul>
 * <li>{@code "slice"}: its budget froze the EEPROM's code; a safe point. Resumed at once.</li>
 * <li>{@code "call", name, args...}: a board call. Devices live on the server thread, so the call is
 * carried out there ({@link #tick}, and as soon as the server can: {@code serveSoon}), and the
 * computer's thread waits for its answer.</li>
 * <li>{@code "wait", ticks, sources...}: the thread sleeps until one of the sources (any, when none)
 * has an interrupt pending, or {@code ticks} pass ({@code -1}: no limit); answered with those
 * pending.</li>
 * <li>{@code "halt"}: the CPU halts; the thread ends.</li>
 * </ul>
 * The kernel ends with {@code "returned"} (the EEPROM's code returned: a panic), {@code "abort",
 * why} (its text didn't load), or an error (the EEPROM's code crashed).
 * <p>
 * <b>The server's tick is protected by time, not by counting calls</b>: each tick, a computer's calls
 * get {@link #SERVER_NANOS_PER_TICK} of the server thread; past that, they wait for the next tick.
 * Lua itself runs on the computer's thread and costs the server nothing.
 * <p>
 * Values cross as {@link Values}: strings as bytes (ISO-8859-1), handles as userdata, the board's
 * records as tables. The Lua state is only ever touched by the computer's thread.
 */
public final class LuaRunner implements Runner {
	/** How long a computer's board calls may use the server thread each tick. */
	public static final long SERVER_NANOS_PER_TICK = 1_000_000;
	/** The firmware, in the mod's Lua scripts. */
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

	/** The computer's thread was stopped (power off, unload): it unwinds. */
	private static final class Stopped extends RuntimeException {
		Stopped() {
			super(null, null, false, false);
		}
	}

	/** A handle (an open file, say) held by a program: Lua userdata. */
	private record HandleObject(Handle handle) implements lua_UserObject {
	}

	/** A board call waiting for the server thread. */
	private record Call(Function<Board, Object> call, CompletableFuture<Object> answer) {
	}

	/** A wait for an interrupt, answered by the server thread with the sources pending. */
	private record Wait(long until, List<String> sources, CompletableFuture<List<String>> answer) {
		List<String> ready(List<String> pending) {
			return sources.isEmpty() ? pending : pending.stream().filter(sources::contains).toList();
		}
	}

	private final lua_State main;
	private final lua_State kernel;
	private final String image;
	private final ConcurrentLinkedQueue<Call> calls = new ConcurrentLinkedQueue<>();
	private volatile Wait wait;
	private volatile State state = State.RUNNING;
	private volatile Throwable crash;
	private volatile boolean stopping;
	private volatile long answered;
	private Thread thread;
	/** Asks the server thread to serve this computer as soon as it can (a call or a wait came in). */
	private Runnable serveSoon = () -> {
	};
	/** Server side: the tick the time budget is for, and what's spent of it. */
	private long budgetTick = -1;
	private long spentNanos;
	/** Server side: the tick it last saw, for waits with a limit. */
	private long now;

	/** A CPU about to run {@code image}, the EEPROM's text; the firmware loads it when the thread starts. */
	public LuaRunner(String image) {
		this.image = image;
		main = luaL_newstate();
		luaL_openselectedlibs(main, LIBRARIES, 0);
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
	public void serveWith(Runnable serveSoon) {
		this.serveSoon = serveSoon;
	}

	@Override
	public boolean callLimited() {
		return false; // its calls are limited by server time
	}

	@Override
	public State state() {
		State current = state;
		if (current != State.RUNNING) {
			return current; // halted, returned or crashed: the thread is done
		}
		return wait != null && calls.isEmpty() ? State.WAITING : State.RUNNING;
	}

	@Override
	public long answered() {
		return answered;
	}

	@Override
	public Throwable crash() {
		return crash;
	}

	/**
	 * Server side, each tick and whenever asked: starts the thread the first time, carries out the
	 * board calls waiting (within this tick's server time), and answers a wait that's over. Returns
	 * what the program is doing.
	 */
	@Override
	public State tick(Board board, int unused, long now, Supplier<List<String>> interrupts) {
		this.now = now;
		if (thread == null) {
			thread = Thread.ofPlatform().name("modularcomputers-cpu").daemon().priority(Thread.MIN_PRIORITY).unstarted(this::run);
			thread.start();
		}
		if (now != budgetTick) {
			budgetTick = now;
			spentNanos = 0;
		}
		long start = System.nanoTime();
		Call call;
		while (spentNanos + (System.nanoTime() - start) < SERVER_NANOS_PER_TICK && (call = calls.poll()) != null) {
			Object answer;
			try {
				answer = call.call().apply(board);
			} catch (RuntimeException e) {
				answer = Result.fail(e.getMessage()); // a device never stops the machine
			}
			answered++;
			call.answer().complete(answer);
		}
		spentNanos += System.nanoTime() - start;
		Wait waiting = wait;
		if (waiting != null) {
			List<String> pending = waiting.ready(interrupts.get());
			if (!pending.isEmpty() || now >= waiting.until()) {
				wait = null;
				waiting.answer().complete(pending);
			}
		}
		return state();
	}

	@Override
	public void stop() {
		stopping = true;
		if (state == State.RUNNING) {
			state = State.HALTED;
		}
		if (thread != null) {
			thread.interrupt(); // out of a wait for an answer; a running slice sees 'stopping' at its next yield
		}
		// The VM is left to the collector: closing it would run the program's finalizers.
	}

	/** Whether the computer's thread is still going (tests: it ends when stopped). */
	public boolean alive() {
		return thread != null && thread.isAlive();
	}

	/** The computer's thread: runs the firmware, serving its requests, until it halts or is stopped. */
	private void run() {
		try {
			push(kernel, image); // the EEPROM's text, as the CPU read it at power-on
			int answers = 1;
			while (true) {
				if (stopping) {
					return;
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
					switch (String.valueOf(what)) {
						case "slice" -> answers = 0;
						case "call" -> answers = answer(serve(said));
						case "wait" -> answers = push(kernel, await(waitFor(said)));
						case "halt" -> {
							finish(State.HALTED, null);
							return;
						}
						default -> {
							finish(State.CRASHED, new LuaException("the firmware asked for '" + what + "'"));
							return;
						}
					}
				} else if (result.status == LUA_OK) {
					if ("abort".equals(what)) {
						finish(State.CRASHED, new Aborted(String.valueOf(said.size() > 1 ? said.get(1) : "?")));
					} else {
						finish(State.RETURNED, null);
					}
					return;
				} else {
					finish(State.CRASHED, new LuaException(text(kernel, -1)));
					return;
				}
			}
		} catch (Stopped stopped) {
			// power off, or unloaded
		} catch (RuntimeException | StackOverflowError e) {
			finish(State.CRASHED, e);
		}
	}

	private void finish(State end, Throwable why) {
		if (!stopping) {
			crash = why;
			state = end;
			serveSoon.run(); // the board sees it at once
		}
	}

	/** A board call, as the server thread will make it. */
	private CompletableFuture<Object> serve(List<Object> said) {
		String name = String.valueOf(said.size() > 1 ? said.get(1) : "");
		List<Object> args = said.subList(Math.min(2, said.size()), said.size());
		Function<Board, Object> call = switch (name) {
			case "list" -> Board::list;
			case "status" -> Board::status;
			case "type" -> board -> board.type(arg(args, 0));
			case "methods" -> board -> board.methods(arg(args, 0));
			case "doc" -> board -> board.doc(arg(args, 0), arg(args, 1));
			case "info" -> board -> board.info(arg(args, 0));
			case "uptime" -> Board::uptime;
			case "power" -> Board::power;
			case "hardware" -> Board::hardware;
			case "invoke" -> board -> {
				Object target = args.isEmpty() ? null : args.getFirst();
				String method = arg(args, 1);
				Object[] rest = args.size() > 2 ? args.subList(2, args.size()).toArray() : new Object[0];
				return target instanceof Handle handle ? board.invoke(handle, method, rest) : board.invoke(String.valueOf(target), method, rest);
			};
			default -> board -> Result.fail("no board call '" + name + "'");
		};
		Call request = new Call(call, new CompletableFuture<>());
		calls.add(request);
		serveSoon.run();
		return request.answer();
	}

	private static String arg(List<Object> args, int i) {
		return i < args.size() && args.get(i) != null ? String.valueOf(args.get(i)) : "";
	}

	private CompletableFuture<List<String>> waitFor(List<Object> said) {
		long ticks = said.size() > 1 && said.get(1) instanceof Long l ? l : -1;
		List<String> sources = said.stream().skip(2).map(String::valueOf).toList();
		Wait request = new Wait(ticks < 0 ? Long.MAX_VALUE : now + ticks, sources, new CompletableFuture<>());
		wait = request;
		serveSoon.run(); // it may be over already
		return request.answer();
	}

	/** Waits for the server thread's answer; a stop ends the wait. */
	private <T> T await(CompletableFuture<T> answer) {
		try {
			return answer.get();
		} catch (InterruptedException e) {
			throw new Stopped();
		} catch (ExecutionException e) {
			throw new IllegalStateException(e.getCause());
		}
	}

	/** Pushes a board call's answer: an invoke's as pcall's ({@code true} and its values, or {@code false} and why), else its value. */
	private int answer(CompletableFuture<Object> pending) {
		Object answer = await(pending);
		if (answer instanceof Result result) {
			if (result.failed()) {
				lua_pushboolean(kernel, 0);
				push(kernel, result.error());
				return 2;
			}
			luaL_checkstack(kernel, result.values().size() + 1, new HostString("too many results"));
			lua_pushboolean(kernel, 1);
			for (Object value : result.values()) {
				push(kernel, value);
			}
			return 1 + result.values().size();
		}
		return push(kernel, answer);
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
