package com.venice.rat.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object Paths {
    private val map = ConcurrentHashMap<Int, String>()
    private val c = AtomicInteger(0)

    fun put(p: String): Int = c.incrementAndGet().also { map[it] = p }
    fun get(k: Int): String? = map[k]
}
