package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.MapsConfigHelper
import com.example.data.model.PrivateLocationInfo
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybMonoTabularStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@Composable
fun VybMapsPanel(
    locationInfo: PrivateLocationInfo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val hasValidMapsKey = remember(context) { MapsConfigHelper.hasValidMapsApiKey(context) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = vybColors.subtleSurface),
        border = BorderStroke(1.dp, vybColors.hairlineBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Ubicación privada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = locationInfo?.areaLabel ?: "San José, Costa Rica",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (locationInfo != null) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Coordenadas locales: %.4f, %.4f",
                        locationInfo.latitude,
                        locationInfo.longitude
                    ),
                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                    color = vybColors.secondaryText
                )
            }

            if (hasValidMapsKey && locationInfo != null) {
                val target = LatLng(locationInfo.latitude, locationInfo.longitude)
                val cameraState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(target, 14f)
                }
                LaunchedEffect(target) {
                    cameraState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = true)
                    ) {
                        Marker(
                            state = MarkerState(position = target),
                            title = "Tu zona privada",
                            snippet = locationInfo.areaLabel
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vybColors.nestedSurface,
                    border = BorderStroke(1.dp, vybColors.hairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "Mapa",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (!hasValidMapsKey) {
                                "Configura MAPS_API_KEY en el panel Secrets (y en GitHub Secrets si compilas el APK vía CI) para visualizar el mapa interactivo de Google Maps."
                            } else {
                                "Activa tu ubicación privada para centrar el mapa en tu zona sin compartirla públicamente."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }
                }
            }

            if (locationInfo != null && locationInfo.suggestions.isNotEmpty()) {
                Text(
                    text = "Recomendaciones en tu zona (solo para ti):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                locationInfo.suggestions.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )
                }
            }
        }
    }
}
