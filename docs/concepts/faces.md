---
title: Faces
section: Concepts
order: 20
---

Some devices work on a side of a block: a [redstone card](/devices/redstone-card/) reads and
sets the signal on a face, a [robotic piston](/devices/robotic-piston/) points one way. Every
such device names a side the same way.

## The names

A face is a string, only. There are two kinds of name.

**The world's names** are Minecraft's own: `north`, `south`, `east`, `west`, `up` and `down`.

**Names relative to the block's front** are `front`, `back`, `left` and `right`. Left and right are
the block's own, as if it stood facing out of its front: a block facing north has west on its left.
Standing in front of it, facing it, its left is on your right. A robot names its sides the same way,
so a case and a robot facing the same way agree (as OpenComputers' sides do).

A program written with relative names works however the block is turned, and two programs written
for the same block compose. A program written with the world's names always means the same side of
the world.

Devices always answer with the world's names, so a program that reads a face back sees `north`,
never `front`.

## Which blocks have a front

Every block that holds devices has a front with its own look, facing the player who placed it: a
case's front, a hub's card bays. A [robotic piston](/hardware/robotic-piston/)'s front is opposite
its cable port. The block tells its devices where it is.

A device with no block to say where its front is takes only the world's names. Asking such a device
for `front` fails with a message saying so. `left` and `right` also fail on a block whose front is
`up` or `down`, because they would mean nothing.

## Example

A redstone card in a case whose front faces north. In mcos, its `/dev` file takes a line a face
(see [/dev](/mcos/dev/)):

```
$ echo "front 15" > /dev/redstone0
$ cat /dev/redstone0
north 15 15
...
```

The first line puts a signal of 15 out of the case's front. The card answers with the world's name:
that face is `north`. Each line is `face level powered`: the signal now on that face, and how much of
it this card puts out. The other faces follow, one a line.
