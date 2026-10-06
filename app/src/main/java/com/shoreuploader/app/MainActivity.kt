package com.shoreuploader.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val clipData = result.data?.clipData
            val data = result.data?.data
            val results: Array<Uri> = when {
                clipData != null -> {
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                data != null -> arrayOf(data)
                else -> emptyArray()
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark theme status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0xFF060708.toInt()
        window.navigationBarColor = 0xFF060708.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mediaPlaybackRequiresUserGesture = false

        webView.setBackgroundColor(0xFF060708.toInt())
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Bridge
        webView.addJavascriptInterface(WebAppInterface(this, webView), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {}

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "video/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "video/mp4", "video/quicktime", "video/x-matroska", "video/webm"))
                }

                try {
                    filePickerLauncher.launch(Intent.createChooser(intent, "Chọn video cần tối ưu"))
                } catch (e: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }
}
