package app.root_academy.core.terminal

import app.root_academy.core.content.VDir
import app.root_academy.core.content.VFile

/** Read-only virtual file system of one host. Directories are implied by file paths and by [dirEntries]. */
class VirtualFs(private val files: Map<String, VFile>, private val dirEntries: Map<String, VDir> = emptyMap()) {
    private val dirs: Set<String> = buildSet {
        add("/")
        for (path in files.keys + dirEntries.keys) {
            var p = if (path in dirEntries) path else path.substringBeforeLast('/', "")
            while (p.isNotEmpty()) {
                add(p)
                p = p.substringBeforeLast('/', "")
            }
        }
    }

    /** The explicit entry for [path], or null for an implied directory (open to everyone). */
    fun dir(path: String): VDir? = dirEntries[path]

    fun isDir(path: String) = path in dirs
    fun file(path: String): VFile? = files[path]
    fun exists(path: String) = isDir(path) || path in files

    /** Names directly inside [dir]; directories get a trailing '/'. */
    fun list(dir: String): List<String> {
        val prefix = if (dir == "/") "/" else "$dir/"
        val names = sortedSetOf<String>()
        for (d in dirs) if (d != dir && d.startsWith(prefix) && '/' !in d.removePrefix(prefix)) names += d.removePrefix(prefix) + "/"
        for (f in files.keys) if (f.startsWith(prefix) && '/' !in f.removePrefix(prefix)) names += f.removePrefix(prefix)
        return names.toList()
    }

    /** All file paths under [dir], recursively. */
    fun filesUnder(dir: String): List<String> {
        val prefix = if (dir == "/") "/" else "$dir/"
        return files.keys.filter { it.startsWith(prefix) }.sorted()
    }

    /** All directories under [dir] (including [dir] itself), sorted. */
    fun dirsUnder(dir: String): List<String> {
        val prefix = if (dir == "/") "/" else "$dir/"
        return dirs.filter { it == dir || it.startsWith(prefix) }.sorted()
    }

    companion object {
        /** Resolves [path] against [cwd] and [home], collapsing `.` and `..`. Always returns an absolute path. */
        fun resolve(cwd: String, home: String, path: String): String {
            val raw = when {
                path == "~" -> home
                path.startsWith("~/") -> home + path.removePrefix("~")
                path.startsWith("/") -> path
                else -> "$cwd/$path"
            }
            val out = ArrayDeque<String>()
            for (part in raw.split('/')) when (part) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(part)
            }
            return "/" + out.joinToString("/")
        }
    }
}
