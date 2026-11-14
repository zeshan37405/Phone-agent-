package com.usman.agent

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    companion object {
        @Volatile var current: MainActivity? = null
        fun ask(ctx: Context, msg: String): Boolean {
            val latch = CountDownLatch(1)
            var ok = false
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            Thread.sleep(1000)
            current?.runOnUiThread {
                AlertDialog.Builder(current)
                    .setMessage(msg).setCancelable(false)
                    .setPositiveButton("ہاں") { _, _ -> ok = true; latch.countDown() }
                    .setNegativeButton("نہیں") { _, _ -> latch.countDown() }
                    .show()
            } ?: return false
            latch.await(120, TimeUnit.SECONDS)
            return ok
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var content: FrameLayout
    private val panels = ArrayList<View>()
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var goal: EditText
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var memory: EditText
    private lateinit var routinesRow: LinearLayout
    private lateinit var msgList: LinearLayout
    private lateinit var summaryView: TextView
    private lateinit var postTopic: EditText
    private lateinit var postOut: EditText
    private val SLATE = 0xFF334155.toInt()
    private val GREEN = 0xFF16A34A.toInt()
    private val RED = 0xFFDC2626.toInt()
    private val BLUE = 0xFF3B82F6.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(c: Int, r: Int = 16) =
        GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun button(t: String, c: Int = SLATE, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE); textSize = 16f
        background = rounded(c)
        setOnClickListener { onClick() }
    }
    private fun field(h: String) = EditText(this).apply {
        hint = h
        setHintTextColor(0xFF8895A7.toInt())
        setTextColor(Color.WHITE)
        background = rounded(0xFF1E2A3A.toInt(), 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    private fun label(t: String, size: Float = 15f, color: Int = 0xFFCBD5E1.toInt()) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color) }
    private fun LinearLayout.put(v: View, top: Int = 10) {
        addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(4); marginEnd = dp(4)
        }) }
    }
    private fun newPanel(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(16))
        }
        val sv = ScrollView(this).apply { addView(col) }
        panels.add(sv)
        content.addView(sv, FrameLayout.LayoutParams(-1, -1))
        return col
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun speak(t: String) { tts?.speak(t.take(3500), TextToSpeech.QUEUE_FLUSH, null, "s") }
    private fun apiKey() = key.text.toString().trim()
    private fun modelName() = model.text.toString().trim()
    private fun aboutMe() = memory.text.toString().trim()
    private fun saveSettings() {
        prefs.edit().putString("key", apiKey()).putString("model", modelName())
            .putString("memory", aboutMe()).apply()
        Agent.memory = aboutMe()
    }

    private val tick = object : Runnable {
        override fun run() {
            val a = AgentService.instance != null
            val n = NotifService.connected
            status.text = (if (a) "● ایجنٹ جڑا ہوا" else "● ایجنٹ بند (Accessibility)") + "    " +
                (if (n) "● پیغامات جڑے" else "● پیغامات بند")
            status.setTextColor(if (a && n) 0xFF22C55E.toInt() else 0xFFF59E0B.toInt())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        current = this
        prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.language = Locale("ur", "PK")
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1220.toInt())
            setPadding(dp(14), dp(36), dp(14), dp(10))
        }
        root.put(label("Phone Agent", 26f, Color.WHITE), 0)
        status = label("", 13f)
        root.put(status, 2)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        buildAgentPanel()
        buildMessagesPanel()
        buildPostPanel()
        buildSettingsPanel()
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf("🤖 ایجنٹ", "💬 پیغامات", "✍️ پوسٹ", "⚙️ سیٹنگ")
        names.forEachIndexed { i, n ->
            tabs.addView(button(n, 0xFF1E293B.toInt()) { select(i) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        root.put(tabs, 6)
        setContentView(root)
        Agent.memory = aboutMe()
        Agent.onLog = { s ->
            runOnUiThread {
                logView.append(s + "\n")
                if (s.startsWith("✅") || s.startsWith("❓")) speak(s.drop(2))
            }
        }
        select(if (apiKey().isEmpty()) 3 else 0)
    }

    private fun select(i: Int) {
        panels.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
        if (i == 1) refreshMessages()
    }
    private fun buildAgentPanel() {
        val a = newPanel()
        goal = field("کیا کام کروانا ہے؟ لکھیں یا مائیک دبائیں").apply { minLines = 2 }
        a.put(goal, 0)
        val mic = Button(this).apply {
            text = "🎤"; textSize = 42f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(BLUE) }
            setOnClickListener { startVoice() }
        }
        a.addView(mic, LinearLayout.LayoutParams(dp(110), dp(110)).apply {
            topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL
        })
        a.put(row(button("▶ شروع", GREEN) { startTask() }, button("■ روکیں", RED) { Agent.stop() }), 14)
        a.put(button("💾 اس کام کو محفوظ کریں") { saveRoutine() }, 10)
        a.put(label("محفوظ شدہ کام (دبائیں = چلائیں، دیر تک دبائیں = حذف):", 13f), 12)
        routinesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hs = HorizontalScrollView(this).apply { addView(routinesRow) }
        a.put(hs, 4)
        refreshRoutines()
        logView = label("", 15f)
        logView.background = rounded(0xFF111A2B.toInt(), 12)
        logView.setPadding(dp(12), dp(12), dp(12), dp(12))
        logView.minHeight = dp(120)
        a.put(logView, 14)
    }
    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بولیں...")
        }
        try { startActivityForResult(i, 77) }
        catch (e: Exception) { toast("آواز کی سروس نہیں ملی۔ Google ایپ اپڈیٹ کریں۔") }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) { goal.setText(text); startTask() }
        }
    }
    private fun startTask() {
        saveSettings()
        if (apiKey().isEmpty()) { toast("پہلے سیٹنگ میں API key لگائیں"); select(3); return }
        logView.text = ""
        Agent.start(goal.text.toString(), apiKey(), modelName())
    }
    private fun routines(): MutableList<String> =
        (prefs.getString("routines", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
    private fun saveRoutine() {
        val g = goal.text.toString().trim()
        if (g.isEmpty()) { toast("پہلے کام لکھیں"); return }
        val r = routines(); if (!r.contains(g)) r.add(g)
        prefs.edit().putString("routines", r.joinToString("\n")).apply()
        refreshRoutines()
    }
    private fun refreshRoutines() {
        routinesRow.removeAllViews()
        routines().forEach { r ->
            val b = button(r.take(24), 0xFF1D4ED8.toInt()) { goal.setText(r); startTask() }
            b.setOnLongClickListener {
                val l = routines(); l.remove(r)
                prefs.edit().putString("routines", l.joinToString("\n")).apply()
                refreshRoutines(); true
            }
            routinesRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
    }

    private fun buildMessagesPanel() {
        val m = newPanel()
        m.put(button("🔔 پیغامات کی اجازت دیں (ایک بار)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, 0)
        m.put(row(button("🔄 تازہ کریں") { refreshMessages() },
            button("📋 خلاصہ سناؤ", GREEN) { summarize() }), 10)
        summaryView = label("", 15f)
        m.put(summaryView, 12)
        msgList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        m.put(msgList, 8)
    }
    private fun refreshMessages() {
        msgList.removeAllViews()
        val items = NotifService.items.take(20)
        if (items.isEmpty()) {
            msgList.put(label("ابھی کوئی پیغام نہیں۔ اجازت دینے کے بعد نئے پیغامات یہاں آئیں گے۔", 14f))
            return
        }
        items.forEach { it0 ->
            val t = label("${it0.app} • ${it0.title}\n${it0.text.take(90)}" +
                (if (it0.reply != null) "   ↩️" else ""), 15f, Color.WHITE)
            t.background = rounded(0xFF1E2A3A.toInt(), 12)
            t.setPadding(dp(12), dp(10), dp(12), dp(10))
            t.setOnClickListener { showMessage(it0) }
            msgList.put(t, 8)
        }
    }
    private fun showMessage(item: Item) {
        val b = AlertDialog.Builder(this).setTitle("${item.app} • ${item.title}").setMessage(item.text)
        if (item.reply != null) b.setPositiveButton("✍️ AI جواب بنائے") { _, _ -> draftReply(item) }
        b.setNegativeButton("بند", null).show()
    }
    private fun draftReply(item: Item) {
        if (apiKey().isEmpty()) { toast("پہلے API key لگائیں"); return }
        toast("جواب بن رہا ہے...")
        val prompt = """
Write a short, natural reply to this message, as if I (the user) am replying. 
Use the same language and script as the message (for example Roman Urdu if the message is Roman Urdu).
About me: ${aboutMe()}
App: ${item.app}
From: ${item.title}
Message: ${item.text}
Return ONLY the reply text.
""".trimIndent()
        Thread {
            try {
                val r = Gemini.ask(apiKey(), modelName(), prompt, false).trim()
                runOnUiThread { confirmSend(item, r) }
            } catch (e: Exception) { runOnUiThread { toast("غلطی: ${e.message}") } }
        }.start()
    }
    private fun confirmSend(item: Item, draft: String) {
        val et = EditText(this).apply { setText(draft); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this)
            .setTitle("جواب (بدل سکتے ہیں)")
            .setView(et)
            .setPositiveButton("بھیجیں") { _, _ ->
                val ok = NotifService.sendReply(this, item, et.text.toString())
                toast(if (ok) "بھیج دیا" else "نہیں بھیج سکا (پیغام شاید ہٹ چکا)")
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }
    private fun summarize() {
        val items = NotifService.items.take(30)
        if (items.isEmpty()) { toast("ابھی کوئی پیغام نہیں، اجازت چیک کریں"); return }
        if (apiKey().isEmpty()) { toast("پہلے API key لگائیں"); return }
        summaryView.text = "خلاصہ بن رہا ہے..."
        val list = items.joinToString("\n") { "- ${it.app}: ${it.title}: ${it.text.take(150)}" }
        val prompt = """
These are my recent phone notifications:
$list

About me: ${aboutMe()}
Summarize in simple Urdu (max 8 short lines). Put anything urgent or important first. Skip spam and promotions.
""".trimIndent()
        Thread {
            try {
                val r = Gemini.ask(apiKey(), modelName(), prompt, false).trim()
                runOnUiThread { summaryView.text = r; speak(r) }
            } catch (e: Exception) { runOnUiThread { summaryView.text = "غلطی: ${e.message}" } }
        }.start()
    }

    private fun buildPostPanel() {
        val p = newPanel()
        postTopic = field("پوسٹ کس بارے میں ہو؟ (مثلاً: اپنی نئی دکان / جمعہ مبارک)").apply { minLines = 2 }
        p.put(postTopic, 0)
        p.put(button("✨ پوسٹ بناؤ", GREEN) { makePost() }, 10)
        postOut = field("یہاں تیار پوسٹ آئے گی، آپ بدل سکتے ہیں").apply { minLines = 6 }
        p.put(postOut, 10)
        p.put(button("📤 شیئر کریں (Facebook / Instagram / WhatsApp)", BLUE) { sharePost() }, 10)
    }
    private fun makePost() {
        if (apiKey().isEmpty()) { toast("پہلے API key لگائیں"); return }
        val topic = postTopic.text.toString().trim()
        if (topic.isEmpty()) { toast("پہلے موضوع لکھیں"); return }
        postOut.setText("بن رہی ہے...")
        val prompt = """
Write a social media post in Urdu about: $topic
Natural, friendly, engaging, with a few fitting emojis and 3-5 hashtags.
About me: ${aboutMe()}
Return ONLY the post text.
""".trimIndent()
        Thread {
            try {
                val r = Gemini.ask(apiKey(), modelName(), prompt, false).trim()
                runOnUiThread { postOut.setText(r) }
            } catch (e: Exception) { runOnUiThread { postOut.setText("غلطی: ${e.message}") } }
        }.start()
    }
    private fun sharePost() {
        val t = postOut.text.toString()
        if (t.isBlank()) { toast("پہلے پوسٹ بنائیں"); return }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) },
            "شیئر کریں"))
    }

    private fun buildSettingsPanel() {
        val s = newPanel()
        key = field("Gemini API key").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        model = field("Model").apply { setText(prefs.getString("model", "gemini-3.8-flash")) }
        memory = field("میرے بارے میں (نام، کام، بات کا انداز...). اسسٹنٹ اسے یاد رکھے گا").apply {
            minLines = 4
            setText(prefs.getString("memory", ""))
        }
        s.put(key, 0)
        s.put(model, 10)
        s.put(memory, 10)
        s.put(button("💾 محفوظ کریں", GREEN) { saveSettings(); toast("محفوظ ہو گیا") }, 10)
        s.put(button("♿ Accessibility آن کریں (ایجنٹ کے لیے)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, 16)
        s.put(button("🔔 پیغامات کی اجازت (پیغامات کے لیے)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, 10)
        s.put(label("نوٹ: پیغامات کا خلاصہ اور جواب بنانے کے لیے متن Google Gemini کو بھیجا جاتا ہے۔ بینک اور پیمنٹ ایپس کے پیغامات پڑھے ہی نہیں جاتے۔", 13f), 16)
    }
    override fun onResume() { super.onResume(); current = this; handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); saveSettings(); super.onPause() }
    override fun onDestroy() {
        if (current === this) current = null
        tts?.shutdown()
        super.onDestroy()
    }
}
