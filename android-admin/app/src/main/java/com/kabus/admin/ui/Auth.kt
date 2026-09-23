package com.kabus.admin.ui

import com.kabus.admin.KaBusAdminApp

internal fun KaBusAdminApp.auth(): String = "Bearer ${session.accessToken}"