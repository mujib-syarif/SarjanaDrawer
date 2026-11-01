package com.mujib.sarjanadrawer

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "com.mujib.sarjanadrawer.STOP"
        const val EXTRA_CODE = "capture_code"
        const val EXTRA_DATA = "capture_data"
        private const val CHANNEL = "bubble"
        private const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: ImageView? = null
    private var bubbleLp: LayoutParams? = null
    private var drawView: DrawView? = null
    private var toolbar: ToolbarView? = null
    private var toolbarLp: LayoutParams? = null
    private var drawing = false
    private var tbX = 0f
    private var tbY = 0f
    private var tbPlaced = false

    // Posisi bubble disimpan sebagai sisi + pecahan tinggi, supaya tetap di tepi layar saat HP diputar.
    private var bubbleRight = true
    private var bubbleFracY = 1f / 3f
    private var lastW = 0
    private var lastH = 0

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handler.post { checkScreen() }
        }
    }

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val frameLock = Any()
    private var latest: Image? = null
    private var capW = 0
    private var capH = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        val hasCapture = code == Activity.RESULT_OK && data != null

        createChannel()
        val notification = buildNotification()
        try {
            if (hasCapture) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // Mis. izin dicabut atau Android menolak tipe foreground service.
            stopSelf()
            return START_NOT_STICKY
        }

        if (hasCapture && projection == null) setupProjection(code, data!!)
        if (bubble == null) showBubble()
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        exitDraw()
        bubble?.let { safeRemove(it) }
        bubble = null
        releaseProjection()
        running = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ukuran layar kadang baru terbaca benar sesaat setelah rotasi, jadi dicek dua kali.
        handler.postDelayed({ checkScreen() }, 300)
        handler.postDelayed({ checkScreen() }, 900)
    }

    /** Dipanggil saat ukuran layar berubah (rotasi): bubble, toolbar, dan area tangkap layar disesuaikan. */
    private fun checkScreen() {
        val (w, h) = realSize()
        if (w == lastW && h == lastH) return
        lastW = w
        lastH = h
        placeBubble()
        val tb = toolbar
        val tlp = toolbarLp
        if (tb != null && tlp != null) {
            clampToolbar(tb)
            tlp.x = tbX.toInt()
            tlp.y = tbY.toInt()
            try {
                wm.updateViewLayout(tb, tlp)
            } catch (e: Exception) {
            }
        }
        if (projection != null) resizeCapture()
    }

    private fun placeBubble() {
        val v = bubble ?: return
        val lp = bubbleLp ?: return
        val (sw, sh) = realSize()
        val size = lp.width
        lp.x = if (bubbleRight) (sw - size).coerceAtLeast(0) else 0
        lp.y = (bubbleFracY * (sh - size)).toInt().coerceIn(0, (sh - size).coerceAtLeast(0))
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
        }
    }

    // ---------- notifikasi ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bubble Sarjana Drawer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Sarjana Drawer aktif")
            .setContentText("Sentuh bubble untuk mencoret layar")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_close), "Matikan", stop
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    // ---------- bubble ----------

    @Suppress("DEPRECATION")
    private fun realSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private fun showBubble() {
        // Logo tampil 28dp; jendela 44dp supaya area sentuh tetap nyaman.
        val size = dp(44f)
        val pad = dp(8f)
        val iv = ImageView(this)
        iv.setPadding(pad, pad, pad, pad)
        iv.setImageBitmap(HeroCatalog.bubbleBitmap(this, size - 2 * pad))
        iv.alpha = 0.95f
        val (sw, sh) = realSize()
        lastW = sw
        lastH = sh
        // Layar penuh + cutout short edges: koordinat x/y sama dengan koordinat layar fisik,
        // jadi tepi kanan di lanskap tidak tergeser oleh poni kamera atau bar navigasi.
        val lp = LayoutParams(
            size, size,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (bubbleRight) sw - size else 0
        lp.y = (bubbleFracY * (sh - size)).toInt()

        iv.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = lp.x
                        startY = lp.y
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && hypot(dx, dy) > dpf(6f)) moved = true
                        if (moved) {
                            val (cw, ch) = realSize()
                            lp.x = (startX + dx).toInt().coerceIn(0, (cw - size).coerceAtLeast(0))
                            lp.y = (startY + dy).toInt().coerceIn(0, (ch - size).coerceAtLeast(0))
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) snapBubble(v, lp, size) else toggleDraw()
                    }
                }
                return true
            }
        })
        try {
            wm.addView(iv, lp)
        } catch (e: Exception) {
            // Izin "tampil di atas aplikasi lain" dicabut: hentikan layanan, jangan crash.
            stopSelf()
            return
        }
        bubble = iv
        bubbleLp = lp
    }

    private fun snapBubble(v: View, lp: LayoutParams, size: Int) {
        val (sw, sh) = realSize()
        bubbleRight = lp.x + size / 2 >= sw / 2
        bubbleFracY = if (sh > size) (lp.y.toFloat() / (sh - size)).coerceIn(0f, 1f) else 0f
        val target = if (bubbleRight) (sw - size).coerceAtLeast(0) else 0
        val anim = ValueAnimator.ofInt(lp.x, target)
        anim.duration = 180
        anim.addUpdateListener {
            lp.x = it.animatedValue as Int
            try {
                wm.updateViewLayout(v, lp)
            } catch (e: Exception) {
            }
        }
        anim.start()
    }

    // ---------- mode coret ----------

    private fun toggleDraw() {
        if (drawing) exitDraw() else enterDraw()
    }

    private fun enterDraw() {
        if (drawing) return
        val dv = DrawView(this)
        val dlp = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        dlp.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        try {
            wm.addView(dv, dlp)
        } catch (e: Exception) {
            toast("Tidak bisa membuka layar coret. Periksa izin tampil di atas aplikasi lain.")
            return
        }
        drawView = dv

        val tb = ToolbarView(
            this, dv,
            onShot = { captureScreenshot() },
            onClose = { exitDraw() },
            onMove = { dx, dy -> moveToolbar(dx, dy) }
        )
        val tlp = LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        tlp.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        tlp.gravity = Gravity.TOP or Gravity.START
        tbPlaced = false
        tb.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val (sw, sh) = realSize()
            if (!tbPlaced && v.width > 0) {
                tbPlaced = true
                tbX = (sw - v.width) / 2f
                tbY = sh * 0.66f
            }
            val oldX = tbX
            val oldY = tbY
            clampToolbar(v)
            if (oldX != tbX || oldY != tbY || tlp.x != tbX.toInt() || tlp.y != tbY.toInt()) {
                tlp.x = tbX.toInt()
                tlp.y = tbY.toInt()
                try {
                    wm.updateViewLayout(v, tlp)
                } catch (e: Exception) {
                }
            }
        }
        tlp.x = dp(12f)
        tlp.y = realSize().second / 2
        try {
            wm.addView(tb, tlp)
        } catch (e: Exception) {
            safeRemove(dv)
            drawView = null
            toast("Tidak bisa membuka toolbar. Periksa izin tampil di atas aplikasi lain.")
            return
        }
        toolbar = tb
        toolbarLp = tlp

        bubble?.visibility = View.GONE
        drawing = true
    }

    private fun clampToolbar(v: View) {
        val (sw, sh) = realSize()
        tbX = tbX.coerceIn(0f, (sw - v.width).toFloat().coerceAtLeast(0f))
        tbY = tbY.coerceIn(0f, (sh - v.height).toFloat().coerceAtLeast(0f))
    }

    private fun moveToolbar(dx: Float, dy: Float) {
        val tb = toolbar ?: return
        val lp = toolbarLp ?: return
        tbX += dx
        tbY += dy
        clampToolbar(tb)
        lp.x = tbX.toInt()
        lp.y = tbY.toInt()
        try {
            wm.updateViewLayout(tb, lp)
        } catch (e: Exception) {
        }
    }

    private fun exitDraw() {
        toolbar?.let { safeRemove(it) }
        drawView?.let { safeRemove(it) }
        toolbar = null
        toolbarLp = null
        drawView = null
        drawing = false
        bubble?.visibility = View.VISIBLE
    }

    private fun safeRemove(v: View) {
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }

    // ---------- screenshot ----------

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ rd ->
            val img = try {
                rd.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (img != null) {
                synchronized(frameLock) {
                    latest?.close()
                    latest = img
                }
            }
        }, handler)
        return r
    }

    private fun setupProjection(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data)
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { releaseProjection() }
            }
        }, handler)
        val (w, h) = realSize()
        capW = w
        capH = h
        val reader = newReader(w, h)
        imageReader = reader
        virtualDisplay = mp.createVirtualDisplay(
            "SarjanaDrawer", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler
        )
        projection = mp
    }

    private fun resizeCapture() {
        val vd = virtualDisplay ?: return
        val (w, h) = realSize()
        if (w == capW && h == capH) return
        capW = w
        capH = h
        synchronized(frameLock) {
            latest?.close()
            latest = null
        }
        imageReader?.close()
        val reader = newReader(w, h)
        imageReader = reader
        vd.resize(w, h, resources.displayMetrics.densityDpi)
        vd.surface = reader.surface
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        synchronized(frameLock) {
            latest?.close()
            latest = null
        }
        imageReader?.close()
        imageReader = null
        val mp = projection
        projection = null
        try {
            mp?.stop()
        } catch (e: Exception) {
        }
    }

    private fun captureScreenshot() {
        if (projection == null) {
            val request = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_REQUEST_CAPTURE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(request)
            return
        }
        val tb = toolbar
        val dv = drawView
        tb?.visibility = View.INVISIBLE
        dv?.capturing = true
        handler.postDelayed({
            val bmp = try {
                grabFrame()
            } catch (e: Exception) {
                null
            }
            tb?.visibility = View.VISIBLE
            dv?.capturing = false
            if (bmp == null) toast("Gagal mengambil layar. Coba lagi.") else saveBitmap(bmp)
        }, 380)
    }

    private fun grabFrame(): Bitmap? = synchronized(frameLock) {
        val img = latest ?: return@synchronized null
        val plane = img.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride
        val w = img.width
        val h = img.height
        val padded = Bitmap.createBitmap(rs / ps, h, Bitmap.Config.ARGB_8888)
        buf.rewind()
        padded.copyPixelsFromBuffer(buf)
        if (padded.width == w) {
            padded
        } else {
            val out = Bitmap.createBitmap(padded, 0, 0, w, h)
            padded.recycle()
            out
        }
    }

    private fun saveBitmap(bmp: Bitmap) {
        Thread {
            val name = "Sarjana_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SarjanaDrawer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri == null) {
                handler.post { toast("Gagal menyimpan screenshot") }
                return@Thread
            }
            try {
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cv.clear()
                cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, cv, null, null)
                Stats.inc(this, Stats.SHOTS)
                handler.post { toast("Screenshot tersimpan di Galeri (Pictures/SarjanaDrawer)") }
            } catch (e: Exception) {
                handler.post { toast("Gagal menyimpan screenshot") }
            }
        }.start()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
