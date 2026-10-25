# Writing the manual (`docs/`)

The manual is a Jekyll site built by GitHub Pages from `docs/` on `master`, patterned after
OpenComputers' wiki (ocdoc.cil.li): a front page that sells the mod, one page per block, item and
device with a fixed layout, a reference for every program and API, and numbered tutorials.

## Rules

- **Facts come from the code**, which is the truth: read the block, item, device, recipe JSON and
  language files. Never invent a number, recipe, tier or behavior; if the code doesn't say, leave it out.
  A page written from memory of OpenComputers is wrong: this is a different mod.
- **Words:** "bus" and "device", never "component". The Java side is "the host".
- **Names:** "Modular Computers" in full on every page. Never name the machines the mod was built
  on, and never use a person's name other than `payonel`.
- Tone: plain and friendly, short sentences, "you". Say what a thing is for before how it works.
- A change to a block, item, device or program updates its page in the same commit.

## A page

Front matter, then Markdown (kramdown, GitHub-flavored; tables and fenced code work):

```yaml
---
title: Tier 1 Case          # the page's heading and its sidebar entry
nav: Case                   # optional shorter sidebar text
section: Hardware           # one of _config.yml's wiki_sections
order: 10                   # place within the section (lower first)
---
```

- Each section has an `index.md` (`order: 0`) listing its pages with a line each.
- **Links** are root-relative with a trailing slash: `[Case](/hardware/case/)`. The layout adds the
  site's base path. External links are absolute. Link every first mention of another page's subject.
- **Images** go in `docs/assets/img/`, referenced `![alt text](/assets/img/name.png)`; always alt text.
  No placeholder images: no image is better than a missing one.
- **Callouts:** a paragraph followed by `{: .note}`, `{: .tip}` or `{: .warn}`.
- **Infobox** (blocks, items, devices), at the page's top, before the first paragraph:

```html
<div class="infobox" markdown="1">

### Facts
| | |
|---|---|
| Kind | Block |
| Stack | 64 |

</div>
```

## Page shapes

- **Block or item:** one-sentence summary; infobox (kind, stack size, tiers, recipe in words,
  power if any); *Use* (what it is for and how to place or use it); *Crafting* (the recipe, in words,
  with its quirks, e.g. a disk in a recipe loses its files); *Notes*.
- **Device (card, sensor, disk...):** summary; infobox (slot, tier, power); *In mcos* (its `/dev`
  file's name, what a read gives, with a sample, what a write does or that it's refused, how it
  polls, and that its ioctls are its methods); *Methods* (every method the device has, none
  missing, checked against its `@Method`s: each as a `### name(args) → results` heading, a
  sentence, then the notes and failures); *Events* (each event's fields, and its interrupt); *Faces*
  if it takes them; *Examples* (several complete programs, the user's ask: through its file and
  through its methods, real uses, each included from `docs/examples/` and run by a test).
- **Program:** from its man page and its header comment: synopsis, options, exit statuses, an
  example, and where it differs from GNU. The man page is the source; keep the two in agreement.
- **Tutorial:** a prerequisites list, then **the whole thing first** (the user's: show the code,
  and a reader pulls what they need from it): every file complete (programs, definitions,
  settings) and every command in order, as tested examples (`docs/examples/`, included); then
  numbered steps each saying what to do and what you will see, what goes wrong and how to fix it,
  and a link to the next tutorial. Where a mechanism has defaults (a service's actions), show them
  written out in full too, named, so a reader can copy and change one.
- **Concept:** what it is, why the mod works that way, what a player or program sees.

## Lua in pages

- A page shows a complete program by including it: a fenced `lua` block holding
  `{% include name.lua %}`, from `docs/examples/` (Jekyll's includes directory; the files are also
  published there, for download). Show a whole program, not a fragment: the manual teaches that a
  program blocks (a read, `sys.sleep`, `sys.poll`) and never yields.
- Every example in `docs/examples/` is run by a test, and `DocsLuaTest` compiles every `lua` block
  in every page. The tests are in `common/src/test/.../runtime/` (`ExamplesTest` and one class a
  group of devices, `*ExamplesTest`, each on `McosHarness`: a computer booting mcos); a device only
  a world makes runs its examples in a server game test (`*ExamplesGameTest`). A block that is only a fragment (a signature, an ellipsis) has a
  `<!-- lua-fragment -->` line before it. Add a test when you add an example.
- Code highlighting comes from rouge, colored by `assets/style.css`.

## Checking

`scripts/docs_check.py` checks every page's front matter and every internal link and image. With
Ruby installed, `cd docs && bundle exec jekyll build` builds the real site.

## Screenshots

They come from the client game tests (`./gradlew :fabric:runProductionClientGameTest`, then
`fabric/build/run/productionClientGameTest/screenshots/`): copy one to `docs/assets/img/` under a
plain name, and look at it before using it. The tests' pictures change when the art does; take them
again then.
