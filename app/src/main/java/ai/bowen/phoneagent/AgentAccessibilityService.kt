package ai.bowen.phoneagent

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Opt-in Accessibility observation and user-invoked actions. No autonomous background control. */
class AgentAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var active: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onInterrupt() {}
    override fun onDestroy() { active = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    data class Element(val label: String, val className: String,
                       val bounds: String, val clickable: Boolean)
    data class Observation(val packageName: String?, val elements: List<Element>) {
        fun contains(query: String): Boolean =
            query.isNotBlank() && elements.any { it.label.contains(query, ignoreCase = true) }
        fun summary(): String = buildString {
            append("Package: ").append(packageName ?: "unknown").append("\n")
            elements.forEachIndexed { index, element ->
                append("#").append(index).append(" ").append(element.label)
                    .append(" [").append(element.className).append("] ")
                    .append(element.bounds).append("\n")
            }
        }
    }

    fun observe(maxNodes: Int = 120): Observation {
        val root = rootInActiveWindow ?: return Observation(null, emptyList())
        val elements = mutableListOf<Element>()
        fun scan(node: AccessibilityNodeInfo) {
            if (elements.size >= maxNodes) return
            if (node.isVisibleToUser) {
                val label = (node.text?.toString()?.takeUnless { it.isBlank() }
                    ?: node.contentDescription?.toString()).orEmpty()
                if (label.isNotBlank() || node.isClickable || node.isEditable) {
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    elements += Element(label.take(120), node.className?.toString().orEmpty(),
                        rect.toShortString(), node.isClickable)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child)
                child.recycle()
                if (elements.size >= maxNodes) break
            }
        }
        scan(root)
        return Observation(root.packageName?.toString(), elements)
    }

    /** No operation on password nodes; confirmation/send/payment actions are blocked in the UI. */
    fun tapLabel(query: String): Boolean {
        if (!ActionPolicy.permitted(AgentAction.TapLabel(query))) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query)
        for (node in matches) {
            if (!node.isVisibleToUser || node.isPassword) continue
            var candidate: AccessibilityNodeInfo? = node
            while (candidate != null) {
                if (candidate.isClickable &&
                    candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                candidate = candidate.parent
            }
        }
        return false
    }
}
