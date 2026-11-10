package de.cappy.ladeleistung

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val GITHUB_URL = "https://github.com/cappy0815/ChargeMonitor"
const val PROJECT_URL = "https://cappy0815.github.io/#charge-monitor"

/** Versionsgeschichte, neueste zuerst */
val CHANGELOG = listOf(
    "1.7" to listOf(
        "Update-Prüfung über die Projektseite: täglich automatisch (abschaltbar) oder mit „Jetzt prüfen“",
        "„⬆ Update“-Knopf oben, wenn eine neue Version bereitliegt",
        "Info mit Versionsgeschichte, GitHub-Link und Projektseite"
    ),
    "1.6" to listOf(
        "Querformat: Verlauf groß im Vollbild mit Achsen, Raster und Durchschnittslinie",
        "Automatik-Pause endet beim nächsten Öffnen der App und beim Neustart",
        "Pause-Hinweise auf der Hauptseite als echte Schaltflächen"
    ),
    "1.5" to listOf(
        "„Automatik pausieren“ in der Benachrichtigung und per langem Tippen aufs App-Symbol",
        "Link zur Projektseite in Hilfe und Info"
    ),
    "1.4" to listOf(
        "Uhr im Standby: aus, digital oder analog (optional mit Sekundenzeiger)",
        "Ring-Skala fest per Schieberegler oder automatisch, Skalenstriche und Beschriftung",
        "Ringgröße im Standby einstellbar"
    ),
    "1.3" to listOf(
        "„Display aus“: Standby für den Ladevorgang abschalten, auf Wunsch sofort wie mit der Seitentaste",
        "Nachtmodus 22–6 Uhr mit eigener Helligkeit"
    ),
    "1.2" to listOf("Beim Einstecken automatisch öffnen, Auto-Standby"),
    "1.1" to listOf("Name „Charge Monitor“, Standby-Anzeige, Bildschirmschoner, Einstellungen, Hilfe, Disclaimer"),
    "1.0" to listOf("Erste Version: Ladeleistung live für Kabel, USB und Qi")
)

fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Exception) { "?" }

/** "1.10" > "1.9", "1.4.1" > "1.4" */
fun newerThan(a: String, b: String): Boolean {
    val x = a.trimStart('v', 'V').split('.', '-').map { it.trim().toIntOrNull() ?: 0 }
    val y = b.trimStart('v', 'V').split('.', '-').map { it.trim().toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
        if (p != q) return p > q
    }
    return false
}

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

/**
 * Update-Prüfung wie bei Claude Limits, Soundboard, Player und SpyMon:
 *  1. Versionsdatei auf der Projektseite  https://cappy0815.github.io/downloads/chargemonitor.json
 *     {"version":"1.7","page":"…","download":"…apk","sha256":"…"}
 *  2. Rückfall: neuestes GitHub-Release von cappy0815/ChargeMonitor
 * Es wird nichts automatisch installiert – die App zeigt nur an und öffnet den Download im Browser.
 * Gesendet wird nur eine normale Abfrage (Kennung "ChargeMonitor/<Version>"), keine Daten aus der App.
 */
object Update {
    const val VERSION_URL = "https://cappy0815.github.io/downloads/chargemonitor.json"
    const val RELEASE_API = "https://api.github.com/repos/cappy0815/ChargeMonitor/releases/latest"
    private const val DAY_MS = 24L * 3600 * 1000

    var busy by mutableStateOf(false); private set
    var latest by mutableStateOf(""); private set
    var lastCheck by mutableLongStateOf(0L); private set
    var failed by mutableStateOf(false); private set
    var auto by mutableStateOf(true); private set
    var showDialog by mutableStateOf(false)
    private var download = ""
    private var page = PROJECT_URL
    private var loaded = false

    private fun sp(c: Context) = c.getSharedPreferences("update", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = sp(c)
        auto = p.getBoolean("auto", true); lastCheck = p.getLong("last", 0)
        latest = p.getString("latest", "") ?: ""; download = p.getString("download", "") ?: ""
        page = p.getString("page", PROJECT_URL) ?: PROJECT_URL
    }

    fun setAuto(c: Context, on: Boolean) { auto = on; sp(c).edit().putBoolean("auto", on).apply() }

    /** gibt es laut letzter Prüfung eine neuere Version? */
    fun available(c: Context): Boolean = latest.isNotEmpty() && newerThan(latest, appVersion(c))

    /** automatische Prüfung fällig (eingeschaltet, letzte Prüfung älter als 24 h) */
    fun due(): Boolean = auto && System.currentTimeMillis() - lastCheck > DAY_MS

