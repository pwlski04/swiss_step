package com.example.stepbystep_v10

import android.app.Service
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.util.Log
import com.google.android.gms.location.*

class LocationTrackingService: Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sessionId: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, loadTrackingSessionId(this))
            ?: loadTrackingSessionId(this)

        saveTrackingState(this, true, sessionId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StepByStep tracking active")
            .setContentText("Recording your walking route.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        startLocationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L
    )
        .setMinUpdateDistanceMeters(0f)
        .setWaitForAccurateLocation(false)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            Log.d("StepByStep_v1.0_TAG", "Location update count: ${result.locations.size}")

            for (location in result.locations) {
                Log.d("StepByStep_v1.0_TAG", "Location: ${location.latitude}, ${location.longitude}")

                handleLocation(location)
            }
        }
    }

    private fun handleLocation(location: Location) {
        val point = PathPoint(
            lat = location.latitude,
            lon = location.longitude,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )

        PathFunctions.addPoint(point)

        /*
         * Important:
         * If you want points to survive app restarts too,
         * PathFunctions.addPoint(...) must also write to local storage/database.
         */
    }

    private fun startLocationUpdates() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            stopSelf()
            return
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(callback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_SESSION_ID = "session_id"

        fun start(context: Context, sessionId: Long) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)

            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            saveTrackingState(context, false, loadTrackingSessionId(context))

            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        }
    }
}