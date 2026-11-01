package de.adohadesign.memorasolo.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Requires Android's ON-DEVICE recognition service. No network fallback is used. */
class OfflineDictation(private val context: Context, private val onChunk: (String) -> Unit,
    private val onState: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var paused = false
    private var retries = 0
    var partial: String = ""
        private set

    fun available(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(): Boolean {
        if (!available()) { onState("Offline-Spracherkennung fehlt. Bitte deutsche Sprachdaten auf dem Handy installieren."); return false }
        paused = false
        running = true
        retries = 0
        launch()
        return running
    }
    private fun launch() {
        if (!running || paused) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState("Sprechen …") }
            override fun onBeginningOfSpeech() { retries = 0 }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onState("Verarbeite …") }
            override fun onError(error: Int) {
                if (!running || paused) return
                partial = ""
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                    stop()
                    onState("Offline-Diktat nicht verfügbar (Fehler $error). Mikrofonberechtigung und deutsche Sprachdaten prüfen.")
                    return
                }
                retries++
                if (retries > 8) {
                    stop()
                    onState("Spracherkennung angehalten (Fehler $error). Bitte Mikrofon und Offline-Sprachpaket prüfen.")
                } else {
                    // Repeated short sessions prevent the recognizer's built-in timeout
                    handler.postDelayed({ launch() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1_200 else 500)
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                partial = ""
                if (text.isNotBlank()) onChunk(punctuate(text))
                retries = 0
                if (running && !paused) handler.postDelayed({ launch() }, 250)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onState("Sprechen … $partial")
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { recognizer?.startListening(intent) } catch (e: Exception) { stop(); onState("Diktat fehlgeschlagen: ${e.message}") }
    }
    private fun punctuate(input: String): String {
        val t = input.trim().replace(Regex("\\s+"), " ")
        if (t.isBlank()) return ""
        val first = t.replaceFirstChar { it.uppercaseChar() }
        return if (first.last() in charArrayOf('.', '!', '?', ':')) first else "$first."
    }
    fun pause() { paused = true; recognizer?.cancel(); recognizer?.destroy(); recognizer = null; onState("Pausiert") }
    fun resume() { if (running) { paused = false; launch() } }
    fun stop() {
        running = false
        paused = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        onState("Diktat beendet")
    }
}
