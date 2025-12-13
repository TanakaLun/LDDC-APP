package com.example.lldc.util

import android.content.Context
import android.content.SharedPreferences

object SharedPrefsUtil {
    private const val PREFS_NAME = "app_settings"
    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun read(key: String, defaultValue: Boolean): Boolean {
        return sharedPrefs?.getBoolean(key, defaultValue) ?: defaultValue
    }

    fun read(key: String, defaultValue: String?): String? {
        return sharedPrefs?.getString(key, defaultValue)
    }

    fun write(key: String, value: Boolean) {
        sharedPrefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun write(key: String, value: String?) {
        sharedPrefs?.edit()?.putString(key, value)?.apply()
    }
} 