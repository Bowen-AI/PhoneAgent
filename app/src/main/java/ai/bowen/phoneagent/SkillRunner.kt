package ai.bowen.phoneagent

/**
 * Deterministic, foreground-only replay policy. This is a pure Kotlin engine;
 * an Android adapter, recording, transcription and persistence are NOT implemented here.
 * The caller must supply an explicitly user-authorized device and confirmation gate.
 */
enum class SkillRunMode { TRIAL, VERIFIED_REPLAY }
enum class SkillRunStatus {
    SUCCESS, BLOCKED, REVALIDATION_REQUIRED, CONFIRMATION_REQUIRED,
    ACTION_FAILED, POSTCONDITION_FAILED
}

data class SkillRunResult(
    val status: SkillRunStatus,
    val completedSteps: Int,
    val failedStepIndex: Int? = null,
    val reason: String
)

/** Describes the foreground UI, never private text-field contents. */
data class SkillScreen(val packageName: String?, val uiFingerprint: String?, val labels: List<String>) {
    // UI-facing labels are normally case-insensitive; identifiers and user data are NOT.
    fun hasLabel(label: String): Boolean =
        label.isNotBlank() && labels.any { it.trim().equals(label.trim(), ignoreCase = true) }
}

/**
 * Implementations must return observed UI after actions, not merely an action dispatch result.
 * confirmAndAct MUST implement an allowlisted, user-mediated action; never evaluate free text.
 */
interface SkillDevice {
    fun installedVersion(packageName: String): Long?
    fun observe(): SkillScreen
    fun openApp(packageName: String): Boolean
    fun tapVisibleLabel(label: String): Boolean
    fun enterIntoField(fieldLabel: String, value: String): Boolean
    fun confirmAndAct(actionDescription: String): Boolean
}

/** Must show a fresh, explicit confirmation for EACH consequential action. */
fun interface SkillConfirmation { fun approvedFor(actionDescription: String): Boolean }

class SkillRunner {
    fun run(
        skill: SkillDefinition,
        currentOwnerId: String,
        mode: SkillRunMode,
        device: SkillDevice,
        parameters: Map<String, String> = emptyMap(),
        confirmation: SkillConfirmation = SkillConfirmation { false }
    ): SkillRunResult {
        fun fail(status: SkillRunStatus, step: Int?, reason: String) =
            SkillRunResult(status, step ?: 0, step, reason)

        // Owner and package comparisons are identifiers: always exact / case-sensitive.
        if (skill.ownerId != currentOwnerId) return fail(SkillRunStatus.BLOCKED, null, "Wrong owner")
        if (skill.steps.isEmpty() ||
            skill.steps.first() != SkillStep.OpenApp(skill.targetApp.packageName))
            return fail(SkillRunStatus.BLOCKED, null, "Skill must open its declared target first")
        if (mode == SkillRunMode.VERIFIED_REPLAY && skill.validation != SkillValidation.VERIFIED)
            return fail(SkillRunStatus.REVALIDATION_REQUIRED, null, "Skill is not verified")

        val version = device.installedVersion(skill.targetApp.packageName)
            ?: return fail(SkillRunStatus.BLOCKED, null, "Target app is not installed")
        if (version !in skill.targetApp.minimumSupportedVersionCode..skill.targetApp.maximumSupportedVersionCode ||
            (mode == SkillRunMode.VERIFIED_REPLAY && skill.verifiedVersionCode != version))
            return fail(SkillRunStatus.REVALIDATION_REQUIRED, null, "Target app version changed")

        var completed = 0
        for ((index, step) in skill.steps.withIndex()) {
            try {
                when (step) {
                    is SkillStep.OpenApp -> {
                        if (step.packageName != skill.targetApp.packageName)
                            return fail(SkillRunStatus.BLOCKED, index, "Unexpected target package")
                        if (!device.openApp(step.packageName))
                            return fail(SkillRunStatus.ACTION_FAILED, index, "Cannot open target app")
                        val screen = device.observe()
                        if (screen.packageName != step.packageName)
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Target app is not foreground")
                        if (skill.targetApp.evaluate(step.packageName, version, screen.uiFingerprint) != AppMatch.COMPATIBLE)
                            return fail(SkillRunStatus.REVALIDATION_REQUIRED, index, "Target UI changed")
                    }
                    is SkillStep.FindAndTap -> {
                        val before = device.observe()
                        if (before.packageName != skill.targetApp.packageName || !before.hasLabel(step.semanticLabel))
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Target label not visible")
                        if (!device.tapVisibleLabel(step.semanticLabel))
                            return fail(SkillRunStatus.ACTION_FAILED, index, "Tap failed")
                        val after = device.observe()
                        if (after.packageName != skill.targetApp.packageName ||
                            !after.hasLabel(step.expectedScreenEvidence))
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Tap result not verified")
                    }
                    is SkillStep.EnterParameter -> {
                        val before = device.observe()
                        if (before.packageName != skill.targetApp.packageName || !before.hasLabel(step.fieldLabel))
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Input field not visible")
                        if (isSensitiveField(step.fieldLabel))
                            return fail(SkillRunStatus.BLOCKED, index, "Sensitive input field blocked")
                        // Parameter *names* and values are exact. Never store or report the value.
                        val value = parameters[step.parameterName]
                            ?: return fail(SkillRunStatus.BLOCKED, index, "Required parameter missing")
                        if (!device.enterIntoField(step.fieldLabel, value))
                            return fail(SkillRunStatus.ACTION_FAILED, index, "Input failed")
                        if (device.observe().packageName != skill.targetApp.packageName)
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Unexpected app after input")
                    }
                    is SkillStep.Verify -> {
                        val observed = device.observe()
                        if (observed.packageName != skill.targetApp.packageName ||
                            !observed.hasLabel(step.expectedEvidence))
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Expected evidence missing")
                    }
                    is SkillStep.ConfirmAndAct -> {
                        if (device.observe().packageName != skill.targetApp.packageName)
                            return fail(SkillRunStatus.BLOCKED, index, "Target app is not foreground")
                        if (!confirmation.approvedFor(step.actionDescription))
                            return fail(SkillRunStatus.CONFIRMATION_REQUIRED, index, "Explicit confirmation required")
                        if (!device.confirmAndAct(step.actionDescription))
                            return fail(SkillRunStatus.ACTION_FAILED, index, "Confirmed action failed")
                        val after = device.observe()
                        if (after.packageName != skill.targetApp.packageName ||
                            !after.hasLabel(step.expectedEvidence))
                            return fail(SkillRunStatus.POSTCONDITION_FAILED, index, "Confirmed action not verified")
                    }
                }
                completed++
            } catch (_: Exception) {
                // No exception messages or typed parameters are exposed to logs.
                return SkillRunResult(SkillRunStatus.ACTION_FAILED, completed, index, "Device error")
            }
        }
        return SkillRunResult(SkillRunStatus.SUCCESS, completed, reason = "All declared checks passed")
    }

    private fun isSensitiveField(label: String): Boolean {
        val normalized = label.lowercase(java.util.Locale.ROOT)
        return listOf("password", "passcode", "security code", "one-time code", "otp",
            "credit card", "card number", "cvv", "cvc", "social security", "ssn", "2fa")
            .any { it in normalized }
    }
}
