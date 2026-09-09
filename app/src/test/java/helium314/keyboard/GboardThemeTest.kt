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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GboardThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)

    @Test
    fun gboardLightColorsMapping() {
        val colors = KeyboardTheme.getGboardLightColors(KeyboardTheme.STYLE_ROUNDED, true)
        assertEquals("#F7F7F7".toColorInt(), colors.get(ColorType.MAIN_BACKGROUND))
        assertEquals("#FFFFFF".toColorInt(), colors.get(ColorType.KEY_BACKGROUND))
        assertEquals("#E9E9E9".toColorInt(), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
        assertEquals("#FFFFFF".toColorInt(), colors.get(ColorType.SPACE_BAR_BACKGROUND))
        assertEquals("#AECBFA".toColorInt(), colors.get(ColorType.ACTION_KEY_BACKGROUND))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.ACTION_KEY_ICON))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.KEY_TEXT))
        assertEquals("#5F6368".toColorInt(), colors.get(ColorType.KEY_HINT_TEXT))
        assertEquals("#1A73E8".toColorInt(), colors.get(ColorType.GESTURE_TRAIL))
        assertEquals("#F7F7F7".toColorInt(), colors.get(ColorType.STRIP_BACKGROUND))
        assertEquals("#F7F7F7".toColorInt(), colors.get(ColorType.NAVIGATION_BAR))
    }

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
    fun gboardAvailableDefaultColors() {
        val dayPresets = KeyboardTheme.getAvailableDefaultColors(prefs, isNight = false)
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_GBOARD))
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_GBOARD_DARK))
        assertTrue(dayPresets.contains(KeyboardTheme.THEME_GBOARD_DYNAMIC))

        val nightPresets = KeyboardTheme.getAvailableDefaultColors(prefs, isNight = true)
        assertTrue(!nightPresets.contains(KeyboardTheme.THEME_GBOARD))
        assertTrue(nightPresets.contains(KeyboardTheme.THEME_GBOARD_DARK))
        assertTrue(nightPresets.contains(KeyboardTheme.THEME_GBOARD_DYNAMIC))
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

        val light = KeyboardTheme.getGboardLightColors(KeyboardTheme.STYLE_ROUNDED, true)
        assertTrue(contrast(light.get(ColorType.KEY_TEXT), light.get(ColorType.KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(light.get(ColorType.KEY_TEXT), light.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(light.get(ColorType.ACTION_KEY_ICON), light.get(ColorType.ACTION_KEY_BACKGROUND)) >= 3.0)

        val dark = KeyboardTheme.getGboardDarkColors(KeyboardTheme.STYLE_ROUNDED, true)
        assertTrue(contrast(dark.get(ColorType.KEY_TEXT), dark.get(ColorType.KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(dark.get(ColorType.KEY_TEXT), dark.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)) >= 4.5)
        assertTrue(contrast(dark.get(ColorType.ACTION_KEY_ICON), dark.get(ColorType.ACTION_KEY_BACKGROUND)) >= 3.0)
    }

    @Test
    fun getPresetColorSettingsComplete() {
        val gboardSettings = KeyboardTheme.getPresetColorSettings(KeyboardTheme.THEME_GBOARD, false, context)
        assertTrue(gboardSettings.isNotEmpty())
        gboardSettings.forEach {
            assertTrue(!it.auto, "Setting ${it.name} should not be auto")
            assertTrue(it.color != null, "Setting ${it.name} color should not be null")
        }

        val lightSettings = KeyboardTheme.getPresetColorSettings(KeyboardTheme.THEME_LIGHT, false, context)
        assertTrue(lightSettings.isNotEmpty())
        lightSettings.forEach {
            assertTrue(!it.auto, "Setting ${it.name} should not be auto")
            assertTrue(it.color != null, "Setting ${it.name} color should not be null")
        }
    }

    @Test
    fun readUserColorThemeWithCustomFunctionalKeys() {
        val presetSettings = KeyboardTheme.getPresetColorSettings(KeyboardTheme.THEME_GBOARD, false, context)
        val colors = KeyboardTheme.readUserColorTheme(
            themeStyle = KeyboardTheme.STYLE_ROUNDED,
            hasBorders = false,
            colorSettings = presetSettings,
            context = context,
            isNight = false,
            backgroundImage = null
        )
        assertEquals("#AECBFA".toColorInt(), colors.get(ColorType.ACTION_KEY_BACKGROUND))
        assertEquals("#202124".toColorInt(), colors.get(ColorType.ACTION_KEY_ICON))
        assertEquals("#E9E9E9".toColorInt(), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
    }
}
