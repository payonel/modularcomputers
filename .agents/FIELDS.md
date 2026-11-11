# The field dictionary

The words devices answer with, and the rules for them (the user's, 2026-11-10: one set of words
and rules, every API checked against it). A device's answers and events use only words defined
here, each with its one meaning; a new word is added here, with its meaning, in the commit that
first uses it. Failures are the one shape the code enforces (`runtime/Failure`); everything else is
kept by this document and the test that checks the code against it.

## The rules

1. **A word means one thing everywhere.** A field named here means what this says, on every
   device, in an answer, an event or a failure.
2. **A fixed field's value is a word from a closed set** (`event`, `why`, `reason`, `expected`,
   `kind`, `condition`...), written in the code as a literal or a named constant, never built from
   text (concatenated, formatted, taken from the world). A word carried in a variable came from
   one of those (a command's end carries its method's name, recorded as the command started).
3. **No data inside a string.** A number is a number field, a list a list; a string a program would
   have to cut up is two fields. A `message` is a person's words, never parsed.
4. **Every field is always there** (`""` when there's nothing to say; a number field's "none" said
   by its own documented value: `status = 0`, `distance = -1`).
5. **`event` is what caused it.** An event a method caused carries that method's name, literally,
   always; a device's own news (no method caused it) has its own word, listed below, never a
   method's name (the user's, 2026-11-06). So an `event` naming a method means that method caused it.
6. **A refusal is `false, {why, ...}`**, its shape its why's (below), built only by `Failure`. A call
   that did what it was asked answers `true` and its values, even when what it reports went badly
   (a 404, a command that didn't work, a use that did nothing): that's the response, not a refusal.
7. **An end's failure is flat:** an event that ended badly has its why's fields beside `why`, and
   (for a command) its `call`.
8. **A device's state is a `get`; what happens on it is its events.** A `/dev` read of events starts
   at the file's open, never a backlog.
9. **A thing is `{kind, name, id, at}`:** what sort of thing, its registry id, its own UUID (`""`
   for a block), where.

## Whys

A refusal's (`Failure`'s builders) and an end's (`EventQueue`'s). Every field always there.

