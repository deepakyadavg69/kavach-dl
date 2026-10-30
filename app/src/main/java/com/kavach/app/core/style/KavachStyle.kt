package com.kavach.app.core.style

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import com.kavach.app.core.telegram.TelegramApi
import com.kavach.app.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🎨 KavachStyle — v5.1.0 "BOT-STYLE" (chief order: bot ka naam/DP/desc device ke naam pe — apne aap!)
 *
 * 1) 🏷 setMyName    = device ka naam (Settings→device_name, fallback MODEL)
 * 2) 📄 setMyDescription  = poora device-detail (brand/model/Android/RAM/screen/battery) — 512 cap
 * 3) 💬 setMyShortDescription = "manage karne ke liye ready" line — 120 cap
 * 4) 🖼 setMyProfilePhoto = assets/kavach_dp.png (neon-shield) + device-name RUNTIME draw (har device apna naam kha-de!)
 *
 * Chalta: 📶 pair ke baad, 🔄 har app-boot (stale ho tabhi — hash check), 🎨 Setup-card se force.
 * Sirf OWNER-BOT; fail-silent (network-off = skip, agle boot pe try).
 */
object KavachStyle {

    private const val KEY_HASH = "botstyle_hash"

    // ═══════════════ 📛 device-name ═══════════════
    /** v5.5.0 FINAL chief-order: bot-naam = **brand ka ACHHA naam** (id nahi!) —
     *  "realme ka ho toh realme aise" 🏷 (model-id RMX… wagarah naam me NAHI chalegi,
     *  poora model-detail description me rehta hai) */
    fun deviceLabel(ctx: Context): String = prettyBrand()

    private fun prettyBrand(): String = when (Build.MANUFACTURER.lowercase().trim()) {
        "realme" -> "Realme"
        "iqoo" -> "iQOO"
        "vivo" -> "Vivo"
        "samsung" -> "Samsung"
        "xiaomi" -> "Xiaomi"
        "redmi" -> "Redmi"
        "poco" -> "POCO"
        "oppo" -> "OPPO"
        "oneplus" -> "OnePlus"
        "motorola" -> "Motorola"
        "infinix" -> "Infinix"
        "tecno" -> "Tecno"
        "itel" -> "itel"
        "lava" -> "Lava"
        "google" -> "Google"
        "honor" -> "HONOR"
        "asus" -> "Asus"
        "nothing" -> "Nothing"
        "nokia" -> "Nokia"
        "sony" -> "Sony"
        "micromax" -> "Micromax"
        "hmd global" -> "Nokia"
        else -> Build.MANUFACTURER.trim().lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    // ═══════════════ 📄 details (512-cap description) ═══════════════
    private fun totalRamGb(ctx: Context): String = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        "%.1f".format(mi.totalMem / 1073741824.0) + "GB"
    }.getOrDefault("—")

    fun deviceDescription(ctx: Context, label: String): String {
        val dm = ctx.resources.displayMetrics
        val scr = "${dm.widthPixels}x${dm.heightPixels}"
        val s = "🛡 KAVACH — $label ka digital bodyguard\n" +
                "📱 ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "🧠 RAM ${
                    totalRamGb(ctx)
                } • 🖥 $scr\n" +
                "🔋 batt-mon • 📍 GPS • 📸 cams siren-ready — kisi bhi haalat me manage-command ke liye READY ✅"
        return s.take(500)
    }

    fun shortDesc(label: String): String =
        "✅ $label — manage karne ke liye READY ( /menu dabao 🎮 )".take(118)

    // ═══════════════ 🖼 DP RENDER (neon-shield + device-naam) ═══════════════
    fun renderDp(ctx: Context, label: String): ByteArray? = runCatching {
        val base = BitmapFactory.decodeStream(ctx.assets.open("kavach_dp.png")) ?: return null
        val W = 512
        val bmp = Bitmap.createScaledBitmap(base, W, W, true).copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(bmp)
        // ── neeche naam-patti (dark banner + glow text) ──
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#071428"); alpha = 225 }
        cv.drawRoundRect(RectF(20f, 398f, 492f, 486f), 26f, 26f, bar)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8"); style = Paint.Style.STROKE; strokeWidth = 4f }
        cv.drawRoundRect(RectF(20f, 398f, 492f, 486f), 26f, 26f, rim)
        var size = 46f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val name = label.take(20)
        while (size > 22f) { tp.textSize = size; if (tp.measureText(name) <= 448f) break; size -= 2f }
        // glow behind + text
        val glow = Paint(tp).apply { color = Color.parseColor("#38BDF8"); maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.OUTER) }
        cv.drawText(name, 256f, 459f, glow)
        cv.drawText(name, 256f, 459f, tp)
        val os = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 92, os)
        os.toByteArray()
    }.getOrNull()

    // ═══════════════ 🔄 SYNC (stale-check + apply) ═══════════════
    /** @param api null ho to khud banata hai (boot-path). force=true → hash mat dekho (Setup-card) */
    suspend fun syncNow(ctx: Context, api: TelegramApi?, force: Boolean): String = withContext(Dispatchers.IO) {
        val label = deviceLabel(ctx)
        val desc = deviceDescription(ctx, label)
        val hash = (label.length xor desc.length xor desc.hashCode()).toString()
        if (!force && SecureStorage.prefs(ctx).getString(KEY_HASH, "") == hash)
            return@withContext "🎨 BOT-STYLE: already synced ✔ ($label)"
        val a = api ?: runCatching {
            TelegramApi(SecureStorage.getToken(ctx))
        }.getOrNull() ?: return@withContext "⚠️ bot-token nahi mila (pair/net?) — baad me auto-retry"
        val done = StringBuilder("🎨 BOT-STYLE sync → $label\n")
        // 🏷 naam
        runCatching {
            val cur = a.getMyName()
            when {
                cur != label && a.setMyName(label) -> done.append("🏷 naam set: $label ✅\n")
                cur == label -> done.append("🏷 naam wahi hai ✔\n")
                else -> done.append("🏷 naam set-fail (Bot API mana ❌)\n")
            }
        }
        // 📄 desc + 💬 short
        if (a.setMyDescription(desc, shortDesc(label))) done.append("📄 description + 💬 short ✅\n")
        // 🖼 DP
        val dp = renderDp(ctx, label)
        if (dp != null) {
            if (a.setMyProfilePhoto(dp)) done.append("🖼 DP upload (naam-likha neon-shield) ✅\n")
            else done.append("🖼 DP fail — API ne mana (photo perm?) ❌\n")
        } else done.append("🖼 DP render fail (asset?) ❌\n")
        SecureStorage.prefs(ctx).edit().putString(KEY_HASH, hash).apply()
        done.append("──────────\n💡 Setup-card se kabhi bhi force-refresh!")
        done.toString().trim()
    }
}
