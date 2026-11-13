package com.usman.agent

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentService : AccessibilityService() {
    companion object { @Volatile var instance: AgentService? = null }

    private val nodes = ArrayList<AccessibilityNodeInfo>()

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun currentPackage(): String = rootInActiveWindow?.packageName?.toString() ?: ""

    private fun labelOf(n: AccessibilityNodeInfo): String =
        (n.text ?: n.contentDescription)?.toString() ?: ""

    fun readScreen(): String {
        nodes.clear()
        val root = rootInActiveWindow ?: return "(empty screen)"
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo) {
            val t = labelOf(n).take(80)
            if (t.isNotBlank() || n.isClickable || n.isEditable) {
                nodes.add(n)
                val kind = if (n.isEditable) "INPUT" else if (n.isClickable) "BTN" else "TEXT"
                sb.append("[${nodes.size - 1}] $kind $t\n")
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        return sb.toString().take(6000)
    }

    fun label(idx: Int): String = nodes.getOrNull(idx)?.let { labelOf(it) } ?: ""
    fun indexOfLabel(l: String): Int = nodes.indexOfFirst { labelOf(it) == l }

    fun click(idx: Int): Boolean {
        var n: AccessibilityNodeInfo? = nodes.getOrNull(idx)
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun type(idx: Int, text: String): Boolean {
        val n = nodes.getOrNull(idx) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(): Boolean {
        val root = rootInActiveWindow ?: return false
        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it)?.let { r -> return r } }
            return null
        }
        return find(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }
}
