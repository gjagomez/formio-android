package com.genesis.formio.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class GeolocationHelper(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    fun getCurrentLocation(
        onResult: (lat: Double, lng: Double) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError("Permiso de ubicación no concedido")
            return
        }

        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onResult(location.latitude, location.longitude)
                } else {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) onResult(loc.latitude, loc.longitude)
                            else onError("No se pudo obtener la ubicación")
                        }
                        .addOnFailureListener { e ->
                            onError(e.message ?: "Error de ubicación")
                        }
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Error de ubicación")
            }
    }
}
