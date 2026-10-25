---
title: The filesystem
section: mcos
order: 20
---

mcos has one tree of paths, starting at `/`, as Linux does. Different filesystems are mounted at
different places in it.

## What is mounted

| Path | What | Kept |
|---|---|---|
| `/` | The boot disk | On the disk |
| `/dev` | The devices as files: see [/dev](/mcos/dev/) | The kernel's |
| `/sys` | What each device can do: a directory a device, a read-only file a method: see [/sys](/mcos/dev/#sys-what-a-device-can-do) | The kernel's |
| `/tmp` | A filesystem in the computer's memory | Until the computer is turned off |
| `/run` | Another one, for services' pids; made by `/etc/rc` | Until the computer is turned off |
| `/mnt/...` | Every other disk on the bus | On the disk |

The kernel mounts the other disks by itself as they come and go, as udisks does. A disk in
`sata:1` appears at `/mnt/sata/1`; a disk known by its id, `id:<uuid>`, appears at
`/mnt/id/<uuid>`. When a disk leaves the bus (its [Disk Station](/devices/disk-station/) is
disconnected, say), it is unmounted, and files that were open on it fail with an I/O error.

`mount` with no arguments lists the mounts; `mount sata:2 /mnt/backup` mounts a disk by hand, and
`mount tmpfs /scratch` makes a new filesystem in memory. A mount point need not exist. See
[mount](/programs/mount/), [umount](/programs/umount/) and [df](/programs/df/). There is no
`/etc/fstab` and there are no mount options.

Only a disk with a filesystem (an *hfs* disk) is mounted. A raw disk has no filesystem to mount; it
is a block of bytes, reached as a file in `/dev` (see [/dev](/mcos/dev/)).

## The layout

On a fresh disk:

| Path | Contents |
|---|---|
| `/bin` | The commands: see [Programs](/programs/) |
| `/lib` | The libraries and, in `/lib/modules`, the kernel's own parts |
| `/etc` | `profile`, `rc`, `rc.conf` and the services in `rc.d` |
| `/home` | Yours. An update of mcos never writes over it |
| `/usr/bin` | More programs |
| `/usr/share/man` | The manual pages that [`man`](/programs/man/) shows |

`PATH` is `/bin:/usr/bin`, and `HOME` is `/home`.

## Disks serve their own files

An hfs disk serves the files itself, a little like 9P does: the files are real files in the world's
save, in a folder per disk, so players and server admins can look at them. This has a few
consequences.

- A file you write is buffered and committed at close. If the power is lost before the file is
  closed, what you wrote is lost. A program that keeps a file open for a long time should flush or
  close it. A file on `/tmp` shows its writes at once.
- A file's time is the world's: game time, 50 milliseconds a tick, set when the file changes.
  mcos's dates count days from 1 in the world's calendar (twenty-four game hours a day), with no
  months.
- A disk's tier sets how much it holds. Writing past that fails with "No space left on device".
- Paths never leave the disk's folder.

## Links

Symbolic links work: [`ln -s`](/programs/ln/). The disk stores them as small files in CIFS's
Minshall+French format, never as host links, which could point out of the save. The disk never
follows one itself; mcos's virtual filesystem does, up to 40 in one lookup.

Hard links are not supported by any filesystem here, as with FAT: `ln` without `-s` fails with
"Operation not permitted".

## Errors

A failed call gives Lua's usual three values: nothing, a message and an errno name. Messages are
Linux's `strerror` text and names are Linux's: `ENOENT` ("No such file or directory"), `EACCES`,
`EEXIST`, `ENOTDIR`, `EISDIR`, `ENOTEMPTY`, `ENOSPC`, `EROFS`, `EMFILE` and so on. A disk's raw bytes in
`/dev` are refused on an hfs disk with `EMEDIUMTYPE` ("Wrong medium type").

## Watching for changes

`sys.inotify` and `sys.watch` give Linux's change notices: see [The sys library](/mcos/sys-api/).
They watch paths, not files, and a change made on a disk's host, outside any computer, isn't seen.

## Programs for files

[`ls`](/programs/ls/), [`cat`](/programs/cat/), [`cp`](/programs/cp/), [`mv`](/programs/mv/),
[`rm`](/programs/rm/), [`mkdir`](/programs/mkdir/), [`rmdir`](/programs/rmdir/),
[`touch`](/programs/touch/), [`find`](/programs/find/), [`tree`](/programs/tree/),
[`du`](/programs/du/), [`head`](/programs/head/), [`tail`](/programs/tail/),
[`less`](/programs/less/), [`grep`](/programs/grep/), [`edit`](/programs/edit/) (a text editor
after nano) and [`mktemp`](/programs/mktemp/).
