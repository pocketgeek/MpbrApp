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
import androidx.compose.ui.graphics.Color
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

        Button(
            onClick = {
                error = null
                try {
                    val mv = muzzleVel.toDouble()
                    val b  = bc.toDouble()
                    val bw = bulletWeight.toDouble()
                    val sh = sightHeight.toDouble()
                    val vz = vitalZone.toDouble()
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
                        windSpeedMph        = windSpeed.toDoubleOrNull() ?: 0.0
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

            if (r.trajectoryTable.isNotEmpty()) {
                TrajectoryTableCard(
                    rows       = r.trajectoryTable,
                    showEnergy = r.energyAtMpbrFtLb > 0.0,
                    showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0
                )
            }

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
                            selectedReticle
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
    showDrift: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Trajectory Table", style = MaterialTheme.typography.titleLarge)

            val header = buildList {
                add("Range"); add("Drop"); add("MOA"); add("MIL")
                if (showDrift) { add("W.MOA"); add("W.MIL") }
                add("Vel")
                if (showEnergy) add("Energy")
            }
            TrajRow(cells = header, style = MaterialTheme.typography.labelMedium, bold = true)
            HorizontalDivider()

            for (row in rows) {
                val cells = buildList {
                    add("${row.rangeYards} yd")
                    add("%.1f in".format(row.dropInches))
                    add("%.1f".format(row.holdoverMoa))
                    add("%.2f".format(row.holdoverMil))
                    if (showDrift) {
                        add("%.1f".format(row.driftMoa))
                        add("%.2f".format(row.driftMil))
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
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier        = Modifier.fillMaxWidth()
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
            var lastUnit: Ballistics.ReticleUnit? = null
            for (r in Ballistics.RETICLE_PRESETS) {
                if (r.unit != lastUnit) {
                    DropdownMenuItem(
                        text    = { Text(if (r.unit == Ballistics.ReticleUnit.MIL) "MIL" else "MOA",
                                        style = MaterialTheme.typography.labelSmall) },
                        onClick = {}, enabled = false
                    )
                    lastUnit = r.unit
                }
                val bg = if (r.unit == Ballistics.ReticleUnit.MIL) Color(0x332196F3) else Color(0x334CAF50)
                DropdownMenuItem(
                    text     = { Text(r.name) },
                    onClick  = { onSelect(r); expanded = false },
                    modifier = Modifier.background(bg)
                )
            }
        }
    }
}

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
    val R   = (sectionH * 0.40f).toInt()          // circle radius in px
    val cx  = W * 0.26f                            // circle center X
    val cy  = sectionTop + sectionH * 0.50f        // circle center Y
    val ppu = R / reticle.vertExtent.toFloat()     // px per reticle unit (mil or MOA)

    // ---- scope circle ----
    cv.drawCircle(cx, cy, R.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE })
    cv.drawCircle(cx, cy, R.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = S * 3f
        })

    // reticle name label above circle
    val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY; textSize = bsz * 0.72f; typeface = Typeface.DEFAULT_BOLD
    }
    val lText = "Reticle: ${reticle.name}"
    cv.drawText(lText, cx - pLbl.measureText(lText) / 2f, sectionTop + S * 8f + bsz * 0.72f, pLbl)

    // ---- clip to circle then draw crosshair + marks ----
    cv.save()
    cv.clipPath(android.graphics.Path().apply {
        addCircle(cx, cy, R.toFloat(), android.graphics.Path.Direction.CW)
    })

    val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S.toFloat() }
    val pMaj  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = S * 2f }
    val pDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }

    // Vertical stadia — always present
    cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)

    if (reticle.style == Ballistics.ReticleStyle.BDC) {
        // ---- BDC: thin inner crosshair + thick outer posts ----
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
        // Windage hashes
        val wh = R * 0.10f
        for (w in reticle.windageMarks) {
            val wx = (w * ppu).toFloat()
            for (sign in listOf(1f, -1f)) {
                cv.drawLine(cx + sign * wx, cy - wh, cx + sign * wx, cy + wh, pMaj)
            }
        }
        // Holdover dots
        val dotR = (R * 0.048f).coerceAtLeast(S * 3f)
        for (h in reticle.holdoverMarks) {
            cv.drawCircle(cx, cy + (h * ppu).toFloat(), dotR, pDot)
        }
    } else {
        // ---- Hash / Dot / Christmas tree ----
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
    cv.restore()

    // ---- callout lines + range labels ----
    val lineStartX = cx + R + S * 10f
    val textX      = W * 0.65f
    val pDash = Paint().apply {
        color = android.graphics.Color.DKGRAY; strokeWidth = S.toFloat()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(S * 5f, S * 3f), 0f)
    }
    val pMark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
    val pTxt  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = bsz * 0.80f }

    var lastY = Float.NEGATIVE_INFINITY
    for (row in result.trajectoryTable) {
        val hold = if (reticle.unit == Ballistics.ReticleUnit.MIL) row.holdoverMil else row.holdoverMoa
        val y    = cy + hold.toFloat() * ppu
        if (y < sectionTop + S * 4 || y > sectionTop + sectionH - S * 4) continue
        if (Math.abs(y - lastY) < bsz * 0.82f) continue   // skip overlapping labels

        cv.drawCircle(cx, y, S * 4f, pMark)
        cv.drawLine(lineStartX, y, textX - S * 4f, y, pDash)
        val unitStr = if (reticle.unit == Ballistics.ReticleUnit.MIL)
            "%.2f mil".format(hold) else "%.1f MOA".format(hold)
        cv.drawText("${row.rangeYards} yd  ($unitStr)", textX, y + bsz * 0.33f, pTxt)
        lastY = y
    }
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
    reticle: Ballistics.ReticlePreset? = null
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
    val cols = buildList {
        add("Rng (yd)"); add("Drop (in)"); add("MOA"); add("MIL")
        if (showDrift)  { add("W.MOA"); add("W.MIL") }
        add("Vel (fps)")
        if (showEnergy) add("E (ft·lb)")
    }

    val reticleH = if (reticle != null) 640 else 0
    val headerH  = pad + tsz.toInt() + 14 + info.size * lnH + pad
    val tableH   = pad + bsz.toInt() + 10 + S + rwH * result.trajectoryTable.size + pad
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
    cv.drawText("MPBR DOPE CARD", pad.toFloat(), y, pTitle)
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
    y += bsz + 10
    cv.drawRect(0f, y, W.toFloat(), y + S, pRule)
    y += S + bsz * 0.3f

    result.trajectoryTable.forEachIndexed { idx, row ->
        if (idx % 2 == 1) cv.drawRect(0f, y - bsz * 0.9f, W.toFloat(), y + bsz * 0.3f, pStripe)
        val cells = buildList {
            add("${row.rangeYards}")
            add("%.1f".format(row.dropInches))
            add("%.1f".format(row.holdoverMoa))
            add("%.2f".format(row.holdoverMil))
            if (showDrift)  { add("%.1f".format(row.driftMoa)); add("%.2f".format(row.driftMil)) }
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
