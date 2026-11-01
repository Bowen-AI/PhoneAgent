package ai.bowen.phoneagent

/**
 * Portable data contract for future tutorial-derived skills.
 *
 * Defining this contract does not implement recording, transcription, persistence, replay,
 * encryption or an autonomous planner. No user recording may be published by default.
 */
enum class SkillVisibility { PRIVATE, SHARED_OPT_IN }
enum class SkillValidation { DRAFT, VERIFIED, NEEDS_REVALIDATION }
enum class AppMatch { COMPATIBLE, NEEDS_REVALIDATION, INCOMPATIBLE }

data class AppCompatibility(
    val packageName: String,
    val testedVersionCode: Long,
    val minimumSupportedVersionCode: Long = testedVersionCode,
    val maximumSupportedVersionCode: Long = testedVersionCode,
    val testedUiFingerprint: String? = null
) {
    init {
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        require(testedVersionCode >= 0L)
        require(minimumSupportedVersionCode <= testedVersionCode)
        require(maximumSupportedVersionCode >= testedVersionCode)
    }

    /**
     * A version range or matching fingerprint is not proof of task success.
     * UI changes must be checked before executing a saved procedure.
     */
    fun evaluate(installedPackage: String, installedVersionCode: Long, observedFingerprint: String?): AppMatch {
        if (packageName != installedPackage) return AppMatch.INCOMPATIBLE
        if (installedVersionCode !in minimumSupportedVersionCode..maximumSupportedVersionCode)
            return AppMatch.NEEDS_REVALIDATION
        if (testedUiFingerprint != null && testedUiFingerprint != observedFingerprint)
            return AppMatch.NEEDS_REVALIDATION
        return AppMatch.COMPATIBLE
    }
}

sealed interface SkillStep {
    data class OpenApp(val packageName: String) : SkillStep
    data class FindAndTap(val semanticLabel: String, val expectedScreenEvidence: String) : SkillStep
    data class EnterParameter(val fieldLabel: String, val parameterName: String) : SkillStep
    data class Verify(val expectedEvidence: String) : SkillStep
    /** A consequential action requires a fresh human confirmation at execution time. */
    data class ConfirmAndAct(val actionDescription: String, val expectedEvidence: String) : SkillStep
}

data class SkillDefinition(
    val id: String,
    val ownerId: String,
    val title: String,
    val targetApp: AppCompatibility,
    val steps: List<SkillStep>,
    val schemaVersion: Int = 1,
    val revision: Int = 1,
    val visibility: SkillVisibility = SkillVisibility.PRIVATE,
    val validation: SkillValidation = SkillValidation.DRAFT,
    val verifiedVersionCode: Long? = null
) {
    init {
        require(id.isNotBlank() && ownerId.isNotBlank() && title.isNotBlank())
        require(schemaVersion >= 1 && revision >= 1)
    }

    /** An extracted candidate is never automatically usable as a verified skill. */
    fun needsVerification(installedPackage: String, installedVersionCode: Long, observedFingerprint: String?): Boolean =
        validation != SkillValidation.VERIFIED ||
            verifiedVersionCode != installedVersionCode ||
            targetApp.evaluate(installedPackage, installedVersionCode, observedFingerprint) != AppMatch.COMPATIBLE
}
