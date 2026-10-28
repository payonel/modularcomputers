---
title: Devices
section: Devices
order: 0
---

A device is anything on a computer's [bus](/concepts/bus/): a terminal, a disk, a card, a sensor, a
piston. This section is the programming reference for every one: what it is called, which methods
it has, which events it raises, and what its file in `/dev` does. For how a device is built,
crafted or placed, see the [Hardware](/hardware/) section.

## What every device has

Every device has several names, as devices do on a real computer (see [Names](/concepts/names/)):

- **A slot path**, fixed by where it is plugged in: `eeprom`, `sata:0`, `exp:1`. A cabled device has none.
- **An id**, written `id:<uuid>`, which every device has however it is connected. The id stays the
  same if you move a part to another slot.
- **A kernel name** in mcos, such as `redstone0`, `storage1` or `dsp`. It is the device's type
  followed by a number, counted in bus order. Run `lsdev -i` to see every device's name, type, slot
  path and id.

Every device also has a **type**, which the bus reports as a lower case word:

| Type | Device |
|---|---|
| `terminal` | [Terminal](/devices/terminal/) |
| `storage` | [Hard disk](/devices/disk/) |
| `disk_station` | [Disk Station](/devices/disk-station/) |
| `eeprom` | [EEPROM](/devices/eeprom/) |
| `redstone` | [Redstone card](/devices/redstone-card/) |
| `network` | [Network card](/devices/network-card/) and [WLAN cards](/devices/wlan-card/) |
| `bluetooth` | [Bluetooth card](/devices/bluetooth-card/) |
| `sound` | [Sound card](/devices/sound-card/) |
| `internet` | [Internet card](/devices/internet-card/) |
| `operator` | [Operator card](/devices/operator-card/) |
| `motion` | [Motion sensor](/devices/motion-sensor/) |
| `hearing` | [Audio sensor](/devices/audio-sensor/) |
| `weather` | [Weather sensor](/devices/weather-sensor/) |
| `actuator` | [Robotic piston](/devices/robotic-piston/) |
| `robotics` | [Robotics device](/devices/robotics/) |
| `charger` | [Charging pad](/devices/charger/) |
| `crafting` | [Crafting card](/devices/crafting-card/) |
| `field` | [Energy field card](/devices/energy-field-card/) |

Besides its type, a device says a little about itself (`board.info`, below): its label, and what its
kind adds, such as a disk's tier and capacity. Each page lists these.

## How a program reaches a device

There are three layers, each built on the one below: the board (code with no OS under it), mcos's
files (`sys`), and the `devices` library over them. Which you use depends on where your code runs.
[Three ways to reach a device](/concepts/device-layers/) compares them, and the tutorial
[One device, three ways](/tutorials/device-layers/) does one job at each.

### In mcos: files in /dev

Programs under [mcos](/mcos/) never call a device directly. The kernel owns every device and shows
every one as a file in [/dev](/mcos/dev/). You open a file, read it, write it, or poll it, as on
Linux, and its ioctls are the device's methods by name: `sys.ioctl(fd, "push", "front")` calls a
piston's `push`. Each device page has an *In mcos* section saying what its file does.

```sh
lsdev -i
cat /dev/redstone0
echo "north 15" > /dev/redstone0
```

A device whose data is a table (a redstone card, a sensor) has a driver that turns it into text: a
line a face, an event a line. A device with no data says what it is when read (its type, id and
methods), and is used through its ioctls. A network card's data goes through sockets
([Networking](/mcos/networking/)); its file is its traffic. Terminals are merged into the console
(`/dev/console`).

For everything else mcos offers (processes, sockets, polling), see the [sys API](/mcos/sys-api/).

### From the EEPROM: the board API

Code that runs straight from the [EEPROM](/devices/eeprom/), with no operating system, and the mcos
kernel itself, can use a global `board`. It is the motherboard's API:

