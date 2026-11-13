package com.usman.agent

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    companion object {
        @Volatile var current: MainActivity? = null

        // Brings this app to the front, asks yes/no, returns the answer.
        fun ask(ctx: Context, msg: String): Boolean {
            val latch = CountDownLatch(1)
            var ok = false
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            Thread.sleep(1000)
            val activity = current ?: return false
            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("ہاں") { _, _ -> ok = true; latch.countDown() }
                    .setNegativeButton("نہیں") { _, _ -> latch.countDown() }
                    .show()
            }
            latch.await(120, TimeUnit.SECONDS)
            return ok
        }
    }

    private lateinit var logView: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        current = this
        val p = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        val key = EditText(this).apply { hint = "Gemini API key"; setText(p.getString("key", "")) }
        val model = EditText(this).apply { hint = "Model"; setText(p.getString("model", "gemini-3.8-flash")) }
        val goal = EditText(this).apply { hint = "کیا کام کروانا ہے؟"; minLines = 2 }
        val accBtn = Button(this).apply {
            text = "1) Accessibility آن کریں"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val go = Button(this).apply { text = "2) شروع کریں" }
        val stop = Button(this).apply { text = "روکیں" }
        logView = TextView(this)
        val scroll = ScrollView(this).apply { addView(logView) }

        go.setOnClickListener {
            p.edit().putString("key", key.text.toString()).putString("model", model.text.toString()).apply()
            logView.text = ""
            Agent.start(goal.text.toString(), key.text.toString().trim(), model.text.toString().trim())
        }
        stop.setOnClickListener { Agent.stop() }

        Agent.onLog = { s -> runOnUiThread { logView.append(s + "\n") } }

        listOf(key, model, goal, accBtn, go, stop).forEach { root.addView(it) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
}
