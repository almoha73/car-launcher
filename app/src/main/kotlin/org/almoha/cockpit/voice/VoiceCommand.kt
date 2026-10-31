package org.almoha.cockpit.voice

import java.text.Normalizer
import java.util.Locale

sealed interface VoiceCommand {
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Forward : VoiceCommand
    data object Rewind : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object StopNavigation : VoiceCommand
    data object Charging : VoiceCommand
    data class Call(val who: String) : VoiceCommand
    data class Navigate(val destination: String) : VoiceCommand
    data class PlaySearch(val query: String) : VoiceCommand
    data class OpenApp(val name: String) : VoiceCommand
    data class Unknown(val text: String) : VoiceCommand
}

/** Minuscules, sans accents ni ponctuation : « Emmène-moi à l'Hôtel » → « emmene moi a l hotel ». */
fun normalizeSpeech(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

object VoiceParser {

    private val callPrefixes = listOf("appelle", "appeler", "appel", "telephone a", "telephoner a", "telephone")

    private val navPrefixes = listOf(
        "emmene moi a", "emmene moi au", "emmene moi aux", "emmene moi chez", "emmene moi",
        "conduis moi a", "conduis moi au", "conduis moi chez", "conduis moi",
        "navigue vers", "navigation vers", "itineraire vers", "itineraire pour", "guide moi vers", "guide moi a",
        "on va a", "on va au", "on va chez", "va a", "va au", "va aux", "va chez",
        "aller a", "aller au", "aller chez", "direction", "rentre a", "rentrer a",
    )

    private val stopNavPhrases = listOf(
        "arrete la navigation", "arreter la navigation", "stop navigation", "stoppe la navigation",
        "annule la navigation", "annuler la navigation", "fin de navigation", "arrete le guidage",
        "annule l itineraire", "arrete l itineraire",
    )

    private val playPrefixes = listOf("mets moi", "mets", "met", "joue", "jouer", "ecoute", "ecouter", "lance la musique de")
    private val openPrefixes = listOf("ouvre", "ouvrir", "lance", "lancer", "demarre")

    fun parse(raw: String): VoiceCommand {
        val t = normalizeSpeech(raw)
        if (t.isEmpty()) return VoiceCommand.Unknown(raw)

        rest(t, callPrefixes)?.let { if (it.isNotEmpty()) return VoiceCommand.Call(it) }

        if (stopNavPhrases.any { t.contains(it) }) return VoiceCommand.StopNavigation

        rest(t, navPrefixes)?.let { if (it.isNotEmpty()) return VoiceCommand.Navigate(dropArticle(it)) }
        if (t in setOf("maison", "a la maison", "on rentre", "rentrer", "rentre", "rentrer a la maison")) {
            return VoiceCommand.Navigate("maison")
        }
        if (t in setOf("travail", "au travail", "au boulot", "boulot", "au bureau")) {
            return VoiceCommand.Navigate("travail")
        }

        if (hasAny(t, "recharge", "recharger", "borne", "electroverse")) return VoiceCommand.Charging

        if (t.contains("volume") || t.contains("plus fort") || t.contains("moins fort")) {
            return if (hasAny(t, "moins fort", "baisse", "baisser", "diminue", "descend")) {
                VoiceCommand.VolumeDown
            } else {
                VoiceCommand.VolumeUp
            }
        }

        if (hasAny(t, "suivant", "suivante", "next", "zappe", "passe")) return VoiceCommand.Next
        if (hasAny(t, "precedent", "precedente", "reviens", "d avant")) return VoiceCommand.Previous
        if (hasAny(t, "avance", "avancer")) return VoiceCommand.Forward
        if (hasAny(t, "recule", "reculer")) return VoiceCommand.Rewind
        if (hasAny(t, "pause", "stop", "arrete", "arreter", "coupe", "silence", "tais toi")) return VoiceCommand.Pause

        rest(t, openPrefixes)?.let { if (it.isNotEmpty()) return VoiceCommand.OpenApp(dropArticle(it)) }

        rest(t, playPrefixes)?.let { query ->
            val q = dropArticle(query)
            return when {
                q.isEmpty() || q in setOf("musique", "la musique", "de la musique", "la suite") -> VoiceCommand.Play
                q.contains("radio") -> VoiceCommand.OpenApp("fluxmedias")
                else -> VoiceCommand.PlaySearch(q)
            }
        }

        if (hasAny(t, "lecture", "play", "reprends", "reprendre", "continue", "musique")) return VoiceCommand.Play
        if (t.contains("radio")) return VoiceCommand.OpenApp("fluxmedias")

        return VoiceCommand.Unknown(raw)
    }

    /** Texte qui suit l'un des préfixes (le plus long gagne), ou null si aucun ne correspond. */
    private fun rest(t: String, prefixes: List<String>): String? =
        prefixes.sortedByDescending { it.length }
            .firstOrNull { t == it || t.startsWith("$it ") }
            ?.let { t.removePrefix(it).trim() }

    private fun dropArticle(s: String): String =
        s.replace(Regex("^(la|le|les|l|un|une|du|de la|des|a la|au)\\s+"), "").trim()

    // Mots entiers uniquement : « passe » ne doit pas déclencher sur « passage ».
    private fun hasAny(t: String, vararg words: String): Boolean {
        val padded = " $t "
        return words.any { padded.contains(" $it ") }
    }
}
