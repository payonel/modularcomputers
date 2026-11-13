import static com.payonel.lungolua.api.LuaApi.*;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_loadstring;
import static com.payonel.lungolua.api.LuaAuxLib.luaL_newstate;
import static com.payonel.lungolua.lib.Linit.luaL_openlibs;
import static com.payonel.lungolua.runtime.Ldo.lua_freeze;
import static com.payonel.lungolua.runtime.Ldo.lua_isfrozen;
import static com.payonel.lungolua.runtime.Ldo.lua_resume;
import static com.payonel.lungolua.runtime.Ldo.lua_thaw;
import static com.payonel.lungolua.runtime.Lstate.lua_close;
import static com.payonel.lungolua.runtime.Lstate.lua_newthread;
import static com.payonel.lungolua.vm.Ldebug.lua_setbudget;

import com.payonel.lungolua.api.HostString;
import com.payonel.lungolua.api.lua_Budget;
import com.payonel.lungolua.api.lua_ResumeResult;
import com.payonel.lungolua.runtime.lua_State;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sending data from another Java thread into a Lua program that never yields and never calls the
 * host, using LungoLua's budget and freeze.
 *
 * The idea in one sentence: a budget freezes the program every so many steps, and while it's
 * frozen nothing in the Lua state is running, so the host may write into a table the program reads.
 * When the program is thawed, the data is simply there.
 *
 * Run it with only LungoLua on the class path:
 *   javac -cp lungolua.jar InboxExample.java && java -cp lungolua.jar:. InboxExample
 *
 * What it prints (the numbers vary):
 *   java: queued "reading 1" at freeze 644
 *     lua:  read "reading 1" after 1287999 spins
 *   ...
 *   done: 5415991 spins, 2708 freezes
 * A slice is 10,000 steps, about 2,000 spins of the loop, so 644 slices is 1,288,000 spins: the
 * program read each message on the very first spin after the thaw that followed its delivery.
 */
public final class InboxExample {
	/** How many VM steps the program runs before its budget freezes it (one "slice"). */
	private static final long STEPS = 10_000;
	/** How many messages the producer sends, and the program waits for. */
	private static final int MESSAGES = 5;
	/** How long the producer waits between messages, in milliseconds. */
	private static final long PRODUCER_PAUSE_MS = 100;

	/**
	 * The program. It's an ordinary busy loop: it never yields, never calls into Java, and doesn't
	 * know it's being frozen. It just looks at its inbox table now and then.
	 */
	private static final String PROGRAM = """
		local inbox = ...       -- the table the host fills
		local head = 1          -- the next message to read
		local spins = 0         -- how many times round the loop (busy work)
		while head <= %d do
		  local message = inbox[head]
		  if message ~= nil then
		    inbox[head] = nil   -- taken: the host never writes this slot again
		    print(("  lua:  read %%q after %%d spins"):format(message, spins))
		    head = head + 1
		  end
		  spins = spins + 1
		end
		return spins
		""".formatted(MESSAGES);

	/**
	 * Step 3's budget callback: called by the VM when the program has spent its steps. Throwing
	 * lua_freeze freezes the whole running chain (the program and any coroutine it's inside), right
	 * where it is. The program can't see it or catch it, not even with pcall.
	 */
	private static final lua_Budget FREEZE = (userCo, spender) -> {
		throw lua_freeze(spender);
	};

	public static void main(String[] args) throws InterruptedException {
		// Step 1: a Lua state, with the standard libraries (for print).
		lua_State L = luaL_newstate();
		luaL_openlibs(L);

		// Step 2: a thread for the program, and the program loaded on it as a function.
		lua_State co = lua_newthread(L);
		lua_setfield(L, LUA_REGISTRYINDEX, new HostString("program")); // (kept from the collector)
		if (luaL_loadstring(co, new HostString(PROGRAM)) != LUA_OK) {
			throw new IllegalStateException(lua_tostring(co, -1).toString());
		}

		// The shared table. The host keeps it in the registry, so it can find it at every freeze;
		// a copy goes to the program as its argument.
		lua_createtable(L, 0, 0);
		lua_pushvalue(L, -1);
		lua_setfield(L, LUA_REGISTRYINDEX, new HostString("inbox"));
		lua_xmove(L, co, 1); // the argument, on the program's stack

		// Step 3: the budget. From now on the program can't run more than STEPS without the host
		// getting control back: that's the scheduler's timer.
		lua_setbudget(co, STEPS, FREEZE);

		// Step 4: something on another Java thread with data for the program (a sensor, a socket, a
		// timer...). It never touches the Lua state: a Lua state belongs to one thread at a time. It
		// only puts its data in a thread-safe Java queue.
		ConcurrentLinkedQueue<String> outbox = new ConcurrentLinkedQueue<>();
		Thread producer = new Thread(() -> {
			for (int i = 1; i <= MESSAGES; i++) {
				try {
					Thread.sleep(PRODUCER_PAUSE_MS);
				} catch (InterruptedException e) {
					return;
				}
				outbox.add("reading " + i);
			}
		}, "producer");
		producer.start();

		// Step 5: the host's loop. Run the program; each time it comes back frozen, move what the
		// producer sent into the inbox table, then thaw it.
		long tail = 1; // the next free slot in the inbox: the host's own count
		long freezes = 0;
		lua_ResumeResult result = lua_resume(co, null, 1);
		while (result.status == LUA_OK && lua_isfrozen(co)) {
			freezes++;
			// Frozen: no Lua is running, so the table is ours to change. No lock, no yield, and the
			// program needs no "receive" call.
			String message;
			while ((message = outbox.poll()) != null) {
				lua_getfield(L, LUA_REGISTRYINDEX, new HostString("inbox"));
				lua_pushstring(L, new HostString(message));
				lua_rawseti(L, -2, tail++);
				lua_pop(L, 1);
				System.out.printf("java: queued \"%s\" at freeze %d%n", message, freezes);
			}
			// Step 6: thaw. The program carries on exactly where it was frozen, and its next look at
			// the inbox finds what was just put there.
			result = lua_thaw(co, null);
		}

		// The program returned (or failed): it's no longer frozen.
		if (result.status != LUA_OK) {
			throw new IllegalStateException("the program failed: " + lua_tostring(co, -1));
		}
		System.out.printf("done: %d spins, %d freezes%n", lua_tointegerx(co, -1).getAsLong(), freezes);
		producer.join();
		lua_close(L);
	}
}
