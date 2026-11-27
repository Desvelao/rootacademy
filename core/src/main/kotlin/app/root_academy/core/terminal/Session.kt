package app.root_academy.core.terminal

import app.root_academy.core.content.Host
import app.root_academy.core.content.Mission

/** What a command printed, plus whether the screen should be cleared first. */
/** [isError] marks stderr-like text: in a pipeline it is shown but not fed to the next stage. */
data class Output(val lines: List<String>, val clear: Boolean = false, val isError: Boolean = false) {
    companion object {
        fun of(vararg lines: String) = Output(lines.toList())
        val EMPTY = Output(emptyList())
    }
}

/** Something that happened in the session; mission objectives are checked against these. */
sealed interface Event {
    data class Ran(val command: String) : Event
    data class Login(val host: String) : Event
    data class Read(val host: String, val path: String) : Event
    data object FlagAccepted : Event
}

class Frame(val host: Host, val fs: VirtualFs, var user: String, var cwd: String)

/** Mutable state of one mission attempt. All logic is deterministic: no clock, no randomness. */
class Session(val mission: Mission) {
    val hosts: List<Host> = mission.hosts
    private val stack = ArrayDeque<Frame>()
    val events = mutableListOf<Event>()
    val history = mutableListOf<String>()

    var trace = 0
        private set
    var failed = false
        private set

    /** When set, the next input line answers a prompt (a password) instead of being a command. */
    var pending: ((String) -> Output)? = null

    init {
        val start = hosts.first { it.name == mission.startHost }
        stack.addLast(frameFor(start, mission.startUser))
    }

    val frame get() = stack.last()
    val host get() = frame.host
    val user get() = frame.user
    val home get() = if (user == "root") "/root" else "/home/$user"
    val canExit get() = stack.size > 1

    private fun frameFor(h: Host, user: String): Frame {
        val home = if (user == "root") "/root" else "/home/$user"
        val fs = VirtualFs(h.files, h.dirs)
        return Frame(h, fs, user, if (fs.isDir(home)) home else "/")
    }

    fun login(h: Host, user: String) {
        stack.addLast(frameFor(h, user))
        events += Event.Login(h.name)
    }

    fun logout() {
        if (stack.size > 1) stack.removeLast()
    }

    fun addTrace(n: Int) {
        trace += n
        if (trace >= mission.traceLimit) failed = true
    }

    fun findHost(ref: String): Host? = hosts.firstOrNull { it.name == ref || it.ip == ref }

    fun prompt(): String {
        val dir = frame.cwd.let { if (it == home) "~" else it }
        return "$user@${host.name}:$dir${if (user == "root") "#" else "$"}"
    }

    fun resolve(path: String) = VirtualFs.resolve(frame.cwd, home, path)

    private fun allowed(mode: String, owner: String, group: String, need: Int): Boolean {
        if (user == "root") return true
        val userGroups = host.groups[user].orEmpty() + user
        val index = when { owner == user -> 0; group.isNotEmpty() && group in userGroups -> 1; else -> 2 }
        return (mode.getOrNull(index)?.digitToIntOrNull() ?: 0) and need != 0
    }

    /** True when every explicit directory on the way to [path] (and [path] itself, if it is one) can be entered. */
    fun canTraverse(path: String): Boolean {
        val fs = frame.fs
        var p = if (fs.isDir(path)) path else path.substringBeforeLast('/', "")
        while (p.isNotEmpty()) {
            fs.dir(p)?.let { if (!allowed(it.mode, it.owner, it.group, 1)) return false }
            p = p.substringBeforeLast('/', "")
        }
        return true
    }

    /** True when the entries of directory [path] may be listed. */
    fun canList(path: String): Boolean {
        val d = frame.fs.dir(path) ?: return true
        return allowed(d.mode, d.owner, d.group, 4)
    }

    fun canRead(path: String): Boolean {
        val f = frame.fs.file(path) ?: return false
        return canTraverse(path) && allowed(f.mode, f.owner, f.group, 4)
    }
}
