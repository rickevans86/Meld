package com.metrolist.music.recognition

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun constrainFloatingPosition(x: Int, y: Int, width: Int, height: Int, bubbleWidth: Int, bubbleHeight: Int): Pair<Int, Int> =
    x.coerceIn(0, (width - bubbleWidth).coerceAtLeast(0)) to y.coerceIn(0, (height - bubbleHeight).coerceAtLeast(0))

internal fun recognitionBusy(status: RecognitionStatus): Boolean =
    status is RecognitionStatus.Listening || status is RecognitionStatus.Processing

/** Visible user-enabled shortcut; microphone capture belongs to the separate one-shot service. */
class FloatingRecognitionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var bubble: LinearLayout? = null
    private var receiverRegistered = false
    private val layout by lazy {
        WindowManager.LayoutParams(dp(112), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(180) }
    }
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!Settings.canDrawOverlays(this@FloatingRecognitionService)) { stopSelf(); return }
            bubble?.visibility = if (intent?.action == Intent.ACTION_USER_PRESENT) View.VISIBLE else View.GONE
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE || !Settings.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        if (bubble != null) return START_NOT_STICKY
        try {
            startVisibleForeground()
            createBubble()
            ContextCompat.registerReceiver(this, lockReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            _running.value = true
        } catch (error: Exception) {
            Timber.w(error, "Unable to show floating recognition button")
            Toast.makeText(this, R.string.floating_recognition_start_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun startVisibleForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CHANNEL, getString(R.string.floating_recognition_title), NotificationManager.IMPORTANCE_LOW))
        val hide = PendingIntent.getService(this, 0, Intent(this, FloatingRecognitionService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.mic)
            .setContentTitle(getString(R.string.floating_recognition_title))
            .setContentText(getString(R.string.floating_recognition_notification))
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(R.drawable.close, getString(R.string.floating_recognition_hide), hide).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION, notification)
    }
    private fun createBubble() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { setColor(Color.rgb(37, 39, 53)); cornerRadius = dp(28).toFloat() }
            elevation = dp(8).toFloat()
        }
        val recognise = ImageButton(this).apply {
            setImageResource(R.drawable.mic); setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(157, 187, 255))
            contentDescription = getString(R.string.floating_recognition_tap)
            setOnClickListener { recogniseOrCancel() }
        }
        val result = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; maxLines = 3; visibility = View.GONE
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener {
                startActivity(Intent(this@FloatingRecognitionService, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_RECOGNITION).putExtra(MainActivity.EXTRA_OPEN_RECOGNITION_HISTORY, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        val hide = ImageButton(this).apply {
            setImageResource(R.drawable.close); setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = getString(R.string.floating_recognition_hide)
            setOnClickListener { stopSelf() }
        }
        root.addView(recognise, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(result, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(hide, LinearLayout.LayoutParams(dp(48), dp(48)))
        attachDrag(recognise)
        attachDrag(result)
        bubble = root
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) root.visibility = View.GONE
        windows.addView(root, layout)
        var seenOwnAttempt = false
        scope.launch {
            combine(RecognitionForegroundService.floatingRunning, MusicRecognitionService.recognitionStatus) { own, status -> own to status }
                .collect { (own, status) ->
                    if (!own && !seenOwnAttempt) return@collect
                    if (own) seenOwnAttempt = true
                    val busy = own && recognitionBusy(status)
                    recognise.setImageResource(if (busy) R.drawable.close else R.drawable.mic)
                    recognise.contentDescription = getString(if (busy) R.string.floating_recognition_cancel else R.string.floating_recognition_tap)
                    val text = when {
                        !own && recognitionBusy(status) -> null
                        status is RecognitionStatus.Success -> "${status.result.title}\n${status.result.artist}"
                        status is RecognitionStatus.Listening -> getString(R.string.recognition_notification_listening)
                        status is RecognitionStatus.Processing -> getString(R.string.recognition_notification_processing)
                        status is RecognitionStatus.NoMatch -> getString(R.string.recognition_notification_no_match)
                        status is RecognitionStatus.Error -> getString(R.string.recognition_notification_failed)
                        else -> null
                    }
                    result.text = text
                    result.visibility = if (text == null) View.GONE else View.VISIBLE
                    result.isClickable = status is RecognitionStatus.Success
                    layout.width = if (text == null) dp(112) else minOf(dp(300), resources.displayMetrics.widthPixels)
                    reposition(layout.x, layout.y)
                    // Keep the completed result; ignore recognition later started from a different surface.
                    if (!own) seenOwnAttempt = false
                }
        }
    }
    private fun recogniseOrCancel() {
        val status = MusicRecognitionService.recognitionStatus.value
        if (RecognitionForegroundService.floatingRunning.value) {
            if (recognitionBusy(status)) stopOwnRecognition(this)
            return
        }
        if (recognitionBusy(status) || AutoRecognitionService.running.value) {
            Toast.makeText(this, R.string.floating_recognition_busy, Toast.LENGTH_SHORT).show(); return
        }
        try {
            startActivity(Intent(this, RecognitionLaunchActivity::class.java)
                .putExtra(RecognitionLaunchActivity.EXTRA_FLOATING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (error: RuntimeException) {
            Timber.w(error, "Could not launch floating recognition")
            Toast.makeText(this, R.string.floating_recognition_start_failed, Toast.LENGTH_LONG).show()
        }
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(view: View) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) reposition(startX + dx.roundToInt(), startY + dy.roundToInt())
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    private fun reposition(x: Int, y: Int) {
        val root = bubble ?: return
        val metrics = resources.displayMetrics
        val (cx, cy) = constrainFloatingPosition(x, y, metrics.widthPixels, metrics.heightPixels, layout.width, root.height.coerceAtLeast(dp(56)))
        layout.x = cx; layout.y = cy
        runCatching { windows.updateViewLayout(root, layout) }.onFailure { stopSelf() }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig); reposition(layout.x, layout.y)
    }
    override fun onDestroy() {
        _running.value = false
        stopOwnRecognition(this)
        scope.cancel()
        if (receiverRegistered) unregisterReceiver(lockReceiver)
        bubble?.let { runCatching { windows.removeView(it) } }; bubble = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    companion object {
        private const val CHANNEL = "floating_recognition"
        private const val NOTIFICATION = 9103
        const val ACTION_HIDE = "com.metrolist.music.floating.HIDE"
        private val _running = MutableStateFlow(false)
        val running = _running.asStateFlow()
        fun show(context: Context): Boolean = try {
            if (!Settings.canDrawOverlays(context)) false
            else { ContextCompat.startForegroundService(context, Intent(context, FloatingRecognitionService::class.java)); true }
        } catch (error: RuntimeException) { Timber.w(error, "Could not start floating recognition shortcut"); false }
        fun hide(context: Context) { context.stopService(Intent(context, FloatingRecognitionService::class.java)) }
        private fun stopOwnRecognition(context: Context) {
            if (RecognitionForegroundService.floatingRunning.value) {
                runCatching { context.startService(Intent(context, RecognitionForegroundService::class.java)
                    .setAction(RecognitionForegroundService.ACTION_STOP_FLOATING)) }
            }
        }
    }
}
