package org.almoha.cockpit.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

object Speech {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()
    private val inFlight = AtomicInteger(0)
    private var audio: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var nextId = 0
    private var initFailed = false

    /** Vrai pendant une lecture : le cockpit affiche alors un gros bouton « Arrêter ». */
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Au-delà, les nouveaux messages sont ignorés : pas de file de lecture sans fin. */
    private const val MAX_QUEUED = 3

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    @Synchronized
    fun speak(context: Context, text: String) {
        if (inFlight.get() + pending.size >= MAX_QUEUED) return
        val engine = tts
        if (engine != null && ready) {
            enqueue(engine, text)
            return
        }
        // Moteur en cours d'initialisation : on garde tous les messages, pas seulement le dernier.
        pending += text
        if (engine == null) init(context.applicationContext)
    }

    @Synchronized
    fun stop() {
        pending.clear()
        tts?.stop()
        inFlight.set(0)
        _speaking.value = false
        releaseFocus()
    }

    private fun init(context: Context) {
        audio = context.getSystemService(AudioManager::class.java)
        initFailed = false
        val engine = TextToSpeech(context) { status -> onInit(status) }
        if (initFailed) {
            // L'échec peut être signalé pendant le constructeur, avant que tts ne soit affecté.
            runCatching { engine.shutdown() }
            initFailed = false
            return
        }
        if (tts == null) tts = engine
    }

    @Synchronized
    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            // Échec : on libère le moteur pour retenter au prochain message.
            val engine = tts
            if (engine == null) initFailed = true else runCatching { engine.shutdown() }
            tts = null
            ready = false
            pending.clear()
            return
        }
        val engine = tts ?: return
        engine.language = Locale.FRANCE
        engine.setSpeechRate(1.0f)
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
        ready = true
        pending.forEach { enqueue(engine, it) }
        pending.clear()
    }

    private fun enqueue(engine: TextToSpeech, text: String) {
        if (inFlight.getAndIncrement() == 0) requestFocus()
        _speaking.value = true
        // QUEUE_ADD : un nouveau message attend la fin du précédent au lieu de le couper.
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, "cockpit-${nextId++}")
        if (result != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            _speaking.value = false
            releaseFocus()
        }
    }

    // Baisse le volume de la musique pendant la lecture du message.
    private fun requestFocus() {
        val manager = audio ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }
    }

    private fun releaseFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audio?.abandonAudioFocusRequest(request) }
    }
}
