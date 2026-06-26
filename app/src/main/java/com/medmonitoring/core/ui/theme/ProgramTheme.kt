package com.medmonitoring.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medmonitoring.core.domain.model.ProgramVisualConfig

val LocalProgramVisuals = staticCompositionLocalOf<ProgramVisualConfig> {
    error("ProgramVisualConfig is not provided")
}

@Composable
fun ProgramTheme(
    visualConfig: ProgramVisualConfig,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colors = visualConfig.theme.lightColors
    val baseColorScheme = when {
        visualConfig.theme.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        visualConfig.theme.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = colors.primary.toColor())
        else -> lightColorScheme(
            primary = colors.primary.toColor(),
            onPrimary = colors.onPrimary.toColor(),
            primaryContainer = colors.primaryContainer.toColor(),
            secondary = colors.secondary.toColor(),
            onSecondary = colors.onSecondary.toColor(),
            surface = colors.surface.toColor(),
            surfaceVariant = colors.surfaceVariant.toColor(),
            onSurface = colors.onSurface.toColor(),
            onSurfaceVariant = colors.onSurfaceVariant.toColor(),
            outline = colors.outline.toColor(),
            outlineVariant = colors.outlineVariant.toColor(),
            error = colors.error.toColor(),
            onError = colors.onError.toColor()
        )
    }
    val colorScheme = baseColorScheme.copy(
        background = baseColorScheme.background,
        surface = baseColorScheme.surface
    )
    val shapeConfig = visualConfig.shapes
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(shapeConfig.chipRadiusDp.dp),
        small = RoundedCornerShape(shapeConfig.smallRadiusDp.dp),
        medium = RoundedCornerShape(shapeConfig.cardRadiusDp.dp),
        large = RoundedCornerShape(shapeConfig.sheetRadiusDp.dp),
        extraLarge = RoundedCornerShape(shapeConfig.dialogRadiusDp.dp)
    )
    val type = visualConfig.typography
    val tabular = if (type.tabularNumbers) "tnum" else null
    val typography = Typography(
        titleLarge = TextStyle(fontSize = type.screenTitleSp.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = type.sectionHeaderSp.sp, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(
            fontSize = type.statValueSp.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = tabular
        ),
        headlineSmall = TextStyle(
            fontSize = type.summaryValueSp.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = tabular
        ),
        displayMedium = TextStyle(
            fontSize = type.stepperValueSp.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = tabular
        ),
        displaySmall = TextStyle(
            fontSize = type.stepperValueSp.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = tabular
        ),
        labelMedium = TextStyle(fontSize = type.tagLabelSp.sp, fontWeight = FontWeight.Medium),
        bodySmall = TextStyle(fontSize = type.metaSp.sp)
    )

    CompositionLocalProvider(LocalProgramVisuals provides visualConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

fun String.toColor(): Color {
    val hex = removePrefix("#")
    val value = hex.toLong(16)
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> error("Unsupported color: $this")
    }
}
