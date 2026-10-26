---
title: Concepts
section: Concepts
order: 0
---

The ideas behind Modular Computers. You can build and program a computer without reading these
pages, but each one explains something you will meet sooner or later, and why the mod works that way.

Modular Computers is built on how real computers and real Linux work. Where it copies them, these
pages say so; where it takes a shortcut, they say that too.

- [The bus](/concepts/bus/): how a computer sees its parts, cards and cabled blocks, and why a
  device can be on only one computer at a time.
- [Faces](/concepts/faces/): how a program names a side of a block, by the world's names or by the
  block's front.
- [Power](/concepts/power/): what draws power, what stores it, and how watts are shown.
- [Interrupts](/concepts/interrupts/): how a computer waits for a key, a frame or a sound without
  checking again and again.
- [Three ways to reach a device](/concepts/device-layers/): the board, mcos's `sys`, and the
  `devices` library, each built on the one below, and which to use.
- [Names](/concepts/names/): why one device answers to a slot path, an id and sometimes a virtual name.
- [Persistence](/concepts/persistence/): how a running computer survives a save, a restart and an
  unloaded chunk, and what a budget means.
