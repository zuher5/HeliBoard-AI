/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard-AI (online voice input only)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.voice

object VoiceConstants {
    // Voice provider: online (Groq Whisper) or system (switch to shortcut IME e.g. Gboard voice)
    const val PREF_VOICE_PROVIDER = "voice_provider"
    const val VOICE_PROVIDER_ONLINE = "online"
    const val VOICE_PROVIDER_SYSTEM = "system"
    const val VOICE_PROVIDER_DEFAULT = VOICE_PROVIDER_ONLINE

    const val PREF_VOICE_LANGUAGE = "voice_language"
    const val VOICE_LANG_FOLLOW_KEYBOARD = "follow_keyboard"
    const val VOICE_LANG_AUTO = "auto"

    const val PREF_VOICE_SMART_PUNCTUATION = "voice_smart_punctuation"
    const val PREF_VOICE_MAX_DURATION_SECONDS = "voice_max_duration_seconds"
    const val VOICE_MAX_DURATION_DEFAULT_SECONDS = 60
}
