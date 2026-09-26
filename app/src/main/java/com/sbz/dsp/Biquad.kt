package com.sbz.dsp

import kotlin.math.*

internal class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1L = 0f; private var z2L = 0f; private var z1R = 0f; private var z2R = 0f

    fun setPeaking(sampleRate: Float, frequency: Float, q: Float, gainDb: Float) {
        val f = frequency.coerceIn(10f, sampleRate * 0.45f)
        val w = 2.0 * Math.PI * f / sampleRate
        val alpha = sin(w) / (2.0 * q.coerceAtLeast(0.1f))
        val A = 10.0.pow(gainDb / 40.0)
        val c = cos(w)
        val bb0 = 1 + alpha * A
        val bb1 = -2 * c
        val bb2 = 1 - alpha * A
        val aa0 = 1 + alpha / A
        val aa1 = -2 * c
        val aa2 = 1 - alpha / A
        b0 = (bb0 / aa0).toFloat(); b1 = (bb1 / aa0).toFloat(); b2 = (bb2 / aa0).toFloat()
        a1 = (aa1 / aa0).toFloat(); a2 = (aa2 / aa0).toFloat()
    }

    fun setLowShelf(sampleRate: Float, frequency: Float, gainDb: Float) {
        val w = 2.0 * Math.PI * frequency.coerceIn(10f, sampleRate * .45f) / sampleRate
        val A = 10.0.pow(gainDb / 40.0); val c = cos(w); val s = sin(w)
        val alpha = s / 2.0 * sqrt((A + 1 / A) * 2.0)
        val beta = 2 * sqrt(A) * alpha
        val bb0 = A*((A+1)-(A-1)*c+beta); val bb1 = 2*A*((A-1)-(A+1)*c); val bb2 = A*((A+1)-(A-1)*c-beta)
        val aa0 = (A+1)+(A-1)*c+beta; val aa1 = -2*((A-1)+(A+1)*c); val aa2 = (A+1)+(A-1)*c-beta
        b0=(bb0/aa0).toFloat(); b1=(bb1/aa0).toFloat(); b2=(bb2/aa0).toFloat(); a1=(aa1/aa0).toFloat(); a2=(aa2/aa0).toFloat()
    }

    fun setHighShelf(sampleRate: Float, frequency: Float, gainDb: Float) {
        val w = 2.0 * Math.PI * frequency.coerceIn(10f, sampleRate * .45f) / sampleRate
        val A = 10.0.pow(gainDb / 40.0); val c = cos(w); val s = sin(w)
        val alpha = s / 2.0 * sqrt((A + 1 / A) * 2.0); val beta = 2 * sqrt(A) * alpha
        val bb0=A*((A+1)+(A-1)*c+beta); val bb1=-2*A*((A-1)+(A+1)*c); val bb2=A*((A+1)+(A-1)*c-beta)
        val aa0=(A+1)-(A-1)*c+beta; val aa1=2*((A-1)-(A+1)*c); val aa2=(A+1)-(A-1)*c-beta
        b0=(bb0/aa0).toFloat(); b1=(bb1/aa0).toFloat(); b2=(bb2/aa0).toFloat(); a1=(aa1/aa0).toFloat(); a2=(aa2/aa0).toFloat()
    }

    fun processL(x: Float): Float { val y=b0*x+z1L; z1L=b1*x-a1*y+z2L; z2L=b2*x-a2*y; return y }
    fun processR(x: Float): Float { val y=b0*x+z1R; z1R=b1*x-a1*y+z2R; z2R=b2*x-a2*y; return y }
    fun reset() { z1L=0f; z2L=0f; z1R=0f; z2R=0f }
}