| why | fields | means |
|---|---|---|
| `busy` | `id` | wait: the device named has news when it's free |
| `held` | `id` | wait: a field holds the robot (the robot's device id) |
| `drifted` | `drift` | a positional command off its robot's spot |
| `fault` | `condition`, `part`, `message` | the machine itself: `missing` a part, `io_error`, `internal_error` |
| `bad_argument` | `argument`, `value`, `expected` | an argument it can't use |
| `bad_state` | `of`, `value`, `expected` | the call is fine; what it acts on isn't in the state it needs |
| `denied` | `reason`, `value` | not allowed |
| `limit` | `of`, `max` | over a limit |
| `network` | `reason`, `message` | failed in transit, or the other end answered badly |
| `not_found` | `kind`, `value` | what it names isn't there |
| `symbolic_link` | `at`, `target` | a disk path met a link: the OS goes on from it |
| `missing_inputs` | `name`, `call` | a craft's grid makes nothing (an end) |
| `no_space` | `name`, `call` | no room for what a craft makes (an end) |
| `blocked` | `name`, `face`, `call` | a move met something (an end, or a field's notice) |
| `stopped` | `call` | a program's `stop()` ended it (an end) |
| `lost` | `name`, `id`, `at`, `call` | what it acted on is gone |
| `unbreakable` | `name`, `id`, `at`, `call` | the game won't let it break (a swing's end) |
| `interrupted` | `call` | a command under way at a save that couldn't go on |
| `shove`, `fall`, `held` | (robotics' notices: `drift`; `drift`, `below`; `id`) | what no program asked for |

## Events: each device's words

An event's `event` is the method that caused it, or one of the device's own-news words here.

| Device | Own news (no method caused it) |
|---|---|
| robotics | `notice` (`why`: `shove`, `fall`, `held`) |
| energy field card | `notice` (`why`: `lost`, `blocked`, `fault`) |
| redstone card | `change` (a face's state) |
| weather sensor | `change` |
| motion sensor | `motion` |
| audio sensor | `sound` |
| network card | `arrived` (a frame came); its own `send` and `broadcast` are their methods |
| charging pad | `docked`, `undocked` |
| disk (`changes()`) | `create`, `modify`, `format`; `remove`, `rename`, `write` (see the review list) |
| internet card | none: `request` is its method |
| operator card | none: `command` is its method |

## Fields

| Field | Means | Type | Where |
|---|---|---|---|
| `address` | another card's address (its id) | string | network card's frames and log |
| `amount` | how much fluid, in the game's units | number | a scan's fluid |
| `args` | a command's arguments, as the program gave them | list | `call` |
| `argument` | which argument, from 1 | number | `bad_argument` |
| `at` | where, `{forward, right, up}` in the computer's frame (blocks); a link's path on a disk (`symbolic_link`) | table / string | things, sightings, `lost`, `symbolic_link` |
| `below` | what a fall has under it | string | robotics' `fall` notice |
| `block` | an IMA ADPCM block's size, bytes (WAV's block align) | number | sound card's format |
| `broadcast` | whether a frame was a broadcast | boolean | received frames, `arrived` |
| `buffer` | the power it holds now | number | charging pad's `get` |
| `bytes` | a size in bytes moved (a frame's, a body's) | number | network and internet logs |
| `call` | `{args, progress}`: what a command was given, how far it got | table | an end that didn't end ok |
| `capacity` | the most it holds (a disk's bytes; a pad's power) | number | disk, charging pad |
| `cause` | why the board last shut down (a word) | string | `lastShutdown()` |
| `channels` | a sound format's channels | number | sound card |
| `charging` | whether the pad is charging a robot | boolean | charging pad |
| `command` | a server command as it ran | string | operator card's log |
| `condition` | what's wrong with the machine (`missing`, `io_error`, `internal_error`) | word | `fault` |
| `container` | a block's container size, in slots | number | a scan's block |
| `count` | how many items | number | items |
| `current` | the boot entry booted now | number | EEPROM's `getBoot` |
| `damage` | an item's wear | number | items |
| `data` | a frame's bytes | string | received frames |
| `directory` | whether a path is a directory | boolean | disk's `stat` |
| `disk` | a disk's id (a boot entry's) | string | EEPROM's boot entries |
| `dropped` | how many of what a broken block dropped stayed in the world | number | a swing's `result` |
| `distance` | how far between two cards, meters; `-1` none reached | number | network card |
| `drift` | how far a robot is off its spot, `{forward, right, up}` | table | `drifted`, notices, robotics' `get` |
| `encoding` | a sound format's encoding | word | sound card |
| `entries` | the boot entries | list | EEPROM's `getBoot` |
| `event` | what an event is (rule 5) | word | every event |
| `expected` | the word for what it needs (a type, a state) | word | `bad_argument`, `bad_state` |
| `face` | a side, as `Face` names it (the world's, or front/back/left/right) | word | redstone, `blocked`, robotics, pistons |
| `fluid` | a waterlogged block's fluid (registry id) | string | a scan's block |
| `forward`, `right`, `up` | the three parts of an `at`, a `drift`, a position (blocks) | number | inside those tables |
| `from` | where a frame came from (an address); a rename's old path | string | received frames, disk's `rename` |
| `hardness` | a block's hardness, the game's | number | a scan's block |
| `headers` | an HTTP response's headers, by name in lower case | table | internet `response` |
| `heading` | which way it faces, degrees right of the origin's forward | number | robotics' positions |
| `id` | a thing's or device's own UUID (`""` for a block) | string | things, `busy`, `held`, devices |
| `kept` | whether a swing kept all a broken block dropped | boolean | a swing's `result` |
| `key` | a Minecraft message's translation key | string | operator card |
| `kind` | what sort of thing (`entity`, `item`, `block`, `fluid`, `air`; `not_found`'s `device`, `file`...) | word | things, `not_found` |
| `label` | a device's or entry's label | string | device info, boot entries, items |
| `length` | how many bytes, from an `offset` | number | a raw disk's `write` change |
| `legs` | whether the robot stands on its legs | boolean | robotics' `get` |
| `level` | a redstone face's signal, 0 to 15 | number | redstone |
| `link` | a symbolic link's target | string | disk's `stat` |
| `lost` | how many events went before this one, its queue full | number | any event |
| `max` | a limit's most | number | `limit` |
| `maxDamage` | an item's most wear (see the review list) | number | items |
| `message` | a person's words, never parsed | string | `fault`, `network`, HTTP `response`, operator, `lastShutdown()` |
| `method` | an HTTP method | word | internet card |
| `millis` | how long it took, milliseconds | number | internet log |
| `missing` | (gone: `fault`'s `part`) | | |
| `mode` | a disk's format (`hfs`, `raw`) | word | disk |
| `modified` | when a file last changed, the world's milliseconds | number | disk's `stat` |
| `name` | what a thing is called: a registry id for the game's things | string | things, items, sightings, whys |
| `next` | the boot entry to boot once next | number | EEPROM's `getBoot` |
| `new`, `old` | the weather now and before | word | weather's `change` |
| `number` | a boot entry's number | number | EEPROM's boot entries |
| `offset` | a byte offset on a raw disk | number | a raw disk's `write` change |
| `of` | of what (a limit's, a bad state's) | word | `limit`, `bad_state` |
| `ok` | how an end or a record went | boolean | ends, logs |
| `order` | the boot order | list | EEPROM's `getBoot` |
| `part` | which part a fault is with | word | `fault` |
| `path` | a path on a disk | string | disk |
| `pathfinding` | whether the robot finds its way round things | boolean | robotics |
| `planned` | where its last command left it, as a position | table | robotics' `get` |
| `position` | where it really is: `{forward, right, up, heading}` from its origin | table | robotics |
| `powered` | what a redstone card puts out on a face | number | redstone |
| `progress` | how far a command got, in its arguments' terms | any | `call` |
| `rate` | a sound format's samples a second | number | sound card |
| `reason` | why, a word from its why's closed list | word | `denied`, `network` |
| `result` | a done command's answer; a Minecraft command's value | any | ends, operator |
| `robot` | the board id of the robot on a pad (see the review list) | string | charging pad |
| `sent` | a request body's whole size, bytes | number | internet log's `progress` |
| `size` | a file's size in bytes | number | disk's `stat` |
| `slot` | a container's slot, from 1 | number | items in containers |
| `solid` | whether a block can't be walked through | boolean | a scan's block |
| `source` | whether a fluid is a source block | boolean | a scan's fluid |
| `state` | (two meanings: see the review list) | | |
| `status` | an HTTP status; `0` no response | number | internet card |
| `successes` | how many of a Minecraft command's runs succeeded | number | operator card |
| `target` | a symbolic link's target | string | `symbolic_link` |
| `tier` | a device's tier | number | disk info |
| `time` | when, the world's time | number | `lastShutdown()` |
| `took` | how many cards took a frame | number | network log |
| `type` | (two meanings: see the review list) | | |
| `url` | a URL | string | internet card |
| `used` | bytes in use | number | disk |
| `value` | what was given or named, as given | any | `bad_argument`, `bad_state`, `denied`, `not_found` |
| `why` | a failure's word | word | failures, ends |
| `wireless` | whether a network card is a WLAN card | boolean | network card info |

Not answers, so not here: the keys a device uses to save itself (`saved`), and the save format's own
(`k`, `v`).

## Found, to review

What the inventory turned up that breaks a rule above, for the user to rule on (nothing changed yet):

1. **`maxDamage`** (an item's most wear) is camelCase; every other field is lower case with
   underscores.
2. **Fields left out when there's none** (rule 4): the charging pad's `robot`, robotics' `get`
   (`position`, `planned`, `drift`), the EEPROM's `next` and `current`, an item's `damage` and
   `maxDamage`, a scan block's `container` and `fluid`, a broken block's `dropped`.
3. **`state` means two things:** what robotics is doing (a word, `walking`), and a scanned block's
   properties (a table).
4. **`type` means two things:** a device's type (`storage`), and a listed path's (`file`,
   `directory`, `link`).
5. **The charging pad's `robot` is an id** (a board's): by rule 9 an id is `id`.
6. **`size` and `bytes`** both count bytes (a file's, a frame's).
7. **The disk's `remove`, `rename` and `write` changes** share their methods' names: by rule 5 that
   says those methods caused them. `remove` and `rename` are their methods'; a raw `write` is the
   `write` method's, but `modify` comes from a handle's close, with no method of its own.
8. **A swing's `result` says `kept` and `dropped` together:** `kept` is `dropped == 0`, the same
   fact twice (and `dropped` is left out when 0: item 2).
