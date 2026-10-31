package org.almoha.cockpit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.media.BrowseMediaItem

/** Catalogue Tidal (accueil, playlists, favoris…) parcouru sans quitter le cockpit, avec de grosses lignes. */
@Composable
fun TidalBrowserDialog(vm: CarViewModel, onDismiss: () -> Unit) {
    val catalog by vm.tidalCatalog.collectAsState()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }

    val keepAwake = LocalKeepAwake.current
    DisposableEffect(Unit) {
        keepAwake(true)
        onDispose {
            keepAwake(false)
            vm.closeTidalCatalog()
        }
    }
    LaunchedEffect(Unit) { vm.openTidalCatalog() }

    fun play(item: BrowseMediaItem) {
        vm.playTidalItem(item)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler { if (!vm.tidalCatalogBack()) onDismiss() }
        Column(
            Modifier
                .fillMaxSize()
                .background(CockpitColors.Background)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (catalog.path.isNotEmpty()) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") { vm.tidalCatalogBack() }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    catalog.path.lastOrNull()?.title ?: "Tidal",
                    color = CockpitColors.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RoundIcon(Icons.Filled.Close, "Fermer", onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, CockpitColors.Media.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = CockpitColors.Media, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Artiste, titre ou playlist…", color = CockpitColors.TextMuted, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = CockpitColors.Text, fontSize = 17.sp),
                        cursorBrush = SolidColor(CockpitColors.Media),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) {
                                focusManager.clearFocus()
                                vm.playSearchInTidal(query)
                                onDismiss()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                catalog.loading && catalog.items.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = CockpitColors.Media) }

                catalog.error != null && catalog.items.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { Text(catalog.error.orEmpty(), color = CockpitColors.TextDim, fontSize = 16.sp) }

                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(catalog.items, key = { it.mediaId + it.title }) { item ->
                        CatalogRow(item, onOpen = { vm.browseTidal(item) }, onPlay = { play(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(item: BrowseMediaItem, onOpen: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = if (item.browsable) onOpen else onPlay)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = CockpitColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    color = CockpitColors.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Dossier jouable (playlist, album) : bouton lecture directe + flèche pour l'ouvrir.
        if (item.playable) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CockpitColors.Media.copy(alpha = 0.2f))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, "Lire", tint = CockpitColors.Media, modifier = Modifier.size(30.dp))
            }
        }
        if (item.browsable) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CockpitColors.TextDim,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = CockpitColors.Text, modifier = Modifier.size(28.dp))
    }
}