    suspend fun check(c: Context): Boolean {
        load(c)
        if (busy) return false
        busy = true; failed = false
        val ua = "ChargeMonitor/" + appVersion(c)
        val r: Triple<String, String, String>? = withContext(Dispatchers.IO) {
            try {
                val o = JSONObject(get(VERSION_URL, ua))
                val v = o.optString("version").trim().trimStart('v', 'V')
                if (v.isNotEmpty()) return@withContext Triple(v, o.optString("download"), o.optString("page", PROJECT_URL))
            } catch (_: Exception) {}
            try {
                val o = JSONObject(get(RELEASE_API, ua))
                val v = o.optString("tag_name").trim().trimStart('v', 'V')
                val assets = o.optJSONArray("assets")
                val dl = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").lowercase().endsWith(".apk") }?.optString("browser_download_url") ?: ""
                if (v.isNotEmpty()) return@withContext Triple(v, dl, o.optString("html_url", GITHUB_URL))
            } catch (_: Exception) {}
            null
        }
        lastCheck = System.currentTimeMillis()
        val e = sp(c).edit().putLong("last", lastCheck)
        if (r != null) {
            latest = r.first; download = r.second; page = r.third
            e.putString("latest", latest).putString("download", download).putString("page", page)
        } else failed = true
        e.apply()
        busy = false
        return r != null
    }

    fun openDownload(c: Context) = openUrl(c, download.ifBlank { page })
    fun openPage(c: Context) = openUrl(c, page)

    fun lastText(): String =
        if (lastCheck == 0L) "noch nie" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(lastCheck))

    private fun get(url: String, ua: String): String {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 12000; h.readTimeout = 12000; h.useCaches = false
        h.setRequestProperty("User-Agent", ua)
        h.setRequestProperty("Accept", "application/vnd.github+json, application/json")
        try {
            if (h.responseCode != 200) throw Exception("HTTP ${h.responseCode}")
            val out = ByteArrayOutputStream()
            h.inputStream.use { inp ->
                val buf = ByteArray(8192); var total = 0
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0 || total >= (1 shl 20)) break
                    out.write(buf, 0, n); total += n
                }
            }
            return out.toString("UTF-8")
        } finally { h.disconnect() }
    }
}

/** Dialog „⬆ Update“ */
@Composable
fun UpdateDialog() {
    if (!Update.showDialog) return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { Update.showDialog = false },
        containerColor = Color(0xFF0E2A1C),
        title = { Text("⬆ Update", color = Color.White) },
        text = {
            Text("Version ${Update.latest} ist verfügbar (installiert: ${appVersion(ctx)}).\n\n" +
                "„Herunterladen“ öffnet die APK im Browser. Danach die Datei antippen und installieren – " +
                "Einstellungen bleiben erhalten.", color = Color(0xFFD6EFE1))
        },
        confirmButton = { TextButton(onClick = { Update.showDialog = false; Update.openDownload(ctx) }) { Text("Herunterladen", color = Neon) } },
        dismissButton = {
            Row {
                TextButton(onClick = { Update.showDialog = false; Update.openPage(ctx) }) { Text("Was ist neu?", color = TextDim) }
                TextButton(onClick = { Update.showDialog = false }) { Text("Später", color = TextDim) }
            }
        }
    )
}

/** Block für Einstellungen und Info: Status, automatische Prüfung, Jetzt prüfen */
@Composable
fun UpdateBlock() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Update.load(ctx) }
    val avail = Update.available(ctx)
    Text(
        when {
            Update.busy -> "Prüfe …"
            Update.failed -> "Update-Server nicht erreichbar – Internet prüfen"
            avail -> "Version ${Update.latest} ist verfügbar"
            Update.latest.isNotEmpty() -> "Aktuell (neueste Version ${Update.latest})"
            else -> "Noch nicht geprüft"
        } + "\nInstalliert: ${appVersion(ctx)} · zuletzt geprüft: ${Update.lastText()}",
        color = when { avail -> Neon; Update.failed -> Warn; else -> TextDim }, fontSize = 13.sp, lineHeight = 18.sp
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow("Täglich automatisch prüfen", "Beim Start höchstens einmal pro Tag; es wird nichts automatisch installiert",
        Update.auto) { Update.setAuto(ctx, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            PillButton(if (Update.busy) "Prüfe …" else "Jetzt prüfen", filled = !avail) {
                if (!Update.busy) scope.launch { Update.check(ctx) }
            }
        }
        if (avail) Box(Modifier.weight(1f)) { PillButton("Herunterladen", filled = true) { Update.openDownload(ctx) } }
    }
}
