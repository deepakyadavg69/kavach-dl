package com.kavach.app.core.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 📡 TelegramApi — Telegram Bot API se baat karne ka layer.
 * (OkHttp + JSON — plain aur reliable, koi bhaari library nahi)
 */
class TelegramApi(private val token: String) {

    private val base = "https://api.telegram.org/bot$token"

    /** 🔑 stable per-bot id (engine offset/dedupe keys ke liye — secret expose nahi) */
    val botKey: String get() = token.substringBefore(":", "x")

    // Long polling ke liye lamba read timeout zaroori
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Bot zinda hai? naam/username check */
    suspend fun getMe(): JSONObject = withContext(Dispatchers.IO) {
        val body = get("$base/getMe")
        body.getJSONObject("result")
    }

    /** 📥 Naye messages lao (long polling). timeout sec tak Telegram wait karta hai. */
    suspend fun getUpdates(offset: Long, timeoutSec: Int = 50): JSONArray =
        withContext(Dispatchers.IO) {
            val body = get("$base/getUpdates?offset=$offset&timeout=$timeoutSec&allowed_updates=%5B%22message%22%2C%22callback_query%22%5D")
            body.getJSONArray("result")
        }

    /** 📤 Text message bhejo (parseMode = "Markdown" ho to `copy-format` chahiye... 🖱) */
    suspend fun sendMessage(chatId: Long, text: String, parseMode: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("chat_id", chatId)
                .put("text", text)
                .put("disable_notification", false)
            if (parseMode != null) payload.put("parse_mode", parseMode)
            val body = payload.toString().toRequestBody(jsonType)
            val resp = post("$base/sendMessage", body)
            resp.optBoolean("ok", false)
        }

    /** 📷 Photo bhejo (JPEG/WebP) — chor ki photo aise hi aayegi.
     *  🩹 v5.5.0 FINAL: caption 1024-cap (TG isse lamba ho toh pura PHOTO hi fail ho jaata tha — "photo fail" ka wahi chor) */
    suspend fun sendPhoto(chatId: Long, jpeg: ByteArray, caption: String? = null, markup: String? = null,
                          mediaMime: String = "image/jpeg", mediaName: String = "kavach.jpg"): Boolean =
        withContext(Dispatchers.IO) {
            val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart(
                    "photo", mediaName,
                    jpeg.toRequestBody(mediaMime.toMediaType())
                )
            caption?.let { mp.addFormDataPart("caption", it.take(1024)) }
            markup?.let { mp.addFormDataPart("reply_markup", it.take(4096)) }
            val resp = post("$base/sendPhoto", mp.build())
            resp.optBoolean("ok", false)
        }

