package com.mujib.sarjanadrawer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_CAPTURE = 1001
        private const val REQ_NOTIF = 1002
        const val ACTION_REQUEST_CAPTURE = "com.mujib.sarjanadrawer.REQUEST_CAPTURE"
    }

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_CAPTURE) {
            intent.action = null
            requestCapture()
        }
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildUi())
    }

    // ---------- UI (iOS, monokrom, kartu modular) ----------

    private fun c(id: Int): Int = getColor(id)

    private fun label(t: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = sp
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        return tv
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        l.background = roundRect(c(R.color.card), dpf(20f), c(R.color.hairline), 1)
        return l
    }

    private fun tile(title: String, value: String, onClick: (() -> Unit)? = null): LinearLayout {
        val t = card()
        t.addView(label(value, 24f, c(R.color.ink), true))
        t.addView(label(title, 13f, c(R.color.ink2)))
        if (onClick != null) t.setOnClickListener { onClick() }
        return t
    }

    private fun rowOf(vararg tiles: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        tiles.forEachIndexed { i, t ->
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(if (i == 0) 0 else dp(5f), dp(5f), if (i == tiles.size - 1) 0 else dp(5f), dp(5f))
            r.addView(t, lp)
        }
        return r
    }

    private fun buildUi(): View {
        val overlayOk = Settings.canDrawOverlays(this)
        val readyHeroes = HeroCatalog.heroes.count { HeroCatalog.isAvailable(this, it) }
        val missing = HeroCatalog.heroes.filter { !HeroCatalog.isAvailable(this, it) }
        val hasLogo = HeroCatalog.hasLogo(this)
        val running = OverlayService.running

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(c(R.color.bg))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18f), dp(24f), dp(18f), dp(28f))
        scroll.addView(col)

        col.addView(label("Sarjana Drawer", 34f, c(R.color.ink), true))
        val sub = label("Coret layar, panah, dan hero", 15f, c(R.color.ink2))
        sub.setPadding(0, 0, 0, dp(14f))
        col.addView(sub)

        col.addView(rowOf(
            tile("Bubble", if (running) "Aktif" else "Mati"),
            tile("Tema", "iOS")
        ))

        val logoTile = card()
        val logoImg = ImageView(this)
        logoImg.setImageBitmap(HeroCatalog.bubbleBitmap(this, dp(44f)))
        logoTile.addView(logoImg, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        logoTile.addView(label(if (hasLogo) "Logo siap" else "Logo belum ada", 13f, c(R.color.ink2)))
        col.addView(rowOf(
            tile("Gambar hero siap", "$readyHeroes/${HeroCatalog.heroes.size}"),
            logoTile
        ))

        col.addView(rowOf(
            tile("Izin tampil di atas app", if (overlayOk) "Aktif" else "Belum") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            },
            tile("Screenshot tersimpan", Stats.get(this, Stats.SHOTS).toString())
        ))

        val stats = card()
        val srow = LinearLayout(this)
        srow.orientation = LinearLayout.HORIZONTAL
        fun stat(title: String, v: Int) {
            val s = LinearLayout(this)
            s.orientation = LinearLayout.VERTICAL
            s.gravity = Gravity.CENTER_HORIZONTAL
            s.addView(label(v.toString(), 22f, c(R.color.ink), true))
            s.addView(label(title, 12f, c(R.color.ink2)))
            srow.addView(s, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        stat("Coretan dan panah", Stats.get(this, Stats.STROKES))
        stat("Hero ditaruh", Stats.get(this, Stats.HEROES))
        stat("Screenshot", Stats.get(this, Stats.SHOTS))
        stats.addView(srow)
        val sl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sl.setMargins(0, dp(5f), 0, dp(5f))
        col.addView(stats, sl)

        if (!hasLogo || missing.isNotEmpty()) {
            val note = card()
            val sb = StringBuilder("Belum ada di folder assets:\\n")
            if (!hasLogo) sb.append("- ${HeroCatalog.LOGO}\\n")
            missing.forEach { sb.append("- heroes/${it.fileName}\\n") }
            note.addView(label(sb.toString().trim(), 12f, c(R.color.ink2)))
            val nl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            nl.setMargins(0, dp(5f), 0, dp(5f))
            col.addView(note, nl)
        }

        val btn = label(if (running) "Matikan bubble" else "Aktifkan bubble", 17f, c(R.color.bg), true)
        btn.gravity = Gravity.CENTER
        btn.background = roundRect(c(R.color.ink), dpf(28f))
        btn.setOnClickListener { if (running) deactivate() else activate() }
        val bl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f))
        bl.setMargins(0, dp(16f), 0, dp(8f))
        col.addView(btn, bl)

        col.addView(label("Setelah aktif, sentuh bubble untuk mulai mencoret. Izin screenshot hanya diminta saat tombol screenshot digunakan.", 12f, c(R.color.ink2)))
        return scroll
    }

    // ---------- aktivasi ----------

    private fun activate() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin 'Tampilkan di atas aplikasi lain', lalu kembali.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        startOverlay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) startOverlay()
    }

    private fun startOverlay() {
        startForegroundService(Intent(this, OverlayService::class.java))
        moveTaskToBack(true)
    }

    private fun requestCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_CODE, resultCode)
                putExtra(OverlayService.EXTRA_DATA, data)
            }
            startService(serviceIntent)
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Izin screenshot tidak diberikan. Fitur coret layar tetap aktif.", Toast.LENGTH_LONG).show()
            setContentView(buildUi())
        }
    }

    private fun deactivate() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        ui.postDelayed({ setContentView(buildUi()) }, 400)
    }
}
