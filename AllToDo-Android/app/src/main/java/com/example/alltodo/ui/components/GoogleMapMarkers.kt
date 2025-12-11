package com.example.alltodo.ui.components

import androidx.compose.runtime.Composable
import com.example.alltodo.data.TodoItem
import com.example.alltodo.data.UserLog
import com.example.alltodo.ui.UnifiedItem
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun GoogleMapMarkers(
    items: List<UnifiedItem>,
    onItemClick: (UnifiedItem) -> Unit
) {
    // [FIX] Rendering based on unified filtered items to match Kakao logic
    items.forEach { uiItem ->
        val position = LatLng(uiItem.latitude, uiItem.longitude)
        
        when (uiItem) {
            is UnifiedItem.Todo -> {
                Marker(
                    state = MarkerState(position = position),
                    title = uiItem.item.text,
                    onClick = {
                        onItemClick(uiItem)
                        true
                    },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }
            is UnifiedItem.History -> {
                Marker(
                    state = MarkerState(position = position),
                    title = "Log: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(uiItem.timestamp)),
                    onClick = {
                        onItemClick(uiItem)
                        true
                    },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }
            is UnifiedItem.CurrentLocation -> {
                Marker(
                    state = MarkerState(position = position),
                    title = "Me",
                    onClick = {
                        // Optional: Show detail or just ignored
                        false
                    },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }
    }
}
