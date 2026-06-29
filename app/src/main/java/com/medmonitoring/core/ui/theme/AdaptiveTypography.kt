package com.medmonitoring.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

enum class TextImportance {
    Primary,
    Secondary,
    Tertiary
}

object AdaptiveTypographyPolicy {
    const val LargeFontScale = 1.35f
    const val SecondaryLargeScale = 0.744f
    const val TertiaryLargeScale = 0.6f
}

@Composable
fun adaptiveTextStyle(
    base: TextStyle,
    importance: TextImportance
): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    if (fontScale < AdaptiveTypographyPolicy.LargeFontScale || importance == TextImportance.Primary) {
        return base
    }
    val scale = when (importance) {
        TextImportance.Primary -> 1f
        TextImportance.Secondary -> AdaptiveTypographyPolicy.SecondaryLargeScale
        TextImportance.Tertiary -> AdaptiveTypographyPolicy.TertiaryLargeScale
    }
    return base.copy(
        fontSize = (base.fontSize.value * scale).sp,
        lineHeight = if (base.lineHeight.isUnspecified) base.lineHeight else (base.lineHeight.value * scale).sp
    )
}

@Composable
fun secondaryTextStyle(base: TextStyle = MaterialTheme.typography.bodySmall): TextStyle =
    adaptiveTextStyle(base, TextImportance.Secondary)

@Composable
fun tertiaryTextStyle(base: TextStyle = MaterialTheme.typography.labelSmall): TextStyle =
    adaptiveTextStyle(base, TextImportance.Tertiary)

@Composable
fun unitTextStyle(base: TextStyle = MaterialTheme.typography.labelLarge): TextStyle =
    adaptiveTextStyle(base, TextImportance.Tertiary)
