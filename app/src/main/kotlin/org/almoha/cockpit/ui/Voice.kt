package org.almoha.cockpit.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.media.Speech
import org.almoha.cockpit.voice.VoiceCommand
import org.almoha.cockpit.voice.VoiceParser
import org.almoha.cockpit.voice.normalizeSpeech

/** Reconnaissance vocale de Cockpit : écoute une phrase, puis transmet les résultats à [onResult]. */
@Stable
class VoiceController(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var heard by mutableStateOf("")
        private set
    var feedback by mutableStateOf("")
        private set

    var onResult: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private var recognizer: SpeechRecognizer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { heard = it }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results).orEmpty()
            heard = text
            finish()
            if (text.isBlank()) {
                feedback = "Je n'ai rien entendu"
                return
            }
            // Petit délai : on laisse la musique reprendre (fin du focus audio) avant d'exécuter « pause », etc.
            handler.postDelayed({ onResult?.invoke(text) }, 400L)
        }

        override fun onError(error: Int) {
            finish()
            feedback = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Pas de réseau pour la reconnaissance"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorisez le micro"
                else -> "Reconnaissance vocale indisponible"
            }
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (listening) return
        Speech.stop()
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        heard = ""
        feedback = ""
        requestFocus()
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { r.startListening(intent) }.onFailure {
            finish()
            feedback = "Reconnaissance vocale indisponible"
        }
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        finish()
    }

    fun showFeedback(text: String) {
        feedback = text
    }

    fun clearFeedback() {
        feedback = ""
        heard = ""
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        releaseFocus()
    }

    private fun finish() {
        listening = false
        releaseFocus()
    }

    // La musique se met en pause pendant l'écoute pour que la phrase soit bien comprise.
    private fun requestFocus() {
        val manager = audio ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }
    }

    private fun releaseFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audio?.abandonAudioFocusRequest(request) }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
}

/**
 * Exécute une phrase reconnue et renvoie le texte à afficher. Les destinations passent par la carte intégrée
 * quand elle est active (guidage lancé directement), sinon par Google Maps.
 */
fun executeVoiceCommand(
    text: String,
    state: UiState,
    vm: CarViewModel,
    mapController: MapController?,
    context: Context,
): String = when (val command = VoiceParser.parse(text)) {
    VoiceCommand.Play -> { vm.play(); "Lecture" }
    VoiceCommand.Pause -> { vm.pause(); "Pause" }
    VoiceCommand.Next -> { vm.next(); "Titre suivant" }
    VoiceCommand.Previous -> { vm.previous(); "Titre précédent" }
    VoiceCommand.Forward -> if (KnownApps.isSpokenWord(state.media.packageName, state.media.durationMs)) {
        vm.forward10s()
        "+10 secondes"
    } else {
        vm.next()
        "Titre suivant"
    }
    VoiceCommand.Rewind -> if (KnownApps.isSpokenWord(state.media.packageName, state.media.durationMs)) {
        vm.rewind10s()
        "−10 secondes"
    } else {
        vm.previous()
        "Titre précédent"
    }
    VoiceCommand.VolumeUp -> { vm.adjustVolume(up = true); "Volume +" }
    VoiceCommand.VolumeDown -> { vm.adjustVolume(up = false); "Volume −" }
    VoiceCommand.Charging -> { vm.openCharging(); "Recharge" }
    VoiceCommand.StopNavigation -> {
        mapController?.clearRoute()
        "Navigation arrêtée"
    }
    is VoiceCommand.Call -> {
        val contact = findContact(state, command.who)
        if (contact != null) {
            vm.call(contact.number)
            "Appel de ${contact.name}"
        } else {
            say(context, "Je ne trouve pas ${command.who} dans vos favoris")
        }
    }
    is VoiceCommand.Navigate -> {
        val target = resolveDestination(state, command.destination)
        when {
            target == null -> say(context, "Adresse « ${command.destination} » non configurée dans les réglages")
            mapController != null -> {
                mapController.routeTo(target.second, startNavigation = true)
                say(context, "Itinéraire vers ${target.first}")
            }
            else -> {
                vm.navigate(KnownApps.MAPS, target.second)
                "Itinéraire vers ${target.first}"
            }
        }
    }
    is VoiceCommand.PlaySearch -> {
        vm.playSearchInTidal(command.query)
        "Tidal : ${command.query}"
    }
    is VoiceCommand.OpenApp -> {
        val key = command.name.replace(" ", "")
        val app = state.apps.firstOrNull {
            val label = normalizeSpeech(it.label).replace(" ", "")
            label.isNotEmpty() && (label.contains(key) || key.contains(label))
        }
        if (app != null) {
            vm.launchApp(app.packageName)
            "Ouverture de ${app.label}"
        } else {
            say(context, "Je ne trouve pas l'application ${command.name}")
        }
    }
    is VoiceCommand.Unknown -> say(context, "Je n'ai pas compris")
}

private fun say(context: Context, text: String): String {
    Speech.speak(context, text)
    return text
}

private fun findContact(state: UiState, who: String): org.almoha.cockpit.data.ContactFav? {
    val words = normalizeSpeech(who).split(" ")
        .filter { it.length >= 3 }
        .map { if (it == "papa" || it == "maman") "parents" else it }
    return state.contacts.firstOrNull { contact ->
        val name = normalizeSpeech(contact.name).split(" ")
        words.any { it in name }
    }
}

/** (libellé, adresse) : maison, travail, puis les favoris de navigation, sinon la phrase telle quelle. */
private fun resolveDestination(state: UiState, destination: String): Pair<String, String>? {
    val d = normalizeSpeech(destination)
    val prefs = state.prefs
    if (d == "maison" || d == "la maison" || d == "chez moi") {
        return prefs.home.takeIf { it.isNotBlank() }?.let { "la maison" to it }
    }
    if (d == "travail" || d == "boulot" || d == "bureau") {
        return prefs.work.takeIf { it.isNotBlank() }?.let { "le travail" to it }
    }
    prefs.navFavorites.firstOrNull { normalizeSpeech(it.name) == d }
        ?.let { return it.name to it.address }
    prefs.navFavorites.firstOrNull { val n = normalizeSpeech(it.name); n.contains(d) || d.contains(n) }
        ?.let { return it.name to it.address }
    return destination to destination
}

@Composable
fun VoiceOverlay(voice: VoiceController) {
    val visible = voice.listening || voice.feedback.isNotEmpty()
    LaunchedEffect(voice.feedback) {
        if (voice.feedback.isNotEmpty()) {
            delay(3_000L)
            voice.clearFeedback()
        }
    }
    if (!visible) return
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (voice.listening) voice.cancel() else voice.clearFeedback()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF20B1118))
                .border(1.dp, CockpitColors.Assistant.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(if (voice.listening) scale else 1f)
                    .clip(CircleShape)
                    .background(CockpitColors.Assistant.copy(alpha = if (voice.listening) 0.35f else 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = CockpitColors.Assistant, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (voice.listening) "J'écoute…" else voice.feedback,
                color = CockpitColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (voice.heard.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "« ${voice.heard} »",
                    color = CockpitColors.TextDim,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (voice.listening) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Ex. : « pause », « suivant », « appelle Patrick », « va à la maison »",
                    color = CockpitColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
