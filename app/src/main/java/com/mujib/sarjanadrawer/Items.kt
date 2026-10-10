package com.mujib.sarjanadrawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

interface Command {
    fun undo()
    fun redo()
}

/** Satu objek di layar: coretan, panah, atau hero. */
abstract class Item {
    /** Lebar garis (px) untuk coretan/panah, skala untuk hero. Diatur oleh slider ukuran. */
    abstract var size: Float

    abstract fun draw(c: Canvas)
    abstract fun bounds(): RectF
    abstract fun hit(x: Float, y: Float, slop: Float): Boolean
    abstract fun translate(dx: Float, dy: Float)
}

fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return hypot(px - x1, py - y1)
    val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

class StrokeItem(val color: Int, override var size: Float) : Item() {
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun add(x: Float, y: Float) {
        xs.add(x)
        ys.add(y)
    }

    override fun draw(c: Canvas) {
        if (xs.isEmpty()) return
        paint.color = color
        paint.strokeWidth = size
        if (xs.size == 1) {
            paint.style = Paint.Style.FILL
            c.drawCircle(xs[0], ys[0], size / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) {
            val mx = (xs[i - 1] + xs[i]) / 2f
            val my = (ys[i - 1] + ys[i]) / 2f
            path.quadTo(xs[i - 1], ys[i - 1], mx, my)
        }
        path.lineTo(xs[xs.size - 1], ys[ys.size - 1])
        c.drawPath(path, paint)
    }

    override fun bounds(): RectF {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (i in xs.indices) {
            if (xs[i] < l) l = xs[i]
            if (xs[i] > r) r = xs[i]
            if (ys[i] < t) t = ys[i]
            if (ys[i] > b) b = ys[i]
        }
        val rect = RectF(l, t, r, b)
        rect.inset(-size / 2f, -size / 2f)
        return rect
    }

    override fun hit(x: Float, y: Float, slop: Float): Boolean {
        if (xs.isEmpty()) return false
        val limit = size / 2f + slop
        if (xs.size == 1) return hypot(x - xs[0], y - ys[0]) <= limit
        for (i in 1 until xs.size) {
            if (distToSegment(x, y, xs[i - 1], ys[i - 1], xs[i], ys[i]) <= limit) return true
        }
        return false
    }

    override fun translate(dx: Float, dy: Float) {
        for (i in xs.indices) {
            xs[i] = xs[i] + dx
            ys[i] = ys[i] + dy
        }
    }
}

class ArrowItem(
    var x1: Float,
    var y1: Float,
    var x2: Float,
    var y2: Float,
    val color: Int,
    override var size: Float
) : Item() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private fun headLen(): Float = size * 3f + 14f

    override fun draw(c: Canvas) {
        paint.color = color
        paint.strokeWidth = size
        val a = atan2(y2 - y1, x2 - x1)
        val hl = headLen()
        val spread = 0.5f
        val bx = x2 - cos(a) * hl * 0.8f
        val by = y2 - sin(a) * hl * 0.8f
        paint.style = Paint.Style.STROKE
        c.drawLine(x1, y1, bx, by, paint)
        val lx = x2 - hl * cos(a - spread)
        val ly = y2 - hl * sin(a - spread)
        val rx = x2 - hl * cos(a + spread)
        val ry = y2 - hl * sin(a + spread)
        val head = Path()
        head.moveTo(x2, y2)
        head.lineTo(lx, ly)
        head.lineTo(rx, ry)
        head.close()
        paint.style = Paint.Style.FILL
        c.drawPath(head, paint)
    }

    override fun bounds(): RectF {
        val rect = RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
        val pad = headLen()
        rect.inset(-pad, -pad)
        return rect
    }

    override fun hit(x: Float, y: Float, slop: Float): Boolean =
        distToSegment(x, y, x1, y1, x2, y2) <= size / 2f + headLen() / 2f + slop

    override fun translate(dx: Float, dy: Float) {
        x1 += dx
        y1 += dy
        x2 += dx
        y2 += dy
    }
}

class HeroItem(
    val hero: Hero,
    private val bitmap: Bitmap?,
    var cx: Float,
    var cy: Float,
    private val base: Float,
    override var size: Float
) : Item() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFF1C1C1E.toInt()
    }

    fun rect(): RectF {
        val w = base * size
        val h = if (bitmap != null) w * bitmap.height / bitmap.width else w
        return RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    override fun draw(c: Canvas) {
        val r = rect()
        if (bitmap != null) {
            c.drawBitmap(bitmap, null, r, paint)
        } else {
            paint.color = 0x33000000
            c.drawRoundRect(r, 24f, 24f, paint)
            textPaint.textSize = r.width() * 0.35f
            c.drawText(hero.code.uppercase(), r.centerX(), r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    override fun bounds(): RectF = rect()

    override fun hit(x: Float, y: Float, slop: Float): Boolean {
        val r = rect()
        r.inset(-slop, -slop)
        return r.contains(x, y)
    }

    override fun translate(dx: Float, dy: Float) {
        cx += dx
        cy += dy
    }
}
