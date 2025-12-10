package com.example.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alltodo.ui.theme.AllToDoGreen

@OptIn(ExperimentalMaterial3Api::class) // [FIX]
@Composable
fun UserProfileView(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    maxPopupItems: Int,
    onMaxItemsChange: (Int) -> Unit,
    popupFontSize: Int,
    onFontSizeChange: (Int) -> Unit
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Info",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AllToDoGreen
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
                    .padding(16.dp),
                tint = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info Fields (Read Only for now)
            OutlinedTextField(
                value = "User 1234",
                onValueChange = {},
                label = { Text("Nickname") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "010-1234-5678",
                onValueChange = {},
                label = { Text("Phone Number") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Settings
            Text("Settings", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            // Max Items Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Max Popup Items: $maxPopupItems")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (maxPopupItems > 1) onMaxItemsChange(maxPopupItems - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    IconButton(onClick = { if (maxPopupItems < 10) onMaxItemsChange(maxPopupItems + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Font Size
            Text("Font Size", modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = popupFontSize == 0,
                    onClick = { onFontSizeChange(0) },
                    label = { Text("Small") }
                )
                FilterChip(
                    selected = popupFontSize == 1,
                    onClick = { onFontSizeChange(1) },
                    label = { Text("Medium") }
                )
                FilterChip(
                    selected = popupFontSize == 2,
                    onClick = { onFontSizeChange(2) },
                    label = { Text("Large") }
                )
            }
        }
    }
}
