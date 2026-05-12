# MPBR Calculator

An Android app that computes Maximum Point Blank Range (MPBR), modeled after
shooterscalculator.com's MPBR tool. Uses a point-mass exterior ballistics
simulator with G1 or G7 drag, and supports altitude / temperature / humidity
atmospheric corrections.

## Opening in Android Studio

1. Unzip the project somewhere on your machine.
2. In Android Studio: **File → Open** → pick the `MpbrApp` folder (the one
   containing `settings.gradle.kts`).
3. Android Studio will sync Gradle and download dependencies. First sync
   takes a few minutes.
4. If prompted about a missing Android SDK, accept the offer to install it.
5. Click ▶️ Run.

Tested with Android Studio Hedgehog / Iguana / Jellyfish (anything from 2023
onward). Builds against Android SDK 34, supports devices from Android 7
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

With the built-in defaults (2700 fps, 0.400 G1, 1.5" sight height, 6" vital
zone, ICAO standard atmosphere):

- Near zero ≈ 25 yd
- Optimal zero (far zero) ≈ 238 yd
- Max ordinate ≈ 3.0" @ 133 yd
- MPBR ≈ 280 yd
- Bore angle above LOS ≈ 6.4 MOA

## Inputs

**Drag model** — G1 or G7. The BC value you enter must reference the model you
pick. Manufacturers usually publish G1; many also publish G7 for long
boat-tail bullets where G7 fits better. Don't convert between them with a
fixed multiplier; use the manufacturer's value.

**Bullet & sight** — muzzle velocity (fps), BC, sight height above bore (in),
vital zone diameter (in).

**Atmosphere** — altitude (ft), temperature (°F), humidity (%). Defaults are
ICAO standard sea level (0, 59, 0) so leaving these alone reproduces dry
sea-level results.

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
+ standard `1 − 0.378·Pᵥ/P` correction). Flat-fire approximation: no wind, no
spin drift, no Coriolis.

## Things to add next

- Trajectory table (drop at every 50 yd) — `Ballistics.simulate()` already
  produces one; render in a `LazyColumn`.
- Save/recall presets per cartridge — Room or DataStore.
- Metric units toggle.
- Wind drift — extend simulator to track lateral position.
- Custom drag function (CDM) for users with manufacturer Doppler radar curves.
