---
title: Hard Disk
nav: Disk
section: Devices
order: 20
---

A hard disk keeps files. It comes in one of two formats: **hfs**, where the disk serves files itself,
or **raw**, where it is bytes you read and write by offset.

<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Type | `storage` |
| In `/dev` | `storage0`, `storage1`... in bus order |
| Address | A SATA slot path such as `sata:0` |
| Tiers | 1 (1 MiB), 2 (4 MiB), 3 (16 MiB) |
| Interrupt | Raised while a change report waits (`changes`) |
| Power | Tier 1 8 W, tier 2 5 W, tier 3 2 W |
| Info | `tier`, `capacity`, `mode` |

</div>

For the item and its sockets, see [Hard disk](/hardware/disk/). To format, wipe or clone one, see
[Disk Station](/devices/disk-station/).

## Formats

- **hfs** serves files. They are real files in the world save, in a folder named for the disk.
  Paths are the disk's own, from its root: `/notes/a.txt`. Nothing a program names can reach outside
  the disk, and `..` at the root stays at the root. Names a host could mistake for something else
  (a backslash, a colon, a NUL, a name ending in a space or a dot, and names like `con` or `nul`)
  are refused (`bad name`).
- **raw** is a block device: bytes read and written at any byte offset, from 0 to the disk's
  capacity. A raw image ends where its data does, and past it are zeros.

A disk in one format refuses the other format's methods. A file system's methods fail on a raw disk
(`raw disk: no filesystem`), and `read` and `write` by offset fail on an hfs disk
(`hfs disk: format it raw to read and write it by offset`).

Space counts each file's bytes plus 512 for each file and directory, so empty files are not free.
A full disk fails a write with `No space left on device`.

## In mcos

mcos boots from an hfs disk and mounts it at `/`. Every other hfs disk is mounted as it joins the
bus, by where it is: a disk in SATA slot 1 at `/mnt/sata/1`, a disk known only by its id at
`/mnt/id/<id>`. It is unmounted when it leaves (files open on it then fail with `EIO`). Programs use
the files there as any others (see [Filesystem](/mcos/filesystem/)). A raw disk is not mounted.

### /dev/storage0, /dev/storage1...

Each disk's file in `/dev` is the disk's bytes, as a Linux block device's are: from offset 0 to its
capacity, read, written and sought anywhere.

- **Read** gives the bytes where the file is (at most 4096 at a time), and moves on. At the
  capacity it gives nothing (the end of the file). A raw disk never written reads as zeros.
