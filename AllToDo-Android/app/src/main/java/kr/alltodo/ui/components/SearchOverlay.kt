package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.alltodo.services.SearchResult
import kr.alltodo.ui.MapProvider
import kr.alltodo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    query: String,
    results: List<SearchResult>,
    isSearching: Boolean,
    errorMessage: String?,
    mapProvider: MapProvider = MapProvider.Google, // [NEW] Theme reference
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceClick: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    // [NEW] Provider-aware theme logic (iOS parity)
    val isSystemDark = isSystemInDarkTheme()
    val isDark = remember(mapProvider, isSystemDark) {
        if (mapProvider == MapProvider.Google) isSystemDark else false
    }
    
    val focusRequester = remember { FocusRequester() }
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 1. Trigger Flash Animation (0.3s)
        isFlashing = true
        delay(300)
        isFlashing = false
        
        // 2. Request Focus (slight delay for reliable keyboard appearance)
        delay(100)
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .width(300.dp)
            .drawWithContent {
                drawContent()
                if (isFlashing) {
                    drawRect(
                        color = Color.White,
                        blendMode = BlendMode.Difference
                    )
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Search.background(isDark))
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(
                    0.0f to AppColors.Search.borderGradientTop(isDark),
                    0.416f to Gray6.copy(alpha = 0.2f),
                    1.0f to AppColors.Search.borderGradientTop(isDark)
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(2.dp), // Space for border
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Search Bar Area
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = AppColors.Search.searchBarTint(isDark),
                fontSize = 18.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(8.dp)
                .background(AppColors.TodoLayer.inputBackground(isDark), RoundedCornerShape(8.dp)),
            singleLine = true,
            cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                autoCorrect = false,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = AppColors.Search.searchBarTint(isDark),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "찾을 곳",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Search.searchBarPlaceholder(isDark),
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    }
                    
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "지우기",
                                tint = AppColors.Search.searchBarTint(isDark),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "음성",
                            tint = AllToDoGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        // 2. Search Results List (Integrated)
        if (results.isNotEmpty()) {
            Divider(
                color = AppColors.Search.divider(isDark),
                thickness = 1.dp
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(results) { result ->
                    SearchResultItem(
                        result = result,
                        isDark = isDark,
                        onClick = { onResultClick(result) }
                    )
                    Divider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = AppColors.Search.divider(isDark),
                        thickness = 0.5.dp
                    )
                }
            }
        } else if (errorMessage != null) {
            Divider(color = AppColors.Search.divider(isDark))
            Text(
                errorMessage,
                color = Color.Yellow,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        } else if (isSearching) {
            Divider(color = AppColors.Search.divider(isDark))
            Text(
                "검색 중...",
                color = Gray5,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [NEW] Type-specific Icon
        val icon = if (result.isAddress) Icons.Default.Place else Icons.Default.Search
        val iconTint = if (result.isAddress) Color(0xFF4285F4) else Gray5

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (result.isAddress) "[주소] ${result.name}" else result.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Search.resultName(isDark)
            )
            Text(
                text = result.address,
                fontSize = 14.sp,
                color = AppColors.Search.resultAddress(isDark),
                maxLines = 2
            )
            if (!result.distance.isNullOrEmpty() && !result.isAddress) {
                val distInt = result.distance.toIntOrNull() ?: 0
                val displayDistance = if (distInt >= 1000) {
                    String.format("%.1fkm", distInt / 1000.0)
                } else {
                    "${distInt}m"
                }
                Text(
                    text = displayDistance,
                    fontSize = 12.sp,
                    color = AppColors.Search.distance(isDark),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
