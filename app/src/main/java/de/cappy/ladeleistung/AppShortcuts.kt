package de.cappy.ladeleistung

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast

/**
 * Menü beim langen Tippen auf das App-Symbol.
 * Dynamisch, damit der Eintrag je nach Zustand "pausieren" oder "fortsetzen" heißt.
 * Nur sichtbar, wenn "Beim Einstecken automatisch öffnen" eingeschaltet ist.
 */
object AppShortcuts {
    private const val ID = "automatik"

    fun update(ctx: Context) {
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
        val prefs = Prefs.get(ctx)
        try {
            if (!prefs.autoStart) {
                sm.removeDynamicShortcuts(listOf(ID))
                return
            }
            val paused = prefs.autoPaused
            val label = if (paused) "Automatik fortsetzen" else "Automatik pausieren"
            val info = ShortcutInfo.Builder(ctx, ID)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(Icon.createWithResource(ctx, R.drawable.ic_shortcut))
                .setIntent(
                    Intent(ctx, ShortcutActivity::class.java)
                        .setAction(if (paused) ChargeWatchService.ACTION_RESUME else ChargeWatchService.ACTION_PAUSE)
                )
                .build()
            sm.dynamicShortcuts = listOf(info)
        } catch (_: Exception) { }
    }
}

/** Unsichtbare Activity für den Kurzbefehl: schaltet um, zeigt kurz eine Meldung, schließt sich. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ChargeWatchService.ACTION_PAUSE -> {
                ChargeWatchService.setPaused(this, true)
                Toast.makeText(this, "Charge Monitor: Automatik pausiert", Toast.LENGTH_SHORT).show()
            }
            ChargeWatchService.ACTION_RESUME -> {
                ChargeWatchService.setPaused(this, false)
                Toast.makeText(this, "Charge Monitor: Automatik läuft wieder", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
