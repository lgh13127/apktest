package com.example.myagorawebapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    // 当页面请求麦克风权限但系统权限尚未授予时，先保存该请求，待用户授权后再 grant
    private var pendingPermissionRequest: PermissionRequest? = null

    // 动态请求权限（麦克风）
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 如果用户授予了系统权限，允许原始的 Web permission 请求并启动前台保活服务
        if (isGranted) {
            pendingPermissionRequest?.let { req ->
                try {
                    req.grant(req.resources)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                pendingPermissionRequest = null
            }
            startMicForegroundServiceIfNeeded()
        } else {
            // 权限被拒绝：清理 pending 请求
            try {
                pendingPermissionRequest?.deny()
            } catch (e: Exception) {
                // ignore
            }
            pendingPermissionRequest = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 注意：布局里 WebView 的 id 为 "@+id/webView"
        webView = findViewById(R.id.webView)

        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.setSupportMultipleWindows(true)
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needsAudio = request.resources.any {
                        it.contains("AUDIO_CAPTURE") || it.contains("android.webkit.resource.AUDIO_CAPTURE")
                    }
                    if (needsAudio) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                request.grant(request.resources)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            startMicForegroundServiceIfNeeded()
                        } else {
                            pendingPermissionRequest = request
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        try {
                            request.grant(request.resources)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        // 加载本地页面（index.html 放在 assets 根目录）
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun startMicForegroundServiceIfNeeded() {
        try {
            val intent = Intent(this, MicForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}