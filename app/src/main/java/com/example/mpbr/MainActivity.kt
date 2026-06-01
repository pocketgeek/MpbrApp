package com.example.mpbr

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.print.PrintHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

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

    var tableStart   by remember { mutableStateOf("0") }
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

    val showSaveDialog   = remember { mutableStateOf(false) }
    val showLoadDialog   = remember { mutableStateOf(false) }
    val showRenameDialog = remember { mutableStateOf(false) }
    var saveDialogName   by remember { mutableStateOf("") }
    var renameTarget     by remember { mutableStateOf("") }
    var renameText       by remember { mutableStateOf("") }
    var loadedSessions   by remember { mutableStateOf(listOf<SessionData>()) }

    var metricMode by remember { mutableStateOf(false) }

    // Display an imperial string in metric units (no-op when metricMode is false)
    fun toMetric(s: String, fmt: String = "%.1f", convert: (Double) -> Double): String =
        if (metricMode) s.toDoubleOrNull()?.let { fmt.format(convert(it)) } ?: s else s

    // Convert metric user input back to imperial for storage (no-op when metricMode is false)
    fun fromMetric(s: String, convert: (Double) -> Double): String =
        if (metricMode) s.toDoubleOrNull()?.let { formatNum(convert(it)) } ?: s else s

    // Same as fromMetric but rounds to integer (for yard fields parsed with toIntOrNull)
    fun fromMetricInt(s: String, convert: (Double) -> Double): String =
        if (metricMode) s.toDoubleOrNull()?.let { convert(it).roundToInt().toString() } ?: s else s

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

    fun currentSession(name: String) = SessionData(
        name         = name,
        muzzleVel    = muzzleVel,
        bc           = bc,
        bulletWeight = bulletWeight,
        sightHeight  = sightHeight,
        vitalZone    = vitalZone,
        dragModel    = dragModel.name,
        presetName   = selectedPreset?.name,
        altitude     = altitude,
        temperature  = temperature,
        humidity     = humidity,
        windSpeed    = windSpeed,
        tableStart   = tableStart,
        tableEnd     = tableEnd,
        tableStep    = tableStep,
        dopeTitle    = dopeTitle,
        reticleName  = selectedReticle?.name
    )

    fun calculate() {
        error = null
        try {
            val mv    = muzzleVel.toDouble()
            val b     = bc.toDouble()
            val bw    = bulletWeight.toDouble()
            val sh    = sightHeight.toDouble()
            val vz    = vitalZone.toDouble()
            val tMin  = (tableStart.toIntOrNull() ?: 0).coerceIn(0, 2000)
            val tMax  = (tableEnd.toIntOrNull()   ?: 500).coerceIn(0, 2000)
            val tStep = (tableStep.toIntOrNull()  ?: 50).coerceIn(1, 500)
            if (tMin >= tMax) {
                error  = "Table start must be less than table end"
                result = null
                return
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
        } catch (_: NumberFormatException) {
            error  = "All fields must be numbers"
            result = null
        } catch (e: IllegalArgumentException) {
            error  = e.message
            result = null
        }
    }

    fun applySession(s: SessionData) {
        muzzleVel       = s.muzzleVel
        bc              = s.bc
        bulletWeight    = s.bulletWeight
        sightHeight     = s.sightHeight
        vitalZone       = s.vitalZone
        dragModel       = if (s.dragModel == "G7") Ballistics.DragModel.G7 else Ballistics.DragModel.G1
        selectedPreset  = s.presetName?.let { n -> Ballistics.PRESETS.find { it.name == n } }
        altitude        = s.altitude
        temperature     = s.temperature
        humidity        = s.humidity
        windSpeed       = s.windSpeed
        tableStart      = s.tableStart
        tableEnd        = s.tableEnd
        tableStep       = s.tableStep
        dopeTitle       = s.dopeTitle
        selectedReticle = s.reticleName?.let { n -> Ballistics.RETICLE_PRESETS.find { it.name == n } }
        calculate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Maximum Point Blank Range",
                style    = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                saveDialogName = buildSessionName(selectedPreset?.name, Date())
                showSaveDialog.value = true
            }) {
                Icon(Icons.Default.Save, contentDescription = "Save session")
            }
            IconButton(onClick = {
                loadedSessions = loadSessions(context)
                showLoadDialog.value = true
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Load session")
            }
        }

        // ---- Ammunition preset ----
        SectionLabel("Ammunition", onSecret = { playGunshot() })
        AmmoPresetDropdown(
            selected = selectedPreset,
            presets  = Ballistics.PRESETS,
            onSelect = { picked ->
                if (picked == null) selectedPreset = null else applyPreset(picked)
            }
        )

        // ---- Drag model selector ----
        SectionLabel("Drag Model")
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
            FilterChip(
                selected = false,
                onClick  = { metricMode = !metricMode },
                label    = { Text(if (metricMode) "Metric" else "Not Metric") }
            )
        }

        // ---- Reticle (shapes the DOPE chart illustration) ----
        SectionLabel("Reticle")
        ReticleDropdown(selected = selectedReticle, onSelect = { selectedReticle = it })

        // ---- Bullet & sight ----
        SectionLabel("Bullet & Sight")
        NumberField(
            if (metricMode) "Muzzle Velocity (m/s)" else "Muzzle Velocity (fps)",
            toMetric(muzzleVel) { it * 0.3048 },
            Modifier.fillMaxWidth()
        ) { v -> userEdit({ muzzleVel = it }, fromMetric(v) { it / 0.3048 }) }
        NumberField("Ballistic Coefficient", bc, Modifier.fillMaxWidth()) { v -> userEdit({ bc = it }, v) }
        NumberField("Bullet Weight (gr)",    bulletWeight, Modifier.fillMaxWidth()) { v -> userEdit({ bulletWeight = it }, v) }
        NumberField(
            if (metricMode) "Sight Height over Bore (mm)" else "Sight Height over Bore (in)",
            toMetric(sightHeight) { it * 25.4 },
            Modifier.fillMaxWidth()
        ) { v -> userEdit({ sightHeight = it }, fromMetric(v) { it / 25.4 }) }
        NumberField(
            if (metricMode) "Vital Zone Diameter (cm)" else "Vital Zone Diameter (in)",
            toMetric(vitalZone) { it * 2.54 },
            Modifier.fillMaxWidth()
        ) { v -> userEdit({ vitalZone = it }, fromMetric(v) { it / 2.54 }) }

        // ---- Atmosphere ----
        SectionLabel("Atmosphere")
        NumberField(
            if (metricMode) "Altitude (m)" else "Altitude (ft)",
            toMetric(altitude, "%.0f") { it * 0.3048 },
            Modifier.fillMaxWidth()
        ) { v -> altitude = fromMetric(v) { it / 0.3048 } }
        NumberField(
            if (metricMode) "Temperature (°C)" else "Temperature (°F)",
            toMetric(temperature) { (it - 32.0) * 5.0 / 9.0 },
            Modifier.fillMaxWidth()
        ) { v -> temperature = fromMetric(v) { it * 9.0 / 5.0 + 32.0 } }
        NumberField("Humidity (%)", humidity, Modifier.fillMaxWidth()) { humidity = it }
        NumberField(
            if (metricMode) "Wind Speed (km/h, full value crosswind)" else "Wind Speed (mph, full value crosswind)",
            toMetric(windSpeed) { it * 1.60934 },
            Modifier.fillMaxWidth()
        ) { v -> windSpeed = fromMetric(v) { it / 1.60934 } }

        // ---- Trajectory table range ----
        SectionLabel("Trajectory Table")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                if (metricMode) "Start (m)" else "Start (yd)",
                toMetric(tableStart, "%.0f") { it * 0.9144 },
                Modifier.weight(1f)
            ) { v -> tableStart = fromMetricInt(v) { it / 0.9144 } }
            NumberField(
                if (metricMode) "Step (m)" else "Step (yd)",
                toMetric(tableStep, "%.0f") { it * 0.9144 },
                Modifier.weight(1f)
            ) { v -> tableStep = fromMetricInt(v) { it / 0.9144 } }
            NumberField(
                if (metricMode) "End (m)" else "End (yd)",
                toMetric(tableEnd, "%.0f") { it * 0.9144 },
                Modifier.weight(1f)
            ) { v -> tableEnd = fromMetricInt(v) { it / 0.9144 } }
        }

        Button(
            onClick  = { calculate() },
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
                    if (metricMode) {
                        ResultRow("Near Zero", "%.0f m".format(r.nearZeroYards * 0.9144))
                        ResultRow("Far Zero",  "%.0f m".format(r.farZeroYards  * 0.9144))
                        ResultRow("Max Ordinate", "%.2f cm @ %.0f m".format(r.maxOrdinateInches * 2.54, r.maxOrdinateRangeYards * 0.9144))
                        if (r.energyAtMuzzleFtLb > 0.0)
                            ResultRow("Muzzle Energy", "%.0f J".format(r.energyAtMuzzleFtLb * 1.35582))
                        ResultRow("Maximum Point Blank Range", "%.0f m".format(r.mpbrYards * 0.9144))
                        if (r.energyAtMpbrFtLb > 0.0)
                            ResultRow("Energy at MPBR", "%.0f J".format(r.energyAtMpbrFtLb * 1.35582))
                        ResultRow("Velocity at Near Zero", "%.0f m/s".format(r.velocityAtNearZeroFps * 0.3048))
                        if (r.energyAtNearZeroFtLb > 0.0)
                            ResultRow("Energy at Near Zero", "%.0f J".format(r.energyAtNearZeroFtLb * 1.35582))
                        ResultRow("Velocity at Far Zero",  "%.0f m/s".format(r.velocityAtFarZeroFps * 0.3048))
                        if (r.energyAtFarZeroFtLb > 0.0)
                            ResultRow("Energy at Far Zero", "%.0f J".format(r.energyAtFarZeroFtLb * 1.35582))
                    } else {
                        ResultRow("Near Zero", "%.0f yd".format(r.nearZeroYards))
                        ResultRow("Far Zero",  "%.0f yd".format(r.farZeroYards))
                        ResultRow("Max Ordinate", "%.2f in @ %.0f yd".format(r.maxOrdinateInches, r.maxOrdinateRangeYards))
                        if (r.energyAtMuzzleFtLb > 0.0)
                            ResultRow("Muzzle Energy", "%.0f ft·lb".format(r.energyAtMuzzleFtLb))
                        ResultRow("Maximum Point Blank Range", "%.0f yd".format(r.mpbrYards))
                        if (r.energyAtMpbrFtLb > 0.0)
                            ResultRow("Energy at MPBR", "%.0f ft·lb".format(r.energyAtMpbrFtLb))
                        ResultRow("Velocity at Near Zero", "%.0f fps".format(r.velocityAtNearZeroFps))
                        if (r.energyAtNearZeroFtLb > 0.0)
                            ResultRow("Energy at Near Zero", "%.0f ft·lb".format(r.energyAtNearZeroFtLb))
                        ResultRow("Velocity at Far Zero",  "%.0f fps".format(r.velocityAtFarZeroFps))
                        if (r.energyAtFarZeroFtLb > 0.0)
                            ResultRow("Energy at Far Zero", "%.0f ft·lb".format(r.energyAtFarZeroFtLb))
                    }
                    ResultRow("Bore Angle Above LOS", "%.2f MOA".format(r.boreAngleMoa))
                }
            }

            // Reticle illustration (on-screen) when a reticle is selected
            selectedReticle?.let { reticle ->
                val reticleBmp = remember(r, reticle) {
                    buildReticleBitmap(r, reticle)
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
                    showEnergy = r.energyAtNearZeroFtLb > 0.0,
                    showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0,
                    showMoa    = selectedReticle == null || selectedReticle!!.unit == Ballistics.ReticleUnit.MOA,
                    showMil    = selectedReticle == null || selectedReticle!!.unit == Ballistics.ReticleUnit.MIL,
                    metricMode = metricMode
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
                    val showEnergy = r.energyAtNearZeroFtLb > 0.0
                    val showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0

                    fun doSave() {
                        val bmp = buildDopeChartBitmap(
                            r, label,
                            altitude.toDoubleOrNull()    ?: 0.0,
                            temperature.toDoubleOrNull() ?: 59.0,
                            humidity.toDoubleOrNull()    ?: 0.0,
                            windSpeed.toDoubleOrNull()   ?: 0.0,
                            vitalZone.toDoubleOrNull()   ?: 6.0,
                            showEnergy, showDrift,
                            selectedReticle,
                            dopeTitle,
                            metricMode
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

            Button(
                onClick = {
                    val label      = selectedPreset?.name ?: "Custom"
                    val showEnergy = r.energyAtNearZeroFtLb > 0.0
                    val showDrift  = (windSpeed.toDoubleOrNull() ?: 0.0) != 0.0
                    val bmp = buildDopeChartBitmap(
                        r, label,
                        altitude.toDoubleOrNull()    ?: 0.0,
                        temperature.toDoubleOrNull() ?: 59.0,
                        humidity.toDoubleOrNull()    ?: 0.0,
                        windSpeed.toDoubleOrNull()   ?: 0.0,
                        vitalZone.toDoubleOrNull()   ?: 6.0,
                        showEnergy, showDrift,
                        selectedReticle,
                        dopeTitle,
                        metricMode
                    )
                    PrintHelper(context as android.app.Activity).apply {
                        scaleMode = PrintHelper.SCALE_MODE_FIT
                    }.printBitmap("MPBR DOPE Chart — $label", bmp)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Print DOPE Chart") }
        }
    }

    // ---- Save session dialog ----
    if (showSaveDialog.value) {
        AlertDialog(
            onDismissRequest = { showSaveDialog.value = false },
            title   = { Text("Save Session") },
            text    = {
                OutlinedTextField(
                    value         = saveDialogName,
                    onValueChange = { saveDialogName = it },
                    label         = { Text("Session name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick  = {
                        if (saveDialogName.isNotBlank()) {
                            saveSession(context, currentSession(saveDialogName.trim()))
                            showSaveDialog.value = false
                        }
                    },
                    enabled = saveDialogName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Load session dialog ----
    if (showLoadDialog.value) {
        AlertDialog(
            onDismissRequest = { showLoadDialog.value = false },
            title   = { Text("Load Session") },
            text    = {
                if (loadedSessions.isEmpty()) {
                    Text("No saved sessions yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in loadedSessions) {
                            Row(
                                modifier          = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick  = { applySession(s); showLoadDialog.value = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text(s.name) }
                                IconButton(onClick = {
                                    renameTarget = s.name
                                    renameText   = s.name
                                    showRenameDialog.value = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                TextButton(onClick = {
                                    deleteSession(context, s.name)
                                    loadedSessions = loadSessions(context)
                                }) { Text("✕") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadDialog.value = false }) { Text("Close") }
            }
        )
    }

    if (showRenameDialog.value) {
        AlertDialog(
            onDismissRequest = { showRenameDialog.value = false },
            title   = { Text("Rename Session") },
            text    = {
                OutlinedTextField(
                    value         = renameText,
                    onValueChange = { renameText = it },
                    label         = { Text("Name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank() && renameText.trim() != renameTarget,
                    onClick = {
                        renameSession(context, renameTarget, renameText.trim())
                        loadedSessions = loadSessions(context)
                        showRenameDialog.value = false
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrajectoryTableCard(
    rows: List<Ballistics.TrajectoryRow>,
    showEnergy: Boolean,
    showDrift: Boolean,
    showMoa: Boolean = true,
    showMil: Boolean = true,
    metricMode: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Trajectory Table", style = MaterialTheme.typography.titleLarge)

            val header = buildList {
                add(if (metricMode) "Rng (m)" else "Range")
                add(if (metricMode) "Drop (cm)" else "Drop")
                if (showMoa) add("MOA")
                if (showMil) add("MIL")
                if (showDrift) {
                    if (showMoa) add("W.MOA")
                    if (showMil) add("W.MIL")
                }
                add(if (metricMode) "Vel (m/s)" else "Vel")
                if (showEnergy) add(if (metricMode) "E (J)" else "Energy")
            }
            TrajRow(cells = header, style = MaterialTheme.typography.labelMedium, bold = true)
            HorizontalDivider()

            for (row in rows) {
                val cells = buildList {
                    add(if (metricMode) "%.0f m".format(row.rangeYards * 0.9144) else "${row.rangeYards} yd")
                    add(if (metricMode) "%.1f cm".format(row.dropInches * 2.54)  else "%.1f in".format(row.dropInches))
                    if (showMoa) add("%.1f".format(row.holdoverMoa))
                    if (showMil) add("%.2f".format(row.holdoverMil))
                    if (showDrift) {
                        if (showMoa) add("%.1f".format(row.driftMoa))
                        if (showMil) add("%.2f".format(row.driftMil))
                    }
                    add(if (metricMode) "%.0f".format(row.velocityFps * 0.3048) else "%.0f fps".format(row.velocityFps))
                    if (showEnergy) add(if (metricMode) "%.0f".format(row.energyFtLb * 1.35582) else "%.0f ft·lb".format(row.energyFtLb))
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
private fun SectionLabel(text: String, onSecret: (() -> Unit)? = null) {
    var tapCount     by remember { mutableIntStateOf(0) }
    var firstTapTime by remember { mutableLongStateOf(0L) }
    val mod = if (onSecret != null) Modifier.clickable {
        val now = System.currentTimeMillis()
        if (tapCount == 0 || now - firstTapTime > 2000L) {
            tapCount = 1; firstTapTime = now
        } else if (++tapCount >= 5) {
            tapCount = 0; onSecret()
        }
    } else Modifier
    Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
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
 * The section spans [sectionTop]..[sectionTop]+[sectionH] at full bitmap width [w].
 */
private fun drawReticleSection(
    cv: android.graphics.Canvas,
    w: Int,
    result: Ballistics.MpbrResult,
    reticle: Ballistics.ReticlePreset,
    sectionTop: Float,
    sectionH: Float,
    bsz: Float,
    s: Int
) {
    val r   = (sectionH * 0.40f).toInt()
    val cx  = w * 0.26f
    val cy  = sectionTop + sectionH * 0.50f
    val ppu = r / reticle.vertExtent.toFloat()

    // ---- scope circle ----
    cv.drawCircle(cx, cy, r.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE })
    cv.drawCircle(cx, cy, r.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 3f
        })
    // CIRCLE_BDC / RING_BDC: draw the full ring outside clip
    if (reticle.style == Ballistics.ReticleStyle.CIRCLE_BDC ||
        reticle.style == Ballistics.ReticleStyle.RING_BDC) {
        val circleR = reticle.majorSpacing.toFloat() * ppu
        cv.drawCircle(cx, cy, circleR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2f
        })
    }

    // HORSESHOE_BDC: arc with 60° gap at bottom (120°→60° clockwise = 300° sweep)
    if (reticle.style == Ballistics.ReticleStyle.HORSESHOE_BDC) {
        val circleR = reticle.majorSpacing.toFloat() * ppu
        val oval = android.graphics.RectF(cx - circleR, cy - circleR, cx + circleR, cy + circleR)
        cv.drawArc(oval, 120f, 300f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2f
        })
    }

    // AR-BDC3: horseshoe (large top arc + two side hooks) drawn outside clip
    if (reticle.style == Ballistics.ReticleStyle.AR_BDC3) {
        val circleR = reticle.majorSpacing.toFloat() * ppu
        val oval    = android.graphics.RectF(cx - circleR, cy - circleR, cx + circleR, cy + circleR)
        val pArc    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2.5f
        }
        // Top arc: 120° centered at 12 o'clock (270° in Android canvas)
        cv.drawArc(oval, 210f, 120f, false, pArc)
        // Right hook: ~35° at 3 o'clock area
        cv.drawArc(oval, 345f,  35f, false, pArc)
        // Left hook: ~35° at 9 o'clock area
        cv.drawArc(oval, 160f,  35f, false, pArc)
    }

    // DRT reticle: draw both rings outside clip for guaranteed visibility
    if (reticle.style == Ballistics.ReticleStyle.DRT) {
        val innerR      = reticle.majorSpacing.toFloat() * ppu           // ~25 MOA
        val outerR      = reticle.postStart.toFloat()   * ppu            // ~71.5 MOA
        val innerStroke = (6f * ppu).coerceAtLeast(s * 2f)               // 6 MOA thick
        val outerStroke = (3f * ppu).coerceAtLeast(s * 1.5f)             // 3 MOA thick
        cv.drawCircle(cx, cy, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = innerStroke
        })
        cv.drawCircle(cx, cy, outerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = outerStroke
        })
    }

    // Circle-dot reticles: draw the large ring + cardinal tick marks outside the clip
    if (reticle.style == Ballistics.ReticleStyle.CIRCLE_DOT) {
        val rings = reticle.holdoverMarks.ifEmpty { listOf(reticle.majorSpacing) }
        val outerR   = rings.last().toFloat() * ppu
        val halfTick = outerR * 0.10f
        val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 4f
        }
        val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; strokeWidth = s * 3f
        }
        for (rMoa in rings) cv.drawCircle(cx, cy, rMoa.toFloat() * ppu, pRing)
        // cardinal ticks on outermost ring only
        cv.drawLine(cx, cy - outerR - halfTick, cx, cy - outerR + halfTick, pTick)
        cv.drawLine(cx, cy + outerR - halfTick, cx, cy + outerR + halfTick, pTick)
        cv.drawLine(cx + outerR - halfTick, cy, cx + outerR + halfTick, cy, pTick)
        cv.drawLine(cx - outerR - halfTick, cy, cx - outerR + halfTick, cy, pTick)
    }

    // reticle name label above circle
    val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY; textSize = bsz * 0.72f; typeface = Typeface.DEFAULT_BOLD
    }
    val lText = "Reticle: ${reticle.name}"
    cv.drawText(lText, cx - pLbl.measureText(lText) / 2f, sectionTop + s * 8f + bsz * 0.72f, pLbl)

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
        val margin = (r - s * 4f)
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
            if (abs(y - lastY) < bsz * 0.82f) continue
            val unitStr = if (reticle.unit == Ballistics.ReticleUnit.MIL)
                "%.2f mil".format(hold) else "%.1f MOA".format(hold)
            callouts.add(ReticleCallout(x, y, calloutColors[idx % calloutColors.size], "${row.rangeYards} yd  ($unitStr)"))
            lastY = y
            idx++
        }
    }

    // ---- clip to circle, draw reticle + trajectory hash marks ----
    val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
    val pMaj  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
    val pDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }

    cv.withClip(android.graphics.Path().apply {
        addCircle(cx, cy, r.toFloat(), android.graphics.Path.Direction.CW)
    }) {
        when (reticle.style) {

            Ballistics.ReticleStyle.BDC -> {
                cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)
                if (reticle.postStart > 0.0) {
                    val postPx = (reticle.postStart * ppu).toFloat()
                    cv.drawLine(cx - postPx, cy, cx + postPx, cy, pLine)
                    val pPost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK; strokeWidth = s * 5f
                    }
                    cv.drawLine(cx - r.toFloat(), cy, cx - postPx, cy, pPost)
                    cv.drawLine(cx + postPx,      cy, cx + r.toFloat(), cy, pPost)
                } else {
                    cv.drawLine(cx - r.toFloat(), cy, cx + r.toFloat(), cy, pLine)
                }
                val wh    = ppu * 0.65f   // proportional to unit spacing, not circle size
                val pWind = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = s * 2f
                }
                for (wm in reticle.windageMarks) {
                    val wx = (wm * ppu).toFloat()
                    for (sign in listOf(1f, -1f)) {
                        cv.drawLine(cx + sign * wx, cy - wh, cx + sign * wx, cy + wh, pWind)
                    }
                }
                val hmHW   = ppu * 0.65f  // proportional to unit spacing
                val pHMark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = s * 2f
                }
                for (h in reticle.holdoverMarks) {
                    val hy = cy + (h * ppu).toFloat()
                    cv.drawLine(cx - hmHW, hy, cx + hmHW, hy, pHMark)
                }
            }

            Ballistics.ReticleStyle.BALLISTIC_E3 -> {
                // Burris Ballistic E3: duplex horizontal posts, short upper stadia,
                // three lower BDC bars, and cascading 10 mph wind dots.
                val gap    = reticle.majorSpacing.toFloat() * ppu   // 4 MOA thin section half-width
                val barHH  = reticle.minorSpacing.toFloat() * ppu   // horizontal bar half-height
                val topLen = 3.2f * ppu
                val topSub = 1.0f * ppu
                val tickH  = (barHH * 0.70f).coerceAtLeast(s * 3f)
                val bdcHW  = listOf(1.5f, 2.5f, 3.5f)
                val wdMoa  = listOf(1.54f, 2.42f, 3.38f)
                val dotR   = (ppu * 0.13f).coerceAtLeast(s * 1.2f)

                val pBlk  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 1.15f }
                val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 1.5f }
                val pBdc  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pNum  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = (ppu * 0.72f).coerceAtLeast(s * 6f)
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }

                // Short upper vertical stadia with the small D = 1 MOA subtension bar.
                cv.drawLine(cx, cy - topLen, cx, cy - s * 0.5f, pBlk)
                cv.drawLine(cx - ppu * 0.35f, cy - topSub, cx + ppu * 0.35f, cy - topSub, pBdc)

                // Heavy horizontal duplex posts taper into the fine center section.
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx - r.toFloat(), cy - barHH)
                    lineTo(cx - gap, cy - s * 0.55f)
                    lineTo(cx - gap, cy + s * 0.55f)
                    lineTo(cx - r.toFloat(), cy + barHH)
                    close()
                }, pFill)
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx + gap, cy - s * 0.55f)
                    lineTo(cx + r.toFloat(), cy - barHH)
                    lineTo(cx + r.toFloat(), cy + barHH)
                    lineTo(cx + gap, cy + s * 0.55f)
                    close()
                }, pFill)

                // Thin horizontal line through the 4 MOA gap, with interior ticks at 1/2/3 MOA.
                cv.drawLine(cx - gap, cy, cx + gap, cy, pBlk)
                for (t in 1 until reticle.majorSpacing.toInt()) {
                    val tx = t * ppu
                    cv.drawLine(cx + tx, cy - tickH, cx + tx, cy + tickH, pTick)
                    cv.drawLine(cx - tx, cy - tickH, cx - tx, cy + tickH, pTick)
                }
                cv.drawText("4", cx - gap * 0.52f, cy - tickH - s * 2f, pNum)
                cv.drawText("4", cx + gap * 0.52f, cy - tickH - s * 2f, pNum)

                // Thin vertical from center to first BDC mark
                cv.drawLine(cx, cy, cx, cy + reticle.holdoverMarks[0].toFloat() * ppu, pBlk)

                // Three BDC marks: horizontal line + windage dot each side, connected by thin vertical
                reticle.holdoverMarks.forEachIndexed { idx, h ->
                    val hy  = cy + h.toFloat() * ppu
                    val hw  = bdcHW.getOrElse(idx) { 2f } * ppu
                    val wdx = wdMoa.getOrElse(idx) { 2f } * ppu
                    cv.drawLine(cx - hw, hy, cx + hw, hy, pBdc)
                    cv.drawCircle(cx + wdx, hy, dotR, pDot)
                    cv.drawCircle(cx - wdx, hy, dotR, pDot)
                    if (idx < reticle.holdoverMarks.size - 1)
                        cv.drawLine(cx, hy, cx, cy + reticle.holdoverMarks[idx + 1].toFloat() * ppu, pBlk)
                }

                // Thick bottom post from below last BDC to scope edge
                val bottomStart = cy + reticle.holdoverMarks.last().toFloat() * ppu + ppu * 0.5f
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx - s * 0.75f, bottomStart)
                    lineTo(cx + s * 0.75f, bottomStart)
                    lineTo(cx + barHH * 0.60f, cy + r.toFloat())
                    lineTo(cx - barHH * 0.60f, cy + r.toFloat())
                    close()
                }, pFill)
            }

            Ballistics.ReticleStyle.DUPLEX -> {
                // Plex/duplex: large rectangular posts with a small notch at the inner tip.
                // Horizontal posts are taller than vertical posts are wide (classic Burris look).
                val gapPx  = reticle.majorSpacing.toFloat() * ppu   // center to inner post tip
                val postHH = reticle.minorSpacing.toFloat() * ppu    // all posts same half-thickness
                val notch  = postHH * 0.60f  // notch depth on inner tip (the pointed cutout)
                val pFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pThin   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }

                // Top post: rectangle with a small V-notch at the inner (bottom) tip
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx - postHH, cy - r.toFloat())
                    lineTo(cx + postHH, cy - r.toFloat())
                    lineTo(cx + postHH, cy - gapPx - notch)
                    lineTo(cx,           cy - gapPx)          // notch tip
                    lineTo(cx - postHH, cy - gapPx - notch)
                    close()
                }, pFill)
                // Bottom post
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx - postHH, cy + r.toFloat())
                    lineTo(cx + postHH, cy + r.toFloat())
                    lineTo(cx + postHH, cy + gapPx + notch)
                    lineTo(cx,           cy + gapPx)          // notch tip
                    lineTo(cx - postHH, cy + gapPx + notch)
                    close()
                }, pFill)
                // Left post: rectangle with V-notch at the inner (right) tip
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx - r.toFloat(), cy - postHH)
                    lineTo(cx - r.toFloat(), cy + postHH)
                    lineTo(cx - gapPx - notch, cy + postHH)
                    lineTo(cx - gapPx,         cy)            // notch tip
                    lineTo(cx - gapPx - notch, cy - postHH)
                    close()
                }, pFill)
                // Right post
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx + r.toFloat(), cy - postHH)
                    lineTo(cx + r.toFloat(), cy + postHH)
                    lineTo(cx + gapPx + notch, cy + postHH)
                    lineTo(cx + gapPx,         cy)            // notch tip
                    lineTo(cx + gapPx + notch, cy - postHH)
                    close()
                }, pFill)

                // Thin center crosshair (the fine lines spanning the open center area)
                cv.drawLine(cx - gapPx, cy, cx + gapPx, cy, pThin)
                cv.drawLine(cx, cy - gapPx, cx, cy + gapPx, pThin)
            }

            Ballistics.ReticleStyle.CIRCLE_BDC -> {
                // Circle drawn outside clip; inside: center dot, thin post, BDC ticks, thick bottom post
                val dotR   = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 2f)
                val circleR = reticle.majorSpacing.toFloat() * ppu
                val tickHW = ppu * 1.8f
                val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = android.graphics.Color.BLACK }
                val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
                val pThick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 5f }
                val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }

                // Center dot inside the circle
                cv.drawCircle(cx, cy, dotR, pFill)

                // Faint reference above center for trajectory callouts
                cv.drawLine(cx, cy - r.toFloat(), cx, cy - circleR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat()
                })

                // Thin BDC post from bottom of circle to last holdover mark
                val lastHy = if (reticle.holdoverMarks.isNotEmpty())
                    cy + reticle.holdoverMarks.last().toFloat() * ppu else cy + circleR
                cv.drawLine(cx, cy + circleR, cx, lastHy, pThin)

                // BDC tick marks (no labels)
                for (h in reticle.holdoverMarks) {
                    val hy = cy + h.toFloat() * ppu
                    cv.drawLine(cx - tickHW, hy, cx + tickHW, hy, pTick)
                }

                // Thick bottom post from last BDC mark to scope edge
                cv.drawLine(cx, lastHy + ppu * 0.5f, cx, cy + r.toFloat(), pThick)
            }

            Ballistics.ReticleStyle.HORSESHOE_BDC -> {
                // Arc drawn outside clip; inside: center dot, thin post from arc gap, BDC ticks, thick post
                val dotR    = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 2f)
                val circleR = reticle.majorSpacing.toFloat() * ppu
                val tickHW  = ppu * 1.8f
                val pFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = android.graphics.Color.BLACK }
                val pThin   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
                val pThick  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 5f }
                val pTick   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }

                cv.drawCircle(cx, cy, dotR, pFill)

                // Faint reference above arc for trajectory callouts
                cv.drawLine(cx, cy - r.toFloat(), cx, cy - circleR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat()
                })

                // Thin post from arc gap bottom to last holdover mark
                val lastHy = if (reticle.holdoverMarks.isNotEmpty())
                    cy + reticle.holdoverMarks.last().toFloat() * ppu else cy + circleR
                cv.drawLine(cx, cy + circleR, cx, lastHy, pThin)

                // Each hash uses its own half-width from windageMarks if provided, else fixed fallback
                reticle.holdoverMarks.forEachIndexed { i, h ->
                    val hy  = cy + h.toFloat() * ppu
                    val hw  = if (i < reticle.windageMarks.size)
                        reticle.windageMarks[i].toFloat() * ppu else tickHW
                    cv.drawLine(cx - hw, hy, cx + hw, hy, pTick)
                }

                cv.drawLine(cx, lastHy + ppu * 0.5f, cx, cy + r.toFloat(), pThick)
            }

            Ballistics.ReticleStyle.AR_BDC3 -> {
                // Horseshoe drawn outside clip; inside: center dot + BDC post + windage dot grid
                val dotR   = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 2f)
                val tickHW = ppu * 1.5f
                val wDotR  = (ppu * 0.28f).coerceAtLeast(s * 1.5f)
                val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pLine  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
                val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pLbl   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; textSize = ppu * 2.6f
                    typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT
                }
                val pLblR  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; textSize = ppu * 2.6f
                    typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
                }

                cv.drawCircle(cx, cy, dotR, pFill)
                cv.drawLine(cx, cy - r.toFloat(), cx, cy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat()
                })
                cv.drawLine(cx, cy, cx, cy + r.toFloat(), pLine)

                // Windage extents (MOA) at 15 mph per range row
                val maxWindage = listOf(5.0f, 7.2f, 9.6f, 12.2f)
                val rangeLabels = listOf("3", "4", "5", "6")

                reticle.holdoverMarks.forEachIndexed { idx, h ->
                    val hy   = cy + h.toFloat() * ppu
                    val maxW = maxWindage.getOrElse(idx) { 5.0f }

                    // BDC tick on the central post
                    cv.drawLine(cx - tickHW, hy, cx + tickHW, hy, pTick)

                    // Windage dots at 1 MOA spacing out to the 15 mph extent
                    var wMoa = 1f
                    while (wMoa <= maxW) {
                        val wx = wMoa * ppu
                        cv.drawCircle(cx + wx, hy, wDotR, pFill)
                        cv.drawCircle(cx - wx, hy, wDotR, pFill)
                        wMoa += 1f
                    }

                    // Range label at outermost dot, both sides
                    val lx = maxW * ppu + ppu * 0.5f
                    cv.drawText(rangeLabels.getOrElse(idx) { "" }, cx + lx, hy + pLbl.textSize * 0.35f, pLbl)
                    cv.drawText(rangeLabels.getOrElse(idx) { "" }, cx - lx, hy + pLblR.textSize * 0.35f, pLblR)

                    // 600-yd row: also label the 10 mph wind-hold position (≈8.1 MOA each side)
                    if (idx == reticle.holdoverMarks.size - 1) {
                        val tenX = 8.1f * ppu + ppu * 0.4f
                        cv.drawText("10", cx + tenX, hy + pLbl.textSize * 1.1f, pLbl)
                        cv.drawText("10", cx - tenX, hy + pLblR.textSize * 1.1f, pLblR)
                    }
                }
            }

        Ballistics.ReticleStyle.BRC -> {
                // BRC: center dot, two holdunder dots below center, inward-pointing chevrons
                val dotR  = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 3f)  // center 3 MOA dot
                val holdR = dotR * 0.65f                                                    // smaller holdunder dots

                val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pChev  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }

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
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat() })
            }

            Ballistics.ReticleStyle.DRT -> {
                // Both rings drawn outside clip above; center dot + faint vertical reference
                val dotR = (reticle.minorSpacing * ppu).toFloat().coerceAtLeast(s * 2f)
                cv.drawCircle(cx, cy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL; color = android.graphics.Color.BLACK
                })
                cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat()
                    })
            }

            Ballistics.ReticleStyle.CIRCLE_DOT -> {
                // Ring drawn outside clip above; here just the center dot + faint reference line
                if (reticle.minorSpacing > 0) {
                    val aDotR = reticle.minorSpacing.toFloat() * ppu
                    cv.drawCircle(cx, cy, aDotR.coerceAtLeast(s * 3f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL; color = android.graphics.Color.BLACK
                    })
                }
                cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.LTGRAY; strokeWidth = s.toFloat()
                    })
            }

            Ballistics.ReticleStyle.MRAD_TREE -> {
                val stepsPerMaj = (reticle.majorSpacing / reticle.minorSpacing).roundToInt()
                val minorPx     = (reticle.minorSpacing * ppu).toFloat()
                val majorPx     = (reticle.majorSpacing * ppu).toFloat()
                val postPx      = reticle.postStart.toFloat() * ppu
                val circleR     = ppu * 1.0f      // 1 MRAD speed ring radius
                val treeStart   = 2               // tree rows begin at 2 MRAD below center
                val treeDepth   = reticle.vertExtent.toInt()

                val pThick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = s * 6f
                }
                val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = s.toFloat()
                }
                val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; strokeWidth = s * 1.5f
                }
                val pNum   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = android.graphics.Color.BLACK
                    textSize  = majorPx * 0.48f
                    typeface  = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }

                // Horizontal: thin inner + thick outer posts
                cv.drawLine(cx - postPx, cy, cx + postPx, cy, pThin)
                cv.drawLine(cx - r.toFloat(), cy, cx - postPx, cy, pThick)
                cv.drawLine(cx + postPx,      cy, cx + r.toFloat(), cy, pThick)

                // Hash marks and numbers on horizontal stadia
                val majTickH  = majorPx * 0.45f
                val minTickH  = majorPx * 0.22f
                val hMaxSteps = (reticle.postStart / reticle.minorSpacing).roundToInt()
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
                    strokeWidth = s * 1.5f
                })

                // Short vertical stadia above center with minor ticks
                cv.drawLine(cx, cy - r.toFloat(), cx, cy - circleR, pThin)
                val vMaxSteps = floor(((r - circleR) / minorPx).toDouble()).toInt()
                for (vStep in 1..vMaxSteps) {
                    val vy    = circleR + vStep * minorPx
                    val isMaj = vStep % stepsPerMaj == 0
                    val tw    = if (isMaj) majTickH else minTickH
                    cv.drawLine(cx - tw, cy - vy, cx + tw, cy - vy, pTick)
                }

                // Vertical connector from circle bottom to tree start
                cv.drawLine(cx, cy + circleR, cx, cy + treeStart * ppu, pThin)

                // Christmas tree: dot grid + half-MRAD ticks between dots
                val dotRad    = (majorPx * 0.13f).coerceAtLeast(s * 3f)
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
                val stepsPerMaj = (reticle.majorSpacing / reticle.minorSpacing).roundToInt() // 4
                val minorPx     = (reticle.minorSpacing * ppu).toFloat()   // 1 MOA
                val majorPx     = (reticle.majorSpacing * ppu).toFloat()   // 4 MOA
                val postPx      = reticle.postStart.toFloat() * ppu         // 26 MOA
                val treeStep    = reticle.majorSpacing.toInt()              // 4
                val treeDepth   = reticle.vertExtent.toInt() - treeStep    // 36

                val pThick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 6f }
                val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
                val pTick  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 1.5f }
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
                cv.drawLine(cx - r.toFloat(), cy, cx - postPx, cy, pThick)
                cv.drawLine(cx + postPx, cy, cx + r.toFloat(), cy, pThick)
                cv.drawLine(cx, cy - r.toFloat(), cx, cy, pThin)                          // V above center
                cv.drawLine(cx, cy, cx, cy + treeDepth * ppu, pThin)                      // V through tree
                cv.drawLine(cx, cy + treeDepth * ppu, cx, cy + r.toFloat(), pThick)       // V bottom post

                // Horizontal hash marks + labels
                val majTickH  = majorPx * 0.45f
                val minTickH  = majorPx * 0.22f
                val hMaxSteps = (reticle.postStart / reticle.minorSpacing).roundToInt()
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
                val vMaxSteps = floor((r / minorPx).toDouble()).toInt()
                for (vStep in 1..vMaxSteps) {
                    val vy = vStep * minorPx
                    if (vy > r) break
                    val isMaj = vStep % stepsPerMaj == 0
                    val tw = if (isMaj) majTickH else minTickH
                    cv.drawLine(cx - tw, cy - vy, cx + tw, cy - vy, pTick)
                    if (isMaj) {
                        val label = (vStep / stepsPerMaj * treeStep).toString()
                        cv.drawText(label, cx + tw + majorPx * 0.12f, cy - vy + pNumL.textSize * 0.35f, pNumL)
                    }
                }

                // Center dot (0.14 MOA)
                cv.drawCircle(cx, cy, (ppu * 0.14f).coerceAtLeast(s * 2f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK })

                // Dot-grid tree: rows every treeStep MOA, dots every 2 MOA within row
                val dotRad    = (majorPx * 0.10f).coerceAtLeast(s * 2f)
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

            Ballistics.ReticleStyle.ACOG_CHEVRON -> {
                // majorSpacing = 2.77: chevron base half-width (MOA) — 19" at 300m
                // minorSpacing = 3.5:  chevron depth tip-to-base (MOA)
                // holdoverMarks: BDC stadia depths below tip (MOA) for 400–800m
                val chevHW = reticle.majorSpacing.toFloat() * ppu   // chevron base half-width
                val chevH  = reticle.minorSpacing.toFloat() * ppu   // chevron tip-to-base depth
                val baseY  = cy + chevH                              // y of chevron base
                // Stadia half-widths for 400–800m (19" ranging, scale = 300m/range)
                val stadiaHW = floatArrayOf(2.08f, 1.66f, 1.39f, 1.19f, 1.04f)
                val pChev = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 3f; strokeCap = Paint.Cap.ROUND }
                val pSta  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                // Thin vertical post above chevron tip (to scope top)
                cv.drawLine(cx, sectionTop, cx, cy, pLine)
                // Chevron arms: tip at (cx, cy), open downward
                cv.drawLine(cx, cy, cx - chevHW, baseY, pChev)
                cv.drawLine(cx, cy, cx + chevHW, baseY, pChev)
                // Vertical BDC stadia arm from chevron base to scope bottom
                cv.drawLine(cx, baseY, cx, sectionTop + sectionH, pLine)
                // BDC stadia marks (widths decrease with distance — ranging feature)
                reticle.holdoverMarks.forEachIndexed { idx, h ->
                    val hy = cy + h.toFloat() * ppu
                    val hw = if (idx < stadiaHW.size) stadiaHW[idx] * ppu else 1.0f * ppu
                    cv.drawLine(cx - hw, hy, cx + hw, hy, pSta)
                }
            }

            Ballistics.ReticleStyle.ACOG_DONUT -> {
                // majorSpacing = 2.0: donut ring radius (MOA)
                // minorSpacing = 0.3: center dot radius (MOA)
                // holdoverMarks: BDC stadia depths below center (MOA) for 400–800m
                val ringR  = reticle.majorSpacing.toFloat() * ppu
                val dotR   = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 2f)
                val stadiaHW = floatArrayOf(2.08f, 1.66f, 1.39f, 1.19f, 1.04f)
                val pRing  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pFill  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                val pSta   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                // Thin vertical crosshair above and below ring
                cv.drawLine(cx, sectionTop, cx, cy - ringR, pLine)
                cv.drawLine(cx, cy + ringR, cx, sectionTop + sectionH, pLine)
                // Donut ring and center dot
                cv.drawCircle(cx, cy, ringR, pRing)
                cv.drawCircle(cx, cy, dotR, pFill)
                // BDC stadia (widths narrow with distance — 19" ranging)
                reticle.holdoverMarks.forEachIndexed { idx, h ->
                    val hy = cy + h.toFloat() * ppu
                    val hw = if (idx < stadiaHW.size) stadiaHW[idx] * ppu else 1.0f * ppu
                    cv.drawLine(cx - hw, hy, cx + hw, hy, pSta)
                }
            }

            Ballistics.ReticleStyle.CHEVRON_BDC -> {
                // UUQ Ranger ER Arrow BDC: separated sidebars, red arrow, and lower BDC ladder.
                // windageMarks: [innerEdge, interiorTick..., outerEdge] for H arm segments each side
                // holdoverMarks: labeled BDC tick positions (MOA below center); etched as numbers in glass
                val chevHW   = reticle.majorSpacing.toFloat() * ppu
                val chevH    = reticle.minorSpacing.toFloat() * ppu
                val tipY     = cy - chevH
                val baseY    = cy + ppu * 0.15f
                val stemTop  = cy + ppu * 0.55f
                val armTHH   = ppu * 0.48f
                val armSHH   = ppu * 0.28f
                val bdcMHW   = ppu * 0.58f
                val bdcMinHW = ppu * 0.42f
                val pChev   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(225, 35, 35)
                    strokeWidth = (s * 2.2f).coerceAtLeast(ppu * 0.10f)
                    strokeCap = Paint.Cap.SQUARE
                    strokeJoin = Paint.Join.MITER
                }
                val pArm    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 1.4f }
                val pPost   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 1.35f }
                val pLbl    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = (ppu * 0.58f).coerceAtLeast(s * 5f)
                    textAlign = Paint.Align.LEFT
                }
                // Two separate H arm segments with no line through the arrow.
                if (reticle.windageMarks.size >= 2) {
                    val innerMoa = reticle.windageMarks.first().toFloat()
                    val outerMoa = reticle.windageMarks.last().toFloat()
                    for (sign in listOf(1f, -1f)) {
                        cv.drawLine(cx + sign * innerMoa * ppu, cy, cx + sign * outerMoa * ppu, cy, pArm)
                        reticle.windageMarks.forEachIndexed { idx, wm ->
                            val wx = cx + sign * wm.toFloat() * ppu
                            val halfH = if (idx == 0 || idx == 2) armTHH else armSHH
                            cv.drawLine(wx, cy - halfH, wx, cy + halfH, pArm)
                        }
                    }
                }

                // BDC stem starts below the red arrow. The visible ladder begins at 4 MOA.
                val maxTick = 11
                cv.drawLine(cx, stemTop, cx, cy + maxTick * ppu, pPost)
                val labelSet = reticle.holdoverMarks.map { it.toInt() }.toHashSet()
                for (i in 4..maxTick) {
                    val ty = cy + i * ppu
                    val hw = if (i in labelSet) bdcMHW else bdcMinHW
                    cv.drawLine(cx - hw, ty, cx + hw, ty, pPost)
                    if (i in labelSet) cv.drawText(i.toString(), cx + bdcMHW + s * 2f, ty + pLbl.textSize * 0.35f, pLbl)
                }

                // Red arrow/chevron above the stem, open at the bottom.
                cv.drawLine(cx, tipY, cx - chevHW, baseY, pChev)
                cv.drawLine(cx, tipY, cx + chevHW, baseY, pChev)
            }

            Ballistics.ReticleStyle.LEAD_RINGS -> {
                // holdoverMarks: ring radii in MOA for each lead ring (10/20/30/40 mph, or custom)
                // majorSpacing: mph per ring step — used to generate "N mph" labels (10 = 10/20/30/40)
                val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2f
                }
                val pLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = (ppu * 8f).coerceAtLeast(s * 5f).coerceAtMost(ppu * 18f)
                    textAlign = Paint.Align.LEFT
                }
                val mphStep = reticle.majorSpacing.toInt().coerceAtLeast(1)
                // Thin full crosshair
                cv.drawLine(cx - r.toFloat(), cy, cx + r.toFloat(), cy, pLine)
                cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)
                // Concentric lead rings with speed labels at top-right of each ring
                reticle.holdoverMarks.forEachIndexed { idx, rm ->
                    val rr = rm.toFloat() * ppu
                    cv.drawCircle(cx, cy, rr, pRing)
                    val mph = (idx + 1) * mphStep
                    val lx = cx + rr * 0.707f + s * 2f
                    val ly = cy - rr * 0.707f - s * 2f
                    cv.drawText("$mph mph", lx, ly, pLbl)
                }
            }

            Ballistics.ReticleStyle.SPIDER_SIGHT -> {
                // majorSpacing = outer ring radius (MOA)
                // minorSpacing = center ring radius (MOA)
                // windageMarks[0] = bead position on all 4 spokes (MOA from center)
                val outerR  = reticle.majorSpacing.toFloat() * ppu
                val innerR  = reticle.minorSpacing.toFloat() * ppu
                val beadR   = (ppu * 4f).coerceAtLeast(s * 2.5f)
                val tickLen = outerR * 0.09f
                val diagonal = 0.70711f   // cos/sin(45°)
                val pOuter  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 4f }
                val pInner  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pSpoke  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pBead   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
                // Outer ring (outside clip so it's always fully visible)
                cv.drawCircle(cx, cy, outerR, pOuter)
                // 4 diagonal tick marks at 45°/135°/225°/315° (pointing inward)
                for (dx in listOf(diagonal, -diagonal)) {
                    for (dy in listOf(diagonal, -diagonal)) {
                        cv.drawLine(cx + dx * outerR, cy + dy * outerR,
                                    cx + dx * (outerR - tickLen), cy + dy * (outerR - tickLen), pSpoke)
                    }
                }
                // 4 crosshair spokes from inner ring to outer ring
                cv.drawLine(cx + innerR, cy, cx + outerR, cy, pSpoke)
                cv.drawLine(cx - innerR, cy, cx - outerR, cy, pSpoke)
                cv.drawLine(cx, cy - innerR, cx, cy - outerR, pSpoke)
                cv.drawLine(cx, cy + innerR, cx, cy + outerR, pSpoke)
                // Center ring
                cv.drawCircle(cx, cy, innerR, pInner)
                // Beads on all 4 spokes at windageMarks[0] radius
                if (reticle.windageMarks.isNotEmpty()) {
                    val br = reticle.windageMarks[0].toFloat() * ppu
                    cv.drawCircle(cx + br, cy, beadR, pBead)
                    cv.drawCircle(cx - br, cy, beadR, pBead)
                    cv.drawCircle(cx, cy - br, beadR, pBead)
                    cv.drawCircle(cx, cy + br, beadR, pBead)
                }
            }

            Ballistics.ReticleStyle.SIG_FL4 -> {
                // SIG manual p.17: dimensions are MOA at max magnification unless marked degrees.
                // Open outlines/arcs in the source diagram are measurement callouts, not reticle lines.
                val crossW    = (0.25f * ppu).coerceAtLeast(s.toFloat())
                val lineW     = (0.45f * ppu).coerceAtLeast(s * 1.25f)
                val markerHW  = 0.75f * ppu
                val bdcStart  = 1.59f * ppu
                val triLen    = 0.75f * ppu
                val triHH     = 0.38f * ppu
                val lowerTip  = cy + 5.73f * ppu
                val lowerEnd  = cy + 20.22f * ppu
                val horizontalTriangles = listOf(3.13f, 5.95f)
                val horizontalHashes = listOf(13.48f)
                val bdcRows = listOf(2.86f, 3.44f, 4.30f, 5.73f)
                val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
                val pCross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = crossW }
                val pRow  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = lineW }

                cv.drawLine(cx - r.toFloat(), cy, cx + r.toFloat(), cy, pCross)
                cv.drawLine(cx, cy - r.toFloat(), cx, cy + lowerTip, pCross)

                // Two filled horizontal reference triangles per side.
                for (triMoa in horizontalTriangles) {
                    val tx = triMoa * ppu
                    cv.drawPath(android.graphics.Path().apply {
                        moveTo(cx - tx + triLen * 0.5f, cy)
                        lineTo(cx - tx - triLen * 0.5f, cy - triHH)
                        lineTo(cx - tx - triLen * 0.5f, cy + triHH)
                        close()
                    }, pFill)
                    cv.drawPath(android.graphics.Path().apply {
                        moveTo(cx + tx - triLen * 0.5f, cy)
                        lineTo(cx + tx + triLen * 0.5f, cy - triHH)
                        lineTo(cx + tx + triLen * 0.5f, cy + triHH)
                        close()
                    }, pFill)
                }

                // One outer horizontal hash per side; other nearby lines in the manual are dimension leaders.
                for (hashMoa in horizontalHashes) {
                    val wx = hashMoa * ppu
                    cv.drawLine(cx + wx, cy - markerHW, cx + wx, cy + markerHW, pRow)
                    cv.drawLine(cx - wx, cy - markerHW, cx - wx, cy + markerHW, pRow)
                }

                for (yMoa in bdcRows) {
                    val hy = cy + yMoa * ppu
                    cv.drawLine(cx - markerHW, hy, cx + markerHW, hy, pRow)
                }
                cv.drawLine(cx, cy + bdcStart, cx, lowerTip, pCross)

                val lowerH = lowerEnd - lowerTip
                val lowerHalf = kotlin.math.tan(Math.toRadians(7.5)).toFloat() * lowerH
                cv.drawPath(android.graphics.Path().apply {
                    moveTo(cx, lowerTip)
                    lineTo(cx - lowerHalf, lowerEnd)
                    lineTo(cx + lowerHalf, lowerEnd)
                    close()
                }, pFill)
            }

            Ballistics.ReticleStyle.RING_BDC -> {
                // Ring drawn outside clip; inside: full crosshair, center circle,
                // lead tick marks on H arm, fine BDC ticks, tapered thick post below postStart
                val cRingR = (reticle.minorSpacing.toFloat() * ppu).coerceAtLeast(s * 2f)
                val pThin  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }
                val pMed   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s * 2f }
                val pPost  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }

                // Full thin crosshair
                cv.drawLine(cx, cy - r.toFloat(), cx, cy + r.toFloat(), pThin)
                cv.drawLine(cx - r.toFloat(), cy, cx + r.toFloat(), cy, pThin)

                // Small center circle (open)
                cv.drawCircle(cx, cy, cRingR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = s.toFloat()
                })

                // Lead/moving-target tick marks on H arm
                val leadHW = ppu * 0.65f
                for (wm in reticle.windageMarks) {
                    val wx = wm.toFloat() * ppu
                    cv.drawLine(cx + wx, cy - leadHW, cx + wx, cy + leadHW, pMed)
                    cv.drawLine(cx - wx, cy - leadHW, cx - wx, cy + leadHW, pMed)
                }

                // Fine BDC ticks (0.8 MOA half-width each side)
                val fineHW = 0.8f * ppu
                for (h in reticle.holdoverMarks) {
                    val hy = cy + h.toFloat() * ppu
                    cv.drawLine(cx - fineHW, hy, cx + fineHW, hy, pMed)
                }

                // Tapered thick post: 4.5 MOA half-width at postStart, 8.9 MOA at scope edge
                if (reticle.postStart > 0.0) {
                    val postY  = cy + reticle.postStart.toFloat() * ppu
                    val topHW  = 4.5f * ppu
                    val botHW  = 8.9f * ppu
                    val edgeY  = cy + r.toFloat()
                    cv.drawPath(android.graphics.Path().apply {
                        moveTo(cx - topHW, postY)
                        lineTo(cx + topHW, postY)
                        lineTo(cx + botHW, edgeY)
                        lineTo(cx - botHW, edgeY)
                        close()
                    }, pPost)
                }
            }

            else -> {
                // Hash / Dot / Christmas tree
                cv.drawLine(cx, sectionTop, cx, sectionTop + sectionH, pLine)
                cv.drawLine(cx - r.toFloat(), cy, cx + r.toFloat(), cy, pLine)

                val stepSz        = if (reticle.minorSpacing > 0) reticle.minorSpacing else reticle.majorSpacing
                val stepsPerMajor = if (reticle.minorSpacing > 0) (reticle.majorSpacing / reticle.minorSpacing).roundToInt() else 1
                val stepCount     = (reticle.vertExtent / stepSz).roundToInt()
                val pMin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; strokeWidth = s.toFloat() }

                for (i in 1..stepCount) {
                    val isMaj = i % stepsPerMajor == 0
                    val hw    = if (isMaj) r * 0.44f else r * 0.22f
                    val paint = if (isMaj) pMaj else pMin
                    for (sign in listOf(1f, -1f)) {
                        val y = cy + sign * i * stepSz.toFloat() * ppu
                        when (reticle.style) {
                            Ballistics.ReticleStyle.DOT ->
                                if (isMaj) cv.drawCircle(cx, y,
                                    (ppu * stepSz * 0.28f).toFloat().coerceAtLeast(s * 2f), pDot)
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
        val dotR = (r * 0.045f).coerceAtLeast(s * 3f)
        for (c in callouts) {
            cv.drawCircle(c.x, c.y, dotR,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = c.color })
        }
    }

    // ---- colored leader lines + labels (outside circle) ----
    val lineStartX = cx + r + s * 10f
    val textX      = w * 0.65f
    for (c in callouts) {
        cv.drawLine(lineStartX, c.y, textX - s * 6f, c.y, Paint().apply {
            this.color = c.color; strokeWidth = s * 1.5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(s * 5f, s * 3f), 0f)
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
    reticle: Ballistics.ReticlePreset
): Bitmap {
    val w   = 1100
    val h   = 560
    val s   = 2
    val bsz = 12f * s
    val bmp = createBitmap(w, h)
    val cv  = android.graphics.Canvas(bmp)
    cv.drawColor(android.graphics.Color.WHITE)
    drawReticleSection(cv, w, result, reticle, 0f, h.toFloat(), bsz, s)
    return bmp
}

/**
 * Renders a DOPE card to a 1200-px-wide Bitmap using Android Canvas.
 * 3× scale so text is sharp when viewed or printed.
 */
private fun buildDopeChartBitmap(
    result: Ballistics.MpbrResult,
    ammoLabel: String,
    altFt: Double,
    tempF: Double,
    rhPct: Double,
    windMph: Double,
    vitalZoneIn: Double,
    showEnergy: Boolean,
    showDrift: Boolean,
    reticle: Ballistics.ReticlePreset? = null,
    cardTitle: String = "MPBR DOPE CARD",
    metricMode: Boolean = false
): Bitmap {
    val s   = 3
    val w   = 400 * s
    val pad = 14 * s
    val tsz = 18f * s
    val bsz = 12f * s
    val lnH = (bsz + 12).toInt()
    val rwH = (bsz + 16).toInt()

    val info = if (metricMode) {
        val windStr = if (windMph == 0.0) "calm" else "%.0f km/h".format(windMph * 1.60934)
        listOf(
            ammoLabel,
            "Near Zero: %.0f m  |  Far Zero: %.0f m  |  MPBR: %.0f m"
                .format(result.nearZeroYards * 0.9144, result.farZeroYards * 0.9144, result.mpbrYards * 0.9144),
            "Max Ordinate: %.2f cm @ %.0f m  |  Bore Angle: %.2f MOA  |  Vital Zone: %.1f cm"
                .format(result.maxOrdinateInches * 2.54, result.maxOrdinateRangeYards * 0.9144, result.boreAngleMoa, vitalZoneIn * 2.54),
            "Alt: %.0f m  |  Temp: %.1f°C  |  RH: %.0f%%  |  Wind: %s"
                .format(altFt * 0.3048, (tempF - 32.0) * 5.0 / 9.0, rhPct, windStr),
            "Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
        )
    } else {
        val windStr = if (windMph == 0.0) "calm" else "%.0f mph".format(windMph)
        listOf(
            ammoLabel,
            "Near Zero: %.0f yd  |  Far Zero: %.0f yd  |  MPBR: %.0f yd"
                .format(result.nearZeroYards, result.farZeroYards, result.mpbrYards),
            "Max Ordinate: %.2f\" @ %.0f yd  |  Bore Angle: %.2f MOA  |  Vital Zone: %.1f\""
                .format(result.maxOrdinateInches, result.maxOrdinateRangeYards, result.boreAngleMoa, vitalZoneIn),
            "Alt: %.0f ft  |  Temp: %.0f°F  |  RH: %.0f%%  |  Wind: %s"
                .format(altFt, tempF, rhPct, windStr),
            "Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
        )
    }
    val showMoa = reticle == null || reticle.unit == Ballistics.ReticleUnit.MOA
    val showMil = reticle == null || reticle.unit == Ballistics.ReticleUnit.MIL
    val cols = buildList {
        add(if (metricMode) "Rng (m)" else "Rng (yd)")
        add(if (metricMode) "Drop (cm)" else "Drop (in)")
        if (showMoa) add("MOA")
        if (showMil) add("MIL")
        if (showDrift) {
            if (showMoa) add("W.MOA")
            if (showMil) add("W.MIL")
        }
        add(if (metricMode) "Vel (m/s)" else "Vel (fps)")
        if (showEnergy) add(if (metricMode) "E (J)" else "E (ft·lb)")
    }

    val reticleH = if (reticle != null) 640 else 0
    val headerH  = pad + tsz.toInt() + 14 + info.size * lnH + pad
    val tableH   = pad + (bsz * 0.2f).toInt() + s * 2 + s + (bsz * 0.8f).toInt() + s * 2 + rwH * result.trajectoryTable.size + pad
    val h        = headerH + s + (if (reticle != null) reticleH + s else 0) + tableH

    val bmp = createBitmap(w, h)
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

    cv.drawRect(0f, y, w.toFloat(), y + s, pRule)
    y += s.toFloat()

    if (reticle != null) {
        drawReticleSection(cv, w, result, reticle, y, reticleH.toFloat(), bsz, s)
        y += reticleH
        cv.drawRect(0f, y, w.toFloat(), y + s, pRule)
        y += s.toFloat()
    }
    y += pad

    val colW = (w - 2 * pad).toFloat() / cols.size
    cols.forEachIndexed { i, col -> cv.drawText(col, pad + i * colW, y, pHdr) }
    y += bsz * 0.2f + s * 2   // descent + equal gap
    cv.drawRect(0f, y, w.toFloat(), y + s, pRule)
    y += s + bsz * 0.8f + s * 2  // skip divider + equal gap + ascent

    result.trajectoryTable.forEachIndexed { idx, row ->
        if (idx % 2 == 1) cv.drawRect(0f, y - bsz * 0.9f, w.toFloat(), y + bsz * 0.3f, pStripe)
        val cells = buildList {
            add(if (metricMode) "%.0f".format(row.rangeYards * 0.9144)  else "${row.rangeYards}")
            add(if (metricMode) "%.1f".format(row.dropInches  * 2.54)   else "%.1f".format(row.dropInches))
            if (showMoa) add("%.1f".format(row.holdoverMoa))
            if (showMil) add("%.2f".format(row.holdoverMil))
            if (showDrift) {
                if (showMoa) add("%.1f".format(row.driftMoa))
                if (showMil) add("%.2f".format(row.driftMil))
            }
            add(if (metricMode) "%.0f".format(row.velocityFps * 0.3048) else "%.0f".format(row.velocityFps))
            if (showEnergy) add(if (metricMode) "%.0f".format(row.energyFtLb * 1.35582) else "%.0f".format(row.energyFtLb))
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
    } catch (_: Exception) { false }
}

/** Format a Double as a clean integer string when whole, else trim trailing zeros. */
private fun formatNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString()
    else "%g".format(d).trimEnd('0').trimEnd('.')

// ---- Session persistence ----

data class SessionData(
    val name: String,
    val muzzleVel: String,
    val bc: String,
    val bulletWeight: String,
    val sightHeight: String,
    val vitalZone: String,
    val dragModel: String,
    val presetName: String?,
    val altitude: String,
    val temperature: String,
    val humidity: String,
    val windSpeed: String,
    val tableStart: String,
    val tableEnd: String,
    val tableStep: String,
    val dopeTitle: String,
    val reticleName: String?
)

private fun SessionData.toJson(): org.json.JSONObject = org.json.JSONObject().apply {
    put("name",        name)
    put("muzzleVel",   muzzleVel)
    put("bc",          bc)
    put("bulletWeight",bulletWeight)
    put("sightHeight", sightHeight)
    put("vitalZone",   vitalZone)
    put("dragModel",   dragModel)
    putOpt("presetName",  presetName)
    put("altitude",    altitude)
    put("temperature", temperature)
    put("humidity",    humidity)
    put("windSpeed",   windSpeed)
    put("tableStart",  tableStart)
    put("tableEnd",    tableEnd)
    put("tableStep",   tableStep)
    put("dopeTitle",   dopeTitle)
    putOpt("reticleName", reticleName)
}

private fun org.json.JSONObject.toSessionData() = SessionData(
    name         = getString("name"),
    muzzleVel    = getString("muzzleVel"),
    bc           = getString("bc"),
    bulletWeight = getString("bulletWeight"),
    sightHeight  = getString("sightHeight"),
    vitalZone    = getString("vitalZone"),
    dragModel    = getString("dragModel"),
    presetName   = optString("presetName").ifEmpty { null },
    altitude     = getString("altitude"),
    temperature  = getString("temperature"),
    humidity     = getString("humidity"),
    windSpeed    = getString("windSpeed"),
    tableStart   = getString("tableStart"),
    tableEnd     = getString("tableEnd"),
    tableStep    = getString("tableStep"),
    dopeTitle    = getString("dopeTitle"),
    reticleName  = optString("reticleName").ifEmpty { null }
)

private const val PREFS_NAME = "mpbr_sessions"

private fun persistSessions(context: android.content.Context, sessions: List<SessionData>) {
    val arr = org.json.JSONArray()
    sessions.forEach { arr.put(it.toJson()) }
    context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        .edit { putString("sessions", arr.toString()) }
}

private fun loadSessions(context: android.content.Context): List<SessionData> {
    val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val json  = prefs.getString("sessions", "[]") ?: "[]"
    val arr   = org.json.JSONArray(json)
    return (0 until arr.length()).map { arr.getJSONObject(it).toSessionData() }
}

private fun saveSession(context: android.content.Context, session: SessionData) {
    val sessions = loadSessions(context).toMutableList()
    val idx = sessions.indexOfFirst { it.name == session.name }
    if (idx >= 0) sessions[idx] = session else sessions.add(session)
    persistSessions(context, sessions)
}

private fun deleteSession(context: android.content.Context, name: String) {
    persistSessions(context, loadSessions(context).filter { it.name != name })
}

private fun renameSession(context: android.content.Context, oldName: String, newName: String) {
    val sessions = loadSessions(context).toMutableList()
    val idx = sessions.indexOfFirst { it.name == oldName }
    if (idx >= 0) sessions[idx] = sessions[idx].copy(name = newName)
    persistSessions(context, sessions)
}

private val gunshotPlaying = java.util.concurrent.atomic.AtomicBoolean(false)

private fun playGunshot() {
    if (!gunshotPlaying.compareAndSet(false, true)) return
    Thread {
        val sampleRate = 44100
        val numSamples = sampleRate / 2          // 500 ms
        val buf = ShortArray(numSamples)
        val rng = java.util.Random()
        for (i in buf.indices) {
            val t     = i.toDouble() / sampleRate
            // sharp crack: white noise with fast decay
            val crack = rng.nextGaussian() * exp(-t * 40.0)
            // low-frequency boom underneath
            val boom  = sin(2 * PI * 65.0 * t) * exp(-t * 10.0) * 0.55
            val s     = ((crack + boom) * Short.MAX_VALUE * 0.85)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buf[i] = s.toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        @Suppress("DEPRECATION")
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, buf.size * 2), AudioTrack.MODE_STATIC
        )
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep(600)
        track.stop()
        track.release()
        gunshotPlaying.set(false)
    }.start()
}

private fun buildSessionName(presetName: String?, date: Date): String {
    val datePart = SimpleDateFormat("MM/dd", Locale.US).format(date)
    return "${presetName ?: "Custom"} — $datePart"
}
