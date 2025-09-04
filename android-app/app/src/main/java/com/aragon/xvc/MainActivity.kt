package com.aragon.xvc

import android.app.Activity
import android.Manifest
import android.hardware.input.InputManager
import android.os.Bundle
import android.text.InputFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.aragon.xvc.input.ControllerState
import com.aragon.xvc.input.getProfileForDevice
import com.aragon.xvc.input.applyKeyToStateWithProfile
import com.aragon.xvc.net.ControllerClient
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.Charset
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), InputManager.InputDeviceListener {
    private fun removeScreenOverlay() {
        overlayView?.let {
            try {
                overlayWindowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        overlayWindowManager = null
        try {
            val lp = window.attributes
            lp.screenBrightness = -1f // restaurar por defecto
            window.attributes = lp
        } catch (_: Exception) {}
        isScreenOff = false
        btnScreenOff.text = "Apagar pantalla (simulado)"
    }

    private lateinit var spDevices: Spinner
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var chkBackground: CheckBox
    private lateinit var btnScreenOff: Button
    private lateinit var btnDarkMode: Button
    private var isScreenOff = false
    private var overlayView: android.view.View? = null
    private var overlayWindowManager: android.view.WindowManager? = null

    private lateinit var inputManager: InputManager
    private val client = ControllerClient()
    private val state = ControllerState()
    private val lastSent = ControllerState()
    private var selectedDeviceId: Int? = null
    private var currentProfile = getProfileForDevice(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forzar modo oscuro por defecto
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)

        // Si el usuario cambió el modo, restaurar preferencia
        val prefs = getSharedPreferences("xvc", MODE_PRIVATE)
        val userPref = prefs.getInt("nightMode", -1)
        if (userPref != -1) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(userPref)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        inputManager = getSystemService(Activity.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)

        spDevices = findViewById(R.id.spDevices)
        etIp = findViewById(R.id.etIp)
        etPort = findViewById(R.id.etPort)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        chkBackground = findViewById(R.id.chkBackground)
        btnScreenOff = findViewById(R.id.btnScreenOff)
        btnDarkMode = findViewById(R.id.btnDarkMode)

        btnScreenOff.setOnClickListener {
            if (!isScreenOff) {
                // Crear overlay negro SYSTEM_ALERT_WINDOW para cubrir toda la pantalla
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Otorga permiso de superposición para simular pantalla apagada", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                    return@setOnClickListener
                }
                overlayView = android.view.View(this).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    alpha = 1f
                    setOnTouchListener { _, _ ->
                        // Quitar overlay y restaurar brillo al tocar cualquier parte
                        removeScreenOverlay()
                        true
                    }
                }
                val params = android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        android.view.WindowManager.LayoutParams.TYPE_PHONE,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                overlayWindowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                overlayWindowManager?.addView(overlayView, params)
                // Guardar brillo actual y poner al mínimo
                try {
                    val lp = window.attributes
                    lp.screenBrightness = 0.01f
                    window.attributes = lp
                } catch (_: Exception) {}
                isScreenOff = true
                btnScreenOff.text = "Encender pantalla"
            } else {
                removeScreenOverlay()
            }
        }

        btnDarkMode.setOnClickListener {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val newMode = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            else
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            // Guardar selección en SharedPreferences
            getSharedPreferences("xvc", MODE_PRIVATE).edit().putInt("nightMode", newMode).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode)
            recreate() // Recargar la actividad para aplicar el cambio de tema
        }

        etPort.filters = arrayOf(InputFilter.LengthFilter(5))

        refreshDevices()

        btnConnect.setOnClickListener {
            if (client.isConnected()) {
                client.disconnect()
                tvStatus.text = "Desconectado"
                btnConnect.text = "Conectar"
                stopService(Intent(this, InputService::class.java))
            } else {
                val ipTxt = etIp.text.toString().trim()
                val port = etPort.text.toString().toIntOrNull() ?: 39500
                if (ipTxt.isEmpty()) {
                    tvStatus.text = "Buscando servidor..."
                    thread { autoDiscoverAndFill(port, autoConnect = false) }
                    return@setOnClickListener
                }
                // Conexión en segundo plano
                btnConnect.isEnabled = false
                tvStatus.text = "Conectando a $ipTxt:$port..."
                thread {
                    val ok = client.connect(ipTxt, port)
                    runOnUiThread {
                        if (ok) {
                            // Persist for service auto-connect
                            getSharedPreferences("xvc", MODE_PRIVATE).edit().putString("host", ipTxt).putInt("port", port).apply()
                            tvStatus.text = "Conectado a $ipTxt:$port"
                            btnConnect.text = "Desconectar"
                            if (chkBackground.isChecked) startBgCapture(ipTxt, port)
                        } else {
                            val err = client.getLastError() ?: "desconocido"
                            tvStatus.text = "Error de conexión: $err"
                        }
                        btnConnect.isEnabled = true
                    }
                }
            }
        }

        // Si ya está conectado y activas el check, inicia el servicio con la conexión actual
        chkBackground.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && client.isConnected()) {
                val ipTxt = etIp.text.toString().trim()
                val port = etPort.text.toString().toIntOrNull() ?: 39500
                getSharedPreferences("xvc", MODE_PRIVATE).edit().putString("host", ipTxt).putInt("port", port).apply()
                startBgCapture(ipTxt, port)
            } else if (!isChecked) {
                try { stopService(Intent(this, InputService::class.java)) } catch (_: Exception) {}
            }
        }

        // Lanzar una búsqueda automática al abrir la app (no bloqueante)
        thread { autoDiscoverAndFill(port = etPort.text.toString().toIntOrNull() ?: 39500, autoConnect = false) }

        spDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, pos: Int, id: Long) {
                val item = spDevices.adapter.getItem(pos) as DeviceItem
                selectedDeviceId = item.id
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {
                selectedDeviceId = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inputManager.unregisterInputDeviceListener(this)
        client.disconnect()
    // Limpiar overlay si está activo
    removeScreenOverlay()
        // Mantener servicio si está habilitado el modo segundo plano
        if (!this::chkBackground.isInitialized || !chkBackground.isChecked) {
            try { stopService(Intent(this, InputService::class.java)) } catch (_: Exception) {}
        }
    }

    // Captura de teclas
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val target = selectedDeviceId
        if (target != null && event.deviceId == target) {
            val device = event.device
            currentProfile = getProfileForDevice(device)
            val handled = applyKeyToStateWithProfile(event, state, currentProfile)
            if (handled) sendIfChanged()
            return handled || super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    // Captura de ejes
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val sourceIsGamepad = (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        val target = selectedDeviceId
        if (sourceIsGamepad && target != null && event.deviceId == target) {
            val device = event.device
            currentProfile = getProfileForDevice(device)
            currentProfile.applyMotionToState(event, state)
            sendIfChanged()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun sendIfChanged() {
        if (state != lastSent) {
            client.sendState(state)
            lastSent.copyFrom(state)
        }
    }

    private fun ensureBackgroundPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100) } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        }
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }
                startActivity(i)
            }
        } catch (_: Exception) {}
    }

    private fun startBgCapture(host: String, port: Int) {
        // Primero asegúrate de tener permiso de superposición; si no, no cedas la conexión aún
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            tvStatus.text = "Otorga permiso de superposición para capturar en segundo plano"
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            return
        }
        ensureBackgroundPermissions()
    // Ceder primero la conexión del Activity para evitar solapamiento
    try { client.disconnect() } catch (_: Exception) {}
    val intent = Intent(this, InputService::class.java).setAction(InputService.ACTION_CONNECT)
            .putExtra("host", host).putExtra("port", port)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    tvStatus.text = "En segundo plano: $host:$port"
    }

    // Discovery: envía broadcast "XVC_DISCOVER" a 255.255.255.255:39501 y espera respuestas JSON {t:"xvc", ip, port, name}
    private fun autoDiscoverAndFill(port: Int, autoConnect: Boolean) {
        try {
            val offers = discoverServers(timeoutMs = 1500)
            if (offers.isNotEmpty()) {
                val first = offers.first()
                runOnUiThread {
                    etIp.setText(first.ip)
                    etPort.setText(first.port.toString())
                    tvStatus.text = "Servidor: ${first.ip}:${first.port} (${first.name})"
                }
                if (autoConnect && !client.isConnected()) {
                    // Conectar en segundo plano
                    thread {
                        val ok = client.connect(first.ip, first.port)
                        runOnUiThread {
                            if (ok) {
                                tvStatus.text = "Conectado a ${first.ip}:${first.port}"
                                btnConnect.text = "Desconectar"
                            } else {
                                tvStatus.text = "Error de conexión: ${client.getLastError() ?: "desconocido"}"
                            }
                        }
                    }
                }
            } else {
                runOnUiThread {
                    if (!client.isConnected() && etIp.text.isNullOrBlank())
                        tvStatus.text = "No se encontró servidor"
                }
            }
        } catch (e: Exception) {
            runOnUiThread { tvStatus.text = "Discovery error: ${e.message}" }
        }
    }

    private data class ServerOffer(val ip: String, val port: Int, val name: String)

    private fun discoverServers(timeoutMs: Int = 1500): List<ServerOffer> {
        val DISC_PORT = 39501
        val buf = "XVC_DISCOVER".toByteArray(Charset.forName("UTF-8"))
        val found = LinkedHashMap<String, ServerOffer>() // key: ip:port

        DatagramSocket(null).use { sock ->
            sock.reuseAddress = true
            sock.broadcast = true
            sock.soTimeout = 500
            sock.bind(InetSocketAddress(0))

            // Enviar broadcast global
            val baddr = InetAddress.getByName("255.255.255.255")
            val out = DatagramPacket(buf, buf.size, baddr, DISC_PORT)
            try { sock.send(out) } catch (_: Exception) {}

            val start = System.currentTimeMillis()
            val inBuf = ByteArray(1024)
            while (System.currentTimeMillis() - start < timeoutMs) {
                val pkt = DatagramPacket(inBuf, inBuf.size)
                try {
                    sock.receive(pkt)
                    val s = String(pkt.data, pkt.offset, pkt.length, Charsets.UTF_8)
                    val js = JSONObject(s)
                    if (js.optString("t") == "xvc") {
                        val ip = js.optString("ip", pkt.address.hostAddress)
                        val port = js.optInt("port", 39500)
                        val name = js.optString("name", "PC")
                        val key = "$ip:$port"
                        if (!found.containsKey(key)) {
                            found[key] = ServerOffer(ip, port, name)
                        }
                    }
                } catch (_: Exception) {
                    // timeout parcial o paquete no JSON, continuar
                }
            }
        }
        return found.values.toList()
    }

    private data class DeviceItem(val id: Int, val name: String) {
        override fun toString(): String = "#$id - $name"
    }

    private fun isGamepad(dev: InputDevice): Boolean {
        val sources = dev.sources
        val game = (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        return game && dev.isExternal
    }

    private fun refreshDevices() {
        val ids: IntArray = inputManager.inputDeviceIds
        val list = mutableListOf<DeviceItem>()
        for (devId in ids) {
            val d = inputManager.getInputDevice(devId) ?: continue
            if (isGamepad(d)) list.add(DeviceItem(d.id, d.name))
        }

        val adapter = ArrayAdapter<DeviceItem>(this, android.R.layout.simple_spinner_dropdown_item, list)
        spDevices.adapter = adapter

        if (list.isNotEmpty()) {
            spDevices.setSelection(0)
            val first = list[0]
            selectedDeviceId = first.id
        } else {
            selectedDeviceId = null
        }
    }

    // InputDevice listener
    override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshDevices()
    override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
}
