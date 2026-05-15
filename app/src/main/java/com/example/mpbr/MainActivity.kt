package com.example.mpbr

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) { MpbrScreen() }
            }
        }
    }
}

@Composable
fun MpbrScreen() {
    val defaultPreset = Ballistics.PRESETS.first { it.name.startsWith("M80") }

    var muzzleVel    by remember { mutableStateOf(formatNum(defaultPreset.muzzleVelocityFps)) }
    var bc           by remember { mutableStateOf("%.3f".format(defaultPreset.ballisticCoeff)) }
    var bulletWeight by remember { mutableStateOf(formatNum(defaultPreset.bulletWeightGr)) }
    var sightHeight  by remember { mutableStateOf("%.2f".format(defaultPreset.sightHeightIn)) }
    var vitalZone    by remember { mutableStateOf("%.1f".format(defaultPreset.vitalZoneIn)) }
    var dragModel    by remember { mutableStateOf(defaultPreset.dragModel) }

    // null = "Custom" — i.e. user has typed something, no canned preset is loaded
    var selectedPreset by remember { mutableStateOf<Ballistics.AmmoPreset?>(defaultPreset) }

    // Atmospheric inputs (defaults = Parma, Idaho conditions)
    var altitude     by remember { mutableStateOf("2231") }
    var temperature  by remember { mutableStateOf("70") }
    var humidity     by remember { mutableStateOf("25") }
    var windSpeed    by remember { mutableStateOf("0") }

    var tableStart   by remember { mutableStateOf("50") }
    var tableEnd     by remember { mutableStateOf("500") }
    var tableStep    by remember { mutableStateOf("50") }
    var dopeTitle    by remember { mutableStateOf("MPBR DOPE CARD") }

    var result by remember { mutableStateOf<Ballistics.MpbrResult?>(null) }
    var error  by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val onPermissionResult = remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionResult.value(granted) }

    var selectedReticle by remember { mutableStateOf<Ballistics.ReticlePreset?>(null) }

    // When the user types in a field, we want the dropdown label to flip to "Custom"
    // so it doesn't lie about what's loaded. These wrap the raw setters.
    fun userEdit(setter: (String) -> Unit, value: String) {
        setter(value)
        selectedPreset = null
    }

    fun applyPreset(p: Ballistics.AmmoPreset) {
        selectedPreset = p
        muzzleVel    = formatNum(p.muzzleVelocityFps)
        bc           = "%.3f".format(p.ballisticCoeff)
        bulletWeight = formatNum(p.bulletWeightGr)
        sightHeight  = "%.2f".format(p.sightHeightIn)
        vitalZone    = "%.1f".format(p.vitalZoneIn)
        dragModel    = p.dragModel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Maximum Point Blank Range",
            style = MaterialTheme.typography.headlineSmall
        )

        // ---- Ammunition preset ----
        SectionLabel("Ammunition")
        AmmoPresetDropdown(
            selected = selectedPreset,
            presets  = Ballistics.PRESETS,
            onSelect = { picked ->
                if (picked == null) selectedPreset = null else applyPreset(picked)
            }
        )

        // ---- Drag model selector ----
        SectionLabel("Drag Model")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = dragModel == Ballistics.DragModel.G1,
                onClick  = { dragModel = Ballistics.DragModel.G1; selectedPreset = null },
                label    = { Text("G1") }
            )
            FilterChip(
                selected = dragModel == Ballistics.DragModel.G7,
                onClick  = { dragModel = Ballistics.DragModel.G7; selectedPreset = null },
                label    = { Text("G7") }
            )
        }

        // ---- Reticle (shapes the DOPE chart illustration) ----
        SectionLabel("Reticle")
        ReticleDropdown(selected = selectedReticle, onSelect = { selectedReticle = it })

        // ---- Bullet & sight ----
        SectionLabel("Bullet & Sight")
        NumberField("Muzzle Velocity (fps)",    muzzleVel)    { userEdit({ muzzleVel = it }, it) }
        NumberField("Ballistic Coefficient",    bc)           { userEdit({ bc = it }, it) }
        NumberField("Bullet Weight (gr)",       bulletWeight) { userEdit({ bulletWeight = it }, it) }
        NumberField("Sight Height (in)",        sightHeight)  { userEdit({ sightHeight = it }, it) }
        NumberField("Vital Zone Diameter (in)", vitalZone)    { userEdit({ vitalZone = it }, it) }

        // ---- Atmosphere ----
        SectionLabel("Atmosphere")
        NumberField("Altitude (ft)",           altitude)   { altitude = it }
        NumberField("Temperature (°F)",        temperature){ temperature = it }
        NumberField("Humidity (%)",            humidity)   { humidity = it }
        NumberField("Wind Speed (mph, full value crosswind)", windSpeed) { windSpeed = it }

        // ---- Trajectory table range ----
        SectionLabel("Trajectory Table")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Start (yd)", tableStart, Modifier.weight(1f)) { tableStart = it }
            NumberField("Step (yd)",  tableStep,  Modifier.weight(1f)) { tableStep  = it }
            NumberField("End (yd)",   tableEnd,   Modifier.weight(1f)) { tableEnd   = it }
        }

        Button(
            onClick = {
                error = null
                try {
                    val mv   = muzzleVel.toDouble()
                    val b    = bc.toDouble()
                    val bw   = bulletWeight.toDouble()
                    val sh   = sightHeight.toDouble()
                    val vz   = vitalZone.toDouble()
                    val tMin  = (tableStart.toIntOrNull() ?: 0).coerceIn(0, 2000)
                    val tMax  = (tableEnd.toIntOrNull()   ?: 500).coerceIn(0, 2000)
                    val tStep = (tableStep.toIntOrNull()  ?: 50).coerceIn(1, 500)
                    if (tMin >= tMax) {
                        error  = "Table start must be less than table end"
                        result = null
                        return@Button
                    }
                    val atm = Ballistics.Atmosphere(
                        altitudeFt   = altitude.toDouble(),
                        temperatureF = temperature.toDouble(),
                        humidityPct  = humidity.toDouble()
                    )
                    result = Ballistics.calculateMpbr(
                        muzzleVelocity      = mv,
                        ballisticCoeff      = b,
                        sightHeightIn       = sh,
                        vitalZoneDiameterIn = vz,
                        bulletWeightGr      = bw,
                        dragModel           = dragModel,
                        atmosphere          = atm,
                        windSpeedMph        = windSpeed.toDoubleOrNull() ?: 0.0,
                        tableStepYards      = tStep,
                        tableMinYards       = tMin,
                        tableMaxYards       = tMax
                    )
                } catch (e: NumberFormatException) {
                    error  = "All fields must be numbers"
                    result = null
                } catch (e: IllegalArgumentException) {
                    error  = e.message
                    result = null
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Results", style = MaterialTheme.typography.titleLarge)
                    ResultRow("Optimal Zero (Far Zero)", "%.0f yd".format(r.farZeroYards))
                    ResultRow("Near Zero",               "%.0f yd".format(r.nearZeroYards))
                    ResultRow(
                        "Max Ordinate",
                        "%.2f in @ %.0f yd".format(r.maxOrdinateInches, r.maxOrdinateRangeYards)
                    )
                    ResultRow("Maximum Point Blank Range", "%.0f yd".format(r.mpbrYards))
                    ResultRow("Velocity at MPBR",          "%.0f fps".format(r.velocityAtMpbrFps))
                    if (r.energyAtMpbrFtLb > 0.0) {
                        ResultRow("Energy at MPBR",   "%.0f ft·lb".format(r.energyAtMpbrFtLb))
                        ResultRow("Momentum at MPBR", "%.1f lb·ft/s".format(r.momentumAtMpbrLbFps))
                    }
                    ResultRow("Bore Angle Above LOS", "%.2f MOA".format(r.boreAngleMoa))
                }
            }

            // Reticle illustration (on-screen) when a reticle is selected
            selectedReticle?.let { reticle ->
                val reticleBmp = remember(r, reticle) {
                    buildReticleBitmap(r, reticle, windSpeed.toDoubleOrNull() ?: 0.0)
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap             = reticleBmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.FillWidth,
                        modifier           = Modifier.fillMaxWidth()
                    )
                }
            }

            if (r.trajectoryTable.isNotEmpty()) {
                TrajectoryTableCard(
                    rows       = r.trajectoryTable,
                    showEnergy = r.energyAtMpbrFtLb > 0.0,
                    showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0,
                    showMoa    = selectedReticle == null || selectedReticle!!.unit == Ballistics.ReticleUnit.MOA,
                    showMil    = selectedReticle == null || selectedReticle!!.unit == Ballistics.ReticleUnit.MIL
                )
            }

            OutlinedTextField(
                value         = dopeTitle,
                onValueChange = { dopeTitle = it },
                label         = { Text("DOPE Card Title") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val label      = selectedPreset?.name ?: "Custom"
                    val showEnergy = r.energyAtMpbrFtLb > 0.0
                    val showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0

                    fun doSave() {
                        val bmp = buildDopeChartBitmap(
                            context, r, label,
                            altitude.toDoubleOrNull()    ?: 0.0,
                            temperature.toDoubleOrNull() ?: 59.0,
                            humidity.toDoubleOrNull()    ?: 0.0,
                            windSpeed.toDoubleOrNull()   ?: 0.0,
                            showEnergy, showDrift,
                            selectedReticle,
                            dopeTitle
                        )
                        val ok = saveDopeChart(context, bmp, label)
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "Saved to Pictures/MPBR DOPE Charts" else "Save failed",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        onPermissionResult.value = { granted -> if (granted) doSave() }
                        permLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        doSave()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save DOPE Chart") }
        }
    }
}

