package com.kabus.admin.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/admin/login")
    suspend fun adminLogin(@Body request: AdminLoginRequest): LoginResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(@Header("Authorization") auth: String): LoginResponse

    @GET("api/admin/dashboard")
    suspend fun dashboard(@Header("Authorization") auth: String): DashboardResponse

    @GET("api/admin/trips")
    suspend fun trips(
        @Header("Authorization") auth: String,
        @Query("date") date: String?,
        @Query("status") status: String?,
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<TripItem>

    @POST("api/admin/trips")
    suspend fun createTrip(
        @Header("Authorization") auth: String,
        @Body request: CreateTripRequestBody
    ): TripItem

    @PATCH("api/admin/trips/{id}/status")
    suspend fun setTripStatus(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("status") status: String
    ): TripItem

    @PUT("api/admin/trips/{id}/bus")
    suspend fun assignTripBus(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: AssignBusRequest
    ): TripItem

    @POST("api/admin/trips/{id}/crew")
    suspend fun assignTripCrew(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: AssignCrewRequest
    ): TripItem

    @GET("api/admin/live-buses")
    suspend fun liveBuses(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int
    ): List<LiveBusItem>

    @GET("api/admin/crew")
    suspend fun crew(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int
    ): List<CrewItem>

    @GET("api/admin/notifications")
    suspend fun notifications(
        @Header("Authorization") auth: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<NotificationItem>

    @PATCH("api/admin/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): Response<Unit>

    @GET("api/public/ad/serve")
    suspend fun serveAd(@Query("placement") placement: String): ServeAdResponse

    @POST("api/public/ad/impression")
    suspend fun recordImpression(@Body request: ImpressionRequest): Response<Unit>

    // ------------------------------------------------------------------
    // Organization hierarchy (Corporation -> Division -> Depot -> Town)
    // SUPER_ADMIN-only mutations; list is SUPER_ADMIN-scoped.
    // ------------------------------------------------------------------

    @GET("api/admin/organizations/corporations")
    suspend fun corporations(@Header("Authorization") auth: String): List<CorporationResponse>

    @POST("api/admin/organizations/corporations")
    suspend fun createCorporation(
        @Header("Authorization") auth: String,
        @Body request: CorporationRequest
    ): CorporationResponse

    @PUT("api/admin/organizations/corporations/{id}")
    suspend fun updateCorporation(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: CorporationRequest
    ): CorporationResponse

    @PATCH("api/admin/organizations/corporations/{id}/enabled")
    suspend fun toggleCorporation(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): CorporationResponse

    @GET("api/admin/organizations/divisions")
    suspend fun divisions(
        @Header("Authorization") auth: String,
        @Query("parentId") parentId: Long
    ): List<DivisionResponse>

    @POST("api/admin/organizations/divisions")
    suspend fun createDivision(
        @Header("Authorization") auth: String,
        @Body request: DivisionRequest
    ): DivisionResponse

    @PATCH("api/admin/organizations/divisions/{id}/enabled")
    suspend fun toggleDivision(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): DivisionResponse

    @GET("api/admin/organizations/depots")
    suspend fun depots(
        @Header("Authorization") auth: String,
        @Query("parentId") parentId: Long
    ): List<DepotResponse>

    @POST("api/admin/organizations/depots")
    suspend fun createDepot(
        @Header("Authorization") auth: String,
        @Body request: DepotRequest
    ): DepotResponse

    @PATCH("api/admin/organizations/depots/{id}/enabled")
    suspend fun toggleDepot(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): DepotResponse

    @GET("api/admin/organizations/towns")
    suspend fun towns(
        @Header("Authorization") auth: String,
        @Query("parentId") parentId: Long
    ): List<TownResponse>

    @POST("api/admin/organizations/towns")
    suspend fun createTown(
        @Header("Authorization") auth: String,
        @Body request: TownRequest
    ): TownResponse

    @PATCH("api/admin/organizations/towns/{id}/enabled")
    suspend fun toggleTown(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): TownResponse

    // ------------------------------------------------------------------
    // Fleet (buses)
    // ------------------------------------------------------------------

    @GET("api/admin/fleet/buses")
    suspend fun buses(
        @Header("Authorization") auth: String,
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<BusItem>

    @POST("api/admin/fleet/buses")
    suspend fun createBus(
        @Header("Authorization") auth: String,
        @Body request: CreateBusRequest
    ): BusItem

    @PUT("api/admin/fleet/buses/{id}")
    suspend fun updateBus(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: UpdateBusRequest
    ): BusItem

    @PATCH("api/admin/fleet/buses/{id}/enabled")
    suspend fun toggleBus(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): BusItem

    // ------------------------------------------------------------------
    // Staff
    // ------------------------------------------------------------------

    @GET("api/admin/staff")
    suspend fun staff(
        @Header("Authorization") auth: String,
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<StaffItem>

    @POST("api/admin/staff")
    suspend fun createStaff(
        @Header("Authorization") auth: String,
        @Body request: CreateStaffRequest
    ): StaffItem

    @PUT("api/admin/staff/{id}")
    suspend fun updateStaff(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: UpdateStaffRequest
    ): StaffItem

    @PATCH("api/admin/staff/{id}/status")
    suspend fun setStaffStatus(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("status") status: String
    ): StaffItem

    // ------------------------------------------------------------------
    // Routes + stops
    // ------------------------------------------------------------------

    @GET("api/admin/routes")
    suspend fun routes(
        @Header("Authorization") auth: String,
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<RouteItem>

    @POST("api/admin/routes")
    suspend fun createRoute(
        @Header("Authorization") auth: String,
        @Body request: CreateRouteRequest
    ): RouteItem

    @PUT("api/admin/routes/{id}")
    suspend fun updateRoute(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: UpdateRouteRequest
    ): RouteItem

    @PUT("api/admin/routes/{id}/stops")
    suspend fun replaceRouteStops(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: ReplaceStopsRequest
    ): RouteItem

    @PATCH("api/admin/routes/{id}/enabled")
    suspend fun toggleRoute(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): RouteItem

    // ------------------------------------------------------------------
    // Admin users (SUPER_ADMIN only)
    // ------------------------------------------------------------------

    @GET("api/admin/users")
    suspend fun adminUsers(
        @Header("Authorization") auth: String,
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<AdminUserItem>

    @POST("api/admin/users")
    suspend fun createAdminUser(
        @Header("Authorization") auth: String,
        @Body request: CreateUserRequest
    ): AdminUserItem

    @PATCH("api/admin/users/{id}/enabled")
    suspend fun toggleUser(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("enabled") enabled: Boolean
    ): AdminUserItem

    @PATCH("api/admin/users/{id}/role")
    suspend fun changeUserRole(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: ChangeRoleRequest
    ): AdminUserItem

    @PATCH("api/admin/users/{id}/password")
    suspend fun changeUserPassword(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body request: ChangePasswordRequest
    ): AdminUserItem

    // ------------------------------------------------------------------
    // Audit logs (SUPER_ADMIN only)
    // ------------------------------------------------------------------

    @GET("api/admin/audit-logs")
    suspend fun auditLogs(
        @Header("Authorization") auth: String,
        @Query("action") action: String?,
        @Query("resourceType") resourceType: String?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<AuditItem>

    // ------------------------------------------------------------------
    // Settings (SUPER_ADMIN only)
    // ------------------------------------------------------------------

    @GET("api/admin/settings")
    suspend fun settings(@Header("Authorization") auth: String): List<SettingItem>

    @PUT("api/admin/settings/{key}")
    suspend fun updateSetting(
        @Header("Authorization") auth: String,
        @Path("key") key: String,
        @Body request: UpdateSettingRequest
    ): SettingItem
}