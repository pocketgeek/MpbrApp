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

**Build toolchain:** Kotlin 2.2.10 · AGP 9.2.1 · Gradle 9.4.1. Zero Android Studio warnings as of v1.75 — KTX functions used throughout, Kotlin stdlib for math, no unused params. Since Kotlin 2.x the Compose compiler ships as `org.jetbrains.kotlin.plugin.compose` (declared in `build.gradle.kts`); the old `composeOptions.kotlinCompilerExtensionVersion` block is no longer used. AGP 9 provides built-in Kotlin support — the explicit `org.jetbrains.kotlin.android` plugin and `android.builtInKotlin=false` property are not used. Note: the system JDK 25 causes Gradle to fail with a version-parse error — build from Android Studio (which uses its bundled JDK 17/21) instead.

## Architecture

The entire app lives in two files under `app/src/main/java/com/example/mpbr/`:

**`Ballistics.kt`** — pure Kotlin object, no Android dependencies. Contains:
- `AmmoPreset` data class and `PRESETS` list — factory ammo with name, MV, BC, weight, sight height, vital zone, drag model, category, and caliber (drives the Type → Caliber → Load picker in the UI)
- `AmmoCategory` enum — `RIFLE`, `RIMFIRE`, `PISTOL`, `SHOTGUN`; defaults to `RIFLE` so only non-rifle presets need an explicit tag
- `ReticlePreset` data class and `RETICLE_PRESETS` list — scope reticle definitions (name, unit, majorSpacing, minorSpacing, vertExtent, style)
- `ReticleUnit` enum — `MIL`, `MOA`
- `ReticleStyle` enum — `HASH`, `DOT`, `CHRISTMAS_TREE`, `BDC`, `MRAD_TREE`, `CIRCLE_DOT`, `MOA_TREE`, `DRT`, `BRC`, `AR_BDC3`, `CIRCLE_BDC`, `DUPLEX`, `BALLISTIC_E3`, `SIG_FL4`, `ACOG_CHEVRON`, `ACOG_DONUT`, `CHEVRON_BDC`, `LEAD_RINGS`, `SPIDER_SIGHT`, `HORSESHOE_BDC`, `RING_BDC`
- `RETICLE_PRESETS` list is sorted by manufacturer (Burris → EOTech → Firefield → German/Czech → Holosun → Leupold → SIG → Trijicon → U.S. Army → UUQ → Viridian → Vortex)
- `Atmosphere` data class — ICAO pressure model + Magnus humidity correction; call `.densityRatio()` and `.speedOfSound()` for scaled values
- `simulate()` — 3D point-mass Euler integrator (x=downrange, y=vertical, z=lateral); dt=0.0005 s by default, 0.0002 s for the high-res final pass. Drag computed from air-relative velocity so crosswind enters the drag force naturally. Returns `List<TrajectoryPoint>`
- `calculateMpbr()` — binary-searches bore angle (50 iterations) until trajectory peak = `vitalZone/2`, then re-simulates at high resolution to extract near zero, far zero, max ordinate, MPBR, and trajectory table. Entry point for the UI. Requires muzzle velocity ≥ 400 fps and that the trajectory actually reaches a far zero — below 400 fps the flat-fire assumption breaks down (the bullet decelerates below its drag-limited terminal fall speed before traveling far, so reported velocity can climb with range instead of decaying, and drop/holdover blow up to thousands of MOA); throws `IllegalArgumentException` with a user-facing message instead of returning nonsense values
- `trajectoryTable()` — interpolates `TrajectoryPoint` list onto clean yard steps; computes holdover MOA/MIL and wind drift MOA/MIL for each row

**`MainActivity.kt`** — single `@Composable` function (`MpbrScreen`) with all state as `mutableStateOf` vars. No ViewModel, no architecture layers. Flow:
1. User picks an ammo preset via the three-step Type → Caliber → Load cascade (`AmmoPresetDropdown`/`SimpleDropdown` in `MainActivity.kt`) → `applyPreset()` populates all fields and sets `selectedPreset`; any manual field edit calls `userEdit()` which resets `selectedPreset = null` (shows "Custom" in the Type picker)
2. User optionally selects a reticle preset (`selectedReticle`) for the DOPE chart illustration
3. Calculate button → validates table start/end (0–2000 yd), calls `Ballistics.calculateMpbr()` with `tableMinYards`/`tableMaxYards`, stores result in `result` state
4. Result renders as: summary Card → reticle illustration Card (if reticle selected, via `buildReticleBitmap()`) → `TrajectoryTableCard` → Save DOPE Chart button

