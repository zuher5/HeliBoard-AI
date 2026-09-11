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
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.ProofreadService
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SliderPreference

private const val KEY_AI_API_KEY = "ai_api_key"
private const val KEY_AI_MODEL = "ai_model"
private const val KEY_AI_ENDPOINT = "ai_endpoint"

@Composable
fun AIIntegrationScreen(
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

    val provider = service.getProvider()
    val isOpenAI = provider == ProofreadService.AiProvider.OPENAI

    val items = listOfNotNull(
        Settings.PREF_AI_PROVIDER,
        KEY_AI_API_KEY,
        KEY_AI_MODEL,
        if (isOpenAI) KEY_AI_ENDPOINT else null,
        Settings.PREF_TRANSLATION_TARGET_LANGUAGE,
        Settings.PREF_CLOUD_AI_MAX_TOKENS,
        SettingsWithoutKey.CUSTOM_AI_KEYS,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ai_integration_settings),
        settings = items
    )
}

fun createAISettings(context: Context) = listOf(
    Setting(context, Settings.PREF_AI_PROVIDER, R.string.ai_provider) { setting ->
        val service = remember { ProofreadService(context) }
        val items = listOf(
            stringResource(R.string.ai_provider_gemini) to ProofreadService.AiProvider.GEMINI.name,
            stringResource(R.string.ai_provider_openai) to ProofreadService.AiProvider.OPENAI.name,
        )
        ListPreference(
            setting,
            items,
            ProofreadService.AiProvider.GEMINI.name,
        )
    },
    Setting(context, KEY_AI_API_KEY, R.string.api_key_label) { setting ->
        val service = remember { ProofreadService(context) }
        val provider = service.getProvider()
        val key = service.getApiKey(provider)
        val hint = when (provider) {
            ProofreadService.AiProvider.GEMINI -> "AIzaSy..."
            ProofreadService.AiProvider.OPENAI -> "sk-... / hf_..."
        }
        SecureTextInputPreference(
            title = setting.title,
            description = ProofreadService.formatApiKeySummary(key, stringResource(R.string.ai_key_not_set)),
            onGet = { service.getApiKey(service.getProvider()) },
            onSet = { service.setApiKey(service.getProvider(), it) },
            onReset = { service.setApiKey(service.getProvider(), null) },
            info = hint,
        )
    },
    Setting(context, KEY_AI_MODEL, R.string.ai_model_name) { setting ->
        val service = remember { ProofreadService(context) }
        val provider = service.getProvider()
        val hint = when (provider) {
            ProofreadService.AiProvider.GEMINI -> "e.g. gemini-2.5-flash, gemini-2.0-flash"
            ProofreadService.AiProvider.OPENAI -> "e.g. Qwen/Qwen2.5-72B-Instruct, gpt-4o-mini"
        }
        SecureTextInputPreference(
            title = setting.title,
            description = service.getModelName(provider).takeIf { it.isNotBlank() }
                ?: stringResource(R.string.ai_model_default, ProofreadService.defaultModel(provider)),
            onGet = { service.getModelName(service.getProvider()) },
            onSet = { service.setModelName(service.getProvider(), it) },
            onReset = { service.setModelName(service.getProvider(), "") },
            info = hint,
        )
    },
    Setting(context, KEY_AI_ENDPOINT, R.string.ai_openai_endpoint, R.string.ai_openai_endpoint_summary) { setting ->
        val service = remember { ProofreadService(context) }
        SecureTextInputPreference(
            title = setting.title,
            description = service.getEndpoint(),
            onGet = { service.getEndpoint() },
            onSet = { service.setEndpoint(it) },
            onReset = { service.setEndpoint(null) },
            info = "e.g. https://api.openai.com/v1, https://api.huggingface.co/v1, http://192.168.1.100:11434/v1",
        )
    },
    Setting(context, Settings.PREF_TRANSLATION_TARGET_LANGUAGE, R.string.translation_target_language) {
        val names = context.resources.getStringArray(R.array.translate_language_names)
        val codes = context.resources.getStringArray(R.array.translate_language_codes)
        ListPreference(it, names.zip(codes), default = Defaults.PREF_TRANSLATION_TARGET_LANGUAGE)
    },
    Setting(context, Settings.PREF_CLOUD_AI_MAX_TOKENS, R.string.ai_cloud_max_tokens, R.string.ai_cloud_max_tokens_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_CLOUD_AI_MAX_TOKENS,
            range = 128f..8192f,
            stepSize = 64,
            description = { it.toInt().toString() }
        )
    },
    Setting(context, SettingsWithoutKey.CUSTOM_AI_KEYS, R.string.custom_ai_keys_title, R.string.custom_ai_keys_summary) {
        Preference(
            name = stringResource(R.string.custom_ai_keys_title),
            description = stringResource(R.string.custom_ai_keys_summary),
            onClick = { SettingsDestination.navigateTo(SettingsDestination.CustomAIKeys) },
        ) { NextScreenIcon() }
    },
)

/** Text input preference that reads/writes through [ProofreadService] (secure prefs) */
@Composable
fun SecureTextInputPreference(
    title: String,
    description: String?,
    onGet: () -> String?,
    onSet: (String) -> Unit,
    onReset: () -> Unit,
    info: String? = null,
) {
    var showDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Preference(
        name = title,
        description = description,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        TextInputDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = {
                if (it.isNotBlank()) onSet(it)
                showDialog = false
            },
            initialText = onGet() ?: "",
            title = { androidx.compose.material3.Text(title) },
            placeholder = if (info == null) null else { { androidx.compose.material3.Text(info) } },
            singleLine = true,
            onNeutral = { onReset(); showDialog = false },
            neutralButtonText = stringResource(R.string.button_default),
        )
    }
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        AIIntegrationScreen(onClickBack = {})
    }
}
