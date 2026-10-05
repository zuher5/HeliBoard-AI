/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard-AI (online-only voice input)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ProofreadService
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.VoiceConstants
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.TextInputPreference

@Composable
fun VoiceSettingsScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefChanged = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val prefs = ctx.prefs()
    val service = remember { ProofreadService(ctx) }
    val securePrefs = service.getSecurePrefs()

    var isOnline by remember {
        mutableStateOf(
            prefs.getString(VoiceConstants.PREF_VOICE_PROVIDER, VoiceConstants.VOICE_PROVIDER_DEFAULT) == VoiceConstants.VOICE_PROVIDER_ONLINE
        )
    }
    var secureRefreshToken by remember { mutableStateOf(0) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == VoiceConstants.PREF_VOICE_PROVIDER) {
                isOnline = prefs.getString(VoiceConstants.PREF_VOICE_PROVIDER, VoiceConstants.VOICE_PROVIDER_DEFAULT) == VoiceConstants.VOICE_PROVIDER_ONLINE
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(securePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> secureRefreshToken++ }
        securePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { securePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.voice_settings_title),
        settings = emptyList()
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    PreferenceCategory(stringResource(R.string.voice_settings_title), showDivider = false)

                    // Provider: online (AI) or system (shortcut IME)
                    val providerItems = listOf(
                        stringResource(R.string.voice_provider_online) to VoiceConstants.VOICE_PROVIDER_ONLINE,
                        stringResource(R.string.voice_provider_system) to VoiceConstants.VOICE_PROVIDER_SYSTEM
                    )
                    val providerSetting = remember {
                        Setting(
                            ctx,
                            VoiceConstants.PREF_VOICE_PROVIDER,
                            R.string.voice_provider_title
                        ) { setting ->
                            ListPreference(
                                setting = setting,
                                items = providerItems,
                                default = VoiceConstants.VOICE_PROVIDER_DEFAULT,
                                onChanged = { isOnline = it == VoiceConstants.VOICE_PROVIDER_ONLINE }
                            )
                        }
                    }
                    providerSetting.Preference()

                    // Voice Model (Online only)
                    if (isOnline) {
                        val aiProvider = service.getProvider()
                        val defaultModel = ProofreadService.defaultVoiceModel(aiProvider)
                        val standardModels = ProofreadService.defaultVoiceModels(aiProvider)
                        val currentModel = remember(aiProvider, secureRefreshToken) { service.getVoiceModel(aiProvider) }
                        val customLabel = stringResource(R.string.voice_model_custom)
                        val pickerItems = standardModels + listOf(customLabel)

                        var showPickerDialog by remember { mutableStateOf(false) }
                        var showCustomDialog by remember { mutableStateOf(false) }

                        Preference(
                            name = stringResource(R.string.voice_model_title),
                            description = currentModel.takeIf { it.isNotBlank() } ?: defaultModel,
                            onClick = { showPickerDialog = true }
                        )

                        if (showPickerDialog) {
                            ListPickerDialog(
                                onDismissRequest = { showPickerDialog = false },
                                items = pickerItems,
                                selectedItem = if (currentModel in standardModels) currentModel else customLabel,
                                title = { Text(stringResource(R.string.voice_model_title)) },
                                getItemName = { it },
                                onItemSelected = { item ->
                                    showPickerDialog = false
                                    if (item == customLabel) {
                                        showCustomDialog = true
                                    } else {
                                        service.setVoiceModel(aiProvider, item)
                                        secureRefreshToken++
                                    }
                                },
                                onDefault = {
                                    showPickerDialog = false
                                    service.setVoiceModel(aiProvider, "")
                                    secureRefreshToken++
                                }
                            )
                        }

                        if (showCustomDialog) {
                            TextInputDialog(
                                onDismissRequest = { showCustomDialog = false },
                                onConfirmed = { input ->
                                    showCustomDialog = false
                                    val trimmed = input.trim()
                                    if (trimmed.isNotEmpty()) {
                                        service.setVoiceModel(aiProvider, trimmed)
                                        secureRefreshToken++
                                    }
                                },
                                title = { Text(stringResource(R.string.voice_model_title)) },
                                initialText = if (currentModel in standardModels) "" else currentModel,
                                placeholder = { Text(defaultModel) },
                                singleLine = true,
                                neutralButtonText = stringResource(R.string.button_default),
                                onNeutral = {
                                    showCustomDialog = false
                                    service.setVoiceModel(aiProvider, "")
                                    secureRefreshToken++
                                }
                            )
                        }
                    }

                    // Language
                    val languageItems = listOf(
                        stringResource(R.string.voice_language_follow_keyboard) to VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD,
                        stringResource(R.string.voice_language_auto) to VoiceConstants.VOICE_LANG_AUTO
                    )
                    val languageSetting = remember {
                        Setting(
                            ctx,
                            VoiceConstants.PREF_VOICE_LANGUAGE,
                            R.string.voice_language_title
                        ) { setting ->
                            ListPreference(
                                setting = setting,
                                items = languageItems,
                                default = VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD
                            )
                        }
                    }
                    languageSetting.Preference()

                    // Smart punctuation
                    val smartPunctSetting = remember {
                        Setting(
                            ctx,
                            VoiceConstants.PREF_VOICE_SMART_PUNCTUATION,
                            R.string.voice_smart_punctuation,
                            R.string.voice_smart_punctuation_summary
                        ) { setting ->
                            SwitchPreference(
                                setting,
                                default = true
                            )
                        }
                    }
                    smartPunctSetting.Preference()

                    // Max duration
                    val maxDurationSetting = remember {
                        Setting(
                            ctx,
                            VoiceConstants.PREF_VOICE_MAX_DURATION_SECONDS,
                            R.string.voice_max_duration_title
                        ) { setting ->
                            TextInputPreference(
                                setting = setting,
                                default = VoiceConstants.VOICE_MAX_DURATION_DEFAULT_SECONDS.toString()
                            )
                        }
                    }
                    maxDurationSetting.Preference()
                }
            }

            Text(
                text = "Online voice input sends recorded audio to the configured AI provider (Settings → AI Integration). Provider \"System\" switches to the system voice IME (e.g. Gboard).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
