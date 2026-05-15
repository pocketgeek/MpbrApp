package com.example.mpbr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Point-mass exterior ballistics with G1 or G7 drag function.
 *
 * Atmosphere defaults to ICAO standard sea level (59 °F, 29.92 inHg, 0% RH);
 * altitude / temperature / humidity inputs scale air density (and speed of
 * sound) accordingly. No wind, no spin drift, no Coriolis — flat-fire.
 *
 * Coordinate system:
 *   x = horizontal range (ft)
 *   y = height relative to line of sight (ft)
 *   LOS is horizontal at y = 0; bore axis starts at y = -sightHeight,
 *   tilted up by launchAngle so the trajectory crosses LOS at zero ranges.
 */
object Ballistics {

    private const val G                  = 32.17405
    private const val SPEED_OF_SOUND_STD = 1116.45      // ft/s at 59 °F
    // Drag calibration: a = (DRAG_K * rho_ratio / BC) * v² * Cd(M).
    // Derived from G-standard projectile, ICAO SL air density.
    private const val DRAG_K             = 0.0002049
    private const val P_STD_INHG         = 29.92
    private const val T_STD_RANKINE      = 518.67       // 59 °F = 518.67 °R

    enum class DragModel { G1, G7 }
    enum class AmmoCategory { RIFLE, RIMFIRE, PISTOL, SHOTGUN }

    enum class ReticleUnit  { MIL, MOA }
    enum class ReticleStyle { HASH, DOT, CHRISTMAS_TREE, BDC, MRAD_TREE, CIRCLE_DOT, MOA_TREE, DRT, BRC, AR_BDC3, CIRCLE_BDC, DUPLEX, BALLISTIC_E3 }

    /**
     * Describes a scope reticle for DOPE chart illustration.
     * majorSpacing = units between labeled/thicker marks.
     * minorSpacing = units between fine marks (0 = major-only).
     * vertExtent   = how many units are visible below the center crosshair.
     */
    data class ReticlePreset(
        val name: String,
        val unit: ReticleUnit,
        val majorSpacing: Double,                       // hash reticles: units between labeled marks
        val minorSpacing: Double,                       // hash reticles: units between fine marks (0 = major-only)
        val vertExtent: Double,                         // units visible below center
        val style: ReticleStyle = ReticleStyle.HASH,
        // BDC reticles — ignored for HASH/DOT/CHRISTMAS_TREE styles:
        val holdoverMarks: List<Double> = emptyList(),  // mark positions in units below center
        val windageMarks: List<Double>  = emptyList(),  // hash positions in units each side of center
        val postStart: Double = 0.0                     // unit where thin line transitions to thick outer post (0 = no posts)
    )

    val RETICLE_PRESETS: List<ReticlePreset> = listOf(
        // ── Burris ───────────────────────────────────────────────────────────────
        // Burris Fullfield 2-8×35 SFP — Plex (duplex) reticle.
        // Classic thick outer posts tapering to thin center crosshair.
        // SFP, subtensions valid at 8× only.
        // majorSpacing = gap from center to inner face of thick post (≈3.85 MOA = 35% of scope).
        // minorSpacing = half-height of the horizontal thick bar (≈1.65 MOA = 15% of scope).
        // Vertical posts use 70% of horizontal half-height for their half-width (slightly narrower).
        // Proportions derived from Burris product image; vertExtent=11 MOA sets the scale.
        // Source: Burris Plex product image + subtension diagram.
        ReticlePreset(
            "Burris Fullfield Plex (MOA, SFP)",
            ReticleUnit.MOA, 3.85, 0.90, 11.0,
            ReticleStyle.DUPLEX
        ),
        // Burris Fullfield 3-12×42 SFP — Ballistic E3 reticle.
        // Duplex horizontal crosshair + ticks at ±1–4 MOA; 1 MOA center dot;
        // 3 BDC holdover marks at 1.49 / 4.31 / 7.18 MOA (200/300/400 yd for average calibers);
        // BDC line half-widths: 1.5 / 2.5 / 3.5 MOA; windage dots at ±1.54 / ±2.42 / ±3.38 MOA.
        // SFP, calibrated at 12×. Source: Burris Ballistic E3 subtension diagram.
        // Source: Burris Ballistic E3 subtension diagram (exact values).
        // majorSpacing = horizontal thin-section half-extent (4 MOA). Ticks at 1/2/3/4 MOA.
        // minorSpacing = post half-height (MOA). postStart = where thick horizontal posts begin (MOA).
        // vertExtent = 16 so 4 MOA gap = 25% of scope radius → thick posts dominate.
        // majorSpacing = thin section half-width (MOA). minorSpacing = horizontal bar half-height (MOA).
        // vertExtent = 40 → wide FOV → reticle appears as a compact cluster in lower scope area.
        ReticlePreset(
            "Burris Fullfield Ballistic E3 (MOA, SFP)",
            ReticleUnit.MOA, 4.0, 2.2, 40.0,
            ReticleStyle.BALLISTIC_E3,
            holdoverMarks = listOf(1.49, 4.31, 7.18),
            postStart     = 4.0
        ),
        // ── EOTech ───────────────────────────────────────────────────────────────
        // EOTech VUDU 3-9×32 SFP HC1 — hunting crosshair. SFP, valid at 9× only.
        // Windage ±2–±12 MOA; holdover 2–22 MOA at 2 MOA steps.
        ReticlePreset(
            "EOTech VUDU HC1 (MOA, SFP)",
            ReticleUnit.MOA, 0.0, 0.0, 22.0,
            ReticleStyle.BDC,
            holdoverMarks = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0),
            windageMarks  = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0),
            postStart     = 0.0
        ),
        // EOTech VUDU 4-12×36 FFP MR5 — MRAD dot-grid Christmas tree. FFP, all mags.
        ReticlePreset(
            "EOTech VUDU MR5 (MRAD, FFP)",
            ReticleUnit.MIL, 1.0, 0.5, 8.0,
            ReticleStyle.MRAD_TREE,
            postStart = 6.0
        ),
        // ── Firefield ────────────────────────────────────────────────────────────
        // Firefield RapidStrike 1-6×24 SFP — Circle Dot BDC reticle.
        // SFP, calibrated for .223 55gr FMJ. Circle 9.95 MOA diameter + 1.34 MOA center dot.
        // BDC tick marks below circle for 300/400/500/600 yd; thick bottom post.
        // BDC positions estimated from image proportions — exact MOA not published.
        // majorSpacing = circle radius (4.975 MOA); minorSpacing = dot radius (0.67 MOA).
        // Source: Firefield product page + reticle image.
        ReticlePreset(
            "Firefield RapidStrike Circle Dot (MOA, SFP)",
            ReticleUnit.MOA, 4.975, 0.67, 22.0,
            ReticleStyle.CIRCLE_BDC,
            holdoverMarks = listOf(7.0, 10.0, 13.5, 17.5)
        ),
        // ── Holosun ──────────────────────────────────────────────────────────────
        // Holosun HS510C — 2 MOA center dot + 65 MOA ring, 1× red dot.
        ReticlePreset(
            "Holosun 510C (2 MOA / 65 MOA)",
            ReticleUnit.MOA, 32.5, 1.0, 40.0,
            ReticleStyle.CIRCLE_DOT
        ),
        // ── Viridian ─────────────────────────────────────────────────────────────
        // Viridian MDS25 — BRC reticle, 1×. Positions estimated from HOB physics.
        ReticlePreset(
            "Viridian MDS25 BRC (MOA, 1×)",
            ReticleUnit.MOA, 0.0, 1.5, 40.0,
            ReticleStyle.BRC,
            holdoverMarks = listOf(7.0, 25.0)
        ),
        // ── Vortex ───────────────────────────────────────────────────────────────
        // Vortex Dead-Hold BDC — Viper 4-12×40, 3.5-10×50, etc. SFP, max mag only.
        ReticlePreset(
            "Vortex Dead-Hold BDC (MOA, SFP)",
            ReticleUnit.MOA, 0.0, 0.0, 11.0,
            ReticleStyle.BDC,
            holdoverMarks = listOf(1.5, 4.5, 7.5, 11.0),
            windageMarks  = listOf(2.0, 4.0, 6.0),
            postStart     = 7.0
        ),
        // Vortex EBR-7C — Venom 5-25×56, Viper PST Gen II, Strike Eagle, Razor HD Gen II. FFP.
        ReticlePreset(
            "Vortex EBR-7C (MOA, FFP)",
            ReticleUnit.MOA, 4.0, 1.0, 40.0,
            ReticleStyle.MOA_TREE,
            postStart = 26.0
        ),
        // Vortex Spitfire AR 1× Prism — DRT (Dual Ring Tactical). Always accurate.
        ReticlePreset(
            "Vortex Spitfire AR DRT (MOA, 1×)",
            ReticleUnit.MOA, 25.0, 1.5, 85.0,
            ReticleStyle.DRT,
            postStart = 71.5
        ),
        // Vortex Strike Eagle 1-8×24 — AR-BDC3 horseshoe BDC. SFP, valid at 8× only.
        ReticlePreset(
            "Vortex Strike Eagle AR-BDC3 (MOA, SFP)",
            ReticleUnit.MOA, 8.3125, 0.5, 25.0,
            ReticleStyle.AR_BDC3,
            holdoverMarks = listOf(2.4, 5.6, 9.5, 14.6)
        )
    )

    /**
     * Common factory ammunition presets. BCs and MVs are nominal factory
     * averages — actual values vary by manufacturer, lot, barrel length, and
     * conditions. Treat these as starting points; users can edit any field
     * after loading a preset, which switches the selector back to Custom.
     */
    data class AmmoPreset(
        val name: String,
        val muzzleVelocityFps: Double,
        val ballisticCoeff: Double,
        val bulletWeightGr: Double,
        val sightHeightIn: Double,
        val vitalZoneIn: Double,
        val dragModel: DragModel = DragModel.G1,
        val category: AmmoCategory = AmmoCategory.RIFLE
    )

    val PRESETS: List<AmmoPreset> = listOf(
        // ── Rifle ─────────────────────────────────────────────────────────────────
        // .22 cal
        AmmoPreset("5.45×39 7N6 53gr",            2900.0, 0.351,  53.0, 1.5, 6.0),
        AmmoPreset("5.45×39 7N10 56gr",           2900.0, 0.351,  56.0, 1.5, 6.0),
        AmmoPreset("5.45×39 60gr FMJ (Tula)",     2936.0, 0.329,  60.0, 1.5, 6.0),
        AmmoPreset("5.45×39 60gr V-MAX",          2810.0, 0.285,  60.0, 1.5, 6.0),
        AmmoPreset("223 Rem 55gr FMJ",            3215.0, 0.202,  55.0, 1.5, 6.0),
        AmmoPreset("223 Rem 62gr FMJ BT",         3100.0, 0.265,  62.0, 1.5, 6.0),
        AmmoPreset("223 Rem 77gr SMK",            2700.0, 0.185,  77.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("224 Valkyrie 60gr V-MAX",     3200.0, 0.265,  60.0, 1.5, 6.0),
        AmmoPreset("224 Valkyrie 75gr ELD-M",     3000.0, 0.240,  75.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("224 Valkyrie 90gr SMK",       2700.0, 0.285,  90.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("M193 (5.56×45)",              3250.0, 0.243,  55.0, 1.5, 6.0),
        AmmoPreset("M855 (5.56×45)",              3025.0, 0.304,  62.0, 1.5, 6.0),
        AmmoPreset("M855A1 EPR (5.56×45)",        3150.0, 0.152,  62.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("Mk262 Mod1 77gr OTM",         2848.0, 0.185,  77.0, 1.5, 6.0, DragModel.G7),
        // .243/6mm
        AmmoPreset("243 Win 55gr BT",             3850.0, 0.230,  55.0, 1.5, 6.0),
        AmmoPreset("243 Win 80gr SP",             3330.0, 0.325,  80.0, 1.5, 6.0),
        AmmoPreset("243 Win 95gr BT",             3100.0, 0.400,  95.0, 1.5, 6.0),
        AmmoPreset("243 Win 100gr PP",            2960.0, 0.380, 100.0, 1.5, 6.0),
        AmmoPreset("6mm ARC 103gr ELD-X",         2800.0, 0.258, 103.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6mm ARC 105gr BTHP",          2750.0, 0.253, 105.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6mm ARC 105gr FMJ Frontier",  2700.0, 0.530, 105.0, 1.5, 6.0),
        AmmoPreset("6mm ARC 108gr ELD-M",         2750.0, 0.270, 108.0, 1.5, 6.0, DragModel.G7),
        // .260/6.5mm
        AmmoPreset("260 Rem 120gr AccuTip",       2890.0, 0.480, 120.0, 1.5, 6.0),
        AmmoPreset("260 Rem 130gr ELD-M",         2875.0, 0.554, 130.0, 1.5, 6.0),
        AmmoPreset("260 Rem 140gr Core-Lokt",     2750.0, 0.435, 140.0, 1.5, 6.0),
        AmmoPreset("6.5 CM 120gr ELD-M",          2910.0, 0.245, 120.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 CM 129gr SST",            2810.0, 0.485, 129.0, 1.5, 6.0),
        AmmoPreset("6.5 CM 130gr Berger Hybrid",  2875.0, 0.287, 130.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 CM 140gr ELD-M",          2710.0, 0.326, 140.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 CM 143gr ELD-X",          2700.0, 0.315, 143.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 CM 147gr ELD-M",          2695.0, 0.351, 147.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 PRC 143gr ELD-X",         2960.0, 0.315, 143.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.5 PRC 147gr ELD-M",         2910.0, 0.352, 147.0, 1.5, 6.0, DragModel.G7),
        // .270/6.8mm
        AmmoPreset("270 Win 130gr BT",            3060.0, 0.337, 130.0, 1.5, 6.0),
        AmmoPreset("270 Win 150gr SP",            2800.0, 0.205, 150.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("270 WSM 130gr BT",            3300.0, 0.433, 130.0, 1.5, 6.0),
        AmmoPreset("270 WSM 140gr AccuBond",      3100.0, 0.496, 140.0, 1.5, 6.0),
        AmmoPreset("270 WSM 150gr Silvertip",     3120.0, 0.496, 150.0, 1.5, 6.0),
        AmmoPreset("6.8 SPC 100gr GMX",           2700.0, 0.138, 100.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.8 SPC 110gr V-MAX",         2600.0, 0.186, 110.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.8 SPC 115gr SMK OTM",       2625.0, 0.162, 115.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("6.8 SPC 120gr SST",           2600.0, 0.202, 120.0, 1.5, 6.0, DragModel.G7),
        // .284/7mm
        AmmoPreset("7mm Rem Mag 139gr",           3150.0, 0.235, 139.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("7mm Rem Mag 150gr BT",        3100.0, 0.250, 150.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("7mm Rem Mag 162gr ELD-X",     3084.0, 0.295, 162.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("7mm-08 139gr SST",            2950.0, 0.486, 139.0, 1.5, 6.0),
        AmmoPreset("7mm-08 140gr BT",             2825.0, 0.319, 140.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("7mm-08 140gr Fusion",         2850.0, 0.390, 140.0, 1.5, 6.0),
        AmmoPreset("7mm-08 150gr JSP",            2650.0, 0.408, 150.0, 1.5, 6.0),
        // .308/30-cal
        AmmoPreset("7.62×39 122gr FMJ",           2418.0, 0.257, 122.0, 1.5, 6.0),
        AmmoPreset("7.62×39 123gr HP",            2350.0, 0.270, 123.0, 1.5, 6.0),
        AmmoPreset("30 Carbine 110gr FMJ",        1990.0, 0.166, 110.0, 1.5, 6.0),
        AmmoPreset("30 Carbine 110gr FTX",        2000.0, 0.166, 110.0, 1.5, 6.0),
        AmmoPreset("30 Carbine 125gr HC",         1950.0, 0.126, 125.0, 1.5, 6.0),
        AmmoPreset("30-06 150gr SP",              2910.0, 0.310, 150.0, 1.5, 6.0),
        AmmoPreset("30-06 165gr BT",              2800.0, 0.225, 165.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("30-06 180gr SP",              2700.0, 0.205, 180.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 BLK 110gr V-MAX",         2375.0, 0.290, 110.0, 1.5, 6.0),
        AmmoPreset("300 BLK 125gr PPU",           2329.0, 0.325, 125.0, 1.5, 6.0),
        AmmoPreset("300 BLK 147gr FMJ",           1920.0, 0.398, 147.0, 1.5, 6.0),
        AmmoPreset("300 BLK 200gr (subsonic)",    1050.0, 0.540, 200.0, 1.5, 6.0),
        AmmoPreset("300 BLK 220gr (subsonic)",    1045.0, 0.608, 220.0, 1.5, 6.0),
        AmmoPreset("300 PRC 212gr ELD-X",         2860.0, 0.334, 212.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 PRC 225gr ELD-M",         2810.0, 0.391, 225.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 PRC 245gr Elite Hunter",  2720.0, 0.413, 245.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 PRC 250gr A-Tip",         2800.0, 0.442, 250.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 Win Mag 150gr PP",        3290.0, 0.310, 150.0, 1.5, 6.0),
        AmmoPreset("300 Win Mag 168gr SMK",       3000.0, 0.235, 168.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 Win Mag 180gr AccuBond",  2950.0, 0.250, 180.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("300 Win Mag 200gr Partition", 2930.0, 0.245, 200.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("M80 (7.62×51 NATO)",          2750.0, 0.398, 147.0, 1.5, 6.0),
        AmmoPreset("308 Win 150gr FMJ",           2820.0, 0.409, 150.0, 1.5, 6.0),
        AmmoPreset("308 Win 165gr BT",            2700.0, 0.450, 165.0, 1.5, 6.0),
        AmmoPreset("308 Win 168gr SMK",           2650.0, 0.224, 168.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("308 Win 175gr SMK",           2600.0, 0.250, 175.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("308 Win 180gr SP",            2600.0, 0.383, 180.0, 1.5, 6.0),
        // .338
        AmmoPreset("338 Lapua 250gr SMK",         2950.0, 0.340, 250.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("338 Lapua 285gr ELD-M",       2900.0, 0.405, 285.0, 1.5, 6.0, DragModel.G7),
        AmmoPreset("338 Lapua 300gr SMK",         2650.0, 0.390, 300.0, 1.5, 6.0, DragModel.G7),
        // .35 cal
        AmmoPreset("350 Legend 150gr",            2325.0, 0.223, 150.0, 1.5, 6.0),
        AmmoPreset("350 Legend 180gr",            2100.0, 0.200, 180.0, 1.5, 6.0),
        // .45 cal rifle
        AmmoPreset("450 Bushmaster 250gr FTX",    2200.0, 0.210, 250.0, 1.5, 6.0),
        AmmoPreset("450 Bushmaster 260gr",        2180.0, 0.144, 260.0, 1.5, 6.0),
        AmmoPreset("458 SOCOM 250gr XD",          2000.0, 0.190, 250.0, 1.5, 6.0),
        AmmoPreset("458 SOCOM 300gr BT",          1900.0, 0.250, 300.0, 1.5, 6.0),
        AmmoPreset("458 SOCOM 325gr FTX",         1800.0, 0.240, 325.0, 1.5, 6.0),
        AmmoPreset("458 SOCOM 350gr FMJ",         1650.0, 0.200, 350.0, 1.5, 6.0),
        // .50 BMG
        AmmoPreset("50 BMG A-MAX 750gr",          2820.0, 1.050, 750.0, 2.0, 6.0),
        AmmoPreset("50 BMG API 649gr",            2800.0, 0.650, 649.0, 2.0, 6.0),
        AmmoPreset("50 BMG CEB MTAC 660gr",       2800.0, 0.390, 660.0, 2.0, 6.0, DragModel.G7),
        AmmoPreset("50 BMG M33 Ball 660gr",       2900.0, 0.701, 660.0, 2.0, 6.0),
        // ── Rimfire ───────────────────────────────────────────────────────────────
        // .17 HMR
        AmmoPreset("17 HMR 17gr A17",             2650.0, 0.128,  17.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("17 HMR 17gr V-MAX",           2550.0, 0.128,  17.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("17 HMR 20gr XTP",             2375.0, 0.125,  20.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        // .17 WSM
        AmmoPreset("17 WSM 20gr",                 3000.0, 0.185,  20.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("17 WSM 25gr",                 2600.0, 0.230,  25.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        // .22 LR
        AmmoPreset("22 LR 32gr Stinger",           1640.0, 0.103,  32.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 LR 40gr Mini-Mag",          1235.0, 0.124,  40.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 LR 40gr Std Velocity",      1070.0, 0.120,  40.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 LR 40gr Velocitor",         1435.0, 0.125,  40.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 LR 60gr SSS (subsonic)",     950.0, 0.200,  60.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        // .22 WMR
        AmmoPreset("22 WMR 30gr VNT",              2200.0, 0.116,  30.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 WMR 40gr JHP Maxi-Mag",    1875.0, 0.114,  40.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        AmmoPreset("22 WMR 40gr JHP Super-X",     1910.0, 0.116,  40.0, 1.5, 2.0, category = AmmoCategory.RIMFIRE),
        // ── Pistol ────────────────────────────────────────────────────────────────
        // 5.7×28mm
        AmmoPreset("5.7×28mm 27gr SS195LF",     1965.0, 0.095,  27.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("5.7×28mm 40gr V-MAX",       1600.0, 0.200,  40.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // .380 ACP
        AmmoPreset("380 ACP 90gr FTX",           990.0, 0.099,  90.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("380 ACP 90gr Gold Dot",     1040.0, 0.101,  90.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("380 ACP 95gr FMJ",           955.0, 0.100,  95.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("380 ACP 99gr HST",          1030.0, 0.110,  99.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // 9mm
        AmmoPreset("9mm 115 gr",          1180.0, 0.150, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 115gr Critical Def",  1135.0, 0.129, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 124 gr",          1110.0, 0.165, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 124gr Gold Dot",      1150.0, 0.134, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 124gr HST",           1150.0, 0.150, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 124gr HST +P",        1200.0, 0.150, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm NATO 124 gr",             1180.0, 0.165, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 135gr Critical Duty", 1160.0, 0.195, 135.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 147 gr (subsonic)",    990.0, 0.190, 147.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("9mm 147gr HST",           1000.0, 0.200, 147.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // .38 Spl / .357
        AmmoPreset("38 Spl 110gr JHP",           990.0, 0.125, 110.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("38 Spl 125gr JHP",           950.0, 0.150, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("38 Spl 158gr LRN",           770.0, 0.165, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 Mag 125gr Gold Dot",   1450.0, 0.141, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 Mag 125gr XTP",        1500.0, 0.151, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 Mag 158gr JSP",        1235.0, 0.163, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 Mag 158gr XTP",        1240.0, 0.206, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 Mag 180gr JHP",          1100.0, 0.195, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 SIG 125gr Gold Dot",     1350.0, 0.141, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 SIG 125gr HST",          1450.0, 0.150, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("357 SIG 135gr FlexLock",     1225.0, 0.153, 135.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // .40/10mm
        AmmoPreset("40 S&W 165gr FMJ",       1060.0, 0.155, 165.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("40 S&W 165gr JHP",       1150.0, 0.145, 165.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("40 S&W 180gr FMJ",       1000.0, 0.165, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("40 S&W 180gr JHP",        1010.0, 0.155, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 155gr XTP",          1410.0, 0.137, 155.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 155gr XTP (full)",   1500.0, 0.137, 155.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 180gr XTP",          1275.0, 0.164, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 180gr XTP (full)",   1300.0, 0.164, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 200gr Hard Cast",     1300.0, 0.194, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("10mm 200gr XTP",          1300.0, 0.199, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // .44
        AmmoPreset("44 Spl 180gr XTP",          1000.0, 0.138, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("44 Spl 200gr Gold Dot",      875.0, 0.145, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("44 Spl 240gr LFN",           750.0, 0.175, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("44 Mag 180gr XTP",          1600.0, 0.138, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("44 Mag 240gr XTP",          1230.0, 0.205, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("44 Mag 300gr XTP",          1200.0, 0.245, 300.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // .45
        AmmoPreset("45 ACP 185gr JHP",       1000.0, 0.130, 185.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 ACP 200gr JHP",       1080.0, 0.140, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 ACP 230gr FMJ",        855.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 ACP 230gr JHP +P",     950.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 Colt 200gr JHP",         1100.0, 0.130, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 Colt 250gr JHP",          780.0, 0.146, 250.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        AmmoPreset("45 Colt 255gr LRN",         1000.0, 0.155, 255.0, 0.7, 4.0, category = AmmoCategory.PISTOL),
        // ── Shotgun ───────────────────────────────────────────────────────────────
        // 12ga slugs — smoothbore (sight height 0.5" bead; vital zone 8" for deer)
        AmmoPreset("12ga Foster 1oz 2¾\"",           1560.0, 0.068, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga Foster 1oz 3\" Mag",         1760.0, 0.068, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga Brenneke 1oz 2¾\"",          1476.0, 0.086, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN),
        // 12ga sabot slugs — rifled barrel (sight height 1.5" scope)
        AmmoPreset("12ga Sabot 300gr SST",            2000.0, 0.200, 300.0, 1.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga Sabot 300gr Trophy Copper",  1900.0, 0.168, 300.0, 1.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga Sabot 385gr AccuTip",        1850.0, 0.200, 385.0, 1.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga Sabot 385gr Partition Gold", 1900.0, 0.190, 385.0, 1.5, 8.0, category = AmmoCategory.SHOTGUN),
        // 20ga slugs
        AmmoPreset("20ga Foster 7/8oz 2¾\"",          1600.0, 0.055, 383.0, 0.5, 8.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("20ga Sabot 260gr AccuTip",        1900.0, 0.140, 260.0, 1.5, 8.0, category = AmmoCategory.SHOTGUN),
        // .410 slug
        AmmoPreset("410 Slug 1/5oz",                  1830.0, 0.040,  87.5, 0.5, 4.0, category = AmmoCategory.SHOTGUN),
        // Buckshot — single-pellet ballistics; vital zone 4" (defensive)
        AmmoPreset("12ga 000 Buck (per pellet)",      1325.0, 0.083,  68.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga 00 Buck (per pellet)",       1325.0, 0.078,  54.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN),
        AmmoPreset("12ga #4 Buck (per pellet)",       1325.0, 0.058,  21.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN)
    )

    /** Standard G1 drag coefficient table. */
    private val G1 = listOf(
        0.00 to 0.2629, 0.50 to 0.2487, 0.60 to 0.2413, 0.70 to 0.2400,
        0.80 to 0.2389, 0.85 to 0.2382, 0.90 to 0.2424, 0.95 to 0.2773,
        1.00 to 0.3678, 1.05 to 0.4275, 1.10 to 0.4496, 1.15 to 0.4543,
        1.20 to 0.4521, 1.30 to 0.4356, 1.40 to 0.4137, 1.50 to 0.3909,
        1.75 to 0.3393, 2.00 to 0.2989, 2.25 to 0.2682, 2.50 to 0.2444,
        2.75 to 0.2257, 3.00 to 0.2106, 3.50 to 0.1882, 4.00 to 0.1730,
        5.00 to 0.1530
    )

    /** Standard G7 drag coefficient table (long boat-tail reference projectile). */
    private val G7 = listOf(
        0.00 to 0.1198, 0.50 to 0.1197, 0.60 to 0.1166, 0.70 to 0.1142,
        0.80 to 0.1138, 0.85 to 0.1138, 0.90 to 0.1175, 0.95 to 0.1281,
        1.00 to 0.1583, 1.05 to 0.1773, 1.10 to 0.1814, 1.15 to 0.1812,
        1.20 to 0.1808, 1.30 to 0.1742, 1.40 to 0.1675, 1.50 to 0.1606,
        1.75 to 0.1450, 2.00 to 0.1311, 2.25 to 0.1206, 2.50 to 0.1116,
        2.75 to 0.1042, 3.00 to 0.0980, 3.50 to 0.0883, 4.00 to 0.0809,
        5.00 to 0.0712
    )

    private fun cd(model: DragModel, mach: Double): Double {
        val tbl = if (model == DragModel.G1) G1 else G7
        if (mach <= tbl.first().first) return tbl.first().second
        if (mach >= tbl.last().first)  return tbl.last().second
        for (i in 0 until tbl.size - 1) {
            val (m0, c0) = tbl[i]
            val (m1, c1) = tbl[i + 1]
            if (mach in m0..m1) {
                val t = (mach - m0) / (m1 - m0)
                return c0 + t * (c1 - c0)
            }
        }
        return tbl.last().second
    }

    /**
     * Atmospheric conditions. All defaults match ICAO standard sea level so
     * leaving them untouched reproduces the original behaviour exactly.
     */
    data class Atmosphere(
        val altitudeFt: Double   = 0.0,
        val temperatureF: Double = 59.0,
        val humidityPct: Double  = 0.0
    ) {
        /** Air density / standard sea-level air density. */
        fun densityRatio(): Double {
            // Pressure at altitude (ICAO troposphere model).
            val pInHg = P_STD_INHG * (1.0 - 6.8756e-6 * altitudeFt).pow(5.2559)
            // Water-vapor partial pressure via Magnus saturation formula.
            val tC        = (temperatureF - 32.0) * 5.0 / 9.0
            val pSatHPa   = 6.1078 * exp(17.27 * tC / (tC + 237.3))
            val pSatInHg  = pSatHPa * 0.02953
            val pVapor    = (humidityPct / 100.0) * pSatInHg

            val tR        = temperatureF + 459.67
            val pressureR = pInHg / P_STD_INHG
            val tempR     = T_STD_RANKINE / tR
            // Humid air is less dense than dry air at the same P, T:
            //   ρ_humid / ρ_dry = 1 - 0.378 * (P_vapor / P_total)
            val humidityF = 1.0 - 0.378 * pVapor / pInHg
            return pressureR * tempR * humidityF
        }

        /** Speed of sound in dry air at this temperature (ft/s). */
        fun speedOfSound(): Double {
            val tR = temperatureF + 459.67
            return SPEED_OF_SOUND_STD * sqrt(tR / T_STD_RANKINE)
        }

        companion object { val STANDARD = Atmosphere() }
    }

    data class TrajectoryPoint(
        val rangeYards: Double,
        val heightInches: Double,
        val lateralInches: Double,  // positive = downwind (right for left-to-right wind)
        val velocityFps: Double,
        val timeSeconds: Double
    )

    fun simulate(
        muzzleVelocity: Double,
        ballisticCoeff: Double,
        sightHeightIn: Double,
        launchAngleRad: Double,
        dragModel: DragModel       = DragModel.G1,
        atmosphere: Atmosphere     = Atmosphere.STANDARD,
        windSpeedMph: Double       = 0.0,   // full-value crosswind; positive = left-to-right
        maxRangeYards: Double      = 1500.0,
        dt: Double                 = 0.0005
    ): List<TrajectoryPoint> {
        val sightHeightFt = sightHeightIn / 12.0
        val rhoRatio      = atmosphere.densityRatio()
        val ss            = atmosphere.speedOfSound()
        val dragCoef      = DRAG_K * rhoRatio / ballisticCoeff
        val windFps       = windSpeedMph * (5280.0 / 3600.0)

        var x  = 0.0
        var y  = -sightHeightFt
        var z  = 0.0            // lateral displacement (ft)
        var vx = muzzleVelocity * cos(launchAngleRad)
        var vy = muzzleVelocity * sin(launchAngleRad)
        var vz = 0.0            // lateral velocity starts at zero
        var t  = 0.0
        val maxX = maxRangeYards * 3.0
        val out  = ArrayList<TrajectoryPoint>(maxRangeYards.toInt() + 4)
        var nextSampleX = 0.0

        while (x < maxX) {
            // Velocity relative to the air mass (crosswind shifts z component)
            val rx   = vx
            val ry   = vy
            val rz   = vz - windFps
            val vRel = sqrt(rx * rx + ry * ry + rz * rz)
            if (vRel < 100.0) break
            val mach = vRel / ss
            val cdv  = cd(dragModel, mach)
            val aMag = dragCoef * vRel * vRel * cdv
            val ax   = -aMag * (rx / vRel)
            val ay   = -aMag * (ry / vRel) - G
            val az   = -aMag * (rz / vRel)

            vx += ax * dt
            vy += ay * dt
            vz += az * dt
            x  += vx * dt
            y  += vy * dt
            z  += vz * dt
            t  += dt

            if (x >= nextSampleX) {
                out.add(TrajectoryPoint(x / 3.0, y * 12.0, z * 12.0, vRel, t))
                nextSampleX += 3.0
            }
        }
        return out
    }

    data class MpbrResult(
        val nearZeroYards: Double,
        val farZeroYards: Double,
        val maxOrdinateInches: Double,
        val maxOrdinateRangeYards: Double,
        val mpbrYards: Double,
        val boreAngleMoa: Double,
        val velocityAtMpbrFps: Double,
        val energyAtMpbrFtLb: Double,
        val momentumAtMpbrLbFps: Double,
        val trajectoryTable: List<TrajectoryRow>
    )

    /** One sampled row of a trajectory table at a clean range step. */
    data class TrajectoryRow(
        val rangeYards: Int,
        val dropInches: Double,         // = -heightInches; positive = below LOS
        val holdoverMoa: Double,        // positive = hold over, negative = hold under
        val holdoverMil: Double,
        val driftInches: Double,        // positive = downwind
        val driftMoa: Double,
        val driftMil: Double,
        val velocityFps: Double,
        val energyFtLb: Double,         // 0 if bullet weight unknown
        val momentumLbFps: Double,      // 0 if bullet weight unknown
        val timeSeconds: Double
    )

    private const val GRAINS_PER_LB = 7000.0

    /** KE = ½ m v² in ft·lb when m is in slugs (lb·s²/ft) and v in ft/s. */
    private fun energyFtLb(weightGr: Double, vFps: Double): Double {
        if (weightGr <= 0.0) return 0.0
        val massSlug = (weightGr / GRAINS_PER_LB) / G   // lb_mass → slug
        return 0.5 * massSlug * vFps * vFps
    }

    /** Linear momentum in lb·ft/s using lb_mass for shooter-friendly units. */
    private fun momentumLbFps(weightGr: Double, vFps: Double): Double {
        if (weightGr <= 0.0) return 0.0
        return (weightGr / GRAINS_PER_LB) * vFps
    }

    /**
     * Sample a simulated trajectory at every `stepYards` and return clean rows.
     * Values between the underlying yard-sampled points are linearly interpolated
     * so the output rows fall exactly on the requested ranges.
     */
    fun trajectoryTable(
        traj: List<TrajectoryPoint>,
        bulletWeightGr: Double,
        stepYards: Int = 50,
        maxYards: Int  = 500,
        minYards: Int  = 0
    ): List<TrajectoryRow> {
        if (traj.isEmpty()) return emptyList()
        val rows = ArrayList<TrajectoryRow>(maxYards / stepYards + 1)
        var i = 0
        var target = stepYards
        while (target < minYards) target += stepYards   // skip rows before minYards
        while (target <= maxYards && i < traj.size - 1) {
            while (i < traj.size - 1 && traj[i + 1].rangeYards < target) i++
            if (i >= traj.size - 1) break
            val a = traj[i]
            val b = traj[i + 1]
            val span = b.rangeYards - a.rangeYards
            val t = if (span > 0) (target - a.rangeYards) / span else 0.0
            val height   = a.heightInches   + t * (b.heightInches   - a.heightInches)
            val lateral  = a.lateralInches  + t * (b.lateralInches  - a.lateralInches)
            val velocity = a.velocityFps    + t * (b.velocityFps    - a.velocityFps)
            val time     = a.timeSeconds    + t * (b.timeSeconds    - a.timeSeconds)
            val drop     = -height
            // 1 MOA = 1.0472" per 100 yd; 1 mil = range_yd * 0.036"
            val moa      = drop    * 100.0 / (target * 1.0472)
            val mil      = drop    / (target * 0.036)
            val driftMoa = lateral * 100.0 / (target * 1.0472)
            val driftMil = lateral / (target * 0.036)
            rows.add(
                TrajectoryRow(
                    rangeYards    = target,
                    dropInches    = drop,
                    holdoverMoa   = moa,
                    holdoverMil   = mil,
                    driftInches   = lateral,
                    driftMoa      = driftMoa,
                    driftMil      = driftMil,
                    velocityFps   = velocity,
                    energyFtLb    = energyFtLb(bulletWeightGr, velocity),
                    momentumLbFps = momentumLbFps(bulletWeightGr, velocity),
                    timeSeconds   = time
                )
            )
            target += stepYards
        }
        return rows
    }

    fun calculateMpbr(
        muzzleVelocity: Double,
        ballisticCoeff: Double,
        sightHeightIn: Double,
        vitalZoneDiameterIn: Double,
        bulletWeightGr: Double     = 0.0,
        dragModel: DragModel       = DragModel.G1,
        atmosphere: Atmosphere     = Atmosphere.STANDARD,
        windSpeedMph: Double       = 0.0,
        tableStepYards: Int        = 50,
        tableMaxYards: Int         = 500,
        tableMinYards: Int         = 0
    ): MpbrResult {
        require(muzzleVelocity      > 0)  { "muzzle velocity must be positive" }
        require(ballisticCoeff      > 0)  { "BC must be positive" }
        require(sightHeightIn       >= 0) { "sight height must be ≥ 0" }
        require(vitalZoneDiameterIn > 0)  { "vital zone must be positive" }
        require(bulletWeightGr      >= 0) { "bullet weight must be ≥ 0" }

        val rIn = vitalZoneDiameterIn / 2.0

        var lo = 0.0
        var hi = 0.05

        repeat(50) {
            val mid  = (lo + hi) / 2.0
            val traj = simulate(muzzleVelocity, ballisticCoeff, sightHeightIn, mid,
                                dragModel, atmosphere)
            val peak = traj.maxOfOrNull { it.heightInches } ?: 0.0
            if (peak > rIn) hi = mid else lo = mid
        }
        val angle = (lo + hi) / 2.0

        val traj = simulate(
            muzzleVelocity, ballisticCoeff, sightHeightIn, angle,
            dragModel, atmosphere, windSpeedMph,
            maxRangeYards = 2000.0, dt = 0.0002
        )

        var nearZero    = 0.0
        var farZero     = 0.0
        var peakHt      = Double.NEGATIVE_INFINITY
        var peakRng     = 0.0
        var mpbr        = 0.0
        var velAtMpbr   = 0.0
        var foundNear   = false
        var foundFar    = false

        for (i in 1 until traj.size) {
            val a = traj[i - 1]
            val b = traj[i]
            if (b.heightInches > peakHt) { peakHt = b.heightInches; peakRng = b.rangeYards }

            if (!foundNear && a.heightInches <= 0.0 && b.heightInches > 0.0) {
                nearZero  = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, 0.0)
                foundNear = true
            }
            if (foundNear && !foundFar && a.heightInches >= 0.0 && b.heightInches < 0.0) {
                farZero  = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, 0.0)
                foundFar = true
            }
            if (foundFar && a.heightInches >= -rIn && b.heightInches < -rIn) {
                mpbr = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, -rIn)
                // Interpolate velocity at the same point so energy/momentum line up.
                val span = b.rangeYards - a.rangeYards
                val tFrac = if (span > 0) (mpbr - a.rangeYards) / span else 0.0
                velAtMpbr = a.velocityFps + tFrac * (b.velocityFps - a.velocityFps)
                break
            }
        }

        val table = trajectoryTable(traj, bulletWeightGr, tableStepYards, tableMaxYards, tableMinYards)

        return MpbrResult(
            nearZeroYards         = nearZero,
            farZeroYards          = farZero,
            maxOrdinateInches     = peakHt,
            maxOrdinateRangeYards = peakRng,
            mpbrYards             = mpbr,
            boreAngleMoa          = angle * (180.0 / PI) * 60.0,
            velocityAtMpbrFps     = velAtMpbr,
            energyAtMpbrFtLb      = energyFtLb(bulletWeightGr, velAtMpbr),
            momentumAtMpbrLbFps   = momentumLbFps(bulletWeightGr, velAtMpbr),
            trajectoryTable       = table
        )
    }

    private fun lerp(x0: Double, x1: Double, y0: Double, y1: Double, yTarget: Double): Double {
        if (y1 == y0) return x0
        val t = (yTarget - y0) / (y1 - y0)
        return x0 + t * (x1 - x0)
    }
}
