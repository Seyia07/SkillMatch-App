package com.skillmatch.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.skillmatch.app.ui.theme.Sm
import kotlin.math.roundToInt

/** Colour + icon + text for each label, so colour is never the only signal. */
data class LabelStyle(val text: String, val bg: Color, val fg: Color, val bar: Color, val icon: ImageVector)

fun labelStyle(label: Int): LabelStyle = when (label) {
    2 -> LabelStyle("Good Fit", Sm.GreenTint, Sm.GreenDark, Sm.Green, Icons.Filled.Check)
    1 -> LabelStyle("Potential Fit", Sm.BrownTint, Sm.BrownDark, Sm.Brown, Icons.Filled.Remove)
    else -> LabelStyle("No Fit", Sm.BerryTint, Sm.BerryDark, Sm.Berry, Icons.Filled.Close)
}

fun percent(x: Double): Int = (x * 100).roundToInt().coerceIn(0, 100)

@Composable
fun LabelChip(label: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val s = labelStyle(label)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(s.bg)
            .padding(horizontal = if (large) 12.dp else 10.dp, vertical = if (large) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(if (large) 18.dp else 16.dp))
        Text(s.text, color = s.fg, style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
    }
}

/** A labelled 0-100% bar. */
@Composable
fun PercentBar(title: String, value: Double, barColor: Color, track: Color = Sm.Track, hint: String? = null, titleColor: Color = Sm.Ink) {
    val pct = percent(value)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$title: $pct percent" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, modifier = Modifier.weight(1f))
            Text("$pct%", style = MaterialTheme.typography.titleSmall, color = Sm.Ink)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Sm.Faint)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .width(maxWidth * value.toFloat().coerceIn(0f, 1f))
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
            )
        }
    }
}

enum class MessageKind { Error, Warning, Info }

@Composable
fun MessageBanner(text: String, kind: MessageKind, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        MessageKind.Error -> Sm.BerryTint to Sm.BerryDark
        MessageKind.Warning -> Sm.BrownTint to Sm.BrownDark
        MessageKind.Info -> Sm.BlueBg to Sm.BlueDeep
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (kind == MessageKind.Info) Icons.Filled.Info else Icons.Filled.ErrorOutline,
            contentDescription = null, tint = fg, modifier = Modifier.size(20.dp),
        )
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WordChip(word: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(word, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

val UpIcon = Icons.Filled.ArrowUpward
val DownIcon = Icons.Filled.ArrowDownward

/** Photo header with a plum tint so white text stays readable. */
@Composable
fun PhotoHeader(
    @DrawableRes image: Int,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight)) {
        Image(
            painterResource(image), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Sm.PlumDark.copy(alpha = 0.72f), Sm.PlumDark.copy(alpha = 0.38f), Sm.PlumDark.copy(alpha = 0.82f)),
                    ),
                ),
        )
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** Light header with a soft coloured shape in the corner (used where there is no photo). */
@Composable
fun SoftHeader(tint: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(170.dp)
                .offset(x = 50.dp, y = (-50).dp)
                .clip(CircleShape)
                .background(tint),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 18.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Sm.BabyBlue.copy(alpha = 0.8f)),
        )
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun BackLink(text: String, onClick: () -> Unit, color: Color) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ScreenTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.headlineSmall, color = color,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sm.Plum, contentColor = Color.White),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Sm.Plum, container: Color = Color.White) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = color),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Sm.Plum, trackColor = Sm.BabyBlue)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Sm.BlueDeep, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Sm.BabyBlue,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Sm.BlueDeep, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sm.Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Sm.Muted, textAlign = TextAlign.Center)
        if (action != null) PrimaryButton(action, onAction)
    }
}

/** Small box used inside a Box for corner decorations. */
@Composable
fun BoxScope.Dot(color: Color, size: Dp, alignment: Alignment) {
    Box(Modifier.align(alignment).size(size).clip(CircleShape).background(color))
}
