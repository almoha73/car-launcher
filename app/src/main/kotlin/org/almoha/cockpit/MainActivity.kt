package org.almoha.cockpit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.almoha.cockpit.service.AutoLaunchNotifier
import org.almoha.cockpit.service.FloatingCockpitService
import org.almoha.cockpit.ui.CockpitRoot
import org.almoha.cockpit.ui.CockpitTheme
import org.almoha.cockpit.util.isPinned

class MainActivity : ComponentActivity() {

    private val vm: CarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoLaunchNotifier.cancel(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        hideSystemBars()
        vm.beforeExternalLaunch = {
            if (isPinned()) runCatching { stopLockTask() }
        }
        setContent {
            CockpitTheme {
                CockpitRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FloatingCockpitService.hide(this)
        AutoLaunchNotifier.cancel(this)
        hideSystemBars()
        vm.onResume()
        if (vm.state.value.prefs.lockDown && !isPinned()) {
            runCatching { startLockTask() }
        } else if (!vm.state.value.prefs.lockDown && isPinned()) {
            runCatching { stopLockTask() }
        }
    }

    override fun onDestroy() {
        vm.beforeExternalLaunch = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
