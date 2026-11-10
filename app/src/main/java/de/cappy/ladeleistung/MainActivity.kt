@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.cappy.ladeleistung

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

enum class Screen { MAIN, SETTINGS, HELP, INFO, STANDBY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs.get(this)
        handleIntent(intent)
        ChargeWatchService.sync(this)
        setContent { App(prefs) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Automatik-Pause gilt nur bis zum nächsten Öffnen der App
        if (Prefs.get(this).autoPaused) ChargeWatchService.setPaused(this, false)
    }

    override fun onResume() {
        super.onResume()
        AppState.resumeTick.intValue++
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_AUTOSTART, false) == true) {
            AppState.autoLaunched.value = true
            i.removeExtra(EXTRA_AUTOSTART)
        }
    }

    companion object { const val EXTRA_AUTOSTART = "autostart" }
}

@Composable
fun App(prefs: Prefs) {
    val live = rememberLive(prefs)
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    val activity = LocalContext.current as Activity
    val autoLaunched = AppState.autoLaunched.value
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seenCharging by remember { mutableStateOf(false) }

    // Per Ladekabel gestartet -> direkt in den Standby
    LaunchedEffect(autoLaunched) { if (autoLaunched) screen = Screen.STANDBY }

    // Kabel ab nach Auto-Start -> App schließen, Sperrbildschirm kommt zurück
    LaunchedEffect(live.reading.charging) {
        if (live.reading.charging) seenCharging = true
        else if (seenCharging && AppState.autoLaunched.value) {
            AppState.autoLaunched.value = false
            activity.finish()
        }
    }

    // Beginn des Standbys merken (Schnelloption 45 s)
    LaunchedEffect(screen) {
        if (screen == Screen.STANDBY) AppState.standbySince.longValue = System.currentTimeMillis()
    }

    // "Display aus": Standby für diesen Ladevorgang beenden, Handy normal schlafen lassen
    var askAdmin by remember { mutableStateOf(false) }
    var pendingOff by remember { mutableStateOf(false) }
    val doDisplayOff: () -> Unit = {
        prefs.pause(live.reading.levelPct)
        val wasAuto = AppState.autoLaunched.value
        AppState.autoLaunched.value = false
        screen = Screen.MAIN
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Sleep.lockNow(activity)
        if (wasAuto) activity.finish() else activity.moveTaskToBack(true)
    }
    // Beim ersten Mal nachfragen, ob "sofort ausschalten" eingerichtet werden soll
    val displayOff: () -> Unit = {
        if (!Sleep.canLockNow(activity) && !prefs.adminAsked) askAdmin = true
        else doDisplayOff()
    }
    // Zurück aus dem System-Dialog: jetzt ausschalten (sofort, falls erlaubt, sonst per Timeout)
    val resumeTick = AppState.resumeTick.intValue
    LaunchedEffect(resumeTick) {
        if (pendingOff) { pendingOff = false; doDisplayOff() }
    }
    if (askAdmin) {
        AlertDialog(
            onDismissRequest = { askAdmin = false },
            containerColor = Color(0xFF0E2A1C),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD6EFE1),
            title = { Text("Display sofort ausschalten?") },
            text = {
                Text("Damit „Display aus“ wie die Seitentaste sofort abschaltet, braucht Charge Monitor " +
                    "einmalig das Recht „Bildschirm sperren“ (Geräteadministrator). Mehr darf die App damit nicht.\n\n" +
                    "Ohne das Recht geht das Display erst nach dem normalen Bildschirm-Timeout aus.\n\n" +
                    "Vor dem Deinstallieren in den Einstellungen wieder abschalten.")
            },
            confirmButton = {
                TextButton(onClick = {
                    askAdmin = false
                    prefs.adminAsked = true
                    pendingOff = true
                    Sleep.requestAdmin(activity)
                }) { Text("Einrichten", color = Neon) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askAdmin = false
                    prefs.adminAsked = true
                    doDisplayOff()
                }) { Text("Nein, Timeout reicht", color = TextDim) }
            }
        )
    }

    // Auto-Standby: beim Laden nach 60 s ohne Bedienung (nicht, wenn pausiert)
    // (nicht im Querformat: dort wird bewusst der Verlauf angesehen)
    LaunchedEffect(screen, lastTouch, live.reading.charging, prefs.autoStandby, prefs.paused, landscape) {
        if (screen == Screen.MAIN && live.reading.charging && prefs.autoStandby && !prefs.paused && !landscape) {
            kotlinx.coroutines.delay(60_000)
            screen = Screen.STANDBY
        }
    }

    // Bildschirm an / Helligkeit / Vollbild je nach Seite
    DisposableEffect(screen, prefs.keepScreenOn, autoLaunched, landscape) {
        val w = activity.window
        val standby = screen == Screen.STANDBY
        // Standby immer hochkant, sonst folgt die App der Drehung (respektiert die Drehsperre)
        activity.requestedOrientation =
            if (standby) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_USER
        val fullscreen = standby || (landscape && screen == Screen.MAIN)
        // Nur der automatisch gestartete Standby darf über dem Sperrbildschirm liegen
        val overLock = standby && autoLaunched
        activity.setShowWhenLocked(overLock)
        activity.setTurnScreenOn(overLock)
        if (standby || prefs.keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Standby-Helligkeit setzt StandbyDisplay selbst (inkl. Nachtdimmung)
        if (!standby) w.attributes = w.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        val ic = WindowCompat.getInsetsController(w, w.decorView)
        if (fullscreen) {
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(WindowInsetsCompat.Type.systemBars())
        } else ic.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }

    BackHandler(enabled = screen != Screen.MAIN) { AppState.autoLaunched.value = false; screen = Screen.MAIN }

    // Update-Prüfung höchstens einmal pro Tag beim Start
    val ctxU = LocalContext.current
    LaunchedEffect(Unit) { Update.load(ctxU); if (Update.due()) Update.check(ctxU) }
    UpdateDialog()

    if (!prefs.disclaimerAccepted) {
        DisclaimerScreen { prefs.disclaimerAccepted = true }
        return
    }

    AnimatedContent(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch = System.currentTimeMillis()
                }
            }
        },
        targetState = screen,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "screen"
    ) { s ->
        when (s) {
            Screen.MAIN ->
                if (landscape) FullChart(live, prefs)
                else MainScreen(live, prefs, displayOff) { screen = it }
            Screen.SETTINGS -> SettingsScreen(prefs, live, { screen = Screen.MAIN }, { screen = Screen.STANDBY })
            Screen.HELP -> HelpScreen { screen = Screen.MAIN }
            Screen.INFO -> InfoScreen { screen = Screen.MAIN }
            Screen.STANDBY -> StandbyDisplay(
                live, prefs,
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { AppState.autoLaunched.value = false; screen = Screen.MAIN },
                onBrightness = { b ->
                    activity.window.attributes = activity.window.attributes.apply { screenBrightness = b }
                },
                onDisplayOff = displayOff
            )
        }
    }
}

