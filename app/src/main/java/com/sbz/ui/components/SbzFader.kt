package com.sbz.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.ui.theme.*
import kotlin.math.roundToInt

/**
 * Professional tactile vertical audio fader.
 *
 * Designed with strict touch isolation: consuming pointer drag events prevents
 * accidental parent container scrolling.
 *
 * Range: -15.0 dB to +15.0 dB
 * Step: 0.5 dB
 * Double-tap: Reset to 0.0 dB
 */
@Composable
fun SbzFader(
    gainDb: Float,
    frequencyHz: Float,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minGainDb: Float = -15.0f,
    maxGainDb: Float = 15.0f,
    stepDb: Float = 0.5f
) {
    val formattedFreq = remember(frequencyHz) {
        if (frequencyHz >= 1000f) {
            val k = frequencyHz / 1000f
            if (k % 1.0f == 0f) "${k.toInt()}k" else String.format("%.1fk", k)
        } else {
            if (frequencyHz % 1.0f == 0f) frequencyHz.toInt().toString() else String.format("%.1f", frequencyHz)
        }
    }

    val formattedGain = remember(gainDb) {
        if (gainDb > 0f) "+%.1f".format(gainDb) else "%.1f".format(gainDb)
    }

    val gainColor = when {
        gainDb > 0.0f -> SbzCyan
        gainDb < 0.0f -> SbzAmber
        else -> SbzTextSecondary
    }

    Column(
        modifier = modifier
            .width(52.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Value Readout
        Text(
            text = formattedGain,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = gainColor,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Tactile Fader Track & Thumb Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .width(44.dp)
                .pointerInput(Unit) {
                    // Double tap to zero out fader
                    detectTapGestures(
                        onDoubleTap = {
                            onGainChanged(0.0f)
                        },
                        onTap = { offset ->
                            val height = size.height.toFloat()
                            val thumbMargin = 24f
                            val effectiveHeight = height - (thumbMargin * 2f)
                            val normalizedY = ((offset.y - thumbMargin) / effectiveHeight).coerceIn(0f, 1f)
                            // Inverted: top is maxGain, bottom is minGain
                            val rawGain = maxGainDb - normalizedY * (maxGainDb - minGainDb)
                            val stepped = (rawGain / stepDb).roundToInt() * stepDb
                            onGainChanged(stepped.coerceIn(minGainDb, maxGainDb))
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Vertical drag with full pointer consumption to avoid parent scroll stealing
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val height = size.height.toFloat()
                            val thumbMargin = 24f
                            val effectiveHeight = height - (thumbMargin * 2f)
                            val deltaGain = -(dragAmount / effectiveHeight) * (maxGainDb - minGainDb)
                            val newGain = (gainDb + deltaGain).coerceIn(minGainDb, maxGainDb)
                            val stepped = (newGain / stepDb).roundToInt() * stepDb
                            onGainChanged(stepped.coerceIn(minGainDb, maxGainDb))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val thumbMargin = 24f
                val effectiveHeight = canvasHeight - (thumbMargin * 2f)

                // Track Slot
                val trackWidth = 8f
                val trackX = (canvasWidth - trackWidth) / 2f
                drawRoundRect(
                    color = SbzFaderTrack,
                    topLeft = Offset(trackX, thumbMargin),
                    size = Size(trackWidth, effectiveHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Center 0 dB Detent Notch
                val zeroY = thumbMargin + effectiveHeight * 0.5f
                drawLine(
                    color = SbzBorder,
                    start = Offset(4f, zeroY),
                    end = Offset(canvasWidth - 4f, zeroY),
                    strokeWidth = 2f
                )

                // Graduation Marks (-15, -10, -5, 0, +5, +10, +15)
                val marks = floatArrayOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)
                for (mark in marks) {
                    val normY = (maxGainDb - mark) / (maxGainDb - minGainDb)
                    val markY = thumbMargin + effectiveHeight * normY
                    val markWidth = if (mark == 0f) 12f else 6f
                    drawLine(
                        color = if (mark == 0f) SbzCyan.copy(alpha = 0.5f) else SbzTextDisabled.copy(alpha = 0.4f),
                        start = Offset(trackX - markWidth - 2f, markY),
                        end = Offset(trackX - 2f, markY),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = if (mark == 0f) SbzCyan.copy(alpha = 0.5f) else SbzTextDisabled.copy(alpha = 0.4f),
                        start = Offset(trackX + trackWidth + 2f, markY),
                        end = Offset(trackX + trackWidth + markWidth + 2f, markY),
                        strokeWidth = 1.5f
                    )
                }

                // Active Gain Level Fill
                val currentNormY = (maxGainDb - gainDb) / (maxGainDb - minGainDb)
                val currentThumbY = thumbMargin + effectiveHeight * currentNormY

                if (gainDb != 0f) {
                    val fillTop = minOf(zeroY, currentThumbY)
                    val fillHeight = kotlin.math.abs(zeroY - currentThumbY)
                    drawRect(
                        color = if (gainDb > 0f) SbzCyan.copy(alpha = 0.6f) else SbzAmber.copy(alpha = 0.6f),
                        topLeft = Offset(trackX + 1.5f, fillTop),
                        size = Size(trackWidth - 3f, fillHeight)
                    )
                }

                // Physical Slider Thumb Cap
                val thumbWidth = 38f
                val thumbHeight = 22f
                val thumbX = (canvasWidth - thumbWidth) / 2f
                val thumbTop = currentThumbY - (thumbHeight / 2f)

                // Thumb Drop Shadow / Outer Border
                drawRoundRect(
                    color = SbzSurfaceVariant,
                    topLeft = Offset(thumbX, thumbTop),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = if (gainDb != 0f) gainColor else SbzBorder,
                    topLeft = Offset(thumbX, thumbTop),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )

                // Thumb Center Position Marker Line
                drawLine(
                    color = if (gainDb != 0f) gainColor else Color.White,
                    start = Offset(thumbX + 4f, currentThumbY),
                    end = Offset(thumbX + thumbWidth - 4f, currentThumbY),
                    strokeWidth = 2.5f
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frequency Legend
        Text(
            text = formattedFreq,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = SbzTextPrimary,
            maxLines = 1
        )
    }
}
