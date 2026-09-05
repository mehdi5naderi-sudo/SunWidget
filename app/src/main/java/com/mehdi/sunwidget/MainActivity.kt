package com.mehdi.sunwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    private val requestCode = 42
    private val prefsName = "sun_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        val title = TextView(this).apply {
            text = "☀  طلوع و غروب خورشید"
            textSize = 26f
        }
        val info = TextView(this).apply {
            text = "برای نمایش دقیق طلوع و غروب، موقعیت مکانی گوشی ذخیره می‌شود.\n\nپیش‌فرض: تهران"
            textSize = 17f
            setPadding(0, 32, 0, 32)
        }
        val button = Button(this).apply { text = "دریافت موقعیت و به‌روزرسانی ویجت" }
        root.addView(title)
        root.addView(info)
        root.addView(button)
        setContentView(root)
        button.setOnClickListener { requestLocation() }
        if (hasLocationPermission()) saveLocationAndUpdate() else requestLocation()
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) {
            saveLocationAndUpdate()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) saveLocationAndUpdate()
    }

    private fun saveLocationAndUpdate() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            if (hasLocationPermission()) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .mapNotNull { provider -> try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null } }
                    .maxByOrNull { it.time }
            } else null
        } catch (_: Exception) { null }

        val lat = location?.latitude ?: 35.6892
        val lon = location?.longitude ?: 51.3890
        getSharedPreferences(prefsName, MODE_PRIVATE).edit().putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
        SunWidgetProvider.updateAll(this)
    }
}
