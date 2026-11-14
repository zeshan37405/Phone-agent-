package com.usman.agent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import org.json.JSONObject

object Agent {
    @Volatile var running = false
    var onLog: (String) -> Unit = {}
    var memory: String = ""

    // Financial apps are never touched.
    private val BLOCKED = listOf(
        "bank", "easypaisa", "jazzcash", "paypal", "wallet", "sadapay", "nayapay",
        "finance", "upaisa", "hbl", "meezan", "ubl", "alfalah", "payment", "keenu", "raast"
    )
    // Actions whose button text matches this always need the user's approval.
    private val RISKY = Regex(
        "send|post|share|publish|delete|remove|submit|confirm|pay|بھیج|ڈیلیٹ|پوسٹ|شیئر|हटा|भेज|पोस्ट",
        RegexOption.IGNORE_CASE
    )

    fun isBlocked(s: String) = BLOCKED.any { s.lowercase().contains(it) }

    fun start(goal: String, key: String, model: String) {
        if (running) return
        val svc = AgentService.instance
        if (svc == null) { onLog("Accessibility سروس آن نہیں ہے۔ پہلا بٹن دبائیں۔"); return }
        running = true
        Thread {
            try { loop(svc, goal, key, model) } catch (e: Exception) { onLog("غلطی: ${e.message}") }
            running = false
            onLog("— ختم —")
        }.start()
    }

    fun stop() { running = false }

    private fun loop(svc: AgentService, goal: String, key: String, model: String) {
        val history = StringBuilder()
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1200)

        for (step in 1..20) {
            if (!running) return
            val pkg = svc.currentPackage()
            if (isBlocked(pkg)) {
                onLog("بلاک شدہ مالیاتی ایپ ($pkg)، رک گیا۔")
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return
            }
            val screen = svc.readScreen()
            val prompt = """
You control an Android phone through accessibility. Goal from the user: "$goal"
Current app: $pkg
About the user (memory): $memory
Screen elements (index, type, text):
$screen
Previous steps:
${history.toString().takeLast(1500)}
Reply ONLY with one JSON object:
{"action":"click|type|open|back|home|scroll|wait|done|ask","index":number,"text":"text to type","app":"app name to open","message":"short Urdu explanation for the user"}
Rules: one action per reply. Use "done" when finished, "ask" if you need info from the user. Never open banking or payment apps. Pressing Send/Post/Delete must be a click on that exact button.
""".trimIndent()

            val reply = Gemini.ask(key, model, prompt)
            val j = JSONObject(reply.trim().removePrefix("```json").removeSuffix("```").trim())
            val action = j.optString("action")
            val msg = j.optString("message")
            val idx = j.optInt("index", -1)
            onLog("$step) $action $msg")
            history.append("$step: $reply\n")

            when (action) {
                "done" -> { onLog("✅ $msg"); return }
                "ask" -> { onLog("❓ $msg"); return }
                "open" -> {
                    val name = j.optString("app")
                    val pm = svc.packageManager
                    val app = pm.getInstalledApplications(0).firstOrNull {
                        pm.getApplicationLabel(it).toString().contains(name, true) &&
                            pm.getLaunchIntentForPackage(it.packageName) != null
                    }
                    if (app == null) { onLog("ایپ نہیں ملی: $name") }
                    else if (isBlocked(app.packageName) || isBlocked(pm.getApplicationLabel(app).toString())) {
                        onLog("مالیاتی ایپ بلاک ہے: $name"); return
                    } else {
                        svc.startActivity(pm.getLaunchIntentForPackage(app.packageName)!!
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                "click" -> {
                    val label = svc.label(idx)
                    if (RISKY.containsMatchIn(label)) {
                        val ok = MainActivity.ask(svc, "$msg\n\nبٹن: [$label]\nدبانے دوں؟")
                        // return to the app we were working in
                        svc.packageManager.getLaunchIntentForPackage(pkg)?.let {
                            svc.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        Thread.sleep(1500)
                        if (!ok) { onLog("آپ نے منع کر دیا۔"); return }
                        svc.readScreen()
                        val newIdx = svc.indexOfLabel(label)
                        if (newIdx < 0) { onLog("بٹن دوبارہ نہیں ملا، پھر کوشش کرتا ہوں۔"); continue }
                        svc.click(newIdx)
                    } else {
                        svc.click(idx)
                    }
                }
                "type" -> svc.type(idx, j.optString("text"))
                "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "scroll" -> svc.scroll()
                "wait" -> {}
                else -> { onLog("نامعلوم عمل: $action"); return }
            }
            Thread.sleep(1500)
        }
        onLog("20 قدم پورے ہو گئے، رک گیا۔")
    }
}
