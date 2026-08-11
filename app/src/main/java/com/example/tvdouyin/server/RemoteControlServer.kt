package com.example.tvdouyin.server

import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD

/**
 * Lightweight HTTP server that serves the phone remote control H5 page.
 *
 * When a phone scans the QR code and opens the URL, this server responds with
 * the HTML/CSS/JS files from the app's assets/remote/ directory.
 * The WebSocket address placeholder ({{WS_HOST}}) in the JS file is dynamically
 * replaced with the actual TV IP and WebSocket port.
 */
class RemoteControlServer(
    port: Int,
    private val assetManager: AssetManager,
    private val serverIp: String,
    private val wsPort: Int
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"

        // Map URL paths to asset files
        val filePath = when {
            uri == "/" || uri == "/index.html" -> "remote/index.html"
            uri == "/style.css" -> "remote/style.css"
            uri == "/remote.js" -> "remote/remote.js"
            uri.startsWith("/") && !uri.contains("..") -> "remote${uri}"
            else -> null
        }

        if (filePath != null) {
            return serveAssetFile(filePath)
        }

        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "404 Not Found"
        )
    }

    /**
     * Read a file from assets and serve it, replacing the WebSocket host placeholder.
     */
    private fun serveAssetFile(path: String): Response {
        return try {
            var content = assetManager.open(path).bufferedReader().use { it.readText() }

            // Replace the WebSocket address placeholder with actual values
            content = content.replace("{{WS_HOST}}", "$serverIp:$wsPort")

            val mimeType = when {
                path.endsWith(".html") -> "text/html; charset=utf-8"
                path.endsWith(".css") -> "text/css; charset=utf-8"
                path.endsWith(".js") -> "application/javascript; charset=utf-8"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".svg") -> "image/svg+xml"
                path.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }

            val response = newFixedLengthResponse(Response.Status.OK, mimeType, content)
            // Allow cross-origin requests (phone browser → TV server)
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "File not found: $path"
            )
        }
    }
}
