package app.root_academy.core.terminal

import app.root_academy.core.content.Mission

/** Result of one input line. */
data class StepResult(val output: Output, val prompt: String, val completed: Boolean, val failed: Boolean)

/** Drives a [Session]: parses lines, runs pipelines, tracks objectives. No Android, clock or randomness. */
class MissionRunner(val mission: Mission) {
    val session = Session(mission)
    private val runner = CommandRunner(session)

    private val done = mutableSetOf<String>()
    private var scanned = 0

    /** Objectives met so far. Events are only ever appended, so each is examined once. */
    val doneObjectives: Set<String>
        get() {
            val events = session.events
            while (scanned < events.size) {
                val e = events[scanned++]
                for (o in mission.objectives) if (o.id !in done && matches(o, e)) done += o.id
            }
            return done
        }

    val completed get() = doneObjectives.size == mission.objectives.map { it.id }.toSet().size
    val failed get() = session.failed

    private fun matches(o: app.root_academy.core.content.Objective, e: Event) = when (o.on) {
        "run" -> e is Event.Ran && e.command == o.target
        "connect" -> e is Event.Login && e.host == o.target
        "read" -> e is Event.Read && "${e.host}:${e.path}" == o.target
        "flag" -> e is Event.FlagAccepted
        else -> false
    }

    fun submitLine(line: String): StepResult {
        if (session.failed) return result(Output.of("TRACE COMPLETE: connection terminated. Restart the mission."))
        val pending = session.pending
        if (pending != null) return result(pending(line))
        if (line.isBlank()) return result(Output.EMPTY)
        session.history += line
        if (!Tokenizer.isBalanced(line)) return result(Output.of("syntax error: unexpected end of input while looking for a matching quote"))
        val stages = Tokenizer.pipeline(line)
        var input: List<String>? = null
        var out = Output.EMPTY
        val errors = mutableListOf<String>()
        for (st in stages) {
            if (st.isEmpty()) return result(Output.of("syntax error near '|'"))
            out = runner.run(st, input) ?: return result(Output.of("${st.first()}: command not found. Try 'help'."))
            // Error text is shown, but the next stage gets an empty stdin, as with stderr in a real shell.
            if (out.isError && st !== stages.last()) { errors += out.lines; input = emptyList() } else input = out.lines
            if (session.failed) break // the connection is gone: later stages must not run
        }
        return result(if (errors.isEmpty()) out else out.copy(lines = errors + out.lines))
    }

    private fun result(out: Output): StepResult {
        val extra = if (session.failed && out.lines.none { it.startsWith("TRACE") })
            out.lines + "TRACE COMPLETE: connection terminated." else out.lines
        return StepResult(out.copy(lines = extra), session.prompt(), completed, failed)
    }
}

/** Replays a reference solution (one command per line, `#` comments) and reports what is missing. */
object SolutionReplayer {
    data class Report(val ok: Boolean, val problems: List<String>)

    fun replay(mission: Mission, solution: String): Report {
        val r = MissionRunner(mission)
        for (line in solution.lines()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            r.submitLine(line)
            if (r.failed) return Report(false, listOf("trace limit reached at '$line'"))
        }
        val missing = mission.objectives.map { it.id }.filter { it !in r.doneObjectives }
        return Report(missing.isEmpty(), missing.map { "objective not completed: $it" })
    }
}

/** Tab completion for command names and paths. */
object Completer {
    fun complete(session: Session, line: String): List<String> {
        val stage = line.substringAfterLast('|').trimStart()
        val words = stage.split(' ')
        val last = words.last()
        if (words.size == 1) return COMMAND_NAMES.filter { it.startsWith(last) }
        val dirPart = last.substringBeforeLast('/', "").let { if (last.contains('/')) "$it/" else "" }
        val dir = if (dirPart.isEmpty()) session.frame.cwd else session.resolve(dirPart)
        val name = last.substringAfterLast('/')
        val fs = session.frame.fs
        if (!fs.isDir(dir) || !session.canTraverse(dir) || !session.canList(dir)) return emptyList()
        return fs.list(dir).filter { it.startsWith(name) && (name.startsWith(".") || !it.startsWith(".")) }.map { dirPart + it }
    }

    fun commonPrefix(options: List<String>): String =
        options.reduceOrNull { a, b -> a.commonPrefixWith(b) } ?: ""
}
