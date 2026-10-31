package org.almoha.cockpit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.almoha.cockpit.CarViewModel
import org.almoha.cockpit.data.NavMode
import org.almoha.cockpit.util.findActivity
import org.almoha.cockpit.util.isPinned

@Composable
fun CockpitRoot(vm: CarViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current

    var showSettings by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(!vm.state.value.prefs.onboardingDone) }
    // À l'ouverture : vue 3 blocs ; le plein écran se choisit avec le bouton de la carte.
    var mapFullScreen by rememberSaveable { mutableStateOf(false) }
    // Créée ici pour survivre au plein écran, à la rotation et à l'ouverture des réglages.
    val mapController = if (state.prefs.navMode == NavMode.EMBEDDED_MAP) {
        rememberMapController(state.permissions.location)
    } else {
        null
    }

    LaunchedEffect(state.prefs.keepScreenOn) {
        view.keepScreenOn = state.prefs.keepScreenOn
    }

    val voice = remember { VoiceController(context.applicationContext) }
    DisposableEffect(voice) {
        onDispose { voice.destroy() }
    }
    val currentState by rememberUpdatedState(state)
    val currentMap by rememberUpdatedState(mapController)
    SideEffect {
        voice.onResult = { text ->
            voice.showFeedback(executeVoiceCommand(text, currentState, vm, currentMap, context))
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else voice.showFeedback("Autorisez le micro pour les commandes vocales")
    }
    val startVoice: () -> Unit = {
        when {
            // Pas de reconnaissance vocale sur l'appareil : on se rabat sur l'assistant Google.
            !voice.isAvailable() -> vm.openAssistant()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> voice.start()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.refreshPermissions()
    }

    val requestCorePermissions: () -> Unit = {
        val permissions = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    BackHandler(enabled = showApps || showSettings) {
        when {
            showApps -> showApps = false
            showSettings -> showSettings = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CockpitColors.BackgroundTop, CockpitColors.Background),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (showSettings) {
                SettingsScreen(
                    state = state,
                    vm = vm,
                    onBack = { showSettings = false },
                    onRequestPermissions = requestCorePermissions,
                )
            } else {
                CockpitScreen(
                    state = state,
                    vm = vm,
                    mapController = mapController,
                    mapFullScreen = mapFullScreen,
                    onMapFullScreenChange = { mapFullScreen = it },
                    onSettings = { showSettings = true },
                    onExit = { showExit = true },
                    onOpenApps = {
                        showApps = true
                        vm.loadAllIcons()
                    },
                    onRequestPermissions = requestCorePermissions,
                    onVoice = startVoice,
                )
            }

            if (showApps) {
                AppsOverlay(
                    state = state,
                    picker = false,
                    onDismiss = { showApps = false },
                    onLaunch = { pkg ->
                        showApps = false
                        vm.launchApp(pkg)
                    },
                    onToggleDock = {},
                )
            }

            if (showExit) {
                ExitOverlay(
                    onConfirm = {
                        showExit = false
                        vm.exit()
                        activity?.let {
                            runCatching { if (it.isPinned()) it.stopLockTask() }
                            it.finishAndRemoveTask()
                        }
                    },
                    onDismiss = { showExit = false },
                )
            }

            if (onboarding) {
                OnboardingOverlay(
                    onAllow = {
                        onboarding = false
                        vm.setOnboardingDone()
                        requestCorePermissions()
                    },
                    onLater = {
                        onboarding = false
                        vm.setOnboardingDone()
                    },
                )
            }
        }

        if (state.dim) {
            DimOverlay(onDismiss = { vm.setDim(false) })
        }

        VoiceOverlay(voice)
        SpeechStopBanner()
    }
}
