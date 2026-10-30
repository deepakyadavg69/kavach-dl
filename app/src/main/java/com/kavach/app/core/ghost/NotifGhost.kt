package com.kavach.app.core.ghost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.RemoteViews
import com.kavach.app.KavachApp
import com.kavach.app.R
import com.kavach.app.core.vault.KStore
import com.kavach.app.security.SecureStorage

/**
 * 👻 NotifGhost — foreground-notification ka MAX-minimalist mode (v3.3.0).
 *
 * SACH (honest): 100% invisible FGS-notification = modern Android pe impossible
 * (system user ko guarantee deta background-app dikhe — task-manager entry rahegi).
 * HAMARA max: 1dp blank content + transparent 1px icon + MIN channel =
 * shade me "gayab jaisa" patli-sui row. Android 13+ pe user khud swipe-dismiss
 * bhi kar sakta — SERVICE phir bhi chalti (hamare watchdog bhi guard karte).
 *
 * Modes:
 *  • /ghostnotif on  → ghost layout+icon (default normal minimal)
 *  • /notifguard off → user ne hatayi to RESPECT karo (auto-repost NAHI; keepalive chalta rahe)
 */
object NotifGhost {

    const val GHOST_CHANNEL = "sys_monitor_g"

    private fun p(ctx: Context) = SecureStorage.prefs(ctx)
    fun ghostOn(ctx: Context) = p(ctx).getBoolean("notif_ghost", false)
    fun setGhost(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("notif_ghost", v).apply()
    fun respectHide(ctx: Context) = p(ctx).getBoolean("notif_respect_hide", false)
    fun setRespect(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("notif_respect_hide", v).apply()

    fun channelFor(ctx: Context): String = if (ghostOn(ctx)) GHOST_CHANNEL else KavachApp.CHANNEL_ID

    /** ghost channel banao jb zarurat (importance baad me change nahi hoti — isliye separate id) */
    fun ensureChannel(ctx: Context) {
        if (!ghostOn(ctx)) return
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(GHOST_CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(GHOST_CHANNEL, "svc", NotificationManager.IMPORTANCE_MIN).apply {
                        setSound(null, null); enableVibration(false); setShowBadge(false); lockscreenVisibility = 0
                    }
                )
            }
        }
    }

    fun ghostView(ctx: Context): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_ghost)

    fun iconRes(ctx: Context): Int =
        if (ghostOn(ctx)) R.drawable.ic_dot else android.R.drawable.ic_menu_info_details

    /** 🔁 migration helper: ghost → normal channel pe turant repost (v5.5.0) */
    fun repostNormalChannel(ctx: Context) {
        ensureChannel(ctx)
        com.kavach.app.core.service.KavachService.instance?.repostForeground()
    }

    /** 🚫 current target-channel user/OEM se BLOCKED toh hai? */
    private fun channelBlocked(ctx: Context): Boolean = runCatching {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return true
        if (android.os.Build.VERSION.SDK_INT >= 26)
            nm.getNotificationChannel(channelFor(ctx))?.importance == NotificationManager.IMPORTANCE_NONE
        else false
    }.getOrDefault(false)

    /** godTick se (~5 min): shade se notif gayab?
     *  v5.5.0 FINAL chief-rule:
     *   • ek-baar MISSING → repost (doosri lane fallback bhi)
     *   • 15min ke andar PHIR missing = user jaan-bhoojh ke hata raha → 6h RESPECT-window
     *     (service keep-alive stack se zinda; chupchaap kaam chalta rahe 💪)
     *   • respect-window khatam → dobara ek-baar polite repost */
    fun watch(ctx: Context) {
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val mine = nm.activeNotifications.any {
                it.id == KavachApp.NOTIF_ID_SERVICE && it.packageName == ctx.packageName
            }
            val now = System.currentTimeMillis()
            if (mine) { p(ctx).edit().putLong("notif_last_repost", 0L).apply(); return@runCatching }
            val last = p(ctx).getLong("notif_missing_log", 0L)
            if (now - last > 30L * 60_000) {
                p(ctx).edit().putLong("notif_missing_log", now).apply()
                KStore.csv(ctx, "notif_guard", "event;mode", "MISSING",
                    if (respectHide(ctx)) "respect" else if (now < p(ctx).getLong("notif_respect_until", 0L)) "respect-window" else "repost")
            }
            if (respectHide(ctx)) {
                // 🫡 user ne hatayi = unki marzi; service keep-alive stack se zinda (eye-keeper+watchdog)
                if (now - p(ctx).getLong("notif_respect_note", 0L) > 6L * 3600_000) {
                    p(ctx).edit().putLong("notif_respect_note", now).apply()
                    com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO,
                        "notifguard", 6L * 3600_000,
                        "👻 NOTIF-GUARD: notification hatayi gayi (user/OEM) — RESPECT mode: repost NAHI kar raha.\nService keep-alive stack (👁eye-keeper + watchdog) se zinda hai 💪 (/notifguard on = auto-repost)")
                }
                return@runCatching
            }
            // 🫡 v5.5.0: auto RESPECT-window — user baar-baar hata raha hai toh tang mat karo
            if (now < p(ctx).getLong("notif_respect_until", 0L)) return@runCatching
            val lastRepost = p(ctx).getLong("notif_last_repost", 0L)
            if (lastRepost > 0 && now - lastRepost < 15L * 60_000) {
                p(ctx).edit().putLong("notif_respect_until", now + 6L * 3600_000).apply()
                KStore.csv(ctx, "notif_guard", "event;mode", "RESPECT-6H", "hide-repeat pakda")
                com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO,
                    "notifguard", 6L * 3600_000,
                    "🫡 NOTIF-GUARD: aap dobara-chhathi chhoopaa rahe — 6 ghante RESPECT (repost nahi).\nKaam chalta rahega keep-alive se 💪 · 6h baad ek polite repost · turant chahiye toh /notifguard")
                return@runCatching
            }
            p(ctx).edit().putLong("notif_last_repost", now).apply()
            // 🚧 channel BLOCKED mila? → doosri lane se koshish
            if (channelBlocked(ctx)) {
                setGhost(ctx, !ghostOn(ctx))
                ensureChannel(ctx)
                KStore.csv(ctx, "notif_guard", "event;mode", "LANE-FLIP", if (ghostOn(ctx)) "ghost" else "normal")
            }
            // 🔁 auto-repost
            com.kavach.app.core.service.KavachService.instance?.repostForeground()
        }
    }

    fun status(ctx: Context): String =
        "👻 NOTIF-GHOST: mode=" + (if (ghostOn(ctx)) "ULTRA-minimal (1dp+transparent)" else "normal-minimal (MIN channel)") +
            " • guard=" + (if (respectHide(ctx)) "🫡 RESPECT (repost nahi)" else "🔁 auto-repost") +
            "\n   ⚠️ 100% invisible = impossible (system guarantee) — task-manager entry rahegi"
}
