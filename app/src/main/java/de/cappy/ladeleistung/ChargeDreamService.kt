package de.cappy.ladeleistung

import android.service.dreams.DreamService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Android-Bildschirmschoner ("Daydream"). Startet automatisch beim Laden,
 * wenn er in den System-Einstellungen ausgewählt ist.
 * DreamService ist kein LifecycleOwner, deshalb wird der Lebenszyklus für
 * Compose hier von Hand nachgebildet.
 */
class ChargeDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Interaktiv, damit der Knopf "Display aus" bedienbar ist.
        // Antippen außerhalb weckt das Handy wie gewohnt (Sperrbildschirm).
        isInteractive = true
        isFullscreen = true
        val prefs = Prefs.get(this)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ChargeDreamService)
            setViewTreeSavedStateRegistryOwner(this@ChargeDreamService)
            setContent {
                val live = rememberLive(prefs)
                StandbyDisplay(
                    live, prefs,
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { wakeUp() },
                    onBrightness = { b -> window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = b } } },
                    onDisplayOff = {
                        prefs.pause(live.reading.levelPct)
                        Sleep.lockNow(this@ChargeDreamService)
                        finish()   // Android darf danach normal schlafen gehen
                    }
                )
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // Für diesen Ladevorgang abgeschaltet -> sofort beenden, Handy schläft normal
        if (Prefs.get(this).paused) { finish(); return }
        AppState.standbySince.longValue = System.currentTimeMillis()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
