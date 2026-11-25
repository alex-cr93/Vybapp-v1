package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.example.data.model.PrivateLocationInfo
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class LocationHelper(private val context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun fetchPrivateLocation(): Result<PrivateLocationInfo> = withContext(Dispatchers.IO) {
        try {
            val location = suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last -> cont.resume(last) }
                                .addOnFailureListener { cont.resume(null) }
                        }
                    }
                    .addOnFailureListener { cont.resume(null) }
            }

            if (location == null) {
                return@withContext Result.failure(
                    IllegalStateException("No se pudo obtener la ubicación actual del dispositivo.")
                )
            }

            val areaLabel = reverseGeocodeArea(location.latitude, location.longitude)
            val suggestions = buildLocalSuggestions(areaLabel)

            Result.success(
                PrivateLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    areaLabel = areaLabel,
                    suggestions = suggestions
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun observeLiveLocationUpdates(intervalMs: Long = 5000L): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs.coerceAtLeast(2500L))
            .setMinUpdateIntervalMillis(2500L)
            .setMinUpdateDistanceMeters(3f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.lastLocation.addOnSuccessListener { last ->
            if (last != null) trySend(last)
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeArea(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale("es", "CR"))
            val results = geocoder.getFromLocation(lat, lon, 1)
            val addr = results?.firstOrNull()
            val parts = listOfNotNull(
                addr?.locality ?: addr?.subAdminArea,
                addr?.adminArea,
                addr?.countryName
            ).distinct()
            if (parts.isNotEmpty()) parts.joinToString(", ")
            else String.format(Locale.US, "%.3f, %.3f", lat, lon)
        } catch (_: Exception) {
            String.format(Locale.US, "%.3f, %.3f", lat, lon)
        }
    }

    private fun buildLocalSuggestions(areaLabel: String): List<String> = listOf(
        "Cafeterías y espacios de trabajo cerca de $areaLabel",
        "Restaurantes populares con SINPE Móvil en $areaLabel",
        "Eventos culturales y parques en $areaLabel"
    )

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMetersBetween(
            lat1: Double,
            lng1: Double,
            lat2: Double,
            lng2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
            return EARTH_RADIUS_METERS * c
        }

        fun formatDistanceLabel(meters: Double): String {
            return when {
                meters < 15.0 -> "Aquí mismo"
                meters < 1000.0 -> "A ${meters.roundToInt()} m"
                else -> String.format(Locale("es", "CR"), "A %.1f km", meters / 1000.0)
            }
        }
    }
}

