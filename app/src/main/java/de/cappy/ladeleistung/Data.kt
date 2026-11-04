package de.cappy.ladeleistung

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs

// ---------- Messwerte ----------
data class Reading(
    val plugged: Int = 0,          // 0 = nicht eingesteckt, 1 AC, 2 USB, 4 Qi, 8 Dock
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
    val voltageMv: Int = 0,
    val currentA: Float = 0f,      // Betrag in Ampere
    val rawCurrent: Int = 0,       // Rohwert von BATTERY_PROPERTY_CURRENT_NOW
    val levelPct: Int = 0,
    val tempC: Float = 0f,
    val supported: Boolean = true
) {
    val charging get() = plugged != 0
    val watts get() = voltageMv / 1000f * currentA
    val full get() = status == BatteryManager.BATTERY_STATUS_FULL
    val sourceText get() = when {
        !charging -> "Nicht angeschlossen"
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Qi · kabellos"
        plugged == BatteryManager.BATTERY_PLUGGED_AC -> "Kabel · Netzteil"
        plugged == BatteryManager.BATTERY_PLUGGED_USB -> "Kabel · USB"
        plugged == BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
        else -> "Lädt"
    }
    val wireless get() = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
}

/** Einheit des CURRENT_NOW-Rohwerts. Laut Android-Doku µA, manche Hersteller liefern mA. */
enum class UnitMode(val label: String) { AUTO("Auto"), MICRO("µA"), MILLI("mA") }

/** Liest Akkudaten direkt aus dem System (Sticky-Broadcast + BatteryManager). */
class BatteryReader(private val ctx: Context) {
    private val bm = ctx.getSystemService(BatteryManager::class.java)
    private var maxAbsRaw = 0

    fun read(unit: UnitMode): Reading {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val supported = raw != Int.MIN_VALUE
        if (supported) maxAbsRaw = maxOf(maxAbsRaw, abs(raw))
        // Auto: Werte > 50.000 können nur µA sein. Bleibt das Maximum darunter -> mA.
        val isMicro = when (unit) {
            UnitMode.MICRO -> true
            UnitMode.MILLI -> false
            UnitMode.AUTO -> maxAbsRaw > 50_000 || maxAbsRaw == 0
        }
        val amps = if (!supported) 0f else abs(raw) / if (isMicro) 1_000_000f else 1_000f
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return Reading(
            plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 1) ?: 1,
            voltageMv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            currentA = amps,
            rawCurrent = raw,
            levelPct = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            supported = supported
        )
    }
}

/** Live-Zustand für die Oberfläche (aktueller Wert, 60-s-Verlauf, Spitze). */
class LiveState {
    var reading by mutableStateOf(Reading())
    val history = mutableStateListOf<Float>()
    var peak by mutableFloatStateOf(0f)
    var capacity by mutableIntStateOf(120)
}

@Composable
fun rememberLive(prefs: Prefs): LiveState {
    val ctx = LocalContext.current.applicationContext
    val reader = remember { BatteryReader(ctx) }
    val s = remember { LiveState() }
    val unit = prefs.unit
    val interval = prefs.intervalMs
    LaunchedEffect(unit, interval) {
        s.capacity = 60_000 / interval
        while (true) {
            val r = reader.read(unit)
            if (r.charging != s.reading.charging) { s.history.clear(); s.peak = 0f }
            s.reading = r
            prefs.checkPause(r)
            if (r.charging) {
                s.history.add(r.watts)
                while (s.history.size > s.capacity) s.history.removeAt(0)
                s.peak = maxOf(s.peak, r.watts)
            }
            delay(interval.toLong())
        }
    }
    return s
}

// ---------- Einstellungen (SharedPreferences, als Compose-State) ----------
class Prefs private constructor(ctx: Context) {
    private val sp = ctx.getSharedPreferences("charge_monitor", Context.MODE_PRIVATE)

    private val _unit = mutableIntStateOf(sp.getInt("unit", 0))
    var unit: UnitMode
        get() = UnitMode.entries[_unit.intValue]
        set(v) { _unit.intValue = v.ordinal; sp.edit().putInt("unit", v.ordinal).apply() }

