package com.kabus.crew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kabus.crew.ui.HomeScreen
import com.kabus.crew.ui.LoginScreen
import com.kabus.crew.ui.theme.KaBusCrewTheme

class MainActivity : ComponentActivity() {

    private val app: KaBusCrewApp by lazy { application as KaBusCrewApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaBusCrewTheme {
                var loggedIn by remember { mutableStateOf(app.session.accessToken != null) }

                if (!loggedIn) {
                    LoginScreen(app) { loggedIn = true }
                } else {
                    // Runtime permissions for the foreground location service.
                    val needed = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.filterNot {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                    }

                    var permissionAsked by remember { mutableStateOf(false) }
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissionAsked = true }

                    // Launch after composition, once the Activity has resumed,
                    // otherwise the activity-result launcher is not initialized.
                    LaunchedEffect(needed, permissionAsked) {
                        if (needed.isNotEmpty() && !permissionAsked) {
                            launcher.launch(needed.toTypedArray())
                        }
                    }

                    HomeScreen(app) { loggedIn = false }
                }
            }
        }
    }
}