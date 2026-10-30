package com.meshmobile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand colours from the FreeCall Offline logo: deep green + gold.
private val Gold = Color(0xFFE9C976)
private val GoldLight = Color(0xFFF4DC96)
private val GoldDeep = Color(0xFFB8903A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5C4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE3),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF8A6A12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E9BF),
    onSecondaryContainer = Color(0xFF2A1F00),
    tertiary = Color(0xFFB8903A),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF10201C),
    surface = Color(0xFFF5F8F6),
    onSurface = Color(0xFF10201C),
    surfaceVariant = Color(0xFFE2ECE8),
    onSurfaceVariant = Color(0xFF3F524D)
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF0F5C4D),
    onPrimaryContainer = Color(0xFFE6F5F1),
    secondary = Color(0xFF7FD1BF),
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF1C2E2A),
    onSecondaryContainer = Color(0xFFDCEBE7),
    tertiary = GoldDeep,
    background = Color(0xFF0B1F1B),
    onBackground = Color(0xFFE6EFEC),
    surface = Color(0xFF0B1F1B),
    onSurface = Color(0xFFE6EFEC),
    surfaceVariant = Color(0xFF16302A),
    onSurfaceVariant = Color(0xFFB4C7C1)
)

private val BrandTypography = Typography().copy(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
)

private val BrandShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

/**
 * App theme: green/gold brand colours, rounded shapes, bolder type, a soft
 * gradient background with a faint "FreeCall Offline" watermark.
 * Follows the phone's light/dark setting.
 */
@Composable
fun FreeCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BrandTypography,
        shapes = BrandShapes
    ) {
        BrandBackground(content)
    }
}

@Composable
private fun BrandBackground(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val top = if (dark) Color(0xFF0B1F1B) else Color(0xFFF5F8F6)
    val bottom = if (dark) Color(0xFF071512) else Color(0xFFE3EDE9)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        Text(
            "FreeCall\nOffline",
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-18f)
                .alpha(0.05f),
            color = if (dark) Gold else Color(0xFF0F5C4D),
            fontSize = 72.sp,
            lineHeight = 78.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        content()
    }
}

/** Branded top bar: gold call badge + "FreeCall OFFLINE" wordmark on deep green, with a gold edge. */
@Composable
fun BrandHeader() {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF082E27), Color(0xFF0F5C4D))))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GoldLight, GoldDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        tint = Color(0xFF241A00),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "FreeCall",
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "OFFLINE",
                        color = Gold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "No internet needed",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(GoldDeep, GoldLight, GoldDeep)))
        )
    }
}
