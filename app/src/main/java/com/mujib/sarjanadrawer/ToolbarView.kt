package com.mujib.sarjanadrawer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/** Toolbar 2 baris bergaya iOS monokrom, bisa digeser lewat pegangan di bagian atas. */
class ToolbarView(
    ctx: Context,
    private val draw: DrawView,
    private val onShot: () -> Unit,
    private val onClose: () -> Unit,
    private val onMove: (Float, Float) -> Unit
) : LinearLayout(ctx) {

    private enum class Panel { NONE, HERO, COLOR, SIZE }

    private val ink = 0xFF1C1C1E.toInt()
    private var panel = Panel.NONE

    private fun d(v: Float): Int = context.dp(v)

    private val panelHost = FrameLayout(ctx)
    private val sizeLabel = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val colorDots = ArrayList<Pair<Int, View>>()

    private val colors = listOf(
        0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759,
        0xFF0A84FF, 0xFFAF52DE, 0xFF1C1C1E, 0xFFFFFFFF
    ).map { it.toInt() }

    private val btnPen = icon(R.drawable.ic_pen, "Pena") { setTool(DrawView.Tool.PEN) }
    private val btnArrow = icon(R.drawable.ic_arrow, "Panah") { setTool(DrawView.Tool.ARROW) }
    private val btnSelect = icon(R.drawable.ic_select, "Pilih dan geser") { setTool(DrawView.Tool.SELECT) }
    private val btnHero = icon(R.drawable.ic_hero, "Hero") { togglePanel(Panel.HERO) }
    private val btnColor = icon(R.drawable.ic_palette, "Warna") { togglePanel(Panel.COLOR) }
    private val btnSize = icon(R.drawable.ic_tune, "Ukuran") { togglePanel(Panel.SIZE) }

    private val btnUndo = icon(R.drawable.ic_undo, "Undo") { draw.undo() }
    private val btnRedo = icon(R.drawable.ic_redo, "Redo") { draw.redo() }
    private val btnShot = icon(R.drawable.ic_camera, "Screenshot") { onShot() }
    private val btnTrash = icon(R.drawable.ic_trash, "Hapus semua") { draw.clearAll() }
    private val btnClose = icon(R.drawable.ic_close, "Tutup") { onClose() }

    private val heroPanel = buildHeroPanel()
    private val colorPanel = buildColorPanel()
    private val sizePanel = buildSizePanel()

    init {
        orientation = VERTICAL
        setPadding(d(10f), d(4f), d(10f), d(10f))
        background = roundRect(0xE8FFFFFF.toInt(), d(26f).toFloat(), 0x33000000, 1)

        val grip = FrameLayout(context)
        grip.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, d(24f))
        val bar = View(context)
        bar.background = roundRect(0x55000000, d(3f).toFloat())
        grip.addView(bar, FrameLayout.LayoutParams(d(40f), d(5f), Gravity.CENTER))
        var lx = 0f
        var ly = 0f
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.rawX
                    ly = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    onMove(e.rawX - lx, e.rawY - ly)
                    lx = e.rawX
                    ly = e.rawY
                }
            }
            true
        }
        addView(grip)
        addView(panelHost)
        addView(row(btnPen, btnArrow, btnSelect, btnHero, btnColor, btnSize))
        addView(row(btnUndo, btnRedo, btnShot, btnTrash, btnClose))

        draw.onToolChanged = { refreshTools() }
        draw.onHistoryChanged = { refreshHistory() }
        draw.onSelectionChanged = { if (panel == Panel.SIZE) syncSlider() }

        refreshTools()
        refreshHistory()
        refreshColors()
    }

    // ---------- builders ----------

    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER
        for (x in v) r.addView(x)
        return r
    }

    private fun icon(res: Int, desc: String, click: () -> Unit): ImageView {
        val v = ImageView(context)
        v.setImageResource(res)
        v.contentDescription = desc
        v.setPadding(d(11f), d(11f), d(11f), d(11f))
        v.layoutParams = LayoutParams(d(46f), d(46f))
        v.setColorFilter(ink)
        v.setOnClickListener { click() }
        return v
    }

    private fun buildHeroPanel(): View {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER
        r.setPadding(0, d(4f), 0, d(6f))
        for (h in HeroCatalog.heroes) {
            val cell = FrameLayout(context)
            cell.background = roundRect(0x14000000, d(12f).toFloat())
            val lp = LayoutParams(d(46f), d(46f))
            lp.setMargins(d(3f), 0, d(3f), 0)
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
                tv.textSize = 13f
                tv.typeface = Typeface.DEFAULT_BOLD
                cell.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            cell.contentDescription = h.name
            cell.setOnClickListener {
                draw.addHero(h)
                setPanel(Panel.NONE)
            }
            r.addView(cell)
        }
        r.visibility = View.GONE
        panelHost.addView(r)
        return r
    }

    private fun buildColorPanel(): View {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER
        r.setPadding(0, d(6f), 0, d(8f))
        for (c in colors) {
            val dot = View(context)
            val lp = LayoutParams(d(30f), d(30f))
            lp.setMargins(d(4f), 0, d(4f), 0)
            dot.layoutParams = lp
            dot.setOnClickListener {
                draw.color = c
                refreshColors()
            }
            colorDots.add(Pair(c, dot))
            r.addView(dot)
        }
        r.visibility = View.GONE
        panelHost.addView(r)
        return r
    }

    private fun buildSizePanel(): View {
        val r = LinearLayout(context)
        r.orientation = HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(d(6f), d(4f), d(6f), d(6f))
        sizeLabel.textSize = 12f
        sizeLabel.setTextColor(ink)
        seek.max = 100
        seek.progressTintList = ColorStateList.valueOf(ink)
        seek.thumbTintList = ColorStateList.valueOf(ink)
        seek.progressBackgroundTintList = ColorStateList.valueOf(0x33000000)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    draw.setProgress(p)
                    updateLabel()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                draw.beginResize()
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                draw.endResize()
            }
        })
        r.addView(sizeLabel, LayoutParams(d(92f), LayoutParams.WRAP_CONTENT))
        r.addView(seek, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        r.visibility = View.GONE
        panelHost.addView(r)
        return r
    }

    // ---------- state ----------

    private fun setTool(t: DrawView.Tool) {
        draw.tool = t
        refreshTools()
    }

    private fun togglePanel(p: Panel) {
        setPanel(if (panel == p) Panel.NONE else p)
    }

    private fun setPanel(p: Panel) {
        panel = p
        heroPanel.visibility = if (p == Panel.HERO) View.VISIBLE else View.GONE
        colorPanel.visibility = if (p == Panel.COLOR) View.VISIBLE else View.GONE
        sizePanel.visibility = if (p == Panel.SIZE) View.VISIBLE else View.GONE
        refreshTools()
        if (p == Panel.SIZE) syncSlider()
    }

    private fun style(v: ImageView, on: Boolean) {
        v.background = if (on) oval(ink) else null
        v.setColorFilter(if (on) Color.WHITE else ink)
    }

    private fun refreshTools() {
        style(btnPen, draw.tool == DrawView.Tool.PEN)
        style(btnArrow, draw.tool == DrawView.Tool.ARROW)
        style(btnSelect, draw.tool == DrawView.Tool.SELECT)
        style(btnHero, panel == Panel.HERO)
        style(btnColor, panel == Panel.COLOR)
        style(btnSize, panel == Panel.SIZE)
        draw.showSelection = draw.tool == DrawView.Tool.SELECT || panel == Panel.SIZE
    }

    private fun refreshHistory() {
        btnUndo.alpha = if (draw.canUndo()) 1f else 0.3f
        btnRedo.alpha = if (draw.canRedo()) 1f else 0.3f
    }

    private fun refreshColors() {
        for ((c, dot) in colorDots) {
            val selected = c == draw.color
            dot.background = oval(c, if (selected) ink else 0x33000000, if (selected) d(3f) else 1)
        }
    }

    private fun syncSlider() {
        seek.progress = draw.currentProgress()
        updateLabel()
    }

    private fun updateLabel() {
        val s = draw.selected
        sizeLabel.text = when (s) {
            is HeroItem -> "Hero ${(s.size * 100f).roundToInt()}%"
            null -> "Garis baru"
            else -> "Garis ${(s.size / context.resources.displayMetrics.density).roundToInt()} dp"
        }
    }
}
