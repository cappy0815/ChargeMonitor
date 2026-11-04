package de.cappy.ladeleistung

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Optionaler Geräteadministrator nur mit der Richtlinie "Bildschirm sperren".
 * Damit kann "Display aus" den Bildschirm sofort abschalten (wie die Seitentaste).
 * Ohne Aktivierung geht das Display nach dem normalen Bildschirm-Timeout aus.
 */
class LockAdminReceiver : DeviceAdminReceiver()

object Sleep {
    fun admin(ctx: Context) = ComponentName(ctx, LockAdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)

    fun canLockNow(ctx: Context): Boolean = dpm(ctx).isAdminActive(admin(ctx))

    fun requestAdmin(ctx: Context) {
        ctx.startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Erlaubt Charge Monitor nur, das Display sofort auszuschalten (wie die Seitentaste). " +
                    "Vor dem Deinstallieren hier wieder abschalten.")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun removeAdmin(ctx: Context) {
        try { dpm(ctx).removeActiveAdmin(admin(ctx)) } catch (_: Exception) {}
    }

    /** Schaltet sofort aus, wenn erlaubt. Rückgabe: true = Display ist aus. */
    fun lockNow(ctx: Context): Boolean =
        if (canLockNow(ctx)) try { dpm(ctx).lockNow(); true } catch (_: Exception) { false } else false
}
