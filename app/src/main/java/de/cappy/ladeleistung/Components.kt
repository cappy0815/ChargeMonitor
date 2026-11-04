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
fun Gauge(r: Reading, scaleMax: Float, sizeDp: Int = 270, dimmed: Boolean = false) {
    val target = if (r.charging) (r.watts / scaleMax).coerceIn(0f, 1f) else 0f
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
    val scaleMax = (if (r.wireless) prefs.qiMax else prefs.wiredMax).toFloat()

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.offset { IntOffset(sx, sy) }.graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (prefs.standbyClock) {
                Text(clock, color = TextDim, fontSize = 44.sp, fontWeight = FontWeight.Thin)
                Spacer(Modifier.height(18.dp))
            }
            Gauge(r, scaleMax, sizeDp = 190, dimmed = true)
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
