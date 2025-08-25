package com.aragon.xvc.net

import com.aragon.xvc.input.ControllerState
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class ControllerClient {
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val seq = AtomicLong(0)
    @Volatile private var lastError: String? = null

    // Hilo de envío
    private val outQueue = LinkedBlockingQueue<String>()
    private var senderThread: Thread? = null
    private var keepAliveThread: Thread? = null

    fun getLastError(): String? = lastError

    @Synchronized
    fun connect(host: String, port: Int, timeoutMs: Int = 3000): Boolean {
        disconnect()
        lastError = null
        return try {
            val s = Socket()
            s.tcpNoDelay = true
            s.keepAlive = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            socket = s
            // Buffer pequeño para reducir latencia; TCP_NODELAY ya activado
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), 512)
            running.set(true)
            // Arranca hilo de envío
            senderThread = thread(start = true, isDaemon = true, name = "xvc-sender") {
                try {
                    while (running.get()) {
                        val msg = outQueue.take() // bloquea hasta que haya algo
                        val w = writer ?: continue
                        // Priorizar el último estado; si hubo acumulación, descarta backlog viejo
                        var toSend = msg
                        while (outQueue.isNotEmpty()) {
                            val next = outQueue.poll() ?: break
                            toSend = next
                        }
                        w.write(toSend)
                        w.write("\n")
                        w.flush()
                    }
                } catch (_: InterruptedException) {
                    // cierre
                } catch (_: Exception) {
                    // desconexión inesperada
                    disconnect()
                }
            }
            // Keepalive: enviar un ping cada 3s para evitar timeouts Wi-Fi/reposo
            keepAliveThread = thread(start = true, isDaemon = true, name = "xvc-keepalive") {
                try {
                    while (running.get()) {
                        val js = JSONObject().put("t", "ping").toString()
                        if (outQueue.size > 10) outQueue.clear()
                        outQueue.offer(js)
                        Thread.sleep(3000)
                    }
                } catch (_: InterruptedException) {
                } catch (_: Exception) {
                    disconnect()
                }
            }
            true
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            disconnect()
            false
        }
    }

    @Synchronized
    fun disconnect() {
        running.set(false)
        try { senderThread?.interrupt() } catch (_: Exception) {}
        senderThread = null
    try { keepAliveThread?.interrupt() } catch (_: Exception) {}
    keepAliveThread = null
        outQueue.clear()
        try { writer?.flush() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    fun isConnected(): Boolean = running.get() && socket?.isConnected == true && socket?.isClosed == false

    fun sendState(state: ControllerState) {
        if (!isConnected()) return
        // Construye JSON y encola (no I/O en hilo UI)
        try {
            val js = JSONObject()
                .put("t", "state")
                .put("seq", seq.incrementAndGet())
                .put("btn", state.btnMask)
                .put("lx", state.lx)
                .put("ly", state.ly)
                .put("rx", state.rx)
                .put("ry", state.ry)
                .put("lt", state.lt)
                .put("rt", state.rt)
            // Evita backlog: mantener solo el último
            outQueue.clear()
            outQueue.offer(js.toString())
        } catch (_: Exception) {
            disconnect()
        }
    }
}
