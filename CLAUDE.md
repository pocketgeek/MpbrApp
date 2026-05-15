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
- `ReticleStyle` enum — `HASH`, `DOT`, `CHRISTMAS_TREE`
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

**Reticle illustration** — `drawReticleSection()` renders a clipped scope circle with crosshair, hash/dot/Christmas-tree marks, and dashed callout lines to range+holdover labels. Marks are spaced at `minorSpacing` units; every `majorSpacing/minorSpacing`-th mark is drawn thicker. Callouts iterate the trajectory table and skip any label whose y-position is within 82% of text height of the previous drawn label (overlap prevention). Adding a new reticle preset: append to `Ballistics.RETICLE_PRESETS` — no drawing code changes needed.

**Atmospheric defaults** — set in the `mutableStateOf` initializers in `MainActivity.kt`: 2231 ft (Parma, ID), 70°F, 25% RH, 0 mph wind.

**Sign conventions**:
- `dropInches` — positive = bullet below LOS (need to hold over)
- `holdoverMoa/Mil` — positive = hold over
- `driftInches/Moa/Mil` — positive = bullet drifts downwind (left-to-right for positive wind input)
- Wind input is full-value crosswind in mph; user is responsible for clock-position adjustment
