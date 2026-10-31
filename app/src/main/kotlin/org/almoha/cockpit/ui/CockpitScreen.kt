package org.almoha.cockpit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import kotlinx.coroutines.delay
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.AppEntry
import org.almoha.cockpit.data.ContactFav
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.data.NavMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CockpitScreen(
    state: UiState,
    vm: CarViewModel,
    mapController: MapController?,
    mapFullScreen: Boolean,
    onMapFullScreenChange: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onOpenApps: () -> Unit,
    onRequestPermissions: () -> Unit,
    onVoice: () -> Unit,
) {
    val density = LocalDensity.current
    // Hauteur réelle du lecteur + dock superposés, pour que les boutons de la carte ne passent pas dessous.
    var bottomOverlayHeight by remember { mutableStateOf(146.dp) }
    var topBarHeight by remember { mutableStateOf(64.dp) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (mapFullScreen && state.prefs.navMode == NavMode.EMBEDDED_MAP && mapController != null) {
            // Le dock se masque après quelques secondes sans toucher l'écran et revient au moindre contact.
            var touches by remember { mutableIntStateOf(0) }
            var dockVisible by remember { mutableStateOf(true) }
            // Un dialogue (sélecteur de source) est une autre fenêtre : ses touches ne passent pas ici.
            var held by remember { mutableStateOf(false) }
            // Pendant la saisie d'une adresse, l'interface reste affichée.
            val searching = mapController.searching
            LaunchedEffect(touches, held, searching) {
                dockVisible = true
                if (held || searching) return@LaunchedEffect
                delay(DOCK_HIDE_DELAY_MS)
                dockVisible = false
            }
            // Fullscreen map background with TopBar, Tidal mini-controls, and bottom Dock superimposed on top!
            CompositionLocalProvider(LocalKeepAwake provides { held = it }) {
            // Boutons « recentrer » et « vue 3 blocs » : toujours visibles, ils ne doivent pas servir de réveil.
            val persistentButtons = remember { mutableStateMapOf<String, Rect>() }
            var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type != PointerEventType.Press) continue
                                val pos = (event.changes.firstOrNull()?.position ?: Offset.Zero) + overlayOrigin
                                if (!dockVisible && persistentButtons.values.any { it.contains(pos) }) continue
                                val waking = !dockVisible
                                touches++
                                if (!waking) continue
                                // Écran « au repos » : le premier toucher ne fait que réveiller l'interface
                                // (gros boutons + dock). On l'avale en entier pour éviter une action involontaire.
                                event.changes.forEach { it.consume() }
                                do {
                                    val next = awaitPointerEvent(PointerEventPass.Initial)
                                    next.changes.forEach { it.consume() }
                                } while (next.changes.any { it.pressed })
                            }
                        }
                    },
            ) {
                IntegratedMapContent(
                    controller = mapController,
                    hasLocationPermission = state.permissions.location,
                    fullScreen = true,
                    onToggleFullScreen = { onMapFullScreenChange(false) },
                    onRequestPermissions = onRequestPermissions,
                    onOpenGoogleMaps = { q -> vm.navigate(KnownApps.MAPS, q) },
                    onOpenCharging = vm::openCharging,
                    modifier = Modifier.fillMaxSize(),
                    home = state.prefs.home,
                    work = state.prefs.work,
                    chargingApp = state.chargingApp(),
                    mapsApp = state.apps.firstOrNull { it.packageName == KnownApps.MAPS },
                    favorites = state.prefs.navFavorites,
                    onAddFavorite = { vm.addNavFavorite(it, it) },
                    onRemoveFavorite = vm::removeNavFavorite,
                    // Les icônes Maps / recharge se masquent avec le dock.
                    showAppButtons = dockVisible,
                    fullScreenTopInset = if (dockVisible) topBarHeight else 8.dp,
                    onPersistentButtonBounds = { key, rect -> persistentButtons[key] = rect },
                    fullScreenBottomInset = bottomOverlayHeight,
                )
                // Au repos, seule la carte reste : barre du haut, lecteur et dock reviennent au toucher.
                AnimatedVisibility(
                    visible = dockVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Fond opaque : la carte claire rendrait l'heure et les icônes illisibles.
                            .background(Color(0xF205070A))
                            .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
                    ) {
                        TopBar(state, vm, onSettings, onExit, onRequestPermissions, onVoice)
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomOverlayHeight = with(density) { it.height.toDp() } },
                ) {
                    // Interface réveillée : gros boutons faciles à viser en conduisant.
                    // Pendant la saisie d'une adresse, le clavier remonte tout : lecteur et dock s'effacent
                    // pour laisser la barre de recherche et ses raccourcis lisibles.
                    AnimatedVisibility(
                        visible = dockVisible && !searching,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                    ) {
                        Column {
                            FullScreenOverlayPlayerBar(state = state, vm = vm, large = true, onVoice = onVoice)
                            Dock(state, vm, onOpenApps, translucent = true, onVoice = onVoice)
                        }
                    }
                    if (!dockVisible) Spacer(Modifier.height(8.dp))
                }
            }
            }
        } else {
            // Saisie d'une adresse : le clavier prend la moitié de l'écran, on ne garde que la carte et son champ.
            val typing = mapController?.searching == true
            Column(Modifier.fillMaxSize()) {
                if (!(typing && landscape)) TopBar(state, vm, onSettings, onExit, onRequestPermissions, onVoice)
                if (landscape) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        NavigationCard(
                            state, vm,
                            Modifier.weight(1.15f).fillMaxHeight(),
                            onRequestPermissions,
                            mapController = mapController,
                            onToggleFullScreen = { onMapFullScreenChange(true) },
                        )
                        if (!typing) MediaCard(
                            state, vm,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                        if (!typing) Column(
                            Modifier.weight(0.84f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PhoneCard(
                                state, vm,
                                Modifier.weight(1f).fillMaxWidth(),
                                onRequestPermissions,
                            )
                            AssistantCard(state, vm, Modifier.height(78.dp).fillMaxWidth())
                        }
                    }
                } else {
                    // Trois zones fixes, sans défilement : tout reste visible au même endroit.
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationCard(
                            state, vm,
                            Modifier.fillMaxWidth().weight(1f),
                            onRequestPermissions,
                            mapController = mapController,
                            onToggleFullScreen = { onMapFullScreenChange(true) },
                        )
                        // Saisie d'une adresse : le clavier prend la place, la carte et son champ restent visibles.
                        if (!typing) {
                            MediaCard(state, vm, Modifier.fillMaxWidth().height(if (state.media.durationMs > 0L) 312.dp else 284.dp), compact = true)
                            PhoneCard(
                                state, vm,
                                Modifier.fillMaxWidth().height(84.dp),
                                onRequestPermissions,
                                compact = true,
                            )
                        }
                    }
                }
                if (!typing) Dock(state, vm, onOpenApps, translucent = false, onVoice = onVoice)
            }
        }
    }
}

@Composable
private fun FullScreenOverlayPlayerBar(
    state: UiState,
    vm: CarViewModel,
    large: Boolean = false,
    onVoice: () -> Unit = {},
) {
    val media = state.media
    val title = if (media.hasSession) {
        media.title.ifBlank { media.appLabel.ifBlank { "Musique" } }
    } else {
        "Musique"
    }
    val artist = if (media.hasSession) {
        media.artist.ifBlank { media.appLabel }
    } else {
        "Touchez pour ouvrir"
    }
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .clip(shape)
            .background(Color(0xD9080D14))
            .border(1.dp, CockpitColors.Stroke, shape)
            .animateContentSize()
            .padding(horizontal = 12.dp),
    ) {
        if (large && maxWidth < 560.dp) {
            // Interface réveillée en portrait : très gros boutons sur trois lignes.
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MediaSourceButton(state, vm, 60.dp)
                    PlayerTitle(title, artist, vm, Modifier.weight(1f), large = true)
                    FavoriteButton(media, vm, 60.dp)
                    VoiceButton(onVoice, 64.dp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PlayerControls(media, vm, small = 62.dp, main = 88.dp, withFavorite = false)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerContacts(state, vm, large = true, modifier = Modifier.weight(1f))
                }
            }
        } else if (large) {
            // Interface réveillée en paysage : une seule ligne, boutons agrandis.
            Row(
                Modifier.fillMaxWidth().height(100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MediaSourceButton(state, vm, 72.dp)
                PlayerTitle(title, artist, vm, Modifier.weight(1f), large = true)
                PlayerControls(media, vm, small = 60.dp, main = 84.dp)
                PlayerContacts(state, vm, large = true)
                VoiceButton(onVoice, 72.dp)
            }
        } else if (maxWidth < 560.dp) {
            // Écran étroit (portrait) : titre et favoris en haut, commandes en dessous.
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MediaSourceButton(state, vm, 36.dp)
                    PlayerTitle(title, artist, vm, Modifier.weight(1f))
                    PlayerContacts(state, vm)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PlayerControls(media, vm, small = 40.dp, main = 46.dp)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MediaSourceButton(state, vm, 36.dp)
                PlayerTitle(title, artist, vm, Modifier.weight(1f))
                PlayerControls(media, vm, small = 36.dp, main = 40.dp)
                PlayerContacts(state, vm)
            }
        }
    }
}

