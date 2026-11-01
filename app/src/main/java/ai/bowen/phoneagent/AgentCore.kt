package ai.bowen.phoneagent

/** Interface for a future model-based planner. All actions must go through policy checks. */
sealed interface AgentAction {
    data class OpenApp(val packageName: String) : AgentAction
    data object OpenSettings : AgentAction
    data class TapLabel(val label: String) : AgentAction
    data class VerifyLabel(val label: String) : AgentAction
    data object Back : AgentAction
}

enum class TaskFamily { MEDIA, SETTINGS, MESSAGING }

data class Scenario(val id: String, val family: TaskFamily,
                    val objective: String, val expectedEvidence: String)

object ScenarioCatalog {
    val cases = listOf(
        Scenario("media_discovery", TaskFamily.MEDIA,
            "Find a named title in a streaming app, for example Hulu or YouTube",
            "Correct title and target application verified; playback tested separately"),
        Scenario("device_settings", TaskFamily.SETTINGS,
            "Locate an Android setting and verify its current state",
            "Correct Settings screen and requested value observed"),
        Scenario("message_draft", TaskFamily.MESSAGING,
            "Open a chat app and draft a message for a specified recipient",
            "Correct recipient and text shown; sending needs explicit consent")
    )
}

/** Deliberately conservative proof-of-concept. Not a general-purpose policy engine. */
object ActionPolicy {
    private val sensitive = Regex(
        "(?i)^(pay|purchase|buy|delete|erase|send|confirm|submit|transfer|place order)$"
    )
    fun permitted(action: AgentAction): Boolean = when (action) {
        is AgentAction.OpenApp -> action.packageName.matches(
            Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
        is AgentAction.TapLabel -> action.label.isNotBlank() &&
            !sensitive.matches(action.label.trim())
        is AgentAction.VerifyLabel -> action.label.isNotBlank()
        AgentAction.Back, AgentAction.OpenSettings -> true
    }
}