**Session save/load** — Save icon (floppy disk) and Load icon (folder) appear in the title row. Sessions persist all input state (ammo fields, reticle, atmosphere, table range, DOPE title, notes) to `SharedPreferences` ("mpbr_sessions" key) as a JSON array. `SessionData` data class and `saveSession`/`loadSessions`/`deleteSession`/`buildSessionName` helpers live at the bottom of `MainActivity.kt`. Save dialog pre-fills name as `"<PresetName> — MM/dd"` (editable); saving a session with an existing name overwrites it. Load dialog lists all sessions — tap a name to restore all fields (result is cleared), tap ✕ to delete.

## Key conventions

**Adding ammo presets** — append to `Ballistics.PRESETS`. G7 model is specified as the 7th constructor argument (`DragModel.G7`); G1 is the default. BC passed to the constructor must match the drag model (do not mix G1 BCs with G7 model or vice versa). Always set the appropriate `category =` for non-rifle rounds (`RIMFIRE`, `PISTOL`, `SHOTGUN`); `RIFLE` is the default and needs no explicit tag. Always set `caliber = "..."` (named arg) — this drives the Caliber step of the Type → Caliber → Load picker, so presets sharing a caliber must use an identical string. Interchangeable civilian/mil-designation cartridges share one caliber string: `.223 Rem` and `5.56×45` (`M193`/`M855`/`M855A1`/`Mk262`) both use `caliber = "223 Rem/5.56"`; `.308 Win` and `7.62×51 NATO` (`M80`) both use `caliber = "308 Win/7.62x51"`. Shotgun presets use the bare gauge as caliber (`"12ga"`, `"20ga"`, `"410"`) regardless of wad/slug type. Keep presets grouped by category in the list — new presets no longer need to worry about dropdown section-header ordering since the picker groups by `category` and `caliber` fields directly, not list position. Shotgun slugs use `sightHeightIn = 0.5` (bead) for smoothbore loads and `1.5` (scoped rifled barrel) for sabots; `vitalZoneIn = 8.0` for deer, `4.0` for buckshot/defensive.

**Trajectory table columns** — controlled by two booleans passed to `TrajectoryTableCard`: `showEnergy` (true when bullet weight > 0) and `showDrift` (true when wind speed != 0). When wind is 0 the W.MOA/W.MIL columns are hidden entirely.

**DOPE chart export** — `buildDopeChartBitmap()` draws a 1200 px wide JPEG-ready `Bitmap` using Android `Canvas` (no Compose rendering). Layout: header block → optional reticle section (640 px tall) → trajectory table → optional notes section. `saveDopeChart()` writes it to `Pictures/MPBR DOPE Charts/` via `MediaStore`. On API < 29 a `WRITE_EXTERNAL_STORAGE` runtime permission is requested first (declared in the manifest with `maxSdkVersion="28"`). The same `showEnergy` / `showDrift` booleans that drive the on-screen table also control which columns appear in the chart.

**Trajectory table range** — configurable via "Start / Step / End" fields in the UI (defaults 0 / 50 / 500 yd; start and end clamped 0–2000, step clamped 1–500). All three are validated (start < end) before calling `calculateMpbr()` with `tableStepYards`, `tableMinYards`, and `tableMaxYards`. The reticle callout code uses a circle-bounds check to show only ranges whose 2D position (elevation + drift) falls within the scope circle.

