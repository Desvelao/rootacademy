package app.root_academy.core.content

/** Where packs are read from: the filesystem (packcheck, tests) or Android assets (app). */
interface ContentSource {
    /** Names of the entries directly inside [dir] (no path), empty if the directory does not exist. */
    fun list(dir: String): List<String>

    /** Text of [path], or null when it does not exist. */
    fun read(path: String): String?
}

/** In-memory source, handy for tests. */
class MapSource(private val files: Map<String, String>) : ContentSource {
    override fun list(dir: String): List<String> {
        val prefix = dir.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
    }

    override fun read(path: String): String? = files[path]
}
