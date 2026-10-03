package com.astradownloader.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MIUI 风格主题（纯 Compose 实现，还原 MIUI 品牌观感）。
 * - 主色：MIUI 品牌蓝 #0060DF
 * - 背景：#F7F7F7（浅灰），卡片：#FFFFFF
 * - 圆角：12dp 大圆角卡片
 * - 文字：深灰 #1A1A1A
 */
object Miuix {
    val BrandBlue = Color(0xFF0060DF)
    val BrandBlueDark = Color(0xFF0A84FF)
    val Background = Color(0xFFF7F7F7)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF8A8A8E)
    val Divider = Color(0xFFE5E5EA)
    val Success = Color(0xFF34C759)
    val Danger = Color(0xFFFF3B30)
    val Warning = Color(0xFFFF9500)
}

private val MiuixColors = lightColorScheme(
    primary = Miuix.BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F1FF),
    onPrimaryContainer = Miuix.BrandBlueDark,
    secondary = Color(0xFF8A8A8E),
    onSecondary = Color.White,
    background = Miuix.Background,
    onBackground = Miuix.OnSurface,
    surface = Miuix.Surface,
    onSurface = Miuix.OnSurface,
    surfaceVariant = Miuix.Surface,
    onSurfaceVariant = Miuix.TextSecondary,
    outline = Miuix.Divider,
    error = Miuix.Danger,
    onError = Color.White,
)

private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val MiuixTypography = Typography()

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MiuixColors,
        shapes = MiuixShapes,
        typography = MiuixTypography,
        content = content,
    )
}