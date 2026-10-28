package com.chrisz.zfrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisz.zfrelay.ui.MainViewModel
import com.chrisz.zfrelay.ui.ZfRelayApp
import com.chrisz.zfrelay.ui.ZfRelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZfRelayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val model: MainViewModel = viewModel()
                    ZfRelayApp(model)
                }
            }
        }
    }
}
