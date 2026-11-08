@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.cappy.ladeleistung

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- Grundgerüst für Unterseiten ----------
@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Mint)
            }
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            content = content
        )
    }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), color = Neon, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Para(text: String) {
    Text(text, color = Color(0xFFD6EFE1), fontSize = 15.sp, lineHeight = 22.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .border(1.dp, Neon.copy(alpha = .15f), RoundedCornerShape(18.dp)).padding(16.dp),
        content = content
    )
}

@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, color = Color.White, fontSize = 15.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (text, value) ->
            val on = value == selected
            Text(
                text, color = if (on) Color.Black else Mint, fontSize = 14.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Neon else Color.Transparent)
                    .border(1.dp, Neon.copy(alpha = if (on) 1f else .4f), RoundedCornerShape(50))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp)
            if (hint != null) Text(hint, color = TextDim, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = Neon,
                uncheckedThumbColor = TextDim, uncheckedTrackColor = Color(0x22FFFFFF),
                uncheckedBorderColor = TextDim.copy(alpha = .4f)
            )
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun PillButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (filled) Color.Black else Mint, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
            .background(if (filled) Neon else Color.Transparent)
            .border(1.dp, Neon.copy(alpha = if (filled) 1f else .5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

fun openDreamSettings(ctx: android.content.Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------- Einstellungen ----------
@Composable
fun SettingsScreen(prefs: Prefs, live: LiveState, onBack: () -> Unit, onStandby: () -> Unit) {
    val ctx = LocalContext.current
    var adminTick by remember { mutableIntStateOf(0) }
    val resume = AppState.resumeTick.intValue
    val adminOn = remember(resume, adminTick) { Sleep.canLockNow(ctx) }
    SubPage("Einstellungen", onBack) {
        Section("Messung")
        Panel {
            ChoiceRow("Einheit des Stromwerts",
                UnitMode.entries.map { it.label to it }, prefs.unit) { prefs.unit = it }
            ChoiceRow("Aktualisierung",
                listOf("0,25 s" to 250, "0,5 s" to 500, "1 s" to 1000), prefs.intervalMs) { prefs.intervalMs = it }
            // Zwei feste Spalten, damit lange Rohwerte nicht umbrechen
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Live-Rohwert", color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${live.reading.rawCurrent}", color = Color.White, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("entspricht", color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(fmt("%.3f A", live.reading.currentA), color = Mint, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            }
        }

        Section("Ring-Skala")
        Panel {
            ChoiceRow("Vollausschlag", listOf("Fest" to false, "Automatisch" to true), prefs.autoScale) { prefs.autoScale = it }
            if (prefs.autoScale) {
                Text("Die Skala richtet sich nach dem höchsten Wert des laufenden Ladevorgangs " +
                    "(Spitze + 20 %, gerundet auf 5, 10, 15 … 120 W). Sie wächst mit, schrumpft aber nicht.",
                    color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
            } else {
                StepSlider("Kabel", prefs.wiredMax, 10, 120, 5) { prefs.wiredMax = it }
                StepSlider("Qi (kabellos)", prefs.qiMax, 5, 30, 1) { prefs.qiMax = it }
            }
            SwitchRow("Skalenstriche und Beschriftung", "0 und Höchstwert an den Ring-Enden",
                prefs.scaleLabels) { prefs.scaleLabels = it }
        }

        Section("Anzeige")
        Panel {
            ChoiceRow("Temperatur-Hinweis ab",
                listOf("35 °C" to 35, "40 °C" to 40, "45 °C" to 45), prefs.tempWarn) { prefs.tempWarn = it }
            SwitchRow("Bildschirm anlassen", "Solange die App im Vordergrund ist",
                prefs.keepScreenOn) { prefs.keepScreenOn = it }
        }

        Section("Automatik")
        Panel {
            AutoStartBlock(prefs)
            SwitchRow("Auto-Standby", "Beim Laden nach 1 Minute ohne Bedienung in den Standby wechseln",
                prefs.autoStandby) { prefs.autoStandby = it }
        }

        Section("Standby-Anzeige & Bildschirmschoner")
        Panel {
            PercentSlider("Helligkeit tagsüber", prefs.standbyBrightness) { prefs.standbyBrightness = it }
            SwitchRow("Nachtmodus 22–6 Uhr", "Eigene, sehr niedrige Helligkeit und abgedunkelter Inhalt",
                prefs.nightMode) { prefs.nightMode = it }
            if (prefs.nightMode)
                PercentSlider("Helligkeit nachts", prefs.nightBrightness) { prefs.nightBrightness = it }
            SwitchRow("„Display aus“ immer zeigen", "Sonst nur in den ersten 45 Sekunden des Standbys",
                prefs.quickAlways) { prefs.quickAlways = it }
            SwitchRow("Display sofort ausschalten",
                "„Display aus“ schaltet wie die Seitentaste sofort ab (braucht Geräteadministrator)",
                adminOn) { on -> if (on) Sleep.requestAdmin(ctx) else { Sleep.removeAdmin(ctx); adminTick++ } }
            SwitchRow("Einbrennschutz", "Inhalt wandert jede Minute ein Stück",
                prefs.pixelShift) { prefs.pixelShift = it }
            ChoiceRow("Uhr", listOf("Aus" to 0, "Digital" to 1, "Analog" to 2), prefs.clockStyle) { prefs.clockStyle = it }
            if (prefs.clockStyle == 2)
                SwitchRow("Sekundenzeiger", null, prefs.clockSeconds) { prefs.clockSeconds = it }
            StepSlider("Ringgröße im Standby", prefs.standbyRingSize, 140, 260, 10, unit = "dp") { prefs.standbyRingSize = it }
            Spacer(Modifier.height(4.dp))
            PillButton("Standby jetzt starten", filled = true, onClick = onStandby)
            Spacer(Modifier.height(10.dp))
            PillButton("Bildschirmschoner einrichten") { openDreamSettings(ctx) }
        }
    }
}

// ---------- Hilfe ----------
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    SubPage("Hilfe", onBack) {
        Section("Projektseite")
        Para("Neue Versionen, Download und Quellcode gibt es auf der Projektseite.")
        ProjectLink()

        Section("Hauptanzeige")
        Para("Der Ring zeigt die aktuelle Ladeleistung in Watt. Der Vollausschlag lässt sich in den Einstellungen getrennt für Kabel und Qi festlegen.")
        Para("Oben steht die Ladequelle: Kabel · Netzteil, Kabel · USB oder Qi · kabellos. Darunter Akkuspannung, Ladestrom, Ladestand und Akkutemperatur sowie der Verlauf der letzten 60 Sekunden mit Durchschnitt und Spitzenwert.")

        Section("Ring-Skala")
        Para("Unter Einstellungen → Ring-Skala legst du fest, bei wie viel Watt der Ring voll ist: fest per Schieberegler, getrennt für Kabel (10–120 W) und Qi (5–30 W), oder automatisch. Automatisch nimmt den höchsten Wert des laufenden Ladevorgangs plus 20 % und rundet auf einen glatten Wert. Die Skala wächst dabei mit, springt aber nicht wieder zurück, solange das Kabel steckt.")
        Para("Die Striche am Ring markieren 0, ein Viertel, die Hälfte, drei Viertel und den Höchstwert. Unter den Ring-Enden stehen 0 und der Höchstwert in Watt.")

        Section("Warum weniger als am Netzteil?")
        Para("Gemessen wird, was im Akku ankommt: Akkuspannung × Ladestrom. Wandlerverluste im Handy und der Eigenverbrauch (Display, Funk) sind nicht enthalten. Bei Qi kommen zusätzlich die Verluste der induktiven Übertragung dazu. Ein 45-W-Netzteil zeigt deshalb hier meist deutlich unter 45 W.")
        Para("Gegen Ende der Ladung (ab etwa 80 %) senkt das Handy den Strom absichtlich ab. Das ist normal.")

        Section("Werte sehen unsinnig aus?")
        Para("Android liefert den Ladestrom laut Vorgabe in Mikroampere, manche Hersteller aber in Milliampere. Die App erkennt das automatisch. Zeigt sie trotzdem z. B. 0,01 W oder mehrere tausend Watt, stell in den Einstellungen unter „Messung“ die Einheit von Hand auf µA oder mA um. Der Live-Rohwert dort hilft beim Prüfen.")

        Section("Standby-Anzeige in der App")
        Para("Über den Knopf „Standby“ oder das Menü startest du eine dunkle, gedimmte Anzeige mit Uhrzeit, Leistung und Ladestand. Die Uhr kann aus, digital oder analog (optional mit Sekundenzeiger) sein, die Ringgröße ist einstellbar. Der Bildschirm bleibt dabei an. Antippen beendet den Standby. Drückst du die Seitentaste, geht der Bildschirm wie gewohnt aus.")

        Section("Bildschirmschoner beim Laden")
        Para("Charge Monitor bringt einen Android-Bildschirmschoner mit. Der startet automatisch, wenn das Handy lädt und der Bildschirm-Timeout abläuft.")
        Para("Einrichten: Einstellungen → Anzeige → Bildschirmschoner → „Charge Monitor“ auswählen und als Startzeitpunkt „Beim Laden“ wählen. Der Menüpfad kann je nach One-UI-Version leicht abweichen.")
        PillButton("Bildschirmschoner-Einstellungen öffnen") { openDreamSettings(ctx) }
        Spacer(Modifier.height(8.dp))

        Section("Beim Einstecken automatisch öffnen")
        Para("Optional in den Einstellungen unter „Automatik“. Steckst du das Ladekabel ein oder legst das Handy auf die Qi-Station, öffnet sich Charge Monitor direkt im Standby, auch über dem Sperrbildschirm. Antippen bringt dich zur normalen Ansicht; ist das Handy gesperrt, kommt dann der Sperrbildschirm. Ziehst du das Kabel wieder ab, schließt sich die App von selbst.")
        Para("Dafür braucht die App drei Dinge: die Berechtigung „Über anderen Apps einblenden“ (nur so erlaubt Android den Start aus dem Hintergrund), eine dauerhafte, unauffällige Benachrichtigung „Charge Monitor bereit“ (Android verlangt sie für Hintergrunddienste) und möglichst „Akku-Optimierung aus“, damit Samsung den Dienst nicht beendet. Nach einem Neustart startet der Dienst von selbst wieder.")

        Section("Automatik pausieren")
        Para("Solange die Automatik läuft, zeigt Android die Benachrichtigung „Charge Monitor bereit“. Wegwischen beendet den Dienst nicht. Zum Abschalten gibt es drei Wege: den Knopf „Automatik pausieren“ in der Benachrichtigung, langes Tippen auf das App-Symbol oder den Knopf in den Einstellungen unter „Automatik“.")
        Para("Pausiert heißt: Der Hintergrunddienst wird beendet, die Benachrichtigung verschwindet, und auch nach einem Neustart startet nichts. Die App öffnet sich dann nicht mehr beim Einstecken. Fortsetzen geht über langes Tippen auf das App-Symbol, die Hauptseite oder die Einstellungen. Der Bildschirmschoner gehört nicht zur Automatik; er wird in den Android-Einstellungen ein- und ausgeschaltet.")

        Section("Auto-Standby")
        Para("Ist die App beim Laden offen und du bedienst sie eine Minute lang nicht, wechselt sie in die Standby-Anzeige. Das schont das Display.")

        Section("Display aus / normaler Standby")
        Para("Du willst die Anzeige nicht? In den ersten 45 Sekunden jedes Standbys steht unten „Display aus“ (in den Einstellungen auch dauerhaft einschaltbar). Auf der Hauptseite gibt es den Knopf immer.")
        Para("„Display aus“ schaltet den Standby für den laufenden Ladevorgang ab: Weder Bildschirmschoner noch Auto-Standby kommen wieder, bis du das Kabel abziehst. Auf der Hauptseite kannst du die Pause mit „fortsetzen“ aufheben.")
        Para("Ohne weitere Einrichtung geht das Display danach mit dem normalen Bildschirm-Timeout aus. Soll es sofort ausgehen wie mit der Seitentaste, richte das beim ersten Tippen auf „Display aus“ direkt ein (die App fragt einmal nach) oder schalte später in den Einstellungen „Display sofort ausschalten“ ein. Dafür wird Charge Monitor als Geräteadministrator eingetragen, aber nur mit dem Recht, den Bildschirm zu sperren. Vor dem Deinstallieren diesen Schalter wieder ausschalten, sonst lässt Android die Deinstallation nicht zu.")

        Section("Nachtmodus")
        Para("Zwischen 22 und 6 Uhr nutzt die Standby-Anzeige eine eigene Helligkeit (Standard 1 %) und dunkelt den Inhalt zusätzlich ab. Die Mindesthelligkeit bestimmt Samsung; ganz schwarz geht das Display damit nicht.")

        Section("Always-On-Display")
        Para("Samsungs Always-On-Display lässt keine Inhalte von anderen Apps zu. Deshalb nutzt Charge Monitor den Bildschirmschoner bzw. die eigene Standby-Anzeige.")

        Section("Einbrennschutz")
        Para("Das Display ist ein OLED. Die Standby-Anzeige nutzt dunkle Farben, reduzierte Helligkeit und verschiebt den Inhalt jede Minute ein Stück. Lass den Einbrennschutz am besten eingeschaltet.")

        Section("Temperatur")
        Para("Ab dem eingestellten Grenzwert wird die Temperatur orange markiert. Hohe Akkutemperatur beim Laden belastet den Akku. Das Handy drosselt die Ladeleistung dann in der Regel selbst. Nicht unter Kissen oder in der prallen Sonne laden.")
    }
}

// ---------- Info & Disclaimer ----------
const val DISCLAIMER = """Charge Monitor zeigt Werte an, die Android und der Gerätehersteller über die Akku-Schnittstelle bereitstellen. Die App misst nichts selbst und greift nicht in den Ladevorgang ein.

• Angezeigt wird die Leistung, die im Akku ankommt (Akkuspannung × Ladestrom). Sie ist kleiner als die Leistung am Netzteil oder an der Qi-Ladestation.

• Genauigkeit, Aktualisierungsrate und Einheit des Stromwerts hängen vom Gerät ab. Die Werte sind nicht kalibriert und nicht für Mess- oder Prüfzwecke geeignet.

• Nutzung auf eigene Verantwortung. Es wird keine Gewähr für die Richtigkeit der Anzeige übernommen und keine Haftung für Schäden an Gerät, Akku oder Zubehör.

• Für die optionale Funktion „Beim Einstecken automatisch öffnen“ nutzt die App einen Hintergrunddienst mit dauerhafter Benachrichtigung und die Berechtigung „Über anderen Apps einblenden“. Beides ist abschaltbar, der Dienst lässt sich jederzeit pausieren.

• Die optionale Funktion „Display sofort ausschalten“ trägt die App als Geräteadministrator ein, ausschließlich mit dem Recht „Bildschirm sperren“. Vor dem Deinstallieren abschalten.

• Datenschutz: Die App hat keine Internet-Berechtigung, nutzt keine Konten, zeigt keine Werbung und überträgt keine Daten. Einstellungen werden nur lokal auf dem Gerät gespeichert."""

@Composable
fun InfoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
    }
    SubPage("Info & Disclaimer", onBack) {
        Spacer(Modifier.height(10.dp))
        Panel {
            Text("Charge Monitor", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text("Version $version", color = TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("Ladeleistung live anzeigen – Kabel, USB und Qi.", color = Mint, fontSize = 14.sp)
        }
        ProjectLink()
        Section("Disclaimer")
        Para(DISCLAIMER)
    }
}

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility).padding(20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Willkommen bei", color = TextDim, fontSize = 15.sp)
        Text("Charge Monitor", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Para(DISCLAIMER)
        }
        Spacer(Modifier.height(12.dp))
        PillButton("Verstanden", filled = true, onClick = onAccept)
    }
}

// ---------- Auto-Start beim Einstecken ----------
@Composable
fun AutoStartBlock(prefs: Prefs) {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue   // nach Rückkehr aus den System-Einstellungen neu prüfen
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val batteryOk = remember(tick) { ChargeWatchService.ignoresBatteryOptimization(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SwitchRow("Beim Einstecken automatisch öffnen",
        "Startet direkt im Standby, auch über dem Sperrbildschirm",
        prefs.autoStart) { on ->
        prefs.autoStart = on
        if (on) prefs.autoPaused = false
        ChargeWatchService.sync(ctx)
        if (on) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (!Settings.canDrawOverlays(ctx)) openOverlaySettings(ctx)
        }
    }
    if (prefs.autoStart) {
        if (prefs.autoPaused) {
            Text("Pausiert: kein Hintergrunddienst, keine Benachrichtigung.",
                color = Warn, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PillButton("Automatik fortsetzen", filled = true) { ChargeWatchService.setPaused(ctx, false) }
        } else {
            PillButton("Automatik pausieren") { ChargeWatchService.setPaused(ctx, true) }
        }
        Spacer(Modifier.height(10.dp))
        StatusLine("Über anderen Apps einblenden", overlayOk) { openOverlaySettings(ctx) }
        StatusLine("Akku-Optimierung aus (empfohlen)", batteryOk) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun StatusLine(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onFix).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✓" else "!", color = if (ok) Neon else Warn, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!ok) Text("Einrichten", color = Warn, fontSize = 13.sp)
    }
}

fun openOverlaySettings(ctx: android.content.Context) {
    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun PercentSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Text("$label: $value %", color = Color.White, fontSize = 15.sp)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 1f..100f,
        colors = SliderDefaults.colors(thumbColor = Neon, activeTrackColor = Neon,
            inactiveTrackColor = Color(0x22FFFFFF))
    )
}

/** Schieberegler mit festen Schritten, Wert rechts neben der Beschriftung. */
@Composable
fun StepSlider(label: String, value: Int, min: Int, max: Int, step: Int, unit: String = "W", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$value $unit", color = Mint, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
    Slider(
        value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
        onValueChange = { v -> onChange(((v - min) / step).roundToInt() * step + min) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / step - 1,
        colors = SliderDefaults.colors(thumbColor = Neon, activeTrackColor = Neon,
            inactiveTrackColor = Color(0x22FFFFFF), activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent)
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
fun ProjectLink() {
    val ctx = LocalContext.current
    Spacer(Modifier.height(8.dp))
    PillButton("Projektseite: cappy0815.github.io") {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cappy0815.github.io/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}
