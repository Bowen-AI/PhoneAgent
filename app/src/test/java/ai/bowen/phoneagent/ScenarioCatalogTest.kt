package ai.bowen.phoneagent

import org.junit.Assert.*
import org.junit.Test

class ScenarioCatalogTest {
    @Test fun allThreeFamiliesRepresented() {
        assertEquals(TaskFamily.values().toSet(), ScenarioCatalog.cases.map { it.family }.toSet())
    }

    @Test fun riskyTapActionsRejected() {
        for (label in listOf("Pay", "Send", "Purchase", "Delete", "Confirm")) {
            assertFalse(ActionPolicy.permitted(AgentAction.TapLabel(label)))
        }
        assertTrue(ActionPolicy.permitted(AgentAction.TapLabel("Search")))
        assertFalse(ActionPolicy.permitted(AgentAction.OpenApp("bad package name")))
    }
}
