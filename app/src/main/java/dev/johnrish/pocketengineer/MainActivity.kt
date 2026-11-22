package dev.johnrish.pocketengineer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.johnrish.pocketengineer.ui.AirFiteApp
import dev.johnrish.pocketengineer.ui.theme.AirFiteTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DESTINATION = "airfite_destination"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme is dark-only, so keep light system-bar icons regardless of the device setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent { AirFiteTheme { AirFiteApp() } }
    }
}
