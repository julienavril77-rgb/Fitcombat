package fr.combatfit.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTimerByUser()
            ACTION_START -> startTimer(intent.getIntExtra(EXTRA_SECONDS, 120))
            else -> if (prefs.getBoolean("running", false)) {
                endAt = prefs.getLong("endAt", 0L)
                if (endAt > System.currentTimeMillis()) { startForeground(NOTIFICATION_ID, runningNotification()); acquireWakeLock() ; scheduleTick() }
                else finishTimer()
            } else stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(seconds: Int) {
        val safeSeconds = seconds.coerceIn(1, 7200)
        endAt = System.currentTimeMillis() + safeSeconds * 1000L
        prefs.edit().putBoolean("running", true).putLong("endAt", endAt).putInt("remaining", safeSeconds).apply()
        startForeground(NOTIFICATION_ID, runningNotification())
        acquireWakeLock()
        handler.removeCallbacks(tickRunnable)
        scheduleTick()
    }

    private fun runningNotification(): Notification {
        val stopIntent = PendingIntent.getService(this, 10, Intent(this, RestTimerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CombatFit · Repos en cours")
            .setContentText("Temps restant")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopIntent)
        if (Build.VERSION.SDK_INT >= 24) builder.setWhen(endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        return builder.build()
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val remaining = ((endAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
            prefs.edit().putInt("remaining", remaining).apply()
            if (remaining <= 0) finishTimer() else scheduleTick()
        }
    }

    private fun scheduleTick() { handler.removeCallbacks(tickRunnable); handler.postDelayed(tickRunnable, 1000L) }

    private fun finishTimer() {
        handler.removeCallbacks(tickRunnable)
        prefs.edit().putBoolean("running", false).putLong("endAt", 0L).putInt("remaining", 0).apply()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val done = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CombatFit · Repos terminé")
            .setContentText("Tu peux reprendre ton entraînement.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_NOTIFICATION_ID, done)
        stopSelf()
    }

    private fun stopTimerByUser() {
        val remaining = ((endAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
        prefs.edit().putBoolean("running", false).putLong("endAt", 0L).putInt("remaining", remaining).apply()
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CombatFit:RestTimer")
        wakeLock?.acquire(2 * 60 * 60 * 1000L)
    }
    private fun releaseWakeLock() { try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}; wakeLock = null }

    override fun onDestroy() { handler.removeCallbacks(tickRunnable); releaseWakeLock(); super.onDestroy() }

    companion object {
        const val PREFS = "combatfit_native_timer"
        const val CHANNEL_RUNNING = "combatfit_timer_running"
        const val CHANNEL_DONE = "combatfit_timer_done"
        const val ACTION_START = "fr.combatfit.app.START_TIMER"
        const val ACTION_STOP = "fr.combatfit.app.STOP_TIMER"
        const val EXTRA_SECONDS = "seconds"
        private const val NOTIFICATION_ID = 4101
        private const val DONE_NOTIFICATION_ID = 4102
    }
}
