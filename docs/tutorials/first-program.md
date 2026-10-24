---
title: Your first program
nav: First program
section: Tutorials
order: 2
---

In this tutorial you write a Lua program with mcos's editor, run it, and learn the shell session
you do it in. mcos is built to behave like Linux, so what you learn here carries over to the real
thing.

## What you need

- [ ] A running computer at its shell prompt, from [Your first computer](/tutorials/first-computer/)
- [ ] Its screen open, so keys you type reach it

## Steps

### 1. Look around

At the prompt, type each command and press Enter:

```
pwd
ls -a
ls /
```

You will see your working directory (`/home`), the hidden file `.shrc` in it, and then the top of the
filesystem. Your home, `/home`, is yours: updating mcos never writes over it. See
[pwd and cd in the shell](/mcos/shell/), [ls](/programs/ls/) and the [filesystem](/mcos/filesystem/).

Every command has a manual. Try:

```
man ls
```

Press `q` to leave the page. ([man](/programs/man/) pages the text with [less](/programs/less/).)

### 2. Write a program

Open the editor on a new file:

```
edit hello.lua
```

You will see a title bar with the file's name, an empty text area, and two lines of shortcuts at the
bottom. The status line says `[ New File ]`. Type this, pressing Enter at the end of each line:

```lua
local name = ...
print("Hello, " .. (name or "world") .. "!")
for i = 1, 3 do
  print(i, i * i)
end
```

Now save and quit:

1. Press `Ctrl+O`. The status line asks for a name and offers `hello.lua`. Press Enter.
2. Press `Ctrl+X` to exit.

If you press `Ctrl+X` with unsaved changes, the editor asks whether to save: `Y` saves, `N` throws the
changes away, `Ctrl+C` goes back. The editor's [page](/programs/edit/) lists every key (arrows,
`Ctrl+K` to cut a line, `Ctrl+U` to paste, `Ctrl+W` to search).

You will see the prompt return. Check the file is there:

```
ls
cat hello.lua
```

### 3. Run it

Every mcos program is Lua, and [lua](/programs/lua/) is Lua's own interpreter:

```
lua hello.lua
lua hello.lua Steve
```

You will see:

```
Hello, world!
1	1
2	2
3	4
```

and then the same again, starting `Hello, Steve!`. Everything after the file's name arrives in the
program as `...`, which is how `name` got its value.

### 4. When it goes wrong

Open the file again with `edit hello.lua`, delete the `do` on the `for` line, save, and run it. Lua
refuses to run it and prints a line starting `lua:` that names the file and line, and a traceback.
Fix the line and run it again. Errors always say where they are; read the first line.

### 5. Try Lua interactively

Run `lua` with nothing after it. You will see a `>` prompt where each line you type is a Lua
expression or statement:

```
> 2^10
1024.0
> #"modular"
7
```

`Ctrl+D` on an empty line leaves. You can also run a line without a file: `lua -e "print(1 + 1)"`.

### 6. Pipes, redirection and files

The shell works as `sh` does. Send a command's output to a file with `>`, add to it with `>>`, and
feed one command's output to another with `|`:

```
lua hello.lua Alex > greeting.txt
cat greeting.txt
ls | grep hello
```

You will see the greeting, then `hello.lua`. The shell remembers what you typed: the Up arrow brings
back earlier lines, and Tab completes command and file names. See the [shell](/mcos/shell/) and
[grep](/programs/grep/).

### 7. Shut down properly

Type `poweroff` ([page](/programs/poweroff/)). mcos closes its files and turns the computer off, so
what you saved is on the disk. Switching the power off with the button does the same; the disk
writes a file when it is closed, so a program that never closes its file loses what it wrote if the
power is cut.

## Congratulations

You wrote and ran a program. Your file lives on the disk like any other, as a real file in the
world's save. Next, [make your computer touch the world with redstone](/tutorials/redstone/).
