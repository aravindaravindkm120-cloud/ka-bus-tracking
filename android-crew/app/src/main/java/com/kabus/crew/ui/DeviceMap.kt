package com.kabus.crew.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kabus.crew.data.CrewLocationHolder
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * "My Device" map card for the crew app.
 *
 * Renders the driver's live phone position published by LocationService's
 * foreground GPS session (CrewLocationHolder keeps the same fix the service
 * uploads). No simulated coordinates are ever drawn here or uploaded; a fix
 * isn't shown until the device actually produces one.
 */
@Composable
fun DeviceMapCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapHolder = remember { arrayOfNulls<MapView>(1) }
    val pin = remember { devicePinBitmap() }

    var deviceLocation by remember { mutableStateOf<Location?>(CrewLocationHolder.lastLocation) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(Unit) {
        val remove = CrewLocationHolder.addListener { loc ->
            deviceLocation = loc
        }
        onDispose {
            remove()
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("My Device", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your phone's real GPS position, exactly as uploaded to the backend. Updates as you move.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(
                        ctx,
                        ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                    )
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        minZoomLevel = 4.0
                        maxZoomLevel = 20.0
                        isClickable = true
                    }.also {
                        mapHolder[0] = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
            val loc = deviceLocation
            val map = mapHolder[0]
            if (loc != null && map != null) {
                LaunchedEffect(loc, map) {
                    val geo = GeoPoint(loc.latitude, loc.longitude)
                    map.controller.setCenter(geo)
                    map.controller.setZoom(16.0)
                    val marker = markerRef ?: Marker(map).apply {
                        title = "My Device"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = BitmapDrawable(context.resources, pin)
                    }.also {
                        map.overlays.add(it)
                        markerRef = it
                    }
                    marker.position = geo
                    map.invalidate()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                loc?.let {
                    String.format(
                        Locale.US,
                        "Lat %.6f  Lon %.6f  accuracy ±%.0f m",
                        it.latitude,
                        it.longitude,
                        it.accuracy
                    )
                } ?: "Waiting for a real GPS fix…",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun devicePinBitmap(): Bitmap {
    val size = 44
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.FILL
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val r = 18f
    canvas.drawCircle(size / 2f, size / 2f, r, fill)
    canvas.drawCircle(size / 2f, size / 2f, r, ring)
    return bmp
}