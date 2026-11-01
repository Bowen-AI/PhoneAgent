package ai.bowen.phoneagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillContractTest {
    private val target = AppCompatibility(
        packageName = "com.example.mockmedia",
        testedVersionCode = 42L,
        testedUiFingerprint = "screen-v1"
    )
    private fun skill() = SkillDefinition(
        id = "test-skill-1",
        ownerId = "local-user",
        title = "Find a movie",
        targetApp = target,
        steps = listOf(SkillStep.FindAndTap("Search", "Search results"))
    )

    @Test fun taughtSkillIsPrivateAndUnverifiedByDefault() {
        assertEquals(SkillVisibility.PRIVATE, skill().visibility)
        assertEquals(SkillValidation.DRAFT, skill().validation)
        assertTrue(skill().needsVerification("com.example.mockmedia", 42L, "screen-v1"))
    }

    @Test fun wrongAppCannotInheritSkill() {
        assertEquals(AppMatch.INCOMPATIBLE, target.evaluate("com.example.other", 42L, "screen-v1"))
    }

    @Test fun appUpdateRequiresRevalidation() {
        assertEquals(AppMatch.NEEDS_REVALIDATION, target.evaluate("com.example.mockmedia", 43L, "screen-v1"))
    }

    @Test fun screenChangeRequiresRevalidation() {
        assertEquals(AppMatch.NEEDS_REVALIDATION, target.evaluate("com.example.mockmedia", 42L, "screen-v2"))
    }

    @Test fun onlyVerifiedSameEnvironmentCanAvoidReverification() {
        val verified = skill().copy(validation = SkillValidation.VERIFIED, verifiedVersionCode = 42L)
        assertEquals(false, verified.needsVerification("com.example.mockmedia", 42L, "screen-v1"))
        assertTrue(verified.needsVerification("com.example.mockmedia", 43L, "screen-v1"))
    }

    @Test fun sensitiveActionHasExplicitConfirmationStep() {
        val s: SkillStep = SkillStep.ConfirmAndAct("Place order", "Order receipt")
        assertTrue(s is SkillStep.ConfirmAndAct)
    }
}
