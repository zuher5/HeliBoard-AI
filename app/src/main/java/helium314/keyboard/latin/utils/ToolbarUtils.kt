// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import androidx.core.view.forEach
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.EnumMap
import java.util.Locale

fun createToolbarKey(context: Context, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER_INSIDE
    val padding = 7.dpToPx(context.resources)
    button.setPadding(padding, padding, padding, padding)
    button.tag = key
    button.contentDescription = key.name.lowercase().getStringResourceOrName("", context)
    button.setBackgroundResource(R.drawable.toolbar_key_background)
    button.elevation = 2.dpToPx(context.resources).toFloat()

    val index = if (key.name.startsWith("CUSTOM_AI_")) {
        key.name.removePrefix("CUSTOM_AI_").toIntOrNull()
    } else null
    val showTags = context.prefs().getBoolean("pref_custom_ai_show_tags_on_toolbar", false)
    val tag = if (index != null) {
        context.prefs().getString("pref_custom_ai_tag_$index", "") ?: ""
    } else ""

    val rawDrawable = if (showTags && tag.isNotBlank()) {
        TagDrawable(tag.take(3).uppercase(Locale.US))
    } else {
        KeyboardIconsSet.instance.getNewDrawable(key.name, context)
    }
    button.setImageDrawable(rawDrawable)
    setToolbarButtonActivatedState(button)
    return button
}

