package ru.vibecut.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF08080C),
                    surface = Color(0xFF111118),
                    surfaceVariant = Color(0xFF1A1A22),
                    primary = Color(0xFF9B7CF7),
                    onPrimary = Color.White,
                    secondary = Color(0xFF22D3EE),
                    tertiary = Color(0xFF67E8A8),
                    error = Color(0xFFFF6B7A),
                    onBackground = Color(0xFFF3F3F7),
                    onSurface = Color(0xFFF0F0F5),
                    outline = Color(0xFF343440),
                ),
                typography = Typography(
                    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
                    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
                    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
                    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
                    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp),
                    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                ),
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(8.dp),
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(22.dp),
                    extraLarge = RoundedCornerShape(28.dp),
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF08080C)) {
                    var currentProjectId by rememberSaveable { mutableStateOf<String?>(null) }
                    if (currentProjectId == null) {
                        ProjectBrowserScreen(onOpen = { currentProjectId = it })
                    } else {
                        VideoEditorScreen(projectId = currentProjectId!!, onBack = { currentProjectId = null })
                    }
                }
            }
        }
    }
}
