package org.almoha.cockpit.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.almoha.cockpit.data.KnownApps

data class BrowseMediaItem(
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val playable: Boolean = true,
    val queueId: Long? = null,
    val browsable: Boolean = false,
)

/** Navigation dans le catalogue Tidal (via son service Android Auto). */
data class CatalogState(
    val path: List<BrowseMediaItem> = emptyList(),
    val items: List<BrowseMediaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class MediaUiState(
    val hasAccess: Boolean = false,
    val hasSession: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val packageName: String = "",
    val appLabel: String = "",
    val appIcon: Bitmap? = null,
    val artwork: ImageBitmap? = null,
    val artworkUri: String? = null,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val positionUpdatedElapsedMs: Long = 0L,
    val canSeek: Boolean = false,
    val canFavorite: Boolean = false,
    val isFavorite: Boolean = false,
    val browseItems: List<BrowseMediaItem> = emptyList(),
)

private const val SWITCH_CHECK_MS = 4_000L

class MediaRepository(private val context: Context) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var started = false
    private val iconCache = HashMap<String, Bitmap?>()
    private val labelCache = HashMap<String, String>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        bind(best(list))
    }

    fun hasAccess(): Boolean = CarNotificationListener.hasAccess(context)

    fun start() {
        if (!hasAccess()) {
            if (started) {
                runCatching {
                    sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
                }
                started = false
            }
            _state.value = MediaUiState(hasAccess = false)
            return
        }
        if (!started) {
            runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChanged,
                    CarNotificationListener.component,
                    handler,
                )
            }.onFailure { android.util.Log.w("CockpitMedia", "register failed", it) }
            started = true
        }
        refresh()
    }

    fun stop() {
        controller?.unregisterCallback(callback)
        controller = null
        if (!started) return
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        started = false
    }

    fun refresh() {
        if (!hasAccess()) {
            _state.value = MediaUiState(hasAccess = false)
            return
        }
        val controllers = runCatching {
            sessionManager.getActiveSessions(CarNotificationListener.component)
        }.onFailure {
            android.util.Log.w("CockpitMedia", "getActiveSessions failed", it)
        }.getOrNull().orEmpty()
        android.util.Log.d("CockpitMedia", "sessions=${controllers.size}")
        bind(best(controllers))
    }

    private fun best(controllers: List<MediaController>?): MediaController? {
        // Android trie les sessions de la plus récemment utilisée à la plus ancienne :
        // on suit cet ordre pour afficher l'app qui joue vraiment (FluxMedias, Tidal, Audible…).
        val list = controllers.orEmpty()
        return list.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                it.playbackState?.state == PlaybackState.STATE_BUFFERING
        }
            ?: list.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED &&
                    !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
            }
            ?: list.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED
            }
    }

    private fun bind(next: MediaController?) {
        if (next?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = MediaUiState(hasAccess = hasAccess())
            return
        }
        val metadata = c.metadata
        val playback = c.playbackState
        val pkg = c.packageName.orEmpty()
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artworkUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val actions = playback?.actions ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val position = playback?.position?.coerceAtLeast(0L) ?: 0L
        val updatedAt = playback?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()

        val favAction = findFavoriteAction(playback)
        val isFav = favAction?.let { act ->
            val n = act.name.toString().lowercase()
            n.contains("supprimer") || n.contains("retirer") || n.contains("remove") || n.contains("unlike")
        } ?: false

        val queueBrowse = runCatching {
            c.queue.orEmpty().take(30).mapNotNull { qItem ->
                val desc = qItem.description
                val qTitle = desc.title?.toString()?.trim().orEmpty()
                if (qTitle.isBlank()) null
                else BrowseMediaItem(
                    mediaId = desc.mediaId.orEmpty(),
                    title = qTitle,
                    subtitle = desc.subtitle?.toString()?.trim().orEmpty(),
                    playable = true,
                    queueId = qItem.queueId,
                )
            }
        }.getOrDefault(emptyList())

        _state.value = MediaUiState(
            hasAccess = true,
            hasSession = true,
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING ||
                playback?.state == PlaybackState.STATE_BUFFERING,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            packageName = pkg,
            appLabel = appLabel(pkg),
            appIcon = appIcon(pkg),
            artwork = artwork?.asImageBitmap(),
            artworkUri = artworkUri,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            durationMs = duration,
            positionMs = position,
            positionUpdatedElapsedMs = updatedAt,
            canSeek = duration > 0L && (actions and PlaybackState.ACTION_SEEK_TO != 0L),
            canFavorite = favAction != null,
            isFavorite = isFav,
            browseItems = if (queueBrowse.isNotEmpty()) queueBrowse else _state.value.browseItems,
        )
    }

    private fun findFavoriteAction(playback: PlaybackState?): PlaybackState.CustomAction? {
        val customs = playback?.customActions.orEmpty()
        return customs.firstOrNull { act ->
            val n = act.name.toString().lowercase()
            val id = act.action.lowercase()
            n.contains("musique") || n.contains("favori") || n.contains("collection") ||
                n.contains("j'aime") || n.contains("like") ||
                id.contains("fav") || id.contains("heart") || id.contains("like") || id.contains("collection")
        }
    }

    fun toggleFavorite() {
        val c = controller ?: return
        val favAction = findFavoriteAction(c.playbackState) ?: return
        runCatching {
            c.transportControls.sendCustomAction(favAction, favAction.extras)
        }
    }

    fun playBrowseItem(item: BrowseMediaItem, openTidal: () -> Unit) {
        if (item.queueId != null) {
            // File d'attente de l'appli en cours.
            runCatching { controller?.transportControls?.skipToQueueItem(item.queueId) }
            return
        }
        // Les autres éléments viennent du catalogue Tidal : ils doivent partir vers Tidal, pas vers l'appli en cours.
        if (item.mediaId.isBlank()) {
            playSearch(item.title, openTidal)
            return
        }
        val current = controller
        if (current?.packageName == KnownApps.TIDAL) {
            runCatching { current.transportControls.playFromMediaId(item.mediaId, null) }
            return
        }
        current?.takeIf { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.transportControls?.pause()
        val session = sessionsFor(KnownApps.TIDAL).firstOrNull()
        if (session != null) {
            session.transportControls.playFromMediaId(item.mediaId, null)
            bind(session)
        } else if (!withBrowserController(KnownApps.TIDAL) { it.transportControls.playFromMediaId(item.mediaId, null) }) {
            playSearch(item.title, openTidal)
        }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        val controls = c.transportControls ?: return
        val duration = _state.value.durationMs
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        controls.seekTo(target)
    }

    fun seekBy(deltaMs: Long) {
        val s = _state.value
        val elapsed = if (s.isPlaying && s.positionUpdatedElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - s.positionUpdatedElapsedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val current = s.positionMs + elapsed
        seekTo(current + deltaMs)
    }

    /** Recherche et lecture dans Tidal, même si une autre appli joue, sans ouvrir Tidal. */
    fun playSearch(query: String, openApp: () -> Unit) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = controller
        if (current?.packageName == KnownApps.TIDAL) {
            current.transportControls.playFromSearch(trimmed, null)
            return
        }
        current?.takeIf { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.transportControls?.pause()
        val session = sessionsFor(KnownApps.TIDAL).firstOrNull()
        if (session != null) {
            session.transportControls.playFromSearch(trimmed, null)
            bind(session)
        } else if (!withBrowserController(KnownApps.TIDAL) { it.transportControls.playFromSearch(trimmed, null) }) {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(KnownApps.TIDAL)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(android.app.SearchManager.QUERY, trimmed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isFailure) openApp()
        }
    }

    fun playMediaId(mediaId: String) {
        controller?.transportControls?.playFromMediaId(mediaId, null)
    }

    fun playPause() {
        val c = controller
        val controls = c?.transportControls
        if (c == null || controls == null) {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    fun play() {
        val controls = controller?.transportControls
        if (controls == null) sendKey(KeyEvent.KEYCODE_MEDIA_PLAY) else controls.play()
    }

    fun pause() {
        val controls = controller?.transportControls
        if (controls == null) sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE) else controls.pause()
    }

    fun next() {
        val controls = controller?.transportControls
        if (controls == null) sendKey(KeyEvent.KEYCODE_MEDIA_NEXT) else controls.skipToNext()
    }

    fun previous() {
        val controls = controller?.transportControls
        if (controls == null) sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) else controls.skipToPrevious()
    }

    private fun sendKey(keyCode: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    /**
     * Bascule la lecture vers une autre appli média sans l'ouvrir :
     * 1. session déjà connue (appli en pause) → play() ;
     * 2. sinon, connexion à son MediaBrowserService (Android Auto) → play() ;
     * 3. si rien ne joue au bout de quelques secondes → [openApp] en dernier recours.
     */
    fun switchTo(pkg: String, openApp: () -> Unit) {
        val current = controller
        if (current?.packageName == pkg) {
            if (current.playbackState?.state != PlaybackState.STATE_PLAYING) current.transportControls.play()
            return
        }
        current?.takeIf { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.transportControls?.pause()

        val target = sessionsFor(pkg).firstOrNull {
            val st = it.playbackState?.state
            st != null && st != PlaybackState.STATE_NONE && st != PlaybackState.STATE_ERROR
        }
        if (target != null) {
            target.transportControls.play()
            bind(target)
        } else if (!playThroughBrowser(pkg)) {
            openApp()
            return
        }
        handler.postDelayed({
            val playing = sessionsFor(pkg).any {
                it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    it.playbackState?.state == PlaybackState.STATE_BUFFERING
            }
            if (!playing) openApp()
        }, SWITCH_CHECK_MS)
    }

    private fun sessionsFor(pkg: String): List<MediaController> = runCatching {
        sessionManager.getActiveSessions(CarNotificationListener.component)
    }.getOrNull().orEmpty().filter { it.packageName == pkg }

    private fun playThroughBrowser(pkg: String): Boolean =
        withBrowserController(pkg) { it.transportControls.play() }

    /** Se connecte au MediaBrowserService de l'appli (celui d'Android Auto) pour piloter sa session. */
    private fun withBrowserController(pkg: String, action: (MediaController) -> Unit): Boolean {
        val service = context.packageManager.queryIntentServices(
            Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg),
            0,
        ).firstOrNull()?.serviceInfo ?: return false
        var browserRef: MediaBrowser? = null
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val b = browserRef ?: return
                runCatching { action(MediaController(context, b.sessionToken)) }
                // Laisse le temps à l'appli de démarrer son service avant de se déconnecter.
                handler.postDelayed({ runCatching { b.disconnect() } }, SWITCH_CHECK_MS)
            }
        }
        browserRef = runCatching {
            MediaBrowser(context, ComponentName(pkg, service.name), callback, null).also { it.connect() }
        }.getOrNull()
        return browserRef != null
    }

    // ---------- Catalogue Tidal ----------
    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()
    private var catalogBrowser: MediaBrowser? = null
    private var catalogRoot: String? = null

    fun openCatalog() {
        if (catalogBrowser?.isConnected == true) {
            loadCatalog(_catalog.value.path.lastOrNull()?.mediaId ?: catalogRoot ?: return)
            return
        }
        _catalog.value = CatalogState(loading = true)
        val service = context.packageManager.queryIntentServices(
            Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE).setPackage(KnownApps.TIDAL),
            0,
        ).firstOrNull()?.serviceInfo
        if (service == null) {
            _catalog.value = CatalogState(error = "Tidal n'est pas installé")
            return
        }
        var ref: MediaBrowser? = null
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val b = ref ?: return
                catalogRoot = b.root
                loadCatalog(b.root)
            }

            override fun onConnectionFailed() {
                catalogBrowser = null
                _catalog.value = CatalogState(error = "Tidal refuse la connexion")
            }

            override fun onConnectionSuspended() {
                catalogBrowser = null
            }
        }
        ref = runCatching {
            MediaBrowser(context, ComponentName(KnownApps.TIDAL, service.name), callback, null).also { it.connect() }
        }.getOrNull()
        catalogBrowser = ref
    }

    private fun loadCatalog(parentId: String) {
        val b = catalogBrowser ?: return
        _catalog.value = _catalog.value.copy(loading = true, error = null)
        runCatching { b.unsubscribe(parentId) }
        b.subscribe(parentId, object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(id: String, children: MutableList<MediaBrowser.MediaItem>) {
                val currentId = _catalog.value.path.lastOrNull()?.mediaId ?: catalogRoot
                if (id != currentId) return
                _catalog.value = _catalog.value.copy(
                    loading = false,
                    items = children.map { item ->
                        BrowseMediaItem(
                            mediaId = item.mediaId.orEmpty(),
                            title = item.description.title?.toString().orEmpty(),
                            subtitle = item.description.subtitle?.toString().orEmpty(),
                            playable = item.isPlayable,
                            browsable = item.isBrowsable,
                        )
                    }.filter { it.title.isNotBlank() },
                )
            }

            override fun onError(id: String) {
                _catalog.value = _catalog.value.copy(loading = false, error = "Impossible de charger ce dossier")
            }
        })
    }

    fun browseCatalog(item: BrowseMediaItem) {
        _catalog.value = _catalog.value.copy(path = _catalog.value.path + item, items = emptyList())
        loadCatalog(item.mediaId)
    }

    /** Remonte d'un niveau ; renvoie false si on était déjà à la racine. */
    fun catalogBack(): Boolean {
        val path = _catalog.value.path
        if (path.isEmpty()) return false
        val parent = path.dropLast(1)
        _catalog.value = _catalog.value.copy(path = parent, items = emptyList())
        loadCatalog(parent.lastOrNull()?.mediaId ?: catalogRoot ?: return false)
        return true
    }

    fun playCatalogItem(item: BrowseMediaItem) {
        val b = catalogBrowser?.takeIf { it.isConnected }
        if (b == null) {
            playSearch(item.title) {}
            return
        }
        controller?.takeIf { it.packageName != KnownApps.TIDAL && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.transportControls?.pause()
        runCatching { MediaController(context, b.sessionToken).transportControls.playFromMediaId(item.mediaId, null) }
    }

    fun closeCatalog() {
        runCatching { catalogBrowser?.disconnect() }
        catalogBrowser = null
    }

    fun openCurrentApp(): Boolean {
        val pkg = controller?.packageName ?: _state.value.packageName
        if (pkg.isBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isBlank()) return ""
        return labelCache.getOrPut(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault("")
        }
    }

    private fun appIcon(pkg: String): Bitmap? {
        if (pkg.isBlank()) return null
        return if (iconCache.containsKey(pkg)) {
            iconCache[pkg]
        } else {
            val bitmap = runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
            }.getOrNull()
            iconCache[pkg] = bitmap
            bitmap
        }
    }
}
