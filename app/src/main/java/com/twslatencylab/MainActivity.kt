package com.twslatencylab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

// ── Native Font Setup ────────────────────────────────────────────────────────
val MONO = FontFamily.Monospace
val SANS = FontFamily.SansSerif

// ── Colors ───────────────────────────────────────────────────────────────────
val BG        = Color(0xFF09090F)
val CARD      = Color(0xFF0C0C1C)
val CARDBORD  = Color(0xFF181832)
val BLUE      = Color(0xFF7070FF)
val CYAN      = Color(0xFF40CCFF)
val GREEN     = Color(0xFF00FF9D)
val YELLOW    = Color(0xFFFFE566)
val ORANGE    = Color(0xFFFF9933)
val RED       = Color(0xFFFF4466)
val DIMTEXT   = Color(0xFF555577)

fun latColor(ms: Double) = when {
    ms < 80  -> GREEN
    ms < 150 -> YELLOW
    ms < 250 -> ORANGE
    else     -> RED
}

// ── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    TWSApp()
                }
            }
        }
    }
}

// ── Root composable ──────────────────────────────────────────────────────────
@Composable
fun TWSApp(vm: MeasurementViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.init(context) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C0C18))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◈ PRECISION AUDIO TOOL", fontFamily = SANS,
                    fontSize = 10.sp, letterSpacing = 3.sp, color = Color(0xFF33337A), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "TWS Latency Lab",
                    fontFamily = SANS,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BLUE 
                )
                Spacer(Modifier.height(4.dp))
                Text("2-phase calibrated Bluetooth delay measurement",
                    fontFamily = SANS, fontSize = 12.sp, color = Color(0xFF44447A))
            }
        }

        Divider(color = Color(0xFF16162A), thickness = 1.dp)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                vm.phase == Phase.ERROR -> {
                    Spacer(Modifier.height(16.dp))
                    TCard(borderColor = RED) {
                        AppText("⚠  Something went wrong", RED, 14.sp, FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        AppText(vm.errorMsg, Color(0xFFCC6677), 12.sp)
                        Spacer(Modifier.height(12.dp))
                        LiveLog(vm.log, maxHeight = 220)
                        Spacer(Modifier.height(16.dp))
                        AppButton("Try Again", RED) { vm.reset() }
                    }
                }

                !hasPermission -> {
                    Spacer(Modifier.height(16.dp))
                    InfoCard("🎙  Microphone required") {
                        AppText(
                            "This app plays a click and records when it arrives through " +
                            "the phone speaker, then through a wired or Bluetooth headset.",
                            Color(0xFF8888BB), 13.sp
                        )
                    }
                    AppButton("Allow Microphone & Continue", BLUE, primary = true) {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                vm.phase == Phase.IDLE -> {
                    Spacer(Modifier.height(16.dp))
                    InfoCard("📡  How it works") {
                        PhaseRow("1", "Device Baseline", BLUE,
                            "Measures raw device audio pipeline overhead.")
                        PhaseRow("2", "Headset Test", CYAN,
                            "Wired headset if one is plugged in, otherwise Bluetooth.")
                        PhaseRow("★", "Net Result", GREEN,
                            "Phase 2 minus Phase 1. The number is the measured difference.")
                    }
                    AppButton("▶  Start Phase 1 — Device Baseline", BLUE, primary = true) {
                        vm.startBaseline()
                    }
                }

                vm.phase == Phase.BASELINE || vm.phase == Phase.TWS -> {
                    Spacer(Modifier.height(16.dp))
                    TCard {
                        val isBaseline = vm.phase == Phase.BASELINE
                        AppText(
                            if (isBaseline) "PHASE 1 · DEVICE BASELINE" else "PHASE 2 · TWS BLUETOOTH TEST",
                            Color(0xFF44449A), 10.sp, letterSpacing = 2.sp, weight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        AppText(
                            if (isBaseline) "🔊  Click playing through phone speaker" else "🎧  Click routing to the connected headset",
                            Color(0xFF9999DD), 14.sp
                        )
                        if (vm.hint.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            AppText(vm.hint, YELLOW, 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))

                        val progress = vm.step.toFloat() / LatencyMeasurer.NUM_TRIALS
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AppText("Trial ${vm.step} of ${LatencyMeasurer.NUM_TRIALS} · heard ${vm.heard}", Color(0xFF33336A), 11.sp)
                            AppText("${(progress * 100).toInt()}%", Color(0xFF33336A), 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (isBaseline) BLUE else GREEN,
                            trackColor = Color(0xFF141428)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LiveLog(vm.log)
                }

                vm.phase == Phase.READY_TWS -> {
                    Spacer(Modifier.height(16.dp))
                    val baselineVal = (vm.baseline ?: 0.0) * 1000
                    TCard(bg = Color(0xFF080E0C), borderColor = Color(0xFF0A2A1A)) {
                        AppText("BASELINE CAPTURED", Color(0xFF1A5535), 10.sp, weight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%.1f".format(baselineVal), fontFamily = SANS, 
                                fontSize = 52.sp, fontWeight = FontWeight.Black, color = GREEN)
                            Text("ms", fontFamily = SANS, fontSize = 18.sp, color = Color(0xFF00AA66),
                                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
                        }
                        
                        if (vm.hint.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            AppText(vm.hint, YELLOW, 13.sp)
                        }

                        if (baselineVal > 150.0) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠", color = YELLOW, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                AppText("Baseline is high enough that Phase 1 may have played through the earbuds.", YELLOW, 10.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    AppButton("▶  Start Phase 2 — Headset Test", GREEN, primary = true) { vm.startTWS() }
                }

                vm.phase == Phase.DONE && vm.result != null -> {
                    val r = vm.result!!
                    val col = latColor(r.latMs)

                    Spacer(Modifier.height(16.dp))

                    TCard(bg = Color(0xFF090910), borderColor = col.copy(alpha = 0.3f)) {
                        AppText("MEASURED NET LATENCY", Color(0xFF555588), 11.sp, letterSpacing = 3.sp, weight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%.1f".format(r.latMs), fontFamily = SANS,
                                fontSize = 72.sp, fontWeight = FontWeight.Black, color = col)
                            Text("ms", fontFamily = SANS, fontSize = 22.sp, color = col.copy(0.6f),
                                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(DIMTEXT, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            AppText("Phase 2 minus Phase 1. Nothing else is subtracted.", DIMTEXT, 12.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TrialSpreadCard(r)

                    Spacer(Modifier.height(12.dp))

                    InfoCard("LATENCY BREAKDOWN") {
                        ResultRow("Phase 2 (headset)", "${"%.1f".format(r.twsMedianMs)} ms")
                        ResultRow("Phase 1 (phone speaker)", "− ${"%.1f".format(r.baselineMs)} ms", color = ORANGE, dim = true)
                        Divider(color = Color(0xFF181830), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        ResultRow("Measured net", "= ${"%.1f".format(r.latMs)} ms", color = GREEN, bold = true)
                    }

                    LiveLog(vm.log, maxHeight = 120)
                    AppButton("Run New Test", BLUE, primary = true) { vm.reset() }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── UI Components ─────────────────────────────────────────────────────────────

@Composable
fun TrialSpreadCard(r: MeasurementResult) {
    val fullLo = r.netDelaysMs.minOrNull() ?: r.latMs
    val fullHi = r.netDelaysMs.maxOrNull() ?: r.latMs

    TCard(
        bg = Color(0xFF0A0A15),
        borderColor = Color(0xFF1F1F3D)
    ) {
        AppText("TRIAL SPREAD", CYAN, 11.sp, FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        AppText(
            "Middle half of the net delays, then the full range. ${r.validTrials} trials kept.",
            Color(0xFF7777AA), 11.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppText("${"%.1f".format(r.loMs)} – ${"%.1f".format(r.hiMs)} ms", GREEN, 16.sp, FontWeight.Bold, fontFamily = MONO)
            AppText("middle half", Color(0xFF8888AA), 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppText("${"%.1f".format(fullLo)} – ${"%.1f".format(fullHi)} ms", Color(0xFFAAAAEE), 14.sp, fontFamily = MONO)
            AppText("full range", Color(0xFF8888AA), 12.sp)
        }
    }
}

@Composable
fun AppText(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified, textAlign: TextAlign = TextAlign.Start, fontFamily: FontFamily = SANS, modifier: Modifier = Modifier) {
    Text(text = text, fontFamily = fontFamily, fontSize = size, fontWeight = weight, color = color, letterSpacing = letterSpacing, textAlign = textAlign, lineHeight = size * 1.4f, modifier = modifier)
}

@Composable
fun TCard(bg: Color = CARD, borderColor: Color = CARDBORD, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)).padding(18.dp), content = content)
}

@Composable
fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).background(CARD, RoundedCornerShape(12.dp)).padding(18.dp)) {
        AppText(title, Color(0xFF8888CC), 13.sp, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun AppButton(label: String, color: Color, primary: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (primary) color.copy(alpha = 0.1f) else Color.Transparent, contentColor = if (primary) color else Color(0xFF444466)), border = BorderStroke(1.dp, if (primary) color.copy(0.8f) else color.copy(0.3f))) {
        Text(label, fontFamily = SANS, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PhaseRow(n: String, name: String, color: Color, desc: String) {
    Row(modifier = Modifier.padding(bottom = 12.dp)) {
        Box(modifier = Modifier.size(24.dp).background(color.copy(0.15f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(n, fontFamily = SANS, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            AppText(name, Color(0xFFAAAAEE), 13.sp, FontWeight.Bold)
            AppText(desc, Color(0xFF666699), 12.sp)
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, color: Color = Color(0xFFAAAADD), dim: Boolean = false, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, if (dim) Color(0xFF444466) else Color(0xFF666688), 12.sp)
        AppText(value, if (dim) Color(0xFF555577) else color, 12.sp, weight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun LiveLog(log: List<LogEntry>, maxHeight: Int = 185) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.size - 1) }
    Box(modifier = Modifier.fillMaxWidth().height(maxHeight.dp).background(Color(0xFF06060E), RoundedCornerShape(10.dp)).padding(10.dp, 8.dp)) {
        LazyColumn(state = listState) {
            items(log) { entry ->
                val color = when (entry.type) {
                    LogType.RESULT -> GREEN
                    LogType.SUCCESS -> Color(0xFF5588FF)
                    LogType.HEADER -> Color(0xFF2A2A5A)
                    LogType.INFO -> Color(0xFF334455)
                }
                AppText(entry.text, color, 10.sp, fontFamily = MONO)
            }
        }
    }
}
