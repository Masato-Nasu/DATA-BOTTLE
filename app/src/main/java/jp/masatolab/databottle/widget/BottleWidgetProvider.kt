package jp.masatolab.databottle.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import jp.masatolab.databottle.MainActivity
import jp.masatolab.databottle.R
import jp.masatolab.databottle.data.AppSettings
import jp.masatolab.databottle.data.BottleType
import jp.masatolab.databottle.data.DataRepository

class BottleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        fun updateAllAsync(context: Context) {
            Thread {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, BottleWidgetProvider::class.java))
                ids.forEach { updateOne(context, manager, it) }
            }.start()
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val settings = AppSettings(context)
            val enabled = settings.enabled()
            val type = settings.lastViewed().takeIf { it in enabled }
                ?: settings.order().firstOrNull { it in enabled }
                ?: BottleType.BATTERY
            val repository = DataRepository(context, settings)
            val metric = repository.read(type)

            val options = manager.getAppWidgetOptions(widgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260)
            val density = context.resources.displayMetrics.density
            val widthPx = (minWidthDp * density).toInt().coerceIn(300, 540)
            val heightPx = (minHeightDp * density).toInt().coerceIn(420, 800)
            val bitmap = BottleBitmapRenderer.render(metric, widthPx, heightPx)

            val views = RemoteViews(context.packageName, R.layout.widget_data_bottle)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
