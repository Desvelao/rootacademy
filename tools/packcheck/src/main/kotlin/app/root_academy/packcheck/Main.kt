package app.root_academy.packcheck

import app.root_academy.core.content.ContentSource
import app.root_academy.core.content.PackLoader
import app.root_academy.core.content.PackValidator
import app.root_academy.core.progress.Badges
import app.root_academy.core.terminal.Crypto
import java.io.File
import kotlin.system.exitProcess

private class FileSource(private val root: File) : ContentSource {
    override fun list(dir: String) = File(root, dir).list()?.toList() ?: emptyList()
    override fun read(path: String) = File(root, path).takeIf { it.isFile }?.readText()
}

/**
 * Validates every pack under the content directory and replays each mission's reference solution.
 * Usage: packcheck [--verbose] <content-dir> | packcheck --hash <flag>
 */
fun main(args: Array<String>) {
    val code = run(args)
    if (code != 0) exitProcess(code)
}

/** Runs packcheck and returns the process exit code (0 ok, 1 problems found, 2 bad usage). */
fun run(args: Array<String>): Int {
    if (args.firstOrNull() == "--hash") {
        if (args.size < 2) { System.err.println("packcheck: --hash needs a flag to hash"); return 2 }
        println(Crypto.sha256(args.drop(1).joinToString(" ")))
        return 0
    }
    val verbose = "--verbose" in args
    val dir = File(args.firstOrNull { !it.startsWith("--") } ?: "content")
    if (!dir.isDirectory) { System.err.println("packcheck: '$dir' is not a directory"); return 2 }
    val source = FileSource(dir)
    val catalog = try { PackLoader(source).loadAll() } catch (e: Exception) {
        System.err.println("packcheck: cannot load packs: ${e::class.simpleName}: ${e.message ?: "(no message)"}")
        generateSequence(e.cause) { it.cause }.forEach { System.err.println("  caused by ${it::class.simpleName}: ${it.message}") }
        if (verbose) e.printStackTrace()
        return 1
    }
    val errors = PackValidator.validate(catalog, source).toMutableList()
    // The "all-flags" badge is for completing every mission and puzzle; keep its threshold in step with the content.
    val allFlags = Badges.all.first { it.id == "all-flags" }.threshold
    val total = catalog.missions.size + catalog.puzzles.size
    if (allFlags != total) errors += "badges: 'all-flags' threshold is $allFlags but there are $total missions and puzzles"
    println("packcheck: ${catalog.missions.size} missions, ${catalog.puzzles.size} puzzles, ${catalog.lessons.size} lessons, " +
        "${catalog.soc?.value?.days?.size ?: 0} SOC shifts, ${catalog.ciphers.size} cipher levels, ${catalog.maps.size} defense maps, " +
        "${catalog.games.size} games, ${catalog.campaigns.size} campaigns")
    if (errors.isEmpty()) println("packcheck: OK") else {
        errors.forEach { System.err.println("  ERROR $it") }
        System.err.println("packcheck: ${errors.size} problem(s)")
        return 1
    }
    return 0
}
