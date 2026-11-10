package de.cappy.ladeleistung

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------- Farben (Grün-Stil) ----------
val BgTop = Color(0xFF0B2A1A)
val BgBottom = Color(0xFF020805)
val Neon = Color(0xFF2BE38A)
val Mint = Color(0xFF7CFFB8)
val Deep = Color(0xFF0F5C36)
val CardBg = Color(0x1A7CFFB8)
val TextDim = Color(0xFF8FBFA4)
val Warn = Color(0xFFFFB347)

fun fmt(pattern: String, vararg a: Any): String = String.format(Locale.GERMANY, pattern, *a)

@Composable
fun SourceChip(r: Reading) {
    val text = if (r.full && r.charging) "${r.sourceText} · voll" else r.sourceText
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "source"
    ) { t ->
        Text(
            t, color = if (r.charging) Mint else TextDim, fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (r.charging) Deep.copy(alpha = .55f) else CardBg)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun Gauge(r: Reading, scaleMax: Float, sizeDp: Int = 270, dimmed: Boolean = false, showLabels: Boolean = true) {
    // Skalenwechsel (z. B. Auto-Skala) weich überblenden
    val maxAnim by animateFloatAsState(scaleMax, tween(800), label = "max")
    val target = if (r.charging) (r.watts / maxAnim).coerceIn(0f, 1f) else 0f
    val sweep by animateFloatAsState(target, spring(dampingRatio = .8f, stiffness = 60f), label = "sweep")
    val watts by animateFloatAsState(if (r.charging) r.watts else 0f, tween(450), label = "watts")

    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        .35f, .9f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val rot by pulse.animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rot"
    )
    val scale = sizeDp / 270f

    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx() * scale
            val inset = stroke / 2 + 10.dp.toPx() * scale
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val start = 135f
            val total = 270f
            drawArc(Color(if (dimmed) 0x14FFFFFF else 0x22FFFFFF), start, total, false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            if (showLabels) {
                // Skalenstriche bei 0, ¼, ½, ¾ und Vollausschlag (innen am Ring)
                val rOuter = arcSize.width / 2 - stroke / 2 - 5.dp.toPx() * scale
                val rInner = rOuter - 7.dp.toPx() * scale
                for (k in 0..4) {
                    val a = Math.toRadians((start + total * k / 4f).toDouble())
                    val c = cos(a).toFloat(); val sn = sin(a).toFloat()
                    drawLine(Color(if (dimmed) 0x22FFFFFF else 0x44FFFFFF),
                        Offset(center.x + rInner * c, center.y + rInner * sn),
                        Offset(center.x + rOuter * c, center.y + rOuter * sn),
                        strokeWidth = 1.5.dp.toPx() * scale, cap = StrokeCap.Round)
                }
            }
            if (r.charging) {
                if (!dimmed) drawArc(Neon.copy(alpha = glow * .25f), start, total * sweep, false, tl, arcSize,
                    style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(listOf(Deep, Neon, Mint, Deep), center),
                    start, total * sweep, false, tl, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val rad = Math.toRadians(rot.toDouble())
                val rr = size.minDimension / 2 - inset - stroke * 1.4f
                drawCircle(Mint.copy(alpha = if (dimmed) .4f else .7f), 4.dp.toPx() * scale,
                    Offset(center.x + (rr * cos(rad)).toFloat(), center.y + (rr * sin(rad)).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (r.charging) fmt("%.1f", watts) else "–",
                color = if (dimmed) Mint else Color.White,
                fontSize = (64 * scale).sp, fontWeight = FontWeight.Light
            )
            Text("Watt", color = Neon, fontSize = (18 * scale).sp, letterSpacing = 2.sp)
        }
        if (showLabels) {
            // Beschriftung an den Ring-Enden: links 0, rechts Vollausschlag
            val end = sizeDp / 2f * .62f            // Abstand der Enden von der Mitte (≈ R·cos45°)
            val labelColor = TextDim.copy(alpha = if (dimmed) .6f else .9f)
            Text("0", color = labelColor, fontSize = (12 * scale).sp,
                modifier = Modifier.align(Alignment.Center).offset(x = (-end).dp, y = (end + 26 * scale).dp))
            Text(fmt("%.0f W", maxAnim), color = labelColor, fontSize = (12 * scale).sp,
                modifier = Modifier.align(Alignment.Center).offset(x = end.dp, y = (end + 26 * scale).dp))
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (warn) Warn.copy(alpha = .12f) else CardBg)
            .border(1.dp, (if (warn) Warn else Neon).copy(alpha = .25f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(label.uppercase(), color = if (warn) Warn else TextDim, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = if (warn) Warn else Color.White,
            fontSize = if (value.length > 8) 18.sp else 22.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false)
    }
}

@Composable
fun Sparkline(live: LiveState) {
    val data = live.history
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Neon.copy(alpha = .15f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VERLAUF 60 S", color = TextDim, fontSize = 11.sp, letterSpacing = 1.5.sp)
            if (live.reading.charging && data.isNotEmpty()) {
                Text(fmt("Ø %.1f W · max %.1f W", data.sum() / data.size, live.peak),
                    color = Mint, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            if (data.size < 2) return@Canvas
            val maxV = maxOf(data.max(), 1f) * 1.15f
            val stepX = size.width / (live.capacity - 1).coerceAtLeast(1)
            val offsetX = size.width - stepX * (data.size - 1)
            val line = Path()
            data.forEachIndexed { idx, v ->
                val x = offsetX + idx * stepX
                val y = size.height - v / maxV * size.height
                if (idx == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(line); lineTo(size.width, size.height); lineTo(offsetX, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Neon.copy(alpha = .35f), Color.Transparent)))
            drawPath(line, Neon, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Kleine, dunkle Standby-Anzeige. Wird in der App (Standby-Modus) und im
 * Bildschirmschoner verwendet. Einbrennschutz: Inhalt wandert jede Minute leicht.
 * Nachts (22–6 Uhr, abschaltbar) wird zusätzlich der Inhalt abgedunkelt.
 *
 * onBrightness: setzt die Fensterhelligkeit (wird minütlich neu berechnet)
 * onDisplayOff: Schnelloption "Display aus"
 */
@Composable
fun StandbyDisplay(
    live: LiveState,
    prefs: Prefs,
    modifier: Modifier = Modifier,
    onBrightness: (Float) -> Unit = {},
    onDisplayOff: (() -> Unit)? = null
) {
    val r = live.reading
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    val sx by animateIntAsState(shift.x, tween(3000), label = "sx")
    val sy by animateIntAsState(shift.y, tween(3000), label = "sy")
    var now by remember { mutableStateOf(Date()) }
    var night by remember { mutableStateOf(prefs.isNight()) }

    LaunchedEffect(prefs.pixelShift) {
        while (true) {
            shift = if (prefs.pixelShift) IntOffset(Random.nextInt(-40, 41), Random.nextInt(-80, 81)) else IntOffset.Zero
            delay(60_000)
        }
    }
    LaunchedEffect(Unit) { while (true) { now = Date(); night = prefs.isNight(); delay(5_000) } }
    LaunchedEffect(night, prefs.standbyBrightness, prefs.nightBrightness, prefs.nightMode) {
        onBrightness(prefs.effectiveStandbyBrightness())
    }

    // Schnelloption: 45 s nach Start sichtbar, oder dauerhaft
    val since = AppState.standbySince.longValue
    var quickVisible by remember { mutableStateOf(true) }
    LaunchedEffect(since, prefs.quickAlways) {
        quickVisible = true
        if (!prefs.quickAlways) {
            val left = 45_000L - (System.currentTimeMillis() - since)
            if (left > 0) delay(left)
            quickVisible = false
        }
    }
    val contentAlpha = if (night) .5f else 1f

    val clock = remember(now) { SimpleDateFormat("HH:mm", Locale.GERMANY).format(now) }
    val scaleMax = prefs.scaleFor(r, live.peak)

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.offset { IntOffset(sx, sy) }.graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (prefs.clockStyle) {
                1 -> {
                    Text(clock, color = TextDim, fontSize = 44.sp, fontWeight = FontWeight.Thin)
                    Spacer(Modifier.height(18.dp))
                }
                2 -> {
                    AnalogClock(sizeDp = 120, seconds = prefs.clockSeconds)
                    Spacer(Modifier.height(18.dp))
                }
            }
            Gauge(r, scaleMax, sizeDp = prefs.standbyRingSize, dimmed = true, showLabels = prefs.scaleLabels)
            Spacer(Modifier.height(14.dp))
            Text(r.sourceText + if (r.full && r.charging) " · voll" else "",
                color = Neon.copy(alpha = .8f), fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            // Akku-Balken
            Box(
                Modifier.width(120.dp).height(6.dp).clip(RoundedCornerShape(50))
                    .background(Color(0x22FFFFFF))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(r.levelPct / 100f)
                    .background(Neon.copy(alpha = .7f)))
            }
            Spacer(Modifier.height(6.dp))
            Text("${r.levelPct} %  ·  " + fmt("%.1f °C", r.tempC),
                color = if (r.tempC >= prefs.tempWarn) Warn else TextDim, fontSize = 13.sp)
        }

        if (onDisplayOff != null) {
            AnimatedVisibility(
                visible = quickVisible,
                enter = fadeIn(tween(400)), exit = fadeOut(tween(1200)),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "Display aus",
                    color = TextDim, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, TextDim.copy(alpha = .35f), RoundedCornerShape(50))
                        .clickable(onClick = onDisplayOff)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** Schlichte analoge Uhr im Grün-Stil (für den Standby). */
@Composable
fun AnalogClock(sizeDp: Int, seconds: Boolean, dimmed: Boolean = true) {
    var now by remember { mutableStateOf(java.util.Calendar.getInstance()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = java.util.Calendar.getInstance()
            // Mit Sekundenzeiger jede Sekunde, sonst alle 10 s neu zeichnen
            delay(if (seconds) 1000L - System.currentTimeMillis() % 1000 else 10_000L)
        }
    }
    val h = now.get(java.util.Calendar.HOUR) + now.get(java.util.Calendar.MINUTE) / 60f
    val m = now.get(java.util.Calendar.MINUTE) + now.get(java.util.Calendar.SECOND) / 60f
    val sec = now.get(java.util.Calendar.SECOND)
    val tick = Color(if (dimmed) 0x55FFFFFF else 0x88FFFFFF)

    Canvas(Modifier.size(sizeDp.dp)) {
        val rad = size.minDimension / 2
        drawCircle(Color(0x18FFFFFF), rad - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val long = i % 3 == 0
            val r1 = rad - 4.dp.toPx(); val r2 = rad - (if (long) 13 else 9).dp.toPx()
            drawLine(if (long) Neon.copy(alpha = .7f) else tick,
                Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                strokeWidth = (if (long) 2.5f else 1.5f).dp.toPx(), cap = StrokeCap.Round)
        }
        fun hand(angleDeg: Float, len: Float, width: Float, color: Color) {
            val a = Math.toRadians(angleDeg - 90.0)
            drawLine(color, center,
                Offset(center.x + len * cos(a).toFloat(), center.y + len * sin(a).toFloat()),
                strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(h * 30f, rad * .5f, 4.dp.toPx(), TextDim)
        hand(m * 6f, rad * .75f, 2.5.dp.toPx(), Mint.copy(alpha = .85f))
        if (seconds) hand(sec * 6f, rad * .82f, 1.dp.toPx(), Neon)
        drawCircle(Neon, 3.dp.toPx())
    }
}

/** Kleiner Hinweis-Chip mit Aktion (antippbar), farbiger Rahmen. */
@Composable
fun NoticeChip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = .12f))
            .border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Verlauf im Vollbild (Querformat): Achsen, Rasterlinien, Durchschnittslinie, Kennwerte. */
@Composable
fun FullChart(live: LiveState, prefs: Prefs) {
    val r = live.reading
    val data = live.history
    val measurer = rememberTextMeasurer()
    val avg = if (data.isNotEmpty()) data.sum() / data.size else 0f

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Kopfzeile: aktueller Wert links, Kennwerte rechts
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(if (r.charging) fmt("%.1f", r.watts) else "–", color = Color.White,
                fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(" W", color = Neon, fontSize = 18.sp, modifier = Modifier.padding(bottom = 7.dp))
            Spacer(Modifier.width(14.dp))
            Text(r.sourceText, color = Mint, fontSize = 14.sp, modifier = Modifier.padding(bottom = 9.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (r.charging && data.isNotEmpty())
                    Text(fmt("Ø %.1f W  ·  max %.1f W", avg, live.peak), color = Mint, fontSize = 14.sp)
                Text(fmt("%.2f V  ·  %.2f A  ·  %d %%  ·  %.1f °C",
                    r.voltageMv / 1000f, r.currentA, r.levelPct, r.tempC),
                    color = if (r.tempC >= prefs.tempWarn) Warn else TextDim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!r.charging || data.size < 2) {
                Text(
                    if (!r.charging) "Kein Ladevorgang – der Verlauf startet beim Einstecken"
                    else "Verlauf wird aufgezeichnet …",
                    color = TextDim, fontSize = 15.sp
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val labelStyle = TextStyle(color = TextDim, fontSize = 11.sp)
                val leftPad = 44.dp.toPx()
                val bottomPad = 22.dp.toPx()
                val w = size.width - leftPad
                val h = size.height - bottomPad

                // Y-Skala mit glatten Schritten (1, 2, 5, 10, 20, 25, 50 W), höchstens 6 Stufen
                val top = maxOf(data.maxOrNull() ?: 0f, 1f) * 1.15f
                val step = listOf(1f, 2f, 5f, 10f, 20f, 25f, 50f).first { kotlin.math.ceil(top / it) <= 6 }
                val n = kotlin.math.ceil(top / step).toInt().coerceAtLeast(2)
                val yMax = n * step

                // Rasterlinien + Beschriftung
                for (k in 0..n) {
                    val v = step * k
                    val y = h - v / yMax * h
                    drawLine(Color(if (k == 0) 0x44FFFFFF else 0x1AFFFFFF),
                        Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    val t = measurer.measure(fmt("%.0f W", v), labelStyle)
                    drawText(t, topLeft = Offset(leftPad - t.size.width - 8.dp.toPx(), y - t.size.height / 2f))
                }
                // Zeitachse: −60 s … jetzt
                val seconds = live.capacity * prefs.intervalMs / 1000
                for (k in 0..4) {
                    val x = leftPad + w * k / 4f
                    val label = if (k == 4) "jetzt" else "−${seconds * (4 - k) / 4} s"
                    val t = measurer.measure(label, labelStyle)
                    val tx = (x - t.size.width / 2f).coerceIn(leftPad, size.width - t.size.width)
                    drawText(t, topLeft = Offset(tx, h + 6.dp.toPx()))
                }

                if (r.charging && data.size >= 2) {
                    val stepX = w / (live.capacity - 1).coerceAtLeast(1)
                    val offsetX = leftPad + w - stepX * (data.size - 1)
                    val line = Path()
                    data.forEachIndexed { i, v ->
                        val x = offsetX + i * stepX
                        val y = h - v / yMax * h
                        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    val fill = Path().apply {
                        addPath(line); lineTo(leftPad + w, h); lineTo(offsetX, h); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(Neon.copy(alpha = .35f), Color.Transparent), endY = h))
                    // Durchschnitt gestrichelt
                    val ya = h - avg / yMax * h
                    drawLine(Mint.copy(alpha = .6f), Offset(leftPad, ya), Offset(size.width, ya),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    drawPath(line, Neon, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Endpunkt
                    drawCircle(Mint, 4.dp.toPx(), Offset(leftPad + w, h - data.last() / yMax * h))
                }
            }
        }
    }
}
