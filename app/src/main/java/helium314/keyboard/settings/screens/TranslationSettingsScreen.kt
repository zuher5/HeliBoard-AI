// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ProofreadService
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ListPreference

private const val KEY_AI_TRANSLATE_MODEL = "ai_translate_model"

@Composable
fun TranslationSettingsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    val b = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val service = remember { ProofreadService(context) }
    val securePrefs = service.getSecurePrefs()
    // secure prefs changes don't trigger the settings prefChanged flow, so we observe them directly
    var refreshToken by remember { mutableStateOf(0) }
    DisposableEffect(securePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshToken++ }
        securePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { securePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (refreshToken < 0)
        Log.v("irrelevant", "recompose on secure preference change")

    val items = listOf(
        Settings.PREF_TRANSLATION_TARGET_LANGUAGE,
        KEY_AI_TRANSLATE_MODEL,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.translation_settings),
        settings = items
    )
}

fun createTranslationSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_TRANSLATION_TARGET_LANGUAGE, R.string.translation_target_language) {
        val names = context.resources.getStringArray(R.array.translate_language_names)
        val codes = context.resources.getStringArray(R.array.translate_language_codes)
        ListPreference(it, names.zip(codes), default = Defaults.PREF_TRANSLATION_TARGET_LANGUAGE)
    },
    Setting(context, KEY_AI_TRANSLATE_MODEL, R.string.ai_translate_model_name, R.string.ai_translate_model_name_summary) { setting ->
        val service = remember { ProofreadService(context) }
        SecureTextInputPreference(
            title = setting.title,
            description = service.getTranslateModelName().takeIf { it.isNotBlank() }
                ?: stringResource(R.string.ai_translate_model_auto),
            onGet = { service.getTranslateModelName() },
            onSet = { service.setTranslateModelName(it) },
            onReset = { service.setTranslateModelName("") },
        )
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        TranslationSettingsScreen(onClickBack = {})
    }
}