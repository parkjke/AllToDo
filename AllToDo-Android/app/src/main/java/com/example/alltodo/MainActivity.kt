package com.example.alltodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.alltodo.ui.theme.AllToDoTheme
import com.example.alltodo.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [FIX] Explicitly initialize Naver Map SDK to resolve auth issues
        // [FIX] Explicitly initialize Naver Map SDK to resolve auth issues
        try {
            val sdk = com.naver.maps.map.NaverMapSdk.getInstance(this)
            sdk.client = com.naver.maps.map.NaverMapSdk.NaverCloudPlatformClient("i7652syq10")
            
            // [DEBUG] Log Package Name to verify match with Console
            android.util.Log.e("AllToDo", "Current Application ID (Package Name): " + applicationContext.packageName)
            
            sdk.onAuthFailedListener = com.naver.maps.map.NaverMapSdk.OnAuthFailedListener { ex ->
                 android.util.Log.e("AllToDo", "Naver Map Auth Failed: " + ex.message)
                 android.widget.Toast.makeText(this, "Naver Auth Failed: " + ex.message, android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            AllToDoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
