package app.root_academy.skills

import app.root_academy.core.content.ContentSource
import app.root_academy.data.ContentRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkillsGroupsTest {
    @Test fun everyCatalogActivityKindHasAnXpGroup() {
        val content = ContentRepository(object : ContentSource {
            private val root = File("../content")
            override fun list(dir: String) = File(root, dir).list()?.toList() ?: emptyList()
            override fun read(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        })
        val missing = content.catalog.activities.map { it.kind }.toSet() - XP_GROUPS.keys
        assertTrue("kinds without an XP group: $missing", missing.isEmpty())
    }
}
