package com.mujib.sarjanadrawer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.hypot

/**
 * Toolbar 3 baris bergaya iOS monokrom:
 *  baris 1: warna cepat (putih, kuning) + slider ukuran
 *  baris 2: pena, panah, pilih, hero (ikon Archer Queen), warna
 *  baris 3: undo, redo, screenshot, hapus semua, tutup
 * Digeser dengan menyeret bagian toolbar mana pun (selain slider).
 */
class ToolbarView(
    ctx: Context,
    private val draw: DrawView,
    private val onShot: () -> Unit,
    private val onClose: () -> Unit,
    private val onMove: (Float, Float) -> Unit
) : LinearLayout(ctx) {

    private enum class Panel { NONE, HERO, COLOR }

    private val ink = 0xFF1C1C1E.toInt()
    private var panel = Panel.NONE
    private var sliding = false

    private val btn = 28f
    private val cols = 5
    private val widthDp = btn * cols

    private fun d(v: Float): Int = context.dp(v)

    private val panelHost = FrameLayout(ctx)
    private val seek = SeekBar(ctx)
    private val swatches = ArrayList<Pair<Int, View>>()

    private val white = 0xFFFFFFFF.toInt()
    private val yellow = 0xFFFFCC00.toInt()

    private val palette = listOf(
        0xFFFFFFFF, 0xFFFFCC00, 0xFFFF9500, 0xFFFF3B30,
        0xFF34C759, 0xFF0A84FF, 0xFFAF52DE, 0xFF1C1C1E
    ).map { it.toInt() }

    private val aqIcon = HeroCatalog.loadAqIcon(ctx)

    private val btnPen = icon(R.drawable.ic_pen, "Pena") { setTool(DrawView.Tool.PEN) }
    private val btnArrow = icon(R.drawable.ic_arrow, "Panah") { setTool(DrawView.Tool.ARROW) }
    private val btnSelect = icon(R.drawable.ic_select, "Pilih dan geser") { setTool(DrawView.Tool.SELECT) }
    private val btnHero = icon(R.drawable.ic_hero, "Hero") { togglePanel(Panel.HERO) }
    private val btnColor = icon(R.drawable.ic_palette, "Warna") { togglePanel(Panel.COLOR) }

    private val btnUndo = icon(R.drawable.ic_undo, "Undo") { draw.undo() }
    private val btnRedo = icon(R.drawable.ic_redo, "Redo") { draw.redo() }
    private val btnShot = icon(R.drawable.ic_camera, "Screenshot") { onShot() }
    private val btnTrash = icon(R.drawable.ic_trash, "Hapus semua") { draw.clearAll() }
    private val btnClose = icon(R.drawable.ic_close, "Tutup") { onClose() }

    private val sliderRow = buildSliderRow()
    private val heroPanel = buildHeroPanel()
    private val colorPanel = buildColorPanel()

    // seret toolbar
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var ignoreDrag = false

    init {
        orientation = VERTICAL
        setPadding(d(8f), d(8f), d(8f), d(8f))
        background = roundRect(0xEEFFFFFF.toInt(), d(20f).toFloat(), 0x33000000, 1)

        if (aqIcon != null) {
            btnHero.setImageBitmap(aqIcon)
            btnHero.clearColorFilter()
            btnHero.scaleType = ImageView.ScaleType.CENTER_CROP
            btnHero.setPadding(d(2f), d(2f), d(2f), d(2f))
            btnHero.clipToOutline = true
        }

        addView(sliderRow)
        addView(panelHost, LayoutParams(d(widthDp), LayoutParams.WRAP_CONTENT))
        addView(row(btnPen, btnArrow, btnSelect, btnHero, btnColor))
        addView(row(btnUndo, btnRedo, btnShot, btnTrash, btnClose))

        draw.onToolChanged = { refreshTools() }
        draw.onHistoryChanged = { refreshHistory() }
        draw.onSelectionChanged = { syncSlider() }

        refreshTools()
        refreshHistory()
        refreshColors()
        syncSlider()
    }

    // ---------- seret toolbar ----------

    private fun onSeek(e: MotionEvent): Boolean {
        val loc = IntArray(2)
        seek.getLocationOnScreen(loc)
        val m = d(8f)
        return e.rawX >= loc[0] - m && e.rawX <= loc[0] + seek.width + m &&
            e.rawY >= loc[1] - m && e.rawY <= loc[1] + seek.height + m
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downX = e.rawX
                downY = e.rawY
                lastX = downX
                lastY = downY
                ignoreDrag = onSeek(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!ignoreDrag && !dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                    dragging = true
                    lastX = downX
                    lastY = downY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
            }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX
                lastY = e.rawY
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                onMove(e.rawX - lastX, e.rawY - lastY)
                lastX = e.rawX
                lastY = e.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    // ---------- builders ----------

    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER
        r.layoutParams = LayoutParams(d(widthDp), LayoutParams.WRAP_CONTENT)
        for (x in v) r.addView(x)
        return r
    }

    private fun icon(res: Int, desc: String, click: () -> Unit): ImageView {
        val v = ImageView(context)
        v.setImageResource(res)
        v.contentDescription = desc
        v.setPadding(d(5f), d(5f), d(5f), d(5f))
        v.layoutParams = LayoutParams(d(btn), d(btn))
        v.setColorFilter(ink)
        v.setOnClickListener { click() }
        return v
    }

    private fun swatch(c: Int, sizeDp: Float, marginDp: Float): View {
        val dot = View(context)
        val lp = LayoutParams(d(sizeDp), d(sizeDp))
        lp.setMargins(d(marginDp), d(marginDp), d(marginDp), d(marginDp))
        dot.layoutParams = lp
        dot.setOnClickListener {
            draw.color = c
            refreshColors()
        }
        swatches.add(Pair(c, dot))
        return dot
    }

    private fun buildSliderRow(): View {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.layoutParams = LayoutParams(d(widthDp), d(btn))

        r.addView(swatch(white, 18f, 3f))
        r.addView(swatch(yellow, 18f, 3f))

        seek.max = 100
        seek.setPadding(d(10f), 0, d(10f), 0)
        seek.progressTintList = ColorStateList.valueOf(ink)
        seek.thumbTintList = ColorStateList.valueOf(ink)
        seek.progressBackgroundTintList = ColorStateList.valueOf(0x33000000)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) draw.setProgress(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                sliding = true
                draw.beginResize()
                refreshTools()
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                draw.endResize()
                sliding = false
                refreshTools()
            }
        })
        r.addView(seek, LayoutParams(0, d(btn), 1f))
        return r
    }

    private fun grid(columns: Int, cells: List<View>): LinearLayout {
        val g = LinearLayout(context)
        g.orientation = VERTICAL
        g.gravity = Gravity.CENTER_HORIZONTAL
        cells.chunked(columns).forEach { chunk ->
            val r = LinearLayout(context)
            r.orientation = HORIZONTAL
            r.gravity = Gravity.CENTER
            chunk.forEach { r.addView(it) }
            g.addView(r)
        }
        return g
    }

    private fun buildHeroPanel(): View {
        val cells = HeroCatalog.heroes.map { h ->
            val cell = FrameLayout(context)
            cell.background = roundRect(0x14000000, d(12f).toFloat())
            val lp = LayoutParams(d(40f), d(40f))
            lp.setMargins(d(3f), d(3f), d(3f), d(3f))
            cell.layoutParams = lp
            val bmp = HeroCatalog.load(context, h)
            if (bmp != null) {
                val iv = ImageView(context)
                iv.setImageBitmap(bmp)
                iv.scaleType = ImageView.ScaleType.FIT_CENTER
                iv.setPadding(d(3f), d(3f), d(3f), d(3f))
                cell.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val tv = TextView(context)
                tv.text = h.code.uppercase()
                tv.gravity = Gravity.CENTER
                tv.setTextColor(ink)
                tv.textSize = 12f
                tv.typeface = Typeface.DEFAULT_BOLD
                cell.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            cell.contentDescription = h.name
            cell.setOnClickListener {
                draw.addHero(h)
                setPanel(Panel.NONE)
            }
            cell
        }
        val g = grid(3, cells)
        g.setPadding(0, d(4f), 0, d(4f))
        g.visibility = View.GONE
        panelHost.addView(g, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return g
    }

    private fun buildColorPanel(): View {
        val g = grid(4, palette.map { swatch(it, 26f, 4f) })
        g.setPadding(0, d(4f), 0, d(4f))
        g.visibility = View.GONE
        panelHost.addView(g, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return g
    }

    // ---------- state ----------

    private fun setTool(t: DrawView.Tool) {
        draw.tool = t
        if (t != DrawView.Tool.SELECT) draw.selected = null
        refreshTools()
        syncSlider()
    }

    private fun togglePanel(p: Panel) {
        setPanel(if (panel == p) Panel.NONE else p)
    }

    private fun setPanel(p: Panel) {
        panel = p
        heroPanel.visibility = if (p == Panel.HERO) View.VISIBLE else View.GONE
        colorPanel.visibility = if (p == Panel.COLOR) View.VISIBLE else View.GONE
        refreshTools()
    }

    private fun style(v: ImageView, on: Boolean) {
        v.background = if (on) oval(ink) else null
        v.setColorFilter(if (on) Color.WHITE else ink)
    }

    private fun refreshTools() {
        style(btnPen, draw.tool == DrawView.Tool.PEN)
        style(btnArrow, draw.tool == DrawView.Tool.ARROW)
        style(btnSelect, draw.tool == DrawView.Tool.SELECT)
        style(btnColor, panel == Panel.COLOR)
        if (aqIcon != null) {
            btnHero.background = oval(if (panel == Panel.HERO) ink else 0x1A000000)
        } else {
            style(btnHero, panel == Panel.HERO)
        }
        draw.showSelection = draw.tool == DrawView.Tool.SELECT || sliding
    }

    private fun refreshHistory() {
        btnUndo.alpha = if (draw.canUndo()) 1f else 0.3f
        btnRedo.alpha = if (draw.canRedo()) 1f else 0.3f
    }

    private fun refreshColors() {
        for ((c, dot) in swatches) {
            val selected = c == draw.color
            dot.background = oval(c, if (selected) ink else 0x33000000, if (selected) d(2f) else 1)
        }
    }

    private fun syncSlider() {
        if (!sliding) seek.progress = draw.currentProgress()
    }
}
