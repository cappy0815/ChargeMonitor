package de.cappy.ladeleistung

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Vordergrund-Dienst für "Beim Einstecken automatisch öffnen".
 * ACTION_POWER_CONNECTED wird seit Android 8 nicht mehr an Manifest-Empfänger
 * geliefert, deshalb muss ein laufender Dienst den Empfänger dynamisch anmelden.
 * Das Öffnen der App aus dem Hintergrund erlaubt Android nur mit der
 * Berechtigung "Über anderen Apps einblenden" (SYSTEM_ALERT_WINDOW).
 */
class ChargeWatchService : Service() {

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val prefs = Prefs.get(c)
            // Neuer Ladevorgang bzw. abgesteckt -> "Display aus"-Pause aufheben
            prefs.unpause()
            if (i.action != Intent.ACTION_POWER_CONNECTED) return
            if (!prefs.autoStart) return
            if (!Settings.canDrawOverlays(c)) return
            c.startActivity(
                Intent(c, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_AUTOSTART, true)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lade-Überwachung", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Hält Charge Monitor bereit, um beim Einstecken zu starten"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val pause = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, ControlReceiver::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Charge Monitor bereit")
            .setContentText("Öffnet sich beim Anschließen des Ladekabels")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Automatik pausieren", pause).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(1, n)

        ContextCompat.registerReceiver(
            this, powerReceiver,
            IntentFilter(Intent.ACTION_POWER_CONNECTED).apply { addAction(Intent.ACTION_POWER_DISCONNECTED) },
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.get(this).watchActive) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "watch"
        const val ACTION_PAUSE = "de.cappy.ladeleistung.PAUSE"
        const val ACTION_RESUME = "de.cappy.ladeleistung.RESUME"

        /** Startet oder stoppt den Dienst passend zur Einstellung (inkl. Pause). */
        fun sync(ctx: Context) {
            val i = Intent(ctx, ChargeWatchService::class.java)
            if (Prefs.get(ctx).watchActive) {
                try { ctx.startForegroundService(i) } catch (_: Exception) {}
            } else ctx.stopService(i)   // beendet Dienst, Empfänger und Benachrichtigung
            AppShortcuts.update(ctx)
        }

        /** Automatik pausieren bzw. fortsetzen (Benachrichtigung, App-Symbol, App). */
        fun setPaused(ctx: Context, paused: Boolean) {
            Prefs.get(ctx).autoPaused = paused
            sync(ctx)
        }

        fun ignoresBatteryOptimization(ctx: Context): Boolean =
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
}

/** Knopf "Automatik pausieren" in der Benachrichtigung (nur app-intern erreichbar). */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            ChargeWatchService.ACTION_PAUSE -> ChargeWatchService.setPaused(c, true)
            ChargeWatchService.ACTION_RESUME -> ChargeWatchService.setPaused(c, false)
        }
    }
}

/** Startet den Dienst nach Neustart des Handys und nach App-Updates neu (nicht, wenn pausiert). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ChargeWatchService.sync(c)
        }
    }
}
