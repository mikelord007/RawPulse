package com.rawpulse.hr.data

import android.content.Context

/**
 * Thin wrapper over SharedPreferences for user settings.
 * Max HR drives the zone colours; if not set explicitly it is estimated from age (220 - age).
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rawpulse", Context.MODE_PRIVATE)

    var age: Int
        get() = prefs.getInt(KEY_AGE, DEFAULT_AGE)
        set(value) = prefs.edit().putInt(KEY_AGE, value.coerceIn(10, 100)).apply()

    /** Explicit max HR override. 0 means "derive from age". */
    var maxHrOverride: Int
        get() = prefs.getInt(KEY_MAX_HR, 0)
        set(value) = prefs.edit().putInt(KEY_MAX_HR, value.coerceIn(0, 240)).apply()

    var preferredDeviceAddress: String?
        get() = prefs.getString(KEY_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    /** The max HR actually used for zone maths. */
    fun effectiveMaxHr(): Int {
        val override = maxHrOverride
        return if (override > 0) override else (220 - age).coerceIn(120, 220)
    }

    companion object {
        private const val KEY_AGE = "age"
        private const val KEY_MAX_HR = "max_hr"
        private const val KEY_DEVICE = "device_address"
        private const val KEY_DEMO = "demo_mode"
        private const val DEFAULT_AGE = 30
    }
}
