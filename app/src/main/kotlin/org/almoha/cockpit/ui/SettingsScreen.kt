package org.almoha.cockpit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.UiState
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.data.NavMode

@Composable
fun SettingsScreen(
    state: UiState,
    vm: CarViewModel,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack)
            Spacer(Modifier.width(14.dp))
            Text(
                "Réglages",
                color = CockpitColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text("Terminé", color = CockpitColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard("Accès") {
                    PermissionRow(
                        "Téléphone",
                        "Appeler directement depuis le cockpit",
                        state.permissions.call,
                        onRequestPermissions,
                    )
                    Divider()
                    PermissionRow(
                        "Contacts",
                        "Afficher vos favoris",
                        state.permissions.contacts,
                        onRequestPermissions,
                    )
                    Divider()
                    PermissionRow(
                        "Position",
                        "Météo locale",
                        state.permissions.location,
                        onRequestPermissions,
                    )
                    Divider()
                    PermissionRow(
                        "Notifications",
                        "Autoriser Cockpit à afficher des notifications",
                        state.permissions.notifications,
                        onRequestPermissions,
                    )
                    Divider()
                    PermissionRow(
                        "Accès notifications (média)",
                        "Piloter Tidal, YouTube Music, etc. et lire les messages",
                        state.permissions.notificationAccess,
                        vm::openNotificationSettings,
                        actionLabel = "Ouvrir",
                    )
                    Divider()
                    PermissionRow(
                        "Bluetooth",
                        "Détecter la connexion à la voiture",
                        state.permissions.bluetooth,
                        onRequestPermissions,
                    )
                    Divider()
                    PermissionRow(
                        "Afficher par-dessus (Bandeau Maps)",
                        "Afficher le bandeau flottant Tidal + Favoris par-dessus Google Maps",
                        state.permissions.overlay,
                        vm::openOverlaySettings,
                        actionLabel = "Ouvrir",
                    )
                }
            }

            item {
                SettingsCard("Navigation & Partage d'écran") {
                    Text(
                        "Choisissez comment combiner la navigation et la musique :",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    DeviceRow(
                        name = "1. Carte GPS intégrée dans Cockpit (Style Android Auto)",
                        detail = "Carte sombre en direct, compteur km/h et recherche d'itinéraire sans quitter l'écran",
                        selected = state.prefs.navMode == NavMode.EMBEDDED_MAP,
                        onClick = { vm.setNavMode(NavMode.EMBEDDED_MAP) },
                    )
                    DeviceRow(
                        name = "2. Google Maps + Bandeau flottant (Écran partagé)",
                        detail = "Affiche une bulle média flottante (lecture, ±10 s, favori) par-dessus Google Maps",
                        selected = state.prefs.navMode == NavMode.FLOATING_SPLIT,
                        onClick = {
                            vm.setNavMode(NavMode.FLOATING_SPLIT)
                            vm.setFloatingBar(true)
                        },
                    )
                    DeviceRow(
                        name = "3. Instructions Google Maps en direct dans Cockpit",
                        detail = "Affiche les flèches, la rue, la distance et l'heure d'arrivée de Google Maps dans Cockpit",
                        selected = state.prefs.navMode == NavMode.LIVE_DIRECTIONS,
                        onClick = { vm.setNavMode(NavMode.LIVE_DIRECTIONS) },
                    )
                    Divider()
                    SettingSwitch(
                        "Bulle média automatique sur Google Maps",
                        "Garde la musique à portée de doigt quand Google Maps s'ouvre",
                        state.prefs.floatingBar,
                        vm::setFloatingBar,
                    )
                }
            }

            item {
                SettingsCard("Voiture") {
                    SettingSwitch(
                        "Lancer à la connexion de la voiture",
                        "Ouvre Cockpit quand le Bluetooth choisi se connecte",
                        state.prefs.autoLaunch,
                        vm::setAutoLaunch,
                    )
                    if (state.prefs.autoLaunch && !state.permissions.overlay) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Android limite le lancement en arrière-plan. Autorisez « Afficher par-dessus » pour plus de fiabilité.",
                                color = CockpitColors.Ev,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = vm::openOverlaySettings) {
                                Text("Autoriser", color = CockpitColors.Ev)
                            }
                        }
                    }
                    Divider()
                    Text(
                        if (state.bluetoothDevices.isEmpty()) {
                            "Aucun appareil Bluetooth jumelé (ou autorisation manquante)."
                        } else {
                            "Choisissez votre voiture :"
                        },
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (state.permissions.bluetooth && state.bluetoothDevices.isNotEmpty()) {
                        DeviceRow(
                            name = "Aucune voiture",
                            detail = "Démarrage automatique désactivé",
                            selected = state.prefs.carBtAddress.isBlank(),
                            onClick = {
                                vm.setCarDevice("", "")
                                vm.setAutoLaunch(false)
                            },
                        )
                        state.bluetoothDevices.take(6).forEach { device ->
                            DeviceRow(
                                name = device.name,
                                detail = device.address,
                                selected = state.prefs.carBtAddress == device.address,
                                onClick = {
                                    vm.setCarDevice(device.name, device.address)
                                    vm.setAutoLaunch(true)
                                },
                            )
                        }
                    }
                    Divider()
                    SettingSwitch(
                        "Lire les messages à voix haute",
                        "WhatsApp et SMS, pendant la session de conduite",
                        state.prefs.readMessages,
                        vm::setReadMessages,
                    )
                    Divider()
                    SettingSwitch(
                        "Garder l'écran allumé",
                        "L'écran ne se met pas en veille",
                        state.prefs.keepScreenOn,
                        vm::setKeepScreenOn,
                    )
                    Divider()
                    SettingSwitch(
                        "Épingler au lancement",
                        "Verrouille Cockpit contre les sorties accidentelles ; libéré le temps d'ouvrir une autre app",
                        state.prefs.lockDown,
                        vm::setLockDown,
                    )
                }
            }

            item {
                SettingsCard("Destinations") {
                    Text(
                        "Utilisées par les boutons Maison et Travail. Laissez vide pour ouvrir simplement l'application de navigation.",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    AddressField(
                        value = state.prefs.home,
                        onValueChange = vm::setHome,
                        label = "Maison",
                        icon = Icons.Filled.Home,
                    )
                    Spacer(Modifier.height(10.dp))
                    AddressField(
                        value = state.prefs.work,
                        onValueChange = vm::setWork,
                        label = "Travail",
                        icon = Icons.Filled.Work,
                    )
                    Divider()
                    Text(
                        "Favoris de navigation : proposés en touchant « Où aller ? » sur la carte, " +
                            "et à la voix (« va à <nom> »). L'étoile sur la carte ajoute la destination affichée.",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    state.prefs.navFavorites.forEach { fav ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC94D), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(fav.name, color = CockpitColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                if (fav.address != fav.name) {
                                    Text(fav.address, color = CockpitColors.TextDim, fontSize = 12.sp)
                                }
                            }
                            TextButton(onClick = { vm.removeNavFavorite(fav.address) }) {
                                Text("Supprimer", color = CockpitColors.Warning)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    NewFavoriteForm(onAdd = vm::addNavFavorite)
                }
            }

            item {
                SettingsCard("Applications du bas") {
                    Text(
                        "Les raccourcis affichés dans la barre inférieure.",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.dock.forEach { app ->
                                AppImage(app, 40.dp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                vm.loadAllIcons()
                                showPicker = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = CockpitColors.Text,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Modifier", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Application de navigation : " +
                            if (state.apps.any { it.packageName == KnownApps.MAPS }) "Google Maps" else "aucune",
                        color = CockpitColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            item {
                SettingsCard("À propos") {
                    Text(
                        "Cockpit 1.0",
                        color = CockpitColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Navigation, musique, appels et assistant réunis dans un écran de conduite. Météo : Open-Meteo.",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Astuce : ajoutez la tuile « Cockpit » dans les réglages rapides Android pour l'ouvrir en un geste.",
                        color = CockpitColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }

    if (showPicker) {
        AppsOverlay(
            state = state,
            picker = true,
            onDismiss = { showPicker = false },
            onLaunch = {},
            onToggleDock = vm::toggleDock,
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0C1219))
            .border(1.dp, CockpitColors.Stroke, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Text(
            title.uppercase(),
            color = CockpitColors.TextDim,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(CockpitColors.Stroke),
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = CockpitColors.Text, fontSize = 14.sp)
            Text(
                description,
                color = CockpitColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF062028),
                checkedTrackColor = CockpitColors.Accent,
                uncheckedThumbColor = CockpitColors.TextDim,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    actionLabel: String = "Autoriser",
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (granted) CockpitColors.Charging else CockpitColors.Warning),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = CockpitColors.Text, fontSize = 14.sp)
            Text(description, color = CockpitColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (granted) {
            Text("Autorisé", color = CockpitColors.Charging, fontSize = 12.sp)
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = CockpitColors.Accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = CockpitColors.Accent,
                unselectedColor = CockpitColors.TextMuted,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = CockpitColors.Text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, color = CockpitColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    // État local : relire la valeur depuis les préférences (asynchrone) ferait perdre des caractères en tapant vite.
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text("Adresse ou lieu", fontSize = 13.sp) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = CockpitColors.TextDim, modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CockpitColors.Text,
            unfocusedTextColor = CockpitColors.Text,
            focusedBorderColor = CockpitColors.Accent,
            unfocusedBorderColor = CockpitColors.Stroke,
            focusedLabelColor = CockpitColors.Accent,
            unfocusedLabelColor = CockpitColors.TextDim,
            cursorColor = CockpitColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewFavoriteForm(onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AddressField(value = name, onValueChange = { name = it }, label = "Nom (ex. Mamie)", icon = Icons.Filled.Star)
    Spacer(Modifier.height(8.dp))
    AddressField(value = address, onValueChange = { address = it }, label = "Adresse", icon = Icons.Filled.Place)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            onAdd(name, address)
            name = ""
            address = ""
        },
        enabled = name.isNotBlank() && address.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = CockpitColors.Accent, contentColor = Color.Black),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Ajouter le favori", fontWeight = FontWeight.SemiBold)
    }
}
