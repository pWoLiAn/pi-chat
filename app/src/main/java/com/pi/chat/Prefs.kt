package com.pi.chat

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "pi_chat_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_URL = "ws://10.0.2.2:8080" // Android emulator → host

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getServerUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun setServerUrl(ctx: Context, url: String) =
        prefs(ctx).edit().putString(KEY_SERVER_URL, url).apply()
}
