package com.twslatencylab

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
class MeasurementViewModel : ViewModel() {

    var phase    by mutableStateOf(Phase.IDLE)
    var step     by mutableStateOf(0)
    var heard    by mutableStateOf(0)
    var hint     by mutableStateOf("")
    var errorMsg by mutableStateOf("")
    var baseline by mutableStateOf<Double?>(null)
    var freshBaseline by mutableStateOf<Double?>(null)
    var bsRaw    by mutableStateOf(listOf<Double>())
    var result   by mutableStateOf<MeasurementResult?>(null)
    var history  by mutableStateOf(listOf<RunRecord>())

    val log = mutableStateListOf<LogEntry>()

    var gameComp by mutableStateOf(70.0)
    val brainAdj = 15.0

    private var measurer: LatencyMeasurer? = null
    private var store: RunStore? = null
    private var phase2Route: String = ""

    fun init(context: Context) {
        if (measurer == null) {
            val app = context.applicationContext
            measurer = LatencyMeasurer(app)
            store = RunStore(app)
            history = store?.load().orEmpty()
        }
    }

    private fun push(text: String, type: LogType = LogType.INFO) {
        viewModelScope.launch(Dispatchers.Main) {
            if (log.size > 100) log.removeAt(0)
            log.add(LogEntry(text, type))
        }
    }

    fun startBaseline() {
        val m = measurer ?: return
        log.clear()
        phase = Phase.BASELINE
        step  = 0
        heard = 0
        hint  = ""
        freshBaseline = null
        push("━━━ PHASE 1 · DEVICE BASELINE ━━━", LogType.HEADER)

        viewModelScope.launch(Dispatchers.Default) {
            val raw = mutableListOf<Double>()
            repeat(LatencyMeasurer.NUM_TRIALS) { i ->
                step = i + 1
                val d = m.runTrial(onLog = { msg -> push(msg) }, forceSpeaker = true)
                if (d != null) {
                    raw.add(d)
                    heard = raw.size
                    hint = ""
                    delay(300)
                } else {
                    hint = "No click heard. Uncover the phone microphone. This baseline is still running."
                    delay(2500)
                }
                yield()
            }

            if (raw.isEmpty()) {
                errorMsg = "No signal detected during baseline."
                phase = Phase.ERROR
                return@launch
            }

            val measured = LatencyMeasurer.median(raw)
            freshBaseline = measured
            baseline = measured
            bsRaw    = raw.toList()
            phase = Phase.READY_TWS
        }
    }

    fun recentBaselineMs(): List<Double> = history.take(5).map { it.speakerMs }

    fun useFreshBaseline() {
        baseline = freshBaseline ?: return
    }

    fun useRecentAverage() {
        val recent = recentBaselineMs()
        if (recent.isEmpty()) return
        baseline = recent.average() / 1000.0
        bsRaw = recent.map { it / 1000.0 }
        if (phase == Phase.IDLE) {
            log.clear()
            hint = ""
            phase = Phase.READY_TWS
        }
    }

    fun startTWS() {
        val m = measurer ?: return
        phase = Phase.TWS
        step  = 0
        heard = 0
        hint  = ""
        phase2Route = ""
        push("━━━ PHASE 2 · HEADSET ━━━", LogType.HEADER)

        viewModelScope.launch(Dispatchers.Default) {
            val raw = mutableListOf<Double>()
            repeat(LatencyMeasurer.NUM_TRIALS) { i ->
                step = i + 1
                val d = m.runTrial(onLog = { msg ->
                    push(msg)
                    if (msg.contains("[route]")) phase2Route = msg.substringAfter("[route]").trim()
                }, forceSpeaker = false)
                if (d != null) {
                    raw.add(d)
                    heard = raw.size
                    hint = ""
                    delay(300)
                } else {
                    hint = "No click heard. Move an earcup onto the phone microphone. The speaker baseline is kept."
                    delay(2500)
                }
                yield()
            }

            if (raw.isEmpty()) {
                hint = "No click reached the phone microphone. Move an earcup against the bottom of the phone, then run the headset test again. The speaker baseline is kept."
                phase = Phase.READY_TWS
                return@launch
            }

            val bsMed  = baseline!!
            val twsMed = LatencyMeasurer.median(raw)

            val bsMedMs  = bsMed * 1000.0
            val twsMedMs = twsMed * 1000.0

            // Net is the measured difference. Nothing else is subtracted:
            // the old A2DP/OS constants were larger than this difference and
            // the screen clamped the result to 0.
            val latMs = twsMedMs - bsMedMs
            val nets  = raw.map { (it * 1000.0) - bsMedMs }

            val p25 = LatencyMeasurer.percentile(nets, 25.0)
            val p75 = LatencyMeasurer.percentile(nets, 75.0)

            hint = ""
            push(
                "Net ${"%.1f".format(latMs)} ms  (phase2 ${"%.1f".format(twsMedMs)} − phase1 ${"%.1f".format(bsMedMs)})",
                LogType.RESULT
            )

            history = store?.add(
                RunRecord(
                    atMs = System.currentTimeMillis(),
                    netMs = latMs,
                    headsetMs = twsMedMs,
                    speakerMs = bsMedMs,
                    trials = raw.size,
                    loMs = p25,
                    hiMs = p75,
                    route = phase2Route
                )
            ) ?: history

            result = MeasurementResult(
                latMs               = latMs,
                a2dpBufferMs        = 0.0,
                systemCalibrationMs = 0.0,
                correctedLatMs      = latMs,
                loMs                = p25,
                hiMs                = p75,
                twsMedianMs         = twsMedMs,
                baselineMs          = bsMedMs, 
                validTrials         = raw.size,
                netDelaysMs         = nets,
                bsDelaysMs          = bsRaw.map { it * 1000.0 },
                gameCompMs          = gameComp,
                brainAdjMs          = brainAdj,
                perceivedLatencyMs  = latMs
            )
            phase = Phase.DONE
        }
    }

    fun reset() {
        phase = Phase.IDLE
        step = 0
        heard = 0
        hint = ""
        baseline = null
        freshBaseline = null
        bsRaw = emptyList()
        result = null
        errorMsg = ""
        log.clear()
    }
}
