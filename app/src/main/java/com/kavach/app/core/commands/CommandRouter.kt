package com.kavach.app.core.commands

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.kavach.app.core.channel.IncomingCommand
import com.kavach.app.core.features.FeatureRegistry
import com.kavach.app.core.telegram.TelegramApi
import com.kavach.app.security.PairingManager
import com.kavach.app.security.SecureStorage

/**
 * 🔀 CommandRouter — har command ko sahi jagah pahunchata hai.
 *
 * SECURITY RULES (sabse pehle):
 *  1. Pairing pending hai → sirf /pair sunta hai
 *  2. Paired hai → sirf OWNER chat ke commands. Baaki sab SILENT ignore 🤐
 */
class CommandRouter(
    private val app: Context,
    private val api: TelegramApi
) {

    /** 🛡 CRASH-SHIELD: koi bhi command bomb phode — app/service pe asar NAHI */
    suspend fun route(cmd: IncomingCommand) {
        try {
            routeInner(cmd)
        } catch (t: Throwable) {
            runCatching {
                api.sendMessage(cmd.chatId,
                    "🛡 Hiccup aaya: ${t.javaClass.simpleName} — command skip, Kavach bilkul safe hai ✔")
            }
        }
    }

    private suspend fun routeInner(cmd: IncomingCommand) {
        val owner = SecureStorage.getOwnerChat(app)

        // ── Pairing phase ──
        if (owner == 0L) {
            val parts = cmd.text.trim().split(Regex("\\s+"))
            if (parts[0].equals("/pair", true)) {
                handlePair(cmd.chatId, parts.getOrNull(1))
            }
            return     // baaki sab kuch ignore (abhi koi malik nahi)
        }

        // ── Stranger danger: malik nahi? toh sassy reject 😏 (24h/stranger throttle)
        if (cmd.chatId != owner) { com.kavach.app.core.shield.StrangerGuard.sass(app, api, cmd.chatId); return }

        // 🧠 spam-shield learning — owner boli = pichhle alert KIND ka weight +3
        runCatching { com.kavach.app.core.shield.AlertBus.ownerSpoke(app) }

        // 🖱️ Inline-keyboard menu tap → menu system ko do
        if (cmd.isCallback) {
            try {
                com.kavach.app.core.menu.DeviceMenu.onCallback(
                    app, api, cmd.chatId, cmd.cbMsgId, cmd.callbackId, cmd.text
                )
            } catch (_: Throwable) { }
            return
        }

        // 📎 UPLOAD (bot pe bheja doc/file → device pe save)
        if (cmd.docFileId.isNotBlank()) {
            try {
                val captionPath = Regex("#save\\s+([^\\s]+)").find(cmd.text)?.groupValues?.get(1)?.trim()
                val destDir = captionPath?.let { com.kavach.app.core.files.KavachFiles.resolve(it) } ?: java.io.File("/sdcard/Download")
                if (!com.kavach.app.core.files.KavachFiles.accessReady(app)) {
                    api.sendMessage(cmd.chatId, "⚠️ All-files access 🔴 — panel se 📁 row GRANT karo, phir dobara bhejo")
                } else {
                    api.sendChatAction(cmd.chatId, "typing")
                    api.sendMessage(cmd.chatId, "📥 Download ho raha… (${cmd.docName})")
                    val path = api.getFilePath(cmd.docFileId)
                    val bytes = path?.let { api.downloadFile(it) }
                    if (bytes == null) api.sendMessage(cmd.chatId, "❌ Download fail (file bada/purana ho sakta)")
                    else {
                        destDir.mkdirs()
                        val out = java.io.File(destDir, cmd.docName)
                        out.writeBytes(bytes)
                        api.sendMessage(cmd.chatId, "✅ SAVE ✔\n📂 `${out.absolutePath}`\n📦 ${com.kavach.app.core.files.KavachFiles.sizeStr(out.length())}", parseMode = "Markdown")
                    }
                }
            } catch (t: Throwable) { api.sendMessage(cmd.chatId, "❌ Upload-pipe fail: ${t.message}") }
            return
        }

        // 🤖 Bot IQ: ek baar commands+profile+☰MENU-BUTTON set kar do (silent)
        if (!SecureStorage.isCmdSet(app)) {
            runCatching {
                api.setMyCommands(); api.setMyDescription(); api.installMenuButton()
                SecureStorage.setCmdSet(app, true)
            }
        }

        // 🧹 Clean-Bot: command-text turant delete — chat kabhi nahi bharti ✦
        if (SecureStorage.isClean(app) && cmd.msgId > 0) {
            runCatching { api.deleteMessage(cmd.chatId, cmd.msgId) }
        }

        val parts = cmd.text.trim().split(Regex("\\s+"))
        val name = parts[0].lowercase()
        val args = parts.drop(1)

        when (name) {
            "/start" -> api.sendMessage(cmd.chatId, welcomeText())
            "/clean" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "off" -> { SecureStorage.setClean(app, false); api.sendMessage(cmd.chatId, "🧹 Clean-Bot OFF — messages rehenge chat me") }
                    "on" -> { SecureStorage.setClean(app, true); api.sendMessage(cmd.chatId, "🧹 Clean-Bot ON — tumhare command-texts turant delete ✦") }
                    else -> api.sendMessage(cmd.chatId, "🧹 Clean-Bot: ${if (SecureStorage.isClean(app)) "🟢 ON" else "🔴 OFF"}\n(/clean on | /clean off)")
                }
            }
            "/menu" -> com.kavach.app.core.menu.DeviceMenu.show(app, api, cmd.chatId)
            "/photo" -> runCatching {
                api.sendChatAction(cmd.chatId, "upload_photo")
                photoGuarded(cmd.chatId, true, "🤳 FRONT — koi toh hai?")
            }
            "/location" -> com.kavach.app.core.actions.KavachActions.locationFlow(app,
                sendImmediate = { m -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, m) } },
                sendFresh = { m -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, m) } })
            // ═══ 🔍 v3.8.0 DEEPFIND — gallery/doc deep-scan (hang-proof, throttled OCR) ═══
            "/deepfind" -> {
                val query = args.joinToString(" ").trim().ifBlank { "docs" }
                api.sendMessage(cmd.chatId, "🔍 DEEPFIND '$query' shuru…\n🐢 dheere-dheere scan (phone hang nahi hoga) — battery≥20% guard ON")
                val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.kavach.app.core.deepfind.deepfind(app, query) }
                        .getOrElse { "❌ scan fail: ${it.javaClass.simpleName} — ${it.message ?: ""}" }
                }
                report.toString().chunked(3800).forEach { api.sendMessage(cmd.chatId, it) }
            }
            "/deepget" -> {
                val q = args.dropLast(1).joinToString(" ").trim()
                val no = args.lastOrNull()?.toIntOrNull() ?: 0
                if (q.isBlank() || no <= 0) api.sendMessage(cmd.chatId, "🔖 use: /deepget <query> <no>  (jaise /deepget docs 2)")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    val got = com.kavach.app.core.deepfind.deepget(app, q, no)
                    if (got == null) api.sendMessage(cmd.chatId, "🤷 wo hit nahi mila — pehle /deepget ke liye /deepfind $q chalao")
                    else {
                        val bytes = got.first.readBytes()
                        api.sendMedia(cmd.chatId, "doc", bytes, "application/octet-stream", got.second, "📥 DEEPGET '$q' #$no — ${got.second}")
                        runCatching { got.first.delete() }
                    }
                }
            }
            "/dive" -> {
                // 🤿 Deepak Dive — semantic photo-khoj (chief order v3.8.0)
                val qDive = args.joinToString(" ").trim()
                api.sendMessage(cmd.chatId, "🤿 DEEPAK DIVE shuru…\n🧠 soch raha hoon: '$qDive'")
                val rep = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.kavach.app.core.deepfind.deepdive(app, qDive) }
                        .getOrElse { "❌ dive fail: ${it.javaClass.simpleName}" }
                }
                rep.chunked(3800).forEach { api.sendMessage(cmd.chatId, it) }
            }
            "/deepstats" -> {
                api.sendChatAction(cmd.chatId, "typing")
                val st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.kavach.app.core.deepfind.deepstats(app)
                }
                api.sendMessage(cmd.chatId, st)
            }
            // ═══ 🛡 v3.9.0 MEDIA-WATCH — WA/IG file-kavach ═══
            "/mediavault" -> {
                val sub = args.firstOrNull()?.lowercase() ?: ""
                val MV = com.kavach.app.core.media.MediaVault
                when {
                    sub == "on" -> { MV.setOn(app, true); api.sendMessage(cmd.chatId, "🛡 MEDIA-VAULT ON ✅ — WA/IG folders live-watch") }
                    sub == "off" -> { MV.setOn(app, false); api.sendMessage(cmd.chatId, "⏹ MEDIA-VAULT band") }
                    sub == "list" -> api.sendMessage(cmd.chatId, MV.list(app, args.getOrNull(1)?.toIntOrNull() ?: 10))
                    sub == "get" -> {
                        val no = args.getOrNull(1)?.toIntOrNull() ?: 0
                        if (no <= 0) api.sendMessage(cmd.chatId, "🔖 use: /mediavault get <no> (list se no dekho)")
                        else {
                            api.sendChatAction(cmd.chatId, "upload_document")
                            val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MV.getFile(app, no) }
                            if (got == null) api.sendMessage(cmd.chatId, "🤷 #$no vault me nahi mila")
                            else api.sendMedia(cmd.chatId, "doc", got.first.readBytes(), "application/octet-stream", got.second, "🛡 MV #$no — ${got.second}")
                        }
                    }
                    sub == "clean" -> api.sendMessage(cmd.chatId, MV.clean(app))
                    sub == "add" -> {
                        val p = args.drop(1).joinToString(" ").trim()
                        if (p.isBlank()) api.sendMessage(cmd.chatId, "🔖 use: /mediavault add /sdcard/Pictures/WhatsApp (brand auto)")
                        else api.sendMessage(cmd.chatId, MV.addDir(app, "OWN", p))
                    }
                    else -> api.sendMessage(cmd.chatId, MV.status(app))
                }
            }
            "/ignotif" ->
                api.sendMessage(cmd.chatId, com.kavach.app.core.notif.NotiXray.query(app, "com.instagram.android", 12))
            // ═══ 📱 v3.5.1 FIX: /apk ab DEVICE-bot pe bhi! (pehle sirf MAIN pe tha — isliye QR chhoo gaya tha) ═══
            "/apk" -> {
                val url = "https://github.com/deepakyadavg69/kavach-dl/releases/latest/download/device-info-latest.apk"
                api.sendChatAction(cmd.chatId, "upload_photo")
                var qrErr: String? = null
                val png: ByteArray? = try { com.kavach.app.core.util.QrGen.urlToPng(url) } catch (t: Throwable) { qrErr = t.javaClass.simpleName; null }
                val caption = "📱 KAVACH Latest APK 📦\n\nQR scan → seedha download ⬇️\n🔗 $url" +
                    (com.kavach.app.BuildConfig.KAVACH_DRIVE_LINK.trim().takeIf { it.isNotBlank() }?.let { "\n☁️ Drive backup: $it" } ?: "") +
                    "\n\n⚡ 'Unknown sources' allow → install → open (direct update rehta hai ✅)"
                if (png != null) api.sendPhoto(cmd.chatId, png, caption, mediaMime = "image/png", mediaName = "kavach_qr.png")
                else api.sendMessage(cmd.chatId, caption + "\n\n⚠️ is baar QR-gen fail: ${qrErr ?: "unknown"}")
            }
            "/record" -> runCatching {
                api.sendChatAction(cmd.chatId, "record_voice")
                recordGuarded(cmd.chatId, args.firstOrNull()?.toIntOrNull()?.coerceIn(5, 120) ?: 15)
            }
            "/evhq" -> runCatching {
                // 📎 HQ ORIGINAL (zero-compress, document) — evidence-grade
                val front = args.firstOrNull()?.lowercase() != "back"
                api.sendChatAction(cmd.chatId, "upload_document")
                val jpg = com.kavach.app.core.actions.KavachCamera.photo(app, front)
                if (jpg == null) api.sendMessage(cmd.chatId, "❌ HQ snap nahi mila — kaaran: ${com.kavach.app.core.actions.KavachCamera.lastWhy}")
                else api.sendMedia(cmd.chatId, "doc", jpg, "image/jpeg",
                    "evHQ_${if (front) "front" else "back"}_${System.currentTimeMillis()}.jpg",
                    "📎 HQ ORIGINAL — zero-compress evidence-grade 🧾\n${com.kavach.app.core.evidence.EvidenceCodec.kb(jpg.size)} • ${com.kavach.app.core.evidence.EvidenceCodec.meta(app)}")
            }
            "/alarm" -> { com.kavach.app.core.actions.KavachActions.sirenStart(app, args.any { it == "talk" }); api.sendMessage(cmd.chatId, "🚨 SIREN ON! /quiet = shanti") }
            "/say" -> { com.kavach.app.core.actions.KavachActions.speakNow(app, args.joinToString(" ").ifBlank { "Kavach dryan rakho" }); api.sendMessage(cmd.chatId, "🗣️ Bol diya...") }
            "/quiet" -> { com.kavach.app.core.actions.KavachActions.sirenStop(); api.sendMessage(cmd.chatId, "🤫 Shanti ho gayi") }
            "/lock" -> api.sendMessage(cmd.chatId, com.kavach.app.core.actions.KavachActions.lockNow(app))
            "/status" -> api.sendMessage(cmd.chatId, statusText())
            "/call" -> {
                api.sendMessage(cmd.chatId, com.kavach.app.core.phone.KavachPhone.makeCall(app, args.firstOrNull() ?: ""))
            }
            "/sms" -> {
                val num = args.firstOrNull() ?: ""
                val msg = args.drop(1).joinToString(" ")
                api.sendMessage(cmd.chatId, com.kavach.app.core.phone.KavachPhone.sendSms(app, num, msg))
            }
            "/logs" , "/calls" -> {
                api.sendMessage(cmd.chatId, com.kavach.app.core.phone.KavachPhone.callLogText(app, 10))
            }
            "/texts" -> {
                api.sendMessage(cmd.chatId, com.kavach.app.core.phone.KavachPhone.smsText(app, 10))
            }
            // ═════════ 📁 FILES commands (Category 5) ═════════
            "/ls" -> com.kavach.app.core.files.KavachFiles.listText(args.joinToString(" ").ifBlank { "/sdcard" })
                .chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/get" -> {
                val path = args.joinToString(" ")
                val f = com.kavach.app.core.files.KavachFiles.getFile(path)
                if (f == null) api.sendMessage(cmd.chatId, "❌ File nahi mila: $path\n💡 pehle /ls dekh ke full path lo")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    if (f.length() <= com.kavach.app.core.files.KavachFiles.MAX_SEND) {
                        val isImg = f.name.matches(Regex(".*\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE))
                        val sent = if (isImg && f.length() < 20L * 1024 * 1024)
                            api.sendPhoto(cmd.chatId, f.readBytes(), "📤 ${f.name} • ${com.kavach.app.core.files.KavachFiles.sizeStr(f.length())}")
                        else api.sendMedia(cmd.chatId, "doc", f.readBytes(), "application/octet-stream", f.name,
                            "📤 ${f.name} • ${com.kavach.app.core.files.KavachFiles.sizeStr(f.length())}")
                        if (!sent) api.sendMessage(cmd.chatId, "❌ Send fail")
                    } else {
                        api.sendMessage(cmd.chatId, "📦 BADA FILE (${com.kavach.app.core.files.KavachFiles.sizeStr(f.length())}) — 45MB parts me bhej raha…")
                        val parts = com.kavach.app.core.files.KavachFiles.splitParts(f, app)
                        parts.forEachIndexed { i, p ->
                            api.sendMedia(cmd.chatId, "doc", p.readBytes(), "application/octet-stream", p.name, "🎁 part ${i + 1}/${parts.size} (PC pe: copy /b part* full_file)")
                            p.delete()
                        }
                        api.sendMessage(cmd.chatId, "✅ Sab parts bheje — ek jagah jod lo (joinparts) 🧩")
                    }
                }
            }
            "/del" -> api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.delete(args.joinToString(" ")))
            "/ren" -> {
                if (args.size < 2) api.sendMessage(cmd.chatId, "✏️ /ren <path> <nayaNaam>") 
                else api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.rename(args[0], args[1]))
            }
            "/mv" -> { if (args.size < 2) api.sendMessage(cmd.chatId, "🚚 /mv <src> <dest>") else api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.moveCopy(args[0], args[1], move = true)) }
            "/cp" -> { if (args.size < 2) api.sendMessage(cmd.chatId, "📄 /cp <src> <dest>") else api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.moveCopy(args[0], args[1], move = false)) }
            "/search" -> {
                if (args.isEmpty()) api.sendMessage(cmd.chatId, "🔍 /search <root> <naam>")
                else {
                    val root = args[0]
                    val q = args.drop(1).joinToString(" ")
                    com.kavach.app.core.files.KavachFiles.searchText(root, q).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
                }
            }
            "/zip" -> {
                val path = args.joinToString(" ")
                api.sendChatAction(cmd.chatId, "typing")
                api.sendMessage(cmd.chatId, "🗜 Zip bana raha hoon…")
                val z = com.kavach.app.core.files.KavachFiles.zipPath(path, app)
                if (z == null) api.sendMessage(cmd.chatId, "❌ Zip fail (path check karo)")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    if (z.length() <= com.kavach.app.core.files.KavachFiles.MAX_SEND)
                        api.sendMedia(cmd.chatId, "doc", z.readBytes(), "application/zip", z.name, "🗜 ZIP • ${com.kavach.app.core.files.KavachFiles.sizeStr(z.length())} 📦")
                    else {
                        api.sendMessage(cmd.chatId, "📦 Bada ZIP (${com.kavach.app.core.files.KavachFiles.sizeStr(z.length())}) — parts!")
                        com.kavach.app.core.files.KavachFiles.splitParts(z, app).forEachIndexed { i, p ->
                            api.sendMedia(cmd.chatId, "doc", p.readBytes(), "application/octet-stream", p.name, "🎁 zip-part ${i + 1}")
                            p.delete()
                        }
                    }
                    z.delete()
                }
            }
            "/unzip" -> { if (args.size < 2) api.sendMessage(cmd.chatId, "🗜 /unzip <zip> <dest>") else api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.unzipTo(args[0], args[1])) }
            "/hide" -> api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.nomedia(args.joinToString(" "), app, true))
            "/unhide" -> api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.nomedia(args.joinToString(" "), app, false))
            "/dothide" -> api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.dotHide(args.joinToString(" "), true))
            "/dotunhide" -> api.sendMessage(cmd.chatId, com.kavach.app.core.files.KavachFiles.dotHide(args.joinToString(" "), false))
            "/peek" -> { com.kavach.app.core.files.KavachFiles.cachePeekText(args.joinToString(" ").ifBlank { "/sdcard/Android/media/com.whatsapp" }).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) } }
            "/reco" -> { com.kavach.app.core.files.KavachFiles.recoveryText().chunked(3900).forEach { api.sendMessage(cmd.chatId, it) } }
            // ═════════ 📍 LOCATION + 📋 CLIPBOARD (v2.1) ═════════
            "/where" -> api.sendMessage(cmd.chatId, com.kavach.app.core.location.KavachLocation.fullText(app))
            "/address", "/adr" -> api.sendMessage(cmd.chatId, "🏠 " + com.kavach.app.core.location.KavachLocation.address(app))
            "/speed" -> api.sendMessage(cmd.chatId, com.kavach.app.core.location.KavachLocation.speedText(app))
            "/beacon" -> {
                if (args.firstOrNull() == "off") { com.kavach.app.core.location.KavachLocation.beaconStop(); api.sendMessage(cmd.chatId, "📡 Beacon band ⏹") }
                else {
                    val ok = com.kavach.app.core.location.KavachLocation.beaconStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } }
                    api.sendMessage(cmd.chatId, if (ok) "📡 BEACON ON 🛰" else "⚠️ Location permission 🔴")
                }
            }
            "/fence" -> {
                val r = args.firstOrNull()?.toIntOrNull() ?: 100
                val ok = com.kavach.app.core.location.KavachLocation.fenceStart(app, r) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } }
                api.sendMessage(cmd.chatId, if (ok) "🚧 FENCE ${r}m ON 🚨" else "⚠️ GPS nahi mili")
            }
            "/unfence" -> { com.kavach.app.core.location.KavachLocation.fenceStop(); api.sendMessage(cmd.chatId, "🚧 Fence OFF 🔓") }
            "/clip" -> api.sendMessage(cmd.chatId, com.kavach.app.core.clipboard.KavachClip.readText(app))
            "/setclip" -> api.sendMessage(cmd.chatId, com.kavach.app.core.clipboard.KavachClip.setText(app, args.joinToString(" ")))
            "/clearclip" -> api.sendMessage(cmd.chatId, com.kavach.app.core.clipboard.KavachClip.clear(app))
            "/clipwatch" -> {
                if (args.firstOrNull() == "off") { com.kavach.app.core.clipboard.KavachClip.watchStop(); api.sendMessage(cmd.chatId, "👁 off") }
                else {
                    val ok = com.kavach.app.core.clipboard.KavachClip.watchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } }
                    api.sendMessage(cmd.chatId, if (ok) "👁 CLIP-WATCH ON" else "⚠️ already ON")
                }
            }
            "/open" -> {
                val url = args.firstOrNull() ?: ""
                if (url.isBlank()) api.sendMessage(cmd.chatId, "👉 /open <url>")
                else api.sendMessage(cmd.chatId, openUrl(app, url))
            }
            // ═════════ ☸💡💬 WHATSAPP commands (v2.2.0) ═════════
            "/wastore" -> com.kavach.app.core.wa.KavachWA.storageReport().chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/waclean" -> api.sendMessage(cmd.chatId, com.kavach.app.core.wa.KavachWA.junkClean())
            "/wamsg" -> {   // 🔍 v5.1.0 advance: buffer + DISK disk-log cross (puraane bhi aaenge)
                val q = args.joinToString(" ")
                val live = com.kavach.app.core.notify.KavachNotify.recentText(q)
                val old = if (q.isBlank()) "" else com.kavach.app.core.wa.WaGod.search(app, q)
                val mix = if (q.isNotBlank() && old.contains("hits")) live + "\n\n──────────\n📂 FILE-LOG me bhi:\n" + old else live
                mix.chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            // ═════════ 😇 WA GOD-MODE commands (v2.9.0) ═════════
            "/wastar" -> {
                val name = args.joinToString(" ")
                if (name.isBlank()) api.sendMessage(cmd.chatId, "👉 /wastar <naam> ⭐ (VIP — har WA msg auto-forward yahan, reboot-safe 💾)")
                else {
                    com.kavach.app.core.wa.WaGod.starAdd(app, name)
                    api.sendMessage(cmd.chatId, "⭐ STAR lag gaya: \"$name\" 🎉\nAb is VIP ka har captured WA message seedha yahan!\n\n🛡 /wastars = GOD-panel • 🗑 /waunstar $name = hatao")
                }
            }
            "/waunstar" -> {
                val name = args.joinToString(" ")
                if (name.isBlank()) api.sendMessage(cmd.chatId, "👉 /waunstar <naam>")
                else if (com.kavach.app.core.wa.WaGod.starRemove(app, name)) api.sendMessage(cmd.chatId, "🗑 \"$name\" star-list se hata diya ✌")
                else api.sendMessage(cmd.chatId, "🤷 \"$name\" star-list me hai hi nahi (/wastars dekho)")
            }
            "/wastars" -> api.sendMessage(cmd.chatId, com.kavach.app.core.wa.WaGod.statusText(app))
            "/wadelete" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaGod.setDelete(app, false); api.sendMessage(cmd.chatId, "🗑 ANTI-DELETE OFF 😶") }
                else { com.kavach.app.core.wa.WaGod.setDelete(app, true); api.sendMessage(cmd.chatId, "🗑 ANTI-DELETE ON 🛡 — koi bhi msg delete karega, bachaya hua ORIGINAL seedha yahan 😈") }
            }
            "/wacall" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaGod.setCall(app, false); api.sendMessage(cmd.chatId, "📞 CALL-SENTRY OFF 😶") }
                else { com.kavach.app.core.wa.WaGod.setCall(app, true); api.sendMessage(cmd.chatId, "📞 CALL-SENTRY ON 🛡 — WA voice/video (incoming + missed) ka turant alert 🔔") }
            }
            "/wasearch" -> {
                val q = args.joinToString(" ")
                if (q.isBlank()) api.sendMessage(cmd.chatId, "👉 /wasearch <naam ya word> 🔍 (disk-vault se — 1000+ msgs tak, reboot ke baad bhi)")
                else com.kavach.app.core.wa.WaGod.search(app, q).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            // ═════════ 🛡 GOD-X commands (v3.0.0) ═════════
            "/shield" -> api.sendMessage(cmd.chatId, com.kavach.app.core.shield.AlertBus.status(app))
            "/mute" -> {
                val m = args.firstOrNull()?.toIntOrNull() ?: 30
                if (m <= 0) { com.kavach.app.core.shield.AlertBus.unmute(app); api.sendMessage(cmd.chatId, "🔊 unmute — sab alerts live") }
                else { com.kavach.app.core.shield.AlertBus.mute(app, m); api.sendMessage(cmd.chatId, "🤫 MUTE $m min — auto-alerts snooze (commands ke jawab aate rahenge)\n🔊 /unmute se wapas") }
            }
            "/unmute" -> { com.kavach.app.core.shield.AlertBus.unmute(app); api.sendMessage(cmd.chatId, "🔊 UNMUTE — sab alerts live wapas") }
            "/digest" -> {
                val a = args.firstOrNull()?.lowercase() ?: ""
                val min = a.toIntOrNull() ?: -1
                when {
                    a == "off" || a == "0" -> { com.kavach.app.core.shield.AlertBus.setDigest(app, 0); api.sendMessage(cmd.chatId, "🗂 DIGEST OFF — sab alerts instant") }
                    min > 0 -> { com.kavach.app.core.shield.AlertBus.setDigest(app, min); api.sendMessage(cmd.chatId, "🗂 DIGEST $min min — chhote alerts batch hoke ayenge 📦 (CRITICAL turant)\nstatus: /shield") }
                    else -> api.sendMessage(cmd.chatId, "👉 /digest <min> ya /digest off — e.g. /digest 5")
                }
            }
            "/outbox" -> api.sendMessage(cmd.chatId, com.kavach.app.core.outbox.OutboxQ.peek(app))
            "/lastping" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.lowbatt.LowBatt.setEnabled(app, false); api.sendMessage(cmd.chatId, "🔋 LAST-PING OFF") }
                else { com.kavach.app.core.lowbatt.LowBatt.setEnabled(app, true); api.sendMessage(cmd.chatId, "🔋 LAST-PING ON — batt ≤8% pe photo+location aakhri baar 😵\n" + com.kavach.app.core.lowbatt.LowBatt.status(app)) }
            }
            "/stranger" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "off" -> { com.kavach.app.core.guard.StrangerGuard.setEnabled(app, false); api.sendMessage(cmd.chatId, "🎭 StrangerGuard OFF") }
                    "enroll" -> { api.sendMessage(cmd.chatId, "🎭 ENROLL shuru ho raha… camera ki taraf dekho 👀 (~8s)"); com.kavach.app.core.guard.StrangerGuard.enroll(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } } }
                    "test" -> { com.kavach.app.core.guard.StrangerGuard.test(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } } }
                    else -> { com.kavach.app.core.guard.StrangerGuard.setEnabled(app, true); api.sendMessage(cmd.chatId, "🎭 StrangerGuard ON 🛡 — unlock pe chehra-scan\n" + com.kavach.app.core.guard.StrangerGuard.status(app)) }
                }
            }
            "/geofence" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "set" -> api.sendMessage(cmd.chatId, com.kavach.app.core.geo.GeoFence.setHere(app))
                    "on" -> { com.kavach.app.core.geo.GeoFence.setEnabled(app, true); api.sendMessage(cmd.chatId, "📡 GEOFENCE ON ▶\n" + com.kavach.app.core.geo.GeoFence.status(app)) }
                    "off" -> { com.kavach.app.core.geo.GeoFence.setEnabled(app, false); api.sendMessage(cmd.chatId, "📡 GEOFENCE OFF") }
                    "radius" -> { (args.getOrNull(1)?.toFloatOrNull() ?: 300f).coerceIn(50f, 10_000f).let { com.kavach.app.core.geo.GeoFence.setRadius(app, it); api.sendMessage(cmd.chatId, "📏 radius = ${it.toInt()}m ✅\n" + com.kavach.app.core.geo.GeoFence.status(app)) } }
                    else -> api.sendMessage(cmd.chatId, com.kavach.app.core.geo.GeoFence.status(app) + "\n👉 /geofence set|on|off|radius 500")
                }
            }
            "/simsentry" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "off" -> { com.kavach.app.core.sim.SimSentry.setEnabled(app, false); api.sendMessage(cmd.chatId, "📵 SIM-SENTRY OFF") }
                    "reset" -> api.sendMessage(cmd.chatId, com.kavach.app.core.sim.SimSentry.reset(app))
                    else -> { com.kavach.app.core.sim.SimSentry.setEnabled(app, true); api.sendMessage(cmd.chatId, com.kavach.app.core.sim.SimSentry.status(app)) }
                }
            }
            "/warelay" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaRelay.setVoice(app, false); api.sendMessage(cmd.chatId, "🎙 VOICE-RELAY OFF") }
                else { com.kavach.app.core.wa.WaRelay.setVoice(app, true); api.sendMessage(cmd.chatId, "🎙 VOICE-RELAY ON — naya WA voice-note = yahan playable 🎧\n(all-files 🟢 chahiye)") }
            }
            "/wastatus" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaRelay.setStatus(app, false); api.sendMessage(cmd.chatId, "📸 STATUS-SNATCH OFF") }
                else { com.kavach.app.core.wa.WaRelay.setStatus(app, true); api.sendMessage(cmd.chatId, "📸 STATUS-SNATCH ON — naye WA statuses auto-yahan (max 3/5min)") }
            }
            "/watype" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaType.setEnabled(app, false); api.sendMessage(cmd.chatId, "⌨️ TYPING-SENTRY OFF") }
                else { com.kavach.app.core.wa.WaType.setEnabled(app, true); api.sendMessage(cmd.chatId, "⌨️ TYPING-SENTRY ON — VIP typing/online dikhe (WA screen par) = alert 👀\n" + com.kavach.app.core.wa.WaType.status(app)) }
            }
            "/godx" -> {
                val sb = StringBuilder("⚡ GOD-X PANEL (v3.0.0)\n══════════\n")
                sb.append(com.kavach.app.core.shield.AlertBus.status(app)).append("\n\n")
                sb.append(com.kavach.app.core.guard.StrangerGuard.status(app)).append("\n")
                sb.append(com.kavach.app.core.geo.GeoFence.status(app)).append("\n")
                sb.append(com.kavach.app.core.sim.SimSentry.status(app)).append("\n")
                sb.append(com.kavach.app.core.lowbatt.LowBatt.status(app)).append("\n")
                sb.append(com.kavach.app.core.wa.WaRelay.status(app)).append("\n")
                sb.append(com.kavach.app.core.wa.WaType.status(app)).append("\n")
                sb.append(com.kavach.app.core.otp.OtpGod.status(app)).append("\n")
                sb.append(com.kavach.app.core.call.CallRecon.status(app)).append("\n")
                sb.append(com.kavach.app.core.ig.IgGod.status(app).substringBefore("\n──────────\n")).append("\n")
                sb.append(com.kavach.app.core.shadow.TypeShadow.status(app)).append("\n")
                sb.append(com.kavach.app.core.ghost.NotifGhost.status(app)).append("\n")
                sb.append("👁 EYE-KEEPER restarts: ").append(com.kavach.app.core.eye.KavachEyeService.keeperRestarts)
                sb.append("📮 outbox: ").append(com.kavach.app.core.outbox.OutboxQ.size(app)).append(" pending (/outbox)")
                sb.toString().chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            // ═════════ 📸 IG GOD-MODE (v3.1.0 — WhatsApp ka IG bhai) ═════════
            "/igmsg" -> {
                com.kavach.app.core.ig.IgGod.recentText(app, args.joinToString(" ")).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            "/igstar" -> {
                val name = args.joinToString(" ")
                if (name.isBlank()) api.sendMessage(cmd.chatId, "👉 /igstar <naam> ⭐ (IG VIP — har msg auto-forward, reboot-safe)")
                else { com.kavach.app.core.ig.IgGod.starAdd(app, name); api.sendMessage(cmd.chatId, "⭐ IG-STAR: \"$name\" 🎉 — msgs auto yahan + unsend-guess bhi CRIT\n/igstars = panel • /igunstar $name = hatao") }
            }
            "/igunstar" -> {
                val name = args.joinToString(" ")
                if (name.isBlank()) api.sendMessage(cmd.chatId, "👉 /igunstar <naam>")
                else if (com.kavach.app.core.ig.IgGod.starRemove(app, name)) api.sendMessage(cmd.chatId, "🗑 \"$name\" IG-list se hata diya")
                else api.sendMessage(cmd.chatId, "🤷 \"$name\" IG-list me nahi (/igstars dekho)")
            }
            "/igstars" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ig.IgGod.status(app))
            "/igdelete" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.ig.IgGod.setDelete(app, false); api.sendMessage(cmd.chatId, "🗑 IG UNSEND-SENTRY OFF") }
                else { com.kavach.app.core.ig.IgGod.setDelete(app, true); api.sendMessage(cmd.chatId, "🗑 IG UNSEND-SENTRY ON — VIP ka notif-gayab = UNSEND guess CRIT 😱") }
            }
            "/igcall" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.ig.IgGod.setCall(app, false); api.sendMessage(cmd.chatId, "📞 IG CALL-SENTRY OFF") }
                else { com.kavach.app.core.ig.IgGod.setCall(app, true); api.sendMessage(cmd.chatId, "📞 IG CALL-SENTRY ON — video-chat/voice-call alert 🔔") }
            }
            "/igsearch" -> {
                val q = args.joinToString(" ")
                if (q.isBlank()) api.sendMessage(cmd.chatId, "👉 /igsearch <naam ya word> 🔍")
                else com.kavach.app.core.ig.IgGod.search(app, q).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            "/iglog" -> {   // 📄 v5.2.0: IG MSG-LOG CSV file (latest-upr)
                val f = com.kavach.app.core.ig.IgGod.msgCsvFile(app)
                if (f == null) api.sendMessage(cmd.chatId, "📄 IG MSG-LOG khali — DM-notif aayegi to bharne lagega ✍")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    api.sendDocument(cmd.chatId, f.readBytes(), "text/csv", f.name, "📄 IG MSG-LOG (CSV) — newest-upr 🆕")
                    f.delete()
                }
            }
            "/igdeleted" -> {   // 🗑 v5.2.0: IG UNSEND-LOG CSV file (advance)
                val f = com.kavach.app.core.ig.IgGod.unsendCsvFile(app)
                if (f == null) api.sendMessage(cmd.chatId, "📁 IG UNSEND-LOG khali — (achhi baat 🪵) koi suspect-unsend nahi pakra")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    api.sendDocument(cmd.chatId, f.readBytes(), "text/csv", f.name, "🗑 IG UNSEND-LOG (CSV) — VIP-mode guards 😈")
                    f.delete()
                }
            }
            "/survive" -> api.sendMessage(cmd.chatId, com.kavach.app.core.shield.SurvivalKit.report(app))
            "/life" -> api.sendMessage(cmd.chatId, com.kavach.app.core.shield.LifeLine.report(app))          // 🫀 v5.3.0 long-life card
            "/update" -> api.sendMessage(cmd.chatId, com.kavach.app.core.update.UpdateGod.run(app, api, cmd.chatId, args.firstOrNull() == "force"))  // 🔄 v5.4.0 ghost-tap
            "/crashes" -> api.sendMessage(cmd.chatId,                                                          // 💥 v5.3.0 crash-trace
                runCatching { com.kavach.app.core.guard.CrashGuard.lastCrashes(app, 3) }.getOrDefault("😇 koi crash record nahi").take(3800))
            "/igstore" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ig.KavachIG.storageReport())
            "/igpic" -> {
                val n = (args.firstOrNull()?.toIntOrNull() ?: 3).coerceIn(1, 8)
                val pics = com.kavach.app.core.ig.KavachIG.latestPics(n)
                if (pics.isEmpty()) api.sendMessage(cmd.chatId, "📸 IG pics nahi mili (all-files 🟢? IG se post/download kiya?)")
                else { api.sendChatAction(cmd.chatId, "upload_photo"); pics.forEach { f -> api.sendPhoto(cmd.chatId, f.readBytes(), "📸 IG pic • ${f.name.takeLast(22)}") } }
            }
            "/igopen" -> {
                val u = args.joinToString(" ").trim().removePrefix("@")
                if (u.isBlank()) api.sendMessage(cmd.chatId, "👉 /igopen <username>")
                else api.sendMessage(cmd.chatId, openUrl(app, "https://instagram.com/_u/$u"))
            }
            "/igtype" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.wa.WaType.setIg(app, false); api.sendMessage(cmd.chatId, "⌨️ IG TYPING-SENTRY OFF") }
                else { com.kavach.app.core.wa.WaType.setIg(app, true); api.sendMessage(cmd.chatId, "⌨️ IG TYPING-SENTRY ON — VIP active-now/typing signal 👀\n" + com.kavach.app.core.wa.WaType.status(app)) }
            }
            // ═════════ 🗄 KSTORE + ⌨️ SHADOW + 🌐 REPORT (v3.1.0) ═════════
            "/kstore" -> api.sendMessage(cmd.chatId, com.kavach.app.core.vault.KStore.sizeReport(app))
            "/shadow" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "off" -> { com.kavach.app.core.shadow.TypeShadow.setEnabled(app, false); api.sendMessage(cmd.chatId, "⌨️ TYPE-SHADOW OFF") }
                    "on" -> { com.kavach.app.core.shadow.TypeShadow.setEnabled(app, true); api.sendMessage(cmd.chatId, "⌨️ TYPE-SHADOW ON 🕶 — focused fields ka text CSV me (passwords SKIP 🔒)\n/shadow = peek • /shadow whatsapp = filter • /shadow clear") }
                    "clear" -> api.sendMessage(cmd.chatId, com.kavach.app.core.shadow.TypeShadow.clear(app))
                    else -> {
                        val f = args.joinToString(" ")
                        api.sendMessage(cmd.chatId, if (f.isBlank()) com.kavach.app.core.shadow.TypeShadow.status(app) + "\n\n" + com.kavach.app.core.shadow.TypeShadow.peek(app, "") else com.kavach.app.core.shadow.TypeShadow.peek(app, f))
                    }
                }
            }
            "/report" -> {
                api.sendChatAction(cmd.chatId, "upload_document")
                val f = com.kavach.app.core.vault.KStore.buildHtmlReport(app)
                api.sendMedia(cmd.chatId, "doc", f.readBytes(), "text/html", f.name, "🌐 KAVACH HTML REPORT — browser me kholo (offline, sab captured data 📊)")
            }
            // ═════════ 💌 OTP-GOD + 📞 CALL-RECON (v3.2.0) ═════════
            "/otp" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.otp.OtpGod.setEnabled(app, false); api.sendMessage(cmd.chatId, "💌 OTP-GOD OFF") }
                else { com.kavach.app.core.otp.OtpGod.setEnabled(app, true); api.sendMessage(cmd.chatId, "💌 OTP-GOD ON — SMS+notif dono se code pakdega 🔑\n" + com.kavach.app.core.otp.OtpGod.status(app)) }
            }
            "/otplog" -> api.sendMessage(cmd.chatId, com.kavach.app.core.otp.OtpGod.logPeek(app))
            "/callwatch" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.call.CallRecon.setEnabled(app, false); api.sendMessage(cmd.chatId, "📞 CALL-RECON OFF") }
                else { com.kavach.app.core.call.CallRecon.setEnabled(app, true); api.sendMessage(cmd.chatId, "📞 CALL-RECON ON — naya call (missed=🚨) turant khabar\n" + com.kavach.app.core.call.CallRecon.status(app)) }
            }
            "/calllog" -> api.sendMessage(cmd.chatId, com.kavach.app.core.call.CallRecon.peek(app))
            // ═════════ 👻 ICON-GHOST (owner 3-point plan) ═════════
            "/icon" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "hide" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ghost.IconGhost.setVisible(app, false))
                    "show" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ghost.IconGhost.setVisible(app, true))
                    "auto" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ghost.IconGhost.autoCmd(app, args.getOrNull(1)))
                    else -> api.sendMessage(cmd.chatId, com.kavach.app.core.ghost.IconGhost.status(app))
                }
            }
            // ═════════ 👻 PHANTOM modes (v3.3.0) ═════════
            "/ghostnotif" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "on" -> { com.kavach.app.core.ghost.NotifGhost.setGhost(app, true); com.kavach.app.core.service.KavachService.instance?.repostForeground(); api.sendMessage(cmd.chatId, "👻 GHOST-NOTIF ON — shade me 1dp-blank + transparent icon 🕳\n(task-manager entry rahegi — system rule)") }
                    "off" -> { com.kavach.app.core.ghost.NotifGhost.setGhost(app, false); com.kavach.app.core.service.KavachService.instance?.repostForeground(); api.sendMessage(cmd.chatId, "👻 GHOST-NOTIF OFF — normal-minimal mode") }
                    else -> api.sendMessage(cmd.chatId, com.kavach.app.core.ghost.NotifGhost.status(app))
                }
            }
            "/notifguard" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.ghost.NotifGhost.setRespect(app, true); api.sendMessage(cmd.chatId, "🫡 NOTIF-GUARD: RESPECT mode — user hataye to repost NAHI (service eye-keeper+watchdog se phir bhi zinda 💪)") }
                else { com.kavach.app.core.ghost.NotifGhost.setRespect(app, false); api.sendMessage(cmd.chatId, "🔁 NOTIF-GUARD ON — shade se gayab hui to 5 min me auto-repost") }
            }
            "/quiet" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.shield.AlertBus.setQuiet(app, false); api.sendMessage(cmd.chatId, "🌙 QUIET-HOURS OFF — raat me bhi instant") }
                else { com.kavach.app.core.shield.AlertBus.setQuiet(app, true); api.sendMessage(cmd.chatId, "🌙 QUIET-HOURS ON — 23:00–07:00 chhote alerts bundle, subah ek 📦 (CRIT turant rehte)") }
            }
            // ═════════ 🛡 FORTRESS (v3.4.0) ═════════
            "/janitor" -> {
                api.sendChatAction(cmd.chatId, "typing")
                val (freed, d) = com.kavach.app.core.vault.SpaceJanitor.sweep(app)
                api.sendMessage(cmd.chatId, "🧹 JANITOR RUN: ${"%.1f".format(freed / 1048576.0)} MB saaf ($d)\n" + com.kavach.app.core.vault.SpaceJanitor.status(app))
            }
            "/vault" -> {
                api.sendMessage(cmd.chatId,
                    "🛡 VAULT-STATUS (v3.5.0 SENSES)\n\n" +
                        "🔐 Secrets: EncryptedSharedPreferences (AES-256 GCM master-key + SIV) ✅\n" +
                        "🎭 Code: R8 obfuscate ON + shrinkResources ON + keep-rules ✅\n" +
                        "🚷 " + com.kavach.app.core.shield.StrangerGuard.status(app) + "\n" +
                        com.kavach.app.core.vault.SpaceJanitor.status(app))
            }
            // ═════════ 🧿 SENSES (v3.5.0 — P2/P3/P7) ═════════
            "/notif" -> api.sendMessage(cmd.chatId, com.kavach.app.core.notif.NotiXray.query(app, args.joinToString(" ").ifBlank { null }))
            "/notifxray" -> {
                when (args.firstOrNull()?.lowercase()) {
                    "off" -> { com.kavach.app.core.notif.NotiXray.setOn(app, false); api.sendMessage(cmd.chatId, "🧬 NOTI-XRAY OFF 🗄 band") }
                    "snitchoff" -> { com.kavach.app.core.notif.NotiXray.setSnitch(app, false); api.sendMessage(cmd.chatId, "🗑 VANISH-snitch band (archive chalta)") }
                    else -> { com.kavach.app.core.notif.NotiXray.setOn(app, true); com.kavach.app.core.notif.NotiXray.setSnitch(app, true); api.sendMessage(cmd.chatId, com.kavach.app.core.notif.NotiXray.status(app)) }
                }
            }
            "/money" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.money.MoneyMeter.setOn(app, false); api.sendMessage(cmd.chatId, "💰 MONEY-METER OFF") }
                else api.sendMessage(cmd.chatId, com.kavach.app.core.money.MoneyMeter.report(app, if (args.firstOrNull()?.startsWith("7") == true) 7 else 1))
            }
            "/nightear" -> {
                if (args.firstOrNull()?.lowercase() == "off") { com.kavach.app.core.night.NightEar.setOn(app, false); api.sendMessage(cmd.chatId, "👂 NIGHT-EAR OFF — mic raat me BILKUL nahi 🚫🎙") }
                else { com.kavach.app.core.night.NightEar.setOn(app, true); api.sendMessage(cmd.chatId, com.kavach.app.core.night.NightEar.status(app)) }
            }
            "/wawatch" -> {
                val name = args.joinToString(" ")
                if (name.isBlank()) api.sendMessage(cmd.chatId, "👉 /wawatch <naam>")
                else {
                    com.kavach.app.core.notify.KavachNotify.targetStart(name) { m ->
                        kotlinx.coroutines.runBlocking {
                            api.sendMessage(cmd.chatId, "🎯 TARGET STRIKE!\n👤 ${m.title}\n💬 ${m.text}\n🕐 ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(m.at))}")
                        }
                    }
                    api.sendMessage(cmd.chatId, "👁 WATCH ON — \"$name\" 📡 (kal toh bhi tobatak!)")
                }
            }
            "/waoff" -> { com.kavach.app.core.notify.KavachNotify.targetStop(); api.sendMessage(cmd.chatId, "👁 target watch OFF ⏹") }
            // ═════════ ⚙️🌐 SYSTEM/NET commands (v2.3.0) ═════════
            "/sysinfo" -> com.kavach.app.core.system.KavachSystem.deviceInfo(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/batt" , "/battd" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.battDetail(app))
            "/bright" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.setBrightness(app, args.firstOrNull()?.toIntOrNull() ?: 70))
            "/wake" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.wake(app, args.firstOrNull()?.toIntOrNull() ?: 2))
            "/wakeoff" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.wakeOff())
            "/datause" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.dataUsage())
            "/bigfiles" -> {
                val mb = args.firstOrNull()?.toIntOrNull() ?: 100
                com.kavach.app.core.system.KavachSystem.bigFiles("/sdcard", mb).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            "/cacheclean" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.cacheClean(app))
            "/netinfo" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.netFull(app))
            "/publicip" -> api.sendMessage(cmd.chatId, "🌍 Public IP: " + com.kavach.app.core.system.KavachSystem.publicIp())
            "/wifiscan" -> api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.wifiScan(app))
            "/speedtest" -> {
                api.sendMessage(cmd.chatId, "⚡ Speed-test (~10-30s)…")
                api.sendMessage(cmd.chatId, com.kavach.app.core.system.KavachSystem.speedTest(
                    args.firstOrNull() ?: "https://github.com/deepakyadavg69/kavach-dl/releases/download/v2.1.0/device-info-v2.1.0.apk"))
            }
            // ═════════ 🔄 SELF-UPDATE + 🎞️ MOTION SNAP (v2.8.0) ═════════
            "/motion" -> {
                val M = com.kavach.app.core.motion.KavachMotion
                when (args.firstOrNull()?.lowercase()) {
                    "on" -> {
                        M.setEnabled(app, true)
                        val ok = M.start(app)
                        api.sendMessage(cmd.chatId, if (ok) "🎞️ MOTION SNAP ON ⚡\n>2.6g jhatka → auto front-photo + location (45s cooldown)\n⏹ band: /motion off" else "❌ Accelerometer nahi mila is device pe")
                    }
                    "off" -> { M.setEnabled(app, false); M.stop(); api.sendMessage(cmd.chatId, "🎞️ MOTION SNAP OFF ⏹") }
                    else -> api.sendMessage(cmd.chatId, M.monitorText(app))
                }
            }
            // ═════════ 📦🌍 APPS/BROWSER (v2.4.0) ═════════
            "/apps" -> com.kavach.app.core.apps.KavachApps.listText(app, args.joinToString(" ")).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/appinfo" -> api.sendMessage(cmd.chatId, com.kavach.app.core.apps.KavachApps.infoText(app, args.firstOrNull() ?: ""))
            "/openapp" -> api.sendMessage(cmd.chatId, com.kavach.app.core.apps.KavachApps.open(app, args.firstOrNull() ?: ""))
            "/uninstall" -> api.sendMessage(cmd.chatId, com.kavach.app.core.apps.KavachApps.uninstall(app, args.firstOrNull() ?: ""))
            "/appwatch" -> {
                if (args.firstOrNull() == "off") { com.kavach.app.core.apps.KavachApps.watchStop(); api.sendMessage(cmd.chatId, "👁 off") }
                else {
                    val ok = com.kavach.app.core.apps.KavachApps.watchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } }
                    api.sendMessage(cmd.chatId, if (ok) "👁 APP-WATCH ON 🛰" else "⚠️ already ON")
                }
            }
            "/google" -> api.sendMessage(cmd.chatId, com.kavach.app.core.browser.KavachBrowser.google(app, args.joinToString(" ")))
            "/dl" -> api.sendMessage(cmd.chatId, com.kavach.app.core.browser.KavachBrowser.download(app, args.firstOrNull() ?: ""), parseMode = "Markdown")
            "/save" -> api.sendMessage(cmd.chatId, com.kavach.app.core.browser.KavachBrowser.saveLink(app, args.firstOrNull() ?: ""))
            "/saved" -> com.kavach.app.core.browser.KavachBrowser.savedText(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/dellink" -> api.sendMessage(cmd.chatId, com.kavach.app.core.browser.KavachBrowser.deleteLink(app, args.firstOrNull()?.toIntOrNull() ?: 0))
            // ═════════ 🔐🧠 GUARD/AI (v2.5.0) ═════════
            "/guard" -> com.kavach.app.core.guard.KavachGuard.monitorText(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/simwatch" -> {
                if (args.firstOrNull() == "off") { com.kavach.app.core.guard.KavachGuard.simWatchStop(); api.sendMessage(cmd.chatId, "📵 off") }
                else {
                    val ok = com.kavach.app.core.guard.KavachGuard.simWatchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s) } }
                    api.sendMessage(cmd.chatId, if (ok) "📵 SIM-WATCH ON 🚨" else "⚠️ already ON")
                }
            }
            "/decoy" -> {
                api.sendMessage(cmd.chatId, if (com.kavach.app.core.guard.KavachGuard.decoyOn(app, null)) "🪤 Decoy ON (3-tap top-left = mukt 🐞)" else "⚠️ overlay 🔴")
            }
            "/decoyoff" -> { com.kavach.app.core.guard.KavachGuard.decoyOff(app); api.sendMessage(cmd.chatId, "⚫ off") }
            "/wrongunlocks" -> api.sendMessage(cmd.chatId, com.kavach.app.core.guard.KavachGuard.wrongUnlockReport(app))
            "/theft" -> {
                api.sendMessage(cmd.chatId, "🆘 THEFT-MODE 💥 — sab kuch nikal raha...")
                com.kavach.app.core.actions.KavachActions.sirenStart(app, speak = true)
                com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_HI)
                com.kavach.app.core.actions.KavachActions.lockNow(app)
                com.kavach.app.core.actions.KavachCamera.photo(app, true)?.let { api.sendPhoto(cmd.chatId, it, "😱 THEFT front") }
                api.sendMessage(cmd.chatId, com.kavach.app.core.location.KavachLocation.fullText(app))
            }
            "/ailook" -> {
                val (jpg, report, faces) = com.kavach.app.core.ai.KavachAI.aiLook(app)
                jpg?.let { api.sendPhoto(cmd.chatId, it, "🧠 faces=$faces") }
                api.sendMessage(cmd.chatId, report)
            }
            "/aidigest" -> api.sendMessage(cmd.chatId, com.kavach.app.core.ai.KavachAI.digestText(app))
            "/scenewatch" -> {
                if (args.firstOrNull() == "off") { com.kavach.app.core.ai.KavachAI.sceneWatchStop(); api.sendMessage(cmd.chatId, "🎞 off") }
                else {
                    val ok = com.kavach.app.core.ai.KavachAI.sceneWatchStart(app) { s, shot ->
                        kotlinx.coroutines.runBlocking { api.sendMessage(cmd.chatId, s); shot?.let { api.sendPhoto(cmd.chatId, it, "🎞 change!") } }
                    }
                    api.sendMessage(cmd.chatId, if (ok) "🎞 Scene-watch ON 🧠" else "⚠️ 🔑 screen-capture session chahiye")
                }
            }
            // ═════════ 👁❓ REMOTE EYE + HELP (v2.6.0) ═════════
            "/eyezoom" -> {
                val z = args.firstOrNull()?.toIntOrNull()
                val frame = com.kavach.app.core.eye.KavachEye.gridFrame(app)
                if (z == null || z !in 1..9) api.sendMessage(cmd.chatId, "🔎 Format: /eyezoom <1-9> — zone crop karo exact-tap ke saath")
                else if (frame == null) api.sendMessage(cmd.chatId, "📸 Frame nahi bana (♿ Screen-power OFF?)")
                else {
                    val (zoom, taps) = com.kavach.app.core.screen.KavachScreen.cropZone(frame, z)
                    api.sendChatAction(cmd.chatId, "upload_photo")
                    api.sendPhoto(cmd.chatId, zoom, "🔎 ZONE $z ZOOM — sub-cell tap:\n${taps.joinToString("\n") { "   /eyetap ${it.drop(4)}" }}\n💡 ya fir pehle buttons se hi zone dabao")
                }
            }
            "/eye" -> api.sendMessage(cmd.chatId, com.kavach.app.core.eye.KavachEye.monitorText(app))
            "/eyetap" -> {
                val x = args.getOrNull(0)?.toFloatOrNull(); val y = args.getOrNull(1)?.toFloatOrNull()
                api.sendMessage(cmd.chatId,
                    if (x != null && y != null && com.kavach.app.core.eye.KavachEye.tapXY(app, x, y)) "👆 tap ✔ ($x,$y)"
                    else "❌ format: /eyetap <x> <y> — ghost-hand ON ho?")
            }
            "/eyehold" -> {
                val x = args.getOrNull(0)?.toFloatOrNull(); val y = args.getOrNull(1)?.toFloatOrNull()
                api.sendMessage(cmd.chatId,
                    if (x != null && y != null && com.kavach.app.core.eye.KavachEye.longPressXY(app, x, y)) "👆 long-press ✔"
                    else "❌ /eyehold x y")
            }
            "/eyetext", "/type" -> {
                api.sendMessage(cmd.chatId,
                    if (com.kavach.app.core.eye.KavachEye.type(args.joinToString(" "))) "⌨ LIKH DIYA ✔ 👻"
                    else "❌ focused EditText nahi mili (screen dekho once)")
            }
            "/swipe" -> {
                api.sendMessage(cmd.chatId,
                    if (com.kavach.app.core.eye.KavachEye.swipeDir(app, args.firstOrNull() ?: "up")) "🖐 swipe ✔" else "❌ ghost-hand 🔴")
            }
            "/key" -> {
                api.sendMessage(cmd.chatId,
                    if (com.kavach.app.core.eye.KavachEye.key(args.firstOrNull() ?: "")) "🔑 ✔" else "❌ key: back/home/recents/notif/quick/power")
            }
            "/wasend" -> {
                if (args.size < 2) api.sendMessage(cmd.chatId, "👉 /wasend <phone> <msg>")
                else {
                    api.sendChatAction(cmd.chatId, "typing")
                    api.sendMessage(cmd.chatId, "👻 Ghost-hand ja rahi WhatsApp me… (~4s)")
                    kotlinx.coroutines.delay(500)
                    api.sendMessage(cmd.chatId, com.kavach.app.core.eye.KavachEye.waSend(app, args[0], args.drop(1).joinToString(" ")))
                }
            }
            "/wastory" -> {
                val p = args.firstOrNull()
                if (p == null) api.sendMessage(cmd.chatId, "👉 /wastory <phone> — 📜 full-chat EXPORT (auto-scroll → TXT 📎)")
                else {
                    api.sendMessage(cmd.chatId, "📜 EXPORT shuru… chat khol raha + auto-scroll kar rahi 👻\n(30-90s — purane→naye sab lines collect hongi)")
                    val (err, f) = com.kavach.app.core.eye.KavachEye.waStory(app, p)
                    if (f != null) {
                        api.sendChatAction(cmd.chatId, "upload_document")
                        api.sendMedia(cmd.chatId, "doc", f.readBytes(), "text/plain", f.name, "📜 WA-STORY EXPORT 💪 +$p")
                        f.delete()
                        // 🧹 v5.1.0 chief-order: export ke baad WA recents se saaf karo (kisi ko shak na ho 🕵️)
                        com.kavach.app.core.eye.KavachEye.clearRecents(app)
                        api.sendMessage(cmd.chatId, "🧹 RECENTS saaf — WA ab recent-apps me nahi dikhega ✔")
                    } else api.sendMessage(cmd.chatId, err)
                }
            }
            "/wacalls" -> {
                api.sendMessage(cmd.chatId, "📞 WA Calls-tab padh rahi ghost-hand… (~5s)")
                com.kavach.app.core.eye.KavachEye.waCallsDump(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            }
            // ═════════ 🐝 v5.1.0 WA-SMART (chief ordered) ═════════
            "/wapeeps" -> {   // 📣 status KISNE lagaya (list) — 2-step SPAM-FREE status-fetch
                api.sendMessage(cmd.chatId, "📣 WA Updates-tab khol rahi ghost… naam padh rahi (~8s)")
                api.sendMessage(cmd.chatId, com.kavach.app.core.eye.KavachEye.waStatusPeople(app))
            }
            "/wapeek" -> {    // 🎯 chune-hue insaan ke hi statuses (baaki spam NAHI)
                val w = args.joinToString(" ")
                if (w.isBlank()) api.sendMessage(cmd.chatId, "👉 /wapeek <no/naam> — list se no do\n(pehle /wapeeps se list lo 📣)")
                else {
                    api.sendMessage(cmd.chatId, "🎯 Status-peek shuru: \"$w\" ke status khol ke shots le rahi (~25s, max-6) 🖐")
                    val (who, shots) = com.kavach.app.core.eye.KavachEye.waStatusPeek(app, w)
                    if (shots.isEmpty()) api.sendMessage(cmd.chatId, who)
                    else {
                        api.sendMessage(cmd.chatId, "📸 ${who.trim()} ke status — ${shots.size} frames:")
                        shots.forEachIndexed { i, b -> api.sendPhoto(cmd.chatId, b, "📣 $who — status ${i + 1}/${shots.size}") }
                    }
                }
            }
            "/walog" -> {     // 📄 chief #7: poora MSG-LOG CSV-file (newest-first)
                val f = com.kavach.app.core.wa.WaGod.msgLogCsvFile(app)
                if (f == null) api.sendMessage(cmd.chatId, "📄 MSG-LOG abhi khali hai — notif-buffer (🔔 row ON) se msgs phcapture hoti hain")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    api.sendDocument(cmd.chatId, f.readBytes(), "text/csv", f.name, "📄 WA MSG-LOG (CSV) — 🆕 newest-upr • ${com.kavach.app.core.wa.WaGod.vaultAll(app).size} rows")
                    f.delete()
                }
            }
            "/wadeleted" -> { // 📁 chief #20 advance: ANTI-DELETE ka nischal LOG file
                val f = com.kavach.app.core.wa.WaGod.deletedCsvFile(app)
                if (f == null) api.sendMessage(cmd.chatId, "📁 DELETED-LOG khali — (touchwood 🪵) kisi ne kuchh delete nahi kiya abhi tak")
                else {
                    api.sendChatAction(cmd.chatId, "upload_document")
                    api.sendDocument(cmd.chatId, f.readBytes(), "text/csv", f.name, "🗑 ANTI-DELETE LOG (CSV) — newest-upr 🚨")
                    f.delete()
                }
            }
            // ═════════ 🏆 v5.0.0 FINALS ═════════
            "/eyetaptext" -> {
                val t = args.joinToString(" ")
                if (t.isBlank()) api.sendMessage(cmd.chatId, "👉 /eyetaptext <screen-pe-dikhta-naam>\n(jaise: /eyetaptext Pay)")
                else { api.sendChatAction(cmd.chatId, "typing"); api.sendMessage(cmd.chatId, com.kavach.app.core.eye.KavachEye.tapByText(app, t)) }
            }
            "/eyeai" -> com.kavach.app.core.eye.KavachEye.screenExplain(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/eyeholdzone" -> {
                val z = args.firstOrNull()?.toIntOrNull()
                if (z == null) api.sendMessage(cmd.chatId, "👉 /eyeholdzone <1-9> — zone pe LONG-PRESS")
                else api.sendMessage(cmd.chatId, if (com.kavach.app.core.eye.KavachEye.longPressZone(app, z)) "🤏 LONG-PRESS zone $z ✔" else "⚠️ ghost-hand OFF")
            }
            "/eyelong" -> {
                api.sendMessage(cmd.chatId, "📜 LONG-SHOT ban rahi… (4 screens chal ke stitch ho rahi 🖐)")
                val b = com.kavach.app.core.eye.KavachEye.eyeLong(app)
                if (b == null) api.sendMessage(cmd.chatId, "📸 fail — ghost-hand/screen check")
                else { api.sendChatAction(cmd.chatId, "upload_photo"); api.sendPhoto(cmd.chatId, b, "📜 LONG-SHOT (stitched 🧵)") }
            }
            // ═════════ ⬅➡ v5.1.0 home-page swap lanes + 🎨 bot-style ═════════
            "/eyeleft" -> {
                val ok = com.kavach.app.core.eye.KavachEye.swipeDir(app, if (true) "left" else "left")
                api.sendMessage(cmd.chatId, if (ok) "⬅ LEFT-swipe ✔ (home-screen page peeche 📲 — 👁 frame: /eyegrab se dekho)" else "⚠️ ghost-hand 🔴")
            }
            "/eyeright" -> {
                val ok = com.kavach.app.core.eye.KavachEye.swipeDir(app, "right")
                api.sendMessage(cmd.chatId, if (ok) "➡ RIGHT-swipe ✔ (home-screen page aage 📲 — frame: /eyegrab se dekho)" else "⚠️ ghost-hand 🔴")
            }
            "/botstyle" -> {  // 🎨 naam/DP/desc sync — Setup-card + boot auto bhi
                api.sendMessage(cmd.chatId, "🎨 Bot ka naam/DP/desc device-name ke hisaab se saja rahi… (5-10s)")
                api.sendMessage(cmd.chatId, com.kavach.app.core.style.KavachStyle.syncNow(app, api, force = true))
            }
            "/eyeread" -> {
                val frame = com.kavach.app.core.eye.KavachEye.gridFrame(app)
                if (frame == null) api.sendMessage(cmd.chatId, "📸 frame nahi bana (screen/ghost check)")
                else {
                    api.sendChatAction(cmd.chatId, "typing")
                    val out = runCatching {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(frame, 0, frame.size)!!
                        val img = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)
                        com.google.android.gms.tasks.Tasks.await(
                            com.google.mlkit.vision.text.TextRecognition.getClient(
                                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).process(img),
                            20, java.util.concurrent.TimeUnit.SECONDS).text
                    }.getOrDefault("")
                    api.sendMessage(cmd.chatId, if (out.isBlank()) "🌑 screen pe text nahi mila" else ("📝 EYE-READ (screen-text):\n$out").take(3800))
                }
            }
            "/camburst" -> {
                api.sendChatAction(cmd.chatId, "upload_photo")
                val b = com.kavach.app.core.finals.KavachFinals.camBurst(app, args.firstOrNull()?.toIntOrNull() ?: 5)
                if (b == null) api.sendMessage(cmd.chatId, "⚠️ burst fail (camera busy?)")
                else api.sendPhoto(cmd.chatId, b, "🎆 CAM-BURST best-of-${args.firstOrNull()?.toIntOrNull() ?: 5} (sabse sharp auto-picked 🧠)")
            }
            "/viddump" -> {
                val vids = com.kavach.app.core.finals.KavachFinals.todaysVideos(app)
                if (vids.isEmpty()) api.sendMessage(cmd.chatId, "🍃 aaj ke videos nahi mile")
                else vids.forEach { api.sendMedia(cmd.chatId, "video", it.readBytes(), "video/mp4", it.name, "🎬 TODAY-VID 🗓") }
            }
            "/callstats" -> com.kavach.app.core.finals.KavachFinals.callStats(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/health" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.health(app))
            "/timeline" -> com.kavach.app.core.finals.KavachFinals.timeline(app).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/findfx" -> com.kavach.app.core.finals.KavachFinals.findFx(app, args.joinToString(" ")).chunked(3900).forEach { api.sendMessage(cmd.chatId, it) }
            "/dups" -> {
                api.sendChatAction(cmd.chatId, "typing")
                val r = if (args.firstOrNull()?.equals("clean", true) == true)
                    com.kavach.app.core.finals.KavachFinals.dupClean(app) else com.kavach.app.core.finals.KavachFinals.dupScan(app)
                api.sendMessage(cmd.chatId, r)
            }
            "/wamsgat" -> {
                if (args.size < 3) api.sendMessage(cmd.chatId, "👉 /wamsgat <NUM> <HH:MM> <MSG>\n(jaise: /wamsgat 9090909090 07:00 Happy Birthday bhai 🎂)")
                else api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.addWaJob(app, args[0], args.drop(2).joinToString(" "), args[1]))
            }
            "/wajobs" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.waJobs(app))
            "/wajobdel" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.waJobDel(app, args.firstOrNull()?.toLongOrNull() ?: 0))
            "/wastalk" -> {
                when {
                    args.isEmpty() -> api.sendMessage(cmd.chatId, "🧬 WORD-STALK list: " + com.kavach.app.core.finals.KavachFinals.stalkList(app).joinToString(", ").ifBlank { "(khali)" } + "\n👉 /wastalk <lafza> add | /wastalk del <lafza>")
                    args[0].equals("del", true) -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.stalkRemove(app, args.drop(1).joinToString(" ")))
                    else -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.stalkAdd(app, args.joinToString(" ")))
                }
            }
            "/wadpwatch" -> when (args.firstOrNull()?.lowercase()) {
                "off" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.dpOff(app))
                else -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.dpAdd(app, args.getOrNull(1) ?: args.first()))
            }
            "/wareadall" -> {
                val (err, f) = com.kavach.app.core.wa.WaGod.fullDump(app, args.firstOrNull() ?: "")
                if (f != null) { api.sendChatAction(cmd.chatId, "upload_document"); api.sendMedia(cmd.chatId, "doc", f.readBytes(), "text/plain", f.name, "🫥 INVISIBLE-READ 📜 (no blue-tick!)"); f.delete() }
                else api.sendMessage(cmd.chatId, err)
            }
            "/macro" -> when (args.firstOrNull()?.lowercase()) {
                "rec" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.macroRec(app))
                "stop" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.macroStop(app))
                "play" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.macroPlay(app))
                else -> api.sendMessage(cmd.chatId, "👉 /macro rec | /macro stop | /macro play 🎬")
            }
            "/rule" -> api.sendMessage(cmd.chatId,
                if (args.size > 1 && args[0] == "add") com.kavach.app.core.finals.KavachFinals.ruleAdd(app, args.drop(1).joinToString(" "))
                else "👉 /rule add batt15=/location|/camfront\n(ya /rules se dekho)")
            "/rules" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.ruleList(app))
            "/ruleclear" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.rulesClear(app))
            "/netkill" -> {
                val p = args.firstOrNull()
                if (p == null) api.sendMessage(cmd.chatId, "👉 /netkill <pkg>\n(jaise: /netkill com.instagram.android)\n⚠️ sach-baat: VPN-free real-blocking possible nahi — app-settings kholta hoon, wahan se Data-restrict kar do 🧯")
                else {
                    runCatching {
                        app.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$p")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        api.sendMessage(cmd.chatId, "🧯 $p ka settings screen khol di ✅\n→ 'Mobile data & WiFi' → Background-data OFF kar do (yahi real-netkill hai legal-side)\n💡 target-phone aankhon ke saamne hota tabhi kar sakte system rules")
                    }.onFailure { api.sendMessage(cmd.chatId, "⚠️ $p settings open fail: ${it.message}") }
                }
            }
            "/nettrip" -> api.sendMessage(cmd.chatId, com.kavach.app.core.finals.KavachFinals.netTripReport(app))
            "/igstory" -> {
                api.sendMessage(cmd.chatId, "📸 IG-STORY best-effort ghost-run ja rahi… (IG khulega → story-tap → hops ×5) 🐾")
                val li = app.packageManager.getLaunchIntentForPackage("com.instagram.android")
                if (li == null) api.sendMessage(cmd.chatId, "❌ Instagram nahi mila")
                else {
                    li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { app.startActivity(li) }
                    kotlinx.coroutines.delay(2600)
                    val packs = ArrayList<ByteArray>()
                    val svc = com.kavach.app.core.eye.KavachEyeService.instance
                    if (svc == null) { api.sendMessage(cmd.chatId, "⚠️ ghost-hand OFF — abhi sirf current screen:"); com.kavach.app.core.eye.KavachEye.gridFrame(app)?.let { api.sendPhoto(cmd.chatId, it, "📸 IG current") }; }
                    else {
                        val m = app.resources.displayMetrics
                        svc.tap(140f * m.density, (m.density * 235f))   // stories row first-circle approx
                        kotlinx.coroutines.delay(1500)
                        repeat(5) {
                            com.kavach.app.core.eye.KavachEye.gridFrame(app)?.let { packs.add(it) }
                            svc.tap(m.widthPixels - 40f, m.heightPixels / 2f)  // next-story hop
                            kotlinx.coroutines.delay(1100)
                        }
                        svc.back()
                        if (packs.isEmpty()) api.sendMessage(cmd.chatId, "🍃 story frames nahi ban sake")
                        else api.sendMediaGroup(cmd.chatId, packs.map { it to "📸 IG-story hop 👻" })
                    }
                }
            }
            "/waread" -> {
                val (err, lines) = com.kavach.app.core.eye.KavachEye.waReadLines(app, args.firstOrNull() ?: "")
                if (err.isNotEmpty()) api.sendMessage(cmd.chatId, err)
                else {
                    if (lines.isEmpty()) api.sendMessage(cmd.chatId, "📖 Kuchh dikhaya nahi (screen par chat khuli ho? 👁 ON?)")
                    else lines.takeLast(30).chunked(12).forEach { api.sendMessage(cmd.chatId, "📖 CHAT-LINES:\n" + it.joinToString("\n") { "  • $it" }) }
                }
            }
            "/waoff" -> { com.kavach.app.core.notify.KavachNotify.targetStop(); api.sendMessage(cmd.chatId, "⏹ WA-watch band ✌") }
            "/help" -> {
                val cat = args.joinToString(" ").ifBlank { "" }
                api.sendChatAction(cmd.chatId, "typing")
                val f = if (cat.isBlank()) com.kavach.app.core.help.HelpCenter.masterFile(app)
                else com.kavach.app.core.help.HelpCenter.helpFile(app, cat)
                api.sendChatAction(cmd.chatId, "upload_document")
                api.sendMedia(cmd.chatId, "doc", f.readBytes(), "text/plain", f.name, "❓ HELP FILE 📄 (examples ke saath ✏️)")
                f.delete()
            }
            "/helpall" -> {
                val f = com.kavach.app.core.help.HelpCenter.masterFile(app)
                api.sendMedia(cmd.chatId, "doc", f.readBytes(), "text/plain", f.name, "❓ MASTER help 📚")
                f.delete()
            }
            "/screenshot", "/shot" -> {
                api.sendChatAction(cmd.chatId, "upload_photo")
                val S = com.kavach.app.core.screen.KavachScreen
                if (!S.sessionAlive()) api.sendMessage(cmd.chatId, "♿ Screen-power OFF — 11-TAP panel se 🔑 ON karo (a11y — reboot-proof 🪦)")
                else S.shot(app)?.let { api.sendPhoto(cmd.chatId, it, "📱 Screenshot • ${S.timeStamp()}") }
                    ?: api.sendMessage(cmd.chatId,  // 🩺 v3.9.2: fake-fail NO — asli wajah bolo
                        "❌ Shot fail — ${S.lastWhyFull()}\n" +
                        " CARE: Settings me 'Display Support' service ON honi chahiye (v3.9.8 ab pure-a11y screenshot karti hai). " +
                        "Banking apps ka FLAG_SECURE (kaala frame) Android rule hai.")
            }
            else -> {
                // 🧩 Plugin features (Phase 2 modules yahan se chalenge)
                val feature = FeatureRegistry.all(app)[name]
                if (feature != null) {
                    try {
                        val reply = feature.execute(app, args)
                        api.sendMessage(cmd.chatId, reply)
                    } catch (t: Throwable) {
                        api.sendMessage(cmd.chatId, "⚠️ Error: ${t.message}")
                    }
                } else {
                    // 🤖 NATURAL LANGUAGE — plain words samajh ke kaam karo!
                    smartFallback(cmd.chatId, cmd.text.lowercase())
                }
            }
        }
    }

    // ═══════ 🚦 BUSY-GUARDED capture helpers (v2.6.1 — camera/mic busy = NO crash, auto-retry) ═══════

    /** 📸 photo — camera free to turant; busy (kisi app me khula) → bot note + live screen + FREE hote hi auto-shot */
    private suspend fun photoGuarded(chatId: Long, front: Boolean, capBase: String) {
        val go: () -> Unit = {
            val jpg = com.kavach.app.core.actions.KavachCamera.photo(app, front)
            kotlinx.coroutines.runBlocking {
                if (jpg != null) sendSlimPhoto(chatId, jpg, capBase)
                else api.sendMessage(chatId, "❌ Photo nahi aayi — " +    // 🩺 v3.9.2 truth-teller
                    com.kavach.app.core.actions.KavachCamera.lastWhy + "\n🔁 10s baad dobara try karo ya /photo do")
            }
        }
        val free = com.kavach.app.core.evidence.BusyGuard.submit(app, com.kavach.app.core.evidence.BusyGuard.Kind.CAM, front,
            onBusy = { kotlinx.coroutines.runBlocking {
                api.sendMessage(chatId, "⏳ CAMERA BUSY 📸\n(abhi kisi app me camera khula hai)\n✅ jaise hi FREE hoga — AUTO-photo bhej dunga 🔁 (max wait 3min)")
                busyScreenShot(chatId)
            } },
            onFree = { kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "✅ Camera FREE mila — shot aa raha 📸…") } },
            onGiveUp = { kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "⌛ 3min ho gaye — camera ab bhi busy 😔 thodi der baad /photo bhejo") } },
            run = go)
        if (free) go()
    }

    /** 🎤 record — mic free to turant; busy (call/recorder chal raha) → bot note + FREE hote hi auto-record */
    private suspend fun recordGuarded(chatId: Long, sec: Int) {
        val go: () -> Unit = {
            val okPerm = androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!okPerm) {
                kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "❌ Mic permission 🔴 — panel se 🟢 karo") }
            } else {
                com.kavach.app.core.media.KavachMedia.recOnce(app, sec, false) { f ->
                    kotlinx.coroutines.runBlocking {
                        api.sendMedia(chatId, "voice", f.readBytes(), "audio/m4a", "kavach.m4a",
                            "🎙️ ${sec}s • ${com.kavach.app.core.evidence.EvidenceCodec.meta(app)}")
                        f.delete()
                    }
                }
            }
        }
        val free = com.kavach.app.core.evidence.BusyGuard.submit(app, com.kavach.app.core.evidence.BusyGuard.Kind.MIC,
            onBusy = { kotlinx.coroutines.runBlocking {
                api.sendMessage(chatId, "⏳ MIC BUSY 🎤\n(📞 call ya koi recorder awaaz le raha hai)\n✅ FREE hote hi AUTO-record bhej dunga 🔁 (max wait 3min)")
                busyScreenShot(chatId)
            } },
            onFree = { kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "✅ Mic FREE — ${sec}s record ho raha 🎤…") } },
            onGiveUp = { kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "⌛ 3min — mic ab bhi busy 😔 baad me dobara bhejo") } },
            run = go)
        if (free) { api.sendMessage(chatId, "🎤 ${sec}s audio record…"); go() }
    }

    /** 🗜 WebP-slim photo send (fallback: original) + evidence caption */
    private suspend fun sendSlimPhoto(chatId: Long, jpg: ByteArray, base: String) {
        val E = com.kavach.app.core.evidence.EvidenceCodec
        val s = E.slim(jpg)
        if (s == null) api.sendPhoto(chatId, jpg, "$base • ${E.meta(app)}")
        else api.sendPhoto(chatId, s.data, "$base\n${s.shrinkNote} • ${E.meta(app)}",
            mediaMime = s.mime, mediaName = s.name)
    }

    /** 🖥 busy-screen ka live dikhao (🔑 screen-session zinda ho to) */
    private suspend fun busyScreenShot(chatId: Long) {
        val S = com.kavach.app.core.screen.KavachScreen
        if (S.sessionAlive()) {
            S.shot(app)?.let { api.sendPhoto(chatId, it, "🖥 LIVE SCREEN — isi waqt phone par ye dikh raha ⬆ • ${S.timeStamp()}") }
        } else {
            api.sendMessage(chatId, "💡 busy-screen dekhna ho to pehle 🔑 screen-session ON karo (👁 panel)")
        }
    }

    // ─── 🤖 Natural-language brain (plain words → actions) ───
    private suspend fun smartFallback(chatId: Long, low: String) {
        fun photoCaption(front: Boolean) = "${if (front) "🤳" else "📷"} (samajh gaya: photo) 📸"
        try {
            when {
                low.contains("menu") || low.contains("button") -> {
                    api.sendMessage(chatId, "🎮 (menu khol laha hoon…)")
                    com.kavach.app.core.menu.DeviceMenu.show(app, api, chatId)
                }
                low.contains("foto") || low.contains("photo") || low.contains("selfi")
                        || low.contains("pic") -> {
                    api.sendChatAction(chatId, "upload_photo")
                    photoGuarded(chatId, true, photoCaption(true))
                }
                low.contains("screenshot") || low.contains("screen shot") || low.contains("screendump") -> {
                    api.sendChatAction(chatId, "upload_photo")
                    val S = com.kavach.app.core.screen.KavachScreen
                    if (!S.sessionAlive()) api.sendMessage(chatId,
                        "♿ Screen-power OFF.\n👉 Device Info kholo → 11-TAP → 🔑 Screen-power row → ON KARO (a11y — ek baar, hamesha ke liye 🪦)")
                    else {
                        val jpg = S.shot(app)
                        if (jpg != null) api.sendPhoto(chatId, jpg, "📱 Screenshot (samajh gaya ✅) • ${S.timeStamp()}")
                        else api.sendMessage(chatId, "❌ Shot fail")
                    }
                }
                low.contains("video") -> {
                    api.sendChatAction(chatId, "record_video")
                    val ok = com.kavach.app.core.actions.KavachCamera.videoStart(app, true)
                    api.sendMessage(chatId, if (ok) "🔴 REC start (menu📸 me ⏹ stop bhejta hoon)" else "❌ start fail")
                }
                low.contains("location") || low.contains("kahan") || low.contains("map") -> {
                    api.sendChatAction(chatId, "find_location")
                    api.sendMessage(chatId, com.kavach.app.core.actions.KavachActions.locationText(app))
                }
                low.contains("alarm") || low.contains("bajao") || low.contains("siren") -> {
                    com.kavach.app.core.actions.KavachActions.sirenStart(app, false)
                    api.sendMessage(chatId, "🚨 BAJ RAHA! (shanti = /quiet)")
                }
                low.contains("quiet") || low.contains("stop") || low.contains("band") -> {
                    com.kavach.app.core.actions.KavachActions.sirenStop()
                    api.sendMessage(chatId, "🤫 Band kar diya")
                }
                low.contains("lock") -> api.sendMessage(chatId, com.kavach.app.core.actions.KavachActions.lockNow(app))
                low.contains("bol") || low.startsWith("/say") -> {
                    com.kavach.app.core.actions.KavachActions.speakNow(app, low.removePrefix("/say").trim())
                    api.sendMessage(chatId, "🗣️ Bol diya!")
                }
                (low.contains("record") && low.contains("audio")) || low.startsWith("audio record") || low.contains("awaaz record") -> {
                    api.sendChatAction(chatId, "record_voice")
                    val sec = Regex("(\\d+)").find(low)?.value?.toIntOrNull()?.coerceIn(5, 120) ?: 15
                    recordGuarded(chatId, sec)
                }
                low.contains("volume") -> {
                    val M = com.kavach.app.core.media.KavachMedia
                    when {
                        low.contains("badha") || low.contains("up") || low.contains("zyada") -> api.sendMessage(chatId, M.volStep(app, true))
                        low.contains("kam") || low.contains("down") -> api.sendMessage(chatId, M.volStep(app, false))
                        else -> api.sendMessage(chatId, M.volText(app))
                    }
                }
                low.contains("gaana") || low.contains("song") -> {
                    val M = com.kavach.app.core.media.KavachMedia
                    when {
                        low.contains("next") || low.contains("agla") -> api.sendMessage(chatId, M.mediaControl(app, "next"))
                        low.contains("pause") || low.contains("ruk") -> api.sendMessage(chatId, M.mediaControl(app, "toggle"))
                        low.contains("play") || low.contains("chala") -> api.sendMessage(chatId, M.mediaControl(app, "toggle"))
                        else -> api.sendMessage(chatId, M.mediaControl(app, "status"))
                    }
                }
                low.contains("dhamki") || low.contains("threat") -> {
                    com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_HI)
                    api.sendMessage(chatId, "🗣 Dhamki bol di 😡")
                }
                low.contains("call") && (low.contains("dikha") || low.contains("log") || low.contains("list")) -> {
                    api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.callLogText(app, 10))
                }
                (low.contains("sms") && low.contains("dikha")) || low.startsWith("read sms") || low.contains("message dikha") -> {
                    api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.smsText(app, 10))
                }
                low.contains("call lagao") || low.contains("call karo") -> {
                    val num = Regex("\\d{7,15}").find(low)?.value ?: ""
                    api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.makeCall(app, num))
                }
                low.contains("sms bhej") -> {
                    val num = Regex("\\d{7,15}").find(low)?.value ?: ""
                    val msg = low.substringAfter(num).trim()
                    api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.sendSms(app, num, msg))
                }
                low.contains("battery") || low.contains("batt") -> api.sendMessage(chatId, "🔋 " + batteryText())
                low.contains("status") || low.contains("haal") -> api.sendMessage(chatId, statusText())
                else -> {
                    // 💡 kya madad: closest suggestion
                    val known = listOf("menu", "photo", "location", "record", "alarm", "quiet", "lock", "status", "help")
                    val sug = known.firstOrNull { k -> low.contains(k) }
                    api.sendMessage(chatId,
                        "🤔 ye command familiar nahi hui.\n" +
                        (if (sug != null) "Kya aapko '/${sug}' lagani thi? 🙂\n" else "Plain words me bolo (jaise 'photo kheencho') ya\n") +
                        "🎮 Sabse tez: /menu dabao!")
                }
            }
        } catch (_: Throwable) { }
    }

    // ─── 🌍 /open URL helper (best-effort: overlay-permission ho to background se chal jata) ───
    private fun openUrl(app: android.content.Context, url: String): String {
        var u = url.trim()
        if (!(u.startsWith("http://") || u.startsWith("https://"))) u = "https://$u"
        return try {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(i)
            "🌐 OPEN ☑ $u\n(💡 phone ke browser me khul gaya hoga)"
        } catch (t: Throwable) {
            "❌ Open fail: ${t.message}\n💡 Background-limit ho to overlay-permission 🟢 karo (panel row)"
        }
    }

    // ─── Pairing ───
    private suspend fun handlePair(chatId: Long, code: String?) {
        if (PairingManager.tryPair(app, code, chatId)) {
            val name = SecureStorage.getDeviceName(app)
            api.sendMessage(
                chatId,
                "✅ KAVACH CONNECTED!\n\n📱 Device: $name\n🛡️ Status: ARMED\n\nAb se main sirf aapke orders maanunga.\n/help — saare commands"
            )
        } else {
            api.sendMessage(chatId, "❌ Galat pair code.")
        }
    }

    // ─── Replies ───
    private fun welcomeText(): String {
        // 🙏 v5.1.0 chief-order: welcome shuddh HINDI, device-naam ke saath — "manage karne ke liye ready" style
        val name = com.kavach.app.core.style.KavachStyle.deviceLabel(app)
        return "🙏 Namaste Chief ji!\n\n🤖 Main hoon **$name** — aur bilkul READY hoon aapke phone ko manage karne ke liye! ✅\n\n" +
                "🎮 /menu — buttons se sab control (sabse aasaan ✨)\n" +
                "📸 /photo · 📍 /location · 🎙 /record — turant kaam\n" +
                "❓ /help — saare commands\n\n" +
                "💬 Bas command bhejo — main abhi kar dunga 💪"
    }

    private fun helpText(): String = """
        🛡️ KAVACH COMMANDS (Device Bot)

        🎮 /menu — BUTTON center sabse tez! 👑
        📸 /photo — front-cam silent photo
        📍 /location — live GPS + maps link
        🎙️ /record [15|30] — aas-paas ki awaaz
        🚨 /alarm [talk] — full-volume siren (+spoken dhamki)
        🗣️ /say <text> — device aapka message bolegi!
        ✋ /quiet — siren band
        🔒 /lock — turant screen lock (Admin chahiye)
        📊 /status, /start, /help
    """.trimIndent()

    private fun statusText(): String {
        val up = SystemClock.elapsedRealtime() / 1000
        val hrs = up / 3600
        val mins = (up % 3600) / 60
        return buildString {
            appendLine("🛡️ KAVACH STATUS")
            appendLine("📱 Device: ${SecureStorage.getDeviceName(app)}")
            appendLine("🔒 Armed: ${if (SecureStorage.isArmed(app)) "HAAN ✅" else "NAHI ❌"}")
            appendLine("🔋 Battery: ${batteryText()}")
            appendLine("🌐 Network: ${networkText()}")
            appendLine("⏱️ Uptime: ${hrs}h ${mins}m")
            appendLine("👑 HQ seva: auto (jo online, wahi chalati hai)")
        }
    }

    private fun batteryText(): String {
        val i: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return "$pct%" + if (charging) " ⚡(charging)" else ""
    }

    private fun networkText(): String {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return "❓ unknown"
        val net = cm.activeNetwork ?: return "❌ Offline"
        val caps = cm.getNetworkCapabilities(net) ?: return "❓ unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "📶 WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "📱 Mobile data"
            else -> "✅ Online"
        }
    }
}
