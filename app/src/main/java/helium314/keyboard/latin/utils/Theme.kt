// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity

private val CatppuccinMochaColorScheme = darkColorScheme(
    primary = Color(0xFFCBA6F7), // mauve
    onPrimary = Color(0xFF1E1E2E), // base
    primaryContainer = Color(0xFF45475A), // surface1
    onPrimaryContainer = Color(0xFFCDD6F4), // text
    secondary = Color(0xFFB4BEFE), // lavender
    onSecondary = Color(0xFF1E1E2E),
    secondaryContainer = Color(0xFF313244), // surface0
    onSecondaryContainer = Color(0xFFCDD6F4),
    tertiary = Color(0xFFF5C2E7), // pink
    onTertiary = Color(0xFF1E1E2E),
    background = Color(0xFF1E1E2E), // base
    onBackground = Color(0xFFCDD6F4), // text
    surface = Color(0xFF1E1E2E), // base
    onSurface = Color(0xFFCDD6F4), // text
    surfaceVariant = Color(0xFF313244), // surface0
    onSurfaceVariant = Color(0xFFA6ADC8), // subtext0
    surfaceContainer = Color(0xFF181825), // mantle
    surfaceContainerHigh = Color(0xFF313244), // surface0
    surfaceContainerHighest = Color(0xFF45475A), // surface1
    outline = Color(0xFF585B70), // surface2
    error = Color(0xFFF38BA8), // red
    onError = Color(0xFF11111B),
)

private val CatppuccinLatteColorScheme = lightColorScheme(
    primary = Color(0xFF8839EF), // mauve
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCD0DA), // surface0
    onPrimaryContainer = Color(0xFF4C4F69), // text
    secondary = Color(0xFF7287FD), // lavender
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E9EF), // mantle
    onSecondaryContainer = Color(0xFF4C4F69),
    tertiary = Color(0xFFEA76CB), // pink
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFEFF1F5), // base
    onBackground = Color(0xFF4C4F69), // text
    surface = Color(0xFFEFF1F5), // base
    onSurface = Color(0xFF4C4F69), // text
    surfaceVariant = Color(0xFFCCD0DA), // surface0
    onSurfaceVariant = Color(0xFF6C6F85), // subtext0
    surfaceContainer = Color(0xFFE6E9EF), // mantle
    surfaceContainerHigh = Color(0xFFCCD0DA),
    surfaceContainerHighest = Color(0xFFBCC0CC),
    outline = Color(0xFFACB0BE), // surface2
    error = Color(0xFFD20F39), // red
    onError = Color(0xFFFFFFFF),
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val b = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "recomposition trigger")
    val appTheme = context.prefs().getString(Settings.PREF_APP_THEME, Defaults.PREF_APP_THEME) ?: Defaults.PREF_APP_THEME

    val colorScheme = if (appTheme == Defaults.APP_THEME_CATPPUCCIN) {
        if (dark) CatppuccinMochaColorScheme
        else CatppuccinLatteColorScheme
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else {
            // todo (later): more colors
            if (dark) darkColorScheme(
                primary = colorResource(R.color.accent),
            )
            else lightColorScheme(
                primary = colorResource(R.color.accent)
            )
        }
    }
    val material3 = Typography()
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = material3.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = material3.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleSmall = material3.titleSmall.copy(fontWeight = FontWeight.Bold)
        ),
        //shapes = Shapes(),
        content = content
    )
}

const val previewDark = true
