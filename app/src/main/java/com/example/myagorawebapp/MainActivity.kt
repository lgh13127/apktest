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
    // 变量名保持为 webView，但查找使用布局中的 id: R.id.webview （小写 v）
    private lateinit var webView: WebView

    // 保存来自 WebView 的待处理权限请求（如果需要先请求系统权限）
    private var pendingPermissionRequest: PermissionRequest? = null

    // Activity Result API：请求 RECORD_AUDIO 权限
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 授权后，允许刚才保存的网页权限请求并启动前台麦克风服务
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
            // 拒绝时，拒绝网页 pending 请求并清理
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

        // 注意：布局 id 是 "@+id/webview" （小写 v），这里必须一致
        webView = findViewById(R.id.webview)

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
                    // 检测是否请求麦克风权限
                    val needsAudio = request.resources.any {
                        it.contains("AUDIO_CAPTURE") || it.contains("android.webkit.resource.AUDIO_CAPTURE")
                    }
                    if (needsAudio) {
                        // 若系统已授予 RECORD_AUDIO，则直接授予网页请求并启动前台服务
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
                            // 保存 pending request，向系统请求 RECORD_AUDIO（授权回调会 grant）
                            pendingPermissionRequest = request
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        // 非麦克风权限：直接授权（请在生产环境加 origin 检查）
                        try {
                            request.grant(request.resources)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        // 加载本地页面（assets/index.html）
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun startMicForegroundServiceIfNeeded() {
        // 启动前台服务以保活麦克风。确保项目中有 MicForegroundService.kt（同包名）
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