    private fun intPref(key: String, def: Int) = mutableIntStateOf(sp.getInt(key, def))
    private fun boolPref(key: String, def: Boolean) = mutableStateOf(sp.getBoolean(key, def))
    private fun putI(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun putB(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    private val _interval = intPref("interval", 500)
    var intervalMs: Int get() = _interval.intValue; set(v) { _interval.intValue = v; putI("interval", v) }

    private val _wiredMax = intPref("wiredMax", 50)
    var wiredMax: Int get() = _wiredMax.intValue; set(v) { _wiredMax.intValue = v; putI("wiredMax", v) }

    private val _qiMax = intPref("qiMax", 20)
    var qiMax: Int get() = _qiMax.intValue; set(v) { _qiMax.intValue = v; putI("qiMax", v) }

    private val _tempWarn = intPref("tempWarn", 40)
    var tempWarn: Int get() = _tempWarn.intValue; set(v) { _tempWarn.intValue = v; putI("tempWarn", v) }

    private val _keepOn = boolPref("keepOn", true)
    var keepScreenOn: Boolean get() = _keepOn.value; set(v) { _keepOn.value = v; putB("keepOn", v) }

    private val _dim = intPref("standbyDim2", 10)
    var standbyBrightness: Int get() = _dim.intValue; set(v) { _dim.intValue = v; putI("standbyDim2", v) }

    private val _night = boolPref("nightMode", true)
    var nightMode: Boolean get() = _night.value; set(v) { _night.value = v; putB("nightMode", v) }

    private val _nightDim = intPref("nightDim", 1)
    var nightBrightness: Int get() = _nightDim.intValue; set(v) { _nightDim.intValue = v; putI("nightDim", v) }

    private val _adminAsked = boolPref("adminAsked", false)
    var adminAsked: Boolean get() = _adminAsked.value; set(v) { _adminAsked.value = v; putB("adminAsked", v) }

    private val _quickAlways = boolPref("quickAlways", false)
    var quickAlways: Boolean get() = _quickAlways.value; set(v) { _quickAlways.value = v; putB("quickAlways", v) }

    // "Standby für diesen Ladevorgang aus"
    private val _pausedAt = mutableStateOf(sp.getLong("pausedAt", 0L))
    private var pausedLevel = sp.getInt("pausedLevel", 0)
    val paused: Boolean get() = _pausedAt.value != 0L
    fun pause(level: Int) {
        _pausedAt.value = System.currentTimeMillis(); pausedLevel = level
        sp.edit().putLong("pausedAt", _pausedAt.value).putInt("pausedLevel", level).apply()
    }
    fun unpause() { if (paused) { _pausedAt.value = 0L; sp.edit().putLong("pausedAt", 0L).apply() } }
    /** Pause endet: Kabel ab, Akku zwischendurch gesunken (= neuer Ladevorgang) oder nach 12 h. */
    fun checkPause(r: Reading) {
        if (!paused) return
        if (!r.charging || r.levelPct < pausedLevel ||
            System.currentTimeMillis() - _pausedAt.value > 12 * 3600_000L) unpause()
    }

    /** Nachtzeit 22–6 Uhr */
    fun isNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return nightMode && (h >= 22 || h < 6)
    }
    fun effectiveStandbyBrightness(): Float =
        (if (isNight()) nightBrightness else standbyBrightness).coerceIn(1, 100) / 100f

    private val _shift = boolPref("pixelShift", true)
    var pixelShift: Boolean get() = _shift.value; set(v) { _shift.value = v; putB("pixelShift", v) }

    private val _clock = boolPref("standbyClock", true)
    var standbyClock: Boolean get() = _clock.value; set(v) { _clock.value = v; putB("standbyClock", v) }

    private val _auto = boolPref("autoStart", false)
    var autoStart: Boolean get() = _auto.value; set(v) { _auto.value = v; putB("autoStart", v) }

    private val _autoStandby = boolPref("autoStandby", true)
    var autoStandby: Boolean get() = _autoStandby.value; set(v) { _autoStandby.value = v; putB("autoStandby", v) }

    private val _accepted = boolPref("disclaimer", false)
    var disclaimerAccepted: Boolean get() = _accepted.value; set(v) { _accepted.value = v; putB("disclaimer", v) }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx.applicationContext).also { inst = it } }
    }
}

/** App-weiter Zustand, den die Activity von außen setzt. */
object AppState {
    /** zählt hoch bei jedem onResume -> Berechtigungsstatus neu prüfen */
    val resumeTick = mutableIntStateOf(0)
    /** true, wenn die App per Ladekabel automatisch gestartet wurde */
    val autoLaunched = mutableStateOf(false)
    /** Zeitpunkt, an dem der aktuelle Standby begonnen hat (für die Schnelloption) */
    val standbySince = mutableLongStateOf(0L)
}
