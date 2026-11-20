package com.munkberries.tikhayapolyanka.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import com.munkberries.tikhayapolyanka.R

val Paper = Color(0xFFF7F0E3)
val CardPaper = Color(0xFFFFF9EF)
val Ink = Color(0xFF3D291D)
val Muted = Color(0xFF6B5C4B)
val Sage = Color(0xFF526448)
val PaleSage = Color(0xFFE1E6D4)
val Terracotta = Color(0xFFAD6245)
val Line = Color(0xFFB5A184)

@OptIn(ExperimentalTextApi::class)
val Montserrat = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.montserrat, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.montserrat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
private fun typography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        displayMedium = t.displayMedium.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        displaySmall = t.displaySmall.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        headlineLarge = t.headlineLarge.copy(fontFamily=Montserrat, fontWeight=FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily=Montserrat, fontWeight=FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily=Montserrat, fontWeight=FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily=Montserrat, fontWeight=FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily=Montserrat),
        bodyMedium = t.bodyMedium.copy(fontFamily=Montserrat),
        bodySmall = t.bodySmall.copy(fontFamily=Montserrat),
        labelLarge = t.labelLarge.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily=Montserrat, fontWeight=FontWeight.Medium),
    )
}
@Composable fun PolyankaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary=Sage, onPrimary=Color.White, primaryContainer=PaleSage, onPrimaryContainer=Ink,
        secondary=Terracotta, onSecondary=Color.White, secondaryContainer=PaleSage, onSecondaryContainer=Ink,
        tertiary=Sage, onTertiary=Color.White, tertiaryContainer=PaleSage, onTertiaryContainer=Ink, background=Paper, onBackground=Ink,
        surface=CardPaper, onSurface=Ink, surfaceVariant=PaleSage, onSurfaceVariant=Muted,
        outline=Muted, outlineVariant=Line, error=Color(0xFF9A392C),
    ), typography=typography(), shapes=Shapes(
        extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ), content=content)
}
