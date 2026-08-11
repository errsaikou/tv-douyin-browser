package com.example.tvdouyin

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.tvdouyin.server.RemoteControlServer
import com.example.tvdouyin.server.WebSocketHandler
import com.example.tvdouyin.util.NetworkUtils
import com.example.tvdouyin.util.QRCodeGenerator

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var cursorOverlay: CursorOverlayView
    private lateinit var qrContainer: FrameLayout
    private lateinit var qrCodeView: ImageView
    private lateinit var qrInfoText: TextView

    private var httpServer: RemoteControlServer? = null
    private var wsServer: WebSocketHandler? = null

    private var lastBackPressedTime: Long = 0
    private var isQrVisible = true

    companion object {
        private const val HTTP_PORT = 8888
        private const val WS_PORT = 8889
        private const val HOME_URL = "file:///android_asset/home.html"
        private const val BACK_PRESS_INTERVAL = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive mode - hide system bars
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_main)

        // Initialize views
        webView = findViewById(R.id.webView)
        cursorOverlay = findViewById(R.id.cursorOverlay)
        qrContainer = findViewById(R.id.qrContainer)
        qrCodeView = findViewById(R.id.qrCodeView)
        qrInfoText = findViewById(R.id.qrInfoText)

        // Configure WebView with desktop UA, JS injection, etc.
        WebViewSetup.configure(webView, this)

        // Link cursor overlay to WebView for click simulation
        cursorOverlay.targetWebView = webView

        // Enable persistent cookies (stay logged in across app restarts)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Start HTTP + WebSocket servers for phone remote control
        startServers()

        // Load the local homepage with video site shortcuts
        webView.loadUrl(HOME_URL)
    }

    // ===================================================================
    // Server Management (phone remote control)
    // ===================================================================

    private fun startServers() {
        val ip = NetworkUtils.getWifiIpAddress(this)
        if (ip == null) {
            Toast.makeText(this, "无法获取 WiFi IP，手机遥控不可用", Toast.LENGTH_LONG).show()
            qrContainer.visibility = View.GONE
            return
        }

        val controlUrl = "http://$ip:$HTTP_PORT"

        // Generate QR code for phone scanning
        val qrBitmap = QRCodeGenerator.generate(controlUrl, 280)
        if (qrBitmap != null) {
            qrCodeView.setImageBitmap(qrBitmap)
            qrInfoText.text = "手机扫码遥控\n$controlUrl"
            qrContainer.visibility = View.VISIBLE
        }

        // Start HTTP server - serves the phone H5 control page
        try {
            httpServer = RemoteControlServer(HTTP_PORT, assets, ip, WS_PORT)
            httpServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "HTTP 服务启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        // Start WebSocket server - handles real-time phone commands
        try {
            wsServer = WebSocketHandler(WS_PORT, object : WebSocketHandler.CommandListener {
                override fun onCursorMove(dx: Float, dy: Float) {
                    runOnUiThread { cursorOverlay.moveCursor(dx, dy) }
                }

                override fun onCursorClick() {
                    runOnUiThread { cursorOverlay.performClickAtCursor() }
                }

                override fun onTextInput(text: String) {
                    runOnUiThread { injectSearchText(text) }
                }

                override fun onAction(action: String) {
                    runOnUiThread { handleRemoteAction(action) }
                }

                override fun onScroll(dy: Float) {
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.scrollBy(0, ${dy.toInt()});", null
                        )
                    }
                }

                override fun onNavigate(url: String) {
                    runOnUiThread { navigateToUrl(url) }
                }
            })
            wsServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "WebSocket 服务启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ===================================================================
    // Search Text Injection (phone types → TV searches)
    // ===================================================================

    /**
     * Inject text into Douyin's search box and trigger search.
     * Uses React-compatible input value setter to ensure the framework detects the change.
     */
    private fun injectSearchText(text: String) {
        val escapedText = text.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function() {
                // Step 1: Try to open the search panel by clicking search icon
                var searchBtn = document.querySelector('[data-e2e="searchbar-button"]')
                    || document.querySelector('.search-icon')
                    || document.querySelector('button[aria-label*="搜索"]');
                if (searchBtn) searchBtn.click();

                // Step 2: After search panel opens, fill in the search text
                setTimeout(function() {
                    var input = document.querySelector('input[data-e2e="searchbar-input"]')
                        || document.querySelector('input[type="search"]')
                        || document.querySelector('input[placeholder*="搜索"]')
                        || document.querySelector('.search-input input')
                        || document.querySelector('input[type="text"]');
                    if (input) {
                        // Use native setter to bypass React's synthetic event system
                        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                            window.HTMLInputElement.prototype, 'value'
                        ).set;
                        nativeInputValueSetter.call(input, '$escapedText');
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));

                        // Step 3: Trigger search by simulating Enter key
                        setTimeout(function() {
                            input.dispatchEvent(new KeyboardEvent('keydown', {
                                key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true
                            }));
                            input.dispatchEvent(new KeyboardEvent('keypress', {
                                key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true
                            }));
                            input.dispatchEvent(new KeyboardEvent('keyup', {
                                key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true
                            }));
                        }, 200);
                    }
                }, 500);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ===================================================================
    // Remote Action Handling (phone quick buttons → TV actions)
    // ===================================================================

    private fun handleRemoteAction(action: String) {
        when (action) {
            "next" -> dispatchWebKeyEvent("ArrowDown", 40)
            "prev" -> dispatchWebKeyEvent("ArrowUp", 38)
            "pause" -> dispatchWebKeyEvent(" ", 32, "Space")
            "back" -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    onBackPressed()
                }
            }
            "home" -> webView.loadUrl(HOME_URL)
            "toggleQr" -> toggleQrCode()
            "fullscreen" -> {
                webView.evaluateJavascript("""
                    (function(){
                        var video = document.querySelector('video');
                        if (video) {
                            if (video.requestFullscreen) video.requestFullscreen();
                            else if (video.webkitRequestFullscreen) video.webkitRequestFullscreen();
                        }
                    })();
                """.trimIndent(), null)
            }
        }
    }

    /**
     * Navigate the WebView to a URL (sent from phone remote).
     * Automatically prepends https:// if missing.
     */
    private fun navigateToUrl(url: String) {
        val finalUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains(".") -> "https://$url"
            else -> "https://www.baidu.com/s?wd=$url" // Treat as search query
        }
        webView.loadUrl(finalUrl)
    }

    /**
     * Dispatch a keyboard event to the Douyin web page
     */
    private fun dispatchWebKeyEvent(key: String, keyCode: Int, code: String? = null) {
        val codeStr = code ?: key
        webView.evaluateJavascript("""
            document.dispatchEvent(new KeyboardEvent('keydown', {
                key: '$key', code: '$codeStr', keyCode: $keyCode, which: $keyCode, bubbles: true
            }));
        """.trimIndent(), null)
    }

    private fun toggleQrCode() {
        isQrVisible = !isQrVisible
        qrContainer.visibility = if (isQrVisible) View.VISIBLE else View.GONE
    }

    // ===================================================================
    // TV Remote Control Key Handling
    // ===================================================================

    /**
     * Check if we are currently on the local homepage.
     */
    private fun isOnHomePage(): Boolean {
        val url = webView.url ?: ""
        return url.startsWith("file:///android_asset/home")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // D-pad: On homepage let WebView handle focus naturally;
            // On video sites, dispatch as keyboard events for video control
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isOnHomePage()) {
                    // Let WebView handle focus navigation between site cards
                    return super.onKeyDown(keyCode, event)
                }
                // On video sites: forward arrow keys as web keyboard events
                val keyMap = mapOf(
                    KeyEvent.KEYCODE_DPAD_DOWN to Pair("ArrowDown", 40),
                    KeyEvent.KEYCODE_DPAD_UP to Pair("ArrowUp", 38),
                    KeyEvent.KEYCODE_DPAD_LEFT to Pair("ArrowLeft", 37),
                    KeyEvent.KEYCODE_DPAD_RIGHT to Pair("ArrowRight", 39)
                )
                keyMap[keyCode]?.let { (key, code) -> dispatchWebKeyEvent(key, code) }
                return true
            }

            // Center/Enter: On homepage → click focused card; On video → pause/play
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (isOnHomePage()) {
                    return super.onKeyDown(keyCode, event)
                }
                dispatchWebKeyEvent(" ", 32, "Space")
                return true
            }

            // Menu key → Toggle QR code visibility
            KeyEvent.KEYCODE_MENU -> {
                toggleQrCode()
                return true
            }

            // Home key on some remotes → Go back to homepage
            KeyEvent.KEYCODE_HOME -> {
                webView.loadUrl(HOME_URL)
                return true
            }

            // Back key → WebView back or double-press to exit
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressedTime < BACK_PRESS_INTERVAL) {
                    finish()
                } else {
                    lastBackPressedTime = now
                    Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ===================================================================
    // Lifecycle
    // ===================================================================

    override fun onDestroy() {
        super.onDestroy()
        // Flush cookies to persistent storage
        CookieManager.getInstance().flush()
        // Stop servers
        httpServer?.stop()
        try { wsServer?.stop(1000) } catch (_: Exception) {}
        // Destroy WebView
        webView.destroy()
    }
}
