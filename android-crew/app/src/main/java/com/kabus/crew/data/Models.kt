package com.kabus.crew.data

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String?
)

data class AuthUser(
    val id: Long,
    val username: String,
    val fullName: String?,
    val email: String?,
    val phone: String?,
    val roles: List<String>,
    val divisionId: Long?,
    val depotId: Long?,
    val townId: Long?
)

data class LoginResponse(
    val user: AuthUser?,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long
)

data class RefreshRequest(
    val refreshToken: String
)

data class Assignment(
    val crewId: Long?,
    val badgeNo: String?,
    val crewName: String?,
    val crewType: String?,
    val tripId: Long?,
    val tripNumber: String?,
    val tripStatus: String?,
    val busId: Long?,
    val busRegistration: String?,
    val busType: String?,
    val routeId: Long?,
    val routeName: String?,
    val routeCode: String?,
    val origin: String?,
    val destination: String?,
    val sessionStatus: String?,
    val sessionId: Long?,
    val sessionKey: String?
) {
    val hasAssignment: Boolean get() = tripId != null && busId != null
}

data class GpsStartRequest(
    val deviceId: String?,
    val appVersion: String?
)

data class GpsStartResponse(
    val sessionKey: String?,
    val sessionId: Long?,
    val busId: Long?,
    val tripId: Long?,
    val routeId: Long?,
    val status: String?,
    val startedAt: String?
)

data class GpsLocationRequest(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val speed: BigDecimal,
    val heading: BigDecimal,
    val accuracy: BigDecimal,
    val altitude: BigDecimal?,
    val timestamp: String,
    val clientNowEpochMillis: Long
)

data class GpsLocationResponse(
    val accepted: Boolean,
    val sessionKey: String?,
    val busId: Long?,
    val tripId: Long?,
    val busStatus: String?,
    val intervalMs: Int,
    val message: String?
)

data class GpsEndResponse(
    val sessionKey: String?,
    val busId: Long?,
    val tripId: Long?,
    val status: String?,
    val tripStatus: String?,
    val endedAt: String?
)

data class CrewStatus(
    val assignment: Assignment? = null,
    val gpsStatus: String?,
    val lastUpdateAgeSeconds: Int,
    val lastUpdateAtEpochMs: Long?
)

data class NotificationItem(
    val id: Long,
    val type: String,
    val title: String,
    val body: String?,
    val dataJson: String?,
    @SerializedName("read") val isRead: Boolean = false
)

data class Page<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0
)

data class ServeAdResponse(
    val adId: String?,
    val campaignId: Long?,
    val title: String?,
    val imageUrl: String?,
    val targetUrl: String?,
    val durationSeconds: Int,
    val frequencySeconds: Int
)

data class ImpressionRequest(
    val adId: String,
    val placement: String,
    val deviceId: String?,
    val durationViewedMs: Long,
    val clicked: Boolean
)