class TagDrawable(private val text: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var tintList: ColorStateList? = null
    private var tintMode: PorterDuff.Mode = PorterDuff.Mode.MULTIPLY
    private var tintFilter: ColorFilter? = null
    private var internalColorFilter: ColorFilter? = null

    override fun setTintList(tint: ColorStateList?) {
        tintList = tint
        updateTintFilter()
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        this.tintMode = tintMode ?: PorterDuff.Mode.MULTIPLY
        updateTintFilter()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        internalColorFilter = colorFilter
        updateTintFilter()
        invalidateSelf()
    }

    override fun onStateChange(state: IntArray): Boolean {
        if (tintList != null) {
            updateTintFilter()
            invalidateSelf()
            return true
        }
        return super.onStateChange(state)
    }

    override fun isStateful(): Boolean {
        return tintList?.isStateful == true || super.isStateful()
    }

    private fun updateTintFilter() {
        val colors = tintList
        if (colors != null) {
            val color = colors.getColorForState(state, Color.WHITE)
            tintFilter = PorterDuffColorFilter(color, tintMode)
        } else {
            tintFilter = null
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        val activeFilter = tintFilter ?: internalColorFilter
        paint.colorFilter = activeFilter
        paint.textSize = bounds.height() * 0.37f
        val textHeight = paint.descent() - paint.ascent()
        val textOffset = textHeight / 2 - paint.descent()
        canvas.drawText(text, cx, cy + textOffset, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun setToolbarButtonsActivatedStateOnPrefChange(buttonsGroup: ViewGroup, key: String?) {
    // settings need to be updated when buttons change
    if (key != Settings.PREF_AUTO_CORRECTION
        && key != Settings.PREF_ALWAYS_INCOGNITO_MODE
        && key != GestureDataGatheringSettings.PREF_BACKGROUND_GATHERING_ENABLED
        && key != GestureDataGatheringSettings.PREF_BACKGROUND_DISABLED_BEFORE_TIME_MILLIS
        && key?.startsWith(Settings.PREF_ONE_HANDED_MODE_PREFIX) == false)
        return

    GlobalScope.launch {
        delay(10) // need to wait until SettingsValues are reloaded
        buttonsGroup.forEach { if (it is ImageButton) setToolbarButtonActivatedState(it) }
    }
}

fun setToolbarButtonActivatedState(button: ImageButton) {
    val activated = when (button.tag) {
        INCOGNITO -> button.context.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
        ONE_HANDED -> Settings.getValues().mOneHandedModeEnabled
        SPLIT -> Settings.getValues().mIsSplitKeyboardEnabled
        AUTOCORRECT -> Settings.getValues().mAutoCorrectionEnabledPerUserSettings
        BACKGROUND_GATHERING -> useBackgroundGathering
        else -> true
    }
    button.isActivated = activated
    val colors = Settings.getValues().mColors
    if (button.background != null) {
        if (activated && button.tag in listOf(INCOGNITO, ONE_HANDED, SPLIT, AUTOCORRECT, BACKGROUND_GATHERING)) {
            colors.setColor(button.background, ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
            if (button.drawable != null) {
                colors.setColor(button, ColorType.ACTION_KEY_ICON)
            }
        } else {
            colors.setColor(button.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            if (button.drawable != null) {
                colors.setColor(button, ColorType.TOOL_BAR_KEY)
            }
        }
    }
}

fun getCodeForToolbarKey(key: ToolbarKey) = Settings.getInstance().getCustomToolbarKeyCode(key) ?: when (key) {
    VOICE -> KeyCode.VOICE_INPUT
    CLIPBOARD -> KeyCode.CLIPBOARD
    NUMPAD -> KeyCode.NUMPAD
    DPAD -> KeyCode.DPAD
    UNDO -> KeyCode.UNDO
    REDO -> KeyCode.REDO
    SETTINGS -> KeyCode.SETTINGS
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_ALL
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
    COPY -> KeyCode.CLIPBOARD_COPY
    CUT -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD_PASTE
    ONE_HANDED -> KeyCode.TOGGLE_ONE_HANDED_MODE
    INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
    AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
    CLEAR_CLIPBOARD -> KeyCode.CLIPBOARD_CLEAR_HISTORY
    CLOSE_HISTORY -> KeyCode.CLIPBOARD
    EMOJI -> KeyCode.EMOJI
    LEFT -> KeyCode.ARROW_LEFT
    RIGHT -> KeyCode.ARROW_RIGHT
    UP -> KeyCode.ARROW_UP
    DOWN -> KeyCode.ARROW_DOWN
    WORD_LEFT -> KeyCode.WORD_LEFT
    WORD_RIGHT -> KeyCode.WORD_RIGHT
    PAGE_UP -> KeyCode.PAGE_UP
    PAGE_DOWN -> KeyCode.PAGE_DOWN
    FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
    FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_START -> KeyCode.MOVE_START_OF_PAGE
    PAGE_END -> KeyCode.MOVE_END_OF_PAGE
    SPLIT -> KeyCode.SPLIT_LAYOUT
    FLOATING -> KeyCode.TOGGLE_FLOATING_WINDOW
    BACKGROUND_GATHERING -> KeyCode.BACKGROUND_GATHERING
    TRANSLATE -> KeyCode.TRANSLATE
    CUSTOM_AI_1 -> KeyCode.CUSTOM_AI_1
    CUSTOM_AI_2 -> KeyCode.CUSTOM_AI_2
    CUSTOM_AI_3 -> KeyCode.CUSTOM_AI_3
    CUSTOM_AI_4 -> KeyCode.CUSTOM_AI_4
    CUSTOM_AI_5 -> KeyCode.CUSTOM_AI_5
    CUSTOM_AI_6 -> KeyCode.CUSTOM_AI_6
    CUSTOM_AI_7 -> KeyCode.CUSTOM_AI_7
    CUSTOM_AI_8 -> KeyCode.CUSTOM_AI_8
    CUSTOM_AI_9 -> KeyCode.CUSTOM_AI_9
    CUSTOM_AI_10 -> KeyCode.CUSTOM_AI_10
}

fun getCodeForToolbarKeyLongClick(key: ToolbarKey) = Settings.getInstance().getCustomToolbarLongpressCode(key) ?: when (key) {
    CLIPBOARD -> KeyCode.CLIPBOARD_PASTE
    UNDO -> KeyCode.REDO
    REDO -> KeyCode.UNDO
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_WORD
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_ALL
    COPY -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD
    LEFT -> KeyCode.KEY_REPEAT
    RIGHT -> KeyCode.KEY_REPEAT
    UP -> KeyCode.KEY_REPEAT
    DOWN -> KeyCode.KEY_REPEAT
    WORD_LEFT -> KeyCode.KEY_REPEAT
    WORD_RIGHT -> KeyCode.KEY_REPEAT
    PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
    PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
    BACKGROUND_GATHERING -> KeyCode.BACKGROUND_GATHERING_TEMP_OFF
    TRANSLATE -> KeyCode.SHOW_TRANSLATE_LANGUAGES
    else -> KeyCode.UNSPECIFIED
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, NUMPAD, DPAD, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, CUT, PASTE, ONE_HANDED, FLOATING, SPLIT,
    INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD, CLOSE_HISTORY, EMOJI, LEFT, RIGHT, UP, DOWN, WORD_LEFT, WORD_RIGHT,
    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END, BACKGROUND_GATHERING,
    TRANSLATE,
    CUSTOM_AI_1, CUSTOM_AI_2, CUSTOM_AI_3, CUSTOM_AI_4, CUSTOM_AI_5,
    CUSTOM_AI_6, CUSTOM_AI_7, CUSTOM_AI_8, CUSTOM_AI_9, CUSTOM_AI_10
}

enum class ToolbarMode {
    EXPANDABLE, TOOLBAR_KEYS, SUGGESTION_STRIP, HIDDEN,
}

val toolbarKeyStrings = entries.associateWithTo(EnumMap(ToolbarKey::class.java)) { it.toString().lowercase(Locale.US) }

val defaultToolbarPref by lazy {
    val default = listOf(SETTINGS, VOICE, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT)
    val others = entries.filterNot { it in default || it == CLOSE_HISTORY }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

val defaultPinnedToolbarPref = entries.filterNot { it == CLOSE_HISTORY }.joinToString(Separators.ENTRY) {
    it.name + Separators.KV + false
}

val defaultClipboardToolbarPref by lazy {
    val default = listOf(CLEAR_CLIPBOARD, UP, DOWN, LEFT, RIGHT, UNDO, CUT, COPY, PASTE, SELECT_WORD, CLOSE_HISTORY)
    val others = entries.filterNot { it in default }
    default.joinToString(Separators.ENTRY) { it.name + Separators.KV + true } + Separators.ENTRY +
            others.joinToString(Separators.ENTRY) { it.name + Separators.KV + false }
}

/** add missing keys, typically because a new key has been added */
fun upgradeToolbarPrefs(prefs: SharedPreferences) {
    upgradeToolbarPref(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)
}

private fun upgradeToolbarPref(prefs: SharedPreferences, pref: String, default: String) {
    if (!prefs.contains(pref)) return
    val list = prefs.getString(pref, default)!!.split(Separators.ENTRY).toMutableList()
    val splitDefault = defaultToolbarPref.split(Separators.ENTRY)
    splitDefault.forEach { entry ->
        val keyWithSeparator = entry.substringBefore(Separators.KV) + Separators.KV
        if (list.none { it.startsWith(keyWithSeparator) })
            list.add("${keyWithSeparator}false")
    }
    // likely not needed, but better prepare for possibility of key removal
    list.removeAll {
        try {
            ToolbarKey.valueOf(it.substringBefore(Separators.KV))
            false
        } catch (_: IllegalArgumentException) {
            true
        }
    }
    prefs.edit { putString(pref, list.joinToString(Separators.ENTRY)) }
}

fun getEnabledToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)

fun getPinnedToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)

fun getEnabledClipboardToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)

fun addPinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // remove the existing version of this key and add the enabled one after the last currently enabled key
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val keys = string.split(Separators.ENTRY).toMutableList()
    keys.removeAll { it.startsWith(key.name + Separators.KV) }
    val lastEnabledIndex = keys.indexOfLast { it.endsWith("true") }
    keys.add(lastEnabledIndex + 1, key.name + Separators.KV + "true")
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, keys.joinToString(Separators.ENTRY)) }
}

fun removePinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // just set it to disabled
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val result = string.split(Separators.ENTRY).joinToString(Separators.ENTRY) {
        if (it.startsWith(key.name + Separators.KV))
            key.name + Separators.KV + "false"
        else it
    }
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, result) }
}

