package com.danielealbano.androidremotecontrolmcp.services.location

import android.annotation.TargetApi
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal object Api33ReverseGeocoder {
    suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): String? =
        suspendCancellableCoroutine { cont ->
            Geocoder(context, Locale.getDefault()).getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: List<Address>) {
                        if (cont.isActive) {
                            cont.resume(addresses.firstOrNull()?.getAddressLine(0))
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        Log.d(TAG, "Geocoder onError: $errorMessage")
                        if (cont.isActive) {
                            cont.resume(null)
                        }
                    }
                },
            )
        }

    private const val TAG = "MCP:Api33ReverseGeocoder"
}
