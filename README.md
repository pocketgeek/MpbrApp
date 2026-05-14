# MPBR Calculator

An Android app that computes Maximum Point Blank Range (MPBR), modeled after
shooterscalculator.com's MPBR tool. Uses a point-mass exterior ballistics
simulator with G1 or G7 drag, and supports altitude / temperature / humidity
atmospheric corrections and full-value crosswind drift.

Includes 115+ factory ammo presets organized into color-coded categories
(rifle, rimfire, pistol, shotgun) with a trajectory table at 50 yd steps out
to 500 yd.

## Opening in Android Studio

1. Unzip the project somewhere on your machine.
2. In Android Studio: **File → Open** → pick the `MpbrApp` folder (the one
   containing `settings.gradle.kts`).
3. Android Studio will sync Gradle and download dependencies. First sync
   takes a few minutes.
4. If prompted about a missing Android SDK, accept the offer to install it.
5. Click ▶️ Run.

Tested with Android Studio Hedgehog / Iguana / Jellyfish (anything from 2023
onward). Builds against Android SDK 35, supports devices from Android 7
(API 24) up.

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

**Ammo preset** — 115+ factory loads grouped into Rifle (green), Rimfire
(blue), Pistol (amber), and Shotgun (purple) categories. Selecting a preset
populates all bullet and sight fields. Editing any field manually switches the
selector to "Custom".

Shotgun notes: smoothbore slug presets use 0.5" sight height (bead) and an 8"
vital zone (deer); sabot presets use 1.5" (scoped rifled barrel). Buckshot
presets model a single pellet's ballistics with a 4" vital zone — useful for
gauging effective range but not pattern spread.

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

## Things to add next

- Save/recall custom loads — Room or DataStore.
- Metric units toggle.
- Custom drag function (CDM) for users with manufacturer Doppler radar curves.