@Composable
private fun TrajectoryTableCard(
    rows: List<Ballistics.TrajectoryRow>,
    showEnergy: Boolean,
    showDrift: Boolean,
    showMoa: Boolean = true,
    showMil: Boolean = true
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Trajectory Table", style = MaterialTheme.typography.titleLarge)

            val header = buildList {
                add("Range"); add("Drop")
                if (showMoa) add("MOA")
                if (showMil) add("MIL")
                if (showDrift) {
                    if (showMoa) add("W.MOA")
                    if (showMil) add("W.MIL")
                }
                add("Vel")
                if (showEnergy) add("Energy")
            }
            TrajRow(cells = header, style = MaterialTheme.typography.labelMedium, bold = true)
            HorizontalDivider()

            for (row in rows) {
                val cells = buildList {
                    add("${row.rangeYards} yd")
                    add("%.1f in".format(row.dropInches))
                    if (showMoa) add("%.1f".format(row.holdoverMoa))
                    if (showMil) add("%.2f".format(row.holdoverMil))
                    if (showDrift) {
                        if (showMoa) add("%.1f".format(row.driftMoa))
                        if (showMil) add("%.2f".format(row.driftMil))
                    }
                    add("%.0f fps".format(row.velocityFps))
                    if (showEnergy) add("%.0f ft·lb".format(row.energyFtLb))
                }
                TrajRow(cells = cells, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TrajRow(
    cells: List<String>,
    style: androidx.compose.ui.text.TextStyle,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (c in cells) {
            Text(
                text     = c,
                style    = style,
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier        = modifier
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmmoPresetDropdown(
    selected: Ballistics.AmmoPreset?,
    presets: List<Ballistics.AmmoPreset>,
    onSelect: (Ballistics.AmmoPreset?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = selected?.name ?: "Custom"

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value         = display,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Preset") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            var lastCategory: Ballistics.AmmoCategory? = null
            for (p in presets) {
                if (p.category != lastCategory) {
                    val headerLabel = when (p.category) {
                        Ballistics.AmmoCategory.RIFLE   -> "Rifle"
                        Ballistics.AmmoCategory.RIMFIRE -> "Rimfire"
                        Ballistics.AmmoCategory.PISTOL  -> "Pistol"
                        Ballistics.AmmoCategory.SHOTGUN -> "Shotgun"
                    }
                    DropdownMenuItem(
                        text    = { Text(headerLabel, style = MaterialTheme.typography.labelSmall) },
                        onClick = {},
                        enabled = false
                    )
                    lastCategory = p.category
                }
                val bgColor = when (p.category) {
                    Ballistics.AmmoCategory.RIFLE   -> Color(0x334CAF50)
                    Ballistics.AmmoCategory.RIMFIRE -> Color(0x332196F3)
                    Ballistics.AmmoCategory.PISTOL  -> Color(0x33FF9800)
                    Ballistics.AmmoCategory.SHOTGUN -> Color(0x339C27B0)
                }
                DropdownMenuItem(
                    text     = { Text(p.name) },
                    onClick  = { onSelect(p); expanded = false },
                    modifier = Modifier.background(bgColor)
                )
            }
            DropdownMenuItem(
                text    = { Text("Custom") },
                onClick = { onSelect(null); expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReticleDropdown(
    selected: Ballistics.ReticlePreset?,
    onSelect: (Ballistics.ReticlePreset?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value         = selected?.name ?: "None",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Reticle") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text    = { Text("None") },
                onClick = { onSelect(null); expanded = false }
            )
            for (r in Ballistics.RETICLE_PRESETS) {
                DropdownMenuItem(
                    text    = { Text(r.name) },
                    onClick = { onSelect(r); expanded = false }
                )
            }
        }
    }
}

/** A single color-coded trajectory callout: 2-D position inside the scope + label. */
private data class ReticleCallout(val x: Float, val y: Float, val color: Int, val label: String)

/**
 * Draws a scope-circle reticle illustration with holdover callouts into [cv].
 * The section spans [sectionTop]..[sectionTop]+[sectionH] at full bitmap width [W].
 */
private fun drawReticleSection(
    cv: android.graphics.Canvas,
    W: Int,
    result: Ballistics.MpbrResult,
    reticle: Ballistics.ReticlePreset,
    sectionTop: Float,
    sectionH: Float,
    bsz: Float,
    S: Int
) {
    val R   = (sectionH * 0.40f).toInt()
    val cx  = W * 0.26f
    val cy  = sectionTop + sectionH * 0.50f
    val ppu = R / reticle.vertExtent.toFloat()

    // ---- scope circle ----
    cv.drawCircle(cx, cy, R.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE })
    cv.drawCircle(cx, cy, R.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = S * 3f
        })
    // DRT reticle: draw both rings outside clip for guaranteed visibility
    if (reticle.style == Ballistics.ReticleStyle.DRT) {
        val innerR      = reticle.majorSpacing.toFloat() * ppu           // ~25 MOA
        val outerR      = reticle.postStart.toFloat()   * ppu            // ~71.5 MOA
        val innerStroke = (6f * ppu).coerceAtLeast(S * 2f)               // 6 MOA thick
        val outerStroke = (3f * ppu).coerceAtLeast(S * 1.5f)             // 3 MOA thick
        cv.drawCircle(cx, cy, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = innerStroke
        })
        cv.drawCircle(cx, cy, outerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = outerStroke
        })
    }

    // Circle-dot reticles: draw the large ring + cardinal tick marks outside the clip
    if (reticle.style == Ballistics.ReticleStyle.CIRCLE_DOT) {
        val ringR    = reticle.majorSpacing.toFloat() * ppu
        val halfTick = ringR * 0.10f   // tick extends 10% of ring radius each side of the ring
        val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = S * 4f
        }
        val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; strokeWidth = S * 3f
        }
        cv.drawCircle(cx, cy, ringR, pRing)
        // 12 o'clock
        cv.drawLine(cx, cy - ringR - halfTick, cx, cy - ringR + halfTick, pTick)
        // 6 o'clock
        cv.drawLine(cx, cy + ringR - halfTick, cx, cy + ringR + halfTick, pTick)
        // 3 o'clock
        cv.drawLine(cx + ringR - halfTick, cy, cx + ringR + halfTick, cy, pTick)
        // 9 o'clock
        cv.drawLine(cx - ringR - halfTick, cy, cx - ringR + halfTick, cy, pTick)
    }

    // reticle name label above circle
    val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY; textSize = bsz * 0.72f; typeface = Typeface.DEFAULT_BOLD
    }
    val lText = "Reticle: ${reticle.name}"
    cv.drawText(lText, cx - pLbl.measureText(lText) / 2f, sectionTop + S * 8f + bsz * 0.72f, pLbl)

    // ---- pre-compute visible callouts before entering clip ----
    // Each entry: (y position in bitmap, color, label text)
    val calloutColors = intArrayOf(
        android.graphics.Color.rgb(210,  45,  45),   // red
        android.graphics.Color.rgb( 30, 130, 200),   // blue
        android.graphics.Color.rgb( 30, 160,  75),   // green
        android.graphics.Color.rgb(170,  55, 185),   // purple
        android.graphics.Color.rgb(200, 125,   0),   // amber
        android.graphics.Color.rgb(  0, 170, 155),   // teal
        android.graphics.Color.rgb(215,  85,   0),   // orange
        android.graphics.Color.rgb(110,  55, 200),   // indigo
        android.graphics.Color.rgb(  0, 150,  90),   // emerald
        android.graphics.Color.rgb(175,   0,  85)    // crimson
    )
    val callouts = mutableListOf<ReticleCallout>()
    run {
        val margin = (R - S * 4f)
        var lastY  = Float.NEGATIVE_INFINITY
        var idx    = 0
        for (row in result.trajectoryTable) {
            val hold  = if (reticle.unit == Ballistics.ReticleUnit.MIL) row.holdoverMil  else row.holdoverMoa
            val drift = if (reticle.unit == Ballistics.ReticleUnit.MIL) row.driftMil     else row.driftMoa
            val x     = cx + drift.toFloat() * ppu
            val y     = cy + hold.toFloat()  * ppu
            // Skip if outside the scope circle
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy > margin * margin) continue
            if (Math.abs(y - lastY) < bsz * 0.82f) continue
            val unitStr = if (reticle.unit == Ballistics.ReticleUnit.MIL)
                "%.2f mil".format(hold) else "%.1f MOA".format(hold)
            callouts.add(ReticleCallout(x, y, calloutColors[idx % calloutColors.size], "${row.rangeYards} yd  ($unitStr)"))
            lastY = y
            idx++
        }
    }

    // ---- clip to circle, draw reticle + trajectory hash marks ----
    cv.save()
    cv.clipPath(android.graphics.Path().apply {
        addCircle(cx, cy, R.toFloat(), android.graphics.Path.Direction.CW)
    })

    val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S.toFloat() }
    val pMaj  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S * 2f }
    val pDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }

    when (reticle.style) {

        Ballistics.ReticleStyle.BDC -> {
            cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)
            if (reticle.postStart > 0.0) {
                val postPx = (reticle.postStart * ppu).toFloat()
                cv.drawLine(cx - postPx, cy, cx + postPx, cy, pLine)
                val pPost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = S * 5f
                }
                cv.drawLine(cx - R.toFloat(), cy, cx - postPx, cy, pPost)
                cv.drawLine(cx + postPx,      cy, cx + R.toFloat(), cy, pPost)
            } else {
                cv.drawLine(cx - R.toFloat(), cy, cx + R.toFloat(), cy, pLine)
            }
            val wh    = ppu * 0.65f   // proportional to unit spacing, not circle size
            val pWind = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; strokeWidth = S * 2f
            }
            for (w in reticle.windageMarks) {
                val wx = (w * ppu).toFloat()
                for (sign in listOf(1f, -1f)) {
                    cv.drawLine(cx + sign * wx, cy - wh, cx + sign * wx, cy + wh, pWind)
                }
            }
            val hmHW   = ppu * 0.65f  // proportional to unit spacing
            val pHMark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; strokeWidth = S * 2f
            }
            for (h in reticle.holdoverMarks) {
                val hy = cy + (h * ppu).toFloat()
                cv.drawLine(cx - hmHW, hy, cx + hmHW, hy, pHMark)
            }
        }

        Ballistics.ReticleStyle.BRC -> {
            // BRC: center dot, two holdunder dots below center, inward-pointing chevrons
            val dotR  = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(S * 3f)  // center 3 MOA dot
            val holdR = dotR * 0.65f                                                    // smaller holdunder dots

            val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
            val pChev  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S * 2f }

            // Center dot
            cv.drawCircle(cx, cy, dotR, pFill)
            // Holdunder dots below center
            for (h in reticle.holdoverMarks) {
                cv.drawCircle(cx, cy + h.toFloat() * ppu, holdR, pFill)
            }
            // Chevrons < > (tip inward, arms flare outward ~±35 MOA horiz, ±10 MOA vert)
            val tipX  = ppu * 20f
            val armX  = ppu * 35f
            val armY  = ppu * 10f
            cv.drawLine(cx - tipX, cy, cx - armX, cy - armY, pChev)
            cv.drawLine(cx - tipX, cy, cx - armX, cy + armY, pChev)
            cv.drawLine(cx + tipX, cy, cx + armX, cy - armY, pChev)
            cv.drawLine(cx + tipX, cy, cx + armX, cy + armY, pChev)
            // Faint vertical reference for trajectory callouts
            cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = S.toFloat() })
        }

        Ballistics.ReticleStyle.DRT -> {
            // Both rings drawn outside clip above; center dot + faint vertical reference
            val dotR = (reticle.minorSpacing * ppu).toFloat().coerceAtLeast(S * 2f)
            cv.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = android.graphics.Color.BLACK
            })
            cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = S.toFloat()
                })
        }

        Ballistics.ReticleStyle.CIRCLE_DOT -> {
            // Ring drawn outside clip above; here just the center dot + faint reference line
            val aDotR = reticle.minorSpacing.toFloat() * ppu
            cv.drawCircle(cx, cy, aDotR.coerceAtLeast(S * 3f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = android.graphics.Color.BLACK
            })
            cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = S.toFloat()
                })
        }

        Ballistics.ReticleStyle.MRAD_TREE -> {
            val stepsPerMaj = Math.round(reticle.majorSpacing / reticle.minorSpacing).toInt()
            val minorPx     = (reticle.minorSpacing * ppu).toFloat()
            val majorPx     = (reticle.majorSpacing * ppu).toFloat()
            val postPx      = reticle.postStart.toFloat() * ppu
            val circleR     = ppu * 1.0f      // 1 MRAD speed ring radius
            val treeStart   = 2               // tree rows begin at 2 MRAD below center
            val treeDepth   = reticle.vertExtent.toInt()

            val pThick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; strokeWidth = S * 6f
            }
            val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; strokeWidth = S.toFloat()
            }
            val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; strokeWidth = S * 1.5f
            }
            val pNum   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = android.graphics.Color.BLACK
                textSize  = majorPx * 0.48f
                typeface  = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            // Horizontal: thin inner + thick outer posts
            cv.drawLine(cx - postPx, cy, cx + postPx, cy, pThin)
            cv.drawLine(cx - R.toFloat(), cy, cx - postPx, cy, pThick)
            cv.drawLine(cx + postPx,      cy, cx + R.toFloat(), cy, pThick)

            // Hash marks and numbers on horizontal stadia
            val majTickH  = majorPx * 0.45f
            val minTickH  = majorPx * 0.22f
            val hMaxSteps = Math.round(reticle.postStart / reticle.minorSpacing).toInt()
            for (hStep in 1..hMaxSteps) {
                val isMaj = hStep % stepsPerMaj == 0
                val hx    = hStep * minorPx
                val th    = if (isMaj) majTickH else minTickH
                cv.drawLine(cx + hx, cy - th, cx + hx, cy + th, pTick)
                cv.drawLine(cx - hx, cy - th, cx - hx, cy + th, pTick)
                if (isMaj) {
                    val label = (hStep / stepsPerMaj).toString()
                    cv.drawText(label, cx + hx, cy - th - majorPx * 0.08f, pNum)
                    cv.drawText(label, cx - hx, cy - th - majorPx * 0.08f, pNum)
                }
            }

            // Center speed ring
            cv.drawCircle(cx, cy, circleR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.BLACK
                strokeWidth = S * 1.5f
            })

            // Short vertical stadia above center with minor ticks
            cv.drawLine(cx, cy - R.toFloat(), cx, cy - circleR, pThin)
            val vMaxSteps = Math.floor(((R - circleR) / minorPx).toDouble()).toInt()
            for (vStep in 1..vMaxSteps) {
                val vy    = circleR + vStep * minorPx
                val isMaj = vStep % stepsPerMaj == 0
                val tw    = if (isMaj) majTickH else minTickH
                cv.drawLine(cx - tw, cy - vy, cx + tw, cy - vy, pTick)
            }

            // Vertical connector from circle bottom to tree start
            cv.drawLine(cx, cy + circleR, cx, cy + treeStart * ppu, pThin)

            // Christmas tree: dot grid + half-MRAD ticks between dots
            val dotRad    = (majorPx * 0.13f).coerceAtLeast(S * 3f)
            val halfTickH = majorPx * 0.20f
            val pDotFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; style = Paint.Style.FILL
            }
            for (row in treeStart..treeDepth) {
                val ry = cy + row * ppu
                for (col in -row..row) {
                    cv.drawCircle(cx + col * ppu, ry, dotRad, pDotFill)
                }
                for (col in -row until row) {
                    val hx = cx + (col + 0.5f) * ppu
                    cv.drawLine(hx, ry - halfTickH, hx, ry + halfTickH, pTick)
                }
            }
        }

        Ballistics.ReticleStyle.MOA_TREE -> {
            // Vortex EBR-7C style: numbered graduated crosshair + dot-grid tree below center.
            val stepsPerMaj = Math.round(reticle.majorSpacing / reticle.minorSpacing).toInt() // 4
            val minorPx     = (reticle.minorSpacing * ppu).toFloat()   // 1 MOA
            val majorPx     = (reticle.majorSpacing * ppu).toFloat()   // 4 MOA
            val postPx      = reticle.postStart.toFloat() * ppu         // 26 MOA
            val treeStep    = reticle.majorSpacing.toInt()              // 4
            val treeDepth   = reticle.vertExtent.toInt() - treeStep    // 36

            val pThick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S * 6f }
            val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S.toFloat() }
            val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S * 1.5f }
            val pNum   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = majorPx * 0.55f
                typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val pNumL  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = majorPx * 0.55f
                typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT
            }
            val pNumR  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = majorPx * 0.55f
                typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
            }

            // Stadia lines
            cv.drawLine(cx - postPx, cy, cx + postPx, cy, pThin)
            cv.drawLine(cx - R.toFloat(), cy, cx - postPx, cy, pThick)
            cv.drawLine(cx + postPx, cy, cx + R.toFloat(), cy, pThick)
            cv.drawLine(cx, cy - R.toFloat(), cx, cy, pThin)                          // V above center
            cv.drawLine(cx, cy, cx, cy + treeDepth * ppu, pThin)                      // V through tree
            cv.drawLine(cx, cy + treeDepth * ppu, cx, cy + R.toFloat(), pThick)       // V bottom post

            // Horizontal hash marks + labels
            val majTickH  = majorPx * 0.45f
            val minTickH  = majorPx * 0.22f
            val hMaxSteps = Math.round(reticle.postStart / reticle.minorSpacing).toInt()
            for (hStep in 1..hMaxSteps) {
                val isMaj = hStep % stepsPerMaj == 0
                val hx = hStep * minorPx
                val th = if (isMaj) majTickH else minTickH
                cv.drawLine(cx + hx, cy - th, cx + hx, cy + th, pTick)
                cv.drawLine(cx - hx, cy - th, cx - hx, cy + th, pTick)
                if (isMaj) {
                    val label = (hStep / stepsPerMaj * treeStep).toString()
                    cv.drawText(label, cx + hx, cy - th - majorPx * 0.08f, pNum)
                    cv.drawText(label, cx - hx, cy - th - majorPx * 0.08f, pNum)
                }
            }

            // Vertical hash marks + labels above center only
            val vMaxSteps = Math.floor((R / minorPx).toDouble()).toInt()
            for (vStep in 1..vMaxSteps) {
                val vy = vStep * minorPx
                if (vy > R) break
                val isMaj = vStep % stepsPerMaj == 0
                val tw = if (isMaj) majTickH else minTickH
                cv.drawLine(cx - tw, cy - vy, cx + tw, cy - vy, pTick)
                if (isMaj) {
                    val label = (vStep / stepsPerMaj * treeStep).toString()
                    cv.drawText(label, cx + tw + majorPx * 0.12f, cy - vy + pNumL.textSize * 0.35f, pNumL)
                }
            }

            // Center dot (0.14 MOA)
            cv.drawCircle(cx, cy, (ppu * 0.14f).coerceAtLeast(S * 2f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK })

            // Dot-grid tree: rows every treeStep MOA, dots every 2 MOA within row
            val dotRad    = (majorPx * 0.10f).coerceAtLeast(S * 2f)
            val halfTickH = majorPx * 0.18f
            val pDot      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
            val labelOff  = majorPx * 0.40f
            var rowMoa    = treeStep
            while (rowMoa <= treeDepth) {
                val ry = cy + rowMoa * ppu
                // Dots at every 2 MOA from -rowMoa to +rowMoa
                var colMoa = -rowMoa
                while (colMoa <= rowMoa) {
                    cv.drawCircle(cx + colMoa * ppu, ry, dotRad, pDot)
                    colMoa += 2
                }
                // 1-MOA intermediate ticks between dots
                colMoa = -(rowMoa - 1)
                while (colMoa < rowMoa) {
                    val hx = cx + colMoa * ppu
                    cv.drawLine(hx, ry - halfTickH, hx, ry + halfTickH, pTick)
                    colMoa += 2
                }
                // Row labels at outermost dot ± offset
                val outerX = rowMoa * ppu
                cv.drawText(rowMoa.toString(), cx - outerX - labelOff, ry + pNumR.textSize * 0.35f, pNumR)
                cv.drawText(rowMoa.toString(), cx + outerX + labelOff * 0.5f, ry + pNumL.textSize * 0.35f, pNumL)
                rowMoa += treeStep
            }
        }

        else -> {
            // Hash / Dot / Christmas tree
            cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)
            cv.drawLine(cx - R.toFloat(), cy, cx + R.toFloat(), cy, pLine)

            val stepSz        = if (reticle.minorSpacing > 0) reticle.minorSpacing else reticle.majorSpacing
            val stepsPerMajor = if (reticle.minorSpacing > 0) Math.round(reticle.majorSpacing / reticle.minorSpacing).toInt() else 1
            val stepCount     = Math.round(reticle.vertExtent / stepSz).toInt()
            val pMin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S.toFloat() }

            for (i in 1..stepCount) {
                val isMaj = i % stepsPerMajor == 0
                val hw    = if (isMaj) R * 0.44f else R * 0.22f
                val paint = if (isMaj) pMaj else pMin
                for (sign in listOf(1f, -1f)) {
                    val y = cy + sign * i * stepSz.toFloat() * ppu
                    when (reticle.style) {
                        Ballistics.ReticleStyle.DOT ->
                            if (isMaj) cv.drawCircle(cx, y,
                                (ppu * stepSz * 0.28f).toFloat().coerceAtLeast(S * 2f), pDot)
                        Ballistics.ReticleStyle.HASH ->
                            cv.drawLine(cx - hw, y, cx + hw, y, paint)
                        Ballistics.ReticleStyle.CHRISTMAS_TREE -> {
                            val extra = if (sign > 0) i * stepSz.toFloat() * ppu * 0.20f else 0f
                            cv.drawLine(cx - hw - extra, y, cx + hw + extra, y, paint)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // Colored trajectory hash marks on the vertical stadia — drawn inside the circle
    // so they appear as horizontal tick marks crossing the crosshair at each holdover position
    val dotR = (R * 0.045f).coerceAtLeast(S * 3f)
    for (c in callouts) {
        cv.drawCircle(c.x, c.y, dotR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = c.color })
    }

    cv.restore()

    // ---- colored leader lines + labels (outside circle) ----
    val lineStartX = cx + R + S * 10f
    val textX      = W * 0.65f
    for (c in callouts) {
        cv.drawLine(lineStartX, c.y, textX - S * 6f, c.y, Paint().apply {
            this.color = c.color; strokeWidth = S * 1.5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(S * 5f, S * 3f), 0f)
        })
        cv.drawText(c.label, textX, c.y + bsz * 0.33f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = c.color; textSize = bsz * 0.80f; typeface = Typeface.DEFAULT_BOLD
            })
    }
}

