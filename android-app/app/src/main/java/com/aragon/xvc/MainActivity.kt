package com.aragon.xvc

import android.app.Activity
import android.hardware.input.InputManager
import android.os.Bundle
import android.text.InputFilter
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.aragon.xvc.input.ControllerState
import com.aragon.xvc.input.applyKeyToState
import com.aragon.xvc.input.applyMotionToState
import com.aragon.xvc.net.ControllerClient
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.Charset
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), InputManager.InputDeviceListener {

    private lateinit var spDevices: Spinner
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView

    private lateinit var inputManager: InputManager
    private val client = ControllerClient()
    private val state = ControllerState()
    private val lastSent = ControllerState()
    private var selectedDeviceId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        inputManager = getSystemService(Activity.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)

        spDevices = findViewById(R.id.spDevices)
        etIp = findViewById(R.id.etIp)
        etPort = findViewById(R.id.etPort)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)

        etPort.filters = arrayOf(InputFilter.LengthFilter(5))

        refreshDevices()

        btnConnect.setOnClickListener {
            if (client.isConnected()) {
                client.disconnect()
                tvStatus.text = "Desconectado"
                btnConnect.text = "Conectar"
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
                            tvStatus.text = "Conectado a $ipTxt:$port"
                            btnConnect.text = "Desconectar"
                        } else {
                            val err = client.getLastError() ?: "desconocido"
                            tvStatus.text = "Error de conexión: $err"
                        }
                        btnConnect.isEnabled = true
                    }
                }
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
    }

    // Captura de teclas
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val target = selectedDeviceId
        if (target != null && event.deviceId == target) {
            val handled = applyKeyToState(event, state)
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
            applyMotionToState(event, state)
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
