# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Lint
./gradlew lint

# Clean
./gradlew clean
```

There are no unit tests in this project. All logic is in two Kotlin files that can be verified by running the app.

## Architecture

The entire app lives in two files under `app/src/main/java/com/example/mpbr/`:

**`Ballistics.kt`** — pure Kotlin object, no Android dependencies. Contains:
- `AmmoPreset` data class and `PRESETS` list — factory ammo with name, MV, BC, weight, sight height, vital zone, drag model, and category
- `AmmoCategory` enum — `RIFLE`, `RIMFIRE`, `PISTOL`, `SHOTGUN`; defaults to `RIFLE` so only non-rifle presets need an explicit tag
- `ReticlePreset` data class and `RETICLE_PRESETS` list — scope reticle definitions (name, unit, majorSpacing, minorSpacing, vertExtent, style)
- `ReticleUnit` enum — `MIL`, `MOA`
- `ReticleStyle` enum — `HASH`, `DOT`, `CHRISTMAS_TREE`, `BDC`, `MRAD_TREE`, `CIRCLE_DOT`
- `Atmosphere` data class — ICAO pressure model + Magnus humidity correction; call `.densityRatio()` and `.speedOfSound()` for scaled values
- `simulate()` — 3D point-mass Euler integrator (x=downrange, y=vertical, z=lateral); dt=0.0005 s by default, 0.0002 s for the high-res final pass. Drag computed from air-relative velocity so crosswind enters the drag force naturally. Returns `List<TrajectoryPoint>`
- `calculateMpbr()` — binary-searches bore angle (50 iterations) until trajectory peak = `vitalZone/2`, then re-simulates at high resolution to extract near zero, far zero, max ordinate, MPBR, and trajectory table. Entry point for the UI
- `trajectoryTable()` — interpolates `TrajectoryPoint` list onto clean yard steps; computes holdover MOA/MIL and wind drift MOA/MIL for each row

**`MainActivity.kt`** — single `@Composable` function (`MpbrScreen`) with all state as `mutableStateOf` vars. No ViewModel, no architecture layers. Flow:
1. User picks an ammo preset → `applyPreset()` populates all fields and sets `selectedPreset`; any manual field edit calls `userEdit()` which resets `selectedPreset = null` (shows "Custom" in dropdown)
2. User optionally selects a reticle preset (`selectedReticle`) for the DOPE chart illustration
3. Calculate button → calls `Ballistics.calculateMpbr()`, stores result in `result` state
4. Result renders as a summary Card + `TrajectoryTableCard` + Save DOPE Chart button

## Key conventions

**Adding ammo presets** — append to `Ballistics.PRESETS`. G7 model is specified as the 7th constructor argument (`DragModel.G7`); G1 is the default. BC passed to the constructor must match the drag model (do not mix G1 BCs with G7 model or vice versa). Always set the appropriate `category =` for non-rifle rounds (`RIMFIRE`, `PISTOL`, `SHOTGUN`); `RIFLE` is the default and needs no explicit tag. Keep presets grouped by category in the list — the dropdown inserts section headers by detecting category changes in order. Shotgun slugs use `sightHeightIn = 0.5` (bead) for smoothbore loads and `1.5` (scoped rifled barrel) for sabots; `vitalZoneIn = 8.0` for deer, `4.0` for buckshot/defensive.

**Trajectory table columns** — controlled by two booleans passed to `TrajectoryTableCard`: `showEnergy` (true when bullet weight > 0) and `showDrift` (true when wind speed != 0). When wind is 0 the W.MOA/W.MIL columns are hidden entirely.

**DOPE chart export** — `buildDopeChartBitmap()` draws a 1200 px wide JPEG-ready `Bitmap` using Android `Canvas` (no Compose rendering). Layout: header block → optional reticle section (640 px tall) → trajectory table. `saveDopeChart()` writes it to `Pictures/MPBR DOPE Charts/` via `MediaStore`. On API < 29 a `WRITE_EXTERNAL_STORAGE` runtime permission is requested first (declared in the manifest with `maxSdkVersion="28"`). The same `showEnergy` / `showDrift` booleans that drive the on-screen table also control which columns appear in the chart.

**Trajectory table range** — `calculateMpbr()` is called with `tableMaxYards = 1000` (50 yd steps), producing 20 rows. The reticle callout code shows only ranges whose holdover fits within the reticle's `vertExtent`.

**Reticle illustration** — `drawReticleSection()` renders a clipped scope circle. Callouts are pre-computed before the clip (so the same color is used both inside and outside). Three drawing paths:
- *Hash/Dot/Christmas-tree* (`else` branch): evenly-spaced marks driven by `majorSpacing` / `minorSpacing`. Colored trajectory ticks drawn inside clip; labels outside.
- *BDC*: thin crosshair + optional thick outer posts (`postStart`), windage hashes at `windageMarks`, holdover hash lines at `holdoverMarks`. Mark height/width = `ppu * 0.65f` (scales with unit, prevents overlap for dense mark lists).
- *MRAD_TREE* (EOTech-style): numbered horizontal stadia with major/minor ticks + thick outer posts, 1 MRAD speed ring, short vertical stadia above center, dot-grid Christmas tree below center (rows `treeStart`..`vertExtent.toInt()`).

**Adding a BDC reticle preset** — append to `Ballistics.RETICLE_PRESETS` with `style = ReticleStyle.BDC`, `holdoverMarks`, `windageMarks`, `postStart` (0 = no thick posts). For SFP scopes, source subtensions from the manufacturer's reticle manual at the scope's maximum magnification. No drawing code changes needed.

**Adding an MRAD_TREE reticle preset** — append with `style = ReticleStyle.MRAD_TREE`, `majorSpacing = 1.0`, `minorSpacing = 0.5`, `vertExtent = <tree depth>`, `postStart = <MRAD where thick posts begin>`. No drawing code changes needed.

**Adding a CIRCLE_DOT reticle preset** (red dot sights) — append with `style = ReticleStyle.CIRCLE_DOT`, `majorSpacing = <ring radius in unit>`, `minorSpacing = <dot radius in unit>`, `vertExtent = <~25% larger than ring radius so the ring sits at ~80% of scope radius with a visible gap from the outer border>`. The drawing automatically adds cardinal tick marks at 12/3/6/9 o'clock (±10% of ring radius each side). The ring and ticks are drawn outside the clip for guaranteed visibility. Subtensions on 1× sights are always accurate. No drawing code changes needed.

**Atmospheric defaults** — set in the `mutableStateOf` initializers in `MainActivity.kt`: 2231 ft (Parma, ID), 70°F, 25% RH, 0 mph wind.

**Sign conventions**:
- `dropInches` — positive = bullet below LOS (need to hold over)
- `holdoverMoa/Mil` — positive = hold over
- `driftInches/Moa/Mil` — positive = bullet drifts downwind (left-to-right for positive wind input)
- Wind input is full-value crosswind in mph; user is responsible for clock-position adjustment
