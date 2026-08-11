package com.example.tvdouyin.server

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

/**
 * WebSocket server for real-time phone ↔ TV communication.
 *
 * Receives JSON messages from the phone H5 control page and dispatches
 * them to the CommandListener (implemented by MainActivity).
 *
 * Message protocol:
 * - { "type": "move",   "dx": float, "dy": float }  → Move cursor
 * - { "type": "click"  }                             → Click at cursor position
 * - { "type": "input",  "text": string }             → Send text to TV search
 * - { "type": "scroll", "dy": float }                → Scroll page
 * - { "type": "action", "action": string }           → Quick action (next/prev/pause/back)
 */
class WebSocketHandler(
    port: Int,
    private val listener: CommandListener
) : WebSocketServer(InetSocketAddress(port)) {

    interface CommandListener {
        fun onCursorMove(dx: Float, dy: Float)
        fun onCursorClick()
        fun onTextInput(text: String)
        fun onAction(action: String)
        fun onScroll(dy: Float)
        fun onNavigate(url: String)
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        // Phone connected - could send a confirmation message back
        conn?.send("""{"type":"connected","message":"已连接到电视"}""")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        // Phone disconnected
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message == null) return

        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")

            when (type) {
                "move" -> {
                    val dx = json.optDouble("dx", 0.0).toFloat()
                    val dy = json.optDouble("dy", 0.0).toFloat()
                    listener.onCursorMove(dx, dy)
                }

                "click" -> {
                    listener.onCursorClick()
                }

                "input" -> {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) {
                        listener.onTextInput(text)
                    }
                }

                "scroll" -> {
                    val dy = json.optDouble("dy", 0.0).toFloat()
                    listener.onScroll(dy)
                }

                "action" -> {
                    val action = json.optString("action", "")
                    if (action.isNotEmpty()) {
                        listener.onAction(action)
                    }
                }

                "navigate" -> {
                    val url = json.optString("url", "")
                    if (url.isNotEmpty()) {
                        listener.onNavigate(url)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        ex?.printStackTrace()
    }

    override fun onStart() {
        // WebSocket server is now listening
        connectionLostTimeout = 60 // Ping clients every 60 seconds
    }
}
