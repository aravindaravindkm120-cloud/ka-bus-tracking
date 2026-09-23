package com.kabus.crew.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kabus.crew.KaBusCrewApp
import com.kabus.crew.MainActivity
import com.kabus.crew.R
import com.kabus.crew.data.CrewLocationHolder
import com.kabus.crew.data.GpsLocationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Foreground service uploading REAL device fixes (FusedLocationProviderClient)
 * to the backend for the duration of a GPS session. There is no simulated GPS:
 * only genuine LocationStore results are sent, the interval is fixed at 5s,
 * and the service stops when the crew ends the session.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fused: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var sessionKey: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopUploading()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val key = intent?.getStringExtra(EXTRA_SESSION_KEY)
                if (key.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                sessionKey = key
                startForeground(NOTIF_ID, buildNotification())
                if (hasLocationPermission()) {
                    startUploading(key)
                }
                return START_STICKY
            }
        }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun startUploading(key: String) {
        val app = application as KaBusCrewApp
        val client = fused ?: return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(10_000)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                CrewLocationHolder.onLocation(location)
                scope.launch {
                    runCatching {
                        app.api.gpsLocation(
                            "Bearer ${app.session.accessToken}",
                            key,
                            toRequest(location)
                        )
                    }
                }
            }
        }
        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        locationCallback = callback
    }

    private fun toRequest(l: Location): GpsLocationRequest {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return GpsLocationRequest(
            latitude = BigDecimal.valueOf(l.latitude).setScale(7, java.math.RoundingMode.HALF_UP),
            longitude = BigDecimal.valueOf(l.longitude).setScale(7, java.math.RoundingMode.HALF_UP),
            speed = BigDecimal.valueOf((if (l.hasSpeed()) l.speed else 0.0).toDouble()).setScale(2, java.math.RoundingMode.HALF_UP),
            heading = BigDecimal.valueOf((if (l.hasBearing()) l.bearing else 0.0).toDouble()).setScale(2, java.math.RoundingMode.HALF_UP),
            accuracy = BigDecimal.valueOf(l.accuracy.toDouble()).setScale(2, java.math.RoundingMode.HALF_UP),
            altitude = if (l.hasAltitude()) BigDecimal.valueOf(l.altitude.toDouble()) else null,
            timestamp = now,
            clientNowEpochMillis = System.currentTimeMillis()
        )
    }

    private fun stopUploading() {
        locationCallback?.let { fused?.removeLocationUpdates(it) }
        locationCallback = null
        fused = null
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KA Bus Crew - GPS active")
            .setContentText("Sharing your live location with the depot")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End session",
                stopIntent
            )
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_location),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lightColor = Color.GREEN
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopUploading()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kabus_crew_location"
        private const val NOTIF_ID = 1001
        private const val ACTION_START = "com.kabus.crew.action.START"
        private const val ACTION_STOP = "com.kabus.crew.action.STOP"
        private const val EXTRA_SESSION_KEY = "sessionKey"

        fun start(context: Context, sessionKey: String) {
            val intent = Intent(context, LocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_KEY, sessionKey)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}