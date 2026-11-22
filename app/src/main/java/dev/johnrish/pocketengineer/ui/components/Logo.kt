package dev.johnrish.pocketengineer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The AirFite mark: an "A" whose crossbar is the accent color. Geometry matches the launcher icon
 * (res/drawable/ic_launcher_foreground.xml) and docs/assets/airfite-logo.svg.
 */
@Composable
fun AirFiteMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
    legs: Color = MaterialTheme.colorScheme.onBackground,
    bar: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier.size(markSize)) {
        val w = this.size.width
        val strokeWidth = w * 0.11f
        val a = Path().apply {
            moveTo(w * 0.18f, w * 0.84f)
            lineTo(w * 0.50f, w * 0.16f)
            lineTo(w * 0.82f, w * 0.84f)
        }
        drawPath(a, legs, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(bar, Offset(w * 0.33f, w * 0.64f), Offset(w * 0.67f, w * 0.64f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
fun AirFiteWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AirFiteMark(markSize = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text("AirFite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    }
}
