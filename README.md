# SkTyper

A [Skript](https://github.com/SkriptLang/Skript) addon for [Typewriter](https://docs.typewritermc.com).

Typewriter handles the story: dialogue, quests, cinematics, NPCs, facts, audiences. SkTyper lets your
scripts read and drive all of it — trigger an entry when someone opens a crate, gate a shop behind a
completed quest, hand out a reward when a cutscene finishes, block PvP while a dialogue is open.

Built against Typewriter `0.9.0`, Skript `2.16.1`, Paper `1.21.x`, Java 21.

---

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [How entries are addressed](#how-entries-are-addressed)
- [Types](#types)
- [Expressions](#expressions)
- [Effects](#effects)
- [Conditions](#conditions)
- [Events](#events)
- [Quests](#quests)
- [Building](#building)
- [Deploying](#deploying)
- [How it works](#how-it-works)
- [Troubleshooting](#troubleshooting)

---

## Install

1. Have Paper 1.21+ on Java 21.
2. Install [Skript](https://github.com/SkriptLang/Skript/releases) 2.9 or newer.
3. Install [Typewriter](https://modrinth.com/plugin/typewriter) 0.9.0, plus its `packetevents`
   dependency.
4. Drop `SkTyper.jar` into `plugins/`.
5. Restart. You should see `SkTyper enabled against Typewriter 0.9.0` in the log.

| Plugin | Required | Notes |
| --- | --- | --- |
| Skript | yes | hard dependency |
| Typewriter | yes | soft dependency in `plugin.yml`; SkTyper disables itself with a readable message if it's missing |
| Typewriter Quest extension | optional | only the quest syntax needs it, everything else works without it |

SkTyper does not add commands, config files or permissions. It is syntax only.

---

## Quick start

```applescript
on join:
    if typewriter fact "tutorial_done" of player is 0:
        trigger typewriter entry "tutorial_start" for player

on typewriter cinematic end:
    if event-string is "intro_cutscene":
        give 1 diamond to event-player

on damage:
    if victim is a player:
        if victim is in a typewriter dialogue:
            cancel event
```

A full script covering every syntax group lives in [`examples/sktyper-showcase.sk`](examples/sktyper-showcase.sk).

---

## How entries are addressed

Everything in Typewriter is an *entry* — a dialogue line, a fact, a quest, an objective, a cinematic
segment, an NPC definition, an audience filter. Anywhere SkTyper takes an entry you can pass either:

- a `typewriter entry` value, or
- plain text holding the entry's **id** (the value in the Typewriter panel) or its **name**.

So these three are equivalent:

```applescript
trigger typewriter entry "welcome_dialogue" for player
set {_e} to typewriter entry "welcome_dialogue"
trigger {_e} for player
trigger typewriter entry "Welcome Dialogue" for player   # by name
```

Ids are stable across renames, so prefer them in scripts you intend to keep.

Lookup falls back from id to name. If neither matches, the entry is silently skipped — use
`typewriter entry "..." exists` if you want to know.

---

## Types

| Type | Pattern | Description |
| --- | --- | --- |
| `typewriter entry` | `typewriter entry`, `typewriter entries` | Any entry on any page |
| `typewriter page` | `typewriter page`, `typewriter pages` | A page from the panel |

Both types can be stored in variables for the lifetime of the server, but they are not serialised, so
they don't survive a restart in a persistent variable. Store the id instead if you need that.

---

## Expressions

### Lookup

```applescript
typewriter entry[ies] [with] [id/name] %strings%
all typewriter entries

typewriter page[s] [with] [id/name] %strings%
all typewriter pages
```

```applescript
set {_dialogue} to typewriter entry "welcome_dialogue"
send "%size of all typewriter entries% entries loaded"
set {_pages} to all typewriter pages
```

### Entry and page properties

```applescript
[the] typewriter id of %entries/pages%
[the] typewriter name of %entries/pages%
[the] typewriter page of %entries%
[the] typewriter type of %pages%          # sequence / static / cinematic / manifest
[the] typewriter priority of %pages%
[the] typewriter entries of %pages%
```

```applescript
send "%the typewriter name of {_dialogue}% lives on %the typewriter page of {_dialogue}%"

loop the typewriter entries of typewriter page "main_story":
    send " - %the typewriter id of loop-value%"
```

### Facts

```applescript
[the] typewriter fact[s] %entries% (of|for) %players%
[the] typewriter fact last update [time] [of] %entries% (of|for) %players%
```

Facts are plain integers. The fact expression is **settable**:

| Change | Effect |
| --- | --- |
| `set ... to %number%` | writes the value |
| `add %number% to ...` | reads, adds, writes |
| `remove %number% from ...` | reads, subtracts, writes |
| `reset` / `delete` | writes 0 |

```applescript
set typewriter fact "talked_to_mayor" of player to 1
add 5 to typewriter fact "coins_collected" of player
reset typewriter fact "daily_progress" of all players

if typewriter fact last update of "daily_reward" of player is less than 1 day ago:
    send "Come back tomorrow."
```

Writes go through Typewriter's fact database, so the engine fires its own refresh trigger and
objectives, audiences and quest states react immediately — the same as if Typewriter had written the
value itself.

Only writable facts can be changed. Permission facts, placeholder facts and other read-only kinds
ignore writes silently; use `refresh typewriter fact` after changing whatever they're derived from.

Reading a fact a player has never had returns `0`, which matches how Typewriter reads it internally.

### Dialogue

```applescript
[the] current typewriter dialogue of %players%
[the] typewriter dialogue speaker[s] of %players%
```

```applescript
on typewriter dialogue switch:
    send "Now showing %the typewriter name of the current typewriter dialogue of event-player%" to console

on typewriter dialogue end:
    send "You spoke with %the typewriter dialogue speakers of event-player%" to event-player
```

### Cinematics

```applescript
[the] typewriter cinematic frame of %players%
```

Settable with `set`, `add` and `remove`. Setting it seeks the cinematic. Typewriter only ever seeks
forwards, so a lower frame than the current one is ignored.

```applescript
if the typewriter cinematic frame of player is greater than 200:
    stop the typewriter cinematic for player

set the typewriter cinematic frame of player to 400
```

Returns nothing when the player isn't watching anything.

### Audiences and NPCs

```applescript
[the] typewriter audience of %entries%
[the] typewriter display name of %entries% (of|for) %players%
[the] interacted typewriter entity instance
```

`typewriter audience` gives every online player currently inside an audience entry — the manifest-page
entries that decide who sees an NPC, a sidebar, a boss bar, an objective.

`typewriter display name` resolves a speaker's name for that specific player, with colour codes and
placeholders already applied.

`the interacted typewriter entity instance` only works inside a `typewriter entity interact` event.

```applescript
send "Boss bar is showing for %the typewriter audience of "boss_bar"%"

on typewriter entity interact:
    send "You clicked %the typewriter display name of event-typewriter entry for event-player%"
```

### Misc

```applescript
[the] typewriter version
```

---

## Effects

### Triggering entries

```applescript
trigger [the] [typewriter] entr(y|ies) %entries% (for|to) %players%
```

Fires the entry exactly as if the story graph had reached it — criteria are checked, modifiers are
applied, and the entry's own triggers run afterwards. Entries that aren't triggerable (facts,
manifest entries) are skipped.

```applescript
trigger typewriter entry "welcome_dialogue" for player
trigger typewriter entries "quest_a" and "quest_b" for all players
```

### Cinematics

```applescript
(start|play) [the] [typewriter] cinematic [page] %page% (for|to) %players%
(start|play) [the] [typewriter] cinematic [page] %page% (for|to) %players% without blocking (chat|messages)
stop [the] [typewriter] cinematic (of|for) %players%
```

The page is given by id or name and has to be a cinematic page. By default Typewriter hides chat and
action bar messages for the duration; the `without blocking messages` form leaves them visible.

```applescript
start typewriter cinematic "intro_cutscene" for player
play typewriter cinematic "credits" for all players without blocking messages
stop the typewriter cinematic for player
```

### Dialogue

```applescript
(continue|advance) [the] typewriter dialogue (of|for) %players%
force [the] next typewriter dialogue (of|for) %players%
(end|close) [the] typewriter (interaction|dialogue) (of|for) %players%
```

`continue` behaves like the player pressing the continue key — if the typing animation is still
running it finishes that first. `force` skips straight to the next entry. `end` closes whatever
interaction the player is in, dialogue or cinematic or content editor.

```applescript
on sneak toggle:
    if player is in a typewriter dialogue:
        continue the typewriter dialogue for player
```

### Audiences

```applescript
add %players% to [the] typewriter audience [of] %entries%
remove %players% from [the] typewriter audience [of] %entries%
```

Audiences normally manage themselves from their own filters. A manual change holds until the audience
recalculates, so treat this as a nudge rather than a permanent assignment.

### Facts

```applescript
refresh [the] typewriter fact[s] %entries% (of|for) %players%
```

Re-fires the engine's fact refresh trigger so everything listening to that fact re-evaluates. Useful
after changing the data behind a placeholder or permission fact, which SkTyper can't write to.

---

## Conditions

Every condition also has a negated form (`is not`, `isn't`, `are not`, `aren't`).

```applescript
%players% (is|are) [currently] in [a] typewriter dialogue
%players% (is|are) [currently] (playing|watching|in) [a] typewriter cinematic [%string%]
%players% (is|are) in [the] typewriter audience [of] %entry%
[the] [typewriter] quest %entry% (is|are) (active|completed|inactive) (of|for) %players%
%players% (is|are) tracking [the] [typewriter] quest [%entry%]
[the] typewriter entr(y|ies) %strings% exist[s]
[the] typewriter criteria of %entry% (is|are) met (of|for) %players%
typewriter (is|are) (loaded|enabled|available)
typewriter quests (is|are) (loaded|enabled|available|supported)
```

```applescript
if player is in a typewriter dialogue:
    cancel event

if player is playing typewriter cinematic "intro_cutscene":
    send "Enjoy the show."

if the typewriter criteria of "secret_dialogue" are met for player:
    send "You found a secret."
```

`typewriter criteria ... are met` runs the same check Typewriter does before firing an entry, so it
answers "would this trigger right now?" without triggering it.

`typewriter quests are available` checks that the Quest extension is actually loaded — worth guarding
on before any quest syntax if your server might run without it.

---

## Events

| Event | Event values |
| --- | --- |
| `on typewriter dialogue start` | `event-player` |
| `on typewriter dialogue switch` | `event-player` |
| `on typewriter dialogue end` | `event-player` |
| `on typewriter cinematic start` | `event-player`, `event-string` (page id) |
| `on typewriter cinematic end` | `event-player`, `event-string` (page id), `event-number` (frame) |
| `on typewriter cinematic tick` | `event-player`, `event-number` (frame) |
| `on typewriter entity interact` | `event-player`, `event-typewriter entry` (definition) |
| `on typewriter quest status change` | `event-player`, `event-typewriter entry`, `event-string`, `past event-string` |
| `on typewriter tracked quest change` | `event-player`, `event-typewriter entry`, `past event-typewriter entry` |
| `on typewriter staging change` | `event-string` (state) |
| `on typewriter content editor start` | `event-player` |
| `on typewriter content editor end` | `event-player` |
| `on typewriter unload` | – |

```applescript
on typewriter cinematic start:
    broadcast "%event-player% is watching %event-string%"

on typewriter cinematic tick:
    if event-number is 100:
        play sound "entity.wither.spawn" to event-player
```

Typewriter fires all of these asynchronously. Skript moves the trigger back onto the main thread
before running it, so ordinary effects are safe inside them.

`cinematic tick` fires up to twenty times a second per viewer. Keep that trigger cheap.

---

## Quests

Quest syntax needs Typewriter's Quest extension. Without it everything still parses, the expressions
return nothing and the effects do nothing.

```applescript
[the] tracked typewriter quest of %players%
[all] (active|completed|inactive) typewriter quests of %players%
[the] typewriter quest status of %entries% (of|for) %players%
[the] typewriter (quest|objective) display [name] of %entries% (of|for) %players%

track [the] [typewriter] quest %entry% (of|for) %players%
untrack [the] [typewriter] quest (of|for) %players%
```

The tracked quest expression is settable — `set` starts tracking, `delete` stops.

```applescript
command /quests:
    trigger:
        if typewriter quests are not available:
            send "<red>The Quest extension isn't installed."
            stop
        send "&6In progress:"
        loop the active typewriter quests of player:
            send " &7- %the typewriter quest display of loop-value for player%"

on typewriter quest status change:
    if event-string is "completed":
        send title "&6Quest complete" with subtitle "%the typewriter quest display of event-typewriter entry for event-player%" to event-player
```

Quest status is text: `inactive`, `active` or `completed`.

**There is no way to set a quest's status directly, and that's on purpose.** Typewriter derives
status from the facts a quest is built on. Change those facts and the status follows:

```applescript
# Not possible - and shouldn't be:
#   set the typewriter quest status of "main_story" for player to "completed"
# Do this instead:
set typewriter fact "main_story_finished" of player to 1
```

`typewriter objective display` renders an objective with its full styling — the tick mark for
completed, grey for not-yet-showing — exactly as it appears in the quest tracker.

---

## Building

```bash
./gradlew build
```

Output lands in `build/libs/SkTyper.jar`. Everything except the Kotlin plugin comes from public
repositories:

| Repository | For |
| --- | --- |
| `repo.papermc.io` | paper-api |
| `repo.skriptlang.org` | Skript |
| `maven.typewritermc.com` | engine-core, engine-paper, engine-loader |
| `repo.codemc.io` | packetevents-api |

The jar contains nothing but `me/mhfs/sktyper/` and `plugin.yml`. No dependencies are shaded.

---

## Deploying

`./gradlew deploy` builds and uploads the jar over FTP. It reads four properties:

```properties
ftpHost=your.host
ftpPort=21
ftpUser=your-user
ftpPassword=your-password
ftpRemotePath=plugins/SkTyper.jar
```

Put them in `~/.gradle/gradle.properties`, not in the repo, or pass them on the command line:

```bash
./gradlew deploy -PftpHost=example.com -PftpUser=me -PftpPassword=secret
```

---

## How it works

Two design decisions are worth knowing about before you touch the build file.

### The Kotlin standard library is not bundled

Typewriter is a Paper-style plugin that pulls `kotlin-stdlib` in through Paper's library loader.
Paper's global plugin classloader group *does* search other plugins' libraries, so a legacy
(`plugin.yml`) plugin like a Skript addon can link straight against Typewriter's copy.

Shipping a second copy would give `kotlin.reflect.KClass`, `kotlin.sequences.Sequence` and friends two
distinct `Class` objects — one per loader — and every call into the Typewriter API would fail with a
loader-constraint `LinkageError`. So `kotlin-stdlib` is `compileOnly`,
`kotlin.stdlib.default.dependency=false` is set in `gradle.properties`, and the jar ships nothing but
its own classes.

If you bump the Kotlin version in `build.gradle`, match it to whatever `TypewriterPaperLoader`
downloads (currently `2.2.10`).

### Quests go through reflection

Everything in `engine-core` and `engine-paper` lives in Typewriter's plugin jar and is linked
directly. The Quest extension doesn't — Typewriter loads extensions into a private `URLClassLoader`
that it discards and rebuilds on every reload.

`bridge/QuestBridge.kt` therefore reaches the quest API reflectively through `ExtensionLoader`, and
mirrors `AsyncQuestStatusUpdate` and `AsyncTrackedQuestUpdate` onto plugin-owned events so Skript has
stable classes to register against. It re-attaches after every `TypewriterUnloadEvent` and retries for
a minute at startup, since extensions finish loading well after we enable.

### Layout

```
me/mhfs/sktyper/
  SkTyper.kt              plugin entry point
  tw/Tw.kt                every call into the Typewriter engine
  bridge/                 reflective Quest extension access + mirrored events
  types/                  Skript ClassInfo registrations
  events/                 Skript event + event value registrations
  elements/
    Elements.kt           the syntax table
    expressions/
    effects/
    conditions/
```

`Elements.kt` registers everything explicitly rather than through `SkriptAddon#loadClasses`, so the
whole syntax surface reads top to bottom in one file and the ordering between overlapping patterns is
deliberate rather than accidental.

### A note on the Skript API

SkTyper uses the static `Skript.registerExpression` / `registerEffect` / `registerEvent` /
`EventValues.registerEventValue` entry points. Skript 2.14 deprecated them in favour of the per-addon
`SyntaxRegistry`, but the static forms still work and are the only ones available across the whole
2.9–2.16 range that servers actually run. If they ever get removed, `elements/Elements.kt` and
`events/TypewriterEvents.kt` are the two files to port.

---

## Troubleshooting

**`Typewriter is not installed or failed to enable`**
Typewriter isn't there, or it crashed on startup. Check the log above SkTyper's line — a missing
`packetevents` is the usual cause.

**`Skript is no longer accepting registrations`**
SkTyper enabled after Skript finished loading. Almost always means something reloaded plugins at
runtime; restart the server properly.

**`Typewriter Quest extension not found`**
Informational. Quest syntax parses but does nothing. Install the Quest extension into
`plugins/Typewriter/extensions/` if you want it.

**A script can't find an entry**
Check the id in the Typewriter panel, and remember lookup is by id first, then name. `typewriter entry
"..." exists` is the quick test. Entries also only exist once Typewriter has finished loading its
pages, so lookups during `on load` may run too early.

**Nothing happens when triggering an entry**
Either the entry isn't triggerable — facts and manifest entries aren't — or its criteria don't pass
for that player. `if the typewriter criteria of "..." are met for player` tells you which.

**Fact writes don't stick**
Read-only fact kinds (permission, placeholder, and similar) ignore writes. Change the underlying data
and call `refresh typewriter fact` instead.
