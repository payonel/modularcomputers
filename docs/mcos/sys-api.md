---
title: The sys library
section: mcos
order: 70
---

`sys` is mcos's system call library, the POSIX layer a Lua program uses to reach the kernel. It plays
the role that a "Lua API" does in other mods. It is the only way to reach the hardware from a program:
a program never touches the computer's devices, only the kernel does.

Get it with `require`:

```lua
local sys = require("sys")
```

## Conventions

- **Errors are Lua's convention**, as `io.open`'s are: a call that fails returns `nil`, a message (what
  `strerror` would say) and the errno's name, as `ENOENT`.
  ```lua
  local fd, why, errno = sys.open("/nope")
  -- nil   No such file or directory   ENOENT
  ```
- **A call may wait.** A read with nothing typed, a write to a full pipe, `accept` with no connection,
  `waitpid` for a child that is still running: the process is blocked until the call can finish, and
  other processes run meanwhile. There is nothing to yield; the kernel stops and resumes the process
  (see [Persistence](/concepts/persistence/)).
- **Files are descriptors.** `open` returns a small integer, `0`, `1` and `2` are standard input,
  output and error. A process may hold 64.
- **Paths** are relative to the process's working directory, or absolute.
- `sys` makes each wrapper the first time a program uses it, so a call you never make costs nothing.

`sys.SIGNALS` is a table of signal numbers: `SIGHUP` 1, `SIGINT` 2, `SIGKILL` 9, `SIGPIPE` 13,
`SIGTERM` 15, `SIGCHLD` 17, `SIGWINCH` 28.

