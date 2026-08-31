package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.FirebaseManager
import com.example.ui.NyayaApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.NyayaViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      FirebaseManager.init(applicationContext)
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "FirebaseManager initialization error: ${e.message}", e)
    }
    
    try {
      enableEdgeToEdge()
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "enableEdgeToEdge error: ${e.message}", e)
    }

    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        val viewModel: NyayaViewModel = viewModel()
        NyayaApp(viewModel = viewModel)
      }
    }
  }
}
