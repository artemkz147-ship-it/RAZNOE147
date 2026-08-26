package ru.vibecut.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF09090C),
                    surface = Color(0xFF15151A),
                    primary = Color(0xFF8B5CF6),
                    secondary = Color(0xFF22D3EE),
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoEditorScreen()
                }
            }
        }
    }
}
