package com.payonel.modularcomputers.runtime;

import static com.payonel.lungolua.api.LuaApi.*;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checklstring;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_checkstack;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_error;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_loadbufferx;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_newstate;
import static com.payonel.lungolua.lib.Linit.luaL_openselectedlibs;
import static com.payonel.lungolua.lib.Lualib.*;
import static com.payonel.lungolua.lib.Lstyx.luaL_persiststate;
import static com.payonel.lungolua.lib.Lstyx.luaL_restorestate;
import static com.payonel.lungolua.runtime.Ldo.lua_resume;
import static com.payonel.lungolua.runtime.Lstate.lua_newthread;

import com.payonel.lungolua.api.HostString;
import com.payonel.lungolua.api.lua_ResumeResult;
import com.payonel.lungolua.api.lua_UserObject;
import com.payonel.lungolua.api.lua_Writer;
import com.payonel.lungolua.lib.luaL_Restored;
import com.payonel.lungolua.lib.luaL_StyxResult;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>
 * <b>Saving a running computer</b> (Styx, LungoLua's persistence, which works from the main thread
 * only, so it's the host's): the server thread pins a request ({@link #persist}) and the computer's
 * thread honours it at its next safe point: the kernel suspended in a yield, its request still on its
 * stack (a slice, a board call not yet made, or a wait, honoured at once). No Lua runs while it
 * persists. {@link #restore} makes the state again; the restored kernel's request is on its stack,
 * and the computer's thread carries on with it (a wait starts over). The permanents (the standard
 * library's functions and userdata, named by their path) are worked out the same way in both.
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
	/** Registry keys: the kernel, and the permanents (object to name). */
	private static final String KERNEL = "modularcomputers.kernel";
	private static final String PERMANENTS = "modularcomputers.perms";
	/** How long a save waits for the computer's thread to reach a safe point. */
	private static final long PERSIST_NANOS = 2_000_000_000L;
	/**
	 * The permanents: every C function and userdata reachable from the registry and the string
	 * metatable once the libraries are open, and each such userdata's metatable, named by its path
	 * (keys sorted, so two states with the same libraries agree); kept in the registry (object to
	 * name), and the inverse returned. LungoLua's own gate works them out this way.
	 */
	private static final String PERMANENTS_WALK = """
		local perms, seen = {}, {}
		local function upv(f) local _, x = debug.getupvalue(f, 1) return x end
		local function walk(t, name)
		  if seen[t] then return end
		  seen[t] = true
		  local keys = {}
		  for k in pairs(t) do keys[#keys + 1] = k end
		  table.sort(keys, function(a, b) return tostring(a) < tostring(b) end)
		  for _, k in ipairs(keys) do
		    local v = t[k]
		    local n = name .. '.' .. tostring(k)
		    if (type(v) == 'function' and debug.getinfo(v, 'S').what == 'C' and type(upv(v)) ~= 'table') or type(v) == 'userdata' then
		      if perms[v] == nil then perms[v] = n end
		      local mt = type(v) == 'userdata' and debug.getmetatable(v)
		      if mt and perms[mt] == nil then perms[mt] = n .. '<mt>' end
		    elseif type(v) == 'table' then
		      walk(v, n)
		    end
		  end
		  local mt = getmetatable(t)
		  if mt then walk(mt, name .. '<mt>') end
		end
		walk(getmetatable(''), 'smt')
		walk(debug.getregistry(), 'R')
		local back = {}
		for v, n in pairs(perms) do back[n] = v end
		debug.getregistry()['modularcomputers.perms'] = perms
		return back
		""";

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

	/** A handle (an open file, say) held by a program: Lua userdata, kept by Styx when its kind can be. */
	private record HandleObject(Handle handle) implements lua_UserObject {
		@Override
		public String persistkind() {
			return handle instanceof com.payonel.modularcomputers.device.Disk.FileHandle ? com.payonel.modularcomputers.device.Disk.FileHandle.KIND : null;
		}

		@Override
		public ByteView persistbytes() {
			return handle instanceof com.payonel.modularcomputers.device.Disk.FileHandle file ? new ByteView(file.persisted()) : ByteView.EMPTY;
		}
	}

	static {
		com.payonel.lungolua.object.Lnames.lua_registerobjectkind(com.payonel.modularcomputers.device.Disk.FileHandle.KIND, (L, ints, count, bytes) ->
			new HandleObject(com.payonel.modularcomputers.device.Disk.FileHandle.restore(
				java.util.Arrays.copyOfRange(bytes.array(), bytes.offset(), bytes.offset() + bytes.size()))));
	}

	/** A board call waiting for the server thread. */
	private record Call(Function<Board, Object> call, CompletableFuture<Object> answer) {
	}

	/**
	 * A wait for an interrupt, answered by the server thread with the sources pending and the device
	 * data taken for the firmware's inbox; {@code held}: what the inbox holds now, by address.
	 */
	private record Wait(long until, List<String> sources, Map<String, Integer> held, CompletableFuture<List<Object>> answer) {
		List<String> ready(List<String> pending) {
			return sources.isEmpty() ? pending : pending.stream().filter(sources::contains).toList();
		}
	}

	private final lua_State main;
	private final lua_State kernel;
	/** The EEPROM's text, for a fresh start; null for a restored one (its request is on the kernel's stack). */
	private final String image;
	/** A pinned request to persist, answered with the state's bytes (or null, when it can't be). */
	private final AtomicReference<Snapshot> pin = new AtomicReference<>();
	/**
	 * A save asked of the computer's thread: its state's bytes once it's at a safe point (null when it
	 * can't be saved, or it ended). {@code servedAt}: what the server thread had handed it then
	 * ({@link #served}); bytes taken after more was handed over would miss it, so they're not used.
	 */
	private record Snapshot(long servedAt, CompletableFuture<byte[]> bytes) {
	}

	/** The computer's thread has ended: set before it looks for a save asked, so one asked after sees it. */
	private volatile boolean ended;
	/** Server side: counts what's handed to the computer's thread (calls' and waits' answers). */
	private volatile long served;
	/** The computers whose threads run, for {@link #prepareAll}. */
	private static final java.util.Set<LuaRunner> LIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
	private volatile long now;

	/** A CPU about to run {@code image}, the EEPROM's text; the firmware loads it when the thread starts. */
	public LuaRunner(String image) {
		this.image = image;
		main = luaL_newstate();
		prepare(main);
		lua_pop(main, 1); // (the inverse permanents: only a restore needs them)
		kernel = lua_newthread(main);
		lua_setfield(main, LUA_REGISTRYINDEX, new HostString(KERNEL)); // kept from the collector, and found again after a restore
		byte[] firmware = LuaScripts.read(FIRMWARE).getBytes(StandardCharsets.ISO_8859_1);
		if (luaL_loadbufferx(kernel, new ByteView(firmware), new HostString("=machine"), new HostString("t")) != LUA_OK) {
			throw new IllegalStateException("the CPU's firmware doesn't load: " + text(kernel, -1)); // a bug in the mod
		}
	}

	/** A computer coming back from its saved state ({@link #persist}). */
	private LuaRunner(lua_State main) {
		this.image = null;
		this.main = main;
		lua_getfield(main, LUA_REGISTRYINDEX, new HostString(KERNEL));
		this.kernel = lua_tothread(main, -1);
		lua_pop(main, 1);
	}

	/** The same libraries and permanents for a new state and a restored one; leaves the inverse permanents (name to object) on the stack. */
	private static void prepare(lua_State L) {
		luaL_openselectedlibs(L, LIBRARIES, 0);
		byte[] walk = PERMANENTS_WALK.getBytes(StandardCharsets.UTF_8);
		if (luaL_loadbufferx(L, new ByteView(walk), new HostString("=permanents"), new HostString("t")) != LUA_OK) {
			throw new IllegalStateException("the permanents don't load: " + text(L, -1));
		}
		lua_call(L, 0, 1);
	}

	/** Makes a saved computer again ({@link #persist}'s bytes); it carries on from where it was saved when its thread starts. */
	public static LuaRunner restore(byte[] saved) {
		lua_State scratch = luaL_newstate(); // (for the allocator)
		luaL_Restored restored = luaL_restorestate(lua_getallocf(scratch), new ByteView(saved), LuaRunner::prepare);
		if (restored.L == null) {
			throw new IllegalStateException("the computer's saved state doesn't restore: " + restored.msg);
		}
		lua_settop(restored.L, 0); // (nothing of the host's is left on it)
		return new LuaRunner(restored.L);
	}

	/**
	 * Server side, as a world save starts (autosave, /save-all, shutdown; Fabric's BEFORE_SAVE):
	 * every running computer is asked for its state at once, and each saves itself on its own
	 * thread, in parallel, at its next safe point. Each case's save then takes its computer's bytes,
	 * so the save waits about as long as the slowest computer, not all of them one after another.
	 * Nothing is handed to the computers until the save is over, so the snapshots stay good.
	 */
	public static void prepareAll() {
		LIVE.forEach(LuaRunner::prepare);
	}

	/** Server side, as a world save ends: snapshots no case took are dropped (they'd go stale). */
	public static void releaseAll() {
		LIVE.forEach(runner -> runner.pin.set(null));
	}

	/** Server side: asks for a snapshot now, unless a good one is asked for already. */
	private Snapshot prepare() {
		Snapshot current = pin.get();
		if (current != null && current.servedAt() == served) {
			return current;
		}
		Snapshot fresh = new Snapshot(served, new CompletableFuture<>());
		pin.set(fresh);
		ring();
		if (thread == null || ended) {
			fresh.bytes().complete(null); // not running, or it ended: nothing will honour it
		}
		return fresh;
	}

	/**
	 * Server side: saves the computer as it runs: the snapshot asked for as the save started
	 * ({@link #prepareAll}), or one asked for now (a chunk unloading), taken by the computer's thread
	 * at its next safe point. It carries out no calls meanwhile: a call queued but not made is a safe
	 * point itself (the computer's thread takes it back, saves with the request still on the kernel's
	 * stack, then queues it again), so a save never moves data between the program and its devices,
	 * and every block's saved copy agrees with the program's. Empty when it can't be saved now (it's
	 * not running, the state holds what can't be persisted, or it took too long); the computer then
	 * comes back halted.
	 */
	@Override
	public Optional<byte[]> persist(Board board) {
		if (thread == null || !thread.isAlive() || state != State.RUNNING) {
			return Optional.empty();
		}
		Snapshot snapshot = prepare();
		try {
			return Optional.ofNullable(snapshot.bytes().get(PERSIST_NANOS, TimeUnit.NANOSECONDS));
		} catch (TimeoutException e) {
			com.payonel.modularcomputers.ModularComputers.LOGGER.error(
				"A running computer didn't reach a safe point to be saved in {} ms: it will come back halted, its program lost", PERSIST_NANOS / 1_000_000);
		} catch (InterruptedException | ExecutionException e) {
			com.payonel.modularcomputers.ModularComputers.LOGGER.error("A running computer couldn't be saved: it will come back halted", e);
		} finally {
			pin.compareAndSet(snapshot, null);
		}
		return Optional.empty();
	}

	/** Wakes the computer's thread: it has news (an answer, a save asked for, a stop), like an interrupt pulling a CPU out of HLT. */
	private void ring() {
		Thread waiting = thread;
		if (waiting != null) {
			java.util.concurrent.locks.LockSupport.unpark(waiting);
		}
	}

	/** Computer's thread: whether a save is asked for and not yet taken. */
	private boolean saveAsked() {
		Snapshot asked = pin.get();
		return asked != null && !asked.bytes().isDone();
	}

	/** Computer's thread, at a safe point: honours a pinned save. */
	private void honour() {
		honour(null);
	}

	/**
	 * {@link #honour()}, unless {@code answered} (the wait it's in) has its answer: then the answer
	 * (it may carry device data, out of the devices now) goes into the VM first, and the pin is
	 * honoured at the next safe point. The server thread answers a wait before it pins a save, so
	 * seeing the pin means seeing the answer.
	 */
	private void honour(CompletableFuture<?> answered) {
		Snapshot asked = pin.get();
		if (asked == null || asked.bytes().isDone() || (answered != null && answered.isDone())) {
			return;
		}
		lua_getfield(main, LUA_REGISTRYINDEX, new HostString(PERMANENTS));
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		lua_Writer writer = (L, bytes) -> {
			if (bytes != null) {
				out.write(bytes.array(), bytes.offset(), bytes.size());
			}
			return 0;
		};
		luaL_StyxResult result = luaL_persiststate(main, lua_gettop(main), writer);
		lua_pop(main, 1);
		if (result.status != LUA_OK) {
			com.payonel.modularcomputers.ModularComputers.LOGGER.warn("A computer can't be saved as it runs: {}", result.msg);
			asked.bytes().complete(null);
		} else {
			asked.bytes().complete(out.toByteArray());
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
			LIVE.add(this);
			thread.start();
		}
		if (now != budgetTick) {
			budgetTick = now;
			spentNanos = 0;
		}
		long start = System.nanoTime();
		Call call;
		while (spentNanos + (System.nanoTime() - start) < SERVER_NANOS_PER_TICK && (call = calls.poll()) != null) {
			answer(call, board);
		}
		Wait waiting = wait;
		if (waiting != null) {
			List<String> pending = new ArrayList<>(interrupts.get());
			waiting.held().forEach((address, held) -> {
				if (held > 0 && !pending.contains(address)) {
					pending.add(address); // its data's in the inbox: still pending for the program
				}
			});
			List<String> ready = waiting.ready(pending);
			if (!ready.isEmpty() || now >= waiting.until()) {
				// Only now is data taken from the devices: it goes straight into the answer.
				List<Map<String, Object>> taken = take(board, interrupts.get(), waiting.held(), start);
				wait = null;
				served++;
				waiting.answer().complete(List.of(ready, taken));
				ring();
			}
		}
		spentNanos += System.nanoTime() - start;
		return state();
	}

	/**
	 * Server side, as a wait is answered: takes device data for the firmware's inbox (machine.lua's
	 * cache, in the VM; user design, 2026-10-13), from each source with data, as much as the inbox has
	 * room for that device: {@link Board#inboxSize} (from the RAM: each device has its own room, so a
	 * full one never crowds out another) less what it {@code held}s, within the tick's server time.
	 * Handshake: a device keeps what there's no room for, so nothing is dropped.
	 */
	private List<Map<String, Object>> take(Board board, List<String> sources, Map<String, Integer> held, long start) {
		List<Map<String, Object>> taken = new ArrayList<>();
		int size = board.inboxSize();
		for (String source : sources) {
			String method = board.streamMethod(source).orElse(null);
			int room = size - held.getOrDefault(source, 0);
			while (method != null && room > 0 && spentNanos + (System.nanoTime() - start) < SERVER_NANOS_PER_TICK) {
				Result result = board.invoke(source, method);
				if (result.failed() || result.first() == null) {
					break;
				}
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("address", source);
				item.put("method", method);
				item.put("value", result.first());
				taken.add(item);
				room--;
			}
		}
		return taken;
	}

	/** Server side: makes a board call and hands back its answer. */
	private void answer(Call call, Board board) {
		Object answer;
		try {
			answer = call.call().apply(board);
		} catch (RuntimeException e) {
			answer = Result.fail(e.getMessage()); // a device never stops the machine
		}
		answered++;
		served++;
		call.answer().complete(answer);
		ring();
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

	/**
	 * The computer's thread: runs the firmware, serving its requests, until it halts or is stopped.
	 */
	private void run() {
		try {
			List<Object> reply = image != null ? List.of(image) : null; // (restored: its request is on its stack)
			while (true) {
				if (stopping) {
					return;
				}
				int asked;
				if (reply != null) {
					luaL_checkstack(kernel, reply.size() + 1, new HostString("too many results"));
					for (Object value : reply) {
						push(kernel, value);
					}
					lua_ResumeResult result = lua_resume(kernel, null, reply.size());
					if (result.status != LUA_YIELD) {
						end(result);
						return;
					}
					asked = result.nres;
				} else {
					asked = lua_gettop(kernel);
				}
				// The request stays on the kernel's stack until it's answered: a save now keeps it.
				List<Object> said = new ArrayList<>();
				int top = lua_gettop(kernel);
				for (int i = top - asked + 1; i <= top; i++) {
					said.add(toJava(kernel, i, 0));
				}
				Object what = said.isEmpty() ? null : said.getFirst();
				switch (String.valueOf(what)) {
					case "slice" -> {
						honour();
						lua_settop(kernel, top - asked);
						reply = List.of();
					}
					case "call" -> {
						honour();
						Object answer = awaitCall(serve(said));
						lua_settop(kernel, top - asked);
						reply = values(answer);
					}
					case "wait" -> {
						honour();
						reply = awaitWait(waitFor(said)); // the sources ready, and the device data taken
						lua_settop(kernel, top - asked);
					}
					case "clock" -> {
						// The machine's clock, in ticks (uptime): the firmware makes a wait's deadline from it.
						lua_settop(kernel, top - asked);
						reply = List.of(now);
					}
					case "halt" -> {
						finish(State.HALTED, null);
						return;
					}
					default -> {
						finish(State.CRASHED, new LuaException("the firmware asked for '" + what + "'"));
						return;
					}
				}
			}
		} catch (Stopped stopped) {
			// power off, or unloaded
		} catch (RuntimeException | StackOverflowError e) {
			finish(State.CRASHED, e);
		} finally {
			LIVE.remove(this);
			ended = true;
			// Ended (halted, crashed, stopped): a save asked for gets "nothing to save" at once.
			Snapshot asked = pin.get();
			if (asked != null) {
				asked.bytes().complete(null);
			}
		}
	}

	/** A board call's answer as the firmware gets it: an invoke's as pcall's ({@code true} and its values, or {@code false} and why), else its value. */
	private static List<Object> values(Object answer) {
		if (answer instanceof Result result) {
			List<Object> values = new ArrayList<>();
			if (result.failed()) {
				values.add(false);
				values.add(result.error());
			} else {
				values.add(true);
				values.addAll(result.values());
			}
			return values;
		}
		return java.util.Collections.singletonList(answer);
	}

	/** The kernel returned or failed: the EEPROM's code returned, its text didn't load, or it crashed. */
	private void end(lua_ResumeResult result) {
		if (result.status == LUA_OK) {
			int top = lua_gettop(kernel);
			Object what = result.nres > 0 ? toJava(kernel, top - result.nres + 1, 0) : null;
			if ("abort".equals(what)) {
				Object why = result.nres > 1 ? toJava(kernel, top - result.nres + 2, 0) : "?";
				finish(State.CRASHED, new Aborted(String.valueOf(why)));
			} else {
				finish(State.RETURNED, null);
			}
		} else {
			finish(State.CRASHED, new LuaException(text(kernel, -1)));
		}
	}

	/**
	 * Waits for a wait's answer, parked until there's news ({@link #ring}): the answer, a save asked
	 * for (honoured here: the kernel is at a safe point), or a stop.
	 */
	private List<Object> awaitWait(CompletableFuture<List<Object>> answer) {
		while (true) {
			if (answer.isDone()) {
				return answer.join();
			}
			if (saveAsked()) {
				honour(answer);
				continue;
			}
			park();
		}
	}

	/** Parks the computer's thread until {@link #ring} (or a stop, which interrupts it). */
	private void park() {
		java.util.concurrent.locks.LockSupport.park(this);
		if (Thread.interrupted() || stopping) {
			throw new Stopped();
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
	private Call serve(List<Object> said) {
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
		return request;
	}

	/**
	 * Waits for a call's answer. A save pinned meanwhile, while the call isn't made yet, is honoured
	 * here: the call comes back out of the queue (the server thread, saving, isn't taking from it),
	 * the state is saved with the request still on the kernel's stack, and the call goes back in.
	 */
	private Object awaitCall(Call call) {
		while (true) {
			if (call.answer().isDone()) {
				return call.answer().join();
			}
			if (saveAsked() && calls.remove(call)) {
				honour();
				calls.add(call);
				serveSoon.run();
				continue;
			}
			park();
		}
	}

	/** How many calls wait for the server thread (tests). */
	int queuedCalls() {
		return calls.size();
	}

	private static String arg(List<Object> args, int i) {
		return i < args.size() && args.get(i) != null ? String.valueOf(args.get(i)) : "";
	}

	/**
	 * {@code "wait", deadline, held, sources...}: the deadline is on the machine's clock (uptime; -1
	 * for none), so a wait saved partway ends when it would have; held is what the firmware's inbox
	 * holds now, by address.
	 */
	private CompletableFuture<List<Object>> waitFor(List<Object> said) {
		long deadline = said.size() > 1 && said.get(1) instanceof Long l ? l : -1;
		Map<String, Integer> held = new java.util.HashMap<>();
		if (said.size() > 2 && said.get(2) instanceof Map<?, ?> map) {
			map.forEach((address, count) -> held.put(String.valueOf(address), count instanceof Long n ? n.intValue() : 0));
		}
		List<String> sources = said.stream().skip(3).map(String::valueOf).toList();
		Wait request = new Wait(deadline < 0 ? Long.MAX_VALUE : deadline, sources, held, new CompletableFuture<>());
		wait = request;
		serveSoon.run(); // it may be over already
		return request.answer();
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
			case LUA_TBOOLEAN -> lua_toboolean(L, idx);
			case LUA_TNUMBER -> lua_isinteger(L, idx) ? (Object) lua_tointegerx(L, idx).getAsLong() : (Object) lua_tonumberx(L, idx).getAsDouble();
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
		while (lua_next(L, at)) {
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
			case Boolean b -> lua_pushboolean(L, b);
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
