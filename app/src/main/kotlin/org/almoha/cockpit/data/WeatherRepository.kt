package org.almoha.cockpit.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class WeatherInfo(
    val temp: Int,
    val min: Int,
    val max: Int,
    val code: Int,
    val isDay: Boolean,
)

class WeatherRepository(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun load(): WeatherInfo? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val location = lastKnownLocation() ?: return@withContext null
        fetch(location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        return providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: requestOnce(manager, providers)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(
        manager: LocationManager,
        providers: List<String>,
    ): Location? {
        val provider = providers.firstOrNull() ?: return null
        return withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    }
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                }
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): WeatherInfo? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            WeatherInfo(
                temp = Math.round(current.getDouble("temperature_2m")).toInt(),
                min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
