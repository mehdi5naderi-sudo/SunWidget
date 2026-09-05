package com.mehdi.sunwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class SunWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val PREFS = "sun_prefs"
        private const val API = "https://api.sunrise-sunset.org/json"
        private val executor = Executors.newSingleThreadExecutor()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, SunWidgetProvider::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.sun_widget)
            val launch = PendingIntent.getActivity(
                context, widgetId,
                Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.sky_image, launch)
            views.setTextViewText(R.id.status_text, "در حال به‌روزرسانی…")
            manager.updateAppWidget(widgetId, views)

            executor.execute {
                val data = fetchSun(context)
                val updated = RemoteViews(context.packageName, R.layout.sun_widget)
                updated.setOnClickPendingIntent(R.id.sky_image, launch)
                if (data != null) {
                    updated.setTextViewText(R.id.sunrise_text, "☀  ${data.sunrise}")
                    updated.setTextViewText(R.id.sunset_text, "◐  ${data.sunset}")
                    updated.setTextViewText(R.id.status_text, "به‌روزرسانی خودکار هر ۳۰ دقیقه")
                    updated.setImageViewBitmap(R.id.sky_image, drawSky(data.sunrise, data.sunset))
                } else {
                    updated.setTextViewText(R.id.sunrise_text, "☀  --:--")
                    updated.setTextViewText(R.id.sunset_text, "◐  --:--")
                    updated.setTextViewText(R.id.status_text, "اتصال به اینترنت ناموفق بود")
                    updated.setImageViewBitmap(R.id.sky_image, drawSky("06:00", "18:00"))
                }
                manager.updateAppWidget(widgetId, updated)
            }
        }

        private data class SunData(val sunrise: String, val sunset: String)

        private fun fetchSun(context: Context): SunData? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = prefs.getString("lat", "35.6892") ?: "35.6892"
            val lon = prefs.getString("lon", "51.3890") ?: "51.3890"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            try {
                val url = URL("$API?lat=${Uri.encode(lat)}&lng=${Uri.encode(lon)}&date=$today&formatted=0")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(body)
                if (root.optString("status") != "OK") throw Exception("API status")
                val result = root.getJSONObject("results")
                val sunrise = result.getString("sunrise").substring(11, 16)
                val sunset = result.getString("sunset").substring(11, 16)
                prefs.edit().putString("cached_date", today).putString("sunrise", sunrise).putString("sunset", sunset).apply()
                return SunData(sunrise, sunset)
            } catch (_: Exception) {
                val cachedDate = prefs.getString("cached_date", null)
                val sunrise = prefs.getString("sunrise", null)
                val sunset = prefs.getString("sunset", null)
                return if (cachedDate == today && sunrise != null && sunset != null) SunData(sunrise, sunset) else null
            }
        }

        private fun drawSky(sunrise: String, sunset: String): Bitmap {
            val w = 900
            val h = 330
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val now = Calendar.getInstance()
            val sunriseMin = toMinutes(sunrise)
            val sunsetMin = toMinutes(sunset)
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val day = nowMin in sunriseMin..sunsetMin
            val top = if (day) Color.rgb(68, 151, 222) else Color.rgb(19, 28, 58)
            val bottom = if (day) Color.rgb(236, 177, 92) else Color.rgb(8, 13, 31)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            })

            val horizonY = h * 0.76f
            val path = Path().apply {
                addArc(RectF(80f, horizonY - 430f, w - 80f, horizonY + 430f), 200f, 140f)
            }
            canvas.drawPath(path, Paint().apply {
                color = Color.argb(110, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            })

            val progress = if (day && sunsetMin > sunriseMin) ((nowMin - sunriseMin).toFloat() / (sunsetMin - sunriseMin)).coerceIn(0f, 1f) else 0.5f
            val angle = Math.PI * (1.0 - progress)
            val cx = w / 2f
            val rx = w * 0.42f
            val ry = h * 1.02f
            val sx = cx + rx * cos(angle).toFloat()
            val sy = horizonY - ry * sin(angle).toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day) Color.rgb(255, 218, 75) else Color.rgb(235, 241, 255) }
            canvas.drawCircle(sx, sy, if (day) 30f else 22f, p)
            if (day) {
                p.color = Color.argb(90, 255, 230, 100)
                canvas.drawCircle(sx, sy, 50f, p)
            } else {
                p.color = Color.rgb(19, 28, 58)
                canvas.drawCircle(sx + 8f, sy - 5f, 18f, p)
                val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                listOf(110 to 65, 210 to 105, 700 to 80, 790 to 145, 590 to 45).forEach { (x, y) -> canvas.drawCircle(x.toFloat(), y.toFloat(), 3f, star) }
            }
            return bitmap
        }

        private fun toMinutes(value: String): Int {
            val parts = value.split(":")
            return parts[0].toInt() * 60 + parts[1].toInt()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }
}
