/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard (AI translate & proofread)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Helper to run AI proofread/translate operations asynchronously, with callbacks
 * executed on the main thread (used from Java code in InputLogic).
 */
object ProofreadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentJob: Job? = null

    val isOperationInProgress: Boolean
        get() = currentJob?.isActive == true

    interface AiCallback {
        fun onSuccess(result: String)
        fun onError(message: String)
    }

    private fun performAsyncOperation(
        context: Context,
        text: String,
        noTextErrorResId: Int,
        allowEmptyInput: Boolean = false,
        apiCall: suspend (ProofreadService) -> Result<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val service = ProofreadService(context)
        if (!service.hasApiKey()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(R.string.ai_no_api_key), true)
            }
            return
        }
        if (!allowEmptyInput && text.isBlank()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(noTextErrorResId), true)
            }
            return
        }
        if (isOperationInProgress) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(R.string.ai_operation_in_progress), true)
            }
            return
        }

        currentJob = scope.launch {
            val result = apiCall(service)
            mainHandler.post {
                currentJob = null
                result.fold(
                    onSuccess = { onSuccess(it) },
                    onFailure = { error ->
                        onError(error.message ?: "Unknown error")
                        KeyboardSwitcher.getInstance().showToast(error.message ?: "Error", false)
                    }
                )
            }
        }
    }

    @JvmStatic
    fun translateAsync(context: Context, text: String, callback: AiCallback) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.translate_no_text,
            apiCall = { it.translate(text) },
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    @JvmStatic
    fun proofreadAsync(context: Context, text: String, callback: AiCallback) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            apiCall = { it.proofread(text) },
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    @JvmStatic
    fun customAsync(context: Context, text: String, prompt: String, callback: AiCallback) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            allowEmptyInput = true,
            apiCall = { it.proofread(text, overridePrompt = prompt) },
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    /** cancel the current operation, if any */
    @JvmStatic
    fun cancelCurrentOperation() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            currentJob = null
        }
    }
}