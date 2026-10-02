package com.sbz.dsp

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ biquad used by the Equalizer314-style parametric response model. */
class BiquadFilter {
    enum class Type { PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH, BAND_PASS }

    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1L = 0f; private var x2L = 0f; private var y1L = 0f; private var y2L = 0f
    private var x1R = 0f; private var x2R = 0f; private var y1R = 0f; private var y2R = 0f
    var isEnabled = true

    fun reset() {
        x1L=0f; x2L=0f; y1L=0f; y2L=0f
        x1R=0f; x2R=0f; y1R=0f; y2R=0f
    }

    fun configure(type: Type, frequencyHz: Float, gainDb: Float, q: Float, sampleRate: Float) {
        val sr = sampleRate.coerceAtLeast(8000f)
        val f = frequencyHz.coerceIn(10f, sr * .49f)
        val qq = q.coerceIn(.1f, 20f)
        val A = 10f.pow(gainDb / 40f)
        val w0 = 2f * Math.PI.toFloat() * f / sr
        val c = cos(w0); val s = sin(w0); val alpha = s / (2f * qq)
        var B0=1f; var B1=0f; var B2=0f; var A0=1f; var A1=0f; var A2=0f
        when(type) {
            Type.PEAKING -> { B0=1+alpha*A; B1=-2*c; B2=1-alpha*A; A0=1+alpha/A; A1=-2*c; A2=1-alpha/A }
            Type.LOW_SHELF -> { val sa=sqrt(A); val t=2*sa*alpha; B0=A*((A+1)-(A-1)*c+t); B1=2*A*((A-1)-(A+1)*c); B2=A*((A+1)-(A-1)*c-t); A0=(A+1)+(A-1)*c+t; A1=-2*((A-1)+(A+1)*c); A2=(A+1)+(A-1)*c-t }
            Type.HIGH_SHELF -> { val sa=sqrt(A); val t=2*sa*alpha; B0=A*((A+1)+(A-1)*c+t); B1=-2*A*((A-1)+(A+1)*c); B2=A*((A+1)+(A-1)*c-t); A0=(A+1)-(A-1)*c+t; A1=2*((A-1)-(A+1)*c); A2=(A+1)-(A-1)*c-t }
            Type.LOW_PASS -> { B0=(1-c)/2; B1=1-c; B2=(1-c)/2; A0=1+alpha; A1=-2*c; A2=1-alpha }
            Type.HIGH_PASS -> { B0=(1+c)/2; B1=-(1+c); B2=(1+c)/2; A0=1+alpha; A1=-2*c; A2=1-alpha }
            Type.BAND_PASS -> { B0=alpha; B1=0f; B2=-alpha; A0=1+alpha; A1=-2*c; A2=1-alpha }
            Type.NOTCH -> { B0=1f; B1=-2*c; B2=1f; A0=1+alpha; A1=-2*c; A2=1-alpha }
        }
        val inv=1f/A0; b0=B0*inv; b1=B1*inv; b2=B2*inv; a1=A1*inv; a2=A2*inv
    }

    fun frequencyResponseDb(frequencyHz: Float, sampleRate: Float): Float {
        val sr=sampleRate.coerceAtLeast(8000f); val f=frequencyHz.coerceIn(1f,sr*.499f)
        val w=2.0*Math.PI*f/sr; val c=Math.cos(w); val s=Math.sin(w)
        val nr=b0.toDouble()*b0 + b1.toDouble()*b1 + b2.toDouble()*b2 + 2.0*(b0.toDouble()*b1+b1.toDouble()*b2)*c + 2.0*b0.toDouble()*b2*c*2.0*c - 2.0*b0.toDouble()*b2
        val dr=1.0 + a1.toDouble()*a1 + a2.toDouble()*a2 + 2.0*(a1.toDouble()+a1.toDouble()*a2)*c + 2.0*a2.toDouble()*c*2.0*c - 2.0*a2.toDouble()
        val ratio=(nr.coerceAtLeast(1e-20)/dr.coerceAtLeast(1e-20)).coerceAtLeast(1e-20)
        return (10.0*Math.log10(ratio)).toFloat()
    }

    fun processLeft(input: Float): Float { if(!isEnabled)return input; val o=b0*input+b1*x1L+b2*x2L-a1*y1L-a2*y2L; x2L=x1L;x1L=input;y2L=y1L;y1L=o;return o }
    fun processRight(input: Float): Float { if(!isEnabled)return input; val o=b0*input+b1*x1R+b2*x2R-a1*y1R-a2*y2R; x2R=x1R;x1R=input;y2R=y1R;y1R=o;return o }
}
