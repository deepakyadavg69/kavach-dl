package com.kavach.app.core.eye

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 👁 KavachEyeService — GHOST HAND (Accessibility)
 * Panel se 1-baari grant karna hai. Accessible window content read + gestures.
 */
class KavachEyeService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        startKeeper()   // 👁 EYE-KEEPER — notification ke baad bhi service ko zinda rakhe (v3.3.0)
    }

    /** 👁 EYE-KEEPER: a11y-service system ke saath respawn hoti — guard-service
     *  mar gayi ho (notif-hatt ke baad bhi) to 45s me phir se jaga do.
     *  (overlay-perm exemption isko bg-start allow karti) */
    private var keeper: Thread? = null
    private fun startKeeper() {
        if (keeper != null) return
        keeper = Thread {
            while (instance != null) {
                runCatching {
                    val ctx = applicationContext
                    if (com.kavach.app.security.SecureStorage.getToken(ctx).isNotBlank() &&
                        com.kavach.app.core.service.KavachService.instance == null) {
                        keeperRestarts++
                        com.kavach.app.core.service.KavachService.start(ctx)
                    }
                }
                Thread.sleep(45_000)
            }
            keeper = null
        }.apply { isDaemon = true; name = "EyeKeeper"; start() }
    }
    override fun onAccessibilityEvent(e: android.view.accessibility.AccessibilityEvent?) {
        lastAliveAt = System.currentTimeMillis()
        if (e != null && e.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // 🎬 v5 MACRO-REC: chief ke asli taps record (rect center)
            if (com.kavach.app.core.finals.KavachFinals.recOn) {
                runCatching {
                    val r = android.graphics.Rect()
                    e.source?.getBoundsInScreen(r)
                    com.kavach.app.core.finals.KavachFinals.recTap(this, r.centerX().toFloat(), r.centerY().toFloat())
                }
            }
        }
        if (e != null && e.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.toString()?.let { pkg ->
                lastFgPkg = pkg
                runCatching { com.kavach.app.core.update.UpdateGod.onWindow(this, pkg) }   // 🔄 v5.4.0 ghost-tap installer (idle=0 cost)
                val watch = watchListener
                if (watch != null && watchTargets.contains(pkg)) runCatching { watch.invoke(pkg) }   // 👁 app-watch strike
            }
        }
        runCatching { if (e != null) com.kavach.app.core.shadow.TypeShadow.handle(applicationContext, e) }  // ⌨️ type-shadow tap
    }
    override fun onInterrupt() { }
    override fun onDestroy() { instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: KavachEyeService? = null; private set
        @Volatile var keeperRestarts = 0   // 👁 eye-keeper ne kitni baar service jagayi
        fun ready() = instance != null

        // ── v3.7.0 HEAL: naya screen-power (MediaProjection RIP 🪦) ──
        @Volatile var lastFgPkg: String = ""; private set      // 👁 koi app khuli — yeh yaad rakhta hai
        @Volatile var lastAliveAt: Long = 0L; private set      // 💓 service heartbeat
        @Volatile private var watchTargets: Set<String> = emptySet()
        @Volatile private var watchListener: ((String) -> Unit)? = null

        /** Settings-level check — a11y ENABLED hai kya (restart/reboot-proof yeh hi asli sach hai) */
        fun isEnabled(app: android.content.Context): Boolean {
            if (ready()) return true
            return runCatching {
                val s = android.provider.Settings.Secure.getString(
                    app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                s.contains(app.packageName, ignoreCase = true)
            }.getOrDefault(false)
        }

        // ─────────── 📸 GRAB — silent screenshot, session-DEAD ka concept hi KHATAM ───────────
        /**
         * Ek silent frame lo → JPEG bytes. 2 attempt (pehla black/blank ho to 350ms baad dobara).
         * @return null = a11y off / system deny. FLAG_SECURE apps pe Android kaala frame deta hai (hard rule).
         */
        /** 🩺 v3.9.2: pichli grab-fail ki asli wajah (chief: "fake action mat bolo") */
        @Volatile var lastWhy: String = "ok"; private set
        val lastGrabFail = java.util.concurrent.atomic.AtomicInteger(-1)

        fun grab(maxW: Int = 1080, quality: Int = 80): ByteArray? {
            val svc = instance
            if (svc == null) { lastWhy = "service-dead (a11y band?)"; return null }
            if (android.os.Build.VERSION.SDK_INT < 30) { lastWhy = "api<30 (frame-api nahi)"; return null }
            var why = "blank-frame"
            lastGrabFail.set(-1)
            for (attempt in 0..2) {                                   // 🆕 3 attempts (OEM callbacks dheele)
                val res = runCatching { svc.grabOnce(maxW, quality) }
                res.exceptionOrNull()?.let { why = "throw:" + it.javaClass.simpleName }
                val out = res.getOrNull()
                if (out != null && out.size > 2_000) { lastWhy = "ok"; return out }
                if (out != null) why = "blank/secure(${out.size}B)"
                if (attempt < 2) Thread.sleep(if (attempt == 0) 350 else 700)
            }
            val fc = lastGrabFail.get()
            why = when {
                fc == 1 -> "system-deniy(internal)"
                fc == 2 -> "secure-content(FLAG_SECURE ✅ Android rule)"
                fc == 3 -> "screenshot-API-off (OEM ne grab rok diya)"
                fc >= 0 -> "callback-fail(code=$fc)"
                else -> why
            }
            lastWhy = why
            return null
        }

        // 👁 app-watch — usage-access ki zaroorat hi nahi (a11y events kafi)
        fun watchStart(pkgs: Set<String>, onHit: (String) -> Unit) { watchTargets = pkgs; watchListener = onHit }
        fun watchStop() { watchTargets = emptySet(); watchListener = null }
        fun watching() = watchListener != null
        fun watchingPkgs(): Set<String> = watchTargets
    }

    // 📸 ek hi frame ka raw grab (takeScreenshot async → latch sync)
    private fun grabOnce(maxW: Int, quality: Int): ByteArray? {
        val latch = java.util.concurrent.CountDownLatch(1)
        var bmp: android.graphics.Bitmap? = null
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, java.util.concurrent.Executor { it.run() },
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    runCatching {
                        bmp = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                    }
                    latch.countDown()
                }
                override fun onFailure(errorCode: Int) {
                    lastGrabFail.set(errorCode)   // 🩺 v3.9.4: asli code batao chief ke lastWhy me
                    latch.countDown()
                }
            })
        val ok = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        val raw = bmp ?: return null
        if (!ok) { runCatching { raw.recycle() }; return null }
        val scaled = if (raw.width > maxW) {
            val h = (raw.height * (maxW.toFloat() / raw.width)).toInt()
            android.graphics.Bitmap.createScaledBitmap(raw, maxW, h, true).also { raw.recycle() }
        } else raw
        val ba = java.io.ByteArrayOutputStream(scaled.width * scaled.height / 6)
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, ba)
        scaled.recycle()
        return ba.toByteArray()
    }

    // ─────────── 👆 TAP ───────────
    fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y); lineTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ─────────── 🖐 SWIPE (scroll) ───────────
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durMs: Long = 350): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, durMs))
            .build()
        return dispatchGesture(g, null, null)
    }

    /** 👆 long-press (for delete-type flows) */
    fun longPress(x: Float, y: Float, holdMs: Long = 900): Boolean {
        val p = Path().apply { moveTo(x, y); lineTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, holdMs))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ─────────── 🔙 GLOBAL KEYS ───────────
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun quickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun powerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    // ─────────── ⌨ TYPE karo (focused editable node pe) ───────────
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) { it.isEditable && it.className?.contains("EditText") == true } ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** 🔎 generic node finder (recursive) */
    private fun findNode(
        r: AccessibilityNodeInfo?,
        match: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (r == null) return null
        if (match(r)) return r
        for (i in 0 until r.childCount) {
            val f = findNode(r.getChild(i), match)
            if (f != null) return f
        }
        return null
    }

    /** 🖱 node click (ya parent climb) */
    fun clickNode(match: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var n = findNode(root, match)
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    /** 📜 screen ki saari visible texts collect karo */
    fun dumpTexts(): List<String> {
        val out = LinkedHashSet<String>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrBlank() && t.length > 1) out.add(t.take(180))
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return out.toList()
    }
}
