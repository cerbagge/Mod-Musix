# Musix

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Fabric](https://img.shields.io/badge/Loader-Fabric-orange)
![Client](https://img.shields.io/badge/Side-Client%20only-blue)
![Version](https://img.shields.io/badge/Version-v4.1.3-blueviolet)

**🌐 Languages**: **English** (this page) · [한국어](README_KO.md) · [Modrinth description](MODRINTH.md)

> ⚠ **Server-specific mod** — only works on **PlanetEarth Server (`planetearth.kr`)**. Has no effect on any other server.

A client-side Fabric mod that lets you play PlanetEarth's chest-GUI based instruments (`악기 - 하프`, `악기 - 베이스(드럼)`, ...) with your **keyboard** or a **MIDI device**. Press a key — the mod clicks the corresponding slot — the server plugin plays the note.

## Features

- **Keyboard input → slot click** (auto-active when chest title starts with `음악` or `악기`)
- **MIDI input support** *(v4.0.0)* — connect any USB MIDI keyboard / pad device and play notes directly
- **Statistics screen** *(v4.1.0)* — live line chart of notes-per-minute, TOP 5 most-played notes, common vs drum preset ratio, session stats
- **Two presets, auto-selected by chest title**
  - `common` — 49 notes (F#2 ~ F#6) for melodic instruments
  - `drum` — 12 notes (bass / hi-hat / snare / cymbal × low/mid/high) for `(드럼)` chests
- **Auto-mapping** — open a chest once, click "자동 매핑" in the menu → slots align to that chest's actual layout (item-name matching, falls back to index)
- **Space + key modifier** *(v3.12.0)* — natural notes on plain keys, sharps/flats on `Space + key`
- **In-game key rebinding** — left-click row to capture next key, right-click to clear, ESC to restore default. Shift / Alt / Space combos supported
- **Conflict detection** with red flash on duplicates
- **Key auto-repeat protection** *(v4.0.3)* — holding a key only triggers one click
- **Per-note usage counter** (× N) on the mapping list
- **Preset tabs** (common / drum)
- **Persistent H2 storage** (shaded — works under Lunar Client / Ichor)
- **Click options** — PICKUP / QUICK_MOVE / SWAP / ... + left / right / middle button (in 고급 설정)
- **Korean + English** locale

## Usage

1. Install the jar in your `mods/` folder (Fabric 1.20.1 + Fabric API required)
2. Join **PlanetEarth Server** (`planetearth.kr`)
3. Open a music chest (e.g. `악기 - 하프`) — the mod silently caches its slot layout
4. Press **`\`** (backslash, default menu key) → opens the Musix menu
5. Click **자동 매핑** → slots align to the current chest
6. Reopen the chest and press the mapped keys to play notes
7. Switching instruments? Auto-map again on the new chest
8. **Statistics**: menu → **통계** button · **MIDI**: menu → **고급 설정** → **MIDI 입력**

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

## MIDI input *(v4.0.0+)*

- **Menu → 고급 설정 → MIDI 입력** — scans connected MIDI devices, click to connect
- Auto-reconnects on next launch if the same device is still plugged in
- Async device scan / connection — won't freeze the client
- Works alongside keyboard input

## Statistics screen *(v4.1.0+)*

- **Session info**: start time, elapsed, total notes, average / current minute rate, last note
- **Line chart**: notes-per-minute over the last 60 minutes (auto-scaling Y axis)
- **TOP 5 notes**: horizontal bar chart of the most-played notes
- **Preset ratio**: common vs drum percentage + absolute counts
- **Reset button**: clears all counters and restarts the session timer

## Build

```sh
./gradlew build
```

Output: `build/libs/musix-<version>.jar`

GitHub Actions builds the jar automatically on every push and creates a Release on tag push (`v*`).

## Compatibility

- Minecraft **1.20.1**, Fabric Loader 0.15+, Fabric API
- **Lunar Client** (Ichor) verified — H2 shaded into jar to avoid ClassLoader isolation
- License: **CC-BY-NC-SA-4.0**

## License

This work is licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
You may share and adapt, but must give credit, use it non-commercially, and share derivatives under the same license.