@Composable
private fun PlayerTitle(
    title: String,
    artist: String,
    vm: CarViewModel,
    modifier: Modifier,
    large: Boolean = false,
) {
    Column(modifier.clickable(onClick = vm::openMediaApp)) {
        Text(
            title,
            color = CockpitColors.Text,
            fontSize = if (large) 19.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (artist.isNotBlank()) {
            Text(
                artist,
                color = CockpitColors.TextDim,
                fontSize = if (large) 14.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    media: org.almoha.cockpit.media.MediaUiState,
    vm: CarViewModel,
    small: Dp,
    main: Dp,
    withFavorite: Boolean = true,
) {
    if (withFavorite) FavoriteButton(media, vm, small)
    TransportButton(
        Icons.Filled.Replay10,
        "Reculer 10s",
        vm::rewind10s,
        size = small,
        accent = CockpitColors.Media,
        enabled = media.hasSession,
    )
    TransportButton(
        Icons.Filled.SkipPrevious,
        "Précédent",
        vm::previous,
        size = small,
        accent = CockpitColors.Media,
        enabled = media.hasSession && media.canSkipPrevious,
    )
    TransportButton(
        if (media.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        if (media.isPlaying) "Pause" else "Lecture",
        { if (media.hasSession) vm.playPause() else vm.openMediaApp() },
        size = main,
        filled = true,
    )
    TransportButton(
        Icons.Filled.SkipNext,
        "Suivant",
        vm::next,
        size = small,
        accent = CockpitColors.Media,
        enabled = media.hasSession && media.canSkipNext,
    )
    TransportButton(
        Icons.Filled.Forward10,
        "Avancer 10s",
        vm::forward10s,
        size = small,
        accent = CockpitColors.Media,
        enabled = media.hasSession,
    )
}

@Composable
private fun FavoriteButton(media: org.almoha.cockpit.media.MediaUiState, vm: CarViewModel, size: Dp) {
    TransportButton(
        if (media.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        "Favori",
        vm::toggleFavorite,
        size = size,
        accent = if (media.isFavorite) Color(0xFFFF5C8A) else CockpitColors.TextDim,
        enabled = media.hasSession,
    )
}

@Composable
private fun VoiceButton(onVoice: () -> Unit, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(CockpitColors.Assistant.copy(alpha = 0.28f))
            .border(1.5.dp, CockpitColors.Assistant.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onVoice),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Commande vocale",
            tint = CockpitColors.Assistant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun PlayerContacts(
    state: UiState,
    vm: CarViewModel,
    large: Boolean = false,
    modifier: Modifier = Modifier,
) {
    state.contacts.take(2).forEach { contact ->
        val shortName = if (contact.name.startsWith("Parents", ignoreCase = true)) "Parents" else contact.name
        Row(
            modifier
                .height(if (large) 64.dp else 36.dp)
                .clip(RoundedCornerShape(if (large) 16.dp else 12.dp))
                .background(CockpitColors.Phone.copy(alpha = 0.18f))
                .border(1.dp, CockpitColors.Phone.copy(alpha = 0.40f), RoundedCornerShape(if (large) 16.dp else 12.dp))
                .clickable { vm.call(contact.number) }
                .padding(horizontal = if (large) 14.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = contact.name,
                tint = CockpitColors.Phone,
                modifier = Modifier.size(if (large) 26.dp else 14.dp),
            )
            Spacer(Modifier.width(if (large) 8.dp else 4.dp))
            Text(
                shortName,
                color = CockpitColors.Text,
                fontSize = if (large) 19.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: UiState,
    vm: CarViewModel,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onRequestPermissions: () -> Unit,
    onVoice: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(62.dp),
    ) {
        val compact = maxWidth < 620.dp
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 14.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                now.format(timeFormat),
                color = CockpitColors.Text,
                fontSize = if (compact) 25.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            if (compact) {
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        now.format(dateFormat),
                        color = CockpitColors.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        "MODE CONDUITE",
                        color = CockpitColors.TextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(18.dp))
            }
            WeatherChip(state, vm, compact, onRequestPermissions)
            Spacer(Modifier.weight(1f))
            BatteryChip(state, compact)
            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            StatusIconButton(
                Icons.Filled.DarkMode,
                "Tamiser l'écran",
                vm::toggleDim,
                active = state.dim,
                size = if (compact) 40.dp else 46.dp,
                iconSize = if (compact) 19.dp else 21.dp,
            )
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            StatusIconButton(
                Icons.Filled.Settings,
                "Réglages",
                onSettings,
                size = if (compact) 40.dp else 46.dp,
                iconSize = if (compact) 19.dp else 21.dp,
            )
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            StatusIconButton(
                Icons.Filled.PowerSettingsNew,
                "Quitter",
                onExit,
                size = if (compact) 40.dp else 46.dp,
                iconSize = if (compact) 19.dp else 21.dp,
            )
        }
    }
}

@Composable
private fun WeatherChip(
    state: UiState,
    vm: CarViewModel,
    compact: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val weather = state.weather
    val look = weather?.let { weatherLook(it.code) }
    Row(
        Modifier
            .height(if (compact) 34.dp else 38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable {
                if (state.permissions.location) vm.refreshWeather(true) else onRequestPermissions()
            }
            .padding(horizontal = if (compact) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (weather == null || look == null) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = CockpitColors.TextDim,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            if (!compact) {
                Text(
                    if (state.permissions.location) "Météo" else "Activer la météo",
                    color = CockpitColors.TextDim,
                    fontSize = 12.sp,
                )
            }
        } else {
            Icon(
                look.icon,
                contentDescription = null,
                tint = look.color,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${weather.temp}°",
                color = CockpitColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(look.label, color = CockpitColors.TextDim, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BatteryChip(state: UiState, compact: Boolean) {
    val tint = when {
        state.charging -> CockpitColors.Charging
        state.batteryLevel <= 15 -> CockpitColors.Warning
        else -> CockpitColors.TextDim
    }
    Row(
        Modifier
            .height(if (compact) 34.dp else 38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = if (compact) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            batteryIcon(state.batteryLevel, state.charging),
            contentDescription = "Batterie ${state.batteryLevel}%",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (!compact) {
            Spacer(Modifier.width(6.dp))
            Text(
                "${state.batteryLevel}%",
                color = CockpitColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun NavigationCard(
    state: UiState,
    vm: CarViewModel,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    mapController: MapController?,
    onToggleFullScreen: () -> Unit = {},
) {
    val mapsApp = state.apps.firstOrNull { it.packageName == KnownApps.MAPS }
    val mode = state.prefs.navMode
    val modeLabel = when (mode) {
        NavMode.EMBEDDED_MAP -> "Carte intégrée"
        NavMode.FLOATING_SPLIT -> "Maps + Bandeau"
        NavMode.LIVE_DIRECTIONS -> "Instructions Maps"
    }

    Panel(
        CockpitColors.Nav,
        modifier,
        contentPadding = PaddingValues(if (mode == NavMode.EMBEDDED_MAP) 10.dp else 18.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 500.dp
            Column(Modifier.fillMaxSize()) {
                SectionLabel(
                    Icons.Filled.NearMe,
                    "Navigation",
                    CockpitColors.Nav,
                    trailing = {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CockpitColors.Nav.copy(alpha = 0.14f))
                                .border(1.dp, CockpitColors.Nav.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                                .clickable(onClick = vm::cycleNavMode)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Layers,
                                contentDescription = "Changer de mode",
                                tint = CockpitColors.Nav,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                modeLabel,
                                color = CockpitColors.Nav,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))

                when (mode) {
                    NavMode.EMBEDDED_MAP -> {
                        if (state.navDirection.active) {
                            LiveNavCompactBanner(
                                nav = state.navDirection,
                                onClick = { vm.navigate(KnownApps.MAPS, null) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (mapController != null) {
                            IntegratedMapContent(
                                controller = mapController,
                                hasLocationPermission = state.permissions.location,
                                fullScreen = false,
                                onToggleFullScreen = onToggleFullScreen,
                                onRequestPermissions = onRequestPermissions,
                                onOpenGoogleMaps = { q -> vm.navigate(KnownApps.MAPS, q) },
                                onOpenCharging = vm::openCharging,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                home = state.prefs.home,
                                work = state.prefs.work,
                                chargingApp = state.chargingApp(),
                                mapsApp = mapsApp,
                                favorites = state.prefs.navFavorites,
                                onAddFavorite = { vm.addNavFavorite(it, it) },
                                onRemoveFavorite = vm::removeNavFavorite,
                                // La recherche passe par le gros bouton « Chercher » sous la carte.
                                showSearchPill = false,
                            )
                            Spacer(Modifier.height(8.dp))
                            MapActions(state, vm, mapController)
                        }
                    }

                    NavMode.LIVE_DIRECTIONS -> {
                        if (state.navDirection.active) {
                            LiveDirectionsFullTile(
                                nav = state.navDirection,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onClick = { vm.navigate(KnownApps.MAPS, null) },
                            )
                        } else {
                            Row(
                                Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (mapsApp == null) {
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Google Maps non détecté",
                                            color = CockpitColors.TextDim,
                                            fontSize = 14.sp,
                                        )
                                    }
                                } else {
                                    NavLaunchTile(
                                        mapsApp,
                                        compact,
                                        Modifier.weight(1f).fillMaxHeight(),
                                        subtitle = "Lancez un trajet Maps : les flèches et instructions s'afficheront ici",
                                    ) {
                                        vm.navigate(mapsApp.packageName, null)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickAction(
                                "Electroverse",
                                Icons.Filled.EvStation,
                                CockpitColors.Ev,
                                vm::openCharging,
                                Modifier.weight(1f),
                            )
                            QuickAction(
                                "Où aller ?",
                                Icons.Filled.Mic,
                                CockpitColors.Assistant,
                                vm::openAssistant,
                                Modifier.weight(1f),
                            )
                        }
                        DestinationShortcuts(state, vm)
                    }

                    NavMode.FLOATING_SPLIT -> {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (mapsApp == null) {
                                Box(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Google Maps non détecté",
                                        color = CockpitColors.TextDim,
                                        fontSize = 14.sp,
                                    )
                                }
                            } else {
                                NavLaunchTile(
                                    mapsApp,
                                    compact,
                                    Modifier.weight(1f).fillMaxHeight(),
                                    subtitle = if (state.permissions.overlay) {
                                        "Ouvre Google Maps avec la bulle média flottante"
                                    } else {
                                        "Touchez ci-dessous pour autoriser le bandeau flottant par-dessus Maps"
                                    },
                                ) {
                                    if (!state.permissions.overlay) {
                                        vm.openOverlaySettings()
                                    } else {
                                        vm.navigate(mapsApp.packageName, null)
                                    }
                                }
                            }
                        }
                        if (!state.permissions.overlay) {
                            Spacer(Modifier.height(6.dp))
                            QuickAction(
                                "Autoriser « Afficher par-dessus » (Bandeau Maps)",
                                Icons.Filled.Layers,
                                CockpitColors.Nav,
                                vm::openOverlaySettings,
                                Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickAction(
                                "Electroverse",
                                Icons.Filled.EvStation,
                                CockpitColors.Ev,
                                vm::openCharging,
                                Modifier.weight(1f),
                            )
                            QuickAction(
                                "Où aller ?",
                                Icons.Filled.Mic,
                                CockpitColors.Assistant,
                                vm::openAssistant,
                                Modifier.weight(1f),
                            )
                        }
                        DestinationShortcuts(state, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveNavCompactBanner(
    nav: org.almoha.cockpit.media.NavDirectionState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CockpitColors.Nav.copy(alpha = 0.16f))
            .border(1.dp, CockpitColors.Nav.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nav.icon != null) {
            Image(
                bitmap = nav.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                nav.title.ifBlank { nav.instruction },
                color = CockpitColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nav.instruction.isNotBlank() && nav.title.isNotBlank()) {
                Text(
                    nav.instruction,
                    color = CockpitColors.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (nav.eta.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                nav.eta,
                color = CockpitColors.Nav,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveDirectionsFullTile(
    nav: org.almoha.cockpit.media.NavDirectionState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, CockpitColors.Nav.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (nav.icon != null) {
                Image(
                    bitmap = nav.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nav.title.ifBlank { "En route" },
                    color = CockpitColors.Nav,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nav.instruction.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        nav.instruction,
                        color = CockpitColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (nav.eta.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                nav.eta,
                color = CockpitColors.TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavLaunchTile(
    app: AppEntry,
    compact: Boolean,
    modifier: Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(onClick = onClick)
            .padding(if (compact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppImage(app, if (compact) 64.dp else 72.dp)
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            app.label,
            color = CockpitColors.Text,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = CockpitColors.TextDim,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun MediaCard(
    state: UiState,
    vm: CarViewModel,
    modifier: Modifier,
    compact: Boolean = false,
) {
    val media = state.media
    val focusManager = LocalFocusManager.current
    val tidalApp = state.apps.firstOrNull { it.packageName == KnownApps.TIDAL }
    var showTidalDrawer by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    var livePositionMs by remember(media.positionMs, media.positionUpdatedElapsedMs, media.isPlaying) {
        mutableLongStateOf(media.positionMs)
    }
    LaunchedEffect(media.positionMs, media.positionUpdatedElapsedMs, media.isPlaying, media.durationMs) {
        if (!media.isPlaying || media.durationMs <= 0L) {
            livePositionMs = media.positionMs
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - media.positionUpdatedElapsedMs).coerceAtLeast(0L)
            livePositionMs = (media.positionMs + elapsed).coerceAtMost(media.durationMs)
            delay(1_000L)
        }
    }

    // Radio en direct dont la pochette porte déjà le nom (logo CNews Radio) : on n'écrit que « En direct ».
    val liveWithLogo = media.hasSession && media.artwork != null && media.artist.equals("En direct", ignoreCase = true)
    val title = if (liveWithLogo) {
        "En direct"
    } else if (media.hasSession) {
        media.title.ifBlank { media.appLabel.ifBlank { "Lecture" } }
    } else {
        "Musique"
    }
    val subtitle = if (liveWithLogo) {
        ""
    } else if (media.hasSession) {
        media.artist.ifBlank {
            if (media.title.isNotBlank() && media.appLabel != media.title) media.appLabel else ""
        }
    } else {
        "Touchez lecture ou recherchez dans Tidal"
    }
    Panel(CockpitColors.Media, modifier) {
        SectionLabel(
            Icons.Filled.MusicNote,
            // Nom de l'application qui joue réellement (FluxMedias, Tidal, Audible…).
            if (media.hasSession && media.appLabel.isNotBlank()) "Musique · ${media.appLabel}" else "Musique",
            CockpitColors.Media,
        )
        Spacer(Modifier.height(6.dp))

        if (showTidalDrawer) TidalBrowserDialog(vm) { showTidalDrawer = false }
        val openSourcePicker = rememberSourcePicker(state, vm)
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                media,
                Modifier
                    .size(if (compact) 76.dp else 100.dp)
                    .clickable(onClick = vm::openMediaApp),
                fallbackIcon = tidalApp?.icon,
            )
            Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = CockpitColors.Text,
                    fontSize = if (compact) 18.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        color = CockpitColors.TextDim,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (media.hasSession && media.album.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        media.album,
                        color = CockpitColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (media.hasSession && media.durationMs > 0L) {
            val progress = dragFraction
                ?: (livePositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            Row(
                Modifier.fillMaxWidth().height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMs(dragFraction?.let { (it * media.durationMs).toLong() } ?: livePositionMs),
                    color = CockpitColors.TextMuted,
                    fontSize = 10.sp,
                )
                Slider(
                    value = progress,
                    // On ne déplace que le curseur pendant le glissement, et on envoie un seul seek au relâchement.
                    onValueChange = { fraction -> dragFraction = fraction },
                    onValueChangeFinished = {
                        dragFraction?.let { fraction ->
                            val target = (fraction * media.durationMs).toLong()
                            livePositionMs = target
                            vm.seekTo(target)
                        }
                        dragFraction = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = CockpitColors.Media,
                        activeTrackColor = CockpitColors.Media,
                        inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                    ),
                )
                Text(formatMs(media.durationMs), color = CockpitColors.TextMuted, fontSize = 10.sp)
            }
        }

        if (!media.hasAccess) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = vm::openNotificationSettings),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = CockpitColors.Media.copy(alpha = 0.9f),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Activer l'accès aux notifications pour piloter la musique",
                    color = CockpitColors.Media.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(Modifier.height(2.dp))
        }
        // Gros boutons centrés : ±10 s seulement pour les livres audio / podcasts, sinon le cœur favori.
        val spoken = KnownApps.isSpokenWord(media.packageName, media.durationMs)
        // Tailles réduites si la colonne est étroite (paysage) pour que les boutons ne se chevauchent pas.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = (maxWidth / 330.dp).coerceIn(0.62f, 1f)
        val side = (if (compact) 52.dp else 56.dp) * scale
        val skip = (if (compact) 60.dp else 64.dp) * scale
        val play = (if (compact) 74.dp else 78.dp) * scale
        Row(
            Modifier.fillMaxWidth().height(play + 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (spoken) {
                TransportButton(Icons.Filled.Replay10, "Reculer de 10 secondes", vm::rewind10s,
                    size = side, accent = CockpitColors.Media, enabled = media.hasSession)
            } else {
                TransportButton(
                    if (media.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (media.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                    vm::toggleFavorite,
                    size = side,
                    accent = if (media.isFavorite) Color(0xFFFF5C8A) else CockpitColors.TextDim,
                    enabled = media.hasSession && media.canFavorite,
                )
            }
            TransportButton(Icons.Filled.SkipPrevious, "Précédent", vm::previous,
                size = skip, accent = CockpitColors.Media, enabled = media.hasSession && media.canSkipPrevious)
            TransportButton(
                if (media.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (media.isPlaying) "Pause" else "Lecture",
                { if (media.hasSession) vm.playPause() else openSourcePicker() },
                size = play,
                filled = true,
            )
            TransportButton(Icons.Filled.SkipNext, "Suivant", vm::next,
                size = skip, accent = CockpitColors.Media, enabled = media.hasSession && media.canSkipNext)
            if (spoken) {
                TransportButton(Icons.Filled.Forward10, "Avancer de 10 secondes", vm::forward10s,
                    size = side, accent = CockpitColors.Media, enabled = media.hasSession)
            }
        }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(Icons.Filled.SwapHoriz, "Source", CockpitColors.Media, openSourcePicker, Modifier.weight(1f), height = 52.dp)
            if (tidalApp != null) {
                BigAction(Icons.AutoMirrored.Filled.QueueMusic, "Tidal", CockpitColors.Media, { showTidalDrawer = true }, Modifier.weight(1f), height = 52.dp)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

@Composable
private fun PhoneCard(
    state: UiState,
    vm: CarViewModel,
    modifier: Modifier,
    onRequestPermissions: () -> Unit,
    compact: Boolean = false,
) {
    Panel(
        CockpitColors.Phone,
        modifier,
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
    ) {
        if (!compact) SectionLabel(
            Icons.Filled.Phone,
            "Favoris",
            CockpitColors.Phone,
            trailing = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = vm::openContacts)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = CockpitColors.Phone,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Contacts",
                        color = CockpitColors.Phone,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            },
        )
        if (!compact) Spacer(Modifier.height(8.dp))
        when {
            !state.permissions.contacts -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Autorisez les contacts pour appeler en un geste",
                    color = CockpitColors.TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                QuickAction(
                    "Autoriser",
                    Icons.Filled.Contacts,
                    CockpitColors.Phone,
                    onRequestPermissions,
                    Modifier.fillMaxWidth(),
                )
            }

            state.contacts.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Patrick et Parents portable introuvables dans les contacts",
                    color = CockpitColors.TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                QuickAction(
                    "Téléphone",
                    Icons.Filled.Phone,
                    CockpitColors.Phone,
                    vm::openDialer,
                    Modifier.fillMaxWidth(),
                )
            }

            compact -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Gros boutons « 📞 Nom » sur une ligne : appel en un geste.
                state.contacts.forEach { contact ->
                    BigAction(
                        Icons.Filled.Phone,
                        contact.name.substringBefore(' '),
                        CockpitColors.Phone,
                        { vm.call(contact.number) },
                        Modifier.weight(1f),
                        height = 60.dp,
                    )
                }
                BigAction(Icons.Filled.Contacts, "Contacts", CockpitColors.Phone, vm::openContacts, Modifier.weight(1.1f), height = 60.dp)
            }

            else -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.contacts.forEach { contact ->
                    ContactButton(
                        contact,
                        Modifier.weight(1f).fillMaxHeight(),
                    ) { vm.call(contact.number) }
                }
            }
        }
    }
}

@Composable
private fun ContactButton(
    contact: ContactFav,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContactAvatar(contact, 58.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            contact.name,
            color = CockpitColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun AssistantCard(state: UiState, vm: CarViewModel, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        CockpitColors.Assistant.copy(alpha = 0.30f),
                        CockpitColors.Assistant.copy(alpha = 0.06f),
                        Color(0xFF0A0E14),
                    ),
                ),
            )
            .border(1.dp, CockpitColors.Stroke, shape)
            .clickable(onClick = vm::openAssistant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(CockpitColors.Assistant.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = CockpitColors.Assistant,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Assistant",
                color = CockpitColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Parlez maintenant",
                color = CockpitColors.TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Dock(
    state: UiState,
    vm: CarViewModel,
    onOpenApps: () -> Unit,
    translucent: Boolean = false,
    onVoice: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 620.dp
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(if (compact) 80.dp else 88.dp)
                .clip(shape)
                .background(if (translucent) Color(0xD9080D14) else Color(0xFF0A0F15))
                .border(1.dp, CockpitColors.Stroke, shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.dock.forEach { app ->
                DockButton(app, compact, Modifier.weight(1f)) { vm.openFromDock(app.packageName) }
            }
            DockAppsButton(compact, Modifier.weight(0.9f), onOpenApps)
            // Micro toujours au même endroit : en bas à droite.
            DockVoiceButton(Modifier.weight(1.1f), onVoice)
        }
    }
}

@Composable
private fun DockButton(
    app: AppEntry,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppImage(app, if (compact) 42.dp else 52.dp)
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            app.label,
            color = CockpitColors.TextDim,
            fontSize = if (compact) 10.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DockAppsButton(
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(if (compact) 42.dp else 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Apps,
                contentDescription = "Toutes les applications",
                tint = CockpitColors.TextDim,
                modifier = Modifier.size(if (compact) 28.dp else 30.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            "Apps",
            color = CockpitColors.TextDim,
            fontSize = if (compact) 10.sp else 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun DockVoiceButton(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitColors.Assistant.copy(alpha = 0.30f))
            .border(1.5.dp, CockpitColors.Assistant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Commande vocale", tint = CockpitColors.Assistant, modifier = Modifier.size(30.dp))
        Text("Parler", color = CockpitColors.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppShortcutButton(
    app: AppEntry,
    onClick: () -> Unit,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppImage(app, size * 0.68f)
    }
}

@Composable
private fun DestinationShortcuts(state: UiState, vm: CarViewModel) {
    val home = state.prefs.home
    val work = state.prefs.work
    if (home.isBlank() && work.isBlank()) return
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (home.isNotBlank()) {
            QuickAction(
                "Maison",
                Icons.Filled.Home,
                CockpitColors.Nav,
                { vm.navigate(KnownApps.MAPS, home) },
                Modifier.weight(1f),
            )
        }
        if (work.isNotBlank()) {
            QuickAction(
                "Travail",
                Icons.Filled.Work,
                CockpitColors.Nav,
                { vm.navigate(KnownApps.MAPS, work) },
                Modifier.weight(1f),
            )
        }
    }
}

private const val DOCK_HIDE_DELAY_MS = 6_000L

private fun UiState.chargingApp(): AppEntry? {
    val byPackage = apps.associateBy { it.packageName }
    return listOf(KnownApps.ELECTROVERSE, KnownApps.CHARGE_MAP, KnownApps.ELECTROMAPS, KnownApps.FRESMILE)
        .firstNotNullOfOrNull { byPackage[it] }
}
