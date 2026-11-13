---
title: How data reaches a running program
nav: The host's inbox
section: Your own OS
order: 5
---

A program on a Modular Computers computer can sit in a busy loop that never yields and never calls
the host, and still find a device's data waiting for it the next time it looks. This page shows how
that works, with nothing of the mod in the way: one Java file, using only LungoLua, the Lua engine
the mod runs on. It's for anyone writing a host of their own, or a kernel who wants to know what's
under the firmware.

## The idea

Data comes from somewhere else, on another thread: a sensor, a socket, a timer. A Lua state belongs
to one thread at a time, so that thread can't touch the program. The usual answers are a callback
the program must call ("anything for me?") or a coroutine that yields to the host. Both put the work
on the program.

LungoLua has another one: a **budget**. The host gives the program a number of VM steps, and when
they're spent, a callback runs that **freezes** the program where it is, however deep it is in its
own calls and coroutines. Nothing in the state runs while it's frozen, so the host can change any
table the program reads, without a lock. Then it **thaws** the program, which carries on exactly
where it was, and its next look at the table finds the data. The program never knows: it can't see a
freeze, and `pcall` can't catch one.

That's a scheduler's timer interrupt, and a mailbox the hardware fills between the program's
instructions.

## The whole program

```java
{% include InboxExample.java %}
```

Run it with only the LungoLua jar on the class path:

```
javac -cp lungolua.jar InboxExample.java
java -cp lungolua.jar:. InboxExample
```

You'll see each message queued by the host at a freeze, then read by the program:

```
java: queued "reading 1" at freeze 644
  lua:  read "reading 1" after 1287999 spins
...
done: 5415991 spins, 2708 freezes
```

The numbers change from run to run.

## Step by step

1. **A state.** `luaL_newstate` and `luaL_openlibs`, as any host does (the libraries only for
   `print`).
2. **A thread for the program.** `lua_newthread`, with the program loaded on it as a function. The
   program is an ordinary loop: it looks at its `inbox` table, takes what's there (clearing each slot
   it reads, so the table never grows), and spins. The table is its argument; the host keeps the same
   table in the registry, so it can find it again.
3. **The budget.** `lua_setbudget(co, STEPS, FREEZE)`. Every `STEPS` VM steps the callback runs, and
   `throw lua_freeze(spender)` freezes the whole running chain. From now on the program can't run
   longer than that without the host getting control back.
4. **A producer on another thread.** It never touches the Lua state. It puts its data in a Java
   queue that's safe across threads (`ConcurrentLinkedQueue`).
5. **The host's loop.** `lua_resume` starts the program. Each time it comes back with `LUA_OK` and
   `lua_isfrozen(co)` true, the program is frozen: the host moves what's in the queue into the table
   (`lua_rawseti`, at the host's own count of the next free slot).
6. **Thaw.** `lua_thaw(co, null)` and the program goes on. When it returns for real, `lua_isfrozen`
   is false and its results are on its stack.

## What the output shows

A slice is 10,000 steps, about 2,000 times round the loop. In the run above, the first message was
queued at freeze 644, which is 1,288,000 spins, and the program read it after 1,287,999: on its very
first look after the thaw. Nothing was polled, and nothing was called.

## In Modular Computers

The computer's firmware works this way. While the program is frozen between slices, the firmware
moves the devices' data into tables the program's reads look at, so its next read takes it from
there, without a call to the host. Your OS sees none of this: it calls `board.invoke` and `board.waitForInterrupt`,
and the data is already there.

What the example leaves out:

- **Saving a running program.** To save a state that has a budget, the callback must be known by
  name (`lua_registerbudget`), so a saved state can find it again.
- **A budget set from Lua.** The firmware sets its budget with `debug.setbudget` from Lua, on the
  program's thread, rather than from Java as here. Either works.
