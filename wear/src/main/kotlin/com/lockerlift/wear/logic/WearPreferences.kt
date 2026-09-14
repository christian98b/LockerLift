package com.lockerlift.wear.logic

import android.content.Context

object WearPreferences {
    private const val PREFS_NAME = "lockerlift_wear_preferences"
    private const val KEY_DEFAULT_REST_SECONDS = "default_rest_seconds"

    fun getDefaultRestSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DEFAULT_REST_SECONDS, WearWorkoutLogic.DEFAULT_REST_DURATION_SECONDS)
    }

    fun setDefaultRestSeconds(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(WearWorkoutLogic.MIN_REST_SECONDS, WearWorkoutLogic.MAX_REST_SECONDS)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DEFAULT_REST_SECONDS, clamped).apply()
    }
}
