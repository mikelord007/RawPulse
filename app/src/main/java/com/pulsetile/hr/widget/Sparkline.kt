package com.pulsetile.hr.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Renders a recent value series to a square, rounded, transparent bitmap that sits
 * behind a widget's number. Autoscales to the window's min/max so the line always
 * fills the card, and — because the source series is a rolling window — the graph
 * scrolls as new samples arrive. Higher values draw higher.
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

        val min = series.minOrNull() ?: return null
        val max = series.maxOrNull() ?: return null
        val range = (max - min).coerceAtLeast(1)

        // Leave headroom top/bottom so the line isn't glued to the edges.
        val topPad = h * 0.20f
        val botPad = h * 0.10f
        val usableH = h - topPad - botPad

        val n = series.size
        val stepX = w.toFloat() / (n - 1)
        fun xAt(i: Int) = i * stepX
        fun yAt(v: Int): Float {
            val norm = (v - min).toFloat() / range // 0..1; higher value -> higher on screen
            return topPad + (1f - norm) * usableH
        }

        val linePath = Path().apply {
            moveTo(xAt(0), yAt(series[0]))
            for (i in 1 until n) lineTo(xAt(i), yAt(series[i]))
        }

        // Filled area under the line.
        val fillPath = Path(linePath).apply {
            lineTo(xAt(n - 1), h.toFloat())
            lineTo(xAt(0), h.toFloat())
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (0x30 shl 24) or (lineColor and 0x00FFFFFF) // ~19% alpha
        }
        canvas.drawPath(fillPath, fillPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.018f).coerceAtLeast(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = (0xCC shl 24) or (lineColor and 0x00FFFFFF) // ~80% alpha
        }
        canvas.drawPath(linePath, linePaint)

        return bitmap
    }
}
