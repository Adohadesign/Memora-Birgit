package de.adohadesign.memorasolo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.adohadesign.memorasolo.MainActivity
import de.adohadesign.memorasolo.R

class MemoraWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_memora)
            fun target(viewId: Int, mode: String, code: Int) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = "de.adohadesign.memorasolo.START_$mode"
                    putExtra("start_mode", mode)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                views.setOnClickPendingIntent(viewId, PendingIntent.getActivity(context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            target(R.id.widget_text, "TEXT", id * 10 + 1)
            target(R.id.widget_voice, "VOICE", id * 10 + 2)
            target(R.id.widget_photo, "PHOTO", id * 10 + 3)
            manager.updateAppWidget(id, views)
        }
    }
}
