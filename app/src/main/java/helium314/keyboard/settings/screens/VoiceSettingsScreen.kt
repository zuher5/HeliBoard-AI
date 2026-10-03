/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard-AI (online-only voice input)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.voice.VoiceConstants
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
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
            PreferenceCategory(stringResource(R.string.voice_settings_title))

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
                        default = VoiceConstants.VOICE_PROVIDER_DEFAULT
                    )
                }
            }
            providerSetting.Preference()

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

            Text(
                text = "Online voice input sends recorded audio to the configured AI provider (Settings → AI Integration). Provider \"System\" switches to the system voice IME (e.g. Gboard).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
