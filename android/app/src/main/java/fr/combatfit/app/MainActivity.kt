package fr.combatfit.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("file:///android_asset/")) return false
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("if(typeof syncNativeTimer==='function')syncNativeTimer();", null)
            }
        }
        webView.addJavascriptInterface(TimerBridge(this), "CombatFitNative")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.postDelayed({
            webView.evaluateJavascript("if(typeof syncNativeTimer==='function')syncNativeTimer();", null)
        }, 250)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(RestTimerService.CHANNEL_RUNNING, "Minuteur en cours", NotificationManager.IMPORTANCE_LOW).apply { description = "Compte à rebours de repos CombatFit" })
            manager.createNotificationChannel(NotificationChannel(RestTimerService.CHANNEL_DONE, "Fin du repos", NotificationManager.IMPORTANCE_HIGH).apply { description = "Alerte lorsque le temps de repos est terminé" })
        }
    }
}

class TimerBridge(private val context: Context) {
    @JavascriptInterface fun startTimer(seconds: Int) {
        val intent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_START
            putExtra(RestTimerService.EXTRA_SECONDS, seconds.coerceIn(1, 7200))
        }
        ContextCompat.startForegroundService(context, intent)
    }
    @JavascriptInterface fun stopTimer() {
        context.startService(Intent(context, RestTimerService::class.java).setAction(RestTimerService.ACTION_STOP))
    }
    @JavascriptInterface fun isTimerRunning(): Boolean = context.getSharedPreferences(RestTimerService.PREFS, Context.MODE_PRIVATE).getBoolean("running", false)
    @JavascriptInterface fun getEndAt(): Long = context.getSharedPreferences(RestTimerService.PREFS, Context.MODE_PRIVATE).getLong("endAt", 0L)
    @JavascriptInterface fun getRemainingSeconds(): Int = context.getSharedPreferences(RestTimerService.PREFS, Context.MODE_PRIVATE).getInt("remaining", 0)
}
