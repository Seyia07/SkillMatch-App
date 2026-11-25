package com.skillmatch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette: plum, green, baby blue, brown (+ a berry red for "No Fit").
object Sm {
    val Plum = Color(0xFF5B2A55)
    val PlumDark = Color(0xFF3F1C3B)
    val PlumTint = Color(0xFFF3E8F1)
    val PlumSoft = Color(0xFFEAD7E6)

    val Green = Color(0xFF2E7D4F)
    val GreenDark = Color(0xFF1E5C38)
    val GreenTint = Color(0xFFE3F2E8)

    val BabyBlue = Color(0xFFCFE8F7)
    val BlueDeep = Color(0xFF1F4E6B)
    val BlueBg = Color(0xFFEEF7FC)
    val BlueLine = Color(0xFFA9CFE6)

    val Brown = Color(0xFF7B4A26)
    val BrownDark = Color(0xFF5C3519)
    val BrownTint = Color(0xFFF4EADF)

    val Berry = Color(0xFFA3324F)
    val BerryDark = Color(0xFF8A2440)
    val BerryTint = Color(0xFFF9E4EA)

    val Ink = Color(0xFF22181F)
    val Muted = Color(0xFF4A4048)
    val Faint = Color(0xFF5E5560)
    val Track = Color(0xFFE4EEF4)
}

private val colors = lightColorScheme(
    primary = Sm.Plum,
    onPrimary = Color.White,
    primaryContainer = Sm.PlumTint,
    onPrimaryContainer = Sm.PlumDark,
    secondary = Sm.Green,
    onSecondary = Color.White,
    secondaryContainer = Sm.GreenTint,
    onSecondaryContainer = Sm.GreenDark,
    tertiary = Sm.Brown,
    onTertiary = Color.White,
    tertiaryContainer = Sm.BrownTint,
    onTertiaryContainer = Sm.BrownDark,
    error = Sm.Berry,
    onError = Color.White,
    errorContainer = Sm.BerryTint,
    onErrorContainer = Sm.BerryDark,
    background = Sm.BlueBg,
    onBackground = Sm.Ink,
    surface = Color.White,
    onSurface = Sm.Ink,
    surfaceVariant = Sm.BlueBg,
    onSurfaceVariant = Sm.Muted,
    outline = Sm.BlueLine,
)

// 16sp minimum for body text, as the brief asks.
private val type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SkillMatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