    // ─── internal helpers ───
    private fun get(url: String): JSONObject {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            return JSONObject(r.body?.string() ?: "{}")
        }
    }

    private fun post(url: String, body: okhttp3.RequestBody): JSONObject {
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            return JSONObject(r.body?.string() ?: "{}")
        }
    }

    // ═════════ 🎮 MENU / INTERACTIVE (v1.4.0) ═════════

    // ─── 🪄 v4.0.0: FRAME-EDIT lane — photo msg ko hi badal do (chat na bhare!) ───
    /** 📸 photo bhejo + message_id wapas (tracking ke liye) */
    suspend fun sendPhotoId(chatId: Long, jpeg: ByteArray, caption: String? = null, markup: String? = null,
                            mediaMime: String = "image/jpeg", mediaName: String = "kavach.jpg"): Int =
        withContext(Dispatchers.IO) {
            val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("photo", mediaName, jpeg.toRequestBody(mediaMime.toMediaType()))
            caption?.let { mp.addFormDataPart("caption", it) }
            markup?.let { mp.addFormDataPart("reply_markup", it) }
            post("$base/sendPhoto", mp.build())
                .optJSONObject("result")?.optInt("message_id", -1) ?: -1
        }

    /** ♻️ purani photo msg edit — naya media attach: chat SA-SUTRA rehti ✨ */
    suspend fun editPhoto(chatId: Long, msgId: Long, jpeg: ByteArray, caption: String? = null, markup: String? = null,
                          mediaMime: String = "image/jpeg", mediaName: String = "kavach.jpg"): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaJson = JSONObject().put("type", "photo").put("media", "attach://f1")
                caption?.let { mediaJson.put("caption", it) }
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("message_id", msgId.toString())
                    .addFormDataPart("media", mediaJson.toString())
                    .addFormDataPart("f1", mediaName, jpeg.toRequestBody(mediaMime.toMediaType()))
                markup?.let { mp.addFormDataPart("reply_markup", it) }
                post("$base/editMessageMedia", mp.build()).optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 🧹 message delete karo (Clean-Bot hygiene — chat na bhare) */
    suspend fun deleteMessage(chatId: Long, msgId: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (msgId <= 0) return@runCatching false
                val p = JSONObject().put("chat_id", chatId).put("message_id", msgId)
                post("$base/deleteMessage", p.toString().toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 📌 Chat ke side me ☰ MENU button (commands list) har waqt BAITHA rakho */
    suspend fun installMenuButton(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = JSONObject().put("menu_button", JSONObject().put("type", "commands"))
                post("$base/setChatMenuButton", p.toString().toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 🖱️ callback tap ka jawab (loading spinner hatao + chhota toast) */
    suspend fun answerCb(cbId: String, toast: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = JSONObject().put("callback_query_id", cbId)
                if (toast.isNotEmpty()) p.put("text", toast)
                post("$base/answerCallbackQuery", p.toString().toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 📤 Menu message bhejo — message_id wapas */
    suspend fun sendKb(chatId: Long, text: String, markupJson: String): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = JSONObject().put("chat_id", chatId).put("text", text)
                    .put("reply_markup", JSONObject(markupJson))
                post("$base/sendMessage", p.toString().toRequestBody(jsonType))
                    .optJSONObject("result")?.optInt("message_id", -1) ?: -1
            }.getOrDefault(-1)
        }

    /** ✏️ Menu ko in-place badlo (category switch ka feel!) */
    suspend fun editKb(chatId: Long, msgId: Long, text: String, markupJson: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = JSONObject().put("chat_id", chatId).put("message_id", msgId)
                    .put("text", text).put("reply_markup", JSONObject(markupJson))
                post("$base/editMessageText", p.toString().toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    // ═════════ 🤖 BOT IQ (v1.6.0) ═════════

    /** ☰ Menu button + '/' autocomplete me command list */
    suspend fun setMyCommands(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val cmds = "[" +
                    "{\"command\":\"menu\",\"description\":\"🎮 Command Center (sab buttons yahan)\"}," +
                    "{\"command\":\"photo\",\"description\":\"🤳 front-cam silent photo\"}," +
                    "{\"command\":\"status\",\"description\":\"📊 device ka haal\"}," +
                    "{\"command\":\"location\",\"description\":\"📍 live GPS + map\"}]"
                post("$base/setMyCommands", "{\"commands\":$cmds}".toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** ✍️ Bot profile texts (profile me dikhta hai) */
    suspend fun setMyDescription(): Boolean =
        setMyDescription("🛡️ KAVACH — private device bodyguard 🚨 (sirf owner ke commands)", "🛡️ Kavach bodyguard")

    /** ✍️ v5.1.0 parameterized desc+short (bot-style engine se aaega) */
    suspend fun setMyDescription(desc: String, short: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                post("$base/setMyDescription",
                    ("{" + "\"description\":" + org.json.JSONObject.quote(desc.take(512)) + "}").toRequestBody(jsonType))
                post("$base/setMyShortDescription",
                    ("{" + "\"short_description\":" + org.json.JSONObject.quote(short.take(120)) + "}").toRequestBody(jsonType))
                true
            }.getOrDefault(false)
        }

    /** 🏷 v5.1.0: bot ka naam set (stale-check ke liye get bhi) */
    suspend fun setMyName(name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                post("$base/setMyName",
                    ("{" + "\"name\":" + org.json.JSONObject.quote(name.take(64)) + "}").toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    suspend fun getMyName(): String? =
        withContext(Dispatchers.IO) {
            runCatching { get("$base/getMyName").optJSONObject("result")?.optString("name") }.getOrNull()
        }

    /** 🖼 v5.1.0: PROFILE PHOTO (Bot API `setMyProfilePhoto` — multipart attach) */
    suspend fun setMyProfilePhoto(png: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val meta = "{\"type\":\"static\",\"photo\":\"attach://dp\"}"
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("photo", null, meta.toRequestBody("application/json".toMediaType()))
                    .addFormDataPart("dp", "kavach_dp.png", png.toRequestBody("image/png".toMediaType()))
                    .build()
                post("$base/setMyProfilePhoto", mp).optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 📄 DOCUMENT bhejo (CSV/TXT exports ke liye) */
    suspend fun sendDocument(chatId: Long, bytes: ByteArray, mime: String, name: String, caption: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("document", name, bytes.toRequestBody(mime.toMediaType()))
                caption?.let { mp.addFormDataPart("caption", it) }
                post("$base/sendDocument", mp.build()).optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** ⌟ Chat me "typing/uploading..." indicator (5s dikhta hai) */
    suspend fun sendChatAction(chatId: Long, action: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                post("$base/sendChatAction",
                    "{\"chat_id\":$chatId,\"action\":\"$action\"}".toRequestBody(jsonType))
                    .optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 🖼️ ALBUM (sendMediaGroup): ek tap ka poora grid — burst/timelapse/gallery ke liye */
    suspend fun sendMediaGroup(chatId: Long, photos: List<Pair<ByteArray, String>>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (photos.isEmpty()) return@runCatching false
                val group = photos.take(10)
                val attach = group.indices.joinToString(",") { i ->
                    "{\"type\":\"photo\",\"media\":\"attach://p$i\",\"caption\":\"${group[i].second.replace("\"", "\\\"").replace("\n", " ")}\"}"
                }
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("media", "[$attach]")
                group.forEachIndexed { i, (bytes, _) ->
                    mp.addFormDataPart("p$i", "p$i.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                }
                post("$base/sendMediaGroup", mp.build()).optBoolean("ok", false)
            }.getOrDefault(false)
        }

    /** 🎬🎙️ Video/voice/audio/document bhejo */
    /** 📥 getFile → server path (upload-to-device pipeline) */
    suspend fun getFilePath(fileId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val ok = post("$base/getFile", "{\"file_id\":\"$fileId\"}".toRequestBody(jsonType))
                if (ok.optBoolean("ok")) ok.optJSONObject("result")?.optString("file_path") else null
            }.getOrNull()
        }

    /** 📥 file bytes download (Bot API file server) */
    suspend fun downloadFile(filePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.telegram.org/file/bot$token/$filePath"
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            }.getOrNull()
        }

    /** 🎬 file-se video (screen-record ke liye) */
    suspend fun sendVideoFile(chatId: Long, file: java.io.File, caption: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("video", file.name, file.readBytes().toRequestBody("video/mp4".toMediaType()))
                caption?.let { mp.addFormDataPart("caption", it) }
                post("$base/sendVideo", mp.build()).optBoolean("ok", false)
            }.getOrDefault(false)
        }

    suspend fun sendMedia(
        chatId: Long, kind: String, bytes: ByteArray, mime: String, filename: String, caption: String? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val method = when (kind) {
                    "video" -> "sendVideo"; "voice" -> "sendVoice"
                    "audio" -> "sendAudio"; else -> "sendDocument"
                }
                val field = if (kind in listOf("video", "voice", "audio")) kind else "document"
                val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart(field, filename, bytes.toRequestBody(mime.toMediaType()))
                caption?.let { mp.addFormDataPart("caption", it) }
                post("$base/$method", mp.build()).optBoolean("ok", false)
            }.getOrDefault(false)
        }
}
