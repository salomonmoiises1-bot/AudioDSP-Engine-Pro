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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Professional tactile vertical audio fader.
 *
 * UI-only control:
 * - Range: -15.0 dB to +15.0 dB
 * - Default step: 0.5 dB
 * - Small drag movements are accumulated.
 * - Double-tap resets to 0.0 dB.
 * - Does not modify the DSP engine.
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
    val latestGainDb by rememberUpdatedState(gainDb)
    val latestOnGainChanged by rememberUpdatedState(onGainChanged)

    val safeStepDb =
        stepDb.coerceAtLeast(0.001f)

    val formattedFreq = remember(frequencyHz) {
        if (frequencyHz >= 1000f) {
            val k = frequencyHz / 1000f

            if (k % 1.0f == 0f) {
                "${k.toInt()}k"
            } else {
                String.format("%.1fk", k)
            }
        } else {
            if (frequencyHz % 1.0f == 0f) {
                frequencyHz.toInt().toString()
            } else {
                String.format("%.1f", frequencyHz)
            }
        }
    }

    val formattedGain = remember(gainDb) {
        if (gainDb > 0f) {
            "+%.1f".format(gainDb)
        } else {
            "%.1f".format(gainDb)
        }
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

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .width(44.dp)

                /*
                 * Tap and double-tap handling.
                 *
                 * Single tap moves the fader directly to the
                 * touched position.
                 *
                 * Double tap resets the fader to 0 dB.
                 */
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            latestOnGainChanged(0.0f)
                        },
                        onTap = { offset ->

                            val height =
                                size.height.toFloat()

                            val thumbMargin = 24f

                            val effectiveHeight =
                                (
                                    height -
                                        thumbMargin * 2f
                                    ).coerceAtLeast(1f)

                            val normalizedY =
                                (
                                    (offset.y -
                                        thumbMargin) /
                                        effectiveHeight
                                    ).coerceIn(
                                        0f,
                                        1f
                                    )

                            /*
                             * Top = maximum gain.
                             * Bottom = minimum gain.
                             */
                            val rawGain =
                                maxGainDb -
                                    normalizedY *
                                    (
                                        maxGainDb -
                                            minGainDb
                                        )

                            val stepped =
                                (
                                    (rawGain /
                                        safeStepDb)
                                        .roundToInt() *
                                        safeStepDb
                                    ).coerceIn(
                                        minGainDb,
                                        maxGainDb
                                    )

                            latestOnGainChanged(
                                stepped
                            )
                        }
                    )
                }

                /*
                 * Vertical drag handling.
                 *
                 * The important correction is that rawGain is kept
                 * independently from the quantized 0.5 dB output.
                 *
                 * This prevents small movements from being discarded.
                 */
                .pointerInput(Unit) {

                    var rawGain =
                        latestGainDb.coerceIn(
                            minGainDb,
                            maxGainDb
                        )

                    detectVerticalDragGestures(

                        onDragStart = {
                            rawGain =
                                latestGainDb.coerceIn(
                                    minGainDb,
                                    maxGainDb
                                )
                        },

                        onVerticalDrag = {
                                change,
                                dragAmount ->

                            change.consume()

                            val height =
                                size.height.toFloat()

                            val thumbMargin = 24f

                            val effectiveHeight =
                                (
                                    height -
                                        thumbMargin * 2f
                                    ).coerceAtLeast(1f)

                            val gainSpan =
                                maxGainDb -
                                    minGainDb

                            if (gainSpan > 0f) {

                                /*
                                 * Convert physical finger movement
                                 * into dB movement.
                                 */
                                val deltaGain =
                                    -(
                                        dragAmount /
                                            effectiveHeight
                                        ) *
                                        gainSpan

                                /*
                                 * Accumulate the unrounded value.
                                 */
                                rawGain =
                                    (
                                        rawGain +
                                            deltaGain
                                        ).coerceIn(
                                            minGainDb,
                                            maxGainDb
                                        )

                                /*
                                 * Quantize only the value sent
                                 * to the application state.
                                 */
                                val stepped =
                                    (
                                        (
                                            rawGain /
                                                safeStepDb
                                            ).roundToInt() *
                                            safeStepDb
                                        ).coerceIn(
                                            minGainDb,
                                            maxGainDb
                                        )

                                latestOnGainChanged(
                                    stepped
                                )
                            }
                        }
                    )
                }
        ) {

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {

                val canvasWidth = size.width
                val canvasHeight = size.height

                val thumbMargin = 24f

                val effectiveHeight =
                    (
                        canvasHeight -
                            thumbMargin * 2f
                        ).coerceAtLeast(1f)

                // Track Slot
                val trackWidth = 8f

                val trackX =
                    (canvasWidth -
                        trackWidth) / 2f

                drawRoundRect(
                    color = SbzFaderTrack,
                    topLeft = Offset(
                        trackX,
                        thumbMargin
                    ),
                    size = Size(
                        trackWidth,
                        effectiveHeight
                    ),
                    cornerRadius = CornerRadius(
                        4f,
                        4f
                    )
                )

                // Center 0 dB Detent Notch
                val zeroY =
                    thumbMargin +
                        effectiveHeight * 0.5f

                drawLine(
                    color = SbzBorder,
                    start = Offset(
                        4f,
                        zeroY
                    ),
                    end = Offset(
                        canvasWidth - 4f,
                        zeroY
                    ),
                    strokeWidth = 2f
                )

                // Graduation Marks
                val marks =
                    floatArrayOf(
                        -15f,
                        -10f,
                        -5f,
                        0f,
                        5f,
                        10f,
                        15f
                    )

                for (mark in marks) {

                    val normY =
                        (
                            maxGainDb -
                                mark
                            ) /
                            (
                                maxGainDb -
                                    minGainDb
                                )

                    val markY =
                        thumbMargin +
                            effectiveHeight *
                            normY

                    val markWidth =
                        if (mark == 0f) {
                            12f
                        } else {
                            6f
                        }

                    drawLine(
                        color =
                            if (mark == 0f) {
                                SbzCyan.copy(
                                    alpha = 0.5f
                                )
                            } else {
                                SbzTextDisabled.copy(
                                    alpha = 0.4f
                                )
                            },
                        start = Offset(
                            trackX -
                                markWidth -
                                2f,
                            markY
                        ),
                        end = Offset(
                            trackX - 2f,
                            markY
                        ),
                        strokeWidth = 1.5f
                    )

                    drawLine(
                        color =
                            if (mark == 0f) {
                                SbzCyan.copy(
                                    alpha = 0.5f
                                )
                            } else {
                                SbzTextDisabled.copy(
                                    alpha = 0.4f
                                )
                            },
                        start = Offset(
                            trackX +
                                trackWidth +
                                2f,
                            markY
                        ),
                        end = Offset(
                            trackX +
                                trackWidth +
                                markWidth +
                                2f,
                            markY
                        ),
                        strokeWidth = 1.5f
                    )
                }

                // Active Gain Level Fill
                val currentNormY =
                    (
                        maxGainDb -
                            gainDb
                        ) /
                        (
                            maxGainDb -
                                minGainDb
                            )

                val currentThumbY =
                    thumbMargin +
                        effectiveHeight *
                        currentNormY

                if (gainDb != 0f) {

                    val fillTop =
                        minOf(
                            zeroY,
                            currentThumbY
                        )

                    val fillHeight =
                        abs(
                            zeroY -
                                currentThumbY
                        )

                    drawRect(
                        color =
                            if (gainDb > 0f) {
                                SbzCyan.copy(
                                    alpha = 0.6f
                                )
                            } else {
                                SbzAmber.copy(
                                    alpha = 0.6f
                                )
                            },
                        topLeft = Offset(
                            trackX + 1.5f,
                            fillTop
                        ),
                        size = Size(
                            trackWidth - 3f,
                            fillHeight
                        )
                    )
                }

                // Physical Slider Thumb Cap
                val thumbWidth = 38f
                val thumbHeight = 22f

                val thumbX =
                    (canvasWidth -
                        thumbWidth) / 2f

                val thumbTop =
                    currentThumbY -
                        thumbHeight / 2f

                // Thumb outer body
                drawRoundRect(
                    color = SbzSurfaceVariant,
                    topLeft = Offset(
                        thumbX,
                        thumbTop
                    ),
                    size = Size(
                        thumbWidth,
                        thumbHeight
                    ),
                    cornerRadius = CornerRadius(
                        4f,
                        4f
                    )
                )

                // Thumb border
                drawRoundRect(
                    color =
                        if (gainDb != 0f) {
                            gainColor
                        } else {
                            SbzBorder
                        },
                    topLeft = Offset(
                        thumbX,
                        thumbTop
                    ),
                    size = Size(
                        thumbWidth,
                        thumbHeight
                    ),
                    cornerRadius = CornerRadius(
                        4f,
                        4f
                    ),
                    style =
                        androidx.compose.ui.graphics
                            .drawscope.Stroke(
                                width = 1.5f
                            )
                )

                // Thumb center marker
                drawLine(
                    color =
                        if (gainDb != 0f) {
                            gainColor
                        } else {
                            Color.White
                        },
                    start = Offset(
                        thumbX + 4f,
                        currentThumbY
                    ),
                    end = Offset(
                        thumbX +
                            thumbWidth -
                            4f,
                        currentThumbY
                    ),
                    strokeWidth = 2.5f
                )
            }
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

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
