package org.almoha.cockpit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import org.almoha.cockpit.data.AppEntry
import org.almoha.cockpit.data.NavFavorite
import org.json.JSONObject
import kotlin.math.roundToInt

/** Prochaine manœuvre du guidage, affichée dans le bandeau vert. */
data class NavStep(
    val distance: String = "",
    val direction: String = "",
    val instruction: String = "",
    val street: String = "",
)

private class MapBridge(
    private val mainHandler: Handler,
    private val onNavStateChanged: (Boolean, NavStep, String) -> Unit,
) {
    @JavascriptInterface
    fun onNavigationUpdate(
        active: Boolean,
        distance: String,
        direction: String,
        instruction: String,
        street: String,
        etaText: String,
    ) {
        mainHandler.post {
            onNavStateChanged(active, NavStep(distance, direction, instruction, street), etaText)
        }
    }
}

/**
 * Carte intégrée et son état de navigation. Elle vit au-dessus de l'écran Cockpit pour que la WebView
 * (et l'itinéraire en cours) survive au passage plein écran / vue 3 blocs et à l'ouverture des réglages.
 */
@Stable
class MapController(context: Context) {
    var speedKmh by mutableIntStateOf(0)
        private set
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var navActive by mutableStateOf(false)
        private set
    var navStep by mutableStateOf(NavStep())
        private set
    var navEtaText by mutableStateOf("")
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageReady = false
    private var pendingLocation: Location? = null
    private var chooserTop = 54
    private var lastGpsFixAt = 0L
    private var lastSpeedAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(android.graphics.Color.parseColor("#070C12"))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(
            MapBridge(mainHandler) { active, step, eta ->
                navActive = active
                navStep = step
                navEtaText = eta
            },
            "AndroidBridge",
        )
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageReady = true
                setChooserTop(chooserTop)
                pendingLocation?.let { pushLocation(it) }
                pendingLocation = null
            }
        }
        loadDataWithBaseURL("https://cockpit.local/", MAP_HTML, "text/html", "UTF-8", null)
    }

    private fun js(script: String) = webView.evaluateJavascript(script, null)

    fun onLocation(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        if (loc.provider == LocationManager.GPS_PROVIDER) {
            lastGpsFixAt = now
            if (loc.hasSpeed()) {
                speedKmh = (loc.speed * 3.6f).roundToInt().coerceAtLeast(0)
                lastSpeedAt = now
            }
        } else if (now - lastGpsFixAt < 10_000L) {
            // Le GPS répond : on ignore la position réseau, moins précise et sans vitesse.
            return
        }
        pushLocation(loc)
    }

    fun pushLocation(loc: Location) {
        if (!pageReady) {
            pendingLocation = loc
            return
        }
        // Cap fiable seulement en mouvement (> ~5 km/h) ; sinon -1 et la page le déduit des positions successives.
        val bearing = if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 1.4f) loc.bearing else -1f
        js("window.updateGps && window.updateGps(${loc.latitude}, ${loc.longitude}, $bearing);")
    }

    /** Sans nouvelle mesure de vitesse depuis quelques secondes (tunnel, GPS coupé), on affiche 0. */
    fun clearStaleSpeed() {
        if (speedKmh != 0 && SystemClock.elapsedRealtime() - lastSpeedAt > 4_000L) speedKmh = 0
    }

    fun setChooserTop(topDp: Int) {
        chooserTop = topDp
        if (pageReady) js("window.setChooserTop && window.setChooserTop($topDp);")
    }

    /** [startNavigation] : lance le guidage dès que l'itinéraire est calculé (commande vocale). */
    fun routeTo(destination: String, startNavigation: Boolean = false) {
        val trimmed = destination.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        searching = false
        js("window.routeToAddress(${JSONObject.quote(trimmed)}, $startNavigation);")
    }

    fun clearRoute() {
        query = ""
        js("window.clearRoute && window.clearRoute();")
    }

    fun stopNavigation() = js("window.stopNavigation && window.stopNavigation();")

    fun recenter() = js("window.recenterGps && window.recenterGps();")

    fun onStart() = webView.onResume()

    fun onStop() = webView.onPause()

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeJavascriptInterface("AndroidBridge")
        webView.destroy()
    }
}

@SuppressLint("MissingPermission")
@Composable
fun rememberMapController(hasLocationPermission: Boolean): MapController {
    val context = LocalContext.current
    val controller = remember { MapController(context) }
    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }

    val granted = hasLocationPermission ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // GPS actif seulement quand Cockpit est visible.
    LifecycleStartEffect(controller, granted) {
        controller.onStart()
        val manager = if (granted) context.getSystemService(LocationManager::class.java) else null
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) = controller.onLocation(loc)
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        if (manager != null) {
            lastKnownLocation(manager)?.let { controller.pushLocation(it) }
            runCatching {
                // Distance minimale à 0 : on reçoit des mesures même à l'arrêt, le compteur retombe à 0.
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                }
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
        onStopOrDispose {
            manager?.let { runCatching { it.removeUpdates(listener) } }
            controller.onStop()
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            delay(2_000L)
            controller.clearStaleSpeed()
        }
    }
    return controller
}

