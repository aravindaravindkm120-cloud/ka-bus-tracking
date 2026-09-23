package com.kabus.crew.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("api/crew/assignment")
    suspend fun assignment(@Header("Authorization") auth: String): Assignment

    @GET("api/crew/status")
    suspend fun status(@Header("Authorization") auth: String): CrewStatus

    @POST("api/crew/gps/start")
    suspend fun gpsStart(
        @Header("Authorization") auth: String,
        @Body request: GpsStartRequest
    ): GpsStartResponse

    @POST("api/crew/gps/location")
    suspend fun gpsLocation(
        @Header("Authorization") auth: String,
        @Query("sessionKey") sessionKey: String,
        @Body request: GpsLocationRequest
    ): GpsLocationResponse

    @POST("api/crew/gps/end")
    suspend fun gpsEnd(
        @Header("Authorization") auth: String,
        @Query("sessionKey") sessionKey: String
    ): GpsEndResponse

    @GET("api/crew/notifications")
    suspend fun notifications(
        @Header("Authorization") auth: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Page<NotificationItem>

    @GET("api/crew/ad")
    suspend fun ad(): ServeAdResponse

    @POST("api/public/ad/impression")
    suspend fun recordImpression(@Body request: ImpressionRequest): Response<Unit>
}