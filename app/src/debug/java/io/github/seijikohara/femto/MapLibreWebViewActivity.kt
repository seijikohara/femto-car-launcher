package io.github.seijikohara.femto

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * Debug-only proof-of-concept for the WebView map backend (Stage 1/2 of the staged
 * fallback). Renders the same MapLibre GL JS page the production [WebMapView] uses
 * (app/src/main/assets/web/map.html), served via [WebViewAssetLoader] from the
 * real https origin appassets.androidplatform.net so the tile-processing Web Worker
 * can fetch cross-origin tiles. The JS health bridge (AndroidMapBridge) reports
 * onMapReady / onWebGlFailed; here it just logs.
 *
 * To force Software WebGL (Stage 2) on the emulator (debuggable only):
 *   adb -e shell 'echo "_ --use-gl=angle --use-angle=swiftshader" > /data/local/tmp/webview-command-line'
 * then relaunch:
 *   adb shell am start -n io.github.seijikohara.femto/.MapLibreWebViewActivity
 */
class MapLibreWebViewActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        val assetLoader =
            WebViewAssetLoader
                .Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                .build()
        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(WebViewMapBridge(), "AndroidMapBridge")
                        webViewClient =
                            object : WebViewClientCompat() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                            }
                        // Surface MapLibre GL JS console output to logcat for diagnosis.
                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    Log.i(
                                        "MapWebView",
                                        "${message.message()} @${message.sourceId()}:${message.lineNumber()}",
                                    )
                                    return true
                                }
                            }
                        loadUrl("https://appassets.androidplatform.net/assets/web/map.html")
                    }
                },
            )
        }
    }

    private class WebViewMapBridge {
        @JavascriptInterface
        fun onMapReady() = Log.i("MapWebView", "bridge.onMapReady (WebGL rendering)")

        @JavascriptInterface
        fun onWebGlFailed(reason: String) = Log.w("MapWebView", "bridge.onWebGlFailed: $reason")
    }
}
