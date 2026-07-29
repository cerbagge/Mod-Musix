# Musix

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1--1.21.x-brightgreen)
![Fabric](https://img.shields.io/badge/Loader-Fabric-orange)
![Client](https://img.shields.io/badge/Side-Client%20only-blue)
![Version](https://img.shields.io/badge/Version-v5.4.1-blueviolet)

**🌐 Languages**: **English** (this page) · [한국어](README_KO.md) · [Modrinth description](MODRINTH.md)

> ⚠ **Server-specific mod** — only works on **PlanetEarth Server (`planetearth.kr`)**. Has no effect on any other server.

A client-side Fabric mod that lets you play PlanetEarth's chest-GUI based instruments (`악기 - 하프`, `악기 - 베이스(드럼)`, ...) with your **keyboard** or a **MIDI device**. Press a key — the mod clicks the corresponding slot — the server plugin plays the note.

## Features

- **Keyboard input → slot click** (auto-active when chest title starts with `음악` or `악기`)
- **Volume control** *(v5.3.0)* — arrow keys, clicks outside the GUI, or `Tab + number` set the server volume via `/instruments`
- **MIDI input support** *(v4.0.0)* — connect any USB MIDI keyboard / pad device and play notes directly
- **MIDI transpose** *(v5.1.0)* — shift incoming notes by ±24 semitones (±2 octaves) from the menu
- **Octave folding** *(v5.2.0)* — notes pushed outside F#2~F#6 by transposing are folded back by octaves instead of being dropped
- **Secondary key per note** *(v4.2.0)* — bind two separate keys to the same note
- **Statistics screen** *(v4.1.0)* — live line chart of notes-per-minute, TOP 5 most-played notes, common vs drum preset ratio, session stats
- **Two presets, auto-selected by chest title**
  - `common` — 49 notes (F#2 ~ F#6) for melodic instruments
  - `drum` — 12 notes (bass / hi-hat / snare / cymbal × low/mid/high) for `(드럼)` chests
- **Auto-mapping** — open a chest once, click "자동 매핑" in the menu → slots align to that chest's actual layout (item-name matching, falls back to index)
- **Space + key modifier** *(v3.12.0)* — natural notes on plain keys, sharps/flats on `Space + key`
- **In-game key rebinding** — left-click row to capture next key, right-click to clear, ESC to restore default
- **10 modifier keys** *(v5.3.0)* — `Shift` `Ctrl` `Alt` `Win` `Space` `Tab` `CapsLock` `Enter` `\` `Backspace` can all be used in combos
- **Conflict detection** with red flash on duplicates
- **Key auto-repeat protection** *(v4.0.3)* — holding a key only triggers one click
- **Per-note usage counter** (× N) on the mapping list
- **Preset tabs** (common / drum / volume settings)
- **Persistent H2 storage** (shaded — works under Lunar Client / Ichor)
- **Click options** — PICKUP / QUICK_MOVE / SWAP / ... + left / right / middle button (in 고급 설정)
- **Korean + English** locale

## Usage

1. Install the jar matching your Minecraft version (see [Compatibility](#compatibility)) in your `mods/` folder — Fabric Loader + Fabric API required
2. Join **PlanetEarth Server** (`planetearth.kr`)
3. Open a music chest (e.g. `악기 - 하프`) — the mod silently caches its slot layout
4. Press **`\`** (backslash, default menu key) → opens the Musix menu
5. Click **자동 매핑** → slots align to the current chest
6. Reopen the chest and press the mapped keys to play notes
7. Switching instruments? Auto-map again on the new chest
8. **Volume**: arrow keys `↑`/`↓` while the chest is open, or `Tab + 1~9,0` for an exact level
9. **Statistics**: menu → **통계** button · **MIDI**: menu → **고급 설정** → **MIDI 입력**

## Default key layout (common preset, 49 notes — natural + sharp)

Naturals are played with the key alone. Sharps and flats are played with **`Space + key`**.

| Row    | Keys                       | Natural notes  | Sharps (with Space)         |
|--------|----------------------------|----------------|-----------------------------|
| 1 row  | `` ` 1 2 3 ``              | G2 → B2        | F#2, G#2, A#2               |
| Q row  | `Q W E R U I O`            | C3 → B3        | C#3, D#3, F#3, G#3, A#3     |
| A row  | `A S D F J K L`            | C4 → B4        | C#4, D#4, F#4, G#4, A#4     |
| Z row  | `Z X C V M , .`            | C5 → B5        | C#5, D#5, F#5, G#5, A#5     |
| extra  | `7 8 9 0`                  | C6 → F6        | C#6, D#6, F#6               |

Everything is rebindable in the menu (left-click row, then press the new key combo).

## Drum preset (auto when title contains `(드럼)`)

12 notes across 4 rows. Slots auto-aligned on chest open (item-name matching).

- `1 2 3` → bass low / mid / high
- `Q W E` → hi-hat low / mid / high
- `A S D` → snare low / mid / high
- `Z X C` → cymbal low / mid / high

Blocked slots (never mapped): `6, 19, 45, 53` — server GUI navigation slots.

## Volume control *(v5.3.0+)*

Only active while an instrument chest is open. **Two different mechanisms:**

| Input | Action | How |
|---|---|---|
| `↑` (up arrow) | Volume up | Sends the outside-GUI **left**-click packet (slot `-999`) |
| `↓` (down arrow) | Volume down | Sends the outside-GUI **right**-click packet (slot `-999`) |
| Left / right-click **outside** the chest GUI | Volume up / down | Vanilla behaviour, passed straight through |
| `Tab + 1` ~ `Tab + 9`, `Tab + 0` | Set volume 1~10 exactly | Sends `/instruments <n>` chat command |

The server treats a click outside the chest GUI as a volume step, so the arrow keys simply fire that
same packet — no cursor is moved. `Tab + number` is the only path that uses the `/instruments` command.

- **Opening an instrument chest sets the volume to 8** via `/instruments 8`, since the mod can't read the server's current value.
- **Levels 9 and 10 do not increase loudness** — they increase how far away the sound can be heard. 8 is the practical maximum volume.
- 0.1 s cooldown; inputs during the cooldown are dropped. **Hold an arrow key to keep stepping every 0.1 s.**
- Arrow-key stepping is skipped while you are holding an item on the cursor — slot `-999` would drop it.
- The "current volume" shown in the menu is an **estimate**; the server owns the real value, so wrap-around
  at 1/10 follows the server's behaviour rather than the mod's.
- All 12 bindings are rebindable in the **통합 설정** tab of the menu.

## MIDI input *(v4.0.0+)*

- **Menu → 고급 설정 → MIDI 입력** — scans connected MIDI devices, click to connect
- Auto-reconnects on next launch if the same device is still plugged in
- Async device scan / connection — won't freeze the client
- Works alongside keyboard input

### Transpose *(v5.1.0+)* & octave folding *(v5.2.0+)*

- Menu top row: `[-12] [-1] (transpose) [+1] [+12]` — shifts incoming MIDI notes by up to **±24 semitones**
- **Octave folding** (ON by default, toggle in 고급 설정) — after transposing, notes outside the playable
  F#2~F#6 range are folded back by whole octaves instead of being silently dropped

## Statistics screen *(v4.1.0+)*

- **Session info**: start time, elapsed, total notes, average / current minute rate, last note
- **Line chart**: notes-per-minute over the last 60 minutes (auto-scaling Y axis)
- **TOP 5 notes**: horizontal bar chart of the most-played notes
- **Preset ratio**: common vs drum percentage + absolute counts
- **Reset button**: clears all counters and restarts the session timer

## Build

Multi-version via [Stonecutter](https://stonecutter.kikugie.dev/). Build all variants at once (requires JDK 21):

```sh
./gradlew chiseledBuild
```

Output: `versions/<mc>/build/libs/musix-mc<mc>-<version>.jar` (one jar per Minecraft version).
1.20.2–1.20.4 is compiled to Java 17 bytecode, 1.20.5+ to Java 21. `gradle.properties` pins a local
`org.gradle.java.home` (adjust the path for your machine, or remove it and set `JAVA_HOME` to a JDK 21).

GitHub Actions builds every variant on each push and attaches them to a Release on tag push (`v*`).

## Compatibility

Pick the jar matching your Minecraft version:

| Jar | Minecraft | Java |
|-----|-----------|------|
| `musix-mc1.20.1-<ver>.jar` | 1.20.1 | 17+ |
| `musix-mc1.20.4-<ver>.jar` | 1.20.2 – 1.20.4 | 17+ |
| `musix-mc1.20.6-<ver>.jar` | 1.20.5 – 1.20.6 | 21+ |
| `musix-mc1.21.1-<ver>.jar` | 1.21+ | 21+ |

- Fabric Loader 0.15+, Fabric API
- **Lunar Client** (Ichor) verified — H2 shaded into jar to avoid ClassLoader isolation
- License: **CC-BY-NC-SA-4.0**

## License

This work is licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
You may share and adapt, but must give credit, use it non-commercially, and share derivatives under the same license.
