package app.root_academy.packcheck

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test fun missingDirectoryIsUsageError() {
        assertEquals(2, run(arrayOf("/definitely/not/here")))
    }

    @Test fun hashNeedsAnArgument() {
        assertEquals(2, run(arrayOf("--hash")))
        assertEquals(0, run(arrayOf("--hash", "flag")))
    }

    @Test fun emptyContentFailsValidation() {
        val dir = kotlin.io.path.createTempDirectory("packcheck").toFile()
        try {
            // No packs at all: the all-flags badge threshold cannot match, so validation must fail.
            assertEquals(1, run(arrayOf(dir.path)))
        } finally { dir.deleteRecursively() }
    }

    @Test fun shippedContentIsValid() {
        var dir: File? = File("content").takeIf { it.isDirectory } ?: File("../../content").takeIf { it.isDirectory }
        assertEquals(0, run(arrayOf(dir!!.path)))
    }
}