@SuppressLint("MissingPermission")
private fun lastKnownLocation(manager: LocationManager): Location? =
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }

@Composable
fun IntegratedMapContent(
    controller: MapController,
    hasLocationPermission: Boolean,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenGoogleMaps: (String?) -> Unit,
    onOpenCharging: () -> Unit,
    modifier: Modifier = Modifier,
    home: String = "",
    work: String = "",
    chargingApp: AppEntry? = null,
    mapsApp: AppEntry? = null,
    favorites: List<NavFavorite> = emptyList(),
    onAddFavorite: (String) -> Unit = {},
    onRemoveFavorite: (String) -> Unit = {},
    showAppButtons: Boolean = true,
    fullScreenTopInset: Dp = 64.dp,
    /** Position des boutons qui restent visibles au repos, pour qu'ils répondent dès le premier toucher. */
    onPersistentButtonBounds: (String, Rect) -> Unit = { _, _ -> },
    /** Barre « Où aller ? » au repos (en vue 3 blocs, remplacée par les gros boutons sous la carte). */
    showSearchPill: Boolean = true,
    fullScreenBottomInset: Dp = 146.dp,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val query = controller.query
    val navActive = controller.navActive

    val topOffset = if (fullScreen) fullScreenTopInset else 8.dp
    val bottomOffset = if (fullScreen) fullScreenBottomInset + 8.dp else 8.dp
    val shortcuts = buildList {
        if (home.isNotBlank()) add(Triple("Maison", Icons.Filled.Home, home))
        if (work.isNotBlank()) add(Triple("Travail", Icons.Filled.Work, work))
        favorites.forEach { add(Triple(it.name, Icons.Filled.Star, it.address)) }
    }
    val isFavorite = query.isNotBlank() && favorites.any { it.address.equals(query, ignoreCase = true) }
    val showShortcuts = controller.searching && shortcuts.isNotEmpty()

    // Le bandeau de choix d'itinéraire (HTML) se place sous la barre de recherche Compose.
    LaunchedEffect(topOffset, showShortcuts) {
        val below = topOffset.value + 40f + 8f + if (showShortcuts) 40f else 0f
        controller.setChooserTop(below.roundToInt())
    }

    val containerModifier = if (fullScreen) {
        modifier.background(Color(0xFF070C12))
    } else {
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF070C12))
            .border(1.dp, CockpitColors.Stroke, RoundedCornerShape(20.dp))
    }

    // Boutons partagés : en haut en vue 3 blocs, en bas en plein écran.
    val RecenterButton: @Composable () -> Unit = {
        Box(
            Modifier
                .onGloballyPositioned { onPersistentButtonBounds("recenter", it.boundsInWindow()) }
                .size(if (fullScreen) 48.dp else 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xD90B1118))
                .border(1.dp, CockpitColors.Stroke, RoundedCornerShape(12.dp))
                .clickable {
                    if (!hasLocationPermission) {
                        onRequestPermissions()
                    } else {
                        context.getSystemService(LocationManager::class.java)
                            ?.let { runCatching { lastKnownLocation(it) }.getOrNull() }
                            ?.let { controller.pushLocation(it) }
                        controller.recenter()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = "Recentrer GPS",
                tint = CockpitColors.Nav,
                modifier = Modifier.size(if (fullScreen) 24.dp else 19.dp),
            )
        }
    }
    val ToggleButton: @Composable () -> Unit = {
        Row(
            Modifier
                .onGloballyPositioned { onPersistentButtonBounds("toggle", it.boundsInWindow()) }
                .size(if (fullScreen) 48.dp else 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (fullScreen) CockpitColors.Nav else Color(0xEB0B1118))
                .border(1.5.dp, CockpitColors.Nav.copy(alpha = if (fullScreen) 0.45f else 0.8f), RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleFullScreen)
                .padding(horizontal = if (fullScreen) 0.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // En vue 3 blocs, bouton plein écran agrandi pour être facile à toucher.
            Icon(
                if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (fullScreen) "Vue partagée" else "Carte plein écran",
                tint = if (fullScreen) Color.Black else CockpitColors.Nav,
                modifier = Modifier.size(if (fullScreen) 28.dp else 30.dp),
            )
        }
    }

    Box(containerModifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                // La même WebView est réutilisée : on la détache de son ancien parent éventuel.
                controller.webView.also { (it.parent as? ViewGroup)?.removeView(it) }
            },
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = topOffset),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Pendant le guidage : bandeau vert pleine largeur avec la prochaine manœuvre, façon Google Maps.
            // En vue 3 blocs, la place manque : seule la flèche (et la distance) est affichée.
            if (navActive && fullScreen) {
                NavStepBanner(controller.navStep)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (navActive) {
                    if (!fullScreen) NavStepBadge(controller.navStep)
                    Spacer(Modifier.weight(1f))
                } else if (controller.searching) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xF00B1118))
                            .border(1.5.dp, CockpitColors.Nav, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = CockpitColors.Nav,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        val focusRequester = remember { FocusRequester() }
                        // Ouvre directement le clavier dans le champ.
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Entrez une adresse ou ville...", color = CockpitColors.TextMuted, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { controller.query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = CockpitColors.Text, fontSize = 14.sp),
                                cursorBrush = SolidColor(CockpitColors.Nav),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        controller.routeTo(query)
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            )
                        }
                        if (query.isNotBlank()) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CockpitColors.Nav)
                                    .clickable {
                                        focusManager.clearFocus()
                                        controller.routeTo(query)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Navigation,
                                    contentDescription = "Calculer les itinéraires",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Fermer",
                            tint = CockpitColors.TextDim,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    controller.searching = false
                                    focusManager.clearFocus()
                                },
                        )
                    }
                } else if ((fullScreen && !showAppButtons) || !showSearchPill) {
                    // Plein écran au repos : seuls « recentrer » et « vue 3 blocs » restent affichés.
                    Spacer(Modifier.weight(1f))
                } else {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xD90B1118))
                            .border(1.dp, CockpitColors.Stroke, RoundedCornerShape(14.dp))
                            .clickable { controller.searching = true }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = CockpitColors.Nav,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            query.ifBlank { "Où aller ? (Rechercher)" },
                            color = if (query.isBlank()) CockpitColors.TextDim else CockpitColors.Text,
                            fontSize = 12.sp,
                            fontWeight = if (query.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (query.isNotBlank()) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                                tint = if (isFavorite) Color(0xFFFFC94D) else CockpitColors.TextDim,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        if (isFavorite) onRemoveFavorite(query) else onAddFavorite(query)
                                    },
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Effacer l'itinéraire",
                                tint = CockpitColors.TextDim,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { controller.clearRoute() },
                            )
                        }
                    }
                }

                if (!fullScreen) {
                    RecenterButton()
                    ToggleButton()
                }
            }

            // Raccourcis Maison / Travail (réglages > Destinations) pendant la recherche
            if (showShortcuts) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    shortcuts.forEach { (label, icon, address) ->
                        MapChip(label, icon) {
                            focusManager.clearFocus()
                            controller.routeTo(address)
                        }
                    }
                }
            }
        }

        // ==================== BOTTOM BAR: SPEED + DISCREET ETA/STOP PILL + ELECTROVERSE + MAPS ====================
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = bottomOffset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // weight(fill = false) : les boutons de droite gardent leur taille, c'est la pastille ETA qui cède.
            Row(
                Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Vitesse façon panneau routier : disque blanc cerclé de rouge.
                SpeedSign(controller.speedKmh, if (fullScreen) 96.dp else 64.dp)

                // Discreet Google Maps-style ETA + Stop pill when navigating
                if (navActive) {
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xEB080D13))
                            .border(1.dp, Color(0xFF1ED760).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(start = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            controller.navEtaText,
                            color = Color(0xFF1ED760),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CockpitColors.Warning.copy(alpha = 0.22f))
                                .clickable { controller.stopNavigation() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Arrêter la navigation",
                                tint = CockpitColors.Warning,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Masquées quand le dock est caché et pendant le guidage, pour libérer la carte.
                AnimatedVisibility(visible = showAppButtons && !navActive, enter = fadeIn(), exit = fadeOut()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapAppButton(chargingApp, Icons.Filled.EvStation, CockpitColors.Ev, "Recharge", onOpenCharging)
                        MapAppButton(mapsApp, Icons.AutoMirrored.Filled.OpenInNew, CockpitColors.Nav, "Google Maps") {
                            onOpenGoogleMaps(query.ifBlank { null })
                        }
                    }
                }
                if (fullScreen) {
                    RecenterButton()
                    ToggleButton()
                }
            }
        }
    }
}

