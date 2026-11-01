package de.adohadesign.memorasolo.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
 * Checks for newly released Briefstudio documents. A notification contains its title,
 * but never the document body. Actual import stays gated behind Memora Solo unlock.
 */
class BriefstudioNotificationWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BriefstudioTransfer.isConnected(applicationContext)) return Result.success()
        return try {
            val documents = BriefstudioTransfer.fetchSaved(applicationContext)
            val dao = DiaryDatabase.get(applicationContext).diaryDao()
            val pending = documents.filter { dao.importedCount(it.id) == 0 }
            postNewNotifications(pending)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun postNewNotifications(pending: List<BriefstudioDocument>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Briefstudio-Übertragung", NotificationManager.IMPORTANCE_DEFAULT))
        val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val previouslyNotified = preferences.getStringSet("notified_ids", emptySet()).orEmpty()
        val pendingIds = pending.map { it.id }.toSet()
        // Remove fulfilled items from the remembered set so future separate documents can be notified.
        val nextNotified = previouslyNotified.intersect(pendingIds).toMutableSet()
        for (doc in pending) {
            if (doc.id in nextNotified) continue
            val intent = Intent(context, de.adohadesign.memorasolo.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("briefstudio_document_id", doc.id)
            }
            val notificationId = doc.id.hashCode()
            val action = PendingIntent.getActivity(context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val title = doc.title.trim().ifBlank { "Neuer Tagebucheintrag" }.take(120)
            manager.notify(notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("Neuer Tagebucheintrag")
                    .setContentText(title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                    .setContentIntent(action)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .build())
            nextNotified.add(doc.id)
        }
        preferences.edit().putStringSet("notified_ids", nextNotified).apply()
    }

    companion object {
        private const val CHANNEL_ID = "briefstudio_updates"
        private const val PREF_NAME = "briefstudio_notification_state"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BriefstudioNotificationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "briefstudio_pending_check", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notifyImported(context: Context, document: BriefstudioDocument) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Briefstudio-Übertragung", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = Intent(context, de.adohadesign.memorasolo.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("briefstudio_document_id", document.id)
            }
            val pendingIntent = PendingIntent.getActivity(context, document.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(document.id.hashCode(), NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Tagebucheintrag angekommen")
                .setContentText(document.title.trim().ifBlank { "Neuer Tagebucheintrag" }.take(120))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build())
        }

        fun clearForImported(context: Context, documentId: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(documentId.hashCode())
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val notified = prefs.getStringSet("notified_ids", emptySet()).orEmpty().toMutableSet()
            if (notified.remove(documentId)) prefs.edit().putStringSet("notified_ids", notified).apply()
        }
    }
}
