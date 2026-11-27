package app.root_academy.core.soc

import app.root_academy.core.content.SocTemplate
import java.util.Random

/** One queue entry: a template plus the seeded values that fill its placeholders. */
data class SocItem(val template: SocTemplate, val params: Map<String, String>)

/**
 * Builds a SOC queue from templates. The same seed always gives the same queue, so daily shifts are identical
 * for everyone. Placeholders in pack text: {name} {sender} {domain} {link} {ip} {user} {host} {time} {n}.
 */
object SocEngine {
    const val HOME_DOMAIN = "halcyon.example"
    private val people = listOf("Ana", "Luis", "Carlos", "Marta", "Jorge", "Elena")
    private val lookalikes = listOf("halcyon-c0rp.example", "halcyon-support.example", "hallcyon.example", "halcyon.example.co")
    private val externals = listOf("mail-service.example", "fastbox.example", "partner-net.example")
    private val hosts = listOf("web", "db", "laptop-07", "files")
    val VERDICTS = listOf("legit", "phishing", "escalate")

    /** Empty when there are no [templates] or [count] is not positive. */
    fun generate(seed: Long, difficulty: Int, count: Int, templates: List<SocTemplate>): List<SocItem> {
        if (count <= 0 || templates.isEmpty()) return emptyList()
        val rnd = Random(seed)
        val eligible = templates.filter { it.level <= difficulty }.ifEmpty { templates }
        val phish = Math.round(count * 0.4f)
        val esc = Math.round(count * 0.2f)
        val wanted = List(phish) { "phishing" } + List(esc) { "escalate" } + List(count - phish - esc) { "legit" }
        val used = mutableMapOf<String, Int>()
        val picked = wanted.map { v ->
            val pool = eligible.filter { it.verdict == v }.ifEmpty { eligible }.sortedBy { it.id }
            val shuffled = pool.shuffled(kotlin.random.Random(rnd.nextLong()))
            // Prefer a template that has not been used yet in this queue.
            shuffled.minByOrNull { used[it.id] ?: 0 }!!.also { used[it.id] = (used[it.id] ?: 0) + 1 }
        }.shuffled(kotlin.random.Random(rnd.nextLong()))
        return picked.map { SocItem(it, params(it, rnd)) }
    }

    private fun params(t: SocTemplate, rnd: Random): Map<String, String> {
        val name = people[rnd.nextInt(people.size)]
        val domain = when (t.sender) {
            "internal" -> HOME_DOMAIN
            "lookalike" -> lookalikes[rnd.nextInt(lookalikes.size)]
            "external" -> externals[rnd.nextInt(externals.size)]
            else -> HOME_DOMAIN
        }
        val n = 10 + rnd.nextInt(80)
        val link = when (t.link) {
            "good" -> "https://intranet.$HOME_DOMAIN/p/$n"
            "lookalike" -> "https://${lookalikes[rnd.nextInt(lookalikes.size)]}/login"
            "mismatch" -> "https://$HOME_DOMAIN.link-track.example/r/$n"
            else -> ""
        }
        val off = "off-hours" in t.clues
        val hour = if (off) 2 + rnd.nextInt(3) else 9 + rnd.nextInt(8)
        return mapOf(
            "name" to name,
            "sender" to "${name.lowercase()}@$domain",
            "domain" to domain,
            "link" to link,
            "ip" to if (t.clues.any { it in setOf("external-ip", "many-failures", "impossible-travel", "data-exfil") }) "203.0.113.$n" else "10.0.7.$n",
            "user" to name.lowercase(),
            "host" to hosts[rnd.nextInt(hosts.size)],
            "time" to "%02d:%02d".format(hour, rnd.nextInt(60)),
            "n" to n.toString(),
        )
    }

    fun render(text: String, params: Map<String, String>): String =
        params.entries.fold(text) { acc, (k, v) -> acc.replace("{$k}", v) }

    /** Share of correct verdicts a shift needs, in percent. */
    const val PASS_PERCENT = 70

    fun passed(correct: Int, total: Int) = total > 0 && correct * 100 >= total * PASS_PERCENT

    /** The smallest number of correct verdicts that passes a shift of [total] items. */
    fun needed(total: Int) = (total * PASS_PERCENT + 99) / 100
}
