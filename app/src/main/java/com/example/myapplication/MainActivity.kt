package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() можно оставить, если хотите, но для простоты уберем
        setContent {
            MyApplicationTheme(darkTheme = false) { // Принудительно светлая тема
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ВЫЗЫВАЕМ НАШ ЭКРАН С КЛЕТОЧНОЙ ДОСКОЙ
                    SchemeScreen()
                }
            }
        }
    }
}