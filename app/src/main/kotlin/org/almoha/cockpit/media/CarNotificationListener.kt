package org.almoha.cockpit.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.data.Prefs

data class NavDirectionState(
    val active: Boolean = false,
    val title: String = "",
    val instruction: String = "",
    val eta: String = "",
    val icon: Bitmap? = null,
)

class CarNotificationListener : NotificationListenerService() {

    private val prefs by lazy { Prefs(applicationContext) }

    override fun onListenerConnected() {
        instance = this
        scanExistingNavigation()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _navState.value = NavDirectionState()
    }

    private fun scanExistingNavigation() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val mapsSbn = list.firstOrNull { isMapsNavigation(it) }
        if (mapsSbn != null) {
            updateMapsNav(mapsSbn)
        } else {
            navKey = null
            _navState.value = NavDirectionState()
        }
    }

    private fun updateMapsNav(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        // Selon les versions, Maps met « 12 min · 8,5 km · 10:05 » dans des champs différents :
        // on prend le premier qui ressemble à une durée restante.
        val etaCandidates = listOf(
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
        ).mapNotNull { extras.getCharSequence(it)?.toString()?.trim() }.filter { it.isNotBlank() }
        val eta = etaCandidates.firstOrNull { ETA_PATTERN.containsMatchIn(it) } ?: subText
        Log.d(TAG, "Maps nav: title=$title text=$text candidates=$etaCandidates")
        navKey = sbn.key

        val iconBitmap = runCatching {
            n.getLargeIcon()?.loadDrawable(applicationContext)?.toBitmap(128, 128)
        }.getOrNull()

        _navState.value = NavDirectionState(
            active = true,
            title = title,
            instruction = text,
            eta = eta,
            icon = iconBitmap,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Seule la notification de guidage en cours met fin à la navigation (pas le partage de position, etc.).
        if (sbn != null && sbn.key == navKey) {
            navKey = null
            _navState.value = NavDirectionState()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isMapsNavigation(sbn)) {
            updateMapsNav(sbn)
            return
        }

        val current = prefs.state.value
        if (!current.readMessages) return
        if (!current.isSessionActive()) return
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive != true) return
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        if (sbn.packageName !in MESSAGING_PACKAGES) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (System.currentTimeMillis() - sbn.postTime > 15_000L) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // WhatsApp republie souvent la même notification (réactions, mises à jour, regroupement) avec
        // une nouvelle heure : on ne lit qu'une fois un même contenu, quelle que soit la republication.
        val contentKey = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        synchronized(spoken) {
            spoken.entries.removeAll { now - it.value > SPOKEN_MEMORY_MS }
            if (spoken.containsKey(contentKey)) return
            spoken[contentKey] = now
        }

        val spokenText = buildString {
            append("Nouveau message")
            if (title.isNotBlank()) append(" de $title")
            if (text.isNotBlank()) append(". ").append(text)
        }.replace('\n', ' ').take(400)

        Log.d(TAG, "Lecture: $spokenText")
        Speech.speak(applicationContext, spokenText)
    }

    private fun isMapsNavigation(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != KnownApps.MAPS || !sbn.isOngoing) return false
        val n = sbn.notification
        return n.category == Notification.CATEGORY_NAVIGATION ||
            n.channelId?.contains("nav", ignoreCase = true) == true
    }

    companion object {
        private const val TAG = "CockpitNotif"

        @Volatile
        private var navKey: String? = null
        /** Contenus déjà lus → heure de lecture (oubliés au bout d'une heure). */
        private val spoken = HashMap<String, Long>()
        private const val SPOKEN_MEMORY_MS = 60 * 60 * 1000L
        private val ETA_PATTERN = Regex("\\d+\\s*(min|h)\\b", RegexOption.IGNORE_CASE)
        private val _navState = MutableStateFlow(NavDirectionState())
        val navState: StateFlow<NavDirectionState> = _navState.asStateFlow()

        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.facebook.orca",
        )

        @Volatile
        private var instance: CarNotificationListener? = null

        val component: ComponentName
            get() = ComponentName(
                "org.almoha.cockpit",
                CarNotificationListener::class.java.name,
            )

        fun isConnected(): Boolean = instance != null

        fun refreshNavigation() {
            instance?.scanExistingNavigation()
        }

        fun hasAccess(context: Context): Boolean {
            val enabled = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context)
            return enabled.contains(context.packageName)
        }
    }
}
