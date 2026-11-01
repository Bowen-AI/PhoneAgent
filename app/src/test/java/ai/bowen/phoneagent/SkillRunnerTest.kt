package ai.bowen.phoneagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRunnerTest {
    private val target = AppCompatibility(
        packageName = "com.example.mockmedia",
        testedVersionCode = 42L,
        testedUiFingerprint = "home-v1"
    )

    private fun skill(
        steps: List<SkillStep> = listOf(
            SkillStep.OpenApp(target.packageName),
            SkillStep.FindAndTap("Search", "Results")
        ),
        verified: Boolean = false
    ) = SkillDefinition(
        id = "my-skill",
        ownerId = "customer-1",
        title = "Find media",
        targetApp = target,
        steps = steps,
        validation = if (verified) SkillValidation.VERIFIED else SkillValidation.DRAFT,
        verifiedVersionCode = if (verified) 42L else null
    )

    private class FakeDevice : SkillDevice {
        var installed: Long? = 42L
        var fingerprint = "home-v1"
        var openWorks = true
        var onTapShowResult = true
        var openCalls = 0
        var tapCalls = 0
        var confirmedCalls = 0
        var enteredValue: String? = null
        var screen = SkillScreen(null, null, emptyList())

        override fun installedVersion(packageName: String): Long? = installed

        override fun observe(): SkillScreen = screen

        override fun openApp(packageName: String): Boolean {
            openCalls++
            if (!openWorks) return false
            screen = SkillScreen(packageName, fingerprint, listOf("SEARCH", "Title", "Place order"))
            return true
        }

        override fun tapVisibleLabel(label: String): Boolean {
            tapCalls++
            if (onTapShowResult) {
                screen = screen.copy(labels = listOf("RESULTS"))
            }
            return true
        }

        override fun enterIntoField(fieldLabel: String, value: String): Boolean {
            enteredValue = value
            return true
        }

        override fun confirmAndAct(actionDescription: String): Boolean {
            confirmedCalls++
            screen = screen.copy(labels = listOf("RECEIPT"))
            return true
        }
    }

    @Test fun trialRunsDraftButDoesNotClaimVerification() {
        val result = SkillRunner().run(skill(), "customer-1", SkillRunMode.TRIAL, FakeDevice())
        assertEquals(SkillRunStatus.SUCCESS, result.status)
        assertEquals(2, result.completedSteps)
        assertEquals(SkillValidation.DRAFT, skill().validation)
    }

    @Test fun savedReplayRequiresVerifiedSkill() {
        val device = FakeDevice()
        val result = SkillRunner().run(skill(), "customer-1", SkillRunMode.VERIFIED_REPLAY, device)
        assertEquals(SkillRunStatus.REVALIDATION_REQUIRED, result.status)
        assertEquals(0, device.openCalls)
    }

    @Test fun ownerIsComparedExactlyAndBlocksBeforeDeviceUse() {
        val device = FakeDevice()
        val result = SkillRunner().run(skill(), "Customer-1", SkillRunMode.TRIAL, device)
        assertEquals(SkillRunStatus.BLOCKED, result.status)
        assertEquals(0, device.openCalls)
    }

    @Test fun packageIdentifierCannotChangeEvenInTrial() {
        val device = FakeDevice()
        val task = skill(listOf(SkillStep.OpenApp("com.example.other")))
        assertEquals(SkillRunStatus.BLOCKED,
            SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, device).status)
        assertEquals(0, device.openCalls)
    }

    @Test fun appVersionDriftRequiresRevalidation() {
        val device = FakeDevice().apply { installed = 43L }
        val result = SkillRunner().run(skill(verified = true), "customer-1",
            SkillRunMode.VERIFIED_REPLAY, device)
        assertEquals(SkillRunStatus.REVALIDATION_REQUIRED, result.status)
        assertEquals(0, device.openCalls)
    }

    @Test fun uiFingerprintDriftRequiresRevalidation() {
        val device = FakeDevice().apply { fingerprint = "home-v2" }
        val result = SkillRunner().run(skill(), "customer-1", SkillRunMode.TRIAL, device)
        assertEquals(SkillRunStatus.REVALIDATION_REQUIRED, result.status)
        assertEquals(0, device.tapCalls)
    }

    @Test fun labelsIgnoreCaseButRequireActualPostcondition() {
        val device = FakeDevice()
        val task = skill(listOf(
            SkillStep.OpenApp(target.packageName),
            SkillStep.FindAndTap("search", "results"),
            SkillStep.Verify("Results")
        ), verified = true)
        val result = SkillRunner().run(task, "customer-1", SkillRunMode.VERIFIED_REPLAY, device)
        assertEquals(SkillRunStatus.SUCCESS, result.status)
        assertEquals(1, device.tapCalls)
    }

    @Test fun successfulTapDispatchWithoutEvidenceFails() {
        val device = FakeDevice().apply { onTapShowResult = false }
        val result = SkillRunner().run(skill(), "customer-1", SkillRunMode.TRIAL, device)
        assertEquals(SkillRunStatus.POSTCONDITION_FAILED, result.status)
        assertEquals(1, result.failedStepIndex)
    }

    @Test fun parameterNamesAreCaseSensitiveAndValuesAreUnchanged() {
        val task = skill(listOf(
            SkillStep.OpenApp(target.packageName),
            SkillStep.EnterParameter("Title", "MovieName")
        ))
        val badDevice = FakeDevice()
        val bad = SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, badDevice,
            parameters = mapOf("moviename" to "The CAsE Test"))
        assertEquals(SkillRunStatus.BLOCKED, bad.status)
        assertEquals(null, badDevice.enteredValue)

        val goodDevice = FakeDevice()
        val good = SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, goodDevice,
            parameters = mapOf("MovieName" to "The CAsE Test"))
        assertEquals(SkillRunStatus.SUCCESS, good.status)
        assertEquals("The CAsE Test", goodDevice.enteredValue)
    }

    @Test fun sensitiveFieldIsBlocked() {
        val task = skill(listOf(
            SkillStep.OpenApp(target.packageName),
            SkillStep.EnterParameter("Password", "secret")
        ))
        val device = FakeDevice().apply { }
        // Visible password fields must still be rejected; do not auto-type into them.
        device.screen = SkillScreen(target.packageName, "home-v1", listOf("Password"))
        // OpenApp resets the simulated screen to mimic the launch: add field through a special device below.
        val protectedDevice = object : SkillDevice by device {
            override fun observe(): SkillScreen =
                device.observe().copy(labels = device.observe().labels + "PASSWORD")
        }
        val result = SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, protectedDevice,
            parameters = mapOf("secret" to "not-logged"))
        assertEquals(SkillRunStatus.BLOCKED, result.status)
        assertEquals(null, device.enteredValue)
    }

    @Test fun purchaseCannotRunWithoutFreshConfirmation() {
        val task = skill(listOf(
            SkillStep.OpenApp(target.packageName),
            SkillStep.ConfirmAndAct("Place order", "Receipt")
        ))
        val device = FakeDevice()
        val result = SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, device)
        assertEquals(SkillRunStatus.CONFIRMATION_REQUIRED, result.status)
        assertEquals(0, device.confirmedCalls)
        val allowed = SkillRunner().run(task, "customer-1", SkillRunMode.TRIAL, device,
            confirmation = SkillConfirmation { action -> action == "Place order" })
        assertEquals(SkillRunStatus.SUCCESS, allowed.status)
        assertEquals(1, device.confirmedCalls)
    }

    @Test fun missingAppFailsClosed() {
        val device = FakeDevice().apply { installed = null }
        assertEquals(SkillRunStatus.BLOCKED,
            SkillRunner().run(skill(), "customer-1", SkillRunMode.TRIAL, device).status)
        assertFalse(device.openCalls > 0)
    }
}