private fun getEnabledToolbarKeys(prefs: SharedPreferences, pref: String, default: String): List<ToolbarKey> {
    val string = prefs.getString(pref, default)!!
    return string.split(Separators.ENTRY).mapNotNull {
        val split = it.split(Separators.KV)
        if (split.last() == "true") {
            try {
                ToolbarKey.valueOf(split.first())
            } catch (_: IllegalArgumentException) {
                null
            }
        } else null
    }
}

fun writeCustomKeyCodes(prefs: SharedPreferences, codes: EnumMap<ToolbarKey, Pair<Int?, Int?>>) {
    val string = codes.mapNotNull { entry -> entry.value?.let { "${entry.key.name},${it.first},${it.second}" } }.joinToString(";")
    prefs.edit { putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, string) }
}

fun readCustomKeyCodes(prefs: SharedPreferences): EnumMap<ToolbarKey, Pair<Int?, Int?>> {
    val map = EnumMap<ToolbarKey, Pair<Int?, Int?>>(ToolbarKey::class.java)
    prefs.getString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, Defaults.PREF_TOOLBAR_CUSTOM_KEY_CODES)!!
        .split(";").forEach {
            runCatching {
                val s = it.split(",")
                map[ToolbarKey.valueOf(s[0])] = s[1].toIntOrNull() to s[2].toIntOrNull()
            }
        }
    return map
}

fun getCustomKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    if (customToolbarKeyCodes == null)
        customToolbarKeyCodes = readCustomKeyCodes(prefs)
    return customToolbarKeyCodes!![key]?.first
}

fun getCustomLongpressKeyCode(key: ToolbarKey, prefs: SharedPreferences): Int? {
    if (customToolbarKeyCodes == null)
        customToolbarKeyCodes = readCustomKeyCodes(prefs)
    return customToolbarKeyCodes!![key]?.second
}

fun clearCustomToolbarKeyCodes() {
    customToolbarKeyCodes = null
}

fun onClickToolbarKey(view: View, onCodeInput: (Int) -> Unit) {
    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_PRESS)
    val code = getCodeForToolbarKey(view.tag as ToolbarKey)
    if (code != KeyCode.UNSPECIFIED) {
        onCodeInput(code)
    }
}

fun onLongClickToolbarKey(view: View, onCodeInput: (Int, Boolean) -> Unit) {
    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_LONG_PRESS)
    val longClickCode = getCodeForToolbarKeyLongClick(view.tag as ToolbarKey)
    if (longClickCode == KeyCode.KEY_REPEAT) {
        onClickToolbarKey(view) { onCodeInput(it, false) }
        repeatToolbarKey(view) { onClickToolbarKey(view) { onCodeInput(it, true) } }
    } else if (longClickCode != KeyCode.UNSPECIFIED) {
        onCodeInput(longClickCode, false)
    }
}

private fun repeatToolbarKey(view: View, onClick: (view: View) -> Unit) {
    view.handler.postDelayed({
        if (view.isPressed) {
            onClick(view)
            repeatToolbarKey(view, onClick)
        }
    }, view.resources.getInteger(R.integer.config_key_repeat_interval).toLong())
}

private var customToolbarKeyCodes: EnumMap<ToolbarKey, Pair<Int?, Int?>>? = null
