package com.kabus.crew

import android.app.Application
import com.kabus.crew.data.ApiService
import com.kabus.crew.data.Session
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KaBusCrewApp : Application() {

    lateinit var session: Session
        private set
    lateinit var api: ApiService
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(ApiService::class.java)
    }
}