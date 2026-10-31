package org.almoha.cockpit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class NavFavorite(val name: String, val address: String)

enum class NavMode {
    FLOATING_SPLIT,
    EMBEDDED_MAP,
    LIVE_DIRECTIONS,
}

data class CockpitPrefs(
    val dock: List<String> = emptyList(),
    val home: String = "",
    val work: String = "",
    val carBtName: String = "",
    val carBtAddress: String = "",
    val autoLaunch: Boolean = false,
    val readMessages: Boolean = true,
    val keepScreenOn: Boolean = true,
    val lockDown: Boolean = false,
    val onboardingDone: Boolean = false,
    val navMode: NavMode = NavMode.EMBEDDED_MAP,
    val floatingBar: Boolean = true,
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherAt: Long = 0L,
    val sessionStartedAt: Long = 0L,
    val navFavorites: List<NavFavorite> = emptyList(),
) {
    fun isSessionActive(now: Long = System.currentTimeMillis()): Boolean =
        sessionStartedAt > 0L && now - sessionStartedAt < 3 * 60 * 60 * 1000L
}

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<CockpitPrefs> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = read()
    }

    init {
        migrateDock()
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    // Electroverse est déjà accessible depuis la carte : on le remplace une fois par FluxMedias dans le dock.
    private fun migrateDock() {
        if (sp.getBoolean(KEY_DOCK_FLUX_MIGRATED, false)) return
        val dock = sp.getString(KEY_DOCK, null)?.split("|")?.filter { it.isNotBlank() }
        val editor = sp.edit().putBoolean(KEY_DOCK_FLUX_MIGRATED, true)
        if (dock != null && KnownApps.ELECTROVERSE in dock) {
            val migrated = dock
                .map { if (it == KnownApps.ELECTROVERSE) KnownApps.FLUX_MEDIAS else it }
                .distinct()
            editor.putString(KEY_DOCK, migrated.joinToString("|"))
        }
        editor.apply()
        _state.value = read()
    }

    fun read(): CockpitPrefs {
        val rawMode = sp.getString(KEY_NAV_MODE, NavMode.EMBEDDED_MAP.name) ?: NavMode.EMBEDDED_MAP.name
        val navMode = runCatching { NavMode.valueOf(rawMode) }.getOrDefault(NavMode.EMBEDDED_MAP)
        return CockpitPrefs(
            dock = sp.getString(KEY_DOCK, null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            home = sp.getString(KEY_HOME, "") ?: "",
            work = sp.getString(KEY_WORK, "") ?: "",
            carBtName = sp.getString(KEY_CAR_NAME, "") ?: "",
            carBtAddress = sp.getString(KEY_CAR_ADDRESS, "") ?: "",
            autoLaunch = sp.getBoolean(KEY_AUTO_LAUNCH, false),
            readMessages = sp.getBoolean(KEY_READ_MESSAGES, true),
            keepScreenOn = sp.getBoolean(KEY_KEEP_SCREEN_ON, true),
            lockDown = sp.getBoolean(KEY_LOCK_DOWN, false),
            onboardingDone = sp.getBoolean(KEY_ONBOARDING, false),
            navMode = navMode,
            floatingBar = sp.getBoolean(KEY_FLOATING_BAR, true),
            weatherLat = sp.getString(KEY_WEATHER_LAT, null)?.toDoubleOrNull(),
            weatherLon = sp.getString(KEY_WEATHER_LON, null)?.toDoubleOrNull(),
            weatherAt = sp.getLong(KEY_WEATHER_AT, 0L),
            sessionStartedAt = sp.getLong(KEY_SESSION_START, 0L),
            navFavorites = readFavorites(),
        )
    }

    private fun readFavorites(): List<NavFavorite> {
        val raw = sp.getString(KEY_NAV_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                NavFavorite(o.getString("name"), o.getString("address"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeFavorites(list: List<NavFavorite>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("name", it.name).put("address", it.address)) }
        sp.edit().putString(KEY_NAV_FAVORITES, array.toString()).apply()
        refresh()
    }

    /** Ajoute ou met à jour un favori (même nom ou même adresse = remplacé). */
    fun addNavFavorite(name: String, address: String) {
        val n = name.trim()
        val a = address.trim()
        if (n.isEmpty() || a.isEmpty()) return
        val others = readFavorites().filterNot {
            it.name.equals(n, ignoreCase = true) || it.address.equals(a, ignoreCase = true)
        }
        writeFavorites(others + NavFavorite(n, a))
    }

    fun removeNavFavorite(address: String) {
        writeFavorites(readFavorites().filterNot { it.address.equals(address, ignoreCase = true) })
    }

    private fun refresh() {
        _state.value = read()
    }

    fun setDock(packages: List<String>) {
        sp.edit().putString(KEY_DOCK, packages.distinct().take(8).joinToString("|")).apply()
        refresh()
    }

    fun setHome(value: String) {
        sp.edit().putString(KEY_HOME, value).apply()
        refresh()
    }

    fun setWork(value: String) {
        sp.edit().putString(KEY_WORK, value).apply()
        refresh()
    }

    fun setCarDevice(name: String, address: String) {
        sp.edit().putString(KEY_CAR_NAME, name).putString(KEY_CAR_ADDRESS, address).apply()
        refresh()
    }

    fun setAutoLaunch(value: Boolean) {
        sp.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()
        refresh()
    }

    fun setReadMessages(value: Boolean) {
        sp.edit().putBoolean(KEY_READ_MESSAGES, value).apply()
        refresh()
    }

    fun setKeepScreenOn(value: Boolean) {
        sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        refresh()
    }

    fun setLockDown(value: Boolean) {
        sp.edit().putBoolean(KEY_LOCK_DOWN, value).apply()
        refresh()
    }

    fun setOnboardingDone() {
        sp.edit().putBoolean(KEY_ONBOARDING, true).apply()
        refresh()
    }

    fun setNavMode(mode: NavMode) {
        sp.edit().putString(KEY_NAV_MODE, mode.name).apply()
        refresh()
    }

    fun setFloatingBar(value: Boolean) {
        sp.edit().putBoolean(KEY_FLOATING_BAR, value).apply()
        refresh()
    }

    fun setWeather(lat: Double, lon: Double, at: Long) {
        sp.edit()
            .putString(KEY_WEATHER_LAT, lat.toString())
            .putString(KEY_WEATHER_LON, lon.toString())
            .putLong(KEY_WEATHER_AT, at)
            .apply()
        refresh()
    }

    fun beginSession() {
        sp.edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
        refresh()
    }

    fun endSession() {
        sp.edit().putLong(KEY_SESSION_START, 0L).apply()
        refresh()
    }

    companion object {
        private const val NAME = "cockpit"
        private const val KEY_DOCK = "dock"
        private const val KEY_HOME = "home"
        private const val KEY_WORK = "work"
        private const val KEY_CAR_NAME = "car_bt_name"
        private const val KEY_CAR_ADDRESS = "car_bt_address"
        private const val KEY_AUTO_LAUNCH = "auto_launch"
        private const val KEY_READ_MESSAGES = "read_messages"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_LOCK_DOWN = "lock_down"
        private const val KEY_ONBOARDING = "onboarding"
        private const val KEY_NAV_MODE = "nav_mode"
        private const val KEY_FLOATING_BAR = "floating_bar"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_WEATHER_AT = "weather_at"
        private const val KEY_SESSION_START = "session_start"
        private const val KEY_DOCK_FLUX_MIGRATED = "dock_flux_migrated"
        private const val KEY_NAV_FAVORITES = "nav_favorites"
    }
}
