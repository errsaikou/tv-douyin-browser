package com.example.tvdouyin

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebView configuration utility.
 *
 * Key responsibilities:
 * 1. Set Desktop Chrome User-Agent to load the PC version of Douyin
 * 2. Inject anti-stutter JS script (blocks H.265/AV1 codecs) on every page load
 * 3. Enable DOM storage, cookies, and hardware acceleration
 */
object WebViewSetup {

    // Pretend to be a standard Windows Chrome desktop browser
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    private var injectScript: String? = null

    fun configure(webView: WebView, activity: Activity) {
        // Load the anti-stutter injection script from assets
        injectScript = try {
            activity.assets.open("inject.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // WebView settings
        webView.settings.apply {
            // Core features
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // Desktop rendering
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = DESKTOP_USER_AGENT

            // Media
            mediaPlaybackRequiresUserGesture = false

            // Security / network
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT

            // File access
            allowFileAccess = true
            allowContentAccess = true
        }

        // Enable hardware acceleration for smooth video playback
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // WebView client - handles page lifecycle and JS injection
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Early injection attempt: inject codec filter ASAP
                // May not always work depending on WebView version, but helps when it does
                injectScript?.let { script ->
                    view?.evaluateJavascript(script, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Guaranteed injection: ensures the codec filter is in place
                // Also catches SPA-style navigations within Douyin
                injectScript?.let { script ->
                    view?.evaluateJavascript(script, null)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Keep all navigation within WebView (don't open external browser)
                return false
            }
        }

        // Chrome client for video fullscreen support and other features
        webView.webChromeClient = object : WebChromeClient() {
            // Custom view for fullscreen video
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
            }
        }
    }
}
