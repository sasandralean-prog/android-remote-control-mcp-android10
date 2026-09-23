package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.os.Build

/** API 33-only accessibility operations kept out of classes loaded on Android 10. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal object Api33AccessibilityCompat {
    fun clearFrameworkCache(service: AccessibilityService) {
        service.clearCache()
    }
}
