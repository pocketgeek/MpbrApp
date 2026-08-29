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
    enum class ReticleStyle { HASH, DOT, CHRISTMAS_TREE, BDC, DRT, BRC, AR_BDC3, CIRCLE_BDC, DUPLEX, BALLISTIC_E3, SIG_FL4, ACOG_CHEVRON, ACOG_DONUT, CHEVRON_BDC, LEAD_RINGS, SPIDER_SIGHT, HORSESHOE_BDC, RING_BDC, SIG_MRAD_MILLING, SIG_MOA_MILLING, EOTECH_MR5_TREE, VORTEX_EBR7C_MOA_TREE, VORTEX_EBR7C_MRAD_TREE, VORTEX_VMR4_MOA_TREE, VORTEX_VMR4_MRAD_TREE, HOLOSUN_510C, HOLOSUN_507COMP, HOLOSUN_507K }

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
        // SFP, calibrated at 12×. Source: Burris Ballistic E3 subtension diagram.
        // majorSpacing = horizontal thin-section half-extent / tick count (4 MOA).
        // minorSpacing = horizontal bar half-height (0.8 MOA → bars are ~8% of scope diameter).
        // vertExtent = 20 → 4 MOA gap = 20% of scope radius.
        // BDC marks at 1.49/4.31/7.18 MOA; line half-widths 1.5/2.5/3.5 MOA;
        // windage dots at ±1.54/2.42/3.38 MOA from vertical center. D = 1 MOA thick stub above center.
        ReticlePreset(
            "Burris Fullfield Ballistic E3 (MOA, SFP)",
            ReticleUnit.MOA, 4.0, 0.8, 20.0,
            ReticleStyle.BALLISTIC_E3,
            holdoverMarks = listOf(1.49, 4.31, 7.18),
            postStart     = 4.0
        ),
        // ── EOTech ───────────────────────────────────────────────────────────────
        // EOTech VUDU 3-9×32 SFP HC1 — hunting crosshair. SFP, valid at 9× only.
        // Windage ±2–±12 MOA; holdover 2–22 MOA at 2 MOA steps. EOTech's official
        // HC1 reticle manual (VD1909 Rev B) tables hash spacing at 18 MOA·mag (i.e.
        // 1.0 MOA at 18×, the 3.5-18x50 model) — normalized to this scope's 9× max
        // mag that's 18/9 = 2.0 MOA, matching the spacing already used here.
        // Source: EOTech Vudu_Reticle_Manual_HC1_RevB.pdf.
        ReticlePreset(
            "EOTech VUDU HC1 (MOA, SFP)",
            ReticleUnit.MOA, 0.0, 0.0, 22.0,
            ReticleStyle.BDC,
            holdoverMarks = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0),
            windageMarks  = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0),
            postStart     = 0.0
        ),
        // EOTech VUDU 4-12×36 FFP MR5 — MRAD dot-grid Christmas tree. FFP, all mags.
        // Numbered stadia 2–8 MRAD (thick posts at ±8), short capped top stub to 3 MRAD,
        // tree rows start at row 2 (not 1 — confirmed from EOTech's own reticle image,
        // eotechinc.com/cdn/shop/files/EOTECH-Vudu-Reticle-MR5.svg).
        ReticlePreset(
            "EOTech VUDU MR5 (MRAD, FFP)",
            ReticleUnit.MIL, 1.0, 0.5, 9.0,
            ReticleStyle.EOTECH_MR5_TREE,
            postStart = 8.0
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
        // Firefield RapidStrike 1-10×24 SFP — CR1 reticle.
        // SFP, valid at 10× only. Calibrated for 5.56x45/.223 Rem 55gr FMJ, 100-yd zero.
        // Horseshoe arc (300° arc, 60° gap at bottom) + 1.34 MOA center dot (0–200 yd);
        // circle radius from IPSC 18" target at 300 yd (18"/300 yd = 5.73 MOA dia → 2.87 MOA r);
        // 300-yd holdover at arc gap exit (tip of vertical subtension); hashes at 400/500/600 yd.
        // holdoverMark depths estimated from .223 55gr FMJ ballistics at 100-yd zero.
        // Source: Firefield FF13075 user manual pp.14–15.
        ReticlePreset(
            "Firefield RapidStrike CR1 1-10x24 (MOA, SFP)",
            ReticleUnit.MOA, 2.87, 0.67, 22.0,
            ReticleStyle.HORSESHOE_BDC,
            holdoverMarks = listOf(6.0, 11.0, 17.0),
            windageMarks  = listOf(2.15, 1.72, 1.43)
        ),
        // ── German/Czech ──────────────────────────────────────────────────────────
        // ZB26/ZB30 anti-aircraft front spider sight (German WWII issue).
        // Iron ring sight: outer ring, 4 crosshair spokes, small center ring,
        // beads on all 4 spokes at ~45% radius, diagonal ticks at 45° on outer ring.
        // Angular values from 7.92mm Mauser physics (775 m/s) for aircraft at
        // 300 km/h: lead_mils = 83.3/775 × 1000 = 107.5 mils ≈ 370 MOA outer ring.
        // Proportions (inner ring ~16%, beads ~45%) from IMA/Aubrey product photos.
        // Exact values unconfirmed — TB/DV for ZB26 AA sight not located.
        // Source: IMA product photos, physics calculation.
        ReticlePreset(
            "German ZB26/ZB30 AA Spider Sight (MOA, 1×)",
            ReticleUnit.MOA, 370.0, 60.0, 450.0,
            ReticleStyle.SPIDER_SIGHT,
            windageMarks = listOf(165.0)
        ),
        // ── Holosun ──────────────────────────────────────────────────────────────
        // Holosun HS510C — 2 MOA center dot + 65 MOA ring, 1× red dot.
        ReticlePreset(
            "Holosun 510C (2 MOA / 65 MOA)",
            ReticleUnit.MOA, 32.5, 1.0, 40.0,
            ReticleStyle.HOLOSUN_510C
        ),
        // Holosun HS510C — 65 MOA ring only, no dot.
        ReticlePreset(
            "Holosun 510C (65 MOA ring only)",
            ReticleUnit.MOA, 32.5, 0.0, 40.0,
            ReticleStyle.HOLOSUN_510C
        ),
        // Holosun HS507COMP CRS — 2 MOA dot + 8/20/32 MOA selectable circles, 1× red dot.
        // holdoverMarks = ring radii (half-diameters): 4/10/16 MOA.
        // majorSpacing = outermost ring radius (for vertExtent reference).
        ReticlePreset(
            "Holosun 507 COMP (2 MOA / 8-20-32 MOA CRS)",
            ReticleUnit.MOA, 16.0, 1.0, 20.0,
            ReticleStyle.HOLOSUN_507COMP,
            holdoverMarks = listOf(4.0, 10.0, 16.0)
        ),
        // Holosun HS507COMP — 32 MOA ring only, no dot.
        ReticlePreset(
            "Holosun 507 COMP (32 MOA ring only)",
            ReticleUnit.MOA, 16.0, 0.0, 20.0,
            ReticleStyle.HOLOSUN_507COMP
        ),
        // Holosun HS507K — 2 MOA center dot + 32 MOA ring, 1× red dot.
        ReticlePreset(
            "Holosun 507K (2 MOA / 32 MOA)",
            ReticleUnit.MOA, 16.0, 1.0, 20.0,
            ReticleStyle.HOLOSUN_507K
        ),
        // Holosun HS507K — 32 MOA ring only, no dot.
        ReticlePreset(
            "Holosun 507K (32 MOA ring only)",
            ReticleUnit.MOA, 16.0, 0.0, 20.0,
            ReticleStyle.HOLOSUN_507K
        ),
        // ── Leupold ──────────────────────────────────────────────────────────────
        // Leupold VX-Freedom 1.5-4x20 SFP — MOA-Ring reticle.
        // Large 40 MOA diameter ring for fast close-range target acquisition.
        // Thin crosshair with 3.4 MOA center circle; fine BDC ticks at 5 MOA spacing
        // from 5–35 MOA; tapered heavy post begins at 40 MOA (4.5 MOA wide → 8.9 MOA wide).
        // Lead/windage markers at ±6.5 MOA and ±13.0 MOA on H arm.
        // SFP: all subtensions valid at 4× (max) magnification only.
        // Source: Leupold MOA-Ring reticle diagram (leupold.com, 2021).
        ReticlePreset(
            "Leupold VX-Freedom MOA-Ring 1.5-4x20 (MOA, SFP)",
            ReticleUnit.MOA, 20.0, 1.7, 55.0,
            ReticleStyle.RING_BDC,
            holdoverMarks = listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0),
            windageMarks  = listOf(6.5, 13.0),
            postStart     = 40.0
        ),
        // ── SIG Sauer ────────────────────────────────────────────────────────────
        // SIG Sauer Tango SPR 1-4×24 SFP — BDC1 reticle. SFP, valid at 4× only.
        // Thick H posts at ±10 MOA (2 MOA wide, 1.5 MOA half-height); thin crosshair
        // with 0.25 MOA ticks; holdover marks at 3.75/6.50/9.50/14.50 MOA.
        // Source: SIG Sauer Tango SPR owner's manual, p.16.
        ReticlePreset(
            "SIG Sauer Tango SPR BDC1 (MOA, SFP)",
            ReticleUnit.MOA, 0.0, 0.0, 17.0,
            ReticleStyle.BDC,
            holdoverMarks = listOf(3.75, 6.50, 9.50, 14.50),
            windageMarks  = listOf(2.0, 4.0, 6.0, 8.0),
            postStart     = 10.0
        ),
        // SIG Sauer Tango SPR 1-4×24 SFP — FL-4 reticle. SFP, valid at 4× only.
        // Main crosshair is thin; lower BDC stadia are at 2.86/3.44/4.30/5.73 MOA
        // with 0.75 MOA half-widths; horizontal reference triangles are at
        // ±3.13/±5.95 MOA, with outer horizontal hashes at ±13.48 MOA.
        // Lower post is 20.22 MOA tall with a 15° included tip angle.
        // Open outlines in the source diagram are dimensional callouts, not reticle artwork.
        // Source: SIG Sauer Tango SPR owner's manual, p.17.
        ReticlePreset(
            "SIG Sauer Tango SPR FL-4 (MOA, SFP)",
            ReticleUnit.MOA, 12.24, 6.12, 22.68,
            ReticleStyle.SIG_FL4,
            holdoverMarks = listOf(2.86, 3.44, 4.30, 5.73),
            windageMarks  = listOf(13.48),
            postStart     = 2.20
        ),
        // SIG Sauer Tango-MSR 5-30×56 FFP — MRAD Milling 2.0 reticle. FFP, all mags.
        // Fine ladder crosshair: 0.2 MRAD minor ticks, numbered every 2 MRAD; short
        // top post (4 MRAD); thin vertical drop-lines from each major windage tick down
        // to 14 MRAD (no horizontal rungs); thick outer posts beyond ±14 MRAD.
        // Dedicated SIG_MRAD_MILLING drawing style — see drawReticleSection().
        // Source: SIG Sauer TANGO-MSR Operator's Manual, p.28 (7404914-01 R00).
        ReticlePreset(
            "SIG Sauer Tango-MSR MRAD Milling 2.0 (MRAD, FFP)",
            ReticleUnit.MIL, 2.0, 0.2, 16.0,
            ReticleStyle.SIG_MRAD_MILLING,
            postStart = 14.0
        ),
        // SIG Sauer Tango-MSR 5-30×56 FFP — MOA Milling 2.0 reticle. FFP, all mags.
        // Plain 4-way ladder/comb crosshair (no drop-lines/mesh, distinct from the MRAD
        // version): 0.5 MOA minor ticks, numbered every 2 MOA; short 10 MOA top post,
        // long 32 MOA bottom post (both hardcoded in the drawing to match the manual
        // diagram); thick outer posts beyond ±30 MOA / 32 MOA below center.
        // Dedicated SIG_MOA_MILLING drawing style — see drawReticleSection().
        // Source: SIG Sauer TANGO-MSR Operator's Manual, p.30 (7404914-01 R00).
        ReticlePreset(
            "SIG Sauer Tango-MSR MOA Milling 2.0 (MOA, FFP)",
            ReticleUnit.MOA, 2.0, 0.5, 36.0,
            ReticleStyle.SIG_MOA_MILLING,
            postStart = 30.0
        ),
        // ── Trijicon ─────────────────────────────────────────────────────────────
        // Trijicon ACOG TA31 4×32 — Donut BDC reticle (5.56mm/.223 Rem, 100m zero).
        // Fixed 4× — subtensions always accurate. Top of donut ring = POA at 100m.
        // Donut ring radius ≈ 2.0 MOA; center dot radius ≈ 0.3 MOA.
        // BDC stadia below for 400–800m; widths derived from 19" ranging:
        //   ±2.08 / ±1.66 / ±1.39 / ±1.19 / ±1.04 MOA at 400/500/600/700/800m.
        // Holdover depths (MOA below center) from M855 trajectory at 100m zero —
        //   approximate; verify against Trijicon subtension card for exact values.
        // Source: Trijicon ACOG Operator's Manual (BAC models), pp.26–28.
        ReticlePreset(
            "Trijicon ACOG TA31 Donut (MOA, 4×)",
            ReticleUnit.MOA, 2.0, 0.3, 35.0,
            ReticleStyle.ACOG_DONUT,
            holdoverMarks = listOf(7.2, 11.8, 17.0, 23.5, 31.0)
        ),
        // ── U.S. Army ─────────────────────────────────────────────────────────────
        // U.S. 2.36" Bazooka (M9/M9A1) — D7161556 Reflecting Sight Assembly, 1×.
        // Reticle: thin crosshair + 4 concentric lead rings for 10/20/30/40 mph crossing targets.
        // Ring radii derived from rocket muzzle velocity (265 ft/s):
        //   lead_mils = V_mph × 1.4667 / 265 × 1000 = V_mph × 5.536 mils (range-independent).
        //   Converted to MOA (×3.4377): rings at ~190/380/570/760 MOA.
        // Note: exact values from TB 9-294-9 not available; these are physics estimates.
        // Source: TM 9-294, SARCO D7161556 product description, physics calculation.
        ReticlePreset(
            "U.S. 2.36\" Bazooka D7161556 (MOA, 1×)",
            ReticleUnit.MOA, 10.0, 0.0, 840.0,
            ReticleStyle.LEAD_RINGS,
            holdoverMarks = listOf(190.0, 380.0, 570.0, 760.0)
        ),
        // ── UUQ ──────────────────────────────────────────────────────────────────
        // UUQ Ranger ER 3×32 Tri-Color Fiber Prism Scope — Arrow/Chevron BDC reticle.
        // Fixed 3× prism — subtensions always accurate.
        // Center: red upward-pointing arrow/chevron above the BDC stem.
        // Horizontal arms: separated side stadia with endpoint/interior ticks.
        // BDC post below arrow: 1 MOA ticks from 4–11 MOA, labeled 4 / 6 / 8 / 10.
        // Calibrated for .22LR / .223 Rem / .308 Win (nominal 100 yd zero).
        // Source: UUQ product page reticle diagram.
        ReticlePreset(
            "UUQ Ranger ER Arrow BDC (MOA, 3×)",
            ReticleUnit.MOA, 0.8, 1.2, 12.0,
            ReticleStyle.CHEVRON_BDC,
            holdoverMarks = listOf(4.0, 6.0, 8.0, 10.0),
            windageMarks  = listOf(5.0, 7.5, 10.0, 13.0)
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
        // Holdover hashmarks at 1.5/4.5/7.5 MOA; thick post begins at 11 MOA (top edge
        // of the bottom post, not a 4th hashmark). Source: Vortex Dead-Hold BDC MOA
        // reticle manual.
        ReticlePreset(
            "Vortex Dead-Hold BDC (MOA, SFP)",
            ReticleUnit.MOA, 0.0, 0.0, 13.0,
            ReticleStyle.BDC,
            holdoverMarks = listOf(1.5, 4.5, 7.5),
            windageMarks  = listOf(2.0, 4.0, 6.0),
            postStart     = 11.0
        ),
        // Vortex EBR-7C — Venom 5-25×56, Viper PST Gen II, Strike Eagle, Razor HD Gen II. FFP.
        ReticlePreset(
            "Vortex EBR-7C (MOA, FFP)",
            ReticleUnit.MOA, 4.0, 1.0, 40.0,
            ReticleStyle.VORTEX_EBR7C_MOA_TREE,
            postStart = 26.0
        ),
        // Vortex EBR-7C MRAD — Venom 5-25×56 FFP MRAD variant. Same reticle geometry as the
        // MOA version above, numbered/scaled in mils: major hashes every 1 mil, minor every
        // 0.5 mil, dot-grid tree below center, thick outer posts starting near FOV edge.
        // postStart/vertExtent converted from the MOA-model FOV geometry (26/40 MOA × 0.2909 mil/MOA).
        ReticlePreset(
            "Vortex EBR-7C (MRAD, FFP)",
            ReticleUnit.MIL, 1.0, 0.5, 11.6,
            ReticleStyle.VORTEX_EBR7C_MRAD_TREE,
            postStart = 7.6
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
        ),
        // Vortex VMR-4 — Viper HD 5-25x50 FFP. CORRECTED (was previously implemented as a
        // dot-grid Christmas tree, copying the EBR-7C assumption — wrong design). The real
        // reticle is a ladder crosshair with dotted windage drop-lines hanging from each
        // major tick (dots grow with distance). Numbered stadia 4/8/12/16/20/24 MOA (4 MOA
        // major / 1 MOA minor); thick posts begin right after 24 MOA. Top arm length (8 MOA)
        // is an estimate — not clearly legible in the source scan; everything else is exact.
        // Source: Vortex Reticle Manual M-00358-0 (VMR-4 MOA), pp.2-3.
        ReticlePreset(
            "Vortex VMR-4 (MOA, FFP)",
            ReticleUnit.MOA, 4.0, 1.0, 26.0,
            ReticleStyle.VORTEX_VMR4_MOA_TREE,
            postStart = 24.0
        ),
        // Vortex VMR-4 MRAD — same corrected design as the MOA version, mil-graduated:
        // 1 MRAD major / 0.5 MRAD minor hashmarks, thick posts begin right after 6 MRAD.
        // Top arm length (4 MRAD) is an estimate. Source: Vortex Reticle Manual M-00359-0
        // (VMR-4 MRAD), pp.2-3.
        ReticlePreset(
            "Vortex VMR-4 (MRAD, FFP)",
            ReticleUnit.MIL, 1.0, 0.5, 8.0,
            ReticleStyle.VORTEX_VMR4_MRAD_TREE,
            postStart = 6.0
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
        val category: AmmoCategory = AmmoCategory.RIFLE,
        val caliber: String = ""
    )

    val PRESETS: List<AmmoPreset> = listOf(
        // ── Rifle ─────────────────────────────────────────────────────────────────
        // .22 cal
        AmmoPreset("5.45×39 7N6 53gr",            2900.0, 0.351,  53.0, 2.6, 6.0, caliber = "5.45×39"),
        AmmoPreset("5.45×39 7N10 56gr",           2900.0, 0.351,  56.0, 2.6, 6.0, caliber = "5.45×39"),
        AmmoPreset("5.45×39 60gr FMJ (Tula)",     2936.0, 0.329,  60.0, 2.6, 6.0, caliber = "5.45×39"),
        AmmoPreset("5.45×39 60gr V-MAX",          2810.0, 0.285,  60.0, 2.6, 6.0, caliber = "5.45×39"),
        // .204 Ruger
        AmmoPreset("204 Ruger 32gr V-MAX",        4225.0, 0.210,  32.0, 2.6, 6.0, caliber = "204 Ruger"),
        AmmoPreset("204 Ruger 40gr V-MAX",        3900.0, 0.275,  40.0, 2.6, 6.0, caliber = "204 Ruger"),
        AmmoPreset("204 Ruger 45gr SP",           3625.0, 0.225,  45.0, 2.6, 6.0, caliber = "204 Ruger"),
        AmmoPreset("223 Rem 55gr FMJ",            3215.0, 0.202,  55.0, 2.6, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 62gr FMJ BT",         3100.0, 0.265,  62.0, 2.6, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 77gr SMK",            2700.0, 0.185,  77.0, 2.6, 6.0, DragModel.G7, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 55gr HP",             3240.0, 0.235,  55.0, 2.6, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 69gr SMK",            2950.0, 0.191,  69.0, 2.6, 6.0, DragModel.G7, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 75gr BTHP",           2790.0, 0.220,  75.0, 2.6, 6.0, DragModel.G7, caliber = "223 Rem/5.56"),
        AmmoPreset("223 Rem 77gr HPBT (SBR)",    2750.0, 0.372,  77.0, 1.5, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("224 Valkyrie 60gr V-MAX",     3200.0, 0.265,  60.0, 2.6, 6.0, caliber = "224 Valkyrie"),
        AmmoPreset("224 Valkyrie 75gr ELD-M",     3000.0, 0.240,  75.0, 2.6, 6.0, DragModel.G7, caliber = "224 Valkyrie"),
        AmmoPreset("224 Valkyrie 90gr SMK",       2700.0, 0.285,  90.0, 2.6, 6.0, DragModel.G7, caliber = "224 Valkyrie"),
        AmmoPreset("M193 (5.56×45)",              3250.0, 0.243,  55.0, 2.6, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("M855 (5.56×45)",              3025.0, 0.304,  62.0, 2.6, 6.0, caliber = "223 Rem/5.56"),
        AmmoPreset("M855A1 EPR (5.56×45)",        3150.0, 0.152,  62.0, 2.6, 6.0, DragModel.G7, caliber = "223 Rem/5.56"),
        AmmoPreset("Mk262 Mod1 77gr OTM",         2848.0, 0.185,  77.0, 2.6, 6.0, DragModel.G7, caliber = "223 Rem/5.56"),
        // .243/6mm
        AmmoPreset("243 Win 55gr BT",             3850.0, 0.230,  55.0, 2.6, 6.0, caliber = "243 Win"),
        AmmoPreset("243 Win 80gr SP",             3330.0, 0.325,  80.0, 2.6, 6.0, caliber = "243 Win"),
        AmmoPreset("243 Win 95gr BT",             3100.0, 0.400,  95.0, 2.6, 6.0, caliber = "243 Win"),
        AmmoPreset("243 Win 100gr PP",            2960.0, 0.380, 100.0, 2.6, 6.0, caliber = "243 Win"),
        AmmoPreset("6mm ARC 103gr ELD-X",         2800.0, 0.258, 103.0, 2.6, 6.0, DragModel.G7, caliber = "6mm ARC"),
        AmmoPreset("6mm ARC 105gr BTHP",          2750.0, 0.253, 105.0, 2.6, 6.0, DragModel.G7, caliber = "6mm ARC"),
        AmmoPreset("6mm ARC 105gr FMJ Frontier",  2700.0, 0.530, 105.0, 2.6, 6.0, caliber = "6mm ARC"),
        AmmoPreset("6mm ARC 106gr TAP ELD-M",       2610.0, 0.580, 106.0, 2.6, 6.0, caliber = "6mm ARC"),
        AmmoPreset("6mm ARC 108gr ELD-M",         2750.0, 0.270, 108.0, 2.6, 6.0, DragModel.G7, caliber = "6mm ARC"),
        AmmoPreset("6mm CM 103gr ELD-X",          3050.0, 0.258, 103.0, 2.6, 6.0, DragModel.G7, caliber = "6mm CM"),
        AmmoPreset("6mm CM 108gr ELD-M",          2960.0, 0.270, 108.0, 2.6, 6.0, DragModel.G7, caliber = "6mm CM"),
        // 6mm Rem
        AmmoPreset("6mm Rem 80gr SP",             3470.0, 0.340,  80.0, 2.6, 6.0, caliber = "6mm Rem"),
        AmmoPreset("6mm Rem 100gr PSP",           3100.0, 0.414, 100.0, 2.6, 6.0, caliber = "6mm Rem"),
        AmmoPreset("6mm Rem 105gr BT",            3100.0, 0.430, 105.0, 2.6, 6.0, caliber = "6mm Rem"),
        // .257/25 cal
        // .257 Roberts
        AmmoPreset("257 Roberts 100gr SP",        2900.0, 0.360, 100.0, 2.6, 6.0, caliber = "257 Roberts"),
        AmmoPreset("257 Roberts 117gr SP",        2780.0, 0.370, 117.0, 2.6, 6.0, caliber = "257 Roberts"),
        AmmoPreset("257 Roberts +P 120gr",        2980.0, 0.391, 120.0, 2.6, 6.0, caliber = "257 Roberts"),
        AmmoPreset("25-06 Rem 87gr V-MAX",        3440.0, 0.400,  87.0, 2.6, 6.0, caliber = "25-06 Rem"),
        AmmoPreset("25-06 Rem 100gr BT",          3210.0, 0.418, 100.0, 2.6, 6.0, caliber = "25-06 Rem"),
        AmmoPreset("25-06 Rem 117gr SST",         2990.0, 0.370, 117.0, 2.6, 6.0, caliber = "25-06 Rem"),
        // .260/6.5mm
        AmmoPreset("6.5 Grendel 90gr TNT HP",     2880.0, 0.270,  90.0, 2.6, 6.0, caliber = "6.5 Grendel"),
        AmmoPreset("6.5 Grendel 123gr ELD-M",     2580.0, 0.263, 123.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 Grendel"),
        AmmoPreset("6.5 Grendel 130gr Hybrid",    2500.0, 0.284, 130.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 Grendel"),
        // 6.5×55 Swedish
        AmmoPreset("6.5×55 120gr SP",             2780.0, 0.416, 120.0, 2.6, 6.0, caliber = "6.5×55"),
        AmmoPreset("6.5×55 140gr BTSP",           2625.0, 0.435, 140.0, 2.6, 6.0, caliber = "6.5×55"),
        AmmoPreset("6.5×55 156gr RN",             2430.0, 0.375, 156.0, 2.6, 6.0, caliber = "6.5×55"),
        AmmoPreset("260 Rem 120gr AccuTip",       2890.0, 0.480, 120.0, 2.6, 6.0, caliber = "260 Rem"),
        AmmoPreset("260 Rem 130gr ELD-M",         2875.0, 0.554, 130.0, 2.6, 6.0, caliber = "260 Rem"),
        AmmoPreset("260 Rem 140gr Core-Lokt",     2750.0, 0.435, 140.0, 2.6, 6.0, caliber = "260 Rem"),
        AmmoPreset("6.5 CM 120gr ELD-M",          2910.0, 0.245, 120.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 CM"),
        AmmoPreset("6.5 CM 129gr SST",            2810.0, 0.485, 129.0, 2.6, 6.0, caliber = "6.5 CM"),
        AmmoPreset("6.5 CM 130gr Berger Hybrid",  2875.0, 0.287, 130.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 CM"),
        AmmoPreset("6.5 CM 140gr ELD-M",          2710.0, 0.326, 140.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 CM"),
        AmmoPreset("6.5 CM 143gr ELD-X",          2700.0, 0.315, 143.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 CM"),
        AmmoPreset("6.5 CM 147gr ELD-M",          2695.0, 0.351, 147.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 CM"),
        AmmoPreset("6.5 PRC 143gr ELD-X",         2960.0, 0.315, 143.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 PRC"),
        AmmoPreset("6.5 PRC 147gr ELD-M",         2910.0, 0.352, 147.0, 2.6, 6.0, DragModel.G7, caliber = "6.5 PRC"),
        // .270/6.8mm
        AmmoPreset("270 Win 130gr BT",            3060.0, 0.337, 130.0, 2.6, 6.0, caliber = "270 Win"),
        AmmoPreset("270 Win 140gr AccuBond",      3100.0, 0.206, 140.0, 2.6, 6.0, DragModel.G7, caliber = "270 Win"),
        AmmoPreset("270 Win 150gr SP",            2800.0, 0.205, 150.0, 2.6, 6.0, DragModel.G7, caliber = "270 Win"),
        AmmoPreset("270 WSM 130gr BT",            3300.0, 0.433, 130.0, 2.6, 6.0, caliber = "270 WSM"),
        AmmoPreset("270 WSM 140gr AccuBond",      3100.0, 0.496, 140.0, 2.6, 6.0, caliber = "270 WSM"),
        AmmoPreset("270 WSM 150gr Silvertip",     3120.0, 0.496, 150.0, 2.6, 6.0, caliber = "270 WSM"),
        AmmoPreset("6.8 Western 165gr AccuBond LR", 2970.0, 0.344, 165.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 Western"),
        AmmoPreset("6.8 Western 170gr BST",       2920.0, 0.327, 170.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 Western"),
        AmmoPreset("6.8 Western 175gr SGK",       2835.0, 0.361, 175.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 Western"),
        AmmoPreset("6.8 SPC 100gr GMX",           2700.0, 0.138, 100.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 SPC"),
        AmmoPreset("6.8 SPC 110gr V-MAX",         2600.0, 0.186, 110.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 SPC"),
        AmmoPreset("6.8 SPC 115gr SMK OTM",       2625.0, 0.162, 115.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 SPC"),
        AmmoPreset("6.8 SPC 120gr SST",           2600.0, 0.202, 120.0, 2.6, 6.0, DragModel.G7, caliber = "6.8 SPC"),
        // .277 Fury / 6.8x51 — SIG's hybrid-case cartridge, developed for the Army's NGSW
        // program (XM7 rifle). Same .277" bore as 6.8 SPC/6.8 Western above, grouped together
        // like the 223/5.56 and 308/7.62x51 caliber pairs. All five loads are SIG's full
        // current commercial lineup, including the 113gr Hybrid Ball — the civilian-sold
        // solid-copper load derived from the military general-purpose round.
        AmmoPreset("277 Fury 130gr Venari SP",    2710.0, 0.409, 130.0, 2.6, 6.0, caliber = "277 Fury/6.8x51"),
        AmmoPreset("277 Fury 135gr Elite FMJ",    3000.0, 0.475, 135.0, 2.6, 6.0, caliber = "277 Fury/6.8x51"),
        AmmoPreset("277 Fury 150gr Elite AccuBond", 3120.0, 0.500, 150.0, 2.6, 6.0, caliber = "277 Fury/6.8x51"),
        AmmoPreset("277 Fury 155gr Hybrid Match",  3000.0, 0.549, 155.0, 2.6, 6.0, caliber = "277 Fury/6.8x51"),
        AmmoPreset("277 Fury 113gr Hybrid Ball",   3200.0, 0.330, 113.0, 2.6, 6.0, caliber = "277 Fury/6.8x51"),
        // .280/7mm-class
        // 280 Rem
        AmmoPreset("280 Rem 140gr BT",            3000.0, 0.269, 140.0, 2.6, 6.0, DragModel.G7, caliber = "280 Rem"),
        AmmoPreset("280 Rem 160gr AccuBond",      2840.0, 0.255, 160.0, 2.6, 6.0, DragModel.G7, caliber = "280 Rem"),
        AmmoPreset("280 Rem 175gr PP",            2645.0, 0.391, 175.0, 2.6, 6.0, caliber = "280 Rem"),
        // 280 Ackley Improved
        AmmoPreset("280 AI 140gr BT",             3150.0, 0.269, 140.0, 2.6, 6.0, DragModel.G7, caliber = "280 AI"),
        AmmoPreset("280 AI 162gr ELD-X",          2970.0, 0.336, 162.0, 2.6, 6.0, DragModel.G7, caliber = "280 AI"),
        AmmoPreset("280 AI 175gr PP",             2850.0, 0.456, 175.0, 2.6, 6.0, caliber = "280 AI"),
        // .284/7mm
        AmmoPreset("7mm Rem Mag 139gr",           3150.0, 0.235, 139.0, 2.6, 6.0, DragModel.G7, caliber = "7mm Rem Mag"),
        AmmoPreset("7mm Rem Mag 150gr BT",        3100.0, 0.250, 150.0, 2.6, 6.0, DragModel.G7, caliber = "7mm Rem Mag"),
        AmmoPreset("7mm Rem Mag 162gr ELD-X",     3084.0, 0.295, 162.0, 2.6, 6.0, DragModel.G7, caliber = "7mm Rem Mag"),
        AmmoPreset("7mm Rem Mag 175gr SP",        2860.0, 0.475, 175.0, 2.6, 6.0, caliber = "7mm Rem Mag"),
        AmmoPreset("7mm PRC 160gr CX",            2925.0, 0.315, 160.0, 2.6, 6.0, DragModel.G7, caliber = "7mm PRC"),
        AmmoPreset("7mm PRC 175gr ELD-X",         2860.0, 0.343, 175.0, 2.6, 6.0, DragModel.G7, caliber = "7mm PRC"),
        AmmoPreset("7mm PRC 180gr ELD-M",         2800.0, 0.352, 180.0, 2.6, 6.0, DragModel.G7, caliber = "7mm PRC"),
        // 7×57 Mauser
        AmmoPreset("7×57 Mauser 140gr SP",        2660.0, 0.330, 140.0, 2.6, 6.0, caliber = "7×57 Mauser"),
        AmmoPreset("7×57 Mauser 154gr SP",        2690.0, 0.356, 154.0, 2.6, 6.0, caliber = "7×57 Mauser"),
        AmmoPreset("7×57 Mauser 175gr PP",        2490.0, 0.347, 175.0, 2.6, 6.0, caliber = "7×57 Mauser"),
        AmmoPreset("7mm-08 139gr SST",            2950.0, 0.486, 139.0, 2.6, 6.0, caliber = "7mm-08"),
        AmmoPreset("7mm-08 140gr BT",             2825.0, 0.319, 140.0, 2.6, 6.0, DragModel.G7, caliber = "7mm-08"),
        AmmoPreset("7mm-08 140gr Fusion",         2850.0, 0.390, 140.0, 2.6, 6.0, caliber = "7mm-08"),
        AmmoPreset("7mm-08 150gr JSP",            2650.0, 0.408, 150.0, 2.6, 6.0, caliber = "7mm-08"),
        // .308/30-cal
        // 7.62×54R
        AmmoPreset("7.62×54R 148gr FMJ 7N1",     2838.0, 0.390, 148.0, 2.6, 6.0, caliber = "7.62×54R"),
        AmmoPreset("7.62×54R 150gr SP",           2900.0, 0.398, 150.0, 2.6, 6.0, caliber = "7.62×54R"),
        AmmoPreset("7.62×54R 174gr FMJ",          2600.0, 0.490, 174.0, 2.6, 6.0, caliber = "7.62×54R"),
        AmmoPreset("7.62×39 122gr FMJ",           2418.0, 0.257, 122.0, 2.6, 6.0, caliber = "7.62×39"),
        AmmoPreset("7.62×39 123gr HP",            2350.0, 0.270, 123.0, 2.6, 6.0, caliber = "7.62×39"),
        AmmoPreset("30 Carbine 110gr FMJ",        1990.0, 0.166, 110.0, 2.6, 6.0, caliber = "30 Carbine"),
        AmmoPreset("30 Carbine 110gr FTX",        2000.0, 0.166, 110.0, 2.6, 6.0, caliber = "30 Carbine"),
        AmmoPreset("30 Carbine 125gr HC",         1950.0, 0.126, 125.0, 2.6, 6.0, caliber = "30 Carbine"),
        AmmoPreset("30-30 Win 150gr FP",          2390.0, 0.186, 150.0, 2.6, 6.0, caliber = "30-30 Win"),
        AmmoPreset("30-30 Win 160gr FTX",         2400.0, 0.330, 160.0, 2.6, 6.0, caliber = "30-30 Win"),
        AmmoPreset("30-30 Win 170gr FP",          2200.0, 0.227, 170.0, 2.6, 6.0, caliber = "30-30 Win"),
        // 300 Savage
        AmmoPreset("300 Savage 150gr PP",         2630.0, 0.330, 150.0, 2.6, 6.0, caliber = "300 Savage"),
        AmmoPreset("300 Savage 180gr SP",         2350.0, 0.383, 180.0, 2.6, 6.0, caliber = "300 Savage"),
        AmmoPreset("30-06 150gr SP",              2910.0, 0.310, 150.0, 2.6, 6.0, caliber = "30-06"),
        AmmoPreset("30-06 165gr BT",              2800.0, 0.225, 165.0, 2.6, 6.0, DragModel.G7, caliber = "30-06"),
        AmmoPreset("30-06 165gr SST",             2800.0, 0.420, 165.0, 2.6, 6.0, caliber = "30-06"),
        AmmoPreset("30-06 180gr SP",              2700.0, 0.205, 180.0, 2.6, 6.0, DragModel.G7, caliber = "30-06"),
        AmmoPreset("30-06 220gr RN",              2410.0, 0.300, 220.0, 2.6, 6.0, caliber = "30-06"),
        AmmoPreset("300 BLK 110gr V-MAX",         2375.0, 0.290, 110.0, 2.6, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 125gr PPU",           2329.0, 0.325, 125.0, 2.6, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 125gr SST (SBR)",    2200.0, 0.305, 125.0, 1.5, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 147gr FMJ",           1920.0, 0.398, 147.0, 2.6, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 200gr (subsonic)",    1050.0, 0.540, 200.0, 2.6, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 220gr (subsonic)",    1045.0, 0.608, 220.0, 2.6, 6.0, caliber = "300 BLK"),
        AmmoPreset("300 BLK 220gr BT sub (SBR)", 1075.0, 0.472, 220.0, 1.5, 6.0, caliber = "300 BLK"),
        // .338/8.6mm BLK
        AmmoPreset("8.6 BLK 135gr CX",             2650.0, 0.193, 135.0, 2.6, 6.0, DragModel.G7, caliber = "8.6 BLK"),
        AmmoPreset("8.6 BLK 210gr Barnes TSX",      2000.0, 0.430, 210.0, 2.6, 6.0, caliber = "8.6 BLK"),
        AmmoPreset("8.6 BLK 250gr Sub-X (subsonic)",1050.0, 0.540, 250.0, 2.6, 6.0, caliber = "8.6 BLK"),
        AmmoPreset("8.6 BLK 300gr SMK (subsonic)",  1050.0, 0.750, 300.0, 2.6, 6.0, caliber = "8.6 BLK"),
        AmmoPreset("300 PRC 212gr ELD-X",         2860.0, 0.334, 212.0, 2.6, 6.0, DragModel.G7, caliber = "300 PRC"),
        AmmoPreset("300 PRC 225gr ELD-M",         2810.0, 0.391, 225.0, 2.6, 6.0, DragModel.G7, caliber = "300 PRC"),
        AmmoPreset("300 PRC 245gr Elite Hunter",  2720.0, 0.413, 245.0, 2.6, 6.0, DragModel.G7, caliber = "300 PRC"),
        AmmoPreset("300 PRC 250gr A-Tip",         2800.0, 0.442, 250.0, 2.6, 6.0, DragModel.G7, caliber = "300 PRC"),
        AmmoPreset("300 Win Mag 150gr PP",        3290.0, 0.310, 150.0, 2.6, 6.0, caliber = "300 Win Mag"),
        AmmoPreset("300 Win Mag 168gr SMK",       3000.0, 0.235, 168.0, 2.6, 6.0, DragModel.G7, caliber = "300 Win Mag"),
        AmmoPreset("300 Win Mag 180gr AccuBond",  2950.0, 0.250, 180.0, 2.6, 6.0, DragModel.G7, caliber = "300 Win Mag"),
        AmmoPreset("300 Win Mag 190gr SMK",       2950.0, 0.290, 190.0, 2.6, 6.0, DragModel.G7, caliber = "300 Win Mag"),
        AmmoPreset("300 Win Mag 200gr Partition", 2930.0, 0.245, 200.0, 2.6, 6.0, DragModel.G7, caliber = "300 Win Mag"),
        AmmoPreset("300 WSM 150gr BT",            3300.0, 0.435, 150.0, 2.6, 6.0, caliber = "300 WSM"),
        AmmoPreset("300 WSM 180gr AccuBond",      2970.0, 0.250, 180.0, 2.6, 6.0, DragModel.G7, caliber = "300 WSM"),
        AmmoPreset("300 WSM 180gr Power Point",   2970.0, 0.474, 180.0, 2.6, 6.0, caliber = "300 WSM"),
        // .303 British
        AmmoPreset("303 British 150gr FMJ",       2700.0, 0.330, 150.0, 2.6, 6.0, caliber = "303 British"),
        AmmoPreset("303 British 174gr FMJ",       2540.0, 0.384, 174.0, 2.6, 6.0, caliber = "303 British"),
        AmmoPreset("303 British 180gr SP",        2460.0, 0.396, 180.0, 2.6, 6.0, caliber = "303 British"),
        AmmoPreset("M80 (7.62×51 NATO)",          2750.0, 0.398, 147.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 125gr SST (SBR)",     3250.0, 0.305, 125.0, 1.5, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 150gr FMJ",           2820.0, 0.409, 150.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 155gr Palma",         2940.0, 0.219, 155.0, 2.6, 6.0, DragModel.G7, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 165gr BT",            2700.0, 0.450, 165.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 168gr SMK",           2650.0, 0.224, 168.0, 2.6, 6.0, DragModel.G7, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 175gr SMK",           2600.0, 0.250, 175.0, 2.6, 6.0, DragModel.G7, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 180gr SP",            2600.0, 0.383, 180.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 190gr Fusion (subsonic)", 1000.0, 0.494, 190.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 200gr FMJBT (subsonic)",  1066.0, 0.235, 200.0, 2.6, 6.0, DragModel.G7, caliber = "308 Win/7.62x51"),
        AmmoPreset("308 Win 205gr HP (subsonic)",     1000.0, 0.480, 205.0, 2.6, 6.0, caliber = "308 Win/7.62x51"),
        // .338
        AmmoPreset("338 Win Mag 200gr AccuBond",  2960.0, 0.225, 200.0, 2.6, 6.0, DragModel.G7, caliber = "338 Win Mag"),
        AmmoPreset("338 Win Mag 225gr AccuBond",  2785.0, 0.249, 225.0, 2.6, 6.0, DragModel.G7, caliber = "338 Win Mag"),
        AmmoPreset("338 Win Mag 250gr Partition", 2650.0, 0.473, 250.0, 2.6, 6.0, caliber = "338 Win Mag"),
        AmmoPreset("338 Lapua 250gr SMK",         2950.0, 0.340, 250.0, 2.6, 6.0, DragModel.G7, caliber = "338 Lapua"),
        AmmoPreset("338 Lapua 285gr ELD-M",       2900.0, 0.405, 285.0, 2.6, 6.0, DragModel.G7, caliber = "338 Lapua"),
        AmmoPreset("338 Lapua 300gr SMK",         2650.0, 0.390, 300.0, 2.6, 6.0, DragModel.G7, caliber = "338 Lapua"),
        // .375
        AmmoPreset("375 H&H 250gr BT",            2900.0, 0.385, 250.0, 2.6, 6.0, caliber = "375 H&H"),
        AmmoPreset("375 H&H 270gr SP",            2690.0, 0.366, 270.0, 2.6, 6.0, caliber = "375 H&H"),
        AmmoPreset("375 H&H 300gr FMJ",           2530.0, 0.338, 300.0, 2.6, 6.0, caliber = "375 H&H"),
        // .35 cal
        // 35 Rem
        AmmoPreset("35 Rem 150gr FP",             2300.0, 0.186, 150.0, 2.6, 6.0, caliber = "35 Rem"),
        AmmoPreset("35 Rem 200gr SP",             2080.0, 0.200, 200.0, 2.6, 6.0, caliber = "35 Rem"),
        // 35 Whelen
        AmmoPreset("35 Whelen 200gr SP",          2910.0, 0.306, 200.0, 2.6, 6.0, caliber = "35 Whelen"),
        AmmoPreset("35 Whelen 225gr AccuBond",    2700.0, 0.430, 225.0, 2.6, 6.0, caliber = "35 Whelen"),
        AmmoPreset("35 Whelen 250gr Partition",   2400.0, 0.446, 250.0, 2.6, 6.0, caliber = "35 Whelen"),
        AmmoPreset("350 Legend 150gr",            2325.0, 0.223, 150.0, 2.6, 6.0, caliber = "350 Legend"),
        AmmoPreset("350 Legend 180gr",            2100.0, 0.200, 180.0, 2.6, 6.0, caliber = "350 Legend"),
        // 400 Legend — straight-wall hunting cartridge, Winchester's full current lineup.
        AmmoPreset("400 Legend 190gr Deer Season XP", 2400.0, 0.198, 190.0, 2.6, 6.0, caliber = "400 Legend"),
        AmmoPreset("400 Legend 215gr Power-Point", 2250.0, 0.206, 215.0, 2.6, 6.0, caliber = "400 Legend"),
        AmmoPreset("400 Legend 300gr Super Suppressed (subsonic)", 1060.0, 0.318, 300.0, 2.6, 6.0, caliber = "400 Legend"),
        // .45 cal rifle
        // 444 Marlin
        AmmoPreset("444 Marlin 240gr FP",         2350.0, 0.175, 240.0, 2.6, 6.0, caliber = "444 Marlin"),
        AmmoPreset("444 Marlin 265gr FP",         2325.0, 0.200, 265.0, 2.6, 6.0, caliber = "444 Marlin"),
        AmmoPreset("444 Marlin 300gr HP",         2050.0, 0.204, 300.0, 2.6, 6.0, caliber = "444 Marlin"),
        // 45-70 Govt
        AmmoPreset("45-70 Govt 300gr JHP",        1880.0, 0.185, 300.0, 2.6, 6.0, caliber = "45-70 Govt"),
        AmmoPreset("45-70 Govt 325gr FTX",        2050.0, 0.230, 325.0, 2.6, 6.0, caliber = "45-70 Govt"),
        AmmoPreset("45-70 Govt 405gr FP",         1330.0, 0.200, 405.0, 2.6, 6.0, caliber = "45-70 Govt"),
        AmmoPreset("45-70 Govt 500gr FP",         1200.0, 0.150, 500.0, 2.6, 6.0, caliber = "45-70 Govt"),
        AmmoPreset("450 Bushmaster 250gr FTX",    2200.0, 0.210, 250.0, 2.6, 6.0, caliber = "450 Bushmaster"),
        AmmoPreset("450 Bushmaster 260gr",        2180.0, 0.144, 260.0, 2.6, 6.0, caliber = "450 Bushmaster"),
        AmmoPreset("458 SOCOM 250gr XD",          2000.0, 0.190, 250.0, 2.6, 6.0, caliber = "458 SOCOM"),
        AmmoPreset("458 SOCOM 300gr BT",          1900.0, 0.250, 300.0, 2.6, 6.0, caliber = "458 SOCOM"),
        AmmoPreset("458 SOCOM 325gr FTX",         1800.0, 0.240, 325.0, 2.6, 6.0, caliber = "458 SOCOM"),
        AmmoPreset("458 SOCOM 350gr FMJ",         1650.0, 0.200, 350.0, 2.6, 6.0, caliber = "458 SOCOM"),
        // .50 Beowulf
        AmmoPreset("50 Beowulf 300gr GD JHP",     1870.0, 0.185, 300.0, 2.6, 6.0, caliber = "50 Beowulf"),
        AmmoPreset("50 Beowulf 325gr JHP",        1800.0, 0.178, 325.0, 2.6, 6.0, caliber = "50 Beowulf"),
        AmmoPreset("50 Beowulf 400gr JFP",        1800.0, 0.193, 400.0, 2.6, 6.0, caliber = "50 Beowulf"),
        // .50 BMG
        AmmoPreset("50 BMG A-MAX 750gr",          2820.0, 1.050, 750.0, 2.0, 6.0, caliber = "50 BMG"),
        AmmoPreset("50 BMG API 649gr",            2800.0, 0.650, 649.0, 2.0, 6.0, caliber = "50 BMG"),
        AmmoPreset("50 BMG CEB MTAC 660gr",       2800.0, 0.390, 660.0, 2.0, 6.0, DragModel.G7, caliber = "50 BMG"),
        AmmoPreset("50 BMG M33 Ball 660gr",       2900.0, 0.701, 660.0, 2.0, 6.0, caliber = "50 BMG"),
        // ── Rimfire ───────────────────────────────────────────────────────────────
        // .17 HMR
        AmmoPreset("17 HMR 17gr A17",             2650.0, 0.128,  17.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 HMR"),
        AmmoPreset("17 HMR 17gr TNT HP",          2550.0, 0.126,  17.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 HMR"),
        AmmoPreset("17 HMR 17gr V-MAX",           2550.0, 0.128,  17.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 HMR"),
        AmmoPreset("17 HMR 20gr XTP",             2375.0, 0.125,  20.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 HMR"),
        // .17 WSM
        AmmoPreset("17 WSM 20gr",                 3000.0, 0.185,  20.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 WSM"),
        AmmoPreset("17 WSM 25gr",                 2600.0, 0.230,  25.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "17 WSM"),
        // .22 LR — hypervelocity
        AmmoPreset("22 LR 33gr Yellow Jacket",     1500.0, 0.095,  33.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 38gr Super Maximum",     1700.0, 0.107,  38.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 32gr Stinger",           1640.0, 0.103,  32.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Velocitor",         1435.0, 0.125,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        // .22 LR — high velocity
        AmmoPreset("22 LR 36gr HP",                1280.0, 0.112,  36.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 36gr CCI Mini-Mag HP",   1260.0, 0.112,  36.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 38gr HP",                1260.0, 0.118,  38.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr CCI Mini-Mag HP",   1235.0, 0.124,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr CCI Mini-Mag RN",   1235.0, 0.126,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr CCI Gamepoint JSP", 1235.0, 0.122,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Winchester Super-X LRN", 1280.0, 0.126, 40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Winchester Power Point", 1280.0, 0.120, 40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Remington Golden Bullet", 1255.0, 0.118, 40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Federal Champion",  1240.0, 0.120,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        // .22 LR — standard/match
        AmmoPreset("22 LR 40gr Std Velocity",      1070.0, 0.120,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Eley Tenex",        1085.0, 0.140,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Lapua Center-X",    1083.0, 0.135,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr SK Standard Plus",  1083.0, 0.130,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        // .22 LR — subsonic
        AmmoPreset("22 LR 40gr CCI Subsonic Seg",  1050.0, 0.118,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 40gr Quiet (subsonic)",   710.0, 0.120,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        AmmoPreset("22 LR 60gr SSS (subsonic)",     950.0, 0.200,  60.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 LR"),
        // .22 WMR
        AmmoPreset("22 WMR 30gr VNT",              2200.0, 0.116,  30.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 WMR"),
        AmmoPreset("22 WMR 40gr JHP Maxi-Mag",    1875.0, 0.114,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 WMR"),
        AmmoPreset("22 WMR 40gr JHP Super-X",     1910.0, 0.116,  40.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 WMR"),
        AmmoPreset("22 WMR 45gr JHP Super-X",     1550.0, 0.140,  45.0, 2.6, 2.0, category = AmmoCategory.RIMFIRE, caliber = "22 WMR"),
        // ── Pistol ────────────────────────────────────────────────────────────────
        // 5.7×28mm
        AmmoPreset("5.7×28mm 27gr SS195LF",     1965.0, 0.095,  27.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "5.7×28mm"),
        AmmoPreset("5.7×28mm 40gr V-MAX",       1600.0, 0.200,  40.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "5.7×28mm"),
        // .32 cal
        AmmoPreset("32 ACP 60gr JHP",              1000.0, 0.100,  60.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "32 ACP"),
        AmmoPreset("32 ACP 71gr FMJ",               905.0, 0.112,  71.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "32 ACP"),
        AmmoPreset("327 Fed Mag 85gr FTX",          1400.0, 0.195,  85.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "327 Fed Mag"),
        AmmoPreset("327 Fed Mag 100gr JHP",         1400.0, 0.150, 100.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "327 Fed Mag"),
        AmmoPreset("327 Fed Mag 115gr SP",          1200.0, 0.165, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "327 Fed Mag"),
        // .380 ACP
        AmmoPreset("380 ACP 85gr FTX Critical Def", 1000.0, 0.097, 85.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "380 ACP"),
        AmmoPreset("380 ACP 90gr FTX",           990.0, 0.099,  90.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "380 ACP"),
        AmmoPreset("380 ACP 90gr Gold Dot",     1040.0, 0.101,  90.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "380 ACP"),
        AmmoPreset("380 ACP 95gr FMJ",           955.0, 0.100,  95.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "380 ACP"),
        AmmoPreset("380 ACP 99gr HST",          1030.0, 0.110,  99.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "380 ACP"),
        // 9mm
        AmmoPreset("9mm 115 gr",          1180.0, 0.150, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 115gr +P",        1300.0, 0.150, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 115gr Critical Def",  1135.0, 0.129, 115.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 124 gr",          1110.0, 0.165, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 124gr Critical Def", 1110.0, 0.134, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 124gr Gold Dot",      1150.0, 0.134, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 124gr HST",           1150.0, 0.150, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 124gr HST +P",        1200.0, 0.150, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm NATO 124 gr",             1180.0, 0.165, 124.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 135gr Critical Duty", 1160.0, 0.195, 135.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 147 gr (subsonic)",    990.0, 0.190, 147.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        AmmoPreset("9mm 147gr HST",           1000.0, 0.200, 147.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "9mm"),
        // .38 Spl / .357
        AmmoPreset("38 Spl 110gr JHP",           990.0, 0.125, 110.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "38 Spl"),
        AmmoPreset("38 Spl 125gr JHP",           950.0, 0.150, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "38 Spl"),
        AmmoPreset("38 Spl 125gr JHP +P",       975.0, 0.150, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "38 Spl"),
        AmmoPreset("38 Spl 158gr LRN",           770.0, 0.165, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "38 Spl"),
        AmmoPreset("38 Spl 158gr LSWCHP +P",     890.0, 0.165, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "38 Spl"),
        AmmoPreset("357 Mag 125gr Gold Dot",   1450.0, 0.141, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 Mag"),
        AmmoPreset("357 Mag 125gr XTP",        1500.0, 0.151, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 Mag"),
        AmmoPreset("357 Mag 158gr JSP",        1235.0, 0.163, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 Mag"),
        AmmoPreset("357 Mag 158gr XTP",        1240.0, 0.206, 158.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 Mag"),
        AmmoPreset("357 Mag 180gr JHP",          1100.0, 0.195, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 Mag"),
        AmmoPreset("357 SIG 125gr Gold Dot",     1350.0, 0.141, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 SIG"),
        AmmoPreset("357 SIG 125gr HST",          1450.0, 0.150, 125.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 SIG"),
        AmmoPreset("357 SIG 135gr FlexLock",     1225.0, 0.153, 135.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "357 SIG"),
        // .40/10mm
        AmmoPreset("40 S&W 155gr JHP",       1205.0, 0.142, 155.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "40 S&W"),
        AmmoPreset("40 S&W 165gr FMJ",       1060.0, 0.155, 165.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "40 S&W"),
        AmmoPreset("40 S&W 165gr JHP",       1150.0, 0.145, 165.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "40 S&W"),
        AmmoPreset("40 S&W 180gr FMJ",       1000.0, 0.165, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "40 S&W"),
        AmmoPreset("40 S&W 180gr JHP",        1010.0, 0.155, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "40 S&W"),
        AmmoPreset("10mm 155gr XTP",          1410.0, 0.137, 155.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        AmmoPreset("10mm 155gr XTP (full)",   1500.0, 0.137, 155.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        AmmoPreset("10mm 180gr XTP",          1275.0, 0.164, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        AmmoPreset("10mm 180gr XTP (full)",   1300.0, 0.164, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        AmmoPreset("10mm 200gr Hard Cast",     1300.0, 0.194, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        AmmoPreset("10mm 200gr XTP",          1300.0, 0.199, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "10mm"),
        // .44
        AmmoPreset("44 Spl 180gr XTP",          1000.0, 0.138, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Spl"),
        AmmoPreset("44 Spl 200gr Gold Dot",      875.0, 0.145, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Spl"),
        AmmoPreset("44 Spl 240gr LFN",           750.0, 0.175, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Spl"),
        AmmoPreset("44 Mag 180gr XTP",          1600.0, 0.138, 180.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Mag"),
        AmmoPreset("44 Mag 240gr JSP",          1180.0, 0.185, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Mag"),
        AmmoPreset("44 Mag 240gr XTP",          1230.0, 0.205, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Mag"),
        AmmoPreset("44 Mag 300gr XTP",          1200.0, 0.245, 300.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "44 Mag"),
        // .45
        AmmoPreset("45 ACP 185gr FMJ",        1000.0, 0.130, 185.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 185gr JHP",       1000.0, 0.130, 185.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 200gr JHP",       1080.0, 0.140, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 230gr FMJ",        855.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 230gr Gold Dot",   890.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 230gr HST",        890.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 ACP 230gr JHP +P",     950.0, 0.162, 230.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 ACP"),
        AmmoPreset("45 Colt 200gr JHP",         1100.0, 0.130, 200.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 Colt"),
        AmmoPreset("45 Colt 250gr JHP",          780.0, 0.146, 250.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 Colt"),
        AmmoPreset("45 Colt 255gr LRN",         1000.0, 0.155, 255.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "45 Colt"),
        // .454/.500
        AmmoPreset("454 Casull 240gr XTP",         1900.0, 0.150, 240.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "454 Casull"),
        AmmoPreset("454 Casull 300gr XTP",         1650.0, 0.195, 300.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "454 Casull"),
        AmmoPreset("454 Casull 335gr WFN",         1600.0, 0.170, 335.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "454 Casull"),
        AmmoPreset("500 S&W 300gr FTX",            2075.0, 0.200, 300.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "500 S&W"),
        AmmoPreset("500 S&W 350gr XTP",            1912.0, 0.230, 350.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "500 S&W"),
        AmmoPreset("500 S&W 500gr XTP",            1425.0, 0.280, 500.0, 0.7, 4.0, category = AmmoCategory.PISTOL, caliber = "500 S&W"),
        // ── Shotgun ───────────────────────────────────────────────────────────────
        // 12ga slugs — smoothbore (sight height 0.5" bead; vital zone 8" for deer)
        AmmoPreset("12ga Foster 1oz 2¾\"",           1560.0, 0.068, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga Foster 1oz 3\" Mag",         1760.0, 0.068, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga TruBall 1oz 2¾\"",           1300.0, 0.068, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga Brenneke 1oz 2¾\"",          1476.0, 0.086, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        // 16ga slugs — smoothbore
        AmmoPreset("16ga Foster 1oz 2¾\"",            1600.0, 0.062, 437.5, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "16ga"),
        // 12ga sabot slugs — rifled barrel (sight height 1.5" scope)
        AmmoPreset("12ga Sabot 300gr SST",            2000.0, 0.200, 300.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga Sabot 300gr Trophy Copper",  1900.0, 0.168, 300.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga Sabot 385gr AccuTip",        1850.0, 0.200, 385.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga Sabot 385gr Partition Gold", 1900.0, 0.190, 385.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        // 20ga slugs
        AmmoPreset("20ga Foster 7/8oz 2¾\"",          1600.0, 0.055, 383.0, 0.5, 8.0, category = AmmoCategory.SHOTGUN, caliber = "20ga"),
        AmmoPreset("20ga Sabot 275gr Trophy Bonded",  1900.0, 0.155, 275.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "20ga"),
        AmmoPreset("20ga Sabot 250gr SST",            1800.0, 0.140, 250.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "20ga"),
        AmmoPreset("20ga Sabot 260gr AccuTip",        1900.0, 0.140, 260.0, 2.6, 8.0, category = AmmoCategory.SHOTGUN, caliber = "20ga"),
        // .410 slug
        AmmoPreset("410 Slug 1/5oz 2½\"",             1830.0, 0.040,  87.5, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "410"),
        AmmoPreset("410 Slug 1/4oz 3\"",              1800.0, 0.042, 109.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "410"),
        AmmoPreset("410 000 Buck (per pellet)",        850.0, 0.083,  68.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "410"),
        AmmoPreset("410 00 Buck (per pellet)",         750.0, 0.078,  54.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "410"),
        // Buckshot — single-pellet ballistics; vital zone 4" (defensive)
        AmmoPreset("12ga 000 Buck (per pellet)",      1325.0, 0.083,  68.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga 00 Buck (per pellet)",       1325.0, 0.078,  54.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga #1 Buck (per pellet)",       1325.0, 0.072,  40.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("12ga #4 Buck (per pellet)",       1325.0, 0.058,  21.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "12ga"),
        AmmoPreset("20ga 00 Buck (per pellet)",       1200.0, 0.078,  54.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "20ga"),
        AmmoPreset("20ga #3 Buck (per pellet)",       1200.0, 0.063,  30.0, 0.5, 4.0, category = AmmoCategory.SHOTGUN, caliber = "20ga")
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
            if (vx < 1.0) break     // horizontal velocity exhausted (low-BC rounds at terminal velocity)
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
        val energyAtMuzzleFtLb: Double,
        val velocityAtNearZeroFps: Double,
        val energyAtNearZeroFtLb: Double,
        val velocityAtFarZeroFps: Double,
        val energyAtFarZeroFtLb: Double,
        val energyAtMpbrFtLb: Double,
        val trajectoryTable: List<TrajectoryRow>,
        val rawTrajectory: List<TrajectoryPoint>    // on-screen target-distance lookup; not saved/printed
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
        // Below this the bullet spends most of its flight decelerating toward — then
        // riding — its drag-limited terminal fall speed (typically ~300 ft/s) rather than
        // flying flat: horizontal velocity bleeds off far faster than vertical velocity
        // builds, so total speed can *increase* with range and drop/holdover values blow
        // up to thousands of MOA well inside any normal table range. The point-blank-range
        // model assumes a flat-fire trajectory throughout, which no longer holds — even the
        // slowest cataloged subsonic loads run 700+ fps, so 400 fps leaves generous margin
        // without accepting inputs no real firearm produces.
        require(muzzleVelocity      >= 400.0) {
            "Muzzle velocity is unrealistically low for a flat-fire trajectory (minimum 400 fps)."
        }

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

        var nearZero      = 0.0
        var farZero       = 0.0
        var peakHt        = Double.NEGATIVE_INFINITY
        var peakRng       = 0.0
        var mpbr          = 0.0
        var velAtNearZero = 0.0
        var velAtFarZero  = 0.0
        var velAtMpbr     = 0.0
        var foundNear     = false
        var foundFar      = false

        for (i in 1 until traj.size) {
            val a = traj[i - 1]
            val b = traj[i]
            if (b.heightInches > peakHt) { peakHt = b.heightInches; peakRng = b.rangeYards }

            if (!foundNear && a.heightInches <= 0.0 && b.heightInches > 0.0) {
                nearZero = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, 0.0)
                val span = b.rangeYards - a.rangeYards
                val f    = if (span > 0) (nearZero - a.rangeYards) / span else 0.0
                velAtNearZero = a.velocityFps + f * (b.velocityFps - a.velocityFps)
                foundNear = true
            }
            if (foundNear && !foundFar && a.heightInches >= 0.0 && b.heightInches < 0.0) {
                farZero = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, 0.0)
                val span = b.rangeYards - a.rangeYards
                val f    = if (span > 0) (farZero - a.rangeYards) / span else 0.0
                velAtFarZero = a.velocityFps + f * (b.velocityFps - a.velocityFps)
                foundFar = true
            }
            if (foundFar && a.heightInches >= -rIn && b.heightInches < -rIn) {
                mpbr = lerp(a.rangeYards, b.rangeYards, a.heightInches, b.heightInches, -rIn)
                val span = b.rangeYards - a.rangeYards
                val f    = if (span > 0) (mpbr - a.rangeYards) / span else 0.0
                velAtMpbr = a.velocityFps + f * (b.velocityFps - a.velocityFps)
                break
            }
        }

        // At very low muzzle velocity / BC combinations the bullet decelerates below the
        // simulator's terminal-velocity cutoff (see `vRel < 100.0` in simulate()) within a
        // few yards, so it never climbs back through the line of sight. Near/far zero, MPBR,
        // and everything derived from them would otherwise silently come back as 0 or other
        // meaningless values instead of failing loudly.
        require(foundFar) {
            "No usable trajectory: at this muzzle velocity and BC, the bullet never reaches " +
                "a far zero for the given sight height and vital zone. Try a more realistic " +
                "muzzle velocity."
        }

        val table = trajectoryTable(traj, bulletWeightGr, tableStepYards, tableMaxYards, tableMinYards)

        return MpbrResult(
            nearZeroYards         = nearZero,
            farZeroYards          = farZero,
            maxOrdinateInches     = peakHt,
            maxOrdinateRangeYards = peakRng,
            mpbrYards             = mpbr,
            boreAngleMoa          = angle * (180.0 / PI) * 60.0,
            energyAtMuzzleFtLb    = energyFtLb(bulletWeightGr, muzzleVelocity),
            velocityAtNearZeroFps = velAtNearZero,
            energyAtNearZeroFtLb  = energyFtLb(bulletWeightGr, velAtNearZero),
            velocityAtFarZeroFps  = velAtFarZero,
            energyAtFarZeroFtLb   = energyFtLb(bulletWeightGr, velAtFarZero),
            energyAtMpbrFtLb      = energyFtLb(bulletWeightGr, velAtMpbr),
            trajectoryTable       = table,
            rawTrajectory         = traj
        )
    }

    /** Interpolate the high-res trajectory at an arbitrary range for on-screen display. */
    fun trajectoryAt(
        traj: List<TrajectoryPoint>,
        rangeYards: Int,
        bulletWeightGr: Double = 0.0
    ): TrajectoryRow? {
        if (traj.isEmpty() || rangeYards <= 0) return null
        var i = 0
        while (i < traj.size - 1 && traj[i + 1].rangeYards < rangeYards) i++
        if (i >= traj.size - 1) return null
        val a    = traj[i]; val b = traj[i + 1]
        val span = b.rangeYards - a.rangeYards
        val f    = if (span > 0) (rangeYards - a.rangeYards) / span else 0.0
        val height   = a.heightInches   + f * (b.heightInches   - a.heightInches)
        val lateral  = a.lateralInches  + f * (b.lateralInches  - a.lateralInches)
        val velocity = a.velocityFps    + f * (b.velocityFps    - a.velocityFps)
        val time     = a.timeSeconds    + f * (b.timeSeconds     - a.timeSeconds)
        val drop     = -height
        val moa      = drop    * 100.0 / (rangeYards * 1.0472)
        val mil      = drop    / (rangeYards * 0.036)
        val driftMoa = lateral * 100.0 / (rangeYards * 1.0472)
        val driftMil = lateral / (rangeYards * 0.036)
        return TrajectoryRow(rangeYards, drop, moa, mil, lateral, driftMoa, driftMil,
            velocity, energyFtLb(bulletWeightGr, velocity),
            momentumLbFps(bulletWeightGr, velocity), time)
    }

    private fun lerp(x0: Double, x1: Double, y0: Double, y1: Double, yTarget: Double): Double {
        if (y1 == y0) return x0
        val t = (yTarget - y0) / (y1 - y0)
        return x0 + t * (x1 - x0)
    }
}
