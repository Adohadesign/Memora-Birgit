package de.adohadesign.memorasolo.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Checks only documents marked ready by Briefstudio. No private text or titles in notifications.
 * Android may defer periodic work to preserve battery. Does not write diary records while locked.
 */
class BriefstudioNotificationWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BriefstudioTransfer.isConnected(applicationContext)) return Result.success()
        return try {
            val documents = BriefstudioTransfer.fetchSaved(applicationContext)
            val dao = DiaryDatabase.get(applicationContext).diaryDao()
            val pending = documents.count { dao.importedCount(it.id) == 0 }
            if (pending > 0) notifyPending(pending)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun notifyPending(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("briefstudio_updates", "Briefstudio-Übertragung", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = android.content.Intent(applicationContext, de.adohadesign.memorasolo.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(applicationContext, 4102, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "briefstudio_updates")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Neue Inhalte im Briefstudio")
            .setContentText("Es stehen $count neue Inhalte für dein Tagebuch bereit.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(4102, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BriefstudioNotificationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "briefstudio_pending_check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
