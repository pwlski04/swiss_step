package com.example.stepbystep_v10.tracking

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.stepbystep_v10.map.paths.PathFunctions
import com.example.stepbystep_v10.map.paths.PathPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTrackingService: Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sessionId: Long = 0L
    private val movementClassifier = MovementClassifier()


    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FOREGROUND_UPDATES -> {
                Log.d("StepByStep_v1.0_TAG", "Service action: foreground updates")
                useForegroundUpdates = true
                TrackingLiveState.isForegroundTracking.value = true
                restartLocationUpdates()
                return START_STICKY
            }

            ACTION_BACKGROUND_UPDATES -> {
                Log.d("StepByStep_v1.0_TAG", "Service action: background updates")
                useForegroundUpdates = false
                TrackingLiveState.isForegroundTracking.value = false
                restartLocationUpdates()
                return START_STICKY
            }
        }

        sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, loadTrackingSessionId(this))
            ?: loadTrackingSessionId(this)

        saveTrackingState(this, true, sessionId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StepByStep tracking active")
            .setContentText("Recording your walking route.")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

        useForegroundUpdates = true
        startLocationUpdates()

        return START_STICKY
    }


    override fun onDestroy() {
        Log.d("StepByStep_v1.0_TAG", "LocationTrackingService destroyed")
        stopLocationUpdates()
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    private val foregroundRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    )
        .setMinUpdateIntervalMillis(500L)
        .setMinUpdateDistanceMeters(0f)
        .setWaitForAccurateLocation(false)
        .build()

    private val backgroundRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, //.PRIORITY_BALANCED_POWER_ACCURACY,
        5000L
    )
        .setMinUpdateIntervalMillis(3000L)
        .setMinUpdateDistanceMeters(0f)
        .setWaitForAccurateLocation(false)
        .build()

    private var useForegroundUpdates = true

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
        val movementType = movementClassifier.classify(location)

        val point = PathPoint(
            location.latitude,
            location.longitude,
            System.currentTimeMillis(),
            sessionId,
            movementType
        )

        PathFunctions.addPoint(point)

        TrackingLiveState.latestPoint.value = point
        TrackingLiveState.movementType.value = movementType

        Log.d(
            "StepByStep_v1.0_TAG",
            "Service location: ${location.latitude}, ${location.longitude}, movement=$movementType, session=$sessionId"
        )
    }

    private fun startLocationUpdates() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        Log.d(
            "StepByStep_v1.0_TAG",
            "Permission check: fine=$fineGranted background=$backgroundGranted foreground=$useForegroundUpdates"
        )

        if (!useForegroundUpdates && !backgroundGranted) {
            Log.d(
                "StepByStep_v1.0_TAG",
                "ACCESS_BACKGROUND_LOCATION=false. Service is still foreground, but background permission is not granted."
            )
        }

        if (!fineGranted) {
            Log.d("StepByStep_v1.0_TAG", "No fine location permission. Stopping service.")
            stopSelf()
            return
        }

        val activeRequest = if (useForegroundUpdates) {
            foregroundRequest
        } else {
            backgroundRequest
        }

        fusedLocationClient.requestLocationUpdates(
            activeRequest,
            callback,
            Looper.getMainLooper()
        )

        Log.d("StepByStep_v1.0_TAG", "requestLocationUpdates called")
    }

    private fun restartLocationUpdates() {
        stopLocationUpdates()
        startLocationUpdates()
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

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_SESSION_ID = "session_id"

        private const val ACTION_FOREGROUND_UPDATES = "foreground_updates"
        private const val ACTION_BACKGROUND_UPDATES = "background_updates"

        fun start(context: Context, sessionId: Long) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)

            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            saveTrackingState(context, false, loadTrackingSessionId(context))

            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        }

        fun useForegroundUpdates(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_FOREGROUND_UPDATES)

            ContextCompat.startForegroundService(context, intent)
        }

        fun useBackgroundUpdates(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_BACKGROUND_UPDATES)

            ContextCompat.startForegroundService(context, intent)
        }
    }
}