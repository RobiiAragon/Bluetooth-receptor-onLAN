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
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            running.set(true)
            // Arranca hilo de envío
            senderThread = thread(start = true, isDaemon = true, name = "xvc-sender") {
                try {
                    while (running.get()) {
                        val msg = outQueue.take() // bloquea hasta que haya algo
                        val w = writer ?: continue
                        w.write(msg)
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
            // Evita crecer sin límite manteniendo solo el último si hay backlog
            if (outQueue.size > 5) outQueue.clear()
            outQueue.offer(js.toString())
        } catch (_: Exception) {
            disconnect()
        }
    }
}
