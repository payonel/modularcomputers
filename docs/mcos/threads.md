---
title: Threads
section: mcos
order: 76
---

A program can do several things at once with threads: wait on a device in one while another talks to
you, or run a timer beside its main loop. mcos's threads are the kernel's own, as Linux's are: each is
a task of the process, with its own place in the code, and they share the process's globals, files
and directory.

```lua
local thread = require("thread")
local sys = require("sys")

local ticker = thread.create(function(n)
  for i = 1, n do
    print("tick " .. i)
    sys.sleep(1)
  end
  return "done"
end, 3)

print("say something:")
print("you said " .. io.read())          -- (the ticker goes on meanwhile)
print(ticker:join())                     -- true   done
```

## How they behave

- **Each is preempted**, as a process is: a busy thread never keeps the others from running, and no
  thread has to yield for another.
- **Each blocks only itself.** A thread waiting in `io.read`, `sys.poll`, `sys.sleep` or a device's
  method leaves the others running.
- **They share** the process's globals, its open files (stdin, stdout, a device's file), its directory,
  environment and signal handlers.
- **Devices are files, so nothing is copied to each thread.** A device's events are its file's: a thread
  waiting on `/dev/robotics0` takes the events it reads, as any reader of a file does. Two threads
  reading one device's events share them, one each: give a device to one thread.
- **The process ends when its main thread does**, its other threads with it, wherever they are (as
  POSIX's return from `main` does). To let a thread finish, `join` it before returning. `os.exit` in any
  thread ends the whole process.
- **A signal's handler** runs in whichever thread comes back from a call first; a call the main thread
  is blocked in returns `EINTR`.
- **A thread's error** ends that thread only: it's said on the process's stderr, and its `join` gives
  `false` and the error.
- **Shared tables need a mutex** when more than one thread changes them: a thread can be stopped between
  any two instructions.

## Functions

### thread.create(f, ...) → thread, or nil and why

A new thread, running `f(...)`. What `f` returns is its `join`'s.

### t:join([seconds]) → true, results... | false, error

Waits for the thread to end: `true` and what it returned, or `false` and its error (`killed` when
cancelled). With `seconds`, `nil` and `ETIMEDOUT` if it hasn't ended by then. Once joined, it's gone.
Fails with `EDEADLK` joining itself.

### t:kill() → true

Ends the thread where it is (its mutexes let go). Its `join` gives `false, "killed"`.

### t:status() → "running", "suspended", "dead" or nil

`suspended`: waiting in a call (a read, a poll, a sleep, a lock). `dead`: done, not yet joined. `nil`:
joined, gone. (Lua's coroutine words, as OpenOS's threads used; `ps` keeps Linux's for processes.)

### thread.current() → tid

The calling thread's id. The main thread's is the process's pid.

### thread.mutex() → mutex

A mutex: one thread holds it at a time.

- `m:lock([seconds])` waits until no other thread holds it, and takes it (`EDEADLK` if this thread
  holds it already: it isn't recursive; `nil` and `ETIMEDOUT` after `seconds`).
- `m:unlock()` lets it go (`EPERM` unless this thread holds it).
- `m:run(f, ...)` runs `f(...)` holding it, and lets it go even if `f` fails.

A mutex matters when threads update the same thing in several steps, and a step can take long
enough for another thread to run. Here a house has two doors, far apart, each with an
[expansion hub](/hardware/expansion-hub/) holding a [motion sensor card](/devices/motion-sensor/), both
hubs cabled back to one computer: `motion0` at the front door, `motion1` at the back. A thread watches
each door, and both keep one tally of visitors. Each update reads a count, opens a file (a call to the disk: slow), and writes the count back. The
other thread keeps running meanwhile, so without the mutex it could slip its own update in between,
both reading `4` and both writing `5`, and a sighting would be lost. With the mutex the other thread
still runs, but waits at `lock:run` until the first update is done.

```lua
{% include threads-tally.lua %}
```

A single step on shared data with no call in it (`tally[name] = (tally[name] or 0) + 1`) needs no
mutex in practice; data each thread keeps to itself never does.

## Underneath

The library is a thin layer over the kernel's calls: `sys.thread(f, ...)` (a tid), `sys.join(tid[,
seconds])`, `sys.cancel(tid)`, `sys.gettid()`, `sys.threads()` (the process's, `{tid, state}` each),
`sys.mutex()`, `sys.lock(id[, seconds])` and `sys.unlock(id)`. `sys.processes()` counts each
process's `threads`. See [The sys library](/mcos/sys-api/).

## Coming from OpenOS

OpenOS's threads were coroutines it scheduled itself, each given a copy of every signal; they had to
yield to let the others run. mcos's are the kernel's, preempted, and wait on files instead of
signals. `t:suspend()` and `t:resume()` have no equivalent (use a mutex, or have the thread wait on
something).