| Call | Gives |
|---|---|
| `board.list()` | the devices on the bus, in bus order: a table each with `address`, `id` and `type` |
| `board.info(address)` | a table about one device: `address`, `id`, `type`, `label` (if it has one) and what its kind adds; nil if there is no such device |
| `board.methods(address)` | the names of the device's methods, in name order |
| `board.doc(address, method)` | one line saying what a method does |
| `board.invoke(address, method, ...)` | calls a method: `true` and its results, or `false` and a message (a busy device's refusal: `busy`, and the id of the device whose news ends the wait when it's another's) |
| `board.waitForInterrupt(seconds, sources...)` | waits until a source has an interrupt pending, or the time passes; gives the sources that do |
| `board.setLabel(address, label)` | gives a device a label (see below) |

The **address** is any of the device's names: its slot path, its `id:<uuid>`, or `console`, a virtual
device that writes to and reads from every terminal.

**A call never waits**: a method answers at once with what the device has now (a terminal's `read`
with no keys gives `nil`), and a slow action answers when it has started, its outcome coming later
as an event. To wait for a device, use `board.waitForInterrupt`, then call it. See
[Interrupts](/concepts/interrupts/#device-calls-never-wait). A device that acts over time (a robot, a
field card, a redstone card, a robotic piston) runs [one command at a
time](/concepts/interrupts/#one-command-at-a-time): given one while another runs, it answers
`false, "busy"`; its running command's end event says when to give it again.

A call never stops the machine. If a device is gone, or an argument is wrong, `board.invoke`
returns `false` and a message such as `bad argument #1 (string expected, got nil)`. This manual
writes a method's results as the values after the leading `true`, and its failures as the message.

```lua
local ok, size = board.invoke("sata:0", "getCapacity")
if ok then
  board.invoke("console", "write", "disk holds " .. size .. " bytes\r\n")
end
```

Values are Lua's: strings are bytes (one character a byte), numbers are integers or floats,
tables are sequences or maps.

## Faces

A device that takes a face takes it as a string. Either a world name (`north`, `south`, `east`,
`west`, `up`, `down`), or a name relative to the front of the block the device is in (`front`,
`back`, `left`, `right`, left and right as someone facing the front sees them). A program written
with relative names works however the block is turned. Devices answer with world names. See
[Faces](/concepts/faces/).

## Interrupts

A device with news raises an interrupt line, which wakes a computer waiting for it. Interrupts are
level-triggered: the line stays up while the device has something waiting, and drops when a program
has read it all. The data stays in the device until you take it. See
[Interrupts](/concepts/interrupts/).

A device takes data only while a program is running on the computer that holds it. Keys typed at a
computer that is off, or a sensor's events while it is off, are dropped.

## Labels

A device on an item can carry a label, which stays on the item. Programs set it with
`board.setLabel(address, label)` or, in mcos, with the `label` program. A label is at most 32
printable characters. A device with no item to keep it on cannot have one.

## Every device

| Page | What it is |
|---|---|
| [Terminal](/devices/terminal/) | A screen and keyboard in one: write escape sequences, read keys |
| [Hard disk](/devices/disk/) | Files on an hfs disk, or bytes by offset on a raw one |
| [Disk Station](/devices/disk-station/) | Formats, wipes, clones and installs mcos on disks |
| [EEPROM](/devices/eeprom/) | The chip a computer boots from |
| [Redstone card](/devices/redstone-card/) | Reads and puts out redstone on six faces |
| [Network card](/devices/network-card/) | Frames over cables, to a card's address or to all |
| [WLAN cards](/devices/wlan-card/) | Frames through the air, with a tier's range |
| [Bluetooth card](/devices/bluetooth-card/) | Reads and sets the board a card is linked to |
| [Sound card](/devices/sound-card/) | Plays samples written to it |
| [Internet card](/devices/internet-card/) | HTTP and HTTPS requests to the real internet |
| [Operator card](/devices/operator-card/) | Runs server commands (creative only) |
| [Motion sensor](/devices/motion-sensor/) | Reports living things moving nearby |
| [Audio sensor](/devices/audio-sensor/) | Reports sounds a player there would hear |
| [Weather sensor](/devices/weather-sensor/) | Reports the weather and its changes |
| [Robotic piston](/devices/robotic-piston/) | Pushes and retracts on any side |
| [Robotics device](/devices/robotics/) | A bot golem's motors and hands: walks it, senses round it, uses what it holds |
| [Charging pad](/devices/charger/) | Charges a robot standing on it |
| [Crafting card](/devices/crafting-card/) | Storage and a crafting grid; a robot's programs craft with it |
| [Energy field card](/devices/energy-field-card/) | Suspends a creature or an item in a field, and moves it |
