package ai.bowen.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Manual diagnostic UI. A model-driven planner is not implemented yet. */
class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        panel.addView(TextView(this).apply {
            text = "PhoneAgent diagnostic prototype\nManual actions and timing only; no AI model yet."
            textSize = 18f
        })
        input = EditText(this).apply {
            hint = "Package name or visible label"
            setSingleLine(true)
        }
        panel.addView(input)
        fun button(label: String, command: () -> Unit) {
            panel.addView(Button(this).apply { text = label; setOnClickListener { command() } })
        }
        button("Enable Accessibility in Android Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Observe visible screen") { observeNow() }
        button("Open Android Settings") { execute("open_settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS)); true
        } }
        button("Open app from package name") { execute("open_app") {
            val pkg = input.text.toString().trim()
            if (!ActionPolicy.permitted(AgentAction.OpenApp(pkg))) false
            else {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch == null) false else { startActivity(launch); true }
            }
        } }
        button("Tap visible text (manual)") { execute("tap_text") {
            AgentAccessibilityService.active?.tapLabel(input.text.toString().trim()) == true
        } }
        button("Verify visible text") { execute("verify_text") {
            AgentAccessibilityService.active?.observe()?.contains(input.text.toString().trim()) == true
        } }
        button("System Back") { execute("back") {
            AgentAccessibilityService.active?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) == true
        } }
        output = TextView(this).apply {
            text = "Grant Accessibility permission manually; use a test device. No task results claimed."
        }
        panel.addView(output)
        setContentView(ScrollView(this).apply { addView(panel) })
    }

    private fun observeNow() {
        val start = SystemClock.elapsedRealtimeNanos()
        val service = AgentAccessibilityService.active
        val observation = service?.observe()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        output.text = (observation?.summary() ?: "Accessibility service not connected")
            .take(12000) + "\nObservation: " +
                String.format(Locale.US, "%.2f ms", elapsedMs)
        record("observe", observation != null, elapsedMs)
    }

    private fun execute(event: String, action: () -> Boolean) {
        val start = SystemClock.elapsedRealtimeNanos()
        val dispatched = try { action() } catch (e: Exception) {
            output.text = "Error: " + e.message
            false
        }
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        output.text = event + ": " + (if (dispatched) "DISPATCHED" else "FAILED") +
            "\nLatency: " + String.format(Locale.US, "%.2f ms", elapsedMs) +
            "\nDispatch does not prove end-to-end task success."
        record(event, dispatched, elapsedMs)
    }

    private fun record(event: String, dispatched: Boolean, elapsedMs: Double) {
        val eventJson = JSONObject()
            .put("event", event)
            .put("dispatched", dispatched)
            .put("elapsed_ms", elapsedMs)
            .put("timestamp_ms", System.currentTimeMillis())
        File(filesDir, "diagnostic_events.jsonl").appendText(eventJson.toString() + "\n")
    }
}
