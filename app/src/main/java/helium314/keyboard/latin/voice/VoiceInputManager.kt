/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard-AI (online-only voice input via Groq Whisper)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ProofreadService
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Online voice input: records PCM audio, converts to WAV, sends to the configured
 * AI provider (Groq Whisper / Gemini / OpenAI-compatible) for transcription.
 * Offline plugin paths from LeanType were intentionally removed.
 */
class VoiceInputManager(private val ims: LatinIME) {

    enum class VoiceState {
        IDLE,
        RECORDING,
        PROCESSING_FINAL,
        ERROR
    }

    interface VoiceInputListener {
        fun onStateChanged(state: VoiceState)
        fun onError(message: String)
    }

    private var state = VoiceState.IDLE
    private var activeSessionId: String? = null

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var audioThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var needsCapitalStart = true
    private var sessionEmittedText = ""

    private val onlineAudioBuffer = ByteArrayOutputStream()
    private var onlineTranscriptionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var listener: VoiceInputListener? = null

    fun setListener(listener: VoiceInputListener?) {
        this.listener = listener
    }

    fun getState(): VoiceState = state

    fun isRecording(): Boolean = state == VoiceState.RECORDING

    fun canStartVoice(): Boolean {
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "canStartVoice: Missing RECORD_AUDIO permission")
            return false
        }
        if (isBlockedEditor(ims.currentInputEditorInfo)) {
            Log.w(TAG, "canStartVoice: Blocked editor (password)")
            return false
        }
        return true
    }

    fun startVoice() {
        if (state == VoiceState.RECORDING) {
            stopVoice()
            return
        }

        if (state != VoiceState.IDLE && state != VoiceState.ERROR) {
            Log.w(TAG, "Resetting previous state $state for new voice session")
            cancelVoice()
        }

        if (!canStartVoice()) {
            notifyError(ims.getString(R.string.voice_error_permission))
            return
        }

        val service = ProofreadService(ims)
        if (!service.hasApiKey()) {
            notifyError(ims.getString(R.string.voice_error_no_api_key))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ims.requestShowSelf(0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to requestShowSelf", e)
            }
        }

        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        needsCapitalStart = true
        sessionEmittedText = ""
        synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.reset()
        }

        val started = startAudioRecordingThread()
        if (started) {
            updateState(VoiceState.RECORDING)
        } else {
            notifyError(ims.getString(R.string.voice_error_record_start))
            cleanupSession()
            updateState(VoiceState.ERROR)
        }
    }

    fun stopVoice() {
        Log.i(TAG, "stopVoice() called, state=$state")
        if (state == VoiceState.RECORDING) {
            updateState(VoiceState.PROCESSING_FINAL)
            stopAudioLoop()
            processOnlineTranscription()
        }
    }

    private fun processOnlineTranscription() {
        val pcmBytes = synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.toByteArray().also { onlineAudioBuffer.reset() }
        }
        val sessionId = activeSessionId

        if (pcmBytes.size < 3200) { // Less than 100ms of audio
            Log.i(TAG, "Online voice audio too short (${pcmBytes.size} bytes), skipping")
            cleanupSession()
            updateState(VoiceState.IDLE)
            return
        }

        val wavBytes = AudioUtils.pcmToWav(pcmBytes, SAMPLE_RATE, 1, 16)
        val prefLang = ims.prefs().getString(VoiceConstants.PREF_VOICE_LANGUAGE, VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD)
            ?: VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD
        val languageTag = when (prefLang) {
            VoiceConstants.VOICE_LANG_AUTO -> "auto"
            VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD, "" -> {
                try {
                    RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag()
                } catch (_: Exception) {
                    java.util.Locale.getDefault().toLanguageTag()
                }
            }
            else -> prefLang
        }

        val service = ProofreadService(ims)
        onlineTranscriptionJob?.cancel()
        onlineTranscriptionJob = coroutineScope.launch(Dispatchers.IO) {
            val result = try {
                kotlinx.coroutines.withTimeout(25_000L) {
                    service.transcribeAudio(wavBytes, languageTag)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Result.failure(Exception(ims.getString(R.string.voice_error_timeout)))
            } catch (e: Exception) {
                Result.failure(e)
            }
            mainHandler.post {
                if (activeSessionId == sessionId) {
                    result.onSuccess { transcribedText ->
                        Log.i(TAG, "Online transcription success: '$transcribedText'")
                        syncRecognizedText(transcribedText, isFinal = true)
                        cleanupSession()
                        updateState(VoiceState.IDLE)
                    }.onFailure { ex ->
                        val err = ex.message ?: "Transcription failed"
                        Log.e(TAG, "Online transcription error: $err", ex)
                        notifyError(err)
                        cleanupSession()
                        updateState(VoiceState.ERROR)
                    }
                }
            }
        }
    }

    fun cancelVoice() {
        Log.i(TAG, "cancelVoice() called, state=$state")
        if (state != VoiceState.IDLE) {
            cleanupSession()
            clearComposingText()
            updateState(VoiceState.IDLE)
        }
    }

    private fun startAudioRecordingThread(): Boolean {
        stopAudioLoop() // Prevent zombie thread overlap on rapid re-entry
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startAudioRecordingThread: Missing RECORD_AUDIO permission")
            return false
        }

        val audioManager = ims.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        try {
            if (audioManager?.isMicrophoneMute == true) {
                Log.w(TAG, "Microphone was muted in AudioManager, unmuting...")
                audioManager.isMicrophoneMute = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unmute via AudioManager", e)
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "startAudioRecordingThread: Invalid min buffer size: $minBufSize")
            return false
        }

        // Multiply by 4 (at least 8192) to prevent hardware buffer overruns
        val bufferSize = maxOf(minBufSize * 4, FRAME_SIZE_BYTES * 8, 8192)

        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        var record: AudioRecord? = null
        for (source in sources) {
            try {
                val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                    val builder = AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setContext(ims)
                    }
                    builder.build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioRecord(
                        source,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized successfully with source: $source")
                    record = candidate
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create AudioRecord with source $source", e)
            }
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state=${record?.state})")
            record?.release()
            return false
        }

        audioRecord = record
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Exception in AudioRecord.startRecording", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }

        isRecording.set(true)

        val maxDurationSec = ims.prefs().getString(VoiceConstants.PREF_VOICE_MAX_DURATION_SECONDS, "${VoiceConstants.VOICE_MAX_DURATION_DEFAULT_SECONDS}")?.toIntOrNull() ?: VoiceConstants.VOICE_MAX_DURATION_DEFAULT_SECONDS
        val maxDurationMs = if (maxDurationSec > 0) maxDurationSec * 1000L else 0L

        audioThread = Thread({
            val buffer = ByteArray(FRAME_SIZE_BYTES)
            var totalBytesWritten = 0L
            val sessionStartTime = System.currentTimeMillis()

            try {
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        synchronized(onlineAudioBuffer) {
                            onlineAudioBuffer.write(buffer, 0, read)
                        }
                        totalBytesWritten += read

                        val now = System.currentTimeMillis()
                        if (maxDurationMs > 0L && (now - sessionStartTime >= maxDurationMs)) {
                            Log.i(TAG, "Max recording duration (${maxDurationMs}ms) reached. Stopping voice input.")
                            mainHandler.post { stopVoice() }
                            break
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                if (isRecording.get()) {
                    Log.e(TAG, "Exception in audio write loop", e)
                }
            } finally {
                Log.i(TAG, "Audio loop ended. Total wrote: $totalBytesWritten bytes")
            }
        }, "VoiceAudioThread").apply {
            isDaemon = true
            start()
        }

        return true
    }

    private fun stopAudioLoop() {
        if (!isRecording.getAndSet(false)) return
        Log.i(TAG, "stopAudioLoop() executing")

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        audioThread?.let { thread ->
            try {
                thread.join(1000)
                if (thread.isAlive) {
                    Log.w(TAG, "VoiceAudioThread hung. Skipping release() to avoid native proxy crash.")
                    audioThread = null
                    audioRecord = null
                    return
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while joining audioThread", e)
            }
        }
        audioThread = null

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    private fun syncRecognizedText(rawText: String, isFinal: Boolean) {
        val ic = ims.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "syncRecognizedText: InputConnection lost! (ic is null, isFinal=$isFinal)")
            return
        }
        if (!isRecording.get() && !isFinal) return

        val isSmartPunctuationEnabled = ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_SMART_PUNCTUATION, true)
        val processedRaw = if (!isSmartPunctuationEnabled) {
            rawText.replace(Regex("[,.?!;:]"), "")
        } else {
            rawText
        }
        val trimmed = processedRaw.trim()

        // If final text is empty, commit a trailing space if we already emitted text.
        if (isFinal && trimmed.isEmpty()) {
            if (sessionEmittedText.isNotEmpty()) {
                ic.beginBatchEdit()
                try {
                    ic.finishComposingText()
                    ic.commitText(" ", 1)
                    val lastChar = sessionEmittedText.lastOrNull()
                    needsCapitalStart = lastChar != null && lastChar in ".!?"
                } finally {
                    ic.endBatchEdit()
                    sessionEmittedText = ""
                }
            }
            return
        }

        if (trimmed.isEmpty()) return

        // Sentence capitalization for the current utterance
        val fullTargetText = if (needsCapitalStart && trimmed.isNotEmpty()) {
            trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        } else {
            trimmed
        }

        val current = sessionEmittedText

        // Find longest common prefix between what's in the editor from this utterance and the new target
        var commonPrefixLen = 0
        val minLen = minOf(current.length, fullTargetText.length)
        while (commonPrefixLen < minLen && current[commonPrefixLen] == fullTargetText[commonPrefixLen]) {
            commonPrefixLen++
        }

        val charsToDelete = current.length - commonPrefixLen
        val textToAppend = fullTargetText.substring(commonPrefixLen)

        if (charsToDelete == 0 && textToAppend.isEmpty() && !isFinal) {
            return
        }

        ic.beginBatchEdit()
        try {
            if (charsToDelete > 0) {
                ic.deleteSurroundingText(charsToDelete, 0)
            }
            if (textToAppend.isNotEmpty()) {
                ic.commitText(textToAppend, 1)
            }
            if (isFinal && fullTargetText.isNotEmpty()) {
                ic.finishComposingText()
                ic.commitText(" ", 1)
                val lastChar = fullTargetText.lastOrNull()
                needsCapitalStart = lastChar != null && lastChar in ".!?"
                sessionEmittedText = ""
            } else {
                sessionEmittedText = fullTargetText
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun clearComposingText() {
        if (sessionEmittedText.isNotEmpty()) {
            val ic = ims.currentInputConnection
            if (ic != null) {
                ic.deleteSurroundingText(sessionEmittedText.length, 0)
            }
            sessionEmittedText = ""
        }
    }

    private fun cleanupSession() {
        onlineTranscriptionJob?.cancel()
        onlineTranscriptionJob = null
        synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.reset()
        }
        stopAudioLoop()
        activeSessionId = null
        sessionEmittedText = ""
    }

    @Synchronized
    private fun updateState(newState: VoiceState) {
        val oldState = this.state
        if (oldState == newState) {
            Log.d(TAG, "State dedup: $oldState -> $newState (ignored)")
            return
        }
        Log.i(TAG, "State transition: $oldState -> $newState")
        this.state = newState
        mainHandler.post {
            listener?.onStateChanged(newState)
        }
    }

    private fun notifyError(message: String) {
        mainHandler.post {
            listener?.onError(message)
        }
    }

    fun release() {
        cancelVoice()
        coroutineScope.cancel()
    }

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_SHORTS = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 480 shorts
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SHORTS * 2 // 960 bytes

        fun isBlockedEditor(info: EditorInfo?): Boolean {
            if (info == null) return false

            val variation = info.inputType and InputType.TYPE_MASK_VARIATION

            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
    }
}