@Composable
private fun SpeedSign(speedKmh: Int, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(size * 0.11f, Color(0xFFD7262E), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$speedKmh",
            color = Color.Black,
            fontSize = (size.value * if (speedKmh >= 100) 0.34f else 0.42f).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavStepBadge(step: NavStep) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(52.dp)
            .clip(shape)
            .background(Color(0xFF0F7A4D))
            .border(1.dp, Color(0xFF34C77B), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            maneuverIcon(step.direction),
            contentDescription = step.instruction,
            tint = Color.White,
            modifier = Modifier.size(36.dp),
        )
        if (step.distance.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(step.distance, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun NavStepBanner(step: NavStep) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0F7A4D))
            .border(1.dp, Color(0xFF34C77B), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            maneuverIcon(step.direction),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (step.distance.isNotBlank()) {
                Text(
                    step.distance,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                step.instruction.ifBlank { "Suivez l'itinéraire" },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (step.street.isNotBlank()) {
                Text(
                    step.street,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun maneuverIcon(direction: String): ImageVector = when (direction) {
    "left" -> Icons.Filled.TurnLeft
    "right" -> Icons.Filled.TurnRight
    "slight left" -> Icons.Filled.TurnSlightLeft
    "slight right" -> Icons.Filled.TurnSlightRight
    "sharp left" -> Icons.Filled.TurnSharpLeft
    "sharp right" -> Icons.Filled.TurnSharpRight
    "uturn" -> Icons.Filled.UTurnLeft
    "roundabout" -> Icons.Filled.RoundaboutRight
    "arrive" -> Icons.Filled.Flag
    else -> Icons.Filled.Straight
}

@Composable
private fun MapAppButton(
    app: AppEntry?,
    fallback: ImageVector,
    accent: Color,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(Color(0xEB080D13))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (app?.icon != null) {
            AppImage(app, 30.dp)
        } else {
            Icon(fallback, contentDescription = description, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MapChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF00B1118))
            .border(1.dp, CockpitColors.Nav.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CockpitColors.Nav, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = CockpitColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val MAP_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/maplibre-gl@4.7.1/dist/maplibre-gl.css" />
<script src="https://cdn.jsdelivr.net/npm/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
<style>
  html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #070C12; overflow: hidden; font-family: sans-serif; }
  .maplibregl-ctrl-attrib, .maplibregl-ctrl-logo { display: none !important; }
  /* Compact single-line route chooser bar before navigation starts */
  #routeChooser {
    position: absolute; top: 54px; left: 10px; right: 10px; z-index: 999;
    background: rgba(8, 13, 20, 0.90); color: #EAF0F6; padding: 6px 8px;
    border-radius: 14px; border: 1px solid rgba(77, 208, 225, 0.40);
    display: none; align-items: center; gap: 6px;
  }
  .route-tabs { display: flex; gap: 6px; flex: 1; overflow-x: auto; }
  .route-tab {
    padding: 6px 10px; border-radius: 10px; font-size: 11.5px; font-weight: 600;
    background: rgba(255,255,255,0.07); color: #96A2B2; text-align: center; cursor: pointer;
    border: 1px solid transparent; white-space: nowrap;
  }
  .route-tab.active {
    background: rgba(77, 208, 225, 0.22); color: #4DD0E1;
    border-color: rgba(77, 208, 225, 0.65);
  }
  .start-pill {
    background: #1ED760; color: #051409; font-weight: 800; font-size: 12px;
    padding: 7px 12px; border-radius: 10px; cursor: pointer; white-space: nowrap;
  }
  .car-arrow { width: 44px; height: 44px; display: block; filter: drop-shadow(0 0 6px rgba(13, 71, 161, 0.55)); }
  .car-dot {
    width: 20px; height: 20px; border-radius: 50%;
    background: #4DD0E1; border: 3px solid #FFFFFF;
    box-shadow: 0 0 14px rgba(77, 208, 225, 0.95);
  }
  .dest-dot {
    width: 18px; height: 18px; border-radius: 50%;
    background: #1ED760; border: 3px solid #FFFFFF; box-shadow: 0 0 8px rgba(0,0,0,0.4);
  }
</style>
</head>
<body>
<div id="routeChooser">
  <div id="routeTabs" class="route-tabs"></div>
  <div id="startNavBtn" class="start-pill" onclick="window.startNavigation()">▶ Démarrer</div>
</div>
<div id="map"></div>
<script>
  var currentLat = 45.75;
  var currentLon = 4.85;
  var hasFix = false;
  var followGps = true;
  var isNavigating = false;
  var firstFix = true;
  var pendingQuery = null;
  var pendingAutoNav = false;
  var startAfterRoute = false;
  var dest = null;
  var nav = null;
  var offRouteCount = 0;
  var lastRerouteAt = 0;
  var rerouting = false;
  var OSRM = 'https://router.project-osrm.org/route/v1/driving/';

  // MapLibre (OpenFreeMap « Liberty », gratuit, sans clé) : permet de faire tourner la carte avec la route.
  var map = new maplibregl.Map({
    container: 'map',
    style: 'https://tiles.openfreemap.org/styles/liberty',
    center: [currentLon, currentLat],
    zoom: 15,
    attributionControl: false,
    dragRotate: false,
    pitchWithRotate: false
  });
  map.touchZoomRotate.disableRotation();
  var styleLoaded = false;
  var onStyle = [];
  function whenStyle(fn) { if (styleLoaded) fn(); else onStyle.push(fn); }
  map.on('load', function() {
    styleLoaded = true;
    var firstSymbol = (map.getStyle().layers || []).find(function(l) { return l.type === 'symbol'; });
    var before = firstSymbol ? firstSymbol.id : undefined;
    map.addSource('routes-alt', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addSource('route-main', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({ id: 'routes-alt', type: 'line', source: 'routes-alt',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#5C768D', 'line-width': 6, 'line-opacity': 0.7, 'line-dasharray': [1.2, 1.2] } }, before);
    map.addLayer({ id: 'route-main-casing', type: 'line', source: 'route-main',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#0B6E7A', 'line-width': 11 } }, before);
    map.addLayer({ id: 'route-main', type: 'line', source: 'route-main',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#4DD0E1', 'line-width': 7 } }, before);
    map.on('click', 'routes-alt', function(e) {
      if (e.features && e.features.length) window.selectRoute(e.features[0].properties.idx);
    });
    onStyle.forEach(function(fn) { fn(); });
    onStyle = [];
  });
  // Style injoignable : repli sur les tuiles OpenStreetMap classiques.
  map.once('error', function() {
    if (styleLoaded) return;
    map.setStyle({ version: 8,
      sources: { osm: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256 } },
      layers: [{ id: 'osm', type: 'raster', source: 'osm' }] });
  });

  // Déplacement de la carte au doigt : on arrête de suivre la position.
  map.on('dragstart', function() { followGps = false; });

  var carEl = document.createElement('div');
  carEl.innerHTML = '<div class="car-dot"></div>';
  var marker = new maplibregl.Marker({ element: carEl, rotationAlignment: 'map', pitchAlignment: 'viewport' })
    .setLngLat([currentLon, currentLat]).addTo(map);
  var destMarker = null;
  var heading = -1;
  var lastHeadingLat = null, lastHeadingLon = null;

  function bearingBetween(lat1, lon1, lat2, lon2) {
    var r = Math.PI / 180;
    var y = Math.sin((lon2 - lon1) * r) * Math.cos(lat2 * r);
    var x = Math.cos(lat1 * r) * Math.sin(lat2 * r) - Math.sin(lat1 * r) * Math.cos(lat2 * r) * Math.cos((lon2 - lon1) * r);
    return (Math.atan2(y, x) / r + 360) % 360;
  }

  // En guidage, sans cap GPS (arrêt, faible vitesse) : direction du tracé à l'endroit où l'on se trouve.
  function routeBearing() {
    if (!nav || nav.pts.length < 2) return -1;
    var i = Math.min(nav.seg, nav.pts.length - 2);
    var j = i + 1;
    while (j < nav.pts.length - 1 && haversineMeters(nav.pts[i][0], nav.pts[i][1], nav.pts[j][0], nav.pts[j][1]) < 15) j++;
    return bearingBetween(nav.pts[i][0], nav.pts[i][1], nav.pts[j][0], nav.pts[j][1]);
  }

  function updateHeading(lat, lon, gpsBearing) {
    var h = -1;
    if (gpsBearing >= 0) {
      h = gpsBearing;
    } else if (lastHeadingLat !== null && haversineMeters(lastHeadingLat, lastHeadingLon, lat, lon) > 12) {
      h = bearingBetween(lastHeadingLat, lastHeadingLon, lat, lon);
    } else if (isNavigating) {
      h = routeBearing();
    }
    if (h < 0) {
      if (lastHeadingLat === null) { lastHeadingLat = lat; lastHeadingLon = lon; }
      return;
    }
    lastHeadingLat = lat;
    lastHeadingLon = lon;
    if (heading < 0) {
      carEl.innerHTML = '<svg class="car-arrow" viewBox="0 0 44 44"><circle cx="22" cy="22" r="20" fill="#FFFFFF"/>' +
        '<path d="M22 6 L34 34 L22 27 L10 34 Z" fill="#1A73E8" stroke="#0D47A1" stroke-width="1.5" stroke-linejoin="round"/></svg>';
    }
    // Chemin de rotation le plus court (pas de tour complet en passant de 350° à 10°).
    var prev = heading < 0 ? h : heading;
    var delta = ((h - (prev % 360)) + 540) % 360 - 180;
    heading = prev + delta;
  }

  // ---------- Animation : la flèche glisse d'une mesure GPS à la suivante, la caméra la suit ----------
  var ANIM_MS = 1000;
  var shown = null;            // position / cap affichés
  var animFrom = null, animStart = 0, animating = false;
  var cameraBusyUntil = 0;     // pendant un easeTo, on ne force pas la caméra

  function navPadding() {
    // En guidage, la flèche est placée dans le tiers bas de l'écran, comme Google Maps.
    return isNavigating ? { top: Math.round(map.getContainer().clientHeight * 0.4), bottom: 0, left: 0, right: 0 }
                        : { top: 0, bottom: 0, left: 0, right: 0 };
  }

  function followCamera(lat, lon, h, zoom, duration) {
    var opts = { center: [lon, lat], padding: navPadding(), pitch: isNavigating ? 45 : 0 };
    if (h >= 0) opts.bearing = h;
    if (zoom) opts.zoom = zoom;
    if (duration) {
      opts.duration = duration;
      cameraBusyUntil = Date.now() + duration + 50;
      map.easeTo(opts);
    } else {
      map.jumpTo(opts);
    }
  }

  function frame() {
    var t = Math.min(1, (Date.now() - animStart) / ANIM_MS);
    var lat = animFrom.lat + (currentLat - animFrom.lat) * t;
    var lon = animFrom.lon + (currentLon - animFrom.lon) * t;
    var h = heading < 0 ? -1 : (animFrom.h < 0 ? heading : animFrom.h + (heading - animFrom.h) * t);
    shown = { lat: lat, lon: lon, h: h };
    marker.setLngLat([lon, lat]);
    if (h >= 0) marker.setRotation(h);
    if (followGps && Date.now() > cameraBusyUntil) followCamera(lat, lon, h);
    if (t < 1) {
      requestAnimationFrame(frame);
    } else {
      animating = false;
    }
  }

  function animateTo() {
    animFrom = shown || { lat: currentLat, lon: currentLon, h: heading };
    animStart = Date.now();
    if (!animating) {
      animating = true;
      requestAnimationFrame(frame);
    }
  }

  function haversineMeters(lat1, lon1, lat2, lon2) {
    var R = 6371000;
    var toRad = Math.PI / 180;
    var dLat = (lat2 - lat1) * toRad;
    var dLon = (lon2 - lon1) * toRad;
    var a = Math.sin(dLat/2)*Math.sin(dLat/2) +
            Math.cos(lat1*toRad)*Math.cos(lat2*toRad)*Math.sin(dLon/2)*Math.sin(dLon/2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  }

  // Projection locale en mètres, suffisante pour mesurer l'écart à l'itinéraire.
  function toXY(lat, lon) {
    var k = Math.PI / 180 * 6371000;
    return [lon * k * Math.cos(currentLat * Math.PI / 180), lat * k];
  }

  function setChooserMessage(text) {
    var tabs = document.getElementById('routeTabs');
    tabs.innerHTML = '';
    var div = document.createElement('div');
    div.className = 'route-tab active';
    div.textContent = text;
    tabs.appendChild(div);
  }

  window.setChooserTop = function(px) {
    document.getElementById('routeChooser').style.top = px + 'px';
  };

  // Code de direction lu par Android pour choisir la flèche du bandeau.
  function directionCode(step) {
    if (!step || !step.maneuver) return 'straight';
    var type = step.maneuver.type || '';
    if (type === 'arrive') return 'arrive';
    if (type === 'roundabout' || type === 'rotary' || type === 'roundabout turn') return 'roundabout';
    return step.maneuver.modifier || 'straight';
  }

  function formatInstruction(step) {
    if (!step || !step.maneuver) return 'Continuez tout droit';
    var type = step.maneuver.type || '';
    var mod = step.maneuver.modifier || '';
    var side = mod.indexOf('left') !== -1 ? 'à gauche' : (mod.indexOf('right') !== -1 ? 'à droite' : '');
    if (type === 'arrive') return 'Arrivée à destination';
    if (type === 'roundabout' || type === 'rotary' || type === 'roundabout turn') {
      var exit = step.maneuver.exit;
      return exit ? ('Au rond-point, prenez la ' + exit + (exit === 1 ? 're' : 'e') + ' sortie') : 'Au rond-point';
    }
    if (type === 'on ramp' || type === 'off ramp') return 'Prenez la sortie' + (side ? ' ' + side : '');
    if (type === 'fork') return side ? ('Restez ' + side) : 'Continuez tout droit';
    if (mod === 'uturn') return 'Faites demi-tour';
    if (mod === 'slight left' || mod === 'slight right') return 'Serrez ' + side;
    if (mod === 'sharp left' || mod === 'sharp right') return 'Tournez franchement ' + side;
    if (side) return 'Tournez ' + side;
    return 'Continuez tout droit';
  }

  function formatDistance(m) {
    if (m >= 1000) return (m / 1000).toFixed(1).replace('.', ',') + ' km';
    return (m >= 100 ? Math.round(m / 10) * 10 : Math.round(m)) + ' m';
  }

  // Prépare le suivi : distances cumulées le long du tracé et point du tracé de chaque manœuvre.
  function prepareNav(route) {
    var pts = route.geometry.coordinates.map(function(c) { return [c[1], c[0]]; });
    var cum = [0];
    for (var i = 1; i < pts.length; i++) {
      cum.push(cum[i - 1] + haversineMeters(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]));
    }
    var steps = (route.legs && route.legs[0] && route.legs[0].steps) ? route.legs[0].steps : [];
    var stepIdx = [];
    var from = 0;
    steps.forEach(function(s) {
      var loc = s.maneuver && s.maneuver.location;
      var best = from;
      var bestD = Infinity;
      if (loc) {
        for (var j = from; j < pts.length; j++) {
          var d = haversineMeters(pts[j][0], pts[j][1], loc[1], loc[0]);
          if (d < bestD) { bestD = d; best = j; }
          if (bestD < 1) break;
        }
      }
      stepIdx.push(best);
      from = best;
    });
    return { route: route, pts: pts, cum: cum, steps: steps, stepIdx: stepIdx, seg: 0, along: 0, offDist: 0 };
  }

  // Projette la position sur le tracé pour savoir où l'on en est et à quelle distance on s'en écarte.
  function updateProgress() {
    if (!nav || nav.pts.length < 2) return;
    var p = toXY(currentLat, currentLon);
    var pts = nav.pts;
    var n = pts.length;
    function search(a, b) {
      var best = { d: Infinity, i: nav.seg, t: 0 };
      for (var i = Math.max(0, a); i < Math.min(n - 1, b); i++) {
        var A = toXY(pts[i][0], pts[i][1]);
        var B = toXY(pts[i + 1][0], pts[i + 1][1]);
        var dx = B[0] - A[0], dy = B[1] - A[1];
        var len2 = dx * dx + dy * dy;
        var t = len2 > 0 ? Math.max(0, Math.min(1, ((p[0] - A[0]) * dx + (p[1] - A[1]) * dy) / len2)) : 0;
        var ex = A[0] + t * dx - p[0], ey = A[1] + t * dy - p[1];
        var d = Math.sqrt(ex * ex + ey * ey);
        if (d < best.d) best = { d: d, i: i, t: t };
      }
      return best;
    }
    var best = search(nav.seg - 5, nav.seg + 300);
    if (best.d > 80) {
      var all = search(0, n - 1);
      if (all.d < best.d) best = all;
    }
    nav.seg = best.i;
    nav.offDist = best.d;
    nav.along = nav.cum[best.i] + best.t * (nav.cum[best.i + 1] - nav.cum[best.i]);
  }

  function notifyBridge() {
    if (!window.AndroidBridge) return;
    if (!isNavigating || !nav) {
      window.AndroidBridge.onNavigationUpdate(false, '', '', '', '', '');
      return;
    }
    var route = nav.route;
    var total = nav.cum[nav.cum.length - 1];
    var remaining = Math.max(0, total - nav.along);
    if (remaining < 30) {
      window.AndroidBridge.onNavigationUpdate(true, '', 'arrive', 'Vous êtes arrivé', '', 'Arrivée');
      return;
    }
    // Prochaine manœuvre : la première située après la position actuelle sur le tracé.
    var k = 1;
    while (k < nav.steps.length && nav.stepIdx[k] <= nav.seg) k++;
    var nextStep = nav.steps[Math.min(k, nav.steps.length - 1)];
    var stepDist = k < nav.steps.length
      ? Math.max(0, nav.cum[nav.stepIdx[k]] - nav.along)
      : remaining;

    var secs = route.distance > 0 ? route.duration * remaining / route.distance : route.duration;
    var mins = Math.max(1, Math.round(secs / 60));
    var arrival = new Date(Date.now() + secs * 1000);
    var hh = ('0' + arrival.getHours()).slice(-2);
    var mm = ('0' + arrival.getMinutes()).slice(-2);

    var etaText = mins + ' min · ' + (remaining / 1000).toFixed(1) + ' km · ' + hh + ':' + mm;
    window.AndroidBridge.onNavigationUpdate(
      true,
      formatDistance(stepDist),
      directionCode(nextStep),
      formatInstruction(nextStep),
      (nextStep && nextStep.name) || '',
      etaText
    );
  }

  // Hors itinéraire sur plusieurs mesures : on recalcule depuis la position actuelle.
  function checkOffRoute() {
    if (!nav || !dest || rerouting) return;
    offRouteCount = nav.offDist > 60 ? offRouteCount + 1 : 0;
    if (offRouteCount >= 3 && Date.now() - lastRerouteAt > 15000) {
      offRouteCount = 0;
      lastRerouteAt = Date.now();
      fetchRoutes(dest.lat, dest.lon, true);
    }
  }

  window.updateGps = function(lat, lon, gpsBearing) {
    currentLat = lat;
    currentLon = lon;
    hasFix = true;
    if (pendingQuery) {
      var q = pendingQuery;
      pendingQuery = null;
      window.routeToAddress(q, pendingAutoNav);
    }
    if (isNavigating) {
      updateProgress();
      checkOffRoute();
      notifyBridge();
    }
    updateHeading(lat, lon, gpsBearing === undefined ? -1 : gpsBearing);
    // Filet de sécurité : le tracé a déjà disparu de la carte (données perdues) → on le renvoie.
    if (allRoutes.length && Date.now() - lastRouteSync > 10000) setRouteData();
    if (firstFix) {
      firstFix = false;
      shown = { lat: lat, lon: lon, h: heading };
      marker.setLngLat([lon, lat]);
      if (followGps) followCamera(lat, lon, heading, Math.max(map.getZoom(), 15));
      return;
    }
    animateTo();
  };

  window.recenterGps = function() {
    followGps = true;
    var p = shown || { lat: currentLat, lon: currentLon, h: heading };
    followCamera(p.lat, p.lon, heading, isNavigating ? 17 : 16, 600);
  };

  var lastRouteSync = 0;
  function setRouteData() {
    lastRouteSync = Date.now();
    whenStyle(function() {
      var alts = [];
      allRoutes.forEach(function(r, i) {
        if (i === selectedIdx) return;
        alts.push({ type: 'Feature', properties: { idx: i }, geometry: r.geometry });
      });
      var chosen = allRoutes[selectedIdx];
      map.getSource('routes-alt').setData({ type: 'FeatureCollection', features: alts });
      map.getSource('route-main').setData({ type: 'FeatureCollection',
        features: chosen ? [{ type: 'Feature', properties: {}, geometry: chosen.geometry }] : [] });
    });
  }

  window.clearRoute = function() {
    isNavigating = false;
    nav = null;
    dest = null;
    pendingQuery = null;
    startAfterRoute = false;
    notifyBridge();
    allRoutes = [];
    setRouteData();
    if (destMarker) { destMarker.remove(); destMarker = null; }
    document.getElementById('routeChooser').style.display = 'none';
    window.recenterGps();
  };

  var allRoutes = [];
  var selectedIdx = 0;

  window.selectRoute = function(idx) {
    selectedIdx = idx;
    setRouteData();

    var chosen = allRoutes[selectedIdx];
    if (chosen && !isNavigating) {
      var coords = chosen.geometry.coordinates;
      var b = new maplibregl.LngLatBounds(coords[0], coords[0]);
      coords.forEach(function(c) { b.extend(c); });
      b.extend([currentLon, currentLat]);
      cameraBusyUntil = Date.now() + 900;
      map.fitBounds(b, { padding: 55, bearing: 0, pitch: 0, duration: 800 });
    }

    var tabs = document.getElementById('routeTabs');
    tabs.innerHTML = '';
    allRoutes.forEach(function(r, i) {
      var mins = Math.max(1, Math.round(r.duration / 60));
      var km = (r.distance / 1000).toFixed(1);
      var div = document.createElement('div');
      div.className = 'route-tab' + (i === selectedIdx ? ' active' : '');
      div.textContent = 'Trajet ' + (i + 1) + ' · ' + mins + ' min (' + km + ' km)';
      div.onclick = function() { window.selectRoute(i); };
      tabs.appendChild(div);
    });
  };

  window.startNavigation = function() {
    if (!allRoutes.length) return;
    isNavigating = true;
    followGps = true;
    offRouteCount = 0;
    nav = prepareNav(allRoutes[selectedIdx]);
    updateProgress();
    document.getElementById('routeChooser').style.display = 'none';
    notifyBridge();
    // Sans cap connu, la flèche et la carte s'orientent selon le début du tracé.
    if (heading < 0) updateHeading(currentLat, currentLon, routeBearing());
    if (heading >= 0) marker.setRotation(heading);
    shown = { lat: currentLat, lon: currentLon, h: heading };
    followCamera(currentLat, currentLon, heading, 17, 900);
  };

  window.stopNavigation = function() {
    isNavigating = false;
    nav = null;
    notifyBridge();
    document.getElementById('routeChooser').style.display = 'flex';
    window.selectRoute(selectedIdx);
  };

  function routesReady() {
    window.selectRoute(0);
    if (startAfterRoute) {
      startAfterRoute = false;
      window.startNavigation();
    }
  }

  // autoStart : recalcul pendant la navigation, on garde le guidage actif sur le nouveau trajet.
  function fetchRoutes(dLat, dLon, autoStart) {
    rerouting = true;
    var url = OSRM + currentLon + ',' + currentLat + ';' + dLon + ',' + dLat +
      '?overview=full&geometries=geojson&alternatives=3&steps=true';
    return fetch(url)
      .then(function(r) { return r.json(); })
      .then(function(data) {
        if (!data.routes || !data.routes.length) {
          if (!autoStart) setChooserMessage('Itinéraire indisponible');
          return;
        }
        allRoutes = data.routes;
        if (autoStart) {
          if (!isNavigating) return;
          window.selectRoute(0);
          nav = prepareNav(allRoutes[0]);
          updateProgress();
          notifyBridge();
          return;
        }
        if (allRoutes.length === 1) {
          var midLat = (currentLat + dLat) / 2 + (dLon - currentLon) * 0.18;
          var midLon = (currentLon + dLon) / 2 - (dLat - currentLat) * 0.18;
          var altUrl = OSRM + currentLon + ',' + currentLat + ';' + midLon + ',' + midLat + ';' + dLon + ',' + dLat +
            '?overview=full&geometries=geojson&steps=true';
          return fetch(altUrl).then(function(r) { return r.json(); }).then(function(altData) {
            if (altData && altData.routes && altData.routes.length) {
              var alt = altData.routes[0];
              if (Math.abs(alt.distance - allRoutes[0].distance) > 150) {
                allRoutes.push(alt);
              }
            }
            routesReady();
          }).catch(function() {
            routesReady();
          });
        }
        routesReady();
      })
      .catch(function() {
        if (!autoStart) setChooserMessage('Erreur réseau');
      })
      .then(function() { rerouting = false; });
  }

  window.routeToAddress = function(query, autoNav) {
    if (!query) return;
    startAfterRoute = !!autoNav;
    isNavigating = false;
    nav = null;
    notifyBridge();
    document.getElementById('routeChooser').style.display = 'flex';
    // Sans position réelle, l'itinéraire partirait du point par défaut : on attend le premier fix.
    if (!hasFix) {
      pendingQuery = query;
      pendingAutoNav = !!autoNav;
      setChooserMessage('En attente de la position GPS…');
      return;
    }
    setChooserMessage('Recherche « ' + query + ' »…');

    fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(query))
      .then(function(r) { return r.json(); })
      .then(function(results) {
        if (!results || !results.length) {
          setChooserMessage('Lieu introuvable');
          return;
        }
        var dLat = parseFloat(results[0].lat);
        var dLon = parseFloat(results[0].lon);
        dest = { lat: dLat, lon: dLon };
        if (destMarker) destMarker.remove();
        var el = document.createElement('div');
        el.className = 'dest-dot';
        destMarker = new maplibregl.Marker({ element: el }).setLngLat([dLon, dLat]).addTo(map);
        followGps = false;
        return fetchRoutes(dLat, dLon, false);
      })
      .catch(function() {
        setChooserMessage('Erreur réseau');
      });
  };
</script>
</body>
</html>
"""
