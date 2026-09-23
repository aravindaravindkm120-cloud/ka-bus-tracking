package com.kabus.admin.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.LiveBusItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live fleet map built on Leaflet + OpenStreetMap (no Google Maps).
 * Buses are rendered as markers and refreshed every 10 seconds from the
 * scope-aware /api/admin/live-buses endpoint. A WebView hosts Leaflet so no
 * extra map SDK dependency is needed.
 */
@Composable
fun LiveMapScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<LiveBusItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var webViewReady by remember { mutableStateOf(false) }
    val latestItems by rememberUpdatedState(items)
    val pull = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    @SuppressLint("SetJavaScriptEnabled")
    fun buildView(): WebView =
        WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webViewReady = true
                }
            }
            loadDataWithBaseURL(
                "https://example.in/",
                LEAFLET_HTML,
                "text/html",
                "utf-8",
                null
            )
        }

    val holder = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { buildView() },
        modifier = Modifier.fillMaxSize(),
        update = { holder.value = it },
        onRelease = { it.destroy() }
    )

    DisposableEffect(Unit) {
        onDispose { holder.value?.destroy() }
    }

    suspend fun fetchBuses() {
        runCatching { app.api.liveBuses(app.auth(), 300) }
            .onSuccess {
                error = null
                items = it
                holder.value?.evaluateJavascript("setBuses(${toMarkerJson(it)})", null)
            }
            .onFailure { error = it.message }
    }

    LaunchedEffect(webViewReady) {
        while (true) {
            fetchBuses()
            delay(10_000)
        }
    }

    val onRefresh: () -> Unit = { scope.launch { pull.run { fetchBuses() } } }

    if (error != null && items.isEmpty()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (!webViewReady) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp))
        }

        // The map is a WebView, so a raw drag over it belongs to Leaflet. The
        // pull-to-refresh gesture is offered on a thin Compose-owned strip at
        // the very top; everything below keeps native map pan/zoom/rotate.
        RefreshableBox(
            refreshing = pull.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.TopCenter)
                    .scrollable(
                        state = rememberScrollableState { delta -> delta },
                        orientation = Orientation.Vertical
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (pull.refreshing) "Refreshing…" else "Pull down here to refresh live buses",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        val count = latestItems.size
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "$count bus${if (count == 1) "" else "es"} on map",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun toMarkerJson(buses: List<LiveBusItem>): String {
    val arr = JSONArray()
    buses.forEach { b ->
        val lat = b.latitude
        val lng = b.longitude
        if (lat != null && lng != null) {
            arr.put(
                JSONObject()
                    .put("id", b.busId)
                    .put("lat", lat)
                    .put("lng", lng)
                    .put("label", b.registrationNo)
                    .put("speed", b.speedKmh)
                    .put("status", b.status)
            )
        }
    }
    return arr.toString()
}

@SuppressLint("SetJavaScriptEnabled")
private const val LEAFLET_HTML: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Live fleet</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
<style>
  html, body, #map { height: 100%; margin: 0; padding: 0; }
  .bus-label { font-weight: bold; }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map');
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map);
  map.setView([14.0, 77.0], 6);

  var markers = {};
  var icon = L.divIcon({
    className: 'bus-label',
    html: '\uD83D\uDE8C',
    iconSize: [26, 26],
    iconAnchor: [13, 26]
  });
  var fitted = false;

  function setBuses(buses) {
    var seen = {};
    buses.forEach(function (b) {
      seen[b.id] = true;
      if (markers[b.id]) {
        markers[b.id].setLatLng([b.lat, b.lng])
                       .bindPopup('<b>' + b.label + '</b><br/>' + (b.speed || 0).toFixed(0) + ' km/h<br/>' + b.status);
      } else {
        var m = L.marker([b.lat, b.lng], {icon: icon})
                 .bindPopup('<b>' + b.label + '</b><br/>' + (b.speed || 0).toFixed(0) + ' km/h<br/>' + b.status)
                 .addTo(map);
        markers[b.id] = m;
      }
    });
    Object.keys(markers).forEach(function (id) {
      if (!seen[id]) {
        map.removeLayer(markers[id]);
        delete markers[id];
      }
    });
    if (!fitted && buses.length > 0) {
      var bounds = buses.map(function (b) { return [b.lat, b.lng]; });
      map.fitBounds(bounds, {padding: [30, 30], maxZoom: 14});
      fitted = true;
    }
  }
</script>
</body>
</html>
"""