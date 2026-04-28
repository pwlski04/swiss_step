package com.example.stepbystep_v10


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTracker(context: Context, private val onLocation: (Location) -> Unit) {
    /* Main functions */
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun start() {
        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        fusedLocationClient.removeLocationUpdates(callback)
    }


    /* Helper functions */
    private val request = LocationRequest.Builder(      // Sends location request every 3 seconds (offline and even in the same spot)
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L
    )
        .setMinUpdateDistanceMeters(0f)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                onLocation(location)
            }
        }
    }
}