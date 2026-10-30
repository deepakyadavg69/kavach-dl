package com.kavach.app.core.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🛟 CrashGuard — v5.3.0 "LONG-LIFE GUARDIAN" (chief order: app KABHI crash na ho,
 *    crash ho toh seh lo + loop ho toh SAFE-MODE me jinda raho + device NORMAL chale).
 *
 * Kisi bhi unhandled thread-exception par:
 *  1) stack-trace files/crash.log + kstore/logs/crash.csv me likhta hai (cap 200KB)
 *  2) LOOP-GUARD: 10min me 3+ crash → SAFE-MODE ON (heavy engines aaram, bot zinda)
 *  3) 2 second me AlarmManager se KAVACH service auto-restart (silent, popup-less)
 *  4) Android default handler NAHI bulate (koi "keeps stopping" popup nahi)
 *
 * Baaki engine pehle se runCatching-wrapped hai — ye LAST-NET hai.
 */
object CrashGuard {

    private var installed = false

    private const val SAFE_KEY = "cg_safe_mode"
    private const val SAFE_TS = "cg_safe_since"
    private const val HIST = "cg_hist"
    private const val LOOP_WINDOW_MS = 600_000L          // 10 min
    private const val LOOP_LIMIT = 3
    private const val SAFE_EXIT_MS = 30 * 60_000L        // 30 clean-min → full power

    private fun prefs(app: Context) = com.kavach.app.security.SecureStorage.prefs(app)

