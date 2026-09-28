/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.common.DynamicColors
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.brightenOrDarken
import helium314.keyboard.latin.utils.isBrightColor
import helium314.keyboard.latin.utils.isGoodContrast
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.EnumMap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.ColorUtils

class KeyboardTheme // Note: The themeId should be aligned with "themeId" attribute of Keyboard style in values/themes-<style>.xml.
private constructor(val themeId: Int, @JvmField val mStyleId: Int) {
    override fun equals(other: Any?) = if (other === this) true
        else (other as? KeyboardTheme)?.themeId == themeId

    override fun hashCode(): Int {
        return themeId
    }

    companion object {
        // old themes, now called styles
        const val STYLE_MATERIAL = "Material"
        const val STYLE_HOLO = "Holo"
        const val STYLE_ROUNDED = "Rounded"

        // new themes that are just colors
        const val THEME_GBOARD_DARK = "gboard_dark"
        const val THEME_DYNAMIC = "dynamic"
        const val THEME_CLOUDY = "cloudy"
        const val THEME_CATPPUCCIN_LATTE = "catppuccin_latte"
        const val THEME_CATPPUCCIN_FRAPPE = "catppuccin_frappe"
        const val THEME_CATPPUCCIN_MACCHIATO = "catppuccin_macchiato"
        const val THEME_CATPPUCCIN_MOCHA = "catppuccin_mocha"
        fun getAvailableDefaultColors(prefs: SharedPreferences, isNight: Boolean) = listOfNotNull(
            THEME_GBOARD_DARK,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) THEME_DYNAMIC else null,
            THEME_CLOUDY,
            THEME_CATPPUCCIN_LATTE,
            THEME_CATPPUCCIN_FRAPPE,
            THEME_CATPPUCCIN_MACCHIATO,
            THEME_CATPPUCCIN_MOCHA
        )
        val STYLES = arrayOf(STYLE_MATERIAL, STYLE_HOLO, STYLE_ROUNDED)

        // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
        // attributes' values in attrs.xml.
        private const val THEME_ID_HOLO_BASE = 0
        private const val THEME_ID_LXX_BASE = 1
        private const val THEME_ID_LXX_BASE_BORDER = 2
        private const val THEME_ID_ROUNDED_BASE = 3
        private const val THEME_ID_ROUNDED_BASE_BORDER = 4
        private const val DEFAULT_THEME_ID = THEME_ID_LXX_BASE

        private val KEYBOARD_THEMES = arrayOf(
            KeyboardTheme(THEME_ID_HOLO_BASE, R.style.KeyboardTheme_HoloBase),
            KeyboardTheme(THEME_ID_LXX_BASE, R.style.KeyboardTheme_LXX_Base),
            KeyboardTheme(THEME_ID_LXX_BASE_BORDER, R.style.KeyboardTheme_LXX_Base_Border),
            KeyboardTheme(THEME_ID_ROUNDED_BASE, R.style.KeyboardTheme_Rounded_Base),
            KeyboardTheme(THEME_ID_ROUNDED_BASE_BORDER, R.style.KeyboardTheme_Rounded_Base_Border)
        )

        // named colors, with names from old settings
        const val COLOR_ACCENT = "accent"
        const val COLOR_GESTURE = "gesture"
        const val COLOR_SUGGESTION_TEXT = "suggestion_text"
        const val COLOR_TEXT = "text"
        const val COLOR_HINT_TEXT = "hint_text"
        const val COLOR_KEYS = "keys"
        const val COLOR_FUNCTIONAL_KEYS = "functional_keys"
        const val COLOR_SPACEBAR = "spacebar"
        const val COLOR_SPACEBAR_TEXT = "spacebar_text"
        const val COLOR_BACKGROUND = "background"

        @JvmStatic
        fun getKeyboardTheme(context: Context): KeyboardTheme {
            val prefs = context.prefs()
            val style = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)
            val borders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
            val matchingId = when (style) {
                STYLE_HOLO -> THEME_ID_HOLO_BASE
                STYLE_ROUNDED -> if (borders) THEME_ID_ROUNDED_BASE_BORDER else THEME_ID_ROUNDED_BASE
                else -> if (borders) THEME_ID_LXX_BASE_BORDER else THEME_ID_LXX_BASE
            }
            return KEYBOARD_THEMES.firstOrNull { it.themeId == matchingId } ?: KEYBOARD_THEMES[DEFAULT_THEME_ID]
        }

        fun getThemeActionAndEmojiKeyLabelFlags(themeId: Int): Int {
            return if (themeId == THEME_ID_LXX_BASE || themeId == THEME_ID_ROUNDED_BASE) Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO else 0
        }

        @JvmStatic
        fun getColorsForCurrentTheme(context: Context): Colors {
            val prefs = context.prefs()
            val isNight = SettingsActivity.forceNight
                ?: (ResourceUtils.isNight(context.resources) && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT))
            val themeName = SettingsActivity.forceTheme
                ?: prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)
            val themeStyle = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)

            return getThemeColors(themeName!!, themeStyle!!, context, prefs, isNight)
        }

        private fun getThemeColors(themeName: String, themeStyle: String, context: Context, prefs: SharedPreferences, isNight: Boolean): Colors {
            val hasBorders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
            val backgroundImage = Settings.readUserBackgroundImage(context, isNight)
            return when (themeName) {
                THEME_GBOARD_DARK -> getGboardDarkColors(themeStyle, hasBorders, backgroundImage)
                THEME_DYNAMIC -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicColors(context, themeStyle, hasBorders, backgroundImage)
                    else getGboardDarkColors(themeStyle, hasBorders, backgroundImage)
                }
                THEME_CATPPUCCIN_LATTE -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    "#8839ef".toColorInt(),
                    "#eff1f5".toColorInt(),
                    "#e6e9ef".toColorInt(),
                    "#ccd0da".toColorInt(),
                    "#e6e9ef".toColorInt(),
                    "#4c4f69".toColorInt(),
                    "#6c6f85".toColorInt(),
                    keyboardBackground = backgroundImage
                )
                THEME_CATPPUCCIN_FRAPPE -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    "#ca9ee6".toColorInt(),
                    "#303446".toColorInt(),
                    "#414559".toColorInt(),
                    "#51576d".toColorInt(),
                    "#292c3c".toColorInt(),
                    "#c6d0f5".toColorInt(),
                    "#a5adce".toColorInt(),
                    keyboardBackground = backgroundImage
                )
                THEME_CATPPUCCIN_MACCHIATO -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    "#c6a0f6".toColorInt(),
                    "#24273a".toColorInt(),
                    "#363a4f".toColorInt(),
                    "#494d64".toColorInt(),
                    "#1e2030".toColorInt(),
                    "#cad3f5".toColorInt(),
                    "#a5adcb".toColorInt(),
                    keyboardBackground = backgroundImage
                )
                THEME_CATPPUCCIN_MOCHA -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    "#cba6f7".toColorInt(),
                    "#1e1e2e".toColorInt(),
                    "#313244".toColorInt(),
                    "#45475a".toColorInt(),
                    "#181825".toColorInt(),
                    "#cdd6f4".toColorInt(),
                    "#a6adc8".toColorInt(),
                    keyboardBackground = backgroundImage
                )
                THEME_CLOUDY -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(255, 113, 129),
                    Color.rgb(81, 97, 113),
                    Color.rgb(117, 128, 142),
                    Color.rgb(99, 109, 121),
                    Color.rgb(117, 128, 142),
                    Color.WHITE,
                    Color.WHITE,
                    keyboardBackground = backgroundImage
                )
                else -> { // user-defined theme
                    val colorSettings = readUserColors(prefs, themeName)
                    val colors = readUserColorTheme(themeStyle, hasBorders, colorSettings, context, isNight, backgroundImage)
                    if (readUserMoreColors(prefs, themeName) == 2)
                        AllColors(readUserAllColors(prefs, themeName, colors), themeStyle, hasBorders, backgroundImage)
                    else {
                        colors
                    }
                }
            }
        }

        fun getGboardDarkColors(themeStyle: String, hasBorders: Boolean, backgroundImage: Drawable? = null) = DefaultColors(
            themeStyle = themeStyle,
            hasKeyBorders = hasBorders,
            accent = "#8AB4F8".toColorInt(),
            background = "#202124".toColorInt(),
            keyBackground = "#303134".toColorInt(),
            functionalKey = "#3C4043".toColorInt(),
            spaceBar = "#303134".toColorInt(),
            keyText = "#E8EAED".toColorInt(),
            keyHintText = "#9AA0A6".toColorInt(),
            suggestionText = "#E8EAED".toColorInt(),
            spaceBarText = "#9AA0A6".toColorInt(),
            gesture = "#8AB4F8".toColorInt(),
            keyboardBackground = backgroundImage,
            actionKeyIcon = "#202124".toColorInt(),
            keepFunctionalKeyWithoutBorders = true,
        )

        fun getPresetColorSettings(presetName: String, isNight: Boolean, context: Context): List<ColorSetting> {
            val colors = getThemeColors(presetName, STYLE_MATERIAL, context, context.prefs(), isNight)
            return listOf(
                ColorSetting(COLOR_ACCENT, false, colors.get(ColorType.ACTION_KEY_BACKGROUND)),
                ColorSetting(COLOR_BACKGROUND, false, colors.get(ColorType.MAIN_BACKGROUND)),
                ColorSetting(COLOR_KEYS, false, colors.get(ColorType.KEY_BACKGROUND)),
                ColorSetting(COLOR_FUNCTIONAL_KEYS, false, colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)),
                ColorSetting(COLOR_SPACEBAR, false, colors.get(ColorType.SPACE_BAR_BACKGROUND)),
                ColorSetting(COLOR_TEXT, false, colors.get(ColorType.KEY_TEXT)),
                ColorSetting(COLOR_HINT_TEXT, false, colors.get(ColorType.KEY_HINT_TEXT)),
                ColorSetting(COLOR_SUGGESTION_TEXT, false, colors.get(ColorType.SUGGESTION_AUTO_CORRECT)),
                ColorSetting(COLOR_SPACEBAR_TEXT, false, colors.get(ColorType.SPACE_BAR_TEXT)),
                ColorSetting(COLOR_GESTURE, false, colors.get(ColorType.GESTURE_TRAIL))
            )
        }

        fun readUserColorTheme(themeStyle: String, hasBorders: Boolean, colorSettings: List<ColorSetting>, context: Context, isNight: Boolean, backgroundImage: Drawable?): Colors {
            val accent = determineUserColor(colorSettings, context, COLOR_ACCENT, isNight)
            val functionalKey = determineUserColor(colorSettings, context, COLOR_FUNCTIONAL_KEYS, isNight)
            val hasCustomFunctionalKey = colorSettings.any { it.name == COLOR_FUNCTIONAL_KEYS && it.auto == false && it.color != null }
            val actionKeyIcon = if (ColorUtils.calculateContrast(Color.WHITE, accent) < ColorUtils.calculateContrast("#202124".toColorInt(), accent)) {
                "#202124".toColorInt()
            } else Color.WHITE
            return DefaultColors(
                themeStyle = themeStyle,
                hasKeyBorders = hasBorders,
                accent = accent,
                background = determineUserColor(colorSettings, context, COLOR_BACKGROUND, isNight),
                keyBackground = determineUserColor(colorSettings, context, COLOR_KEYS, isNight),
                functionalKey = functionalKey,
                spaceBar = determineUserColor(colorSettings, context, COLOR_SPACEBAR, isNight),
                keyText = determineUserColor(colorSettings, context, COLOR_TEXT, isNight),
                keyHintText = determineUserColor(colorSettings, context, COLOR_HINT_TEXT, isNight),
                suggestionText = determineUserColor(colorSettings, context, COLOR_SUGGESTION_TEXT, isNight),
                spaceBarText = determineUserColor(colorSettings, context, COLOR_SPACEBAR_TEXT, isNight),
                gesture = determineUserColor(colorSettings, context, COLOR_GESTURE, isNight),
                keyboardBackground = backgroundImage,
                actionKeyIcon = actionKeyIcon,
                keepFunctionalKeyWithoutBorders = hasCustomFunctionalKey,
            )
        }

        fun writeUserColors(prefs: SharedPreferences, themeName: String, colors: List<ColorSetting>) {
            val key = Settings.PREF_USER_COLORS_PREFIX + themeName
            val value = Json.encodeToString(colors.filter { it.color != null || it.auto == false })
            prefs.edit { putString(key, value) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }

        fun readUserColors(prefs: SharedPreferences, themeName: String): List<ColorSetting> {
            val key = Settings.PREF_USER_COLORS_PREFIX + themeName
            return Json.decodeFromString(prefs.getString(key, Defaults.PREF_USER_COLORS)!!)
        }

        fun writeUserMoreColors(prefs: SharedPreferences, themeName: String, value: Int) {
            val key = Settings.PREF_USER_MORE_COLORS_PREFIX + themeName
            prefs.edit { putInt(key, value) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }

        fun readUserMoreColors(prefs: SharedPreferences, themeName: String): Int {
            val key = Settings.PREF_USER_MORE_COLORS_PREFIX + themeName
            return prefs.getInt(key, Defaults.PREF_USER_MORE_COLORS)
        }

        fun writeUserAllColors(prefs: SharedPreferences, themeName: String, colorMap: EnumMap<ColorType, Int>) {
            val key = Settings.PREF_USER_ALL_COLORS_PREFIX + themeName
            prefs.edit { putString(key, colorMap.map { "${it.key},${it.value}" }.joinToString(";")) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }

        fun readUserAllColors(prefs: SharedPreferences, themeName: String, fallback: Colors?): EnumMap<ColorType, Int> {
            val key = Settings.PREF_USER_ALL_COLORS_PREFIX + themeName
            val colorsString = prefs.getString(key, Defaults.PREF_USER_ALL_COLORS)!!
            val colorMap = EnumMap<ColorType, Int>(ColorType::class.java)
            colorsString.split(";").forEach {
                val ct = try {
                    ColorType.valueOf(it.substringBefore(",").uppercase())
                } catch (_: IllegalArgumentException) {
                    return@forEach
                }
                val i = it.substringAfter(",").toIntOrNull() ?: return@forEach
                colorMap[ct] = i
            }
            if (fallback != null && colorMap.size < ColorType.entries.size) {
                ColorType.entries.forEach {
                    if (it in colorMap) return@forEach
                    colorMap[it] = fallback.get(it)
                }
            }
            return colorMap
        }

        fun getUnusedThemeName(initialName: String, prefs: SharedPreferences): String {
            val existingNames = getExistingThemeNames(prefs)
            if (initialName !in existingNames) return initialName
            var i = 1
            while ("$initialName$i" in existingNames)
                i++
            return "$initialName$i"
        }

        private fun getExistingThemeNames(prefs: SharedPreferences) =
            prefs.all.keys.mapNotNull {
                when {
                    it.startsWith(Settings.PREF_USER_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_COLORS_PREFIX)
                    it.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_ALL_COLORS_PREFIX)
                    it.startsWith(Settings.PREF_USER_MORE_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_MORE_COLORS_PREFIX)
                    else -> null
                }
            }.toSortedSet()

        // returns false if not renamed due to invalid name or collision
        fun renameUserColors(from: String, to: String, prefs: SharedPreferences): Boolean {
            if (to.isBlank()) return false // don't want that
            if (to == from) return true // nothing to do
            val existingNames = getExistingThemeNames(prefs)
            if (to in existingNames) return false
            // all good, now rename
            prefs.edit {
                if (prefs.contains(Settings.PREF_USER_COLORS_PREFIX + from)) {
                    putString(Settings.PREF_USER_COLORS_PREFIX + to, prefs.getString(Settings.PREF_USER_COLORS_PREFIX + from, ""))
                    remove(Settings.PREF_USER_COLORS_PREFIX + from)
                }
                if (prefs.contains(Settings.PREF_USER_ALL_COLORS_PREFIX + from)) {
                    putString(Settings.PREF_USER_ALL_COLORS_PREFIX + to, prefs.getString(Settings.PREF_USER_ALL_COLORS_PREFIX + from, ""))
                    remove(Settings.PREF_USER_ALL_COLORS_PREFIX + from)
                }
                if (prefs.contains(Settings.PREF_USER_MORE_COLORS_PREFIX + from)) {
                    putInt(Settings.PREF_USER_MORE_COLORS_PREFIX + to, prefs.getInt(Settings.PREF_USER_MORE_COLORS_PREFIX + from, 0))
                    remove(Settings.PREF_USER_MORE_COLORS_PREFIX + from)
                }
                if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == from)
                    putString(Settings.PREF_THEME_COLORS, to)
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == from)
                    putString(Settings.PREF_THEME_COLORS_NIGHT, to)
            }
            return true
        }

        fun determineUserColor(colors: List<ColorSetting>, context: Context, colorName: String, isNight: Boolean): Int {
            val c = colors.firstOrNull { it.name == colorName }
            val color = c?.color
            val auto = c?.auto ?: true
            return if (auto || color == null)
                determineAutoColor(colors, colorName, isNight, context)
            else color
        }

        private fun determineAutoColor(colors: List<ColorSetting>, colorName: String, isNight: Boolean, context: Context): Int {
            when (colorName) {
                COLOR_ACCENT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        // try determining accent color on Android 10 & 11, accent is not available in resources
                        val wrapper: Context = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
                        val value = TypedValue()
                        if (wrapper.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) return value.data
                    }
                    return ContextCompat.getColor(Settings.getDayNightContext(context, isNight), R.color.accent)
                }
                COLOR_GESTURE -> return determineUserColor(colors, context, COLOR_ACCENT, isNight)
                COLOR_SUGGESTION_TEXT ->
                    return determineUserColor(colors, context, COLOR_TEXT, isNight)
                COLOR_TEXT -> {
                    // base it on background color, and not key, because it's also used for suggestions
                    val background = determineUserColor(colors, context, COLOR_BACKGROUND, isNight)
                    return if (isBrightColor(background)) {
                        // but if key borders are enabled, we still want reasonable contrast
                        if (!context.prefs().getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
                            || isGoodContrast(Color.BLACK, determineUserColor(colors, context, COLOR_KEYS, isNight))
                        ) Color.BLACK
                        else Color.GRAY
                    } else Color.WHITE
                }
                COLOR_HINT_TEXT -> {
                    return if (isBrightColor(determineUserColor(colors, context, COLOR_KEYS, isNight))) Color.DKGRAY
                    else determineUserColor(colors, context, COLOR_TEXT, isNight)
                }
                COLOR_KEYS ->
                    return brightenOrDarken(determineUserColor(colors, context, COLOR_BACKGROUND, isNight), isNight)
                COLOR_FUNCTIONAL_KEYS ->
                    return brightenOrDarken(determineUserColor(colors, context, COLOR_KEYS, isNight), true)
                COLOR_SPACEBAR -> return determineUserColor(colors, context, COLOR_KEYS, isNight)
                COLOR_SPACEBAR_TEXT -> {
                    val spacebar = determineUserColor(colors, context, COLOR_SPACEBAR, isNight)
                    val hintText = determineUserColor(colors, context, COLOR_HINT_TEXT, isNight)
                    if (isGoodContrast(hintText, spacebar)) return hintText and -0x7f000001 // add some transparency
                    val text = determineUserColor(colors, context, COLOR_TEXT, isNight)
                    if (isGoodContrast(text, spacebar)) return text and -0x7f000001
                    return if (isBrightColor(spacebar)) Color.BLACK and -0x7f000001
                    else Color.WHITE and -0x7f000001
                }
                COLOR_BACKGROUND -> return ContextCompat.getColor(
                    Settings.getDayNightContext(context, isNight),
                    R.color.keyboard_background
                )
                else -> return ContextCompat.getColor(Settings.getDayNightContext(context, isNight), R.color.keyboard_background)
            }
        }
    }
}

@Serializable
data class ColorSetting(val name: String, val auto: Boolean?, val color: Int?) {
    var displayName = name
}
