# MPBR Calculator

An Android app that computes Maximum Point Blank Range (MPBR), modeled after
shooterscalculator.com's MPBR tool. Uses a point-mass exterior ballistics
simulator with G1 or G7 drag, and supports altitude / temperature / humidity
atmospheric corrections and full-value crosswind drift.

Includes 168 factory ammo presets organized into color-coded categories
(rifle, rimfire, pistol, shotgun) with a trajectory table at 50 yd steps out
to 1,000 yd. DOPE charts can be saved as a JPEG to your gallery, optionally
with a scope reticle illustration showing color-coded holdover callouts for
every range that fits within the reticle's extent.

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
- Optimal zero (far zero) ≈ 229 yd
- Max ordinate ≈ 3.0" @ 128 yd
- MPBR ≈ 270 yd

## Inputs

**Ammo preset** — 168 factory loads grouped into Rifle (green), Rimfire
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

| Preset | Unit | Focal plane | Style | Details |
|---|---|---|---|---|
| Vortex Dead-Hold BDC (MOA, SFP) | MOA | SFP (max mag) | BDC | Holdover dots: 1.5 / 4.5 / 7.5 / 11.0 MOA; windage ±2 / ±4 / ±6 MOA; thick outer posts at 7 MOA |
| Viridian MDS25 BRC (MOA, 1×) | MOA | 1× (always accurate) | BRC | 3 MOA center dot (50/200 yd); holdunder dots ≈7 MOA (15 yd) and ≈25 MOA (7 yd) below; inward chevrons for ranging. Dot positions estimated — exact MOA not published by Viridian |
| Vortex Spitfire AR DRT (MOA, 1×) | MOA | 1× (always accurate) | DRT | 3 MOA center dot; inner ring 44 MOA ID / 6 MOA thick; outer ring 140 MOA ID / 3 MOA thick |
| Vortex EBR-7C (MOA, FFP) | MOA | FFP (all mags) | MOA tree | Numbered H/V stadia (1 MOA minor / 4 MOA major, labeled 4–24/32), thick posts at ±26 MOA; dot-grid tree below center rows 4→36 MOA at 2 MOA dot spacing; thick bottom post. Used in Venom 5-25×56, Viper PST Gen II, Strike Eagle, Razor HD Gen II |
| EOTech VUDU HC1 (MOA, SFP) | MOA | SFP (9×) | BDC | Holdover hashes 2–22 MOA in 2 MOA steps; windage ±2–±12 MOA in 2 MOA steps |
| EOTech VUDU MR5 (MRAD, FFP) | MIL | FFP (all mags) | MRAD tree | Numbered horizontal stadia with 0.5 MRAD ticks; 1 MRAD speed ring; dot-grid Christmas tree rows 2–8 MRAD |
| Holosun 510C (2 MOA / 65 MOA) | MOA | 1× (always accurate) | Circle-dot | 65 MOA ring + 2 MOA center dot + cardinal tick marks at 12/3/6/9 o'clock |

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

**Trajectory Table** — configurable start and end range (defaults 50 / 500 yd,
range 0–2000 yd, 50 yd steps). Both on-screen table and DOPE chart use these
values. Start must be less than end.

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

1. Summary card — near/far zero, MPBR, max ordinate, bore angle, velocity/energy
2. **Reticle illustration** (if a reticle is selected) — live on-screen scope view
   with color-coded holdover callout dots and labels for each range in the table
3. Trajectory table — holdover/drift/velocity/energy for each range in the configured
   start-to-end window

## Saving a DOPE chart

After calculating, tap **Save DOPE Chart** (appears below the trajectory
table). The app renders a 1200 px JPEG containing:

- Load name, near/far zero, MPBR, max ordinate, bore angle
- Altitude, temperature, humidity, wind conditions
- **Reticle illustration** (if a reticle is selected) — a scope circle showing
  the reticle's crosshair and marks. Each trajectory range gets a unique color:
  a filled dot on the stadia, a dashed leader line, and a bold "250 yd (2.3 MOA)"
  label. Labels that would overlap are skipped; ranges beyond the reticle's
  extent are omitted. FFP reticles work at any magnification; SFP values are
  valid at maximum magnification only.
- Full trajectory table at 50 yd steps over the configured range (same columns
  as on-screen — drift columns appear only when wind ≠ 0, energy only when bullet
  weight is set; the redundant MOA or MIL column is dropped when a reticle is
  selected)
- Date of generation

The file is written to **Pictures/MPBR DOPE Charts/** in your gallery.
On Android 8 or earlier the app will ask for storage permission the first time.

## Things to add next

- Save/recall custom loads — Room or DataStore.
- Metric units toggle.
- Custom drag function (CDM) for users with manufacturer Doppler radar curves.
