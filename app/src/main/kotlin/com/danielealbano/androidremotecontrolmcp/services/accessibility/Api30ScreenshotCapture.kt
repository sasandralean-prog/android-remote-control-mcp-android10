package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Isolates Android 11 screenshot framework references from the API 29-loadable
 * accessibility service class.
 */
@TargetApi(Build.VERSION_CODES.R)
internal object Api30ScreenshotCapture {
    suspend fun capture(
        service: AccessibilityService,
        timeoutMs: Long,
    ): Bitmap? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val executor = Executor { it.run() }
                val callback =
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap =
                                Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace,
                                )
                            screenshot.hardwareBuffer.close()
                            if (continuation.isActive) {
                                continuation.resume(bitmap)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Screenshot failed with error code: $errorCode")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }
        }

    private const val TAG = "MCP:Api30Screenshot"
}
