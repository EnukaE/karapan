package com.example.widget
import android.content.Context
import android.location.Location
object LocationTracker {
    suspend fun getCurrentLocation(context: Context): Location? = null
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float = 0f
}
