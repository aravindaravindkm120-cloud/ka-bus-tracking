package com.kabus.admin

import android.app.Application
import com.kabus.admin.data.ApiService
import com.kabus.admin.data.Session
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KaBusAdminApp : Application() {

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

    companion object {
        const val TAG = "KaBusAdmin"
    }
}