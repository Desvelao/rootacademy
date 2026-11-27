package app.root_academy.core.terminal

/** Splits a command line into pipeline stages, each a list of words. Supports '...' and "..." quoting. */
object Tokenizer {
    /** False when [line] ends inside an open quote, which a real shell reports as an unexpected EOF. */
    fun isBalanced(line: String): Boolean {
        var quote: Char? = null
        for (c in line) when {
            quote != null -> if (c == quote) quote = null
            c == '\'' || c == '"' -> quote = c
        }
        return quote == null
    }

    fun pipeline(line: String): List<List<String>> {
        val stages = mutableListOf<MutableList<String>>(mutableListOf())
        val cur = StringBuilder()
        var quote: Char? = null
        var hasWord = false
        fun endWord() {
            if (hasWord) stages.last() += cur.toString()
            cur.clear()
            hasWord = false
        }
        for (c in line) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '\'' || c == '"' -> { quote = c; hasWord = true }
                c == '|' -> { endWord(); stages.add(mutableListOf()) }
                c.isWhitespace() -> endWord()
                else -> { cur.append(c); hasWord = true }
            }
        }
        endWord()
        return stages
    }
}
