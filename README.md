# Musix

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Fabric](https://img.shields.io/badge/Loader-Fabric-orange)
![Client](https://img.shields.io/badge/Side-Client%20only-blue)

**🌐 Languages**: **English** (this page) · [한국어](README_KO.md)

> ⚠ **Server-specific mod** — only works on **PlanetEarth Server (`planetearth.kr`)**. Has no effect on any other server.

A client-side Fabric mod that lets you play PlanetEarth's chest-GUI based instruments (`음악-하프`, `악기 - 베이스(드럼)`, ...) with your keyboard. Press a key — the mod clicks the corresponding slot — the server plugin plays the note.

## Features

- **Keyboard input → slot click** (auto-active when chest title starts with `음악` or `악기`)
- **Two presets, auto-selected by chest title**
  - `common` — 49 notes (F#2 ~ F#6) for melodic instruments
  - `drum` — 9 notes (bass / hi-hat / snare, low/mid/high) for `(드럼)` chests
- **Auto-mapping** — open a chest once, click "Auto-map" in the menu, slots align to that chest's actual layout
- **In-game key rebinding** — left-click row to capture next key, right-click to clear, ESC to restore default. Shift / Alt combos supported
- **Conflict detection** with red flash on duplicates
- **Per-note usage counter** (× N) on the mapping list
- **Preset tabs** (common / drum)
- **Persistent H2 storage** (shaded — works under Lunar Client / Ichor)
- **Click options** — PICKUP / QUICK_MOVE / SWAP / ... + left / right / middle button
- **Korean + English** locale

## Usage

1. Install the jar in your `mods/` folder (Fabric 1.20.1 + Fabric API required)
2. Join **PlanetEarth Server** (`planetearth.kr`)
3. Open a music chest (e.g. `음악-하프`) — the mod silently caches its slot layout
4. Press **\\** (backslash, the default menu key), click **Auto-map** in the menu → slots align to that chest
5. Reopen the chest and press the mapped keys to play notes
6. Switching instruments? Auto-map again on the new chest

## Default key layout (common preset, 49 notes)

| Row | Keys | Notes |
|---|---|---|
| 1 row | `1 2 3 4 5 6 7 8 9 0 - =` | F#2 → F3 |
| Q row | `Q W E R T Y U I O P [ ]` | F#3 → F4 |
| A row | `A S D F G H J K L ; '` | F#4 → E5 |
| Z row | `Z X C V B N M , . /` | F5 → D6 |
| extra | `\` | D#6 |
| unbound | — | E6, F6, F#6 (set yourself in the menu) |

## Drum preset (auto when title contains `(드럼)`)

12 notes across 4 rows. Slots auto-aligned on chest open (item-name matching).

- `1 2 3` → bass high / mid / low
- `Q W E` → hi-hat high / mid / low
- `A S D` → snare high / mid / low
- `Z X C` → cymbal high / mid / low

Blocked slots (never mapped): `6, 19, 45, 53` — server GUI navigation slots.

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
