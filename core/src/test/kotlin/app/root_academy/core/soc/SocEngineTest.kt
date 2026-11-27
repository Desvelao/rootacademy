package app.root_academy.core.soc

import app.root_academy.core.content.SocTemplate
import kotlin.test.*

class SocEngineTest {
    private val templates = listOf(
        SocTemplate("p1", "email", "phishing", sender = "lookalike", link = "lookalike"),
        SocTemplate("p2", "email", "phishing", level = 2, sender = "external"),
        SocTemplate("l1", "email", "legit", sender = "internal", link = "good"),
        SocTemplate("e1", "login", "escalate", clues = listOf("external-ip")),
    )

    @Test fun sameSeedSameQueue() {
        assertEquals(SocEngine.generate(5, 2, 8, templates), SocEngine.generate(5, 2, 8, templates))
        assertNotEquals(SocEngine.generate(5, 2, 8, templates), SocEngine.generate(6, 2, 8, templates))
    }

    @Test fun mixAndDifficulty() {
        val q = SocEngine.generate(1, 1, 10, templates)
        assertEquals(10, q.size)
        assertEquals(4, q.count { it.template.verdict == "phishing" })
        assertEquals(2, q.count { it.template.verdict == "escalate" })
        assertTrue(q.none { it.template.id == "p2" }, "level 2 template must not appear at difficulty 1")
    }

    @Test fun placeholdersAreFilled() {
        val item = SocEngine.generate(3, 1, 6, templates).first { it.template.id == "e1" }
        val text = SocEngine.render("{user} from {ip}", item.params)
        assertFalse(text.contains("{"))
        assertTrue(item.params.getValue("ip").startsWith("203.0.113."))
    }

    @Test fun passMark() {
        assertTrue(SocEngine.passed(7, 10))
        assertFalse(SocEngine.passed(6, 10))
    }

    @Test fun emptyInputsGiveEmptyQueue() {
        assertTrue(SocEngine.generate(1, 1, 5, emptyList()).isEmpty())
        assertTrue(SocEngine.generate(1, 1, 0, templates).isEmpty())
        assertTrue(SocEngine.generate(1, 1, -2, templates).isEmpty())
    }

    @Test fun smallCounts() {
        assertEquals(1, SocEngine.generate(1, 1, 1, templates).size)
        assertEquals(2, SocEngine.generate(1, 1, 2, templates).size)
    }
}
