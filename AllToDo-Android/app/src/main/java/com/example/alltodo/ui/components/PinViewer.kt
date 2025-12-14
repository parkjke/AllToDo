package com.example.alltodo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.alltodo.ui.PinImageManager

@Composable
fun PinViewer(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    val pins = remember(refreshTrigger) { PinImageManager.getPinList() } // Reload list if needed
    var selectedPin by remember { mutableStateOf<Pair<Int, String>?>(null) } // Reset selection on refresh

    // Initial Selection
    LaunchedEffect(pins) { if (selectedPin == null) selectedPin = pins.firstOrNull() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full Screen
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin Gallery & Debugger", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    
                    Row {
                        // Rebuild Button
                        IconButton(onClick = { 
                            PinImageManager.clearCacheAndRebuild(context)
                            refreshTrigger++
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rebuild", tint = Color.Black)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                        }
                    }
                }
                
                Divider(color = Color.LightGray)
                
                // Content Split (Top/Bottom)
                Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                    // TOP: List
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    ) {
                        items(pins) { pin ->
                            val isSelected = selectedPin == pin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFFE0F7FA) else Color.White)
                                    .clickable { selectedPin = pin }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Show Cached Bitmap if available, else SVG
                                val cached = remember(pin.first, refreshTrigger) { PinImageManager.getPinBitmap(pin.first) }
                                if (cached != null) {
                                    Image(
                                        bitmap = cached.asImageBitmap(), 
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(pin.first),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = pin.second.replace("pin_", "").replace("_v1", "").replace("_v2", ""),
                                    fontSize = 16.sp, // Increased font size
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                            }
                            Divider(color = Color.LightGray)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // BOTTOM: Detail
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        if (selectedPin != null) {
                            val pin = selectedPin!!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Original SVG (Vector)", fontWeight = FontWeight.Bold, color = Color.Black)
                                Spacer(modifier = Modifier.height(8.dp))
                                Image(
                                    painter = painterResource(pin.first),
                                    contentDescription = "SVG",
                                    modifier = Modifier.size(120.dp) // Large SVG
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Divider()
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text("Bitmap Simulations (Parity Check)", fontWeight = FontWeight.Bold, color = Color.Black)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Density Variations
                                val densities = listOf(
                                    "1.0 (mdpi)" to 1.0f,
                                    "1.5 (hdpi)" to 1.5f,
                                    "2.0 (xhdpi)" to 2.0f,
                                    "3.0 (xxhdpi)" to 3.0f,
                                    "4.0 (xxxhdpi)" to 4.0f
                                )
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    densities.forEach { (label, density) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val simulatedBitmap = remember(pin.first, density) {
                                                PinImageManager.createBitmapFromVector(context, pin.first, density)
                                            }
                                            
                                            if (simulatedBitmap != null) {
                                                Image(
                                                    bitmap = simulatedBitmap.asImageBitmap(),
                                                    contentDescription = label
                                                ) // Intrinsic size
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(label, fontSize = 12.sp, color = Color.Gray)
                                                Text(
                                                    "${simulatedBitmap.width}x${simulatedBitmap.height}px", 
                                                    fontSize = 10.sp, color = Color.Gray
                                                )
                                            } else {
                                                Text("Err", color = Color.Red)
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Currently Cached File", fontWeight = FontWeight.Bold, color = Color.Black)
                                val currentCached = PinImageManager.getPinBitmap(pin.first)
                                if (currentCached != null) {
                                    Text(
                                        "Size: ${currentCached.width}x${currentCached.height}px (Density: ${context.resources.displayMetrics.density})",
                                        color = Color.Blue
                                    )
                                    Image(bitmap = currentCached.asImageBitmap(), contentDescription = "Current")
                                } else {
                                    Text("Not cached yet (Lazy load)", color = Color.Red)
                                }
                            }
                        } else {
                            Text("Select a pin", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
