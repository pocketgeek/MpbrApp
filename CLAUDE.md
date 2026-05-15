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

**Build toolchain:** Kotlin 2.2.10 · AGP 9.2.1 · Gradle 9.4.1. Zero Android Studio warnings as of v1.27 — KTX functions used throughout, Kotlin stdlib for math, no unused params. Since Kotlin 2.x the Compose compiler ships as `org.jetbrains.kotlin.plugin.compose` (declared in `build.gradle.kts`); the old `composeOptions.kotlinCompilerExtensionVersion` block is no longer used. Note: the system JDK 25 causes Gradle to fail with a version-parse error — build from Android Studio (which uses its bundled JDK 17/21) instead.

## Architecture

The entire app lives in two files under `app/src/main/java/com/example/mpbr/`:

**`Ballistics.kt`** — pure Kotlin object, no Android dependencies. Contains:
- `AmmoPreset` data class and `PRESETS` list — factory ammo with name, MV, BC, weight, sight height, vital zone, drag model, and category
- `AmmoCategory` enum — `RIFLE`, `RIMFIRE`, `PISTOL`, `SHOTGUN`; defaults to `RIFLE` so only non-rifle presets need an explicit tag
- `ReticlePreset` data class and `RETICLE_PRESETS` list — scope reticle definitions (name, unit, majorSpacing, minorSpacing, vertExtent, style)
- `ReticleUnit` enum — `MIL`, `MOA`
- `ReticleStyle` enum — `HASH`, `DOT`, `CHRISTMAS_TREE`, `BDC`, `MRAD_TREE`, `CIRCLE_DOT`, `MOA_TREE`, `DRT`, `BRC`, `AR_BDC3`
- `Atmosphere` data class — ICAO pressure model + Magnus humidity correction; call `.densityRatio()` and `.speedOfSound()` for scaled values
- `simulate()` — 3D point-mass Euler integrator (x=downrange, y=vertical, z=lateral); dt=0.0005 s by default, 0.0002 s for the high-res final pass. Drag computed from air-relative velocity so crosswind enters the drag force naturally. Returns `List<TrajectoryPoint>`
- `calculateMpbr()` — binary-searches bore angle (50 iterations) until trajectory peak = `vitalZone/2`, then re-simulates at high resolution to extract near zero, far zero, max ordinate, MPBR, and trajectory table. Entry point for the UI
- `trajectoryTable()` — interpolates `TrajectoryPoint` list onto clean yard steps; computes holdover MOA/MIL and wind drift MOA/MIL for each row

**`MainActivity.kt`** — single `@Composable` function (`MpbrScreen`) with all state as `mutableStateOf` vars. No ViewModel, no architecture layers. Flow:
1. User picks an ammo preset → `applyPreset()` populates all fields and sets `selectedPreset`; any manual field edit calls `userEdit()` which resets `selectedPreset = null` (shows "Custom" in dropdown)
2. User optionally selects a reticle preset (`selectedReticle`) for the DOPE chart illustration
3. Calculate button → validates table start/end (0–2000 yd), calls `Ballistics.calculateMpbr()` with `tableMinYards`/`tableMaxYards`, stores result in `result` state
4. Result renders as: summary Card → reticle illustration Card (if reticle selected, via `buildReticleBitmap()`) → `TrajectoryTableCard` → Save DOPE Chart button

## Key conventions

**Adding ammo presets** — append to `Ballistics.PRESETS`. G7 model is specified as the 7th constructor argument (`DragModel.G7`); G1 is the default. BC passed to the constructor must match the drag model (do not mix G1 BCs with G7 model or vice versa). Always set the appropriate `category =` for non-rifle rounds (`RIMFIRE`, `PISTOL`, `SHOTGUN`); `RIFLE` is the default and needs no explicit tag. Keep presets grouped by category in the list — the dropdown inserts section headers by detecting category changes in order. Shotgun slugs use `sightHeightIn = 0.5` (bead) for smoothbore loads and `1.5` (scoped rifled barrel) for sabots; `vitalZoneIn = 8.0` for deer, `4.0` for buckshot/defensive.

**Trajectory table columns** — controlled by two booleans passed to `TrajectoryTableCard`: `showEnergy` (true when bullet weight > 0) and `showDrift` (true when wind speed != 0). When wind is 0 the W.MOA/W.MIL columns are hidden entirely.

**DOPE chart export** — `buildDopeChartBitmap()` draws a 1200 px wide JPEG-ready `Bitmap` using Android `Canvas` (no Compose rendering). Layout: header block → optional reticle section (640 px tall) → trajectory table. `saveDopeChart()` writes it to `Pictures/MPBR DOPE Charts/` via `MediaStore`. On API < 29 a `WRITE_EXTERNAL_STORAGE` runtime permission is requested first (declared in the manifest with `maxSdkVersion="28"`). The same `showEnergy` / `showDrift` booleans that drive the on-screen table also control which columns appear in the chart.

**Trajectory table range** — configurable via "Start / Step / End" fields in the UI (defaults 0 / 50 / 500 yd; start and end clamped 0–2000, step clamped 1–500). All three are validated (start < end) before calling `calculateMpbr()` with `tableStepYards`, `tableMinYards`, and `tableMaxYards`. The reticle callout code uses a circle-bounds check to show only ranges whose 2D position (elevation + drift) falls within the scope circle.

