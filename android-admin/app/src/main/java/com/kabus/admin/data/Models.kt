package com.kabus.admin.data

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String?
)

/** Strict role-specific admin login: valid credentials + membership in the requested role. */
data class AdminLoginRequest(
    val email: String,
    val password: String,
    val requestedRole: String
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

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long
)

data class ScopeInfo(
    val wholeSystem: Boolean,
    val divisionId: Long?,
    val depotId: Long?,
    val townId: Long?,
    val divisionIds: List<Long>?,
    val depotIds: List<Long>?,
    val townIds: List<Long>?
)

data class DashboardResponse(
    val role: String,
    val scope: ScopeInfo?,
    val corporations: Long,
    val divisions: Long,
    val depots: Long,
    val towns: Long,
    val buses: Long,
    val busesActive: Long,
    val busesLive: Long,
    val busesStale: Long,
    val busesOffline: Long,
    val crew: Long,
    val staff: Long,
    val routes: Long,
    val activeTrips: Long,
    val activeGpsSessions: Long
)

data class TripItem(
    val id: Long,
    val tripNumber: String,
    val tripDate: String?,
    val scheduledDeparture: String?,
    val scheduledArrival: String?,
    val status: String,
    val direction: String?,
    val routeId: Long?,
    val routeName: String?,
    val routeCode: String?,
    val busId: Long?,
    val busRegistrationNo: String?,
    val depotId: Long?,
    val depotName: String?,
    val divisionId: Long?,
    val divisionName: String?,
    val crewAssignments: List<CrewAssignmentItem> = emptyList(),
    val busAssignments: List<BusAssignmentItem> = emptyList()
)

data class CrewAssignmentItem(
    val id: Long,
    val crewId: Long,
    val badgeNo: String?,
    val fullName: String?,
    val crewType: String?,
    val status: String?,
    val assignedFrom: String?,
    val assignedTo: String?
)

data class BusAssignmentItem(
    val id: Long,
    val busId: Long,
    val registrationNo: String?,
    val status: String?,
    val assignedFrom: String?,
    val assignedTo: String?
)

data class LiveBusItem(
    val busId: Long,
    val registrationNo: String,
    val busType: String?,
    val tripId: Long?,
    val routeId: Long?,
    val routeName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val speedKmh: Double?,
    val heading: Double?,
    val status: String,
    val capturedAt: String?
)

data class CrewItem(
    val crewId: Long,
    val badgeNo: String?,
    val fullName: String?,
    val crewType: String?,
    val status: String?,
    val dutyStatus: String?
)

data class NotificationItem(
    val id: Long,
    val type: String,
    val title: String,
    val body: String?,
    val dataJson: String?,
    @SerializedName("read") val isRead: Boolean = false
)

/** Spring Data Page envelope (only the fields we use). */
data class Page<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 0
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

// ----------------------------------------------------------------------
// Organization hierarchy (Corporation -> Division -> Depot -> Town)
// Matches backend web/dto/OrgDtos.java + AdminOrganizationController.
// ----------------------------------------------------------------------

data class CorporationRequest(
    val code: String,
    val name: String,
    val address: String?,
    val city: String?,
    val state: String?,
    val contactEmail: String?,
    val contactPhone: String?
)

