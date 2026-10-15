package dev.garen.deviceboost

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.util.Locale

/** Reads/writes the three system animation scales (same as Developer options). */
object AnimSettings {
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    private val KEYS = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE
    )

    fun hasPermission(c: Context): Boolean =
        c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun read(c: Context): Triple<Float, Float, Float> {
        val r = c.contentResolver
        return Triple(
            Settings.Global.getFloat(r, KEYS[0], 1f),
            Settings.Global.getFloat(r, KEYS[1], 1f),
            Settings.Global.getFloat(r, KEYS[2], 1f)
        )
    }

    /** Sets window, transition and animator scale to [v]. Returns false if not permitted. */
    fun setAll(c: Context, v: Float): Boolean {
        val s = String.format(Locale.US, "%.2f", v)
        return try {
            KEYS.forEach { Settings.Global.putString(c.contentResolver, it, s) }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun format(v: Float): String = String.format(Locale.US, "%.2fx", v)
}