**Reticle illustration** — `drawReticleSection()` renders a clipped scope circle. Callouts are pre-computed before the clip as `ReticleCallout(x, y, color, label)` where `x = cx + drift*ppu` and `y = cy + holdover*ppu` — dots land at the bullet's actual 2D reticle position when wind is non-zero. The circle-bounds check is 2D (`dx²+dy² ≤ (R-margin)²`). Drawing paths by style:
- *`else`* (HASH/DOT/CHRISTMAS_TREE): evenly-spaced marks driven by `majorSpacing` / `minorSpacing`.
- *`BDC`*: thin crosshair + optional thick outer posts (`postStart`), windage hashes at `windageMarks`, holdover hash lines at `holdoverMarks`. Mark size = `ppu * 0.65f`.
- *`MRAD_TREE`*: numbered horizontal stadia + thick outer posts, 1 MRAD speed ring, ticked vertical stadia above center, dot-grid tree below (rows at `majorSpacing` MRAD).
- *`MOA_TREE`*: same concept as MRAD_TREE for MOA; 4 MOA major / 1 MOA minor, dot-grid tree at 2 MOA horizontal spacing, thick H posts at `postStart`, thick bottom V post.
- *`CIRCLE_DOT`*: large ring + cardinal tick marks drawn outside clip; center dot inside clip.
- *`DRT`*: two concentric rings (inner 6 MOA thick, outer 3 MOA thick) drawn outside clip; center dot inside clip.
- *`BRC`*: center dot + smaller holdunder dots from `holdoverMarks` + inward chevrons, all inside clip.

**Adding a BDC reticle preset** — append to `Ballistics.RETICLE_PRESETS` with `style = ReticleStyle.BDC`, `holdoverMarks`, `windageMarks`, `postStart` (0 = no thick posts). For SFP scopes, source subtensions from the manufacturer's reticle manual at the scope's maximum magnification. No drawing code changes needed.

**Adding an MRAD_TREE reticle preset** — append with `style = ReticleStyle.MRAD_TREE`, `majorSpacing = 1.0`, `minorSpacing = 0.5`, `vertExtent = <tree depth>`, `postStart = <MRAD where thick posts begin>`. No drawing code changes needed.

**Adding a MOA_TREE reticle preset** (Vortex EBR-7C style) — append with `style = ReticleStyle.MOA_TREE`, `majorSpacing = 4.0`, `minorSpacing = 1.0`, `vertExtent = <tree depth + majorSpacing>` (the extra majorSpacing becomes the bottom thick post gap), `postStart = <MOA where horizontal thick posts begin>`. The drawing produces: numbered H/V stadia, dot-grid tree (rows every `majorSpacing` MOA starting at `majorSpacing`; dots at 2 MOA spacing per row), and thick bottom post. No drawing code changes needed for this style.

**Adding an AR_BDC3 reticle preset** (broken-circle BDC, e.g. Vortex Strike Eagle) — append with `style = ReticleStyle.AR_BDC3`, `majorSpacing = <circle radius MOA>`, `minorSpacing = <center dot radius MOA>`, `holdoverMarks = listOf(...)`, `vertExtent = <slightly larger than 600-yd holdover>`. The broken circle (4 × 60° arcs with 30° gaps at cardinals) is drawn outside the clip; the vertical post and labeled holdover tick marks are inside the clip. Labels are hardcoded as "3"/"4"/"5"/"6" for hundreds of yards. No drawing code changes needed for this style.

**Adding a BRC reticle preset** (Bullet Rise Compensating, e.g. Viridian MDS25) — append with `style = ReticleStyle.BRC`, `minorSpacing = <center dot radius MOA>`, `holdoverMarks = listOf(<15yd holdunder MOA>, <7yd holdunder MOA>)`, `vertExtent` large enough to show all dots. The drawing hardcodes chevron geometry (tip at ±20 MOA, arms to ±35/±10 MOA). Dot positions must be sourced from manufacturer; the Viridian values are estimated from HOB physics since no official MOA spec is published. No drawing code changes needed.

**Adding a DRT reticle preset** (dual-ring tactical, e.g. Vortex Spitfire) — append with `style = ReticleStyle.DRT`, `majorSpacing = <inner ring center radius MOA>`, `minorSpacing = <dot radius MOA>`, `postStart = <outer ring center radius MOA>`, `vertExtent = <~18% larger than outer ring center radius>`. Both rings are drawn outside the clip in `drawReticleSection()` at stroke widths derived from MOA thickness (inner = 6 MOA, outer = 3 MOA hardcoded for the DRT style). No drawing code changes needed.

**Adding a CIRCLE_DOT reticle preset** (red dot sights) — append with `style = ReticleStyle.CIRCLE_DOT`, `majorSpacing = <ring radius in unit>`, `minorSpacing = <dot radius in unit>`, `vertExtent = <~25% larger than ring radius so the ring sits at ~80% of scope radius with a visible gap from the outer border>`. The drawing automatically adds cardinal tick marks at 12/3/6/9 o'clock (±10% of ring radius each side). The ring and ticks are drawn outside the clip for guaranteed visibility. Subtensions on 1× sights are always accurate. No drawing code changes needed.

**Atmospheric defaults** — set in the `mutableStateOf` initializers in `MainActivity.kt`: 2231 ft (Parma, ID), 70°F, 25% RH, 0 mph wind.

**Sign conventions**:
- `dropInches` — positive = bullet below LOS (need to hold over)
- `holdoverMoa/Mil` — positive = hold over
- `driftInches/Moa/Mil` — positive = bullet drifts downwind (left-to-right for positive wind input)
- Wind input is full-value crosswind in mph; user is responsible for clock-position adjustment
