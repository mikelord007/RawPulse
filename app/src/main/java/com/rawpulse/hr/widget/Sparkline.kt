package com.rawpulse.hr.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Renders a recent value series to a square, rounded, transparent bitmap that sits
 * behind a widget's number. Autoscales to the window's min/max so the line always
 * fills the card, and — because the source series is a rolling window — the graph
 * scrolls as new samples arrive. Higher values draw higher.
 *
 * The line is smoothed with Catmull-Rom → cubic-bezier interpolation so it reads as
 * a flowing curve rather than a jagged polyline, and the area beneath it is filled
 * with a soft vertical gradient so the bold number in front stays legible.
 */
object Sparkline {

    fun render(
        sizePx: Int,
        series: List<Int>,
        lineColor: Int,
        cornerRadiusPx: Float
    ): Bitmap? {
        if (sizePx <= 0 || series.size < 2) return null

        val w = sizePx
        val h = sizePx
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Match the card's rounded corners.
        val clip = Path().apply {
            addRoundRect(
                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                cornerRadiusPx, cornerRadiusPx, Path.Direction.CW
            )
        }
        canvas.clipPath(clip)

        // Lightly smooth the raw samples first so a single noisy beat doesn't spike the curve.
        val values = smoothSeries(series)

        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(1f)

        // Keep the curve in the lower two-thirds of the card so it sits behind the
        // number as a backdrop rather than crossing straight through the digits.
        val topPad = h * 0.42f
        val botPad = h * 0.12f
        val usableH = h - topPad - botPad

        val n = values.size
        val stepX = w.toFloat() / (n - 1)
        fun xAt(i: Int) = i * stepX
        fun yAt(v: Float): Float {
            val norm = (v - min) / range // 0..1; higher value -> higher on screen
            return topPad + (1f - norm) * usableH
        }

        // Build a smooth curve through the points using Catmull-Rom control points.
        val linePath = Path()
        linePath.moveTo(xAt(0), yAt(values[0]))
        for (i in 0 until n - 1) {
            val p0x = xAt(if (i - 1 < 0) 0 else i - 1)
            val p0y = yAt(values[if (i - 1 < 0) 0 else i - 1])
            val p1x = xAt(i)
            val p1y = yAt(values[i])
            val p2x = xAt(i + 1)
            val p2y = yAt(values[i + 1])
            val p3x = xAt(if (i + 2 >= n) n - 1 else i + 2)
            val p3y = yAt(values[if (i + 2 >= n) n - 1 else i + 2])

            val c1x = p1x + (p2x - p0x) / 6f
            val c1y = p1y + (p2y - p0y) / 6f
            val c2x = p2x - (p3x - p1x) / 6f
            val c2y = p2y - (p3y - p1y) / 6f
            linePath.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y)
        }

        // Soft vertical gradient fill under the curve: strongest near the line, fading to nothing.
        val fillPath = Path(linePath).apply {
            lineTo(xAt(n - 1), h.toFloat())
            lineTo(xAt(0), h.toFloat())
            close()
        }
        val rgb = lineColor and 0x00FFFFFF
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, topPad, 0f, h.toFloat(),
                (0x59 shl 24) or rgb, // ~35% alpha near the curve
                0x00000000 or rgb,     // fully transparent at the bottom
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        // Soft glow beneath the stroke for a bit of depth.
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.03f).coerceAtLeast(4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = (0x40 shl 24) or rgb
            maskFilter = BlurMaskFilter(sizePx * 0.02f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(linePath, glowPaint)

        // Crisp curve on top.
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.02f).coerceAtLeast(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = (0xF2 shl 24) or rgb // ~95% alpha for a vivid line
        }
        canvas.drawPath(linePath, linePaint)

        // Highlight the latest sample with a small filled dot + halo.
        val lastX = xAt(n - 1)
        val lastY = yAt(values[n - 1])
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (0x40 shl 24) or rgb
        }
        canvas.drawCircle(lastX, lastY, (sizePx * 0.045f).coerceAtLeast(6f), haloPaint)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (0xFF shl 24) or rgb
        }
        canvas.drawCircle(lastX, lastY, (sizePx * 0.022f).coerceAtLeast(3f), dotPaint)

        return bitmap
    }

    /** 3-point moving average to take the edge off single-beat noise. */
    private fun smoothSeries(series: List<Int>): FloatArray {
        val n = series.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            val a = series[if (i - 1 < 0) 0 else i - 1]
            val b = series[i]
            val c = series[if (i + 1 >= n) n - 1 else i + 1]
            out[i] = (a + b + c) / 3f
        }
        return out
    }
}
