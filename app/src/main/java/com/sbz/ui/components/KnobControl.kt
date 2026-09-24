package com.sbz.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Precision rotary knob control with rotational sweep and direct touch dragging.
 */
@Composable
fun KnobControl(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    unit: String = "",
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SbzCyan
) {
    val norm = remember(value, range) {
        ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    }

    val displayValue = remember(value) {
        if (value > 0f) "+%.1f".format(value) else "%.1f".format(value)
    }

    Column(
        modifier = modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            color = SbzTextSecondary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .pointerInput(range) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val span = range.endInclusive - range.start
                        val delta = -(dragAmount / 150f) * span
                        val newValue = (value + delta).coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 4f

                val startAngle = 135f
                val sweepTotal = 270f
                val currentSweep = sweepTotal * norm

                // Background Track Arc
                drawArc(
                    color = SbzSurfaceVariant,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Active Arc
                if (currentSweep > 1f) {
                    drawArc(
                        color = activeColor,
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Dial Knob
                val innerRadius = radius - 8.dp.toPx()
                drawCircle(
                    color = SbzCardBg,
                    radius = innerRadius,
                    center = center
                )
                drawCircle(
                    color = SbzBorder,
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Pointer indicator needle
                val angleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                val needleStart = Offset(
                    (center.x + (innerRadius * 0.4f * cos(angleRad))).toFloat(),
                    (center.y + (innerRadius * 0.4f * sin(angleRad))).toFloat()
                )
                val needleEnd = Offset(
                    (center.x + (innerRadius * 0.9f * cos(angleRad))).toFloat(),
                    (center.y + (innerRadius * 0.9f * sin(angleRad))).toFloat()
                )
                drawLine(
                    color = Color.White,
                    start = needleStart,
                    end = needleEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$displayValue$unit",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = SbzTextPrimary,
            maxLines = 1
        )
    }
}