data class CorporationResponse(
    val id: Long,
    val code: String,
    val name: String,
    val address: String?,
    val city: String?,
    val state: String?,
    val contactEmail: String?,
    val contactPhone: String?,
    val enabled: Boolean,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class DivisionRequest(
    val corporationId: Long,
    val code: String,
    val name: String,
    val headOffice: String?
)

data class DivisionResponse(
    val id: Long,
    val corporationId: Long,
    val corporationCode: String?,
    val corporationName: String?,
    val code: String,
    val name: String,
    val headOffice: String?,
    val enabled: Boolean,
    val adminId: Long?,
    val adminName: String?,
    val adminEmail: String?,
    val adminStatus: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class DepotRequest(
    val divisionId: Long,
    val code: String,
    val name: String,
    val address: String?,
    val phone: String?
)

data class DepotResponse(
    val id: Long,
    val divisionId: Long,
    val divisionCode: String?,
    val divisionName: String?,
    val code: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val enabled: Boolean,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class TownRequest(
    val depotId: Long,
    val code: String,
    val name: String
)

data class TownResponse(
    val id: Long,
    val depotId: Long,
    val depotCode: String?,
    val depotName: String?,
    val code: String,
    val name: String,
    val enabled: Boolean,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ----------------------------------------------------------------------
// Fleet (buses) - GET/POST /api/admin/fleet/buses
// ----------------------------------------------------------------------

data class CreateBusRequest(
    val registrationNo: String,
    val busType: String,
    val capacity: Int,
    val fuelType: String?,
    val makeModel: String?,
    val manufactureYear: Int?,
    val gpsDeviceId: String?,
    val gpsEnabled: Boolean?,
    val status: String?,
    val depotId: Long,
    val townId: Long
)

data class UpdateBusRequest(
    val busType: String,
    val capacity: Int,
    val fuelType: String?,
    val makeModel: String?,
    val manufactureYear: Int?,
    val gpsDeviceId: String?,
    val gpsEnabled: Boolean?,
    val status: String?,
    val townId: Long?
)

data class BusItem(
    val id: Long,
    val registrationNo: String,
    val busType: String?,
    val capacity: Int?,
    val fuelType: String?,
    val makeModel: String?,
    val manufactureYear: Int?,
    val gpsDeviceId: String?,
    val gpsEnabled: Boolean,
    val status: String,
    val enabled: Boolean,
    val divisionId: Long?,
    val divisionName: String?,
    val depotId: Long?,
    val depotName: String?,
    val townId: Long?,
    val townName: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ----------------------------------------------------------------------
// Staff - GET/POST /api/admin/staff
// ----------------------------------------------------------------------

data class CreateStaffRequest(
    val fullName: String,
    val phone: String?,
    val empCode: String?,
    val designation: String?,
    val status: String?,
    val depotId: Long,
    val townId: Long?,
    val userId: Long?
)

data class UpdateStaffRequest(
    val fullName: String,
    val phone: String?,
    val empCode: String?,
    val designation: String?,
    val status: String?,
    val townId: Long?
)

data class StaffItem(
    val id: Long,
    val fullName: String,
    val phone: String?,
    val empCode: String?,
    val designation: String?,
    val status: String,
    val userId: Long?,
    val username: String?,
    val corporationId: Long?,
    val corporationName: String?,
    val divisionId: Long?,
    val divisionName: String?,
    val depotId: Long?,
    val depotName: String?,
    val townId: Long?,
    val townName: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ----------------------------------------------------------------------
// Routes + stops - /api/admin/routes
// ----------------------------------------------------------------------

data class CreateRouteRequest(
    val divisionId: Long?,
    val code: String,
    val name: String,
    val origin: String,
    val destination: String,
    val distanceKm: Double?,
    val estDurationMin: Int?,
    val status: String?
)

data class UpdateRouteRequest(
    val name: String,
    val origin: String,
    val destination: String,
    val distanceKm: Double?,
    val estDurationMin: Int?,
    val status: String?
)

data class RouteStopInput(
    val stopOrder: Int,
    val stopName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStart: Double?
)

data class ReplaceStopsRequest(
    val stops: List<RouteStopInput>
)

data class RouteStopItem(
    val id: Long,
    val stopOrder: Int,
    val stopName: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceFromStart: Double?
)

data class RouteItem(
    val id: Long,
    val code: String,
    val name: String,
    val origin: String,
    val destination: String,
    val distanceKm: Double?,
    val estDurationMin: Int?,
    val status: String,
    val enabled: Boolean,
    val divisionId: Long?,
    val divisionName: String?,
    val corporationName: String?,
    val stopCount: Int,
    val stops: List<RouteStopItem> = emptyList(),
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ----------------------------------------------------------------------
// Admin users - GET/POST /api/admin/users (SUPER_ADMIN only)
// ----------------------------------------------------------------------

data class CreateUserRequest(
    val username: String,
    val fullName: String,
    val email: String?,
    val phone: String?,
    val password: String,
    val role: String,
    val divisionId: Long?,
    val depotId: Long?,
    val townId: Long?,
    val enabled: Boolean?
)

data class ChangeRoleRequest(
    val role: String,
    val divisionId: Long?,
    val depotId: Long?,
    val townId: Long?
)

data class ChangePasswordRequest(
    val password: String
)

data class AdminUserItem(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String?,
    val phone: String?,
    val enabled: Boolean,
    val locked: Boolean,
    val mustChangePassword: Boolean,
    val role: String?,
    val divisionId: Long?,
    val divisionName: String?,
    val depotId: Long?,
    val depotName: String?,
    val townId: Long?,
    val townName: String?,
    @SerializedName("lastLoginAt") val lastLoginAt: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

// ----------------------------------------------------------------------
// Audit logs - GET /api/admin/audit-logs (SUPER_ADMIN only)
// ----------------------------------------------------------------------

data class AuditItem(
    val id: Long,
    val action: String?,
    val resourceType: String?,
    val resourceId: Long?,
    val detailJson: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val userId: Long?,
    val username: String?,
    @SerializedName("createdAt") val createdAt: String?
)

// ----------------------------------------------------------------------
// Settings - GET/PUT /api/admin/settings (SUPER_ADMIN only)
// ----------------------------------------------------------------------

data class SettingItem(
    val key: String,
    val value: String?,
    val type: String?,
    val description: String?,
    val defaultValue: String?,
    val updatedBy: Long?,
    val updatedByUsername: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class UpdateSettingRequest(
    val value: String
)

// ----------------------------------------------------------------------
// Trip actions
// ----------------------------------------------------------------------

data class CreateTripRequestBody(
    val routeId: Long,
    val busId: Long,
    val tripNumber: String?,
    val tripDate: String,
    val scheduledDeparture: String,
    val scheduledArrival: String,
    val direction: String?,
    val status: String?
)

data class AssignBusRequest(val busId: Long)
data class AssignCrewRequest(val crewId: Long)
data class EnabledBody(val enabled: Boolean)