package org.almoha.cockpit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.almoha.cockpit.MainActivity
import org.almoha.cockpit.R
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.media.CarNotificationListener
import org.almoha.cockpit.media.MediaRepository
import org.almoha.cockpit.util.Launcher
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs

class FloatingCockpitService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private lateinit var mediaRepo: MediaRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaRepo = MediaRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service au premier plan : sans cela Android l'arrête au bout d'une minute environ
        // quand Maps est affiché, et le bandeau disparaît.
        // startForeground doit être appelé même si on s'arrête juste après.
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        // Pas de redémarrage automatique : il réafficherait le bandeau même dans Cockpit.
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bandeau flottant", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Garde le bandeau Cockpit affiché par-dessus Google Maps"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Bandeau Cockpit actif")
            .setContentText("Touchez pour revenir à Cockpit")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    /**
     * Bulle flottante par-dessus Google Maps : grosse icône de l'appli média en cours, entourée de
     * favori, −10 s et +10 s. Toucher l'icône = lecture / pause, appui long = retour à Cockpit.
     * La pastille se déplace en glissant depuis n'importe où (fond ou boutons) et garde sa position.
     */
    private fun showOverlay() {
        if (rootView != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm

        val dm = resources.displayMetrics
        val bubbleSize = dp(84)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Dernière position choisie ; par défaut en bas au centre, par-dessus le bandeau d'arrivée de Maps
            // (la pastille reprend le temps restant, les km et l'heure d'arrivée).
            val saved = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE)
            x = saved.getInt(KEY_X, ((dm.widthPixels - dp(300)) / 2).coerceAtLeast(0))
            y = saved.getInt(KEY_Y, dm.heightPixels - dp(150))
        }

        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F2080D14"))
            setStroke(dp(3), Color.parseColor("#5596A2B2"))
        }
        val bubble = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            background = ring
            contentDescription = "Lecture / pause — appui long : Cockpit"
        }
        val appIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER)
            setImageResource(android.R.drawable.ic_media_play)
        }
        // Petit indicateur lecture / pause en bas à droite de l'icône.
        val stateBadge = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM or Gravity.END)
            background = circleBg("#FFFFFFFF")
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setColorFilter(Color.parseColor("#081018"))
            setImageResource(android.R.drawable.ic_media_play)
        }
        bubble.addView(appIcon)
        bubble.addView(stateBadge)

        val favBtn = sideButton("♡", "#EAF0F6")
        val rewBtn = sideButton("−10", "#1ED760")
        val fwdBtn = sideButton("+10", "#1ED760")
        // Livre audio / podcast : ±10 s. Musique : piste précédente / suivante.
        fun spoken(): Boolean = mediaRepo.state.value.let { KnownApps.isSpokenWord(it.packageName, it.durationMs) }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(favBtn)
            addView(rewBtn)
            addView(bubble)
            addView(fwdBtn)
        }
        // Infos de guidage de Google Maps : temps restant · km · heure d'arrivée.
        val etaView = TextView(this).apply {
            setTextColor(Color.parseColor("#1ED760"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(8), dp(2), dp(8), dp(4))
            visibility = View.GONE
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
                setColor(Color.parseColor("#E6080D14"))
                setStroke(dp(1), Color.parseColor("#554DD0E1"))
            }
            elevation = dp(10).toFloat()
            addView(etaView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(controls)
        }

        // Un seul geste pour toute la pastille : glisser la déplace, sinon c'est un toucher sur l'élément.
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        var longPressed = false
        var pendingLongPress: Runnable? = null
        fun cancelLongPress(view: View) {
            pendingLongPress?.let { view.removeCallbacks(it) }
            pendingLongPress = null
        }
        fun dragListener(onTap: (() -> Unit)?, onLongPress: (() -> Unit)? = null) =
            View.OnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        dragging = false
                        longPressed = false
                        view.isPressed = onTap != null
                        if (onLongPress != null) {
                            val longPress = Runnable {
                                longPressed = true
                                onLongPress()
                            }
                            pendingLongPress = longPress
                            view.postDelayed(longPress, 600L)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (dragging || abs(dx) > dp(8) || abs(dy) > dp(8)) {
                            dragging = true
                            view.isPressed = false
                            cancelLongPress(view)
                            params.x = (startX + dx).coerceIn(0, (dm.widthPixels - panel.width).coerceAtLeast(0))
                            params.y = (startY + dy).coerceIn(0, (dm.heightPixels - panel.height).coerceAtLeast(0))
                            runCatching { wm.updateViewLayout(panel, params) }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        cancelLongPress(view)
                        if (dragging) {
                            getSharedPreferences(POSITION_PREFS, MODE_PRIVATE).edit()
                                .putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                        } else if (!longPressed) {
                            onTap?.invoke()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        cancelLongPress(view)
                    }
                }
                true
            }

        panel.setOnTouchListener(dragListener(onTap = null))
        controls.setOnTouchListener(dragListener(onTap = null))
        etaView.setOnTouchListener(dragListener(onTap = null))
        favBtn.setOnTouchListener(dragListener({ mediaRepo.toggleFavorite() }))
        rewBtn.setOnTouchListener(dragListener({ if (spoken()) mediaRepo.seekBy(-10_000L) else mediaRepo.previous() }))
        fwdBtn.setOnTouchListener(dragListener({ if (spoken()) mediaRepo.seekBy(10_000L) else mediaRepo.next() }))
        bubble.setOnTouchListener(
            dragListener(
                onTap = {
                    // Un message est en cours de lecture : le premier appui le coupe.
                    if (org.almoha.cockpit.media.Speech.speaking.value) {
                        org.almoha.cockpit.media.Speech.stop()
                    } else if (mediaRepo.state.value.hasSession) {
                        mediaRepo.playPause()
                    } else if (!mediaRepo.openCurrentApp()) {
                        Launcher.openApp(this@FloatingCockpitService, KnownApps.TIDAL)
                    }
                },
                onLongPress = { returnToCockpit() },
            ),
        )

        runCatching {
            wm.addView(panel, params)
            rootView = panel
        }

        mediaRepo.start()
        CarNotificationListener.refreshNavigation()
        scope.launch {
            CarNotificationListener.navState.collect { nav ->
                val eta = formatEta(nav.eta)
                etaView.text = eta
                etaView.visibility = if (nav.active && eta.isNotBlank()) View.VISIBLE else View.GONE
            }
        }
        scope.launch {
            mediaRepo.state.collect { media ->
                val icon = media.appIcon
                if (media.hasSession && icon != null) {
                    appIcon.clearColorFilter()
                    appIcon.setImageBitmap(icon)
                } else {
                    appIcon.setImageResource(android.R.drawable.ic_media_play)
                    appIcon.setColorFilter(Color.parseColor("#EAF0F6"))
                }
                stateBadge.setImageResource(
                    if (media.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                )
                favBtn.text = if (media.isFavorite) "♥" else "♡"
                favBtn.setTextColor(Color.parseColor(if (media.isFavorite) "#FF5C8A" else "#EAF0F6"))
                val spokenWord = KnownApps.isSpokenWord(media.packageName, media.durationMs)
                rewBtn.text = if (spokenWord) "−10" else "⏮"
                fwdBtn.text = if (spokenWord) "+10" else "⏭"
                rewBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (spokenWord) 15f else 22f)
                fwdBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (spokenWord) 15f else 22f)
                favBtn.alpha = if (media.hasSession) 1f else 0.4f
                rewBtn.alpha = if (media.hasSession) 1f else 0.4f
                fwdBtn.alpha = if (media.hasSession) 1f else 0.4f
                // Anneau vert pendant la lecture, gris en pause.
                ring.setStroke(dp(3), Color.parseColor(if (media.isPlaying) "#1ED760" else "#5596A2B2"))
            }
        }
    }

    /**
     * Maps ne donne que « Arrivée à 12:23 » (ni temps ni km restants) : on en déduit le temps restant.
     * Résultat : « 2 h 44 · arrivée 12:23 » ou « 18 min · arrivée 10:05 ».
     */
    private fun formatEta(raw: String): String {
        val match = Regex("(\\d{1,2})\\s*[:h]\\s*(\\d{2})").find(raw) ?: return raw
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return raw
        val now = LocalTime.now()
        var minutes = Duration.between(now.withSecond(0).withNano(0), LocalTime.of(hour, minute)).toMinutes()
        if (minutes < 0) minutes += 24 * 60
        val remaining = if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
        return "$remaining · arrivée ${"%02d:%02d".format(hour, minute)}"
    }

    private fun sideButton(label: String, color: String): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor(color))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 1) 15f else 24f)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
        background = circleBg("#26FFFFFF")
    }

    private fun returnToCockpit() {
        removeOverlay()
        stopSelf()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(launch) }
    }

    private fun circleBg(hexColor: String): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(hexColor))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private fun removeOverlay() {
        val view = rootView ?: return
        rootView = null
        runCatching { windowManager?.removeView(view) }
    }

    override fun onDestroy() {
        removeOverlay()
        mediaRepo.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SHOW = "org.almoha.cockpit.SHOW_FLOATING"
        private const val POSITION_PREFS = "floating_bubble"
        // Nouvelles clés : la position par défaut est passée en bas de l'écran.
        private const val KEY_X = "x_bottom"
        private const val KEY_Y = "y_bottom"
        private const val CHANNEL_ID = "floating_bar"
        private const val NOTIFICATION_ID = 42

        fun show(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, FloatingCockpitService::class.java).apply {
                action = ACTION_SHOW
            }
            return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
        }

        // stopService ne démarre pas le service s'il ne tourne pas ; onDestroy retire le bandeau.
        fun hide(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingCockpitService::class.java)) }
        }
    }
}
