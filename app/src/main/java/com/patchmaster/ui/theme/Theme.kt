package com.patchmaster.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Cyan = Color(0xFF00BCD4)
private val Teal = Color(0xFF009688)
private val DeepOrange = Color(0xFFFF5722)
private val DarkBg = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurface2 = Color(0xFF2D2D2D)
private val OnDark = Color(0xFFE0E0E0)

val PatchMasterColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00251E),
    secondary = Color(0xFF00ACC1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    tertiary = DeepOrange,
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF79747E)
)

val PatchMasterDarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFA7F3EC),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF004E5C),
    tertiary = Color(0xFFFF8A65),
    onTertiary = Color(0xFF3E0A00),
    background = DarkBg,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFCF6679),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    outline = Color(0xFF938F99)
)

val PatchMasterTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
)

@Composable
fun PatchMasterTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PatchMasterDarkColors else PatchMasterColors,
        typography = PatchMasterTypography,
        content = content
    )
}
