package org.almoha.cockpit.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Bitmap? = null,
)

enum class AppCategory { NAV, MEDIA, PHONE, MESSAGE, EV, OTHER }

object KnownApps {
    const val MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
    const val TIDAL = "com.aspiro.tidal"
    const val SPOTIFY = "com.spotify.music"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val WHATSAPP = "com.whatsapp"
    const val MESSAGES = "com.google.android.apps.messaging"
    const val DIALER = "com.google.android.dialer"
    const val ELECTROVERSE = "energy.octopus.electricjuice.android"
    const val CHARGE_MAP = "com.chargemap_beta.android"
    const val ELECTROMAPS = "com.enredats.electromaps"
    const val FRESMILE = "com.freshmile.charge"
    const val PODCATCHER = "com.almoha73.podcatcher"
    const val ZENTUBE = "com.almoha73.zentube"
    const val RADIO = "com.radio.pocketfm"
    const val FLUX_MEDIAS = "com.almoha73.fluxmedias"
    const val AUDIBLE = "com.audible.application"

    /** Applis proposées dans le sélecteur de source média, dans cet ordre. */
    val MEDIA_SOURCES = listOf(
        FLUX_MEDIAS, TIDAL, SPOTIFY, YT_MUSIC, "deezer.android.app", AUDIBLE, PODCATCHER, RADIO,
    )

    private val SPOKEN_WORD = setOf(AUDIBLE, PODCATCHER, "com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts")

    /**
     * Livre audio ou podcast : « avance / recule » = ±10 s. Pour la musique, ça change de piste.
     * Une piste de plus de 20 minutes est aussi traitée comme du contenu parlé.
     */
    fun isSpokenWord(pkg: String, durationMs: Long = 0L): Boolean =
        pkg in SPOKEN_WORD || durationMs > 20 * 60 * 1000L

    fun category(pkg: String): AppCategory = when (pkg) {
        MAPS, WAZE -> AppCategory.NAV
        TIDAL, SPOTIFY, YT_MUSIC, PODCATCHER, ZENTUBE, RADIO, FLUX_MEDIAS -> AppCategory.MEDIA
        DIALER, "com.android.phone", "com.orange.phone" -> AppCategory.PHONE
        WHATSAPP, MESSAGES, "com.facebook.orca", "org.telegram.messenger",
        "org.thoughtcrime.securesms" -> AppCategory.MESSAGE
        ELECTROVERSE, CHARGE_MAP, ELECTROMAPS, FRESMILE -> AppCategory.EV
        else -> AppCategory.OTHER
    }
}

class AppsRepository(private val context: Context) {

    suspend fun loadLabels(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val collator = Collator.getInstance(Locale.FRANCE)
        resolved
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                runCatching {
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString().ifBlank { pkg }
                    AppEntry(pkg, label)
                }.getOrNull()
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    suspend fun loadIcon(pkg: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192)
        }.getOrNull()
    }

    fun launch(pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0)
    }.isSuccess

    fun defaultDock(installed: Set<String>): List<String> = listOf(
        KnownApps.MAPS,
        KnownApps.TIDAL,
        KnownApps.FLUX_MEDIAS,
        KnownApps.DIALER,
        KnownApps.WHATSAPP,
        KnownApps.PODCATCHER,
        KnownApps.YT_MUSIC,
        KnownApps.CHARGE_MAP,
    ).filter { it in installed }.distinct().take(8)
}
