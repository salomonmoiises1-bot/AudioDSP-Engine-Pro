package com.sbz.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sbz.dsp.model.DspConfig
import com.sbz.ui.theme.*
import kotlin.math.*

/**
 * High-definition Real-time Frequency Response Curve Visualizer.
 * Plots the calculated cumulative dB response across 20 Hz – 20 kHz log spectrum.
 */
@Composable
fun EqCurveVisualizer(
    eqGains: List<Float>,
    toneBassDb: Float = 0f,
    toneMidDb: Float = 0f,
    toneTrebleDb: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SbzSurface)
            .border(1.dp, SbzBorder, RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val width = size.width
            val height = size.height
            val minFreq = 20.0
            val maxFreq = 20000.0
            val logMin = log10(minFreq)
            val logMax = log10(maxFreq)

            val minDb = -18.0f
            val maxDb = 18.0f

            fun freqToX(freq: Double): Float {
                val norm = (log10(freq) - logMin) / (logMax - logMin)
                return (norm * width).toFloat().coerceIn(0f, width)
            }

            fun dbToY(db: Float): Float {
                val norm = (maxDb - db) / (maxDb - minDb)
                return (norm * height).coerceIn(0f, height)
            }

            // Grid Lines (dB markers)
            val dbMarks = floatArrayOf(-12f, -6f, 0f, 6f, 12f)
            for (db in dbMarks) {
                val y = dbToY(db)
                val isZero = db == 0f
                drawLine(
                    color = if (isZero) SbzCyan.copy(alpha = 0.35f) else SbzBorder.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (isZero) 1.5f else 1.0f
                )
            }

            // Frequency Grid Vertical Lines (50, 100, 500, 1k, 5k, 10k, 20k)
            val freqMarkers = doubleArrayOf(50.0, 100.0, 500.0, 1000.0, 5000.0, 10000.0)
            for (f in freqMarkers) {
                val x = freqToX(f)
                drawLine(
                    color = SbzBorder.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }

            // Calculate Frequency Curve Points
            val sampleCount = 120
            val curvePoints = ArrayList<Offset>(sampleCount)
            val fillPath = Path()

            for (i in 0 until sampleCount) {
                val normX = i.toFloat() / (sampleCount - 1).toFloat()
                val logFreq = logMin + normX * (logMax - logMin)
                val freq = 10.0.pow(logFreq)

                // Sum contribution of all 32 bands
                var totalGainDb = 0.0f
                for (b in 0 until min(DspConfig.FREQUENCIES.size, eqGains.size)) {
                    val bandFreq = DspConfig.FREQUENCIES[b].toDouble()
                    val bandGain = eqGains[b]
                    if (bandGain != 0.0f) {
                        // Octave distance for peaking filter bell
                        val octDist = abs(log2(freq / bandFreq))
                        val q = 3.5
                        val factor = 1.0 / (1.0 + (octDist * q).pow(2.0))
                        totalGainDb += (bandGain * factor).toFloat()
                    }
                }

                // Add Tone contributions
                if (freq <= 300.0) {
                    val factor = (1.0 - (freq / 300.0)).coerceIn(0.0, 1.0)
                    totalGainDb += (toneBassDb * factor).toFloat()
                } else if (freq in 300.0..3500.0) {
                    val midCenter = 1000.0
                    val oct = abs(log2(freq / midCenter))
                    val factor = (1.0 - (oct / 1.8)).coerceIn(0.0, 1.0)
                    totalGainDb += (toneMidDb * factor).toFloat()
                } else if (freq >= 3500.0) {
                    val factor = ((freq - 3500.0) / 16500.0).coerceIn(0.0, 1.0)
                    totalGainDb += (toneTrebleDb * factor).toFloat()
                }

                val x = freqToX(freq)
                val y = dbToY(totalGainDb.coerceIn(minDb, maxDb))
                curvePoints.add(Offset(x, y))

                if (i == 0) {
                    fillPath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                }
            }

            // Draw Area Fill under curve
            val zeroY = dbToY(0f)
            val closedFillPath = Path().apply {
                addPath(fillPath)
                lineTo(curvePoints.last().x, zeroY)
                lineTo(curvePoints.first().x, zeroY)
                close()
            }

            drawPath(
                path = closedFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SbzCyan.copy(alpha = 0.25f),
                        SbzCyan.copy(alpha = 0.02f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw Smooth Curve Stroke
            val strokePath = Path()
            for (i in curvePoints.indices) {
                if (i == 0) {
                    strokePath.moveTo(curvePoints[i].x, curvePoints[i].y)
                } else {
                    val p0 = curvePoints[i - 1]
                    val p1 = curvePoints[i]
                    val midX = (p0.x + p1.x) / 2f
                    val midY = (p0.y + p1.y) / 2f
                    strokePath.quadraticTo(p0.x, p0.y, midX, midY)
                }
            }
            if (curvePoints.isNotEmpty()) {
                strokePath.lineTo(curvePoints.last().x, curvePoints.last().y)
            }

            drawPath(
                path = strokePath,
                color = SbzCyan,
                style = Stroke(width = 2.5f)
            )
        }
    }
}