- **Write** puts the bytes where the file is, at once (no buffering on the disk's side), and moves
  on. At the capacity it fails with `ENOSPC`.
- **Seek** works from the start, from where it is, and from the end (the capacity).
- An **hfs disk's** file refuses reads and writes with `EMEDIUMTYPE`: its filesystem owns its bytes.
  It can still be opened, for its ioctls.
- A disk that is only read (a Disk Station's source) refuses writes with `EROFS`.

For `sys.poll`, a disk's file is always ready to read and to write, and has `PRI` while the disk has
news (its interrupt came since the file was last read or asked).

```sh
lsdev                   # every device, with its /dev name
label storage1 backups  # a label on the disk's item
df                      # each mounted disk's size and use
```

### Its ioctls are its methods

The file's ioctls are the disk's methods below, by name, with their arguments, in any open mode,
on either format, mounted or not. A method that answers nothing gives `true`; one that fails gives
`nil`, its message and `EINVAL`; a disk that is gone gives `ENODEV`.

<!-- lua-fragment -->
```lua
local disk = io.open("/dev/storage1")
local free = sys.ioctl(sys.fileno(disk), "getCapacity") - sys.ioctl(sys.fileno(disk), "getUsed")
```

Paths given to `list`, `stat` and the others are the disk's own, from its root, whatever it is
mounted as. `setLabel` (the kernel's, on every device file) puts a label on the disk's item.

Some methods are for code without an OS, such as an EEPROM's, and are not for programs under mcos:
`open` gives a handle that only the firmware can use (under mcos, open files through the
filesystem), and `changes` is taken by the kernel, which turns a mounted disk's reports into
[inotify](/mcos/sys-api/) notices. Through an ioctl, `stat` of a path with nothing there answers
`true` (the method answered nothing), and so does `read` at the end of a raw disk.

## Methods, both formats

### getCapacity() → bytes

How many bytes the disk holds.

### getUsed() → bytes

Bytes in use. On hfs: files and their entries, and what open files will write at close. On raw: the
whole capacity.

### getMode() → "hfs" or "raw"

The disk's format.

### setMode(mode) → nothing

Formats the disk as `"hfs"` (an empty filesystem) or `"raw"`. Everything on it is lost and open
files close. Any other mode fails with `unknown mode`; a read-only disk fails with `read-only disk`.

### changes() → event or nil

The oldest change on the disk that has not been taken yet. See Events.

## Methods, hfs

Every one fails on a raw disk. Writes fail with `read-only disk` on a read-only disk (a
[Disk Station](/devices/disk-station/)'s source disk).

### open(path[, mode]) → handle

Opens a file. `mode` is `r` (the default), `r+`, `w`, `w+`, `a` or `a+`, as C's `fopen` (a `b` is
ignored). A new file is made at once, empty. At most 16 files are open at once on a disk
(`too many open files`). Fails with `no such file: path`, `is a directory: path` or `bad mode`.

The handle has its own methods, below.

### list(path[, typed]) → names

The names in a directory, sorted; directories end in `/`. With `typed` true, a table of
`{name, type}` each, where type is `file`, `directory` or `link`. `path` defaults to `/`. Fails
with `no such file` when it is not a directory.

### stat(path[, nofollow]) → {size, directory, modified} or nil

`modified` is the world's time when the file last changed, in milliseconds (a game tick is 50). Nil
if there is nothing there. With `nofollow`, a symbolic link is described itself, with `link` set to
its target (and `size` its length).

### mkdir(path) → nothing

Makes a directory. Its parent must exist. Fails with `already exists: path`.

### remove(path) → nothing

Removes a file, a link (not what it points to), or an empty directory (`directory not empty`). The
root cannot be removed.

### rename(from, to) → nothing

Moves a file or directory. `to` must not exist (`already exists`), and a directory cannot move into
itself.

### symlink(target, path) → nothing

Makes a symbolic link at `path`. The target is any text (the operating system follows it, the disk
never does), at most 1023 bytes (`name too long`). `path` must not exist. A link is kept as a CIFS
`XSym` file of 1067 bytes.

### readlink(path) → target

A link's target. Fails with `not a link`.

A path that meets a link fails with the message `symbolic link`, a NUL, the link's path, a NUL and
its target: the operating system goes on from there.

## Methods, raw

Every one fails on an hfs disk.

### read(offset, n) → string or nil

Up to `n` bytes (at most 4096) from the byte offset, fewer at the end. Nil at the end. An offset
past the capacity fails with `no such offset`, a negative `n` with `a negative count`.

### write(offset, data) → count

Writes the bytes at the offset, as many as fit before the end, and gives how many it wrote. Fails
with `read-only disk` on a read-only disk.

## File handles

A handle never holds a host file open. It reads through a 4096 byte window, and buffers its writes.
`close` commits them all at once: the file is all old or all new. Power lost, or the disk taken out,
before `close` loses the writes. A handle left unclosed and forgotten is abandoned the same way.
Writes reserve space as they go and fail with `No space left on device` when there is none.

### handle.read(n) → string or nil

Up to `n` bytes (at most 4096), nil at the end. Fails with `not open for reading`. A reader at the
end looks again for data another handle has added since.

### handle.write(data) → nothing

Writes where the file is at (at the end, if opened to append). Takes effect at close. Fails with
`not open for writing`, or `Cannot allocate memory` when the program's memory cannot hold it.

### handle.seek([whence[, offset]]) → position

Moves to `offset` from `"set"` (the start), `"cur"` (the default) or `"end"`, and gives where it is.
Fails with `bad whence` or `can't seek before the start`.

### handle.close() → nothing

Commits what was written and closes. On failure the handle stays open with its writes kept.

## Events

A disk reports every change made on it, whoever made it (this computer, another one, a Disk
Station), as an event. The oldest is taken with `changes()`. At most 32 wait; when more come, the
oldest is dropped and the next event to arrive carries `lost`, the number dropped. Reports are kept
across a save, and dropped when no program runs.

| Event | Fields |
|---|---|
| `modify`, `create`, `remove` | `path` |
| `rename` | `path` (the new one), `from` |
| `format` | `path` (`/`), `mode` |
| `write` (raw) | `offset`, `length` |

The disk's interrupt line is up while any wait. Under mcos, the kernel takes a mounted disk's
reports and gives them to programs as inotify notices, in the paths it is mounted at
(`MODIFY /mnt/sata/1/log`, a rename as `MOVED_FROM` and `MOVED_TO`, a format as `OVERFLOW`).

## Examples

### Back up a directory onto another disk

Copies `/home` onto the disk in SATA slot 1, keeping the last three copies, after asking the disk
through its ioctls whether the copy fits.

```lua
{% include disk-backup.lua %}
```
[Download disk-backup.lua](/examples/disk-backup.lua)

### Keep a log on a raw disk

A raw disk has no files: this program keeps its own format on the disk's bytes, through its `/dev`
file, read and sought by offset.

```lua
{% include disk-raw-log.lua %}
```
[Download disk-raw-log.lua](/examples/disk-raw-log.lua)

```sh
dmesg | lua disk-raw-log.lua add
lua disk-raw-log.lua show
```

### Watch a disk for changes

Logs every change made on a mounted disk, by anyone, as it happens.

```lua
{% include disk-watch.lua %}
```
[Download disk-watch.lua](/examples/disk-watch.lua)

### Survey every disk

Asks each disk on the bus for its format, its use and what is at its root, through ioctls on its
`/dev` file, whether mcos mounted it or not.

```lua
{% include disk-survey.lua %}
```
[Download disk-survey.lua](/examples/disk-survey.lua)
