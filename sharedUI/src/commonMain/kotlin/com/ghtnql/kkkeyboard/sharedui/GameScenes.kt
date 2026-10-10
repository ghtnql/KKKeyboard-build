package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kkkeyboard.sharedui.generated.resources.Res
import kkkeyboard.sharedui.generated.resources.cafe_background
import kkkeyboard.sharedui.generated.resources.cafe_customers
import kkkeyboard.sharedui.generated.resources.rain_city_alley
import org.jetbrains.compose.resources.painterResource

private val RainMint = Color(0xFF70E7CE)
private val Ink = Color(0xFF243C4D)

/** Rainy alley, falling answers, and moving raindrops form the playable arena. */
@Composable
internal fun RainGameScene(targets: List<RainTarget>, typedAnswer: String, feedback: String, mode: PracticeMode,
                           catalog: PracticeTranslationCatalog, modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    val rain = rememberInfiniteTransition(label = "rain")
    val phase by rain.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "rainfall")
    BoxWithConstraints(modifier.fillMaxSize()) {
        Image(painterResource(Res.drawable.rain_city_alley), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x34081B28)))
        Canvas(Modifier.fillMaxSize()) {
            repeat(43) { index ->
                val x = size.width * ((index * 73 + 17) % 101) / 101f
                val y = (size.height * ((index * 41 % 97) / 97f + phase)) % size.height
                drawLine(Color(0x99C8EBF3), Offset(x, y), Offset(x - 3.dp.toPx(), y + (9 + index % 5).dp.toPx()),
                    strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
            drawLine(Color(0xA4B5E9E4), Offset(0f, size.height - 4.dp.toPx()),
                Offset(size.width, size.height - 4.dp.toPx()), 2.dp.toPx())
        }
        val density = LocalDensity.current
        targets.forEach { target ->
            var widthPx by remember(target.id) { mutableIntStateOf(0) }
            var heightPx by remember(target.id) { mutableIntStateOf(0) }
            val x = with(density) { ((maxWidth.toPx() - widthPx).coerceAtLeast(0f) * target.x).toDp() }
            val matching = typedAnswer.isNotBlank() && target.item.matchesGamePrefix(typedAnswer, mode)
            val travel = (maxHeight - with(density) { heightPx.toDp() }).coerceAtLeast(0.dp)
            Box(Modifier.offset(x = x, y = travel * target.y.coerceIn(0f, 1f))
                .widthIn(max = maxWidth - 12.dp).onSizeChanged { widthPx = it.width; heightPx = it.height }
                .shadow(5.dp, RoundedCornerShape(8.dp))
                .background(Color(0xFFF9F7F0), RoundedCornerShape(8.dp))
                .border(if (matching) 2.dp else 1.dp,
                    if (matching) RainMint else Color(0xAA536C70), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)) {
                PracticePromptView(catalog.resolve(target.item), compact = true, textColor = Color(0xFF1D292B))
            }
        }
        if (feedback == strings.rainHit) {
            Text("+ ${strings.rainHit}", Modifier.align(Alignment.TopEnd).padding(14.dp)
                .background(Color(0xB117645D), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Café backdrop, customer pose, and receipt stay together while the keyboard opens. */
@Composable
internal fun CafeGameScene(session: CafeSession, typedAnswer: String, reaction: Pair<Int, Boolean>? = null,
                           catalog: PracticeTranslationCatalog, modifier: Modifier = Modifier, sceneHeight: Dp = 320.dp) {
    val strings = LocalUiStrings.current
    val idle = rememberInfiniteTransition(label = "customer")
    val bob by idle.animateFloat(-2f, 2f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse), label = "customerBreathing")
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFD1EBDE))) {
        val compact = maxHeight < 240.dp
        Image(painterResource(Res.drawable.cafe_background), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val customerSize = if (compact) (maxHeight * 0.52f).coerceAtMost(maxWidth * 0.40f)
            else (maxWidth * 0.44f).coerceAtMost(maxHeight * 0.72f)
        val row = (reaction?.first ?: session.currentIndex) % 2
        val pose = when (reaction?.second) { true -> 1; false -> 2; null -> 0 }
        // Clip one cell of the two-row, three-column character atlas.
        Box(Modifier.offset(x = maxWidth * 0.07f, y = (if (compact) 8.dp else maxWidth * 0.10f) + bob.dp)
            .size(customerSize).clip(RoundedCornerShape(4.dp))) {
            Image(painterResource(Res.drawable.cafe_customers), null,
                Modifier.offset(x = -(customerSize * pose), y = -(customerSize * row))
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .width(customerSize * 3).height(customerSize * 2),
                contentScale = ContentScale.FillBounds)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp)
            .widthIn(max = maxWidth * (if (compact) 0.72f else 0.64f)).shadow(7.dp, RoundedCornerShape(5.dp))
            .background(Color(0xFFFFFEF6), RoundedCornerShape(5.dp))
            .border(3.dp, Color(0xFFE3A19A), RoundedCornerShape(5.dp)).padding(if (compact) 6.dp else 10.dp)) {
            Text(strings.orderCard(session.currentIndex + 1, session.orderCount), color = Color(0xFF9B6870),
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
            if (session.hideOrder) {
                Text(strings.rememberOrder, color = Ink, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp)
            } else {
                session.currentItem?.let { PracticePromptView(catalog.resolve(it), compact = compact, textColor = Ink) }
            }
            if (typedAnswer.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(typedAnswer, color = Color(0xFF548A7D), fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            LinearProgressIndicator(
                progress = { (session.remainingMs.toFloat() / session.difficulty.orderMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = Color(0xFF6EB8A4), trackColor = Color(0xFFE4E9DD),
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp)
            .background(Color(0xDDFDF8EF), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(strings.seconds(session.remainingMs / 1000), color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(strings.successes(session.successes), color = Ink)
        }
    }
}
