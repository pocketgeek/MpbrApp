# MPBR Calculator

An Android app that computes Maximum Point Blank Range (MPBR), modeled after
shooterscalculator.com's MPBR tool. Uses a point-mass exterior ballistics
simulator with G1 or G7 drag, and supports altitude / temperature / humidity
atmospheric corrections and full-value crosswind drift.

Includes 305 factory ammo presets organized into color-coded categories
(rifle, rimfire, pistol, shotgun) with a configurable trajectory table (50 yd
steps, default 50–500 yd, max 2,000 yd). DOPE charts can be saved as a JPEG
to your gallery, optionally with a scope reticle illustration showing
color-coded holdover callouts — positioned at the bullet's actual 2D location
on the reticle (elevation + crosswind drift) for every range that fits within
the reticle's extent.

## Screenshots

<table>
<tr>
<td align="center"><img src="screenshots/Screenshot_20260516-001331.MPBR%20Calculator.png" width="190"><br><em>Main input screen (M80 preset)</em></td>
<td align="center"><img src="screenshots/Screenshot_20260516-001344.MPBR%20Calculator.png" width="190"><br><em>Ammo preset dropdown (rimfire section)</em></td>
<td align="center"><img src="screenshots/Screenshot_20260516-001338.MPBR%20Calculator.png" width="190"><br><em>Reticle preset dropdown by manufacturer</em></td>
</tr>
<tr>
<td align="center"><img src="screenshots/Screenshot_20260516-001404.MPBR%20Calculator.png" width="190"><br><em>Inputs with EOTech VUDU HC1 selected</em></td>
<td align="center"><img src="screenshots/Screenshot_20260516-001408.MPBR%20Calculator.png" width="190"><br><em>Atmosphere and trajectory table settings</em></td>
<td align="center"><img src="screenshots/Screenshot_20260516-001417.MPBR%20Calculator.png" width="190"><br><em>Results: MPBR summary + reticle illustration</em></td>
</tr>
<tr>
<td align="center"><img src="screenshots/Screenshot_20260516-001422.MPBR%20Calculator.png" width="190"><br><em>Trajectory table and Save DOPE Chart</em></td>
<td align="center"><img src="screenshots/DOPE_M80__7_62_51_NATO__20260516_001435.jpg" width="190"><br><em>Exported DOPE chart JPEG</em></td>
<td></td>
</tr>
</table>

## Opening in Android Studio

1. Unzip the project somewhere on your machine.
2. In Android Studio: **File → Open** → pick the `MpbrApp` folder (the one
   containing `settings.gradle.kts`).
3. Android Studio will sync Gradle and download dependencies. First sync
   takes a few minutes.
4. If prompted about a missing Android SDK, accept the offer to install it.
5. Click ▶️ Run.

Tested with Android Studio Meerkat (2024.3) or later. Builds against Android
SDK 35, supports devices from Android 7 (API 24) up.

Build toolchain: Kotlin 2.2.10 · AGP 9.2.1 · Gradle 9.4.1. The Kotlin
Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is now
separate from the Kotlin plugin — required since Kotlin 2.x.

## What's where

```
MpbrApp/
├── settings.gradle.kts          ← root project config
├── build.gradle.kts             ← top-level plugin versions
├── gradle.properties
├── gradle/wrapper/              ← Gradle wrapper jar + properties
├── gradlew, gradlew.bat         ← wrapper launchers
└── app/
    ├── build.gradle.kts         ← app module (Compose, Kotlin, Material 3)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/mpbr/
        │   ├── MainActivity.kt  ← Compose UI
        │   └── Ballistics.kt    ← G1/G7 drag, atmosphere, MPBR solver
        └── res/                 ← icons, theme, strings, backup rules
```

The interesting code lives in those two `.kt` files; everything else is
boilerplate Android needs to actually launch the app.

## Default sanity check

App opens with the M80 (7.62×51 NATO) preset at 2231 ft / 70°F / 25% RH
(Parma, ID defaults), 6" vital zone:

- Near zero ≈ 25 yd
- Far zero ≈ 229 yd
- Max ordinate ≈ 3.0" @ 128 yd
- MPBR ≈ 270 yd

## Inputs

**Ammo preset** — 305 factory loads grouped into Rifle (green), Rimfire
(blue), Pistol (amber), and Shotgun (purple) categories. Selecting a preset
populates all bullet and sight fields. Editing any field manually switches the
selector to "Custom".

Shotgun notes: smoothbore slug presets use 0.5" sight height (bead) and an 8"
vital zone (deer); sabot presets use 1.5" (scoped rifled barrel). Buckshot
presets model a single pellet's ballistics with a 4" vital zone — useful for
gauging effective range but not pattern spread.

**Reticle** — optional scope reticle for the DOPE chart illustration. Defaults
to "None". Selecting a reticle also removes the redundant MOA or MIL column
from the trajectory table (whichever unit the reticle doesn't use). Available
presets are all real manufacturer scopes:

Presets are sorted by manufacturer:

| Preset | Unit | Focal plane | Style | Details |
|---|---|---|---|---|
| Burris Fullfield Ballistic E3 (MOA, SFP) | MOA | SFP (12×) | Ballistic E3 | Thin vertical (scope top → D=1 MOA stub above center) → thick horizontal bars (±4 MOA gap, ticks at 1/2/3/4 MOA) → BDC marks at 1.49/4.31/7.18 MOA (line widths ±1.5/2.5/3.5 MOA, windage dots ±1.54/2.42/3.38 MOA) → thick bottom post |
| Burris Fullfield Plex (MOA, SFP) | MOA | SFP (8×) | Duplex | Classic duplex: thick tapered posts tapering to short thin center crosshair; gap 0.35 MOA, post half-height 0.93 MOA, extends to ±10.6 MOA. Source: Burris subtension diagram |
| EOTech VUDU HC1 (MOA, SFP) | MOA | SFP (9×) | BDC | Holdover hashes 2–22 MOA in 2 MOA steps; windage ±2–±12 MOA in 2 MOA steps |
| EOTech VUDU MR5 (MRAD, FFP) | MIL | FFP (all mags) | MRAD tree | Numbered horizontal stadia with 0.5 MRAD ticks; 1 MRAD speed ring; dot-grid Christmas tree rows 2–8 MRAD |
| German ZB26/ZB30 AA Spider Sight (MOA, 1×) | MOA | 1× (always accurate) | Spider Sight | Outer ring + 4 crosshair spokes + small center ring + beads on all 4 spokes at ~45% radius + 4 diagonal tick marks at 45° on outer ring. Outer ring ≈ 370 MOA (7.92mm at 775 m/s, 300 km/h aircraft). Values estimated from physics and product photos — source document not located. Source: IMA/Aubrey product photos |
| Firefield RapidStrike Circle Dot (MOA, SFP) | MOA | SFP (6×) | Circle BDC | 9.95 MOA circle + 1.34 MOA center dot; thin BDC post 300–600 yd below circle; thick bottom post. Positions estimated — exact MOA not published |
| Firefield RapidStrike CR1 1-10x24 (MOA, SFP) | MOA | SFP (10×) | Horseshoe BDC | 5.73 MOA horseshoe arc (300°, 60° gap at bottom; = 18" IPSC target at 300 yd) + 1.34 MOA center dot (0–200 yd); 300-yd holdover at arc gap; hashes at 400/500/600 yd (depths estimated from .223 55gr FMJ ballistics at 100-yd zero). Source: FF13075 user manual pp.14–15 |
| Holosun 507 COMP (2 MOA / 8-20-32 MOA CRS) | MOA | 1× (always accurate) | Circle-dot | Three concentric rings at 8/20/32 MOA + 2 MOA center dot; cardinal tick marks on outermost ring. CRS lets shooter select any single ring or combine all three |
| Holosun 507 COMP (32 MOA ring only) | MOA | 1× (always accurate) | Circle-dot | 32 MOA ring only, no center dot; cardinal tick marks at 12/3/6/9 o'clock |
| Holosun 507K (2 MOA / 32 MOA) | MOA | 1× (always accurate) | Circle-dot | 32 MOA ring + 2 MOA center dot; cardinal tick marks at 12/3/6/9 o'clock |
| Holosun 507K (32 MOA ring only) | MOA | 1× (always accurate) | Circle-dot | 32 MOA ring only, no center dot; cardinal tick marks at 12/3/6/9 o'clock |
| Holosun 510C (2 MOA / 65 MOA) | MOA | 1× (always accurate) | Circle-dot | 65 MOA ring + 2 MOA center dot + cardinal tick marks at 12/3/6/9 o'clock |
| Holosun 510C (65 MOA ring only) | MOA | 1× (always accurate) | Circle-dot | 65 MOA ring only, no center dot; cardinal tick marks at 12/3/6/9 o'clock |
| Leupold VX-Freedom MOA-Ring 1.5-4x20 (MOA, SFP) | MOA | SFP (4×) | Ring BDC | 40 MOA ring + 3.4 MOA center circle + full crosshair; lead/windage tick marks at ±6.5 and ±13.0 MOA on H arm; fine BDC ticks at 5 MOA spacing (5–35 MOA); tapered thick post from 40 MOA (4.5 MOA wide) to scope edge (8.9 MOA wide). Source: Leupold MOA-Ring reticle diagram (2021) |
| SIG Sauer Tango SPR BDC1 (MOA, SFP) | MOA | SFP (4×) | BDC | Thick H posts at ±10 MOA; holdover marks at 3.75/6.50/9.50/14.50 MOA; thin crosshair with 0.25 MOA ticks. Source: Tango SPR manual p.16 |
| SIG Sauer Tango SPR FL-4 (MOA, SFP) | MOA | SFP (4×) | FL-4 | Thin crosshair with two filled horizontal reference triangles per side at ±3.13/±5.95 MOA; one outer horizontal hash per side at ±13.48 MOA; lower BDC stadia at 2.86/3.44/4.30/5.73 MOA with 0.75 MOA half-widths; lower post to 20.22 MOA with 15° included tip angle. Open outlines in the source diagram are dimensional callouts, not reticle artwork. Source: Tango SPR manual p.17 |
| Trijicon ACOG TA31 Donut (MOA, 4×) | MOA | 4× fixed (always accurate) | ACOG Donut | ~2 MOA radius illuminated ring + center dot; top of ring = 100m POA; BDC stadia at 400/500/600/700/800m (widths ±2.08/1.66/1.39/1.19/1.04 MOA for 19" shoulder ranging). Holdover depths from M855 at 100m zero — verify against Trijicon subtension card. Source: ACOG Operator's Manual pp.26–28 |
| U.S. 2.36" Bazooka D7161556 (MOA, 1×) | MOA | 1× (always accurate) | Lead Rings | Thin crosshair + 4 concentric lead rings at ~190/380/570/760 MOA for 10/20/30/40 mph crossing targets. Ring radii derived from rocket muzzle velocity (265 ft/s): lead_mils = V_mph × 5.536 mils (range-independent). Exact subtensions from TB 9-294-9 not yet confirmed. Source: TM 9-294, SARCO product description |
| UUQ Ranger ER Arrow BDC (MOA, 3×) | MOA | 3× fixed (always accurate) | Chevron BDC | Upward fiber-optic chevron (∧) at center; separated H arm segments at ±5/7.5/10/13 MOA with I-beam ticks; V post below chevron only with 1 MOA minor ticks labeled at 4/6/8/10 MOA. Calibrated for .22LR/.223/.308 at 100 yd zero. Source: UUQ product page reticle diagram |
| Viridian MDS25 BRC (MOA, 1×) | MOA | 1× (always accurate) | BRC | 3 MOA center dot (50/200 yd); holdunder dots ≈7 MOA (15 yd) and ≈25 MOA (7 yd) below; inward chevrons for ranging. Dot positions estimated — exact MOA not published by Viridian |
| Vortex Dead-Hold BDC (MOA, SFP) | MOA | SFP (max mag) | BDC | Holdover dots: 1.5 / 4.5 / 7.5 / 11.0 MOA; windage ±2 / ±4 / ±6 MOA; thick outer posts at 7 MOA |
| Vortex EBR-7C (MOA, FFP) | MOA | FFP (all mags) | MOA tree | Numbered H/V stadia (1 MOA minor / 4 MOA major, labeled 4–24/32), thick posts at ±26 MOA; dot-grid tree below center rows 4→36 MOA at 2 MOA dot spacing; thick bottom post. Used in Venom 5-25×56, Viper PST Gen II, Strike Eagle, Razor HD Gen II |
| Vortex Spitfire AR DRT (MOA, 1×) | MOA | 1× (always accurate) | DRT | 3 MOA center dot; inner ring 44 MOA ID / 6 MOA thick; outer ring 140 MOA ID / 3 MOA thick |
| Vortex Strike Eagle AR-BDC3 (MOA, SFP) | MOA | SFP (8×) | AR-BDC3 | Horseshoe (large top arc + side hooks, open bottom) + 1 MOA center dot; BDC post at 2.4/5.6/9.5/14.6 MOA (300–600 yd); windage dot rows widening at 1 MOA steps to ±5/7/10/12 MOA; "3"–"6" range labels and "10" mph wind label at 600 yd |

**Drag model** — G1 or G7. The BC value you enter must reference the model you
pick. Manufacturers usually publish G1; many also publish G7 for long
boat-tail bullets where G7 fits better. Don't convert between them with a
fixed multiplier; use the manufacturer's value.

**Bullet & sight** — muzzle velocity (fps), BC, sight height above bore (in),
vital zone diameter (in).

**Atmosphere** — altitude (ft), temperature (°F), humidity (%), wind speed
(mph full-value crosswind). Defaults are Parma, ID conditions (2231 ft, 70°F,
25% RH, 0 mph). Set wind to 0 to hide the drift columns in the trajectory
table.

**Trajectory Table** — configurable start, step, and end range (defaults
0 / 50 / 500 yd; start 0–2000, step 1–500, end 0–2000). Both on-screen
table and DOPE chart use these values. Start must be less than end. Start,
step, and end accept whole numbers only.

**Target Distance** — optional single-range lookup. Check the box and enter
a distance (any whole number, not limited to the table step interval) to see
drop, holdover MOA/MIL, velocity, and energy at that exact range in the
Results card (interpolated from the high-resolution trajectory). When a
reticle is selected and results are shown, a second checkbox appears —
**Show target distance only on reticle** — which hides all other range
callouts on the reticle illustration and displays only the target distance
dot. This is useful for previewing the exact hold point for a known distance
regardless of how coarse the table step is.

**Metric toggle** — the **Not Metric / Metric** chip on the right side of
the Drag Model row toggles SI units. When active the label reads "Metric";
when inactive it reads "Not Metric". Inputs show m/s, mm, cm, m, °C, km/h;
results and trajectory table show m, cm, m/s, J; DOPE chart exports in
metric too. Internal calculations always use imperial — sessions save and
load in imperial regardless of the toggle. Bullet weight stays in grains
(universal in the ammunition world). BC is dimensionless. Angular values
(MOA, MIL) are unit-system-independent and never change.

**DOPE Card Title** — editable heading printed at the top of the saved JPEG
(default "MPBR DOPE CARD"). Leave blank to keep the default.

**Notes / Turret Adjustments** — multiline free-text field below the title.
Appears as a "Notes:" section at the bottom of both the saved JPEG and the
printed DOPE chart, after the trajectory table. Use it for turret click
settings, zero-distance notes, or any range card annotation. Saved and
restored with sessions.

**Sessions** — tap the 💾 (Save) icon in the title bar to save the current
setup (all inputs: ammo, reticle, atmosphere, table range, DOPE title, notes) under
a name. The name pre-fills as `"<Preset> — MM/dd"` and can be edited. Saving
with an existing name overwrites it. Tap the 📂 (Load) icon to open the
sessions list — tap a name to restore all inputs and auto-calculate, tap ✕
to delete a session. Sessions persist across app restarts (stored in
SharedPreferences).

## Algorithm

1. Bisect bore angle until trajectory peak above LOS equals exactly
   `vital_zone / 2`.
2. Re-simulate at high resolution and read off:
   - **Near zero** — first LOS crossing (rising)
   - **Far zero** — second LOS crossing (falling); optimal sight-in distance
   - **Max ordinate** — peak height and its range
   - **MPBR** — range where bullet drops to `-vital_zone / 2`
   - **Bore angle** — informational, barrel angle above LOS

Drag uses standard G1/G7 Cd vs Mach tables with linear interpolation. Air
density and speed of sound scale from ICAO sea-level standards using the
ICAO troposphere pressure model, temperature, and humidity (Magnus saturation
+ standard `1 − 0.378·Pᵥ/P` correction). Crosswind enters as a lateral
air-relative velocity component so it naturally affects drag magnitude and
produces lateral drift. No spin drift, no Coriolis.

## Results layout

After calculating, the results screen shows (in order):

1. Summary card — near/far zero, MPBR, max ordinate, bore angle, velocity/energy;
   if Target Distance is enabled, also shows drop, holdover, velocity, and energy
   at that exact range
2. **Reticle illustration** (if a reticle is selected) — live on-screen scope view
   with color-coded holdover callout dots and labels for each range in the table;
   when **Show target distance only on reticle** is checked, only the target
   distance callout is shown
3. Trajectory table — holdover/drift/velocity/energy for each range in the configured
   start-to-end window

## Saving a DOPE chart

After calculating, tap **Save DOPE Chart** or **Print DOPE Chart** (appear below
the trajectory table). **Save** renders a 1200 px JPEG and writes it to
**Pictures/MPBR DOPE Charts/** in your gallery (on Android 8 or earlier the app
will ask for storage permission the first time). **Print** sends the same image
to the system print dialog (AirPrint, PDF, etc.). Both render:

- Load name, near/far zero, MPBR, max ordinate, bore angle
- Altitude, temperature, humidity, wind conditions
- **Reticle illustration** (if a reticle is selected) — a scope circle showing
  the reticle's crosshair and marks. Each trajectory range gets a unique color:
  a filled dot placed at the bullet's actual 2D position on the reticle
  (elevation + crosswind drift), a dashed leader line, and a bold
  "250 yd (2.3 MOA)" label. Labels that would overlap are skipped; points
  outside the reticle's extent are omitted. FFP reticles work at any
  magnification; SFP values are valid at maximum magnification only.
- Full trajectory table at 50 yd steps over the configured range (same columns
  as on-screen — drift columns appear only when wind ≠ 0, energy only when bullet
  weight is set; the redundant MOA or MIL column is dropped when a reticle is
  selected)
- **Notes section** (if the Notes field is non-empty) — rendered at the bottom
  after the trajectory table; each newline in the field becomes a separate line
- Date of generation

## Easter eggs

Tap the **Ammunition** section label five times quickly to play a synthesized gunshot sound.

## Things to add next

- Frickin Laser Beams.

## Code quality

All Android Studio warnings resolved as of v1.27:
- KTX functions used throughout (`createBitmap`, `withClip`)
- Kotlin stdlib used for math (`roundToInt`, `abs`, `floor`)
- No unused parameters, no shadowed lambdas, all locals lowercase
- `MPBR`, `reticle`, `holdunder`, `Traj`, and date-format tokens added to
  the project spellcheck dictionary (`.idea/dictionaries/project.xml`)
