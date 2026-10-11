package com.mujib.sarjanadrawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.roundToInt

class DrawView(ctx: Context) : View(ctx) {

    enum class Tool { PEN, ARROW, SELECT }

    var onSelectionChanged: ((Item?) -> Unit)? = null
    var onHistoryChanged: (() -> Unit)? = null
    var onToolChanged: (() -> Unit)? = null

    val items = ArrayList<Item>()
    private val undoStack = ArrayList<Command>()
    private val redoStack = ArrayList<Command>()

    var tool = Tool.PEN
    var color: Int = 0xFFFFFFFF.toInt()
    var penWidth: Float = ctx.dpf(5f)
    var arrowWidth: Float = ctx.dpf(5f)
    var heroScale: Float = 1f

    var selected: Item? = null
        set(value) {
            field = value
            onSelectionChanged?.invoke(value)
            invalidate()
        }

    var showSelection: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var capturing: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dpf(1.5f)
        color = 0xFF0A84FF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(ctx.dpf(8f), ctx.dpf(6f)), 0f)
    }

    private var current: Item? = null
    private var lastX = 0f
    private var lastY = 0f
    private var accDx = 0f
    private var accDy = 0f
    private var resizeOld = 0f

    override fun onDraw(canvas: Canvas) {
        for (item in items) item.draw(canvas)
        if (showSelection && !capturing) {
            val s = selected
            if (s != null) {
                val b = RectF(s.bounds())
                b.inset(-context.dpf(6f), -context.dpf(6f))
                canvas.drawRoundRect(b, context.dpf(8f), context.dpf(8f), selPaint)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (tool) {
                    Tool.PEN -> {
                        val s = StrokeItem(color, penWidth)
                        s.add(x, y)
                        items.add(s)
                        current = s
                    }
                    Tool.ARROW -> {
                        val a = ArrowItem(x, y, x, y, color, arrowWidth)
                        items.add(a)
                        current = a
                    }
                    Tool.SELECT -> {
                        val hit = hitTest(x, y)
                        selected = hit
                        current = hit
                        lastX = x
                        lastY = y
                        accDx = 0f
                        accDy = 0f
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (tool) {
                    Tool.PEN -> {
                        val s = current as? StrokeItem
                        if (s != null) {
                            for (h in 0 until e.historySize) s.add(e.getHistoricalX(h), e.getHistoricalY(h))
                            s.add(x, y)
                        }
                    }
                    Tool.ARROW -> {
                        val a = current as? ArrowItem
                        if (a != null) {
                            a.x2 = x
                            a.y2 = y
                        }
                    }
                    Tool.SELECT -> {
                        val c = current
                        if (c != null) {
                            c.translate(x - lastX, y - lastY)
                            accDx += x - lastX
                            accDy += y - lastY
                            lastX = x
                            lastY = y
                        }
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishGesture()
                current = null
                invalidate()
            }
        }
        return true
    }

    private fun finishGesture() {
        val cur = current ?: return
        when (tool) {
            Tool.PEN -> {
                push(AddCmd(cur))
                Stats.inc(context, Stats.STROKES)
            }
            Tool.ARROW -> {
                val a = cur as ArrowItem
                if (hypot(a.x2 - a.x1, a.y2 - a.y1) < context.dpf(24f)) {
                    items.remove(a)
                } else {
                    push(AddCmd(a))
                    Stats.inc(context, Stats.STROKES)
                }
            }
            Tool.SELECT -> {
                if (accDx != 0f || accDy != 0f) push(MoveCmd(cur, accDx, accDy))
            }
        }
    }

    private fun hitTest(x: Float, y: Float): Item? {
        val slop = context.dpf(14f)
        for (i in items.indices.reversed()) {
            if (items[i].hit(x, y, slop)) return items[i]
        }
        return null
    }

    // ---------- hero ----------

    fun addHero(hero: Hero) {
        val bmp = HeroCatalog.load(context, hero)
        val item = HeroItem(hero, bmp, width / 2f, height / 2f, context.dpf(120f), heroScale)
        items.add(item)
        push(AddCmd(item))
        Stats.inc(context, Stats.HEROES)
        tool = Tool.SELECT
        onToolChanged?.invoke()
        selected = item
    }

    // ---------- ukuran (slider) ----------

    private fun toSize(item: Item?, p: Int): Float =
        if (item is HeroItem) 0.3f + p / 100f * 2.7f
        else context.dpf(2f) + p / 100f * context.dpf(38f)

    fun currentProgress(): Int {
        val s = selected
        val v = when {
            s is HeroItem -> (s.size - 0.3f) / 2.7f * 100f
            s != null -> (s.size - context.dpf(2f)) / context.dpf(38f) * 100f
            else -> (penWidth - context.dpf(2f)) / context.dpf(38f) * 100f
        }
        return v.roundToInt().coerceIn(0, 100)
    }

    fun beginResize() {
        resizeOld = selected?.size ?: 0f
    }

    fun setProgress(p: Int) {
        val s = selected
        if (s != null) {
            s.size = toSize(s, p)
            if (s is HeroItem) heroScale = s.size
        } else {
            penWidth = toSize(null, p)
            arrowWidth = penWidth
        }
        invalidate()
    }

    fun endResize() {
        val s = selected ?: return
        if (s.size != resizeOld) push(ResizeCmd(s, resizeOld, s.size))
    }

    // ---------- undo / redo ----------

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val c = undoStack.removeAt(undoStack.size - 1)
        c.undo()
        redoStack.add(c)
        afterChange()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val c = redoStack.removeAt(redoStack.size - 1)
        c.redo()
        undoStack.add(c)
        afterChange()
    }

    fun clearAll() {
        if (items.isEmpty()) return
        val snapshot = ArrayList(items)
        items.clear()
        selected = null
        push(ClearCmd(snapshot))
    }

    fun resetAll() {
        items.clear()
        undoStack.clear()
        redoStack.clear()
        selected = null
    }

    private fun push(c: Command) {
        undoStack.add(c)
        redoStack.clear()
        afterChange()
    }

    private fun afterChange() {
        invalidate()
        onHistoryChanged?.invoke()
        onSelectionChanged?.invoke(selected)
    }

    private inner class AddCmd(val item: Item) : Command {
        override fun undo() {
            items.remove(item)
            if (selected === item) selected = null
        }

        override fun redo() {
            items.add(item)
        }
    }

    private inner class MoveCmd(val item: Item, val dx: Float, val dy: Float) : Command {
        override fun undo() = item.translate(-dx, -dy)
        override fun redo() = item.translate(dx, dy)
    }

    private inner class ResizeCmd(val item: Item, val old: Float, val new: Float) : Command {
        override fun undo() {
            item.size = old
        }

        override fun redo() {
            item.size = new
        }
    }

    private inner class ClearCmd(val snapshot: List<Item>) : Command {
        override fun undo() {
            items.addAll(snapshot)
        }

        override fun redo() {
            items.clear()
            selected = null
        }
    }
}
