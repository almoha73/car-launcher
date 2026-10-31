package org.almoha.cockpit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.AppEntry
import org.almoha.cockpit.data.KnownApps

/** Empêche le dock plein écran de se masquer (et le lecteur de rétrécir) tant qu'un dialogue est ouvert. */
val LocalKeepAwake = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/** Sélecteur de source (et catalogue Tidal) : renvoie la fonction qui l'ouvre, les dialogues sont gérés ici. */
@Composable
fun rememberSourcePicker(state: UiState, vm: CarViewModel): () -> Unit {
    var open by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    if (open) {
        val keepAwake = LocalKeepAwake.current
        DisposableEffect(Unit) {
            keepAwake(true)
            onDispose { keepAwake(false) }
        }
        MediaSourceDialog(state, vm, onBrowseTidal = { open = false; browse = true }) { open = false }
    }
    if (browse) TidalBrowserDialog(vm) { browse = false }
    return { open = true }
}

/** Icône de l'appli média en cours ; un appui ouvre le sélecteur de source. */
@Composable
fun MediaSourceButton(state: UiState, vm: CarViewModel, size: Dp) {
    val openPicker = rememberSourcePicker(state, vm)
    val media = state.media
    val icon = media.appIcon ?: state.apps.firstOrNull { it.packageName == media.packageName }?.icon
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = openPicker),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon.asImageBitmap(), "Changer de source", Modifier.size(size * 0.78f))
        } else {
            Icon(Icons.Filled.LibraryMusic, "Changer de source", tint = CockpitColors.Media, modifier = Modifier.size(size * 0.6f))
        }
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.36f)
                .background(CockpitColors.Media, CircleShape)
                .padding(2.dp),
        )
    }
}

@Composable
private fun MediaSourceDialog(state: UiState, vm: CarViewModel, onBrowseTidal: () -> Unit, onDismiss: () -> Unit) {
    val media = state.media
    val byPkg = state.apps.associateBy { it.packageName }
    val sources = (KnownApps.MEDIA_SOURCES + media.packageName)
        .filter { it.isNotBlank() }
        .distinct()
        .mapNotNull { byPkg[it] }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CockpitColors.CardHigh)
                .padding(20.dp),
        ) {
            Text("Écouter sur…", color = CockpitColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            sources.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { app ->
                        SourceTile(app, current = app.packageName == media.packageName, Modifier.weight(1f)) {
                            vm.switchMediaSource(app.packageName)
                            onDismiss()
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (sources.any { it.packageName == KnownApps.TIDAL }) {
                DialogAction(Icons.AutoMirrored.Filled.QueueMusic, "Parcourir Tidal", onBrowseTidal)
                Spacer(Modifier.height(10.dp))
            }
            if (media.hasSession) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            vm.openMediaApp()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = CockpitColors.TextDim)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Ouvrir ${media.appLabel.ifBlank { "l'appli" }}",
                        color = CockpitColors.Text,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CockpitColors.Media.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CockpitColors.Media)
        Spacer(Modifier.width(12.dp))
        Text(label, color = CockpitColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourceTile(app: AppEntry, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (current) CockpitColors.Media.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(2.dp, if (current) CockpitColors.Media else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(icon.asImageBitmap(), app.label, Modifier.size(56.dp))
        } else {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(CockpitColors.Media.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(app.label.take(1), color = CockpitColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = if (current) CockpitColors.Media else CockpitColors.Text,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
