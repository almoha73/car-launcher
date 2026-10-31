package org.almoha.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.media.Speech

/**
 * Gros bouton étiqueté (icône + texte), toujours à la même place dans sa zone :
 * on le trouve d'un coup d'œil, sans chercher.
 */
@Composable
fun BigAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    strong: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(accent.copy(alpha = if (strong) 0.32f else 0.14f))
            .border(1.5.dp, accent.copy(alpha = if (strong) 0.9f else 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = CockpitColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Zone Navigation : Maison / Travail / Favoris / Chercher, ou Arrêter / Recentrer / Maps pendant le guidage. */
@Composable
fun MapActions(state: UiState, vm: CarViewModel, controller: MapController) {
    var showFavorites by remember { mutableStateOf(false) }
    val home = state.prefs.home
    val work = state.prefs.work
    val favorites = state.prefs.navFavorites

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (controller.navActive) {
            BigAction(Icons.Filled.Close, "Arrêter", CockpitColors.Warning, controller::stopNavigation, Modifier.weight(1f))
            BigAction(Icons.Filled.MyLocation, "Recentrer", CockpitColors.Nav, controller::recenter, Modifier.weight(1f))
            BigAction(
                Icons.AutoMirrored.Filled.OpenInNew,
                "Maps",
                CockpitColors.Nav,
                { vm.navigate(KnownApps.MAPS, null) },
                Modifier.weight(1f),
            )
        } else {
            if (home.isNotBlank()) {
                NavShortcut(Icons.Filled.Home, "Maison") { controller.routeTo(home, startNavigation = true) }
            }
            if (work.isNotBlank()) {
                NavShortcut(Icons.Filled.Work, "Travail") { controller.routeTo(work, startNavigation = true) }
            }
            if (favorites.isNotEmpty()) {
                NavShortcut(Icons.Filled.Star, "Favoris") { showFavorites = true }
            }
            NavShortcut(Icons.Filled.Search, "Chercher") { controller.searching = true }
        }
    }

    if (showFavorites) {
        PickerDialog("Aller à…", onDismiss = { showFavorites = false }) {
            favorites.forEach { fav ->
                BigAction(
                    Icons.Filled.Star,
                    fav.name,
                    CockpitColors.Nav,
                    {
                        showFavorites = false
                        controller.routeTo(fav.address, startNavigation = true)
                    },
                    Modifier.fillMaxWidth(),
                    height = 64.dp,
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavShortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    BigAction(icon, label, CockpitColors.Nav, onClick, Modifier.weight(1f), height = 56.dp)
}

/** Petit dialogue de choix avec de grosses lignes, réutilisé par les zones. */
@Composable
fun PickerDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CockpitColors.CardHigh)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = CockpitColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Pendant la lecture d'un message : gros bouton pour la couper immédiatement. */
@Composable
fun BoxScope.SpeechStopBanner() {
    val speaking by Speech.speaking.collectAsState()
    if (!speaking) return
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = 80.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF2B3261E))
            .clickable { Speech.stop() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text("Lecture du message", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        Text("Arrêter", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
