package com.example.mpbr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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

/** Format a Double as a clean integer string when whole, else trim trailing zeros. */
private fun formatNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString()
    else "%g".format(d).trimEnd('0').trimEnd('.')