**Reticle illustration** — `drawReticleSection()` renders a clipped scope circle. Callouts are pre-computed before the clip as `ReticleCallout(x, y, color, label)` where `x = cx + drift*ppu` and `y = cy + holdover*ppu` — dots land at the bullet's actual 2D reticle position when wind is non-zero. The circle-bounds check is 2D (`dx²+dy² ≤ (R-margin)²`). Drawing paths by style:
- *`else`* (HASH/DOT/CHRISTMAS_TREE): evenly-spaced marks driven by `majorSpacing` / `minorSpacing`.
- *`BDC`*: thin crosshair + optional thick outer posts (`postStart`), windage hashes at `windageMarks`, holdover hash lines at `holdoverMarks`. Mark size = `ppu * 0.65f`.
- *`MRAD_TREE`*: numbered horizontal stadia + thick outer posts, 1 MRAD speed ring, ticked vertical stadia above center, dot-grid tree below (rows at `majorSpacing` MRAD).
- *`MOA_TREE`*: same concept as MRAD_TREE for MOA; 4 MOA major / 1 MOA minor, dot-grid tree at 2 MOA horizontal spacing, thick H posts at `postStart`, thick bottom V post.
- *`CIRCLE_DOT`*: one or more concentric rings + cardinal tick marks on outermost ring, drawn outside clip; center dot inside clip. If `holdoverMarks` is non-empty each entry is drawn as a ring (radii in the reticle's unit); otherwise `majorSpacing` is used as the single ring radius.
- *`DRT`*: two concentric rings (inner 6 MOA thick, outer 3 MOA thick) drawn outside clip; center dot inside clip.
- *`BRC`*: center dot + smaller holdunder dots from `holdoverMarks` + inward chevrons, all inside clip.
- *`SIG_FL4`*: thin crosshair; thick H posts (`postStart` = half-height, `minorSpacing` = inner gap); above-center tick marks at `minorSpacing` and `majorSpacing` MOA; BDC stadia at `holdoverMarks` (equal 0.75 MOA half-width); windage hash marks (white, over the thick arm) at `windageMarks`; narrow downward triangle (15° full apex) from last holdover mark to 20.22 MOA below center (hardcoded from SIG diagram). `vertExtent = 22.68`.
- *`ACOG_CHEVRON`*: upward-pointing ∧ chevron (arms from tip at center to base at `minorSpacing` MOA below); thin V post above tip and below base; BDC stadia at `holdoverMarks` with widths narrowing from ±2.08 to ±1.04 MOA (hardcoded for 19" ranging). `majorSpacing` = chevron base half-width.
- *`ACOG_DONUT`*: illuminated ring at center (`majorSpacing` = ring radius) + center fill dot (`minorSpacing` = dot radius); thin V post above and below ring; BDC stadia at `holdoverMarks` with widths narrowing from ±2.08 to ±1.04 MOA (hardcoded for 19" ranging).
- *`CHEVRON_BDC`*: two separate H arm segments with gap at center (`windageMarks[0]` = inner edge, `windageMarks[last]` = outer edge, uniform ticks at all positions); V post from center downward only (no line above); 1 MOA minor ticks on V post, labeled at `holdoverMarks` integers; upward chevron (∧) at center (tip at center, base at `minorSpacing` MOA below, half-width `majorSpacing` MOA).
- *`LEAD_RINGS`*: thin full crosshair + concentric rings at `holdoverMarks` radii (MOA); each ring labeled "N mph" at 45° top-right using `majorSpacing` as the mph step (e.g. 10 → labels "10 mph"/"20 mph"/etc.).
- *`SPIDER_SIGHT`*: outer ring (`majorSpacing` MOA radius, drawn outside clip) + 4 crosshair spokes from `minorSpacing` to `majorSpacing` + center ring at `minorSpacing` + beads on all 4 spokes at `windageMarks[0]` radius + 4 diagonal tick marks (9% of outer radius) inward at ±45° on outer ring.
- *`HORSESHOE_BDC`*: 300° arc open at bottom (60° gap, drawn outside clip) + center dot inside clip + thin vertical post from arc bottom through gap + BDC tick marks at `holdoverMarks` depths + thick bottom post to scope edge. Same parameter layout as `CIRCLE_BDC`: `majorSpacing` = arc radius, `minorSpacing` = dot radius, `holdoverMarks` = holdover depths below center.

**Results summary card** — shows Near Zero, Far Zero, Max Ordinate, MPBR, Energy at MPBR (if bullet weight > 0), Velocity/Energy at Near Zero, Velocity/Energy at Far Zero, Bore Angle. All computed by interpolating the high-res trajectory at the crossing points.

**DOPE chart header** — `buildDopeChartBitmap()` accepts `vitalZoneIn` and prints it on the Max Ordinate line. Call site passes `vitalZone.toDoubleOrNull() ?: 6.0`.

**DOPE chart notes** — `buildDopeChartBitmap()` accepts a `notes: String` parameter (default `""`). When non-blank, a separator rule + bold "Notes:" label + the note lines are appended after the trajectory table. Each `\n` in the string becomes a separate rendered line. The `notes` parameter is sourced from the `dopeNotes` state var (a multiline `OutlinedTextField` labeled "Notes / Turret Adjustments" that appears below the DOPE Card Title field). Saved and restored as `dopeNotes` in `SessionData` / JSON; old sessions without the key load as `""` via `optString`.

**Adding a BDC reticle preset** — append to `Ballistics.RETICLE_PRESETS` with `style = ReticleStyle.BDC`, `holdoverMarks`, `windageMarks`, `postStart` (0 = no thick posts). For SFP scopes, source subtensions from the manufacturer's reticle manual at the scope's maximum magnification. If `minorSpacing > 0`, fine horizontal ticks are drawn at that spacing across the H arm up to `postStart`. No other drawing code changes needed.

**Adding an MRAD_TREE reticle preset** — append with `style = ReticleStyle.MRAD_TREE`, `majorSpacing = 1.0`, `minorSpacing = 0.5`, `vertExtent = <tree depth + majorSpacing>` (the extra majorSpacing becomes the bottom thick post gap, same convention as MOA_TREE), `postStart = <MRAD where thick posts begin>`. Tree rows start at `majorSpacing` MRAD below center (row 1, not row 0) and run to `vertExtent - majorSpacing`, matching how manufacturer dot-grid tree reticles are numbered. No drawing code changes needed.

**Adding a MOA_TREE reticle preset** (Vortex EBR-7C style) — append with `style = ReticleStyle.MOA_TREE`, `majorSpacing = 4.0`, `minorSpacing = 1.0`, `vertExtent = <tree depth + majorSpacing>` (the extra majorSpacing becomes the bottom thick post gap), `postStart = <MOA where horizontal thick posts begin>`. The drawing produces: numbered H/V stadia, dot-grid tree (rows every `majorSpacing` MOA starting at `majorSpacing`; dots at 2 MOA spacing per row), and thick bottom post. No drawing code changes needed for this style.

**Adding a BALLISTIC_E3 reticle preset** (Burris Ballistic E3 style) — append with `style = ReticleStyle.BALLISTIC_E3`, `majorSpacing = 4.0` (tick count / horizontal thin half-width in MOA), `minorSpacing = <bar half-height MOA>`, `holdoverMarks = listOf(<200yd MOA>, <300yd MOA>, <400yd MOA>)`, `postStart = 4.0` (where horizontal thick bars begin), `vertExtent = 40.0` (wide FOV so BDC cluster is compact). Drawing structure: thin vertical from scope top → D=1 MOA thick stub above center → thick horizontal bars at center level → thin section with ticks → BDC marks (widths 1.5/2.5/3.5 MOA, dots 1.54/2.42/3.38 MOA hardcoded) → thick bottom post. Source: Burris E3 subtension diagram.

**Adding a DUPLEX reticle preset** (duplex/Plex, e.g. Burris Fullfield) — append with `style = ReticleStyle.DUPLEX`, `majorSpacing = <gap from center to thick post inner face in MOA>`, `minorSpacing = <post half-height in MOA>`, `vertExtent = <slightly larger than outer post extent>`. The four thick posts are drawn as filled trapezoids inside the clip (wide at scope edge, tapering to the thin crosshair width at `gapPx`). The circular clip automatically rounds the outer post corners. Source subtension values from Burris Plex diagram: A (gap), B (half-height), W (outer extent). No drawing code changes needed.

**Adding a CIRCLE_BDC reticle preset** (circle + BDC post, e.g. Firefield RapidStrike) — append with `style = ReticleStyle.CIRCLE_BDC`, `majorSpacing = <circle radius MOA>`, `minorSpacing = <dot radius MOA>`, `holdoverMarks = listOf(...)`, `vertExtent = <~25% larger than last holdover MOA>`. The circle is drawn outside the clip; inside: center dot, thin post from circle bottom to first holdover, tick marks with range labels ("300"/"400"/"500"/"600" hardcoded), then thick bottom post to scope edge. No drawing code changes needed.

**Adding an AR_BDC3 reticle preset** (horseshoe BDC, e.g. Vortex Strike Eagle) — append with `style = ReticleStyle.AR_BDC3`, `majorSpacing = <horseshoe radius MOA>`, `minorSpacing = <center dot radius MOA>`, `holdoverMarks = listOf(...)`, `vertExtent = <large enough that the outermost 2D point (outer windage at farthest range) fits; use √(maxHorizMOA² + maxVertMOA²) × 1.3>`. The horseshoe (top arc ~120° + two side hooks ~35° each, open at bottom) is drawn outside the clip. Inside: center dot, vertical BDC post, windage dot rows widening at 1 MOA spacing to the 15 mph holdover, range labels, and a "10" mph label on the last row. Labels are hardcoded as "3"/"4"/"5"/"6" for hundreds of yards; windage extents and "10" mph position are also hardcoded — update the drawing code to change them. No drawing code changes needed if only adding a new preset with different holdover positions.

**Adding a BRC reticle preset** (Bullet Rise Compensating, e.g. Viridian MDS25) — append with `style = ReticleStyle.BRC`, `minorSpacing = <center dot radius MOA>`, `holdoverMarks = listOf(<15yd holdunder MOA>, <7yd holdunder MOA>)`, `vertExtent` large enough to show all dots. The drawing hardcodes chevron geometry (tip at ±20 MOA, arms to ±35/±10 MOA). Dot positions must be sourced from manufacturer; the Viridian values are estimated from HOB physics since no official MOA spec is published. No drawing code changes needed.

**Adding a DRT reticle preset** (dual-ring tactical, e.g. Vortex Spitfire) — append with `style = ReticleStyle.DRT`, `majorSpacing = <inner ring center radius MOA>`, `minorSpacing = <dot radius MOA>`, `postStart = <outer ring center radius MOA>`, `vertExtent = <~18% larger than outer ring center radius>`. Both rings are drawn outside the clip in `drawReticleSection()` at stroke widths derived from MOA thickness (inner = 6 MOA, outer = 3 MOA hardcoded for the DRT style). No drawing code changes needed.

**Adding a CIRCLE_DOT reticle preset** (red dot sights) — append with `style = ReticleStyle.CIRCLE_DOT`, `majorSpacing = <outermost ring radius in unit>`, `minorSpacing = <dot radius in unit>` (set to `0.0` to suppress the center dot entirely), `vertExtent = <~25% larger than outermost ring radius>`. For multi-ring sights (e.g. Holosun 507 COMP CRS) set `holdoverMarks = listOf(<r1>, <r2>, ..., <outerR>)` with each ring radius; cardinal ticks land on the outermost ring. For single-ring sights leave `holdoverMarks` empty — `majorSpacing` is used as the sole ring radius. Rings and ticks are drawn outside the clip for guaranteed visibility. Subtensions on 1× sights are always accurate. No drawing code changes needed.

**Adding a SIG_FL4 reticle preset** — append with `style = ReticleStyle.SIG_FL4`, `majorSpacing = <upper above-center tick height MOA>`, `minorSpacing = <lower above-center tick height MOA>`, `vertExtent = <scope top extent above center MOA>`, `postStart = <H post half-height MOA>`, `holdoverMarks = listOf(...)` (BDC depths below center), `windageMarks = listOf(...)` (H arm windage positions). The H post inner gap (1.59 MOA), center crosshair half-height (0.90 MOA), and triangle bottom (20.22 MOA below center) are hardcoded from the SIG Tango SPR p.17 diagram. Windage marks are drawn as white notches over the thick arm. No drawing code changes needed for new presets of this style.

**Adding an ACOG_DONUT reticle preset** — append with `style = ReticleStyle.ACOG_DONUT`, `majorSpacing = <ring radius MOA>`, `minorSpacing = <center dot radius MOA>`, `holdoverMarks = listOf(...)` (BDC stadia depths), `vertExtent = <large enough for all stadia>`. BDC stadia widths are hardcoded as ±2.08/1.66/1.39/1.19/1.04 MOA (derived from 19" shoulder width at 300/400/500/600/700/800m). No drawing code changes needed.

**Adding a HORSESHOE_BDC reticle preset** (horseshoe arc + BDC post, e.g. Firefield CR1) — append with `style = ReticleStyle.HORSESHOE_BDC`, `majorSpacing = <arc radius MOA>`, `minorSpacing = <dot radius MOA>`, `holdoverMarks = listOf(...)` (holdover depths below center; 300-yd holdover is implicitly at the arc bottom = `majorSpacing`), `windageMarks = listOf(...)` (half-width in MOA for each corresponding hash — typically IPSC shoulder width / 2 at that range; if shorter than holdoverMarks a fixed fallback is used), `vertExtent = <~30% larger than last holdover MOA>`. The arc is a 300° sweep with a 60° gap at the bottom (startAngle 120°, sweep 300° in Android coords); drawn outside the clip. Inside: center dot, thin post from arc bottom through the gap to last holdover, BDC ticks at variable widths, thick post to scope edge. No drawing code changes needed.

**Adding a SPIDER_SIGHT reticle preset** (ring + spokes + beads, e.g. ZB26/ZB30 AA sight) — append with `style = ReticleStyle.SPIDER_SIGHT`, `majorSpacing = <outer ring radius MOA>`, `minorSpacing = <center ring radius MOA>`, `windageMarks = listOf(<bead radius MOA>)` (beads on all 4 spokes), `vertExtent = <outer ring radius × 1.2>`. Outer ring radius for machine gun AA sights: `V_target_mps / V_bullet_mps × 3438 MOA` (range-independent). No drawing code changes needed.

**Adding a LEAD_RINGS reticle preset** (concentric lead rings, e.g. U.S. Bazooka D7161556) — append with `style = ReticleStyle.LEAD_RINGS`, `majorSpacing = <mph per ring step>` (10 for 10/20/30/40 mph rings), `holdoverMarks = listOf(...)` (ring radii in MOA), `vertExtent = <outermost ring radius + ~10% margin>`. Drawing: thin full crosshair + one circle per holdoverMark; each labeled "N mph" at top-right 45°. The ring radii for rocket/cannon weapons can be computed as `V_mph × (1.4667 / muzzle_fps) × 3438 MOA` (range-independent). No drawing code changes needed.

**Adding a RING_BDC reticle preset** (full ring + crosshair + lead marks + BDC ticks, e.g. Leupold MOA-Ring) — append with `style = ReticleStyle.RING_BDC`, `majorSpacing = <ring radius MOA>`, `minorSpacing = <center circle radius MOA>`, `holdoverMarks = listOf(...)` (fine BDC tick depths below center in MOA), `windageMarks = listOf(...)` (lead/windage tick positions on H arm in MOA), `postStart = <MOA depth where heavy tapered post begins>`, `vertExtent = <~40% larger than postStart>`. Drawing: ring at `majorSpacing` outside clip; inside: full thin crosshair, small open center circle at `minorSpacing`, vertical lead ticks on H arm at `±windageMarks`, fine horizontal BDC ticks at `holdoverMarks` (0.8 MOA half-width each side), filled trapezoid post from `postStart` (4.5 MOA half-width) to scope edge (8.9 MOA half-width). SFP scopes: source all subtensions at max magnification.

**Adding a CHEVRON_BDC reticle preset** (upward chevron + BDC post, e.g. UUQ Ranger ER) — append with `style = ReticleStyle.CHEVRON_BDC`, `majorSpacing = <chevron base half-width MOA>`, `minorSpacing = <chevron tip-to-base depth MOA>`, `holdoverMarks = listOf(...)` (BDC labeled tick depths — integers), `windageMarks = listOf(innerEdge, tick..., outerEdge)` (H arm structure: first = arm inner edge MOA, last = arm outer edge MOA, middle entries = interior ticks), `vertExtent = <last holdover MOA + 3 or more>`. Drawing: two separate H arm segments (NOT a continuous crosshair, gap at center); V post below chevron tip only; 1 MOA minor ticks on V post with labeled ticks wider at holdoverMarks; upward chevron at center. For fixed-magnification prisms subtensions are always accurate. No drawing code changes needed.

**Atmospheric defaults** — set in the `mutableStateOf` initializers in `MainActivity.kt`: 2231 ft (Parma, ID), 70°F, 25% RH, 0 mph wind.

**Sign conventions**:
- `dropInches` — positive = bullet below LOS (need to hold over)
- `holdoverMoa/Mil` — positive = hold over
- `driftInches/Moa/Mil` — positive = bullet drifts downwind (left-to-right for positive wind input)
- Wind input is full-value crosswind in mph; user is responsible for clock-position adjustment