    fun install(app: Context) {
        if (installed) return
        installed = true
        anrWatchStart(app)   // 🐕 v3.9.7: main-thread 12s+ atakne pe silent-restart
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching { logCrash(app, thread, err) }
            runCatching { noteCrash(app, thread, err) }          // 🌀 v5.3.0 loop-guard + csv
            runCatching { beaconCrash(app, thread, err) }        // 📡 chief ko report
            runCatching { scheduleRestart(app) }
            runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
            System.exit(2)
        }
    }

    // ═══════════ v5.3.0 LONG-LIFE brain ═══════════

    /** ⚑ har service-birth stamp (safe-exit clock + total rebirths) */
    fun stampBoot(app: Context) {
        runCatching {
            prefs(app).edit()
                .putLong("cg_boot_ts", System.currentTimeMillis())
                .putLong("cg_boots", prefs(app).getLong("cg_boots", 0L) + 1)
                .apply()
        }
    }

    fun isSafeMode(app: Context): Boolean =
        runCatching { prefs(app).getBoolean(SAFE_KEY, false) }.getOrDefault(false)

    /** 🌀 crash-timestamp ring — loop mila toh SAFE-MODE chadha do (ek baar) */
    private fun noteCrash(app: Context, t: Thread, e: Throwable) {
        val now = System.currentTimeMillis()
        runCatching {
            com.kavach.app.core.vault.KStore.csv(app, "crash", "thread;class;msg;anchor",
                t.name, e.javaClass.simpleName,
                (e.message ?: "?").take(120), (e.stackTrace.firstOrNull()?.toString() ?: "?").take(120))
        }
        val hist = (prefs(app).getString(HIST, "") ?: "").split(",")
            .filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }.toMutableList()
        hist.add(now)
        val recent = hist.filter { now - it < LOOP_WINDOW_MS }
        prefs(app).edit().putString(HIST, recent.takeLast(10).joinToString(",")).apply()
        if (recent.size >= LOOP_LIMIT && !prefs(app).getBoolean(SAFE_KEY, false)) {
            prefs(app).edit().putBoolean(SAFE_KEY, true).putLong(SAFE_TS, now).apply()
            runCatching {
                com.kavach.app.core.vault.KStore.csv(app, "life", "event;gapmin;guess",
                    "SAFEMODE-IN", "0", "crash-loop x${recent.size} in 10min 🌀")
            }
        }
    }

    /** ⏲ service ke heartbeat se call — 30min clean-uptime → SAFE-MODE se bahar.
     *  TRUE sirf exit-waali baar (service ek "✅ bahar aaya" msg bheje). */
    fun safeTick(app: Context): Boolean {
        if (!isSafeMode(app)) return false
        val boot = prefs(app).getLong("cg_boot_ts", 0L)
        return if (boot > 0 && System.currentTimeMillis() - boot > SAFE_EXIT_MS) {
            prefs(app).edit().putBoolean(SAFE_KEY, false).apply()
            runCatching {
                com.kavach.app.core.vault.KStore.csv(app, "life", "event;gapmin;guess",
                    "SAFEMODE-OUT", "30", "clean-30min ✔ full-power")
            }
            true
        } else false
    }

    /** 🧾 crash-history counts for /life panel */
    fun counts(app: Context): Triple<Int, Int, Int> {   // (crashes, anr, boots)
        val f = File(app.filesDir, "crash.log")
        val txt = runCatching { f.readText() }.getOrDefault("")
        val crashes = txt.split("━━━").count { it.contains("💥") }
        val anr = txt.split("━━━").count { it.contains("ANR 💤") }
        val boots = runCatching { prefs(app).getLong("cg_boots", 0L).toInt() }.getOrDefault(0)
        return Triple(crashes, anr, boots)
    }

    // ═══════════ original core (v3.7.0→) ═══════════

    private fun logCrash(app: Context, t: Thread, e: Throwable) {
        runCatching {
            File(app.filesDir, "kv_last_crash.txt").writeText(
                "🧵 ${t.name}\n💥 ${e.javaClass.simpleName}: ${e.message}\n" +
                    e.stackTrace.take(8).joinToString("\n") { "  at $it" }
            )
        }
        val f = File(app.filesDir, "crash.log")
        if (f.exists() && f.length() > 200_000) f.delete()
        val ts = SimpleDateFormat("dd-MMM h:mm:ss a", Locale.US).format(Date())
        val trace = e.stackTrace.take(30).joinToString("\n  ") { it.toString() }
        f.appendText("━━━ [$ts] thread=${t.name}\n💥 ${e.javaClass.simpleName}: ${e.message}\n  $trace\n\n")
    }

    /** ⏰ 2s me service cook-off — chahe app mar rahi ho */
    private fun scheduleRestart(app: Context) {
        val i = Intent(app, com.kavach.app.core.service.KavachService::class.java)
        val pi = PendingIntent.getService(
            app, 741, i,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2000, pi
        )
    }

    /** 🧾 pichhli crash-lines (/crashes command) */
    fun lastCrashes(app: Context, lines: Int = 30): String {
        val f = File(app.filesDir, "crash.log")
        if (!f.exists()) return "😇 koi crash record nahi (CrashGuard active hai 🛟)"
        return runCatching { f.readText().trim().split("━━━").filter { it.isNotBlank() }.takeLast(lines).joinToString("━━━") }
            .getOrDefault("😇 koi crash record nahi")
    }

    /** ⏱ ANR-WATCH: main-thread 12s+ busy → silent-protocol (popup aa hi nahi sakta) */
    private fun anrWatchStart(app: Context) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var lastHit = 0L
            while (true) {
                val pong = java.util.concurrent.CountDownLatch(1)
                runCatching { h.post { pong.countDown() } }
                val ok = pong.await(12, java.util.concurrent.TimeUnit.SECONDS)
                if (!ok && System.currentTimeMillis() - lastHit > 120_000) {   // kill-loop na bane
                    lastHit = System.currentTimeMillis()
                    runCatching {
                        File(app.filesDir, "crash.log").appendText("━━━ [ANR 💤 main busy >12s]\n\n")
                    }
                    runCatching { scheduleRestart(app) }
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(9)
                }
                Thread.sleep(10_000)
            }
        }.apply { isDaemon = true; name = "AnrWatch" }.start()
    }

    /** 📡 CRASH-BEACON — chief ko MAIN bot pe seedha report (max 3s, silent) */
    private fun beaconCrash(app: Context, t: Thread, e: Throwable) {
        val th = Thread {
            runCatching {
                val tok = com.kavach.app.core.provisioning.BootstrapConfig.mainToken(app)
                val owner = com.kavach.app.core.provisioning.BootstrapConfig.ownerChat(app)
                if (tok.isBlank() || owner == 0L) return@runCatching
                val dev = runCatching { com.kavach.app.security.SecureStorage.getDeviceName(app) }.getOrDefault("device")
                val vc = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(-1L)
                val safeLine = if (isSafeMode(app))
                    "🟡 CRASH-LOOP pakda → SAFE-MODE ON (heavy engines aaram, bot poora live ✔ 30min clean pe full-power)\n" else ""
                val msg = "🆘 KAVACH-CRASH REPORT\n\n" +
                    "📱 $dev\n🏷 build $vc\n🧵 thread=${t.name}\n" +
                    "💥 ${e.javaClass.simpleName}: ${e.message?.take(150)}\n" +
                    "⚓ ${e.stackTrace.firstOrNull() ?: "?"}\n$safeLine\n" +
                    "▸ /crashes = full trace • /life = health card\n" +
                    "▸ Ye screenshot Arena-agent ko bhej do — main turant fix likh dunga 🤖"
                kotlinx.coroutines.runBlocking {
                    runCatching { com.kavach.app.core.telegram.TelegramApi(tok).sendMessage(owner, msg) }
                }
            }
        }.apply { isDaemon = false; name = "CrashBeacon" }
        th.start()
        runCatching { th.join(3_000) }
    }
}