Most programs use `io` (Lua's, over these calls) for files and `sys` for everything else; the page
for [the shell](/mcos/shell/) and the [programs](/programs/) are examples. `sys.fileno(file)` gives
an `io` file's descriptor, for `sys.poll`.

## Files

### sys.open(path[, mode]) → fd
Opens a file. `mode` is `fopen`'s: `"r"` (the default), `"w"`, `"a"`, `"r+"`, `"w+"`, `"a+"`; `b` is
ignored. A directory can be opened only to read, then `getdents` lists it. A file opened only to read
has no write; only to write, no read: the other call fails with `EBADF`.

### sys.close(fd) → true
Closes a descriptor. A file's writes reach the disk when the last descriptor to it closes.

### sys.read(fd[, n]) → data
Reads up to `n` bytes (4096 by default; at most 4096 for a file), what's there now, like `read(2)`.
An empty string is the end of the file. On a terminal, in canonical mode, it waits for a line.

### sys.write(fd, data) → count
Writes all of `data` (a string or a number), waiting for room as long as it takes. Returns how many
bytes were written. A write to a pipe nobody reads fails with `EPIPE` and sends `SIGPIPE`.

### sys.lseek(fd, offset, whence) → position
Moves in a file. `whence` is `"set"` (the default), `"cur"` or `"end"`. Pipes and terminals fail with
`ESPIPE`.

### sys.dup(fd[, from]) → fd
A copy of a descriptor: the lowest free one, at or above `from` if given.

### sys.getdents(fd) → entries
The entries of an open directory, `{name, type}` each, where type is `"file"`, `"directory"`,
`"link"`, `"device"` or `"fifo"`. Once.

### sys.pipe() → readfd, writefd
A pipe. It holds 4096 bytes: a writer waits for room, a reader for data. The read end reads the end
of file once every write end is closed.

### sys.isatty(fd) → boolean
Whether the descriptor is a terminal.

### sys.fileno(file) → fd
An `io` file's descriptor. Not a kernel call; `ld.lua` adds it. What the file has already read into
its buffer, `poll` does not see (as with C's stdio).

## Paths

All take paths. They return `nil`, a message and an errno when they fail.

### sys.stat(path) → table
`{type, size, modified, path, mount, target}`. `type` is `"file"`, `"directory"`, `"link"`, `"device"`
or `"fifo"`. `path` is where it is with links followed, `mount` its mount's path. `modified` is, on a
disk, the world's time when it last changed, in milliseconds (a tick is 50); on `/tmp`, 0.

### sys.lstat(path) → table
As `stat`, but a link itself: `type` is `"link"` and `target` is where it leads.

### sys.readlink(path) → target
A link's target.

### sys.realpath(path) → path
The path with every link followed. It must exist.

### sys.mkdir(path), sys.rmdir(path), sys.unlink(path) → true
Make a directory, remove an empty directory, remove a file or a link (never what it points to).

### sys.rename(from, to), sys.link(from, to) → true
Move a name (a link moves as itself); make a hard link. Hard links are not supported by any filesystem:
`link` fails with `EPERM` once the names are good.

### sys.symlink(target, path) → true
A symbolic link at `path`. The target is kept as given, and need not exist. A relative target is
followed from the link's directory.

### sys.chdir(path) → true, sys.getcwd() → path
Change and read the working directory.

### sys.statfs(path) → table
`{type, size, used}` of the filesystem holding the path: `"hfs"`, `"devfs"` or `"tmpfs"`, and its
size and use in bytes.

### sys.mount(source, target[, fstype]) → true
Mounts `source` on `target`: a disk by its bus address (`sata:1`, or `id:<uuid>`; it must be an hfs
disk), `"tmpfs"` for a new filesystem in memory, or `"devfs"`. Fails with `EBUSY` if something is
mounted there, `ENODEV` for a non-storage device, `EINVAL` for a raw disk.

### sys.umount(path) → true
Unmounts. `EBUSY` while files are open on it.

### sys.mounts() → list
What is mounted, `{source, path, type}` each, sorted by path.

### sys.image(path) → function
A Lua file as a function, its text compiled once and shared by every process that runs it, as a page
cache shares a program's text. Called with a table for globals and its arguments. A file that doesn't
compile fails with `ENOEXEC`. This is how `loadfile` and `require` work.

## Processes

### sys.spawn(path, argv, opts) → pid
Starts a process (`posix_spawn`) running `path`, a Lua file, with `argv` as its arguments (a list
of strings), as the caller's child. `opts` may hold:

| Option | Meaning |
|---|---|
| `fds` | The child's descriptors: `{[0] = a parent fd, ...}`. 0, 1 and 2 are inherited when not given; others aren't (close-on-exec). A value of `false` starts it closed. |
| `env` | The child's environment, a table; the caller's if not given. |
| `ignore` | A list of signals it starts ignoring, like `{"SIGINT"}`. |
| `pgid` | `0`: a process group of its own. |
| `setsid` | A session of its own, a group of its own. Its controlling terminal is its standard input's, if that is a terminal with no session; else it has none, so no terminal's signals reach it (a daemon). |

An ignored signal stays ignored in the child; handlers don't carry over. Fails with `ENOENT` for a missing
file, `EACCES` for a directory, `EBADF` for a bad descriptor in `fds`.

### sys.waitpid(pid[, "nohang"]) → pid, how, value
Waits for a child that ended (`-1` for any), and reaps it. `how` is `"exited"` (`value` its
status) or `"killed"` (`value` the signal). With `"nohang"`, `0` if none has ended. `ECHILD` when
there is nothing to wait for.

### sys.exit([code])
Ends the process. `os.exit` calls it.

### sys.getpid() → pid
### sys.kill(pid, signal) → true
Sends a signal (`"SIGTERM"` by default). A negative `pid` is a process group. `ESRCH` for no such process.

### sys.signal(name, disposition) → old
What the process does with a signal: a function (called in the process, with the signal's name, when
its next system call returns; a blocked call returns `EINTR`), `"ignore"` or `"default"`. Returns the old
one. `SIGKILL` can't be caught. By default a signal ends the process, except `SIGCHLD` and
`SIGWINCH`, which are ignored.

### sys.environ() → table
The environment the process started with, a copy.

### sys.processes() → list
Like `/proc`: `{pid, ppid, pgid, path, argv, state}` each, `state` being `"running"`, `"sleeping"` or `"zombie"`.

### sys.sleep(seconds) → true
Waits, in game time.

## Waiting for several things

### sys.poll(fds, seconds) → ready
Like `poll(2)`. `fds` maps each descriptor to the events wanted, a set: `{IN = true, PRI = true,
OUT = true}`. `IN` means a read won't wait, `OUT` that a write won't, `PRI` that something exceptional
happened (a device's state changed). `HUP` and `ERR` are reported whether asked or not. Returns a table
mapping each descriptor with something to report to the events that are true. With `seconds` it waits
that long; without, as long as it takes; if time ran out with nothing ready, the table is empty.

```lua
local ready = sys.poll({[sock] = {IN = true}, [0] = {IN = true}}, 1)
if ready[sock] then print(sys.recvfrom(sock)) end
```

A tty is ready to read when a line is; a pipe when it has data; a device file when an event waits;
a disk's file is always ready. Any device's file has `PRI` while its device has news (its interrupt
came since the file was last read or asked), so a program can wait for a device before asking it.

### sys.inotify() → fd, sys.watch(fd, path)
`inotify` makes an open file whose reads are file changes, a line each: `CREATE`, `MODIFY`, `DELETE`,
`MOVED_FROM`, `MOVED_TO`, each followed by the path (`MODIFY /log`), and `OVERFLOW` with no path
(notices were dropped, or a disk was formatted: look again). `watch` adds a path; watching a directory
reports its entries too. Poll it as any file.

```lua
local fd = sys.inotify()
sys.watch(fd, "/home")
print(sys.read(fd))   -- waits: e.g. "CREATE /home/new.txt"
```

## Terminals

### sys.openpty() → master, terminal
A pseudo-terminal, as `openpty(3)`. What the master writes is typed on the terminal end (through the line
discipline: echo, editing, Ctrl+C); what the terminal shows is what the master reads. Closing the master
hangs the line up (SIGHUP, then end of file and `EIO`).

### sys.ioctl(fd, op[, arg])
A terminal's ioctls, by Linux's names:

| op | Does |
|---|---|
| `TCGETS` | The termios flags, a table of booleans. |
| `TCSETS` | Sets them from a table (`icanon`, `echo`, `echoe`, `echoctl`, `isig`, `icrnl`, `onlcr`, `iutf8`). |
| `TIOCGWINSZ` | Returns columns, rows. |
| `TIOCSWINSZ` | A pty's size, `{columns, rows}`; sends `SIGWINCH` to the program in front. |
| `TIOCSTI` | Puts bytes into the input as if typed. |

A socket's ioctls are described in [Networking](/mcos/networking/). A device file's are its
device's methods, by name, with their arguments (`sys.ioctl(fd, "setStrength", 16)`), and a sound
card's OSS names too: see [/dev](/mcos/dev/). Anything else fails with `ENOTTY` or `EINVAL`.

## Sockets

See [Networking](/mcos/networking/) for how they behave.

### sys.socket([kind]) → fd
`"dgram"` (UDP, the default) or `"stream"` (TCP).
### sys.bind(fd, port) → port
### sys.sendto(fd, data, address, port) → count
### sys.recvfrom(fd) → data, address, port
### sys.listen(fd[, backlog]) → true
### sys.accept(fd) → fd, address, port
### sys.connect(fd, address, port) → true
### sys.shutdown(fd) → true

## The computer

### sys.devices() → list
The devices on the [bus](/concepts/bus/) as the kernel knows them, in bus order: `{name, id, path, type,
label, get, set}` each. `name` is `storage0`, `path` the slot (nil if not in one), `get` and `set` whether
it has data to read and write. The kernel's list can be a moment behind a hot-plug.

A device's label is set with its file's `setLabel` ioctl (`sys.ioctl(fd, "setLabel", "backups")`;
`""` or `nil` clears it), kept on the device's item. `EINVAL` for one that can't be (more than 32
characters, or not printable), `EPERM` for a device with no item to keep it on.

### sys.gethostname() → name, sys.sethostname(name) → true
The host's name. Until it is set, the first network card's address, or `localhost` with none. A name is
letters, digits, `-` and `.`, at most 64; `""` unsets it.

### sys.time() → seconds
The real-time clock, the world's game time, in seconds since the world's epoch.

### sys.uptime() → seconds
Since boot.

### sys.sysinfo() → table
`{totalram, usedram, freeram, uptime, procs}`, memory in bytes.

### sys.klog(after) → list
The kernel's log (the last 200 entries) since the entry numbered `after`, oldest first: `{seq, time,
source, id, text}` each. `"clear"` empties it. `dmesg` reads it.

### sys.reboot(how)
`"halt"`, `"poweroff"` or `"restart"`: every process ends (its files closed, so the disks get what was
written), then the CPU halts, the computer turns off, or it restarts.

## Libraries

These are ordinary Lua libraries in `/lib`, loaded with `require`.

### fs
Conveniences for files, over `sys`. Failed calls return `nil`, a message naming the path, and the errno.
`fs.join(...)`, `fs.name(path)`, `fs.stat`, `fs.lstat`, `fs.readlink`, `fs.realpath`, `fs.symlink(target,
path)`, `fs.exists(path)`, `fs.isdir(path)`, `fs.list(path)` (entries `{name, type}`, sorted),
`fs.command(name[, path])` (where a command is, as `execvp` looks it up), `fs.mkdir(path[, parents])`,
`fs.remove(path[, recursive])`, `fs.copy(from, to[, recursive])`, `fs.move(from, to)` (across
filesystems by copying), `fs.pattern(glob)` and `fs.glob(glob)` (the paths a shell pattern matches).

### serialization
Lua values as text, and back. `serialization.serialize(value[, pretty])` gives Lua source for nil,
booleans, numbers, strings and tables of those (keys sorted); a function, a thread or a table met again
inside itself is an error, unless `pretty`, which is for reading. `serialization.unserialize(text)` gives
the value, or `nil` and why.

### http
`http.request(url, options)` makes a request through the internet card, following redirects when
`options.follow` (up to 20, each judged by the card again), and gives the response `{status, message,
headers, url}` and the body's descriptor, or `nil` and why. `http.resolve(base, location)` resolves a
`Location`.

### readline
Line editing, as GNU readline does it, for programs that read lines. `readline.new({complete =
function(before, word) ..., breaks = "[ \t]"})` makes an editor with its own history; `editor:read(prompt)`
reads a line, or gives `nil` and `"EINTR"` for Ctrl+C, or `nil` at the end of input. The terminal goes
raw while a line is typed. `lua` and `sh` both use it.

## The standard library

`ld.lua` sets up each program with Lua's base library, `coroutine`, `string`, `table`, `math` and
`utf8`, and its own `io`, `os`, `print`, `require`, `package`, `loadfile` and `dofile`. `debug` has only
`traceback`.

- `io` is C's stdio: a file buffers (a terminal's output by line), and `file:read(n)` waits until it has all
  `n` bytes or the end. A program that passes a stream on reads the descriptor instead.
- `io.popen(command, mode)` runs the command through `sh`.
- `os.time` and `os.date` use the world's calendar: days from 1, each day twenty-four game hours (1200
  real seconds), the epoch at the first day's sunrise, 06:00. `os.date`'s fields are `day`, `hour`,
  `min`, `sec` and `yday`; its formats are `%d %H %M %S %T %R %s %c %n %t %%`.
- `os.getenv(name)` and, not in Lua's `os`, `os.setenv(name, value)` for the environment children get.
- `package.path` is `/lib/?.lua;/lib/?/init.lua;/usr/lib/?.lua;./?.lua`.

A program that returns an integer exits with it. One that returns `false` or `nil` and a message prints
the message to standard error and exits 1. An error prints the message and a stack traceback to standard
error and exits 1.

## A complete example

A program that logs the weather to a file. It reads a weather sensor's file, which gives a line each time
the weather changes (see [/dev](/mcos/dev/)), and adds the time.

```lua
-- /home/weather.lua
local sys = require("sys")
local sensor = assert(io.open("/dev/weather0"))
local log = assert(io.open("/home/weather.log", "a"))
while true do
  local line = sensor:read("l")          -- waits for the next change
  if not line then break end
  log:write(os.date(), " ", line, "\n")
  log:flush()
end
```
