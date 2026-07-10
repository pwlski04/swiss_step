package io.github.pwlski04.swissstep.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import io.github.pwlski04.swissstep.paths.PathPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import org.mapsforge.core.model.LatLong
import io.github.pwlski04.swissstep.chains.PathStorage
import io.github.pwlski04.swissstep.paths.SegmentIndex
import io.github.pwlski04.swissstep.chains.AppRouteRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* Access these from Service and ViewModel */

object AppPathStorage {
    var instance: PathStorage? = null
}

object AppSegmentIndex {
    var instance: SegmentIndex? = null
}


class LocationTrackingService: Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sessionId: Long = 0L
    private val movementClassifier = MovementClassifier()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var pointsSinceLastSave = 0
    private val SAVE_INTERVAL = 10      // every ca. 50s in background, every ca. 10s in foreground

    private var lastCheckpointTime = System.currentTimeMillis()
    private val CHECKPOINT_INTERVAL_MS = 60 * 60 * 1000L      // fold the append log into a fresh snapshot hourly during long unattended sessions

    private var lastNotifiedMovementType: MovementType? = null
    private var lastNotifiedIsDrawing: Boolean? = null
    private var locationUpdatesStarted = false

    /* Notification */
    private fun buildNotification(isDrawing: Boolean, movementType: MovementType): android.app.Notification {
        return if (isDrawing) {
            val movementText = when (movementType) {
                MovementType.WALKING -> "walking"
                MovementType.RUNNING -> "running"
                MovementType.BIKING -> "biking"
                MovementType.TRANSPORT -> "using transport"
                MovementType.STILL -> "still"
            }
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking active")
                .setContentText("Recording your route. Currently " + movementText + ".")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(pendingIntent())
                .setDeleteIntent(deleteIntent())
                .build()
        } else {
            // Minimal silent notification required to keep foreground service alive
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("swiss step – location active")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setSilent(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // hidden from lock screen
                .setContentIntent(pendingIntent())
                .setDeleteIntent(deleteIntent())
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .build()
        }
    }

    private fun showTrackingNotification(movementType: MovementType) {
        /* Updates the ongoing notification's text to the current movement type, but only re-notifies when something actually changed */
        if (movementType == lastNotifiedMovementType && lastNotifiedIsDrawing == true) return
        lastNotifiedMovementType = movementType
        lastNotifiedIsDrawing = true

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(true, movementType))
    }

    private fun showIdleNotification() {
        if (lastNotifiedIsDrawing == false) return
        lastNotifiedIsDrawing = false
        lastNotifiedMovementType = null

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(false, MovementType.STILL))
    }

    private fun pendingIntent(): PendingIntent? {
        val openAppIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        return openAppIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun deleteIntent(): PendingIntent {
        /*
        Android 14+ lets users swipe away ongoing/foreground-service notifications (setOngoing no
        longer blocks it). The service itself keeps running either way, so instead of fighting the
        platform we just detect the swipe and instantly re-post the notification.
        */
        val intent = Intent(this, LocationTrackingService::class.java)
            .setAction(ACTION_NOTIFICATION_DISMISSED)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showForegroundNotification() {
        createNotificationChannel()

        // State-aware
        val isDrawing = TrackingLiveState.isDrawing.value
        val movementType = TrackingLiveState.movementType.value
        lastNotifiedIsDrawing = isDrawing
        lastNotifiedMovementType = if (isDrawing) movementType else null

        val notification = buildNotification(isDrawing, movementType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }


    /* Lifecycle */
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        isRunning = true

        // Switch from idle to full foreground request (or back) when drawing state changes
        serviceScope.launch {
            TrackingLiveState.isDrawing.collect {
                if (locationUpdatesStarted && useForegroundUpdates) restartLocationUpdates()
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /*
        Dispatches on the starting Intent's action to decide which location-update profile
        to run: ACTION_FOREGROUND_UPDATES / ACTION_BACKGROUND_UPDATES switch an already-running
        service between profiles, a null intent means the system restarted the killed service
        (resume tracking in the background if we were mid-recording), and any other start
        defaults to foreground/idle updates.
        */

        showForegroundNotification()

        if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
            // Don't touch session/location-update state for a regular notification swipe
            return START_STICKY
        }

        // Handle restart with null intent
        if (intent == null) {
            // Restarted by system after being killed — resume tracking if was drawing
            if (TrackingLiveState.isDrawing.value) {
                useForegroundUpdates = false  // background mode since app isn't open
                startLocationUpdates()
            }
            return START_STICKY
        }
        //sessionId = intent.getLongExtra(EXTRA_SESSION_ID, loadTrackingSessionId(this))

        sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, loadTrackingSessionId(this))
            ?: loadTrackingSessionId(this)

        saveTrackingState(this, true, sessionId)

        when (intent?.action) {
            ACTION_FOREGROUND_UPDATES -> {
                Log.d("SwissStep_TAG", "Service action: foreground updates")
                useForegroundUpdates = true
                TrackingLiveState.isForegroundTracking.value = true
                restartLocationUpdates()
                return START_STICKY
            }

            ACTION_BACKGROUND_UPDATES -> {
                Log.d("SwissStep_TAG", "Service action: background updates")
                useForegroundUpdates = false
                TrackingLiveState.isForegroundTracking.value = false
                restartLocationUpdates()
                return START_STICKY
            }
        }

        useForegroundUpdates = true
        TrackingLiveState.isForegroundTracking.value = true
        startLocationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        serviceJob.cancel()
        super.onDestroy()

        isRunning = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        /*
        Called when the user swipes the app away from recents. Flushes storage/in-progress
        recording to disk immediately, and only stops the service if we weren't actively drawing a
        route (otherwise tracking keeps running in the background).
        */

        super.onTaskRemoved(rootIntent)
        serviceScope.launch(Dispatchers.IO) {
            AppPathStorage.instance?.save(applicationContext)
            if (TrackingLiveState.isDrawing.value) {
                AppRouteRecorder.instance?.saveInProgress(applicationContext)
            }
        }

        if(!TrackingLiveState.isDrawing.value) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    /* Location requests */
    private val foregroundRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    )
        .setMinUpdateIntervalMillis(500L)
        .setMinUpdateDistanceMeters(2f)
        .setWaitForAccurateLocation(false)
        .build()

    private val backgroundRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5000L
    )
        .setMinUpdateIntervalMillis(3000L)
        .setMinUpdateDistanceMeters(2f)
        .setWaitForAccurateLocation(false)
        .build()

    // Used when the app is open but not drawing — just enough to show the location dot
    private val idleRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        8000L
    )
        .setMinUpdateIntervalMillis(5000L)
        .setMinUpdateDistanceMeters(10f)
        .setWaitForAccurateLocation(false)
        .build()

    private var useForegroundUpdates = true

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            Log.d("SwissStep_TAG", "Location update count: ${result.locations.size}")

            for (location in result.locations) {
                Log.d("SwissStep_TAG", "Location: ${location.latitude}, ${location.longitude}")

                handleLocation(location)
            }
        }
    }


    /* Main location handling */
    private fun handleLocation(location: Location) {
        /*
        Called for every raw GPS fix with and without active recording. Always classifies movement
        and publishes it to TrackingLiveState (which drives the UI's live location dot/marker). Only
        feeds the point into PathStorage/RouteRecorder and periodically persists to disk when a route
        is actively being drawn, and only if the fix falls within Switzerland's bounds.
        */
        val movementType = movementClassifier.classify(location)

        val point = PathPoint(
            location.latitude,
            location.longitude,
            System.currentTimeMillis(),
            sessionId,
            movementType
        )

        TrackingLiveState.latestPoint.value = point
        TrackingLiveState.movementType.value = movementType

        // Record into PathStorage (works whether app is open or closed)
        if (TrackingLiveState.isDrawing.value) {
            showTrackingNotification(movementType)
            val storage = AppPathStorage.instance ?: return
            val index   = AppSegmentIndex.instance ?: return

            val inSwitzerland = location.latitude in 45.81796..47.80845 && location.longitude in 5.9559..10.49215
            if (inSwitzerland) {
                val effectiveType = if (movementType != MovementType.STILL) {
                    storage.lastActiveMovementType = movementType
                    movementType
                } else {
                    storage.lastActiveMovementType
                }
                AppRouteRecorder.instance?.recordPoint(location.latitude, location.longitude, movementType)
                index.ensureLoaded(location.latitude, location.longitude, "live")
                storage.onGpsPoint(
                    LatLong(location.latitude, location.longitude),
                    effectiveType,
                    index,
                    point.timestamp
                )
            }
        } else {
            showIdleNotification()
        }

        if (TrackingLiveState.isDrawing.value) {
            pointsSinceLastSave++
            if (pointsSinceLastSave >= SAVE_INTERVAL) {
                pointsSinceLastSave = 0
                val dueForCheckpoint = System.currentTimeMillis() - lastCheckpointTime >= CHECKPOINT_INTERVAL_MS
                if (dueForCheckpoint) lastCheckpointTime = System.currentTimeMillis()
                serviceScope.launch(Dispatchers.IO) {
                    // Normally a cheap append; only folds into a full rewrite once an hour so an
                    // unattended multi-day session doesn't leave the log growing forever.
                    if (dueForCheckpoint) {
                        AppPathStorage.instance?.checkpoint(applicationContext)
                    } else {
                        AppPathStorage.instance?.save(applicationContext)
                    }
                }
            }
        }
    }


    /* Manange location updates */
    private fun startLocationUpdates() {
        /*
        Picks a location-request profile (background/foreground/idle) based on current mode and
        drawing state, then subscribes to it.
        */

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
            "SwissStep_TAG",
            "Permission check: fine=$fineGranted background=$backgroundGranted foreground=$useForegroundUpdates"
        )

        if (!useForegroundUpdates && !backgroundGranted) {
            Log.d(
                "SwissStep_TAG",
                "ACCESS_BACKGROUND_LOCATION=false. Service is still foreground, but background permission is not granted."
            )
        }

        if (!fineGranted) {
            Log.d("SwissStep_TAG", "No fine location permission. Stopping service.")
            stopSelf()
            return
        }

        val activeRequest = when {
            !useForegroundUpdates -> backgroundRequest
            TrackingLiveState.isDrawing.value -> foregroundRequest
            else -> idleRequest
        }

        fusedLocationClient.requestLocationUpdates(
            activeRequest,
            callback,
            Looper.getMainLooper()
        )

        locationUpdatesStarted = true
        Log.d("SwissStep_TAG", "requestLocationUpdates called")
    }

    private fun restartLocationUpdates() {
        stopLocationUpdates()
        startLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(callback)
        locationUpdatesStarted = false
    }


    /* Notification channel */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location tracking",
            NotificationManager.IMPORTANCE_MIN
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
        private const val ACTION_NOTIFICATION_DISMISSED = "notification_dismissed"

        var isRunning = false

        fun start(context: Context, sessionId: Long) {
            /* Starts the service fresh for a new tracking session (e.g. when location permission is first granted). */
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)

            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            /* Fully stops the service and marks tracking state as inactive. */
            saveTrackingState(context, false, loadTrackingSessionId(context))

            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        }

        fun restartForForeground(context: Context, sessionId: Long) {
            /*
            Called when the app comes back to the foreground without location permission granted
            yet, to nudge the (already-running or restartable) service back to life via a plain
            startService call.
            */

            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            context.startService(intent)
        }

        fun useForegroundUpdates(context: Context) {
            /*
            Switches an already-running service to the higher-frequency/higher-accuracy location
            profile (app is in the foreground).
            */

            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_FOREGROUND_UPDATES)

            context.startService(intent)
        }

        fun useBackgroundUpdates(context: Context) {
            /*
            Switches an already-running service to the less frequent, battery-friendlier location
            profile (app is backgrounded but still recording).
            */

            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_BACKGROUND_UPDATES)

            context.startService(intent)
        }
    }
}