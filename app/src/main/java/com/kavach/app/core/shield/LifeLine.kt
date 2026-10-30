package com.kavach.app.core.shield

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🫀 LifeLine — v5.3.0 "LONG-LIFE GUARDIAN" (chief order:
 *    device mujhe maare toh pakad lo, uptime gin-te raho, device NORMAL chale).
 *
 * SAB passive — koi naya alarm/wakelock NAHI:
 *  • beat()      → service ke existing 10-min heartbeat se chalti (0 extra wakeup)
 *  • birthAudit()→ service ke onCreate se: pichhli-beat ke baad >12min gap?
 *                  → process "MARI thi" — guess: CRASH (log fresh?) ya OEM-MURDER?
 *                  → kstore/logs/life.csv me likho; din me 3+ deaths → ek eqaali
 *                    advisory (sirf 1 msg/din — silence-rule!)
 *  • report()    → /life health-card, section() → /health me 3-line jhalak
 */
object LifeLine {

    private val TS = SimpleDateFormat("dd-MMM HH:mm", Locale.US)
    private fun p(ctx: Context) = com.kavach.app.security.SecureStorage.prefs(ctx)
    private fun dayNow(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)

    /** 🫀 har 10-min beat (service heartbeat se) — uptime-minutes ginte raho */
    fun beat(ctx: Context) {
        runCatching {
            val now = System.currentTimeMillis()
            val e = p(ctx).edit()
            if (p(ctx).getInt("life_day", -1) != dayNow()) {
                e.putInt("life_day", dayNow()).putInt("life_upmin", 0).putInt("life_deaths", 0)
            }
            val last = p(ctx).getLong("life_alive_ts", 0L)
            if (last > 0) {
                val mins = ((now - last) / 60000).coerceIn(0, 15).toInt()
                e.putInt("life_upmin", p(ctx).getInt("life_upmin", 0) + mins)
            }
            e.putLong("life_alive_ts", now).apply()
        }
    }

    /** 🔍 service-birth audit: "pichhli baar kab tak zinda thi?" — gap > 12min = maut hui thi
     *  @return owner ko bhejne wali advisory (ya null) */
    fun birthAudit(ctx: Context): String? = runCatching {
        val now = System.currentTimeMillis()
        val last = p(ctx).getLong("life_alive_ts", 0L)
        val bootTs = p(ctx).getLong("cg_boot_ts", System.currentTimeMillis())
        if (last <= 0L) return null
        val gapMin = ((now - last) / 60000).toInt()
        if (gapMin <= 12 || bootTs <= last) return null   // normal 10-min beat-gap ya same-process — koi maut nahi

        val guess = if (crashRecent(ctx, last, now)) "CRASH 💥 (fix-record likha)" else "OEM-MURDER? 🤔 (system ne maara)"
        com.kavach.app.core.vault.KStore.csv(ctx, "life", "event;gapmin;guess", "DEATH", "$gapMin", guess)
        val d = p(ctx).getInt("life_deaths", 0) + 1
        p(ctx).edit().putInt("life_deaths", d).apply()

        if (d >= 3 && p(ctx).getInt("life_adv_day", -1) != dayNow()) {
            p(ctx).edit().putInt("life_adv_day", dayNow()).apply()
            "⚠️ 🫀 LIFE-ALERT: aaj $d baar process mari (last-gap ${gapMin}min · shak: $guess)\n" +
                "▸ Device maarne ki koshish me lagta hai — /survive kholo:\n" +
                "   🔋 BATTERY-EXEMPT + 🧟 ANTI-HIBERNATE + 📟 OEM-RESCUE — teeno card ON ✔\n" +
                "▸ Kavach phir-bhi rebirth le raha hai (watchdog chain) — magar shield-gap aa sakta hai 🛡"
        } else null
    }.getOrNull()

    private fun crashRecent(ctx: Context, fromTs: Long, toTs: Long): Boolean {
        val f = File(ctx.filesDir, "crash.log")
        return f.exists() && f.lastModified() in (fromTs + 1)..(toTs + 60_000)
    }

    /** 🫀 3-line jhalak — /health card ke andar */
    fun section(ctx: Context): String = runCatching {
        val safe = com.kavach.app.core.guard.CrashGuard.isSafeMode(ctx)
        "🫀 LIFE-TODAY: ⏱ up ${p(ctx).getInt("life_upmin", 0)}min • 💀 deaths ${p(ctx).getInt("life_deaths", 0)} • " +
            if (safe) "🟡 SAFE-MODE (half-load)" else "🟢 normal-power"
    }.getOrDefault("🫀 LIFE: —")

    /** 🩺 /life — asli long-life health card */
    fun report(ctx: Context): String = runCatching {
        val up = p(ctx).getInt("life_upmin", 0)
        val deaths = p(ctx).getInt("life_deaths", 0)
        val cal = java.util.Calendar.getInstance()
        val dayMin = (cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)).coerceAtLeast(1)
        val pct = (up * 100 / dayMin).coerceIn(0, 100)
        val (crashes, anr, boots) = com.kavach.app.core.guard.CrashGuard.counts(ctx)
        val safe = com.kavach.app.core.guard.CrashGuard.isSafeMode(ctx)
        val safeSince = p(ctx).getLong("cg_safe_since", 0L)
        val aliveAgo = ((System.currentTimeMillis() - p(ctx).getLong("life_alive_ts", 0L)) / 60000)
        val memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576

        val tail = com.kavach.app.core.vault.KStore.csvTail(ctx, "life", 5)
        val tailTxt = if (tail.isEmpty()) "   😇 koi death/event abhi tak nahi"
        else tail.asReversed().joinToString("\n") { r ->
            "   • ${r.getOrElse(0) { "" }.takeLast(5)} ${r.getOrElse(1) { "" }}" +
                (if (r.size > 2 && r[2] != "0") " (gap ${r[2]}min)" else "") +
                " — ${r.getOrElse(3) { "" }}".take(48)
        }

        """
🫀 KAVACH LIFE-CARD 🫀 (LONG-LIFE GUARDIAN v5.3.0)
══════════
📅 AAJ:
  ⏱ uptime: ~${up}min / ${dayMin}min  (${pct}% ${if (pct >= 95) "🏆 SOLID" else if (pct >= 80) "🟢 theek" else "🟠 device maar raha!"})
  💀 deaths today: $deaths ${if (deaths == 0) "(kalam-perfect ✔)" else ""}
🛡 GUARDS:
  💥 total crashes logged: $crashes • 💤 ANR-watch hits: $anr
  🌀 SAFE-MODE: ${if (safe) "🟡 ON (since ${TS.format(Date(safeSince))} — 30min clean pe auto-exit)" else "🟢 OFF (full-power)"}
  ⚑ total rebirths (is install me): $boots
📟 DEVICE-NORMAL proof (chief rule!):
  🪶 app RAM abhi: ~${memMb}MB • wakeups: 0 extra (purane tick se hi chalta)
  💓 last alive-beat: ${if (aliveAgo <= 11) "$aliveAgo min pehle ✔" else "⚠ $aliveAgo min (beat late!)"}
📜 LAST EVENTS (life.csv):
$tailTxt
══════════
💡 instructions: /crashes = crash detail • /survive = OEM-rescue cards
        """.trimIndent()
    }.getOrElse { "⚠ /life fail: ${it.message}" }
}