/** Renders just the reticle illustration for on-screen display (2× scale, 1100×560 px). */
private fun buildReticleBitmap(
    result: Ballistics.MpbrResult,
    reticle: Ballistics.ReticlePreset,
    windSpeedMph: Double
): Bitmap {
    val W   = 1100
    val H   = 560
    val S   = 2
    val bsz = 12f * S
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv  = android.graphics.Canvas(bmp)
    cv.drawColor(android.graphics.Color.WHITE)
    drawReticleSection(cv, W, result, reticle, 0f, H.toFloat(), bsz, S)
    return bmp
}

/**
 * Renders a DOPE card to a 1200-px-wide Bitmap using Android Canvas.
 * 3× scale so text is sharp when viewed or printed.
 */
private fun buildDopeChartBitmap(
    context: android.content.Context,
    result: Ballistics.MpbrResult,
    ammoLabel: String,
    altFt: Double,
    tempF: Double,
    rhPct: Double,
    windMph: Double,
    showEnergy: Boolean,
    showDrift: Boolean,
    reticle: Ballistics.ReticlePreset? = null,
    cardTitle: String = "MPBR DOPE CARD"
): Bitmap {
    val S   = 3
    val W   = 400 * S
    val pad = 14 * S
    val tsz = 18f * S
    val bsz = 12f * S
    val lnH = (bsz + 12).toInt()
    val rwH = (bsz + 16).toInt()

    val windStr = if (windMph == 0.0) "calm" else "%.0f mph".format(windMph)
    val info = listOf(
        ammoLabel,
        "Near Zero: %.0f yd  |  Far Zero: %.0f yd  |  MPBR: %.0f yd"
            .format(result.nearZeroYards, result.farZeroYards, result.mpbrYards),
        "Max Ordinate: %.2f\" @ %.0f yd  |  Bore Angle: %.2f MOA"
            .format(result.maxOrdinateInches, result.maxOrdinateRangeYards, result.boreAngleMoa),
        "Alt: %.0f ft  |  Temp: %.0f°F  |  RH: %.0f%%  |  Wind: %s"
            .format(altFt, tempF, rhPct, windStr),
        "Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
    )
    val showMoa = reticle == null || reticle.unit == Ballistics.ReticleUnit.MOA
    val showMil = reticle == null || reticle.unit == Ballistics.ReticleUnit.MIL
    val cols = buildList {
        add("Rng (yd)"); add("Drop (in)")
        if (showMoa) add("MOA")
        if (showMil) add("MIL")
        if (showDrift) {
            if (showMoa) add("W.MOA")
            if (showMil) add("W.MIL")
        }
        add("Vel (fps)")
        if (showEnergy) add("E (ft·lb)")
    }

    val reticleH = if (reticle != null) 640 else 0
    val headerH  = pad + tsz.toInt() + 14 + info.size * lnH + pad
    val tableH   = pad + (bsz * 0.2f).toInt() + S * 2 + S + (bsz * 0.8f).toInt() + S * 2 + rwH * result.trajectoryTable.size + pad
    val H        = headerH + S + (if (reticle != null) reticleH + S else 0) + tableH

    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv  = android.graphics.Canvas(bmp)
    cv.drawColor(android.graphics.Color.WHITE)

    val pTitle  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = tsz; typeface = Typeface.DEFAULT_BOLD
    }
    val pBody   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = bsz
    }
    val pHdr    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = bsz; typeface = Typeface.DEFAULT_BOLD
    }
    val pRule   = Paint().apply { color = 0xFFCCCCCC.toInt() }
    val pStripe = Paint().apply { color = 0xFFF0F0F0.toInt() }

    var y = pad.toFloat() + tsz
    cv.drawText(cardTitle.ifBlank { "MPBR DOPE CARD" }, pad.toFloat(), y, pTitle)
    y += 14f
    for (line in info) { y += lnH; cv.drawText(line, pad.toFloat(), y, pBody) }
    y += pad

    cv.drawRect(0f, y, W.toFloat(), y + S, pRule)
    y += S.toFloat()

    if (reticle != null) {
        drawReticleSection(cv, W, result, reticle, y, reticleH.toFloat(), bsz, S)
        y += reticleH
        cv.drawRect(0f, y, W.toFloat(), y + S, pRule)
        y += S.toFloat()
    }
    y += pad

    val colW = (W - 2 * pad).toFloat() / cols.size
    cols.forEachIndexed { i, col -> cv.drawText(col, pad + i * colW, y, pHdr) }
    y += bsz * 0.2f + S * 2   // descent + equal gap
    cv.drawRect(0f, y, W.toFloat(), y + S, pRule)
    y += S + bsz * 0.8f + S * 2  // skip divider + equal gap + ascent

    result.trajectoryTable.forEachIndexed { idx, row ->
        if (idx % 2 == 1) cv.drawRect(0f, y - bsz * 0.9f, W.toFloat(), y + bsz * 0.3f, pStripe)
        val cells = buildList {
            add("${row.rangeYards}")
            add("%.1f".format(row.dropInches))
            if (showMoa) add("%.1f".format(row.holdoverMoa))
            if (showMil) add("%.2f".format(row.holdoverMil))
            if (showDrift) {
                if (showMoa) add("%.1f".format(row.driftMoa))
                if (showMil) add("%.2f".format(row.driftMil))
            }
            add("%.0f".format(row.velocityFps))
            if (showEnergy) add("%.0f".format(row.energyFtLb))
        }
        cells.forEachIndexed { i, cell -> cv.drawText(cell, pad + i * colW, y, pBody) }
        y += rwH
    }

    return bmp
}

/** Saves [bmp] as a JPEG to Pictures/MPBR DOPE Charts via MediaStore. Returns true on success. */
private fun saveDopeChart(
    context: android.content.Context,
    bmp: Bitmap,
    ammoLabel: String
): Boolean {
    val name = "DOPE_${ammoLabel.replace(Regex("[^A-Za-z0-9]"), "_")}_" +
               SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE,    "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MPBR DOPE Charts")
                put(MediaStore.Images.Media.IS_PENDING,    1)
            }
        }
        val uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)
            ?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) { false }
}

/** Format a Double as a clean integer string when whole, else trim trailing zeros. */
private fun formatNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString()
    else "%g".format(d).trimEnd('0').trimEnd('.')
