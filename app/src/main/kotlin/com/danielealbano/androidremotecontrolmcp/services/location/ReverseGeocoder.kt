package com.danielealbano.androidremotecontrolmcp.services.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "MCP:ReverseGeocoder"

/** Framework-only (GMS-free) reverse geocoding shared by all LocationProvider implementations. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun reverseGeocode(
    context: Context,
    latitude: Double,
    longitude: Double,
): String? {
    if (!Geocoder.isPresent()) {
        Log.d(TAG, "Geocoder not present on this device")
        return null
    }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33ReverseGeocoder.reverseGeocode(context, latitude, longitude)
        } else {
            reverseGeocodeLegacy(context, latitude, longitude)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(TAG, "Reverse geocoding failed: ${e.message}")
        null
    }
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocodeLegacy(
    context: Context,
    latitude: Double,
    longitude: Double,
): String? =
    withContext(Dispatchers.IO) {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.getAddressLine(0)
    }