@Composable
fun MainScreen(live: LiveState, prefs: Prefs, displayOff: () -> Unit, go: (Screen) -> Unit) {
    val r = live.reading
    var menu by remember { mutableStateOf(false) }
    val scaleMax = prefs.scaleFor(r, live.peak)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Kopfzeile mit Menü
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("CHARGE MONITOR", color = TextDim, fontSize = 14.sp, letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Menü", tint = Mint)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                    containerColor = Color(0xFF0E2A1C)) {
                    listOf(
                        "Standby-Anzeige" to Screen.STANDBY,
                        "Einstellungen" to Screen.SETTINGS,
                        "Hilfe" to Screen.HELP,
                        "Info & Disclaimer" to Screen.INFO
                    ).forEach { (t, s) ->
                        DropdownMenuItem(
                            text = { Text(t, color = Color.White) },
                            onClick = { menu = false; go(s) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SourceChip(r)
        // Hinweise als kleine Chips direkt unter der Ladequelle (nehmen unten keinen Platz weg)
        val ctx = LocalContext.current
        val notes = buildList<Triple<String, Color, () -> Unit>> {
            if (Update.available(ctx)) add(Triple("⬆ Version ${Update.latest} verfügbar", Neon) { Update.showDialog = true })
            if (prefs.autoStart && prefs.autoPaused)
                add(Triple("Automatik pausiert · Fortsetzen ›", Warn) { ChargeWatchService.setPaused(ctx, false) })
            if (prefs.paused && r.charging) add(Triple("Standby pausiert · Fortsetzen ›", Mint) { prefs.unpause() })
        }
        notes.forEach { (t, c, a) -> Spacer(Modifier.height(8.dp)); NoticeChip(t, c, a) }
        Spacer(Modifier.height(12.dp))

        // Mitte: Tacho passt sich an die freie Höhe an; reicht sie trotzdem nicht, wird gescrollt
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fixed = 334.dp   // Kacheln, Verlauf und Abstände
            val gauge = (maxHeight - fixed).value.toInt().coerceIn(150, 270)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))   // Platz für den Leuchtrand oben
                Gauge(r, scaleMax, sizeDp = gauge, showLabels = prefs.scaleLabels)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Spannung", fmt("%.2f V", r.voltageMv / 1000f), Modifier.weight(1f))
                    Stat("Strom", if (r.supported) fmt("%.2f A", r.currentA) else "n. v.", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Akku", "${r.levelPct} %", Modifier.weight(1f))
                    Stat("Temperatur", fmt("%.1f °C", r.tempC), Modifier.weight(1f), warn = r.tempC >= prefs.tempWarn)
                }
                Spacer(Modifier.height(12.dp))
                Sparkline(live)
                Spacer(Modifier.height(12.dp))
            }
        }

        // Unten fest: die beiden Knöpfe
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.weight(1f)) { PillButton("Standby") { prefs.unpause(); go(Screen.STANDBY) } }
            Box(Modifier.weight(1f)) { PillButton("Display aus", onClick = displayOff) }
        }
    }
}
