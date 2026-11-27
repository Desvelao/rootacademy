package app.root_academy.core.content

import kotlin.test.*

class PackTest {
    private val mission = """{"id":"m1","startHost":"a","hosts":[{"ip":"1.1.1.1","name":"a"}],
        "objectives":[{"id":"f","on":"flag"}],"flag_sha256":"${"a".repeat(64)}"}"""
    private val strings = """{"title":"T","brief":"B","debrief":"D","walkthrough":"W","hint.1":"1","hint.2":"2","hint.3":"3","objective.f":"Flag"}"""

    private fun source(extra: Map<String, String> = emptyMap()) = MapSource(
        mapOf(
            "missions/m1/mission.json" to mission,
            "missions/m1/strings.en.json" to strings,
            "missions/m1/strings.es.json" to strings,
            "missions/m1/solution.txt" to "submit x",
        ) + extra,
    )

    @Test fun loadsPacks() {
        val c = PackLoader(source()).loadAll()
        assertEquals("T", c.mission("m1")!!.text("es", "title"))
        assertEquals("missing", c.mission("m1")!!.text("en", "missing"))
    }

    @Test fun validatorFlagsReplayAndStringProblems() {
        val src = source(mapOf("missions/m1/strings.es.json" to """{"title":"T"}"""))
        val errors = PackValidator.validate(PackLoader(src).loadAll(), src)
        assertTrue(errors.any { it.contains("[es] missing string 'brief'") })
        assertTrue(errors.any { it.contains("solution replay") })
    }

    @Test fun validatorRejectsBadFlagHash() {
        val src = source(mapOf("missions/m1/mission.json" to mission.replace("a".repeat(64), "xyz")))
        assertTrue(PackValidator.validate(PackLoader(src).loadAll()).any { it.contains("64") })
    }

    @Test fun validatorCatchesMissionFieldsThatWouldBreakAtRuntime() {
        val bad = mission
            .replace("""{"id":"f","on":"flag"}""", """{"id":"f","on":"flag"},{"id":"r","on":"run","target":"nope"},{"id":"rd","on":"read","target":"a:rel/file"}""")
            .replace("""{"ip":"1.1.1.1","name":"a"}""", """{"ip":"1.1.1.1","name":"a","files":{"rel/file":{"text":"x"},"/a//b":{"text":"x"},"/etc/shadow":{"text":"x","mode":"9"}}}""")
            .replace("\"flag_sha256\"", "\"traceLimit\":0,\"flag_sha256\"")
        val src = source(mapOf("missions/m1/mission.json" to bad))
        val errors = PackValidator.validate(PackLoader(src).loadAll())
        assertTrue(errors.any { it.contains("unknown command 'nope'") }, errors.toString())
        assertTrue(errors.any { it.contains("'rel/file' must be absolute") }, errors.toString())
        assertTrue(errors.any { it.contains("'/a//b' must be absolute") }, errors.toString())
        assertTrue(errors.any { it.contains("expected three octal digits") }, errors.toString())
        assertTrue(errors.any { it.contains("traceLimit must be positive") }, errors.toString())
    }

    @Test fun validatorChecksChipCommandsHostsAndPuzzleTools() {
        val bad = mission
            .replace("\"flag_sha256\"", "\"commands\":[\"grpe\",\"cat\"],\"flag_sha256\"")
            .replace("""{"ip":"1.1.1.1","name":"a"}""", """{"ip":"1.1.1.1","name":"a","logins":[{"user":"u","password":"p"}],"groups":{"ghost":["g"]},"files":{"/x":{"text":"t"}},"dirs":{"/x":{}}},{"ip":"1.1.1.1","name":"b"}""")
        val puzzle = """{"id":"p1","category":"crypto","tools":["hexx","hex"],"flag_sha256":"${"a".repeat(64)}"}"""
        val src = source(mapOf("missions/m1/mission.json" to bad, "puzzles/p1/puzzle.json" to puzzle))
        val errors = PackValidator.validate(PackLoader(src).loadAll())
        assertTrue(errors.any { it.contains("unknown chip command 'grpe'") }, errors.toString())
        assertTrue(errors.none { it.contains("'cat'") })
        assertTrue(errors.any { it.contains("duplicate host ip") }, errors.toString())
        assertTrue(errors.any { it.contains("logins but no 'ssh'") }, errors.toString())
        assertTrue(errors.any { it.contains("groups name unknown user 'ghost'") }, errors.toString())
        assertTrue(errors.any { it.contains("both a file and a directory") }, errors.toString())
        assertTrue(errors.any { it.contains("p1: unknown tool 'hexx'") }, errors.toString())
        assertTrue(errors.none { it.contains("unknown tool 'hex'") })
    }

    @Test fun loaderReportsPackDirectoriesWithoutAPackFile() {
        val src = source(mapOf("missions/typo/mision.json" to mission, "missions/typo/strings.en.json" to strings))
        val c = PackLoader(src).loadAll()
        assertEquals(1, c.missions.size)
        assertTrue(PackValidator.validate(c).any { it.contains("missions/typo") && it.contains("mission.json") })
    }

    @Test fun malformedPacksFailToLoad() {
        val src = source(mapOf("missions/m1/mission.json" to "{ not json"))
        assertFailsWith<Exception> { PackLoader(src).loadAll() }
        val unknownKey = source(mapOf("missions/m1/mission.json" to mission.replace("\"id\":\"m1\",", "\"id\":\"m1\",\"bogus\":1,")))
        assertFailsWith<Exception> { PackLoader(unknownKey).loadAll() }
    }

    @Test fun unlockedLessonsAreRequired() {
        val lesson = mapOf(
            "lessons/l1/lesson.json" to """{"id":"l1","skill":"linux"}""",
            "lessons/l1/strings.en.json" to """{"title":"T","body":"B"}""",
            "lessons/l1/strings.es.json" to """{"title":"T","body":"B"}""",
        )
        val errors = PackValidator.validate(PackLoader(source(lesson)).loadAll())
        assertTrue(errors.any { it.contains("l1: lesson is never unlocked") }, errors.toString())
    }

    @Test fun validatorChecksTheCipherTool() {
        val hash = app.root_academy.core.cipher.Ciphers.answerHash("veni")
        val text = """{"title":"T","prompt":"P","walkthrough":"W","hint.1":"1","hint.2":"2","hint.3":"3"}"""
        fun withTool(tool: String) = source(mapOf(
            "cipher/c1/cipher.json" to """{"id":"c1","order":1,"chapter":"rome","cipher":"caesar","ciphertext":"yhql","tool":"$tool","flag_sha256":"$hash"}""",
            "cipher/c1/strings.en.json" to text, "cipher/c1/strings.es.json" to text, "cipher/c1/solution.txt" to "3",
        ))
        val bad = withTool("bogus")
        assertTrue(PackValidator.validate(PackLoader(bad).loadAll(), bad).any { it.contains("c1: unknown tool 'bogus'") })
        val good = withTool("freq")
        assertTrue(PackValidator.validate(PackLoader(good).loadAll(), good).none { it.contains("unknown tool") })
    }
}
