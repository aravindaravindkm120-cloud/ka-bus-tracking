package com.kabus.crew.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.kabus.crew.KaBusCrewApp
import com.kabus.crew.data.ImpressionRequest
import com.kabus.crew.data.ServeAdResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CREW_APP_HOME ad slot. Placed strictly BELOW the Start/End GPS controls in
 * the layout so it can never overlay them. Genuine-impression only: reported
 * after the banner has been visibly on screen for >=1s.
 */
@Composable
fun AdBanner() {
    val context = LocalContext.current
    val app = context.applicationContext as KaBusCrewApp
    var ad by remember { mutableStateOf(ServeAdResponse(null, null, null, null, null, 0, 0)) }
    val coroutineScope = rememberCoroutineScope()

    var frequencySec by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val res = app.api.ad()
                ad = res
                frequencySec = if (res.frequencySeconds > 0) res.frequencySeconds.toLong() else 60L
            }
            delay(frequencySec * 1000)
        }
    }

    LaunchedEffect(ad.adId) {
        val id = ad.adId ?: return@LaunchedEffect
        delay(1000)
        coroutineScope.launch {
            runCatching {
                app.api.recordImpression(
                    ImpressionRequest(
                        adId = id,
                        placement = "CREW_APP_HOME",
                        deviceId = app.session.deviceId,
                        durationViewedMs = ad.durationSeconds.coerceAtLeast(1).toLong() * 1000,
                        clicked = false
                    )
                )
            }
        }
    }

    val imageUrl = ad.imageUrl ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !ad.targetUrl.isNullOrBlank()) {
                    val url = ad.targetUrl ?: return@clickable
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    )
                    coroutineScope.launch {
                        runCatching {
                            app.api.recordImpression(
                                ImpressionRequest(
                                    adId = ad.adId ?: return@launch,
                                    placement = "CREW_APP_HOME",
                                    deviceId = app.session.deviceId,
                                    durationViewedMs = 1000,
                                    clicked = true
                                )
                            )
                        }
                    }
                }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = ad.title,
                modifier = Modifier
                    .width(56.dp)
                    .height(56.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                ad.title ?: "Sponsored",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}