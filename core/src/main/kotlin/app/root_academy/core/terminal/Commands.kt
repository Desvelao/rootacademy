package app.root_academy.core.terminal

import kotlin.math.abs
import app.root_academy.core.content.Host

/** Input from the previous pipeline stage, or null when the command is first. */
private typealias Stdin = List<String>?

/** Lines of a file's text; a final newline ends the last line instead of starting an empty one. */
internal fun fileLines(text: String): List<String> = text.lines().let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }

/** Names of all commands, in the order `help` shows them. */
val COMMAND_NAMES = listOf(
    "help", "ls", "cd", "pwd", "cat", "head", "tail", "grep", "find", "wc", "echo", "whoami", "id", "uname",
    "base64", "rot13", "hash", "strings", "history", "scan", "connect", "ssh", "exit", "submit", "clear",
)

/** Noise added to the trace meter by commands that look suspicious to a defender. */
private val NOISE = mapOf("scan" to 15, "connect" to 3, "ssh" to 2, "find" to 2)

/**
 * Runs one command (one pipeline stage). Errors are plain text lines, like a real shell.
 * Returns null for an unknown command.
 */
internal class CommandRunner(private val s: Session) {
    fun run(args: List<String>, stdin: Stdin): Output? {
        val name = args.first()
        if (name !in COMMAND_NAMES) return null
        val rest = args.drop(1)
        s.addTrace(NOISE[name] ?: 0)
        val out = when (name) {
            "help" -> help(rest)
            "ls" -> ls(rest)
            "cd" -> cd(rest)
            "pwd" -> Output.of(s.frame.cwd)
            "cat" -> cat(rest, stdin)
            "head" -> headTail(rest, stdin, head = true)
            "tail" -> headTail(rest, stdin, head = false)
            "grep" -> grep(rest, stdin)
            "find" -> find(rest)
            "wc" -> wc(rest, stdin)
            "echo" -> Output.of(rest.joinToString(" "))
            "whoami" -> Output.of(s.user)
            "id" -> Output.of("uid=${if (s.user == "root") 0 else 1000}(${s.user})")
            "uname" -> Output.of("Linux ${s.host.name} 5.15.0 x86_64")
            "base64" -> base64(rest, stdin)
            "rot13" -> Output(text(rest, stdin).map(Crypto::rot13))
            "hash" -> hash(rest, stdin)
            "strings" -> strings(rest)
            "history" -> Output(s.history.mapIndexed { i, l -> "%4d  %s".format(i + 1, l) })
            "scan" -> scan(rest)
            "connect" -> connect(rest)
            "ssh" -> ssh(rest)
            "exit" -> exit()
            "submit" -> submit(rest)
            "clear" -> Output(emptyList(), clear = true)
            else -> Output.EMPTY
        }
        s.events += Event.Ran(name)
        return out
    }

    private fun err(cmd: String, msg: String) = Output(listOf("$cmd: $msg"), isError = true)

    private fun help(args: List<String>): Output {
        val name = args.firstOrNull() ?: return Output(
            listOf("Commands (help <command> shows details):") +
                COMMAND_NAMES.map { "  " + it.padEnd(8) + COMMAND_HELP.getValue(it).summary } +
                "Pipes work: cat file | grep word. Use Tab to complete, ↑ for history.",
        )
        val h = COMMAND_HELP[name] ?: return err("help", "no help for '$name'. Type help to list the commands")
        return Output(listOf("Usage:   ${h.usage}", h.summary.replaceFirstChar { it.uppercase() } + ".", "Example: ${h.example}"))
    }

    private fun ls(args: List<String>): Output {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val long = 'l' in flags
        val all = 'a' in flags
        val target = args.firstOrNull { !it.startsWith("-") }?.let(s::resolve) ?: s.frame.cwd
        val fs = s.frame.fs
        if (!fs.exists(target)) return err("ls", "cannot access '$target': No such file or directory")
        if (!fs.isDir(target)) return Output.of(target.substringAfterLast('/'))
        if (!s.canTraverse(target) || !s.canList(target)) return err("ls", "cannot open directory '$target': Permission denied")
        val names = fs.list(target).filter { all || !it.startsWith(".") }
        if (!long) return Output(listOf(names.joinToString("  ")).filter { it.isNotEmpty() })
        return Output(
            names.map { n ->
                val path = if (target == "/") "/$n" else "$target/$n"
                val f = fs.file(path)
                val d = fs.dir(path.removeSuffix("/"))
                if (f == null) "d${perm(d?.mode ?: "755")} ${(d?.owner ?: "root").padEnd(5)} $n" else "-${perm(f.mode)} ${f.owner.padEnd(5)} $n"
            },
        )
    }

    private fun perm(mode: String): String = mode.padStart(3, '0').take(3).map { d ->
        val v = d.digitToIntOrNull() ?: 0
        (if (v and 4 != 0) "r" else "-") + (if (v and 2 != 0) "w" else "-") + (if (v and 1 != 0) "x" else "-")
    }.joinToString("")

    private fun cd(args: List<String>): Output {
        val target = s.resolve(args.firstOrNull() ?: "~")
        val fs = s.frame.fs
        return when {
            fs.isDir(target) && !s.canTraverse(target) -> err("cd", "${args.firstOrNull() ?: "~"}: Permission denied")
            fs.isDir(target) -> { s.frame.cwd = target; Output.EMPTY }
            fs.exists(target) -> err("cd", "${args.first()}: Not a directory")
            else -> err("cd", "${args.firstOrNull()}: No such file or directory")
        }
    }

    /** Reads a file as lines, recording the read event. Returns an error output instead when impossible. */
    private fun readFile(cmd: String, arg: String): Pair<List<String>?, Output?> {
        val path = s.resolve(arg)
        val fs = s.frame.fs
        if (fs.isDir(path)) return null to err(cmd, "$arg: Is a directory")
        val f = fs.file(path) ?: return null to err(cmd, "$arg: No such file or directory")
        if (!s.canRead(path)) return null to err(cmd, "$arg: Permission denied")
        s.events += Event.Read(s.host.name, path)
        return fileLines(f.text) to null
    }

    private fun cat(args: List<String>, stdin: Stdin): Output {
        if (args.isEmpty()) return Output(stdin ?: emptyList())
        val out = mutableListOf<String>()
        var failed = 0
        for (a in args) {
            val (lines, e) = readFile("cat", a)
            if (e != null) { out += e.lines; failed++ } else out += lines!!
        }
        return Output(out, isError = failed == args.size)
    }

    private fun headTail(args: List<String>, stdin: Stdin, head: Boolean): Output {
        val name = if (head) "head" else "tail"
        var n = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-n" && i + 1 < args.size -> { n = args[i + 1].toIntOrNull() ?: return err(name, "invalid number of lines: '${args[i + 1]}'"); i += 2 }
                a.startsWith("-n") && a.length > 2 -> { n = a.drop(2).toIntOrNull() ?: return err(name, "invalid number of lines: '${a.drop(2)}'"); i++ }
                a.length > 1 && a[0] == '-' && a.drop(1).all(Char::isDigit) -> { n = a.drop(1).toIntOrNull() ?: return err(name, "invalid number of lines: '${a.drop(1)}'"); i++ }
                a.length > 1 && a[0] == '-' -> return err(name, "invalid option -- '${a.drop(1)}'")
                else -> { files += a; i++ }
            }
        }
        fun pick(lines: List<String>) = if (head) (if (n >= 0) lines.take(n) else lines.dropLast(-n)) else lines.takeLast(abs(n))
        if (files.isEmpty()) return Output(pick(stdin ?: emptyList()))
        val out = mutableListOf<String>()
        for ((k, f) in files.withIndex()) {
            val (l, e) = readFile(name, f)
            if (e != null) { out += e.lines; continue }
            if (files.size > 1) { if (k > 0) out += ""; out += "==> $f <==" }
            out += pick(l!!)
        }
        return Output(out)
    }

    private fun grep(args: List<String>, stdin: Stdin): Output {
        var ignoreCase = false; var recursive = false; var numbers = false
        var invert = false; var countOnly = false; var filesOnly = false; var wordMatch = false
        val words = mutableListOf<String>()
        var pattern: String? = null
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-e" -> { pattern = args.getOrNull(i + 1) ?: return err("grep", "option requires an argument -- 'e'"); i += 2; continue }
                a.startsWith("-") && a.length > 1 -> {
                    a.drop(1).firstOrNull { it !in "irnvclwE" }?.let { return err("grep", "invalid option -- '$it'") }
                    if ('i' in a) ignoreCase = true
                    if ('r' in a) recursive = true
                    if ('n' in a) numbers = true
                    if ('v' in a) invert = true
                    if ('c' in a) countOnly = true
                    if ('l' in a) filesOnly = true
                    if ('w' in a) wordMatch = true
                    // -E is accepted: patterns are always treated as extended regular expressions.
                }
                else -> words += a
            }
            i++
        }
        if (pattern == null) pattern = words.removeFirstOrNull() ?: return err("grep", "missing pattern")
        val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val source = if (wordMatch) "\\b(?:$pattern)\\b" else pattern
        val regex = try {
            Regex(source, opts)
        } catch (_: Exception) {
            return err("grep", "Invalid regular expression")
        }
        val targets = if (words.isEmpty() && recursive) listOf(".") else words
        val out = mutableListOf<String>()
        fun scan(label: String?, lines: List<String>) {
            val hits = lines.withIndex().filter { (_, l) -> regex.containsMatchIn(l) != invert }
            when {
                filesOnly -> if (hits.isNotEmpty()) out += label ?: "(standard input)"
                countOnly -> out += (label?.let { "$it:" } ?: "") + hits.size
                else -> hits.forEach { (i, l) -> out += (label?.let { "$it:" } ?: "") + (if (numbers) "${i + 1}:" else "") + l }
            }
        }
        if (targets.isEmpty()) {
            scan(null, stdin ?: emptyList())
        } else {
            if (recursive) s.addTrace(5)
            val paths = targets.flatMap { t ->
                val p = s.resolve(t)
                if (recursive && s.frame.fs.isDir(p)) s.frame.fs.filesUnder(p) else listOf(p)
            }
            val label = targets.size > 1 || recursive
            for (p in paths) {
                if (s.frame.fs.isDir(p)) { out += "grep: $p: Is a directory"; continue }
                if (s.frame.fs.file(p) != null && !s.canRead(p) && recursive) continue
                val (lines, e) = readFile("grep", p)
                if (e != null) out += e.lines else scan(if (label) p else null, lines!!)
            }
        }
        return Output(out)
    }

    private fun find(args: List<String>): Output {
        var glob: String? = null; var ignoreCase = false; var type: String? = null
        var start: String? = null
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-name" || a == "-iname" -> {
                    glob = args.getOrNull(i + 1) ?: return err("find", "missing argument to '$a'")
                    ignoreCase = a == "-iname"; i += 2
                }
                a == "-type" -> {
                    type = args.getOrNull(i + 1)?.takeIf { it == "f" || it == "d" } ?: return err("find", "Arguments to -type should contain only 'f' or 'd'")
                    i += 2
                }
                a.startsWith("-") -> return err("find", "unknown predicate '$a'")
                start == null -> { start = s.resolve(a); i++ }
                else -> return err("find", "paths must precede expression: '$a'")
            }
        }
        val root = start ?: s.frame.cwd
        val rx = glob?.let {
            Regex(it.map { c -> when (c) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(c.toString()) } }.joinToString(""), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }
        val fs = s.frame.fs
        if (!fs.exists(root)) return err("find", "'$root': No such file or directory")
        var files = if (fs.isDir(root)) fs.filesUnder(root) else listOf(root)
        var dirs = if (fs.isDir(root)) fs.dirsUnder(root) else emptyList()
        // Like real find, a directory we may not enter is still printed but its subtree is skipped with an error.
        val denied = dirs.filter { !(s.canTraverse(it) && s.canList(it)) }
            .let { all -> all.filter { d -> all.none { o -> o != d && d.startsWith(if (o == "/") "/" else "$o/") } } }
        val errors = denied.map { "find: '$it': Permission denied" }
        fun hidden(p: String) = denied.any { d -> p != d && p.startsWith(if (d == "/") "/" else "$d/") }
        files = files.filterNot(::hidden)
        dirs = dirs.filterNot(::hidden)
        val hits = when (type) { "f" -> files; "d" -> dirs; else -> (files + dirs).sorted() }
            .filter { rx == null || rx.matches(it.substringAfterLast('/')) }
        return Output(hits + errors)
    }

    private fun wc(args: List<String>, stdin: Stdin): Output {
        val flags = args.filter { it.startsWith("-") && it.length > 1 }.joinToString("") { it.drop(1) }
        flags.firstOrNull { it !in "lwc" }?.let { return err("wc", "invalid option -- '$it'") }
        val files = args.filter { !it.startsWith("-") || it.length == 1 }
        fun counts(lines: List<String>) = Triple(lines.size, lines.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotEmpty() } }, lines.sumOf { it.toByteArray().size + 1 })
        fun format(c: Triple<Int, Int, Int>, label: String?): String {
            val parts = if (flags.isEmpty()) listOf(c.first, c.second, c.third) else
                listOfNotNull(c.first.takeIf { 'l' in flags }, c.second.takeIf { 'w' in flags }, c.third.takeIf { 'c' in flags })
            return (parts.map { it.toString() } + listOfNotNull(label)).joinToString(" ")
        }
        if (files.isEmpty()) return Output.of(format(counts(stdin ?: emptyList()), null))
        val out = mutableListOf<String>()
        var total = Triple(0, 0, 0)
        for (f in files) {
            val (lines, e) = readFile("wc", f)
            if (e != null) { out += e.lines; continue }
            val c = counts(lines!!)
            total = Triple(total.first + c.first, total.second + c.second, total.third + c.third)
            out += format(c, f)
        }
        if (files.size > 1) out += format(total, "total")
        return Output(out)
    }

    /** Text from a file argument (if it names a readable file), else the args themselves, else stdin. */
    private fun text(args: List<String>, stdin: Stdin): List<String> {
        if (args.isEmpty()) return stdin ?: emptyList()
        val joined = args.joinToString(" ")
        val path = s.resolve(joined)
        if (s.frame.fs.file(path) != null && s.canRead(path)) {
            s.events += Event.Read(s.host.name, path)
            return fileLines(s.frame.fs.file(path)!!.text)
        }
        return listOf(joined)
    }

    private fun base64(args: List<String>, stdin: Stdin): Output {
        val decode = "-d" in args
        val lines = text(args.filter { it != "-d" }, stdin)
        return Output(lines.map { l ->
            if (decode) Crypto.base64Decode(l) ?: "base64: invalid input" else Crypto.base64Encode(l)
        })
    }

    private fun hash(args: List<String>, stdin: Stdin): Output {
        val i = args.indexOf("-a")
        val algo = if (i >= 0) args.getOrNull(i + 1) ?: "sha256" else "sha256"
        val rest = args.filterIndexed { idx, _ -> i < 0 || (idx != i && idx != i + 1) }
        val name = when (algo.lowercase()) {
            "md5" -> "MD5"; "sha1" -> "SHA-1"; "sha256" -> "SHA-256"
            else -> return err("hash", "unknown algorithm '$algo' (md5, sha1, sha256)")
        }
        val joined = text(rest, stdin).joinToString("\n")
        return Output.of(Crypto.digest(name, joined))
    }

    private fun strings(args: List<String>): Output {
        val arg = args.firstOrNull() ?: return err("strings", "missing file")
        val (lines, e) = readFile("strings", arg)
        if (e != null) return e
        val out = mutableListOf<String>()
        val run = StringBuilder()
        for (c in lines!!.joinToString("\n")) {
            if (c in ' '..'~') run.append(c) else {
                if (run.length >= 4) out += run.toString()
                run.clear()
            }
        }
        if (run.length >= 4) out += run.toString()
        return Output(out)
    }

    private fun describe(h: Host) = "${h.ip.padEnd(12)} ${h.name.padEnd(12)} ${h.services.joinToString(",")}"

    private fun scan(args: List<String>): Output {
        val target = args.firstOrNull()
        fun matches(h: Host): Boolean = when {
            target == null || target == "all" -> true
            h.name == target -> true
            '/' in target -> h.ip.startsWith(target.substringBefore('/').substringBeforeLast('.') + ".") // a subnet such as 10.0.7.0/24
            target.count { it == '.' } >= 3 -> h.ip == target // one exact address
            else -> h.ip.startsWith("$target.") // a prefix such as 10.0.7
        }
        val found = s.hosts.filter { !it.hidden && it !== s.host && matches(it) }
        return Output(listOf("Scanning network...") + (if (found.isEmpty()) listOf("No hosts found.") else listOf("IP           NAME         SERVICES") + found.map(::describe)))
    }

    private fun connect(args: List<String>): Output {
        if (args.size < 2) return err("connect", "usage: connect <host> <service>")
        val h = s.findHost(args[0]) ?: return err("connect", "${args[0]}: unknown host")
        val svc = args[1]
        if (svc !in h.services) return err("connect", "${args[0]}: connection refused on '$svc'")
        return Output(h.banners[svc]?.lines() ?: listOf("Connected to $svc on ${h.name}. No banner."))
    }

    private fun ssh(args: List<String>): Output {
        val ref = args.firstOrNull() ?: return err("ssh", "usage: ssh [user@]host")
        val user = if ('@' in ref) ref.substringBefore('@') else s.user
        val h = s.findHost(ref.substringAfter('@')) ?: return err("ssh", "Could not resolve hostname ${ref.substringAfter('@')}")
        if ("ssh" !in h.services || h.logins.isEmpty()) return err("ssh", "connect to host ${h.name} port 22: Connection refused")
        s.pending = { pw ->
            s.pending = null
            if (h.logins.any { it.user == user && it.password == pw }) {
                s.login(h, user)
                Output.of("Welcome to ${h.name}.")
            } else {
                s.addTrace(8)
                Output.of("Permission denied, please try again.")
            }
        }
        return Output.of("$user@${h.name}'s password:")
    }

    private fun exit(): Output {
        if (!s.canExit) return Output.of("exit: this is your own machine. Nothing to log out of.")
        s.logout()
        return Output.of("Connection closed.")
    }

    private fun submit(args: List<String>): Output {
        val flag = args.joinToString(" ")
        if (flag.isBlank()) return err("submit", "usage: submit <flag>")
        return if (FlagChecker.matches(flag, s.mission.flagSha256)) {
            s.events += Event.FlagAccepted
            Output.of("Flag accepted!")
        } else {
            s.addTrace(10)
            Output.of("Wrong flag.")
        }
    }
}
