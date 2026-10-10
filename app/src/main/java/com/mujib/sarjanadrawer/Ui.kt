package com.mujib.sarjanadrawer

import android.content.Context
import android.graphics.drawable.GradientDrawable

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun roundRect(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(color)
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

fun oval(color: Int, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }
