package com.aragon.xvc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.*
import androidx.core.app.NotificationCompat
import com.aragon.xvc.input.ControllerState
import com.aragon.xvc.input.applyKeyToState
import com.aragon.xvc.input.applyMotionToState
import com.aragon.xvc.net.ControllerClient
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class InputService : Service() {
    private val TAG = "InputService"
    private val CHANNEL_ID = "xvc_client"
    private val NOTIF_ID = 1

    private var overlayView: CaptureOverlay? = null
    private var windowManager: WindowManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val client = ControllerClient()
    private val state = ControllerState()
    private val last = ControllerState()

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var targetHost: String? = null
    @Volatile private var targetPort: Int = 39500
    @Volatile private var evtCount: Int = 0
    @Volatile private var lastNotifMs: Long = 0
    @Volatile private var connectLoopStarted: Boolean = false
    @Volatile private var desiredConnected: Boolean = false
    // Lógica simple (sin agregador) como la versión que funcionaba

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        acquireLocks()
        try { showOverlay() } catch (_: Exception) {}
        // Auto-connect from stored prefs if present
        try {
            val p = getSharedPreferences("xvc", Context.MODE_PRIVATE)
            val host = p.getString("host", null)
            val port = p.getInt("port", 39500)
            if (!host.isNullOrBlank()) {
                targetHost = host
                targetPort = port
                startOrRetryConnect()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                desiredConnected = false
                try { client.disconnect() } catch (_: Exception) {}
                stopSelf();
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                targetHost = intent.getStringExtra("host")
                targetPort = intent.getIntExtra("port", 39500)
                desiredConnected = true
                startOrRetryConnect()
            }
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "XVC Client", NotificationManager.IMPORTANCE_LOW))
        }
        val openPI = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPI = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("XVC Client activo")
            .setContentText(currentNotifText())
            .setOngoing(true)
            .setContentIntent(openPI)
            .addAction(0, "Detener", stopPI)
        if (Build.VERSION.SDK_INT >= 34) builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        startForeground(NOTIF_ID, builder.build())
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xvc:client").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "xvc:wifi").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        if (windowManager == null) windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
    val p = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.START or Gravity.TOP
    overlayView = CaptureOverlay(this) {
            val k = it.first
            var changed = applyKeyToState(k, state)
            it.second?.let { ev ->
                val before = state.copy()
                val beforeBtns = before.btnMask
                applyMotionToState(ev, state)
                // Deadzone y filtro leve en ejes; los botones se comparan aparte
                fun near(a: Int, b: Int, dz: Int) = kotlin.math.abs(a - b) <= dz
                val axisChanged = (!near(state.lx, before.lx, 500) || !near(state.ly, before.ly, 500) ||
                                   !near(state.rx, before.rx, 500) || !near(state.ry, before.ry, 500) ||
                                   !near(state.lt, before.lt, 2) || !near(state.rt, before.rt, 2))
                val btnChanged = state.btnMask != beforeBtns
                if (axisChanged || btnChanged) {
                    changed = true
                } else {
                    // revertir sólo ejes (mantener btnMask intacto)
                    state.lx = before.lx; state.ly = before.ly
                    state.rx = before.rx; state.ry = before.ry
                    state.lt = before.lt; state.rt = before.rt
                }
            }
            if (changed) {
                sendIfChanged(); recordInput()
            }
        }
    try { windowManager?.addView(overlayView, p); overlayView?.requestFocus() } catch (_: Exception) {}
    }

    private fun sendIfChanged() { if (state != last) { client.sendState(state); last.copyFrom(state) } }

    private fun startOrRetryConnect() {
        if (connectLoopStarted) { maybeUpdateNotif(); return }
        connectLoopStarted = true
        executor.execute {
            var delay = 500L
            while (!executor.isShutdown) {
                try {
                    if (!desiredConnected) {
                        // Usuario no pidió conexión; no reconectar
                        Thread.sleep(1000)
                        continue
                    }
                    if (client.isConnected()) {
                        // Connected; refresh notif occasionally
                        delay = 500L
                        maybeUpdateNotif()
                        Thread.sleep(2000)
                        continue
                    }
                    val host = targetHost ?: discoverOnce()?.first
                    val port = targetPort
                    if (host != null) {
                        val ok = client.connect(host, port)
                        if (ok) {
                            targetHost = host
                            delay = 500L
                            maybeUpdateNotif()
                            continue
                        }
                    }
                    // Not connected, backoff
                    Thread.sleep(delay)
                    delay = (delay * 2).coerceAtMost(5000L)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    // ignore and retry with backoff
                    try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                }
            }
        }
    }

    private fun recordInput() {
        evtCount++
        maybeUpdateNotif()
    }

    private fun currentNotifText(): String {
        val h = targetHost
        val base = if (h.isNullOrBlank()) "Buscando…" else "Host $h:$targetPort"
        return "$base • eventos $evtCount"
    }

    private fun maybeUpdateNotif() {
        val now = System.currentTimeMillis()
        if (now - lastNotifMs < 1500) return
        lastNotifMs = now
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openPI = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPI = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("XVC Client activo")
            .setContentText(currentNotifText())
            .setOngoing(true)
            .setContentIntent(openPI)
            .addAction(0, "Detener", stopPI)
        nm.notify(NOTIF_ID, b.build())
    }

    private fun discoverOnce(timeoutMs: Int = 1500): Pair<String, Int>? {
        return try {
            val DISC_PORT = 39501
            val buf = "XVC_DISCOVER".toByteArray()
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = timeoutMs
                val pkt = DatagramPacket(buf, buf.size, InetAddress.getByName("255.255.255.255"), DISC_PORT)
                sock.send(pkt)
                val inBuf = ByteArray(1024)
                val resp = DatagramPacket(inBuf, inBuf.size)
                sock.receive(resp)
                val s = String(resp.data, 0, resp.length, Charsets.UTF_8)
                val js = JSONObject(s)
                if (js.optString("t") == "xvc") Pair(js.optString("ip", resp.address.hostAddress), js.optInt("port", 39500)) else null
            }
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        super.onDestroy()
    try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
    desiredConnected = false
    client.disconnect()
        executor.shutdownNow()
        try { wakeLock?.release() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_STOP = "STOP"
        const val ACTION_CONNECT = "CONNECT"
    }
}

// Overlay 1x1 que captura teclas y ejes de gamepads, sin enviar toques a otras apps.
class CaptureOverlay(ctx: Context, private val onInput: (Pair<android.view.KeyEvent, android.view.MotionEvent?>) -> Unit): View(ctx) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        requestFocus()
    }
    private val focusKeeper = object: Runnable { override fun run() { if (!hasWindowFocus()) requestFocus(); postDelayed(this, 2000) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(focusKeeper, 2000) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(focusKeeper) }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (!hasWindowFocus) post { requestFocus() } }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { onInput(Pair(event, null)); return true }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean { onInput(Pair(event, null)); return true }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean { onInput(Pair(event, null)); return true }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val src = event.source
        val isGamepad = (src and InputDevice.SOURCE_GAMEPAD) != 0 || (src and InputDevice.SOURCE_JOYSTICK) != 0
        if (!isGamepad) return super.onGenericMotionEvent(event)
        if (event.action != MotionEvent.ACTION_MOVE && event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_UP) return super.onGenericMotionEvent(event)
        onInput(Pair(KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN), event))
        return true
    }
}
