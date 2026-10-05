package helium314.keyboard

import androidx.core.graphics.toColorInt
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.DefaultColors
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GboardThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)

    private fun presetColorMap(theme: String, isNight: Boolean = false): Map<String, Int?> =
        KeyboardTheme.getPresetColorSettings(theme, isNight, context).associate { it.name to it.color }

    @Test
    fun gboardDarkColorsMapping() {
        val colors = KeyboardTheme.getGboardDarkColors(KeyboardTheme.STYLE_ROUNDED, true)
        assertEquals("#202124".toColorInt(), colors.get(ColorType.MAIN_BACKGROUND))
        assertEquals("#303134".toColorInt(), colors.get(ColorType.KEY_BACKGROUND))
        assertEquals("#3C4043".toColorInt(), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
        assertEquals("#303134".toColorInt(), colors.get(ColorType.SPACE_BAR_BACKGROUND))
        assertEquals("#8AB4F8".toColorInt(), colors.get(ColorType.ACTION_KEY_BACKGROUND))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.ACTION_KEY_ICON))
        assertEquals("#E8EAED".toColorInt(), colors.get(ColorType.KEY_TEXT))
        assertEquals("#9AA0A6".toColorInt(), colors.get(ColorType.KEY_HINT_TEXT))
        assertEquals("#8AB4F8".toColorInt(), colors.get(ColorType.GESTURE_TRAIL))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.STRIP_BACKGROUND))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.NAVIGATION_BAR))
    }

    @Test
    fun catppuccinMochaMapping() {
        val map = presetColorMap(KeyboardTheme.THEME_CATPPUCCIN_MOCHA)
        assertEquals("#cba6f7".toColorInt(), map[KeyboardTheme.COLOR_ACCENT])
        assertEquals("#1e1e2e".toColorInt(), map[KeyboardTheme.COLOR_BACKGROUND])
        assertEquals("#313244".toColorInt(), map[KeyboardTheme.COLOR_KEYS])
        assertEquals("#45475a".toColorInt(), map[KeyboardTheme.COLOR_FUNCTIONAL_KEYS])
        assertEquals("#313244".toColorInt(), map[KeyboardTheme.COLOR_SPACEBAR])
        assertEquals("#cdd6f4".toColorInt(), map[KeyboardTheme.COLOR_TEXT])
        assertEquals("#a6adc8".toColorInt(), map[KeyboardTheme.COLOR_HINT_TEXT])
    }

    @Test
    fun catppuccinLatteMapping() {
        val map = presetColorMap(KeyboardTheme.THEME_CATPPUCCIN_LATTE)
        assertEquals("#8839ef".toColorInt(), map[KeyboardTheme.COLOR_ACCENT])
        assertEquals("#eff1f5".toColorInt(), map[KeyboardTheme.COLOR_BACKGROUND])
        assertEquals("#e6e9ef".toColorInt(), map[KeyboardTheme.COLOR_KEYS])
        assertEquals("#ccd0da".toColorInt(), map[KeyboardTheme.COLOR_FUNCTIONAL_KEYS])
        assertEquals("#e6e9ef".toColorInt(), map[KeyboardTheme.COLOR_SPACEBAR])
        assertEquals("#4c4f69".toColorInt(), map[KeyboardTheme.COLOR_TEXT])
        assertEquals("#6c6f85".toColorInt(), map[KeyboardTheme.COLOR_HINT_TEXT])
    }

    @Test
    fun availableDefaultColorsOnlyExpectedThemes() {
        val dayPresets = KeyboardTheme.getAvailableDefaultColors(prefs, isNight = false)
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_GBOARD_DARK))
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_DYNAMIC))
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_CLOUDY))
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_CATPPUCCIN_MOCHA))
        assertEquals(7, dayPresets.size, "Only the kept themes plus Catppuccin should be listed")
        assertFalse(dayPresets.contains("light"))
        assertFalse(dayPresets.contains("dark"))
        assertFalse(dayPresets.contains("forest"))
        assertFalse(dayPresets.contains("gboard"))

        val nightPresets = KeyboardTheme.getAvailableDefaultColors(prefs, isNight = true)
        assertEquals(dayPresets, nightPresets, "Day and night must expose the same themes")
    }

    @Test
    fun defaultColorsBackwardCompatibility() {
        val colors = DefaultColors(
            themeStyle = KeyboardTheme.STYLE_ROUNDED,
            hasKeyBorders = true,
            accent = Color.BLUE,
            background = Color.GRAY,
            keyBackground = Color.WHITE,
            functionalKey = Color.LTGRAY,
            spaceBar = Color.WHITE,
            keyText = Color.BLACK,
            keyHintText = Color.DKGRAY,
        )
        // actionKeyIcon not specified -> fallback to Color.WHITE
        assertEquals(Color.WHITE, colors.get(ColorType.ACTION_KEY_ICON))
    }

    @Test
    fun gboardContrastCompliance() {
        // WCAG AA contrast threshold: 4.5:1 for normal text, 3:1 for large text / components
        fun lum(color: Int): Double {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            fun chan(c: Double) = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            return 0.2126 * chan(r) + 0.7152 * chan(g) + 0.0722 * chan(b)
        }
        fun contrast(c1: Int, c2: Int): Double {
            val l1 = lum(c1)
            val l2 = lum(c2)
            return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
        }

        val dark = KeyboardTheme.getGboardDarkColors(KeyboardTheme.STYLE_ROUNDED, true)
        assertTrue(contrast(dark.get(ColorType.KEY_TEXT), dark.get(ColorType.KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(dark.get(ColorType.KEY_TEXT), dark.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(dark.get(ColorType.ACTION_KEY_ICON), dark.get(ColorType.ACTION_KEY_BACKGROUND)) >= 3.0)

        listOf(
            KeyboardTheme.THEME_CATPPUCCIN_LATTE,
            KeyboardTheme.THEME_CATPPUCCIN_FRAPPE,
            KeyboardTheme.THEME_CATPPUCCIN_MACCHIATO,
            KeyboardTheme.THEME_CATPPUCCIN_MOCHA,
        ).forEach { theme ->
            val settings = KeyboardTheme.getPresetColorSettings(theme, false, context)
            val colors = KeyboardTheme.readUserColorTheme(
                themeStyle = KeyboardTheme.STYLE_ROUNDED,
                hasBorders = false,
                colorSettings = settings,
                context = context,
                isNight = false,
                backgroundImage = null
            )
            assertTrue(
                contrast(colors.get(ColorType.KEY_TEXT), colors.get(ColorType.KEY_BACKGROUND)) >= 4.5,
                "$theme key text must contrast with key background"
            )
            assertTrue(
                contrast(colors.get(ColorType.KEY_TEXT), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)) >= 4.5,
                "$theme key text must contrast with functional key background"
            )
        }
    }

    @Test
    fun getPresetColorSettingsComplete() {
        listOf(
            KeyboardTheme.THEME_GBOARD_DARK,
            KeyboardTheme.THEME_CLOUDY,
            KeyboardTheme.THEME_CATPPUCCIN_LATTE,
            KeyboardTheme.THEME_CATPPUCCIN_FRAPPE,
            KeyboardTheme.THEME_CATPPUCCIN_MACCHIATO,
            KeyboardTheme.THEME_CATPPUCCIN_MOCHA,
        ).forEach { theme ->
            val settings = KeyboardTheme.getPresetColorSettings(theme, false, context)
            assertTrue(settings.isNotEmpty(), "$theme must expose preset color settings")
            settings.forEach {
                assertTrue(it.auto == false, "$theme setting ${it.name} should not be auto")
                assertTrue(it.color != null, "$theme setting ${it.name} color should not be null")
            }
        }
    }

    @Test
    fun readUserColorThemeWithCustomFunctionalKeys() {
        val presetSettings = KeyboardTheme.getPresetColorSettings(KeyboardTheme.THEME_CATPPUCCIN_MOCHA, false, context)
        val colors = KeyboardTheme.readUserColorTheme(
            themeStyle = KeyboardTheme.STYLE_ROUNDED,
            hasBorders = false,
            colorSettings = presetSettings,
            context = context,
            isNight = false,
            backgroundImage = null
        )
        assertEquals("#cba6f7".toColorInt(), colors.get(ColorType.ACTION_KEY_BACKGROUND))
        assertEquals("#45475a".toColorInt(), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
    }
}
