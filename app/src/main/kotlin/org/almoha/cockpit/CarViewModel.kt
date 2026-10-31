package org.almoha.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.almoha.cockpit.data.AppEntry
import org.almoha.cockpit.data.AppsRepository
import org.almoha.cockpit.data.ContactFav
import org.almoha.cockpit.data.ContactsRepository
import org.almoha.cockpit.data.CockpitPrefs
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.data.NavMode
import org.almoha.cockpit.data.Prefs
import org.almoha.cockpit.data.WeatherInfo
import org.almoha.cockpit.data.WeatherRepository
import org.almoha.cockpit.media.CarNotificationListener
import org.almoha.cockpit.media.MediaRepository
import org.almoha.cockpit.media.MediaUiState
import org.almoha.cockpit.media.NavDirectionState
import org.almoha.cockpit.service.FloatingCockpitService
import org.almoha.cockpit.util.Launcher

data class PermissionsState(
    val call: Boolean = false,
    val contacts: Boolean = false,
    val location: Boolean = false,
    val notifications: Boolean = false,
    val bluetooth: Boolean = false,
    val notificationAccess: Boolean = false,
    val overlay: Boolean = false,
) {
    val allCoreGranted: Boolean
        get() = call && contacts && location && notifications
}

data class BtDevice(val name: String, val address: String)

data class UiState(
    val apps: List<AppEntry> = emptyList(),
    val dock: List<AppEntry> = emptyList(),
    val contacts: List<ContactFav> = emptyList(),
    val media: MediaUiState = MediaUiState(),
    val navDirection: NavDirectionState = NavDirectionState(),
    val weather: WeatherInfo? = null,
    val batteryLevel: Int = 100,
    val charging: Boolean = false,
    val prefs: CockpitPrefs = CockpitPrefs(),
    val permissions: PermissionsState = PermissionsState(),
    val bluetoothDevices: List<BtDevice> = emptyList(),
    val dim: Boolean = false,
    val weatherLoading: Boolean = false,
)

class CarViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val prefs = Prefs(context)
    private val appsRepo = AppsRepository(context)
    private val contactsRepo = ContactsRepository(context)
    private val weatherRepo = WeatherRepository(context)
    private val mediaRepo = MediaRepository(context)

    private val _state = MutableStateFlow(UiState(prefs = prefs.state.value))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastWeatherFetch = 0L
    private var weatherJob: Job? = null
    private var receiverRegistered = false
    private var iconsLoading = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra("level", 0) ?: return
            val scale = intent.getIntExtra("scale", 100).coerceAtLeast(1)
            val status = intent.getIntExtra("status", 0)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            _state.update {
                it.copy(
                    batteryLevel = level * 100 / scale,
                    charging = charging,
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            val apps = appsRepo.loadLabels()
            _state.update {
                it.copy(apps = apps, dock = resolveDock(prefs.state.value.dock, apps))
            }
            val priority = (
                appsRepo.defaultDock(apps.mapTo(HashSet()) { it.packageName }) +
                    prefs.state.value.dock +
                    listOf(
                        KnownApps.MAPS,
                        KnownApps.TIDAL,
                        KnownApps.ELECTROVERSE,
                        KnownApps.DIALER,
                        KnownApps.FLUX_MEDIAS,
                    ) +
                    KnownApps.MEDIA_SOURCES
                ).toSet()
            val priorityIcons = apps
                .filter { it.packageName in priority }
                .mapNotNull { app -> appsRepo.loadIcon(app.packageName)?.let { app.packageName to it } }
                .toMap()
            // Fusionne avec l'état courant pour ne pas écraser les icônes chargées entre-temps par loadAllIcons().
            _state.update { current ->
                val merged = current.apps.map { app ->
                    if (app.icon == null) priorityIcons[app.packageName]?.let { app.copy(icon = it) } ?: app else app
                }
                current.copy(apps = merged, dock = resolveDock(prefs.state.value.dock, merged))
            }
        }
        viewModelScope.launch {
            prefs.state.collect { p ->
                _state.update { current ->
                    current.copy(prefs = p, dock = resolveDock(p.dock, current.apps))
                }
            }
        }
        viewModelScope.launch {
            mediaRepo.state.collect { m ->
                _state.update { it.copy(media = m) }
            }
        }
        viewModelScope.launch {
            CarNotificationListener.navState.collect { nav ->
                _state.update { it.copy(navDirection = nav) }
            }
        }
        registerBattery()
        refreshPermissions()
        mediaRepo.start()
        loadContacts()
        loadBluetoothDevices()
        refreshWeather(force = false)
    }

    private fun resolveDock(chosen: List<String>, apps: List<AppEntry>): List<AppEntry> {
        if (apps.isEmpty()) return emptyList()
        val installed = apps.mapTo(HashSet()) { it.packageName }
        val wanted = if (chosen.isEmpty()) appsRepo.defaultDock(installed) else chosen
        val byPackage = apps.associateBy { it.packageName }
        return wanted.mapNotNull { byPackage[it] }.take(8)
    }

    private fun registerBattery() {
        if (receiverRegistered) return
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiver.onReceive(context, sticky)
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun onResume() {
        prefs.beginSession()
        refreshPermissions()
        mediaRepo.start()
        CarNotificationListener.refreshNavigation()
        loadContacts()
        loadBluetoothDevices()
        refreshWeather(force = false)
    }

    fun onPause() = Unit

    fun loadAllIcons() {
        if (iconsLoading) return
        iconsLoading = true
        viewModelScope.launch {
            val missing = _state.value.apps.filter { it.icon == null }
            missing.chunked(12).forEach { batch ->
                val icons = batch.mapNotNull { app ->
                    appsRepo.loadIcon(app.packageName)?.let { app.packageName to it }
                }.toMap()
                if (icons.isNotEmpty()) {
                    _state.update { current ->
                        current.copy(
                            apps = current.apps.map { app ->
                                icons[app.packageName]?.let { app.copy(icon = it) } ?: app
                            },
                        )
                    }
                }
            }
            iconsLoading = false
        }
    }

    fun refreshPermissions() {
        val permissions = PermissionsState(
            call = granted(Manifest.permission.CALL_PHONE),
            contacts = granted(Manifest.permission.READ_CONTACTS),
            location = granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION),
            notifications = if (Build.VERSION.SDK_INT >= 33) {
                granted(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            bluetooth = if (Build.VERSION.SDK_INT >= 31) {
                granted(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                true
            },
            notificationAccess = CarNotificationListener.hasAccess(context),
            overlay = Settings.canDrawOverlays(context),
        )
        _state.update {
            it.copy(
                permissions = permissions,
                media = it.media.copy(hasAccess = permissions.notificationAccess),
            )
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun loadContacts() {
        viewModelScope.launch {
            val contacts = contactsRepo.loadFavorites()
            _state.update { it.copy(contacts = contacts) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBluetoothDevices() {
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            _state.update { it.copy(bluetoothDevices = emptyList()) }
            return
        }
        val adapter = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        }.getOrNull()
        val devices = runCatching {
            adapter?.bondedDevices.orEmpty().map {
                BtDevice(it.name ?: it.address, it.address)
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
        _state.update { it.copy(bluetoothDevices = devices) }
    }

    fun refreshWeather(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherFetch < 20 * 60 * 1000L) return
        if (!weatherRepo.hasPermission()) return
        if (weatherJob?.isActive == true) return
        _state.update { it.copy(weatherLoading = true) }
        weatherJob = viewModelScope.launch(Dispatchers.IO) {
            val weather = weatherRepo.load()
            // En cas d'échec (pas encore de position, réseau absent), on retentera au prochain onResume.
            if (weather != null) lastWeatherFetch = System.currentTimeMillis()
            _state.update { it.copy(weather = weather ?: it.weather, weatherLoading = false) }
        }
    }

    /**
     * Appelé avant d'ouvrir une autre application. L'activité s'en sert pour quitter l'épinglage d'écran,
     * qui bloquerait sinon tout lancement (Maps, Tidal, appel...). Il sera rétabli au retour dans Cockpit.
     */
    var beforeExternalLaunch: (() -> Unit)? = null

    private inline fun <T> external(block: () -> T): T {
        beforeExternalLaunch?.invoke()
        return block()
    }

    fun launchApp(pkg: String): Boolean = external {
        val currentPrefs = _state.value.prefs
        if (pkg == KnownApps.MAPS &&
            (currentPrefs.navMode == NavMode.FLOATING_SPLIT || currentPrefs.floatingBar) &&
            Settings.canDrawOverlays(context)
        ) {
            FloatingCockpitService.show(context)
        }
        appsRepo.launch(pkg)
    }

    fun navigate(pkg: String, query: String?): Boolean = external {
        val currentPrefs = _state.value.prefs
        if ((currentPrefs.navMode == NavMode.FLOATING_SPLIT || currentPrefs.floatingBar) &&
            Settings.canDrawOverlays(context)
        ) {
            FloatingCockpitService.show(context)
        }
        Launcher.navigate(context, pkg, query)
    }

    fun openAssistant() = external { Launcher.assistant(context) }

    fun call(number: String) = external { Launcher.call(context, number) }

    fun openDialer() = external { Launcher.openDialer(context) }

    fun openContacts() = external { Launcher.openContacts(context) }

    fun openCharging() = external {
        Launcher.openCharging(context, _state.value.apps.mapTo(HashSet()) { it.packageName })
    }

    fun openMediaApp() {
        val pkg = _state.value.media.packageName.ifBlank {
            listOf(
                KnownApps.TIDAL,
                KnownApps.YT_MUSIC,
                KnownApps.PODCATCHER,
                KnownApps.ZENTUBE,
                KnownApps.FLUX_MEDIAS,
            ).firstOrNull { appsRepo.isInstalled(it) }.orEmpty()
        }
        if (pkg.isNotBlank()) external { appsRepo.launch(pkg) }
    }

    /** Dock : une appli média bascule la lecture en arrière-plan ; les autres s'ouvrent. */
    fun openFromDock(pkg: String) {
        if (pkg in KnownApps.MEDIA_SOURCES) switchMediaSource(pkg) else launchApp(pkg)
    }

    val tidalCatalog get() = mediaRepo.catalog
    fun openTidalCatalog() = mediaRepo.openCatalog()
    fun browseTidal(item: org.almoha.cockpit.media.BrowseMediaItem) = mediaRepo.browseCatalog(item)
    fun tidalCatalogBack() = mediaRepo.catalogBack()
    fun playTidalItem(item: org.almoha.cockpit.media.BrowseMediaItem) = mediaRepo.playCatalogItem(item)
    fun closeTidalCatalog() = mediaRepo.closeCatalog()

    fun switchMediaSource(pkg: String) = mediaRepo.switchTo(pkg) { external { appsRepo.launch(pkg) } }

    fun playPause() = mediaRepo.playPause()
    fun play() = mediaRepo.play()
    fun pause() = mediaRepo.pause()

    fun adjustVolume(up: Boolean) {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return
        audio.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI,
        )
    }
    fun next() = mediaRepo.next()
    fun previous() = mediaRepo.previous()
    fun seekTo(ms: Long) = mediaRepo.seekTo(ms)
    fun seekBy(deltaMs: Long) = mediaRepo.seekBy(deltaMs)
    fun rewind10s() = mediaRepo.seekBy(-10_000L)
    fun forward10s() = mediaRepo.seekBy(10_000L)
    fun toggleFavorite() = mediaRepo.toggleFavorite()
    fun playSearchInTidal(query: String) =
        mediaRepo.playSearch(query) { external { appsRepo.launch(KnownApps.TIDAL) } }
    fun playMediaId(mediaId: String) = mediaRepo.playMediaId(mediaId)
    fun playBrowseItem(item: org.almoha.cockpit.media.BrowseMediaItem) =
        mediaRepo.playBrowseItem(item) { external { appsRepo.launch(KnownApps.TIDAL) } }
    fun openNotificationSettings() = external { Launcher.openNotificationAccess(context) }
    fun openOverlaySettings() = external { Launcher.openOverlaySettings(context) }

    fun setDim(value: Boolean) = _state.update { it.copy(dim = value) }
    fun toggleDim() = _state.update { it.copy(dim = !it.dim) }

    fun setHome(value: String) = prefs.setHome(value)
    fun addNavFavorite(name: String, address: String) = prefs.addNavFavorite(name, address)
    fun removeNavFavorite(address: String) = prefs.removeNavFavorite(address)
    fun setWork(value: String) = prefs.setWork(value)
    fun setDock(packages: List<String>) = prefs.setDock(packages)
    fun setAutoLaunch(value: Boolean) = prefs.setAutoLaunch(value)
    fun setReadMessages(value: Boolean) = prefs.setReadMessages(value)
    fun setKeepScreenOn(value: Boolean) = prefs.setKeepScreenOn(value)
    fun setLockDown(value: Boolean) = prefs.setLockDown(value)
    fun setOnboardingDone() = prefs.setOnboardingDone()
    fun setCarDevice(name: String, address: String) = prefs.setCarDevice(name, address)
    fun setNavMode(mode: NavMode) = prefs.setNavMode(mode)
    fun setFloatingBar(value: Boolean) = prefs.setFloatingBar(value)
    fun cycleNavMode() {
        val next = when (_state.value.prefs.navMode) {
            NavMode.EMBEDDED_MAP -> NavMode.FLOATING_SPLIT
            NavMode.FLOATING_SPLIT -> NavMode.LIVE_DIRECTIONS
            NavMode.LIVE_DIRECTIONS -> NavMode.EMBEDDED_MAP
        }
        prefs.setNavMode(next)
    }

    fun toggleDock(pkg: String) {
        val current = _state.value.prefs.dock.ifEmpty {
            _state.value.dock.map { it.packageName }
        }.toMutableList()
        if (pkg in current) {
            current.remove(pkg)
        } else if (current.size < 8) {
            current.add(pkg)
        }
        prefs.setDock(current)
    }

    fun exit() {
        prefs.endSession()
        mediaRepo.stop()
    }

    override fun onCleared() {
        super.onCleared()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
        }
        beforeExternalLaunch = null
        mediaRepo.stop()
    }
}
