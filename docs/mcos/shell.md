---
title: The shell
section: mcos
order: 30
---

`sh` is mcos's command interpreter, after POSIX `sh`. It reads commands, expands their words and
runs them. Only shell behavior lives in it: commands are programs in `/bin`, found through `PATH`,
or named by a path, and the builtins are only what has to change the shell itself. The full
reference is the [sh](/programs/sh/) page; this one is the tour.

## The prompt

An interactive shell shows `PS1`, by default `\w \$ `: the working directory (with `~` for `HOME`
unless `HOME` is `/`) and a `$`. The console's shell is a login shell (`sh -l`), which runs
`/etc/profile` and then `/home/.shrc`. The profile sets `PATH`, `HOME`, `ENV` and three aliases:
`ll` (`ls -alF`), `la` (`ls -a`) and `l` (`ls -CF`). Put your own aliases and variables in
`/home/.shrc`.

## Commands

```
$ ls /bin | grep s > names
$ echo "$HOME" ${USER:-nobody}
/home nobody
$ n=$(ls | head -n 1); echo $n
$ cat *.lua 2> /dev/null || echo none
$ sleep 5 &
```

The shell has words with quotes and backslashes, `$` expansions (`$NAME`, `${NAME:-word}`, `$?`,
`$$`, `$!`, `$#`, `$@`), command substitution (`$(...)` and backticks), field splitting on `IFS`,
and globs (`*`, `?`, `[...]`). Commands are joined with `|`, `&&`, `||`, `;`, a new line and `&`
(in the background). Redirections are `<`, `>`, `>>`, `n>&m` and `n>&-`.

The builtins are `cd`, `pwd`, `exit`, `export`, `unset`, `set`, `.`, `source`, `alias`, `unalias`,
`read`, `type`, `command`, `true`, `false` and `:`.

What isn't there yet: `if`, `for`, `while`, `case`, functions, here-documents and job control. A
line is simple commands joined by the operators above. For logic, write the program in Lua: every
mcos program is Lua.

## Running a program

A word that names a file with a `/` is a path; any other is looked up in the directories of `PATH`.
A Lua file runs directly, so a program you write in `/home/hello.lua` runs as `/home/hello.lua`
(or `lua /home/hello.lua`). A first line starting with `#!` is skipped. A command's exit status is
`$?`: 127 for not found, 126 for found but not runnable, and 128 plus the signal's number when a
signal ended it (130 for Ctrl+C).

## Line editing

At a terminal the shell reads its lines with readline, a library that works as GNU readline does
and with bash's usual bindings:

| Key | Does |
|---|---|
| Left, Right | Move a character |
| Ctrl+Left, Ctrl+Right | Move a word |
| Home, Ctrl+A / End, Ctrl+E | Start or end of the line |
| Backspace, Delete | Erase |
| Ctrl+U, Ctrl+K | Cut before or after the cursor |
| Ctrl+W | Cut the word before the cursor |
| Up, Down | Go through the history (the last 100 lines) |
| Tab | Complete a command, a file name |
| Ctrl+L | Clear the screen |
| Ctrl+C | Drop the line, prompt again |
| Ctrl+D | On an empty line, end of input: the shell exits |

Tab completes the first word of a command to builtins, aliases and programs in `PATH`, and any
other word to file names (directories end in `/`). One match is put in, followed by a space; several
put in what they all start with, and a second Tab lists them. A line can be edited anywhere,
across the rows it wraps onto. Keys typed ahead while a command starts are handed to that command,
and echoed where the cursor is, as bash does.

While a command runs, Ctrl+C ends it (the shell carries on).

## The tty

The terminal line is the kernel's, a line discipline like Linux's with its termios flags: `icanon`,
`echo`, `echoe`, `echoctl`, `isig`, `icrnl`, `onlcr` and `iutf8`. [`stty`](/programs/stty/) shows
and sets them (`stty raw`, `stty sane`, `stty size`). A program that wants every key turns the line
raw, as the editor `edit` and readline do while you type.

## Learning more

[`man`](/programs/man/) shows a manual page; every program has one, and they are the same text as
the [Programs](/programs/) pages. `lsdev` lists devices, `ps` processes, `dmesg` the kernel's log,
`free` memory and `uptime` the time since boot.
