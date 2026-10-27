package com.fatlosstrack.ui.components

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger

/**
 * Speech-to-text for the AI bar, backed by the platform [SpeechRecognizer] (no extra dependency).
 *
 * Scoped to composition via [rememberVoiceInputState]: a fresh recognizer is created per
 * listening session and destroyed when the session ends, the app goes to the background,
 * or the bar leaves composition. Callers must hold RECORD_AUDIO before calling [start].
 */
@Stable
class VoiceInputState internal constructor(private val context: Context) {
    /** False when no recognition service is installed — the mic button is hidden. */
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    var isListening: Boolean by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null

    /**
     * Start listening. [onTranscript] receives partial results while the user speaks and then
     * the final transcript; [onFailure] receives a [SpeechRecognizer] error code when nothing
     * usable was heard.
     */
    fun start(
        languageTag: String,
        onTranscript: (String) -> Unit,
        onFailure: (Int) -> Unit,
    ) {
        if (!isAvailable || isListening) return
        release()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        var lastPartial: String? = null
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== sr) return
                bestMatch(partialResults)?.let {
                    lastPartial = it
                    onTranscript(it)
                }
            }

            override fun onResults(results: Bundle?) {
                if (recognizer !== sr) return
                val heard = bestMatch(results) ?: lastPartial
                release()
                if (heard != null) onTranscript(heard) else onFailure(SpeechRecognizer.ERROR_NO_MATCH)
            }

            override fun onError(error: Int) {
                if (recognizer !== sr) return
                AppLogger.instance?.user("Voice input error: $error")
                release()
                // Some recognizers report no-match/timeout after already streaming partials — keep those.
                val heard = lastPartial
                if (heard != null) onTranscript(heard) else onFailure(error)
            }
        })
        recognizer = sr
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        AppLogger.instance?.user("Voice input started: $languageTag")
        sr.startListening(intent)
    }

    /** Stop recording; the final transcript is still delivered. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Abort the session without delivering anything further. */
    fun cancel() {
        release()
    }

    internal fun release() {
        val sr = recognizer ?: return
        recognizer = null
        isListening = false
        sr.cancel()
        sr.destroy()
    }

    private fun bestMatch(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

@Composable
fun rememberVoiceInputState(): VoiceInputState {
    val context = LocalContext.current
    val state = remember(context) { VoiceInputState(context) }
    DisposableEffect(state) {
        onDispose { state.release() }
    }
    // Don't keep the mic open while the app is in the background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.cancel() }
    return state
}

/**
 * Recognizer language tag for the app language setting ("en", "sl", "hu").
 * Prefers the device's regional variant (e.g. en-GB) when one of the system locales matches.
 * Reads the system configuration because the app default locale is forced to Locale.US.
 */
internal fun speechLanguageTag(appLanguage: String): String {
    val systemLocales = Resources.getSystem().configuration.locales
    for (i in 0 until systemLocales.size()) {
        val locale = systemLocales[i]
        if (locale.language == appLanguage && locale.country.isNotEmpty()) return locale.toLanguageTag()
    }
    return when (appLanguage) {
        "sl" -> "sl-SI"
        "hu" -> "hu-HU"
        "en" -> "en-US"
        else -> appLanguage
    }
}

/** User-facing message for a [SpeechRecognizer] error code, or null to stay silent. */
@StringRes
internal fun voiceErrorMessage(error: Int): Int? = when (error) {
    // Raised by our own stop/cancel — not worth surfacing
    SpeechRecognizer.ERROR_CLIENT -> null
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_no_match
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission_denied
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_error_language
    else -> R.string.voice_error_generic
}
