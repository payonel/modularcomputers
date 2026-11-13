using System;
using System.Collections.Concurrent;
using System.Threading;
using Payonel.LungoLua.Api;
using lua_State = Payonel.LungoLua.Runtime.lua_State;
using static Payonel.LungoLua.Api.LuaApi;
using static Payonel.LungoLua.Api.LuaAuxLib;
using static Payonel.LungoLua.Lib.Linit;
using static Payonel.LungoLua.Runtime.Ldo;
using static Payonel.LungoLua.Runtime.Lstate;
using static Payonel.LungoLua.Vm.Ldebug;

/*
** InboxExample.java on LungoLua's C# port: the same steps and the same calls, for a host written
** in C#. Modular Computers runs on Java; this is only how the same thing is done in .NET.
**
** Sending data from another .NET thread into a Lua program that never yields and never calls the
** host, using LungoLua's budget and freeze.
**
** Build it against lungolua.dll (made by `make -C csharp` in payonel/lua, branch lungolua), with
** the .NET 10 SDK, then run it: `dotnet InboxExample.dll`.
**
** What it prints (the numbers vary):
**   C#: queued "reading 1" at freeze 644
**     lua:  read "reading 1" after 1287999 spins
**   ...
**   done: 5415991 spins, 2708 freezes
*/
public static class InboxExample
{
  /* How many VM steps the program runs before its budget freezes it (one "slice"). */
  private const long STEPS = 10_000;
  /* How many messages the producer sends, and the program waits for. */
  private const int MESSAGES = 5;
  /* How long the producer waits between messages, in milliseconds. */
  private const int PRODUCER_PAUSE_MS = 100;

  /*
  ** The program. It's an ordinary busy loop: it never yields, never calls into C#, and doesn't
  ** know it's being frozen. It just looks at its inbox table now and then.
  */
  private static readonly string PROGRAM = @"
    local inbox = ...       -- the table the host fills
    local head = 1          -- the next message to read
    local spins = 0         -- how many times round the loop (busy work)
    while head <= MESSAGES do
      local message = inbox[head]
      if message ~= nil then
        inbox[head] = nil   -- taken: the host never writes this slot again
        print(('  lua:  read %q after %d spins'):format(message, spins))
        head = head + 1
      end
      spins = spins + 1
    end
    return spins
  ".Replace("MESSAGES", MESSAGES.ToString());

  /*
  ** Step 3's budget callback: called by the VM when the program has spent its steps. Throwing
  ** lua_freeze freezes the whole running chain (the program and any coroutine it's inside), right
  ** where it is. The program can't see it or catch it, not even with pcall.
  */
  private static readonly lua_Budget FREEZE = (userCo, spender) => { throw lua_freeze(spender); };

  public static void Main()
  {
    // Step 1: a Lua state, with the standard libraries (for print).
    lua_State L = luaL_newstate();
    luaL_openlibs(L);

    // Step 2: a thread for the program, and the program loaded on it as a function.
    lua_State co = lua_newthread(L);
    lua_setfield(L, LUA_REGISTRYINDEX, new HostString("program")); // (kept from the collector)
    if (luaL_loadstring(co, new HostString(PROGRAM)) != LUA_OK)
      throw new InvalidOperationException(lua_tostring(co, -1).ToString());

    // The shared table. The host keeps it in the registry, so it can find it at every freeze;
    // a copy goes to the program as its argument.
    lua_createtable(L, 0, 0);
    lua_pushvalue(L, -1);
    lua_setfield(L, LUA_REGISTRYINDEX, new HostString("inbox"));
    lua_xmove(L, co, 1); // the argument, on the program's stack

    // Step 3: the budget. From now on the program can't run more than STEPS without the host
    // getting control back: that's the scheduler's timer.
    lua_setbudget(co, STEPS, FREEZE);

    // Step 4: something on another .NET thread with data for the program (a sensor, a socket, a
    // timer...). It never touches the Lua state: a Lua state belongs to one thread at a time. It
    // only puts its data in a thread-safe queue.
    var outbox = new ConcurrentQueue<string>();
    var producer = new Thread(() =>
    {
      for (int i = 1; i <= MESSAGES; i++)
      {
        Thread.Sleep(PRODUCER_PAUSE_MS);
        outbox.Enqueue("reading " + i);
      }
    });
    producer.Start();

    // Step 5: the host's loop. Run the program; each time it comes back frozen, move what the
    // producer sent into the inbox table, then thaw it.
    long tail = 1; // the next free slot in the inbox: the host's own count
    long freezes = 0;
    lua_ResumeResult result = lua_resume(co, null, 1);
    while (result.status == LUA_OK && lua_isfrozen(co))
    {
      freezes++;
      // Frozen: no Lua is running, so the table is ours to change. No lock, no yield, and the
      // program needs no "receive" call.
      while (outbox.TryDequeue(out string message))
      {
        lua_getfield(L, LUA_REGISTRYINDEX, new HostString("inbox"));
        lua_pushstring(L, new HostString(message));
        lua_rawseti(L, -2, tail++);
        lua_pop(L, 1);
        Console.WriteLine($"C#: queued \"{message}\" at freeze {freezes}");
      }
      // Step 6: thaw. The program carries on exactly where it was frozen, and its next look at
      // the inbox finds what was just put there.
      result = lua_thaw(co, null);
    }

    // The program returned (or failed): it's no longer frozen.
    if (result.status != LUA_OK)
      throw new InvalidOperationException("the program failed: " + lua_tostring(co, -1));
    Console.WriteLine($"done: {lua_tointegerx(co, -1).getAsLong()} spins, {freezes} freezes");
    producer.Join();
    lua_close(L);
  }
}
