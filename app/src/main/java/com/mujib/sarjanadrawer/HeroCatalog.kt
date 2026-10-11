package com.mujib.sarjanadrawer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

data class Hero(val code: String, val name: String, val fileName: String, val keys: List<String>)

object HeroCatalog {
    const val HERO_DIR = "heroes"
    const val LOGO = "mujib_logo.png"
    const val AQ_ICON = "icons/aq_icon.png"

    val heroes = listOf(
        Hero("bk", "Barbarian King", "Hero_Skin_BK_Lunar_King_2_shadow.png", listOf("_BK_")),
        Hero("aq", "Archer Queen", "Hero_Skin_AQ_Lunar_Queen_1.png", listOf("_AQ_")),
        Hero("mp", "Minion Prince", "league_prince_pose02_noshadows.png", listOf("league_prince", "MinionPrince")),
        Hero("gw", "Grand Warden", "Hero_Skin_GW_Warrior_Warden_1_grass.png", listOf("_GW_")),
        Hero("rc", "Royal Champion", "RoyalChampion_LNY_03_4k.png", listOf("RoyalChampion")),
        Hero("dd", "Dragon Duke", "DragonDuke_f012_4k.png", listOf("DragonDuke"))
    )

    private val cache = HashMap<String, Bitmap>()

    /** Cari file hero di assets/heroes. Nama persis dipakai dulu, kalau tidak ada cari yang memuat kata kunci hero. */
    fun resolveAsset(ctx: Context, hero: Hero): String? {
        val list = try {
            ctx.applicationContext.assets.list(HERO_DIR)
        } catch (e: Exception) {
            null
        } ?: return null
        if (list.contains(hero.fileName)) return hero.fileName
        return list.firstOrNull { f ->
            f.endsWith(".png", ignoreCase = true) && hero.keys.any { f.contains(it, ignoreCase = true) }
        }
    }

    /** Ikon kepala Archer Queen untuk tombol hero di toolbar. Null kalau file ikon tidak ada. */
    fun loadAqIcon(ctx: Context): Bitmap? = try {
        ctx.applicationContext.assets.open(AQ_ICON).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    fun isAvailable(ctx: Context, hero: Hero): Boolean = resolveAsset(ctx, hero) != null

    fun load(ctx: Context, hero: Hero): Bitmap? {
        cache[hero.code]?.let { return it }
        val name = resolveAsset(ctx, hero) ?: return null
        return try {
            ctx.applicationContext.assets.open("$HERO_DIR/$name").use { BitmapFactory.decodeStream(it) }
                ?.also { cache[hero.code] = it }
        } catch (e: Exception) {
            null
        }
    }

    fun loadLogo(ctx: Context): Bitmap? = try {
        ctx.applicationContext.assets.open(LOGO).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    fun hasLogo(ctx: Context): Boolean = try {
        ctx.applicationContext.assets.open(LOGO).close()
        true
    } catch (e: Exception) {
        false
    }

    /** Perkecil bertahap (dibagi dua) supaya logo kecil tetap halus, tidak bergerigi. */
    private fun stepDown(src: Bitmap, target: Int): Bitmap {
        var b = src
        while (b.width / 2 >= target) {
            b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        }
        return Bitmap.createScaledBitmap(b, target, target, true)
    }

    /** Bitmap bulat untuk bubble. Kalau logo belum ada, dipakai huruf M di lingkaran hitam. */
    fun bubbleBitmap(ctx: Context, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = size / 2f
        val logo = loadLogo(ctx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (logo == null) {
            paint.color = 0xFF1C1C1E.toInt()
            c.drawCircle(r, r, r, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = size * 0.5f
            paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText("M", r, r - (paint.descent() + paint.ascent()) / 2f, paint)
        } else {
            paint.color = Color.WHITE
            c.drawCircle(r, r, r, paint)
            val inner = (size * 0.92f).toInt()
            val off = (size - inner) / 2f
            val side = minOf(logo.width, logo.height)
            val crop = Bitmap.createBitmap(logo, (logo.width - side) / 2, (logo.height - side) / 2, side, side)
            val scaled = stepDown(crop, inner)
            val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
            p2.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            c.save()
            c.translate(off, off)
            c.drawCircle(inner / 2f, inner / 2f, inner / 2f, p2)
            c.restore()
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2f
        ring.color = 0x33000000
        c.drawCircle(r, r, r - 1f, ring)
        return out
    }
}
