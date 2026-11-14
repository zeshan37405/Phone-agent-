package com.usman.agent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    fun ask(key: String, model: String, prompt: String, json: Boolean = true): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.doOutput = true
        c.connectTimeout = 20000
        c.readTimeout = 60000
        val body = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("role", "user").put("parts",
                JSONArray().put(JSONObject().put("text", prompt)))))
        if (json) body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw Exception("Gemini error $code: ${text.take(200)}")
        return JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
