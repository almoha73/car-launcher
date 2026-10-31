package org.almoha.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.AppEntry

@Composable
fun DimOverlay(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.DarkMode,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Touchez pour réveiller l'écran",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun AppsOverlay(
    state: UiState,
    picker: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit,
    onToggleDock: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = state.dock.map { it.packageName }
    val apps = remember(query, state.apps) {
        if (query.isBlank()) {
            state.apps
        } else {
            state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = shape,
            color = Color(0xFF0A0F16),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitColors.Stroke),
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = CockpitColors.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            if (picker) "Barre du bas" else "Applications",
                            color = CockpitColors.Text,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (picker) {
                            Text(
                                "${selected.size} / 8",
                                color = CockpitColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SearchField(query, { query = it }, Modifier.width(230.dp))
                    Spacer(Modifier.width(12.dp))
                    StatusIconButton(
                        Icons.Filled.Close,
                        "Fermer",
                        onDismiss,
                        size = 40.dp,
                        iconSize = 19.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in selected
                        AppGridTile(
                            app = app,
                            picker = picker,
                            selected = isSelected,
                            enabled = !picker || isSelected || selected.size < 8,
                            onClick = {
                                if (picker) onToggleDock(app.packageName) else onLaunch(app.packageName)
                            },
                        )
                    }
                }
                if (picker) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Touchez pour ajouter ou retirer une application (8 maximum).",
                            color = CockpitColors.TextMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CockpitColors.Accent,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Terminé", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = CockpitColors.TextMuted,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Rechercher", color = CockpitColors.TextMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = CockpitColors.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(CockpitColors.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppGridTile(
    app: AppEntry,
    picker: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .then(
                if (picker && selected) {
                    Modifier
                        .background(CockpitColors.Accent.copy(alpha = 0.10f))
                        .border(1.5.dp, CockpitColors.Accent.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppImage(app, 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                color = CockpitColors.TextDim,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (picker) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) CockpitColors.Accent else Color.White.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingOverlay(
    onAllow: () -> Unit,
    onLater: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .widthIn(max = 560.dp)
                .heightIn(max = maxHeight - 24.dp)
                .padding(12.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF0C1219),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitColors.Stroke),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        CockpitColors.Accent.copy(alpha = 0.35f),
                                        CockpitColors.Nav.copy(alpha = 0.05f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = CockpitColors.Accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Bienvenue dans Cockpit",
                            color = CockpitColors.Text,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Votre écran de conduite",
                            color = CockpitColors.TextDim,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Navigation, musique, appels et assistant, en gros boutons. Pour fonctionner en voiture, Cockpit a besoin de :",
                    color = CockpitColors.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                PermissionBullet(
                    Icons.Filled.Contacts,
                    "Téléphone et contacts",
                    "Appeler vos favoris en un geste",
                )
                PermissionBullet(
                    Icons.Filled.LocationOn,
                    "Position",
                    "Afficher la météo locale",
                )
                PermissionBullet(
                    Icons.Filled.NotificationsActive,
                    "Notifications",
                    "Piloter la musique et lire les messages à voix haute",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le Bluetooth (facultatif) permet de lancer Cockpit à la connexion de la voiture.",
                    color = CockpitColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onLater) {
                        Text("Plus tard", color = CockpitColors.TextDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onAllow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitColors.Accent,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Autoriser", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CockpitColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CockpitColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                detail,
                color = CockpitColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
fun ExitOverlay(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.widthIn(max = 420.dp).padding(20.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0C1219),
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitColors.Stroke),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Quitter le cockpit ?",
                    color = CockpitColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La lecture des messages à voix haute sera désactivée et l'écran pourra se mettre en veille.",
                    color = CockpitColors.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = CockpitColors.TextDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitColors.Warning.copy(alpha = 0.9f),
                            contentColor = Color(0xFF1A0505),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Quitter", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
