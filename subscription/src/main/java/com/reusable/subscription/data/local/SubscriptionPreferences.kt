package com.reusable.subscription.data.local

import android.content.Context
import android.content.SharedPreferences

object SubscriptionPreferences {
    private const val PREF_NAME = "SharedPref"

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun putString(context: Context, key: String, value: String?) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String): String {
        return prefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun getNullableString(context: Context, key: String, defaultValue: String?): String? {
        return prefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        return prefs(context).getBoolean(key, defaultValue)
    }

    fun putInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return prefs(context).getInt(key, defaultValue)
    }
}
