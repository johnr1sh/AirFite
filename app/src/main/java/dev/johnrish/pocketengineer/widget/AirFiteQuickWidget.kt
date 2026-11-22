package dev.johnrish.pocketengineer.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.Button
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.MainActivity

class AirFiteQuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Color(0xFF0D1117)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text("AirFite", style = androidx.glance.text.TextStyle(color = ColorProvider(Color(0xFFE6EDF3))))
                Text("Your mobile coding agent", style = androidx.glance.text.TextStyle(color = ColorProvider(Color(0xFF8B949E))))
                Spacer(GlanceModifier.height(12.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        text = "New chat",
                        onClick = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "Chat"))
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Button(
                        text = "Workspace",
                        onClick = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "Files"))
                    )
                }
            }
        }
    }
}

class AirFiteQuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirFiteQuickWidget()
}
