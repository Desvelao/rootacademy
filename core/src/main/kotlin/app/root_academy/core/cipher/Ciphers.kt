package app.root_academy.core.cipher

import app.root_academy.core.terminal.Crypto

/** Historical and toy ciphers, plus the analysis helpers the Cipher Quest tools expose. Pure and deterministic. */
object Ciphers {
    /** Upper-case letters only: the form answers are compared in. */
    fun normalize(text: String) = text.filter { it in 'a'..'z' || it in 'A'..'Z' }.uppercase()

    fun answerHash(plaintext: String) = Crypto.sha256(normalize(plaintext))

    fun caesar(text: String, shift: Int) = Crypto.caesar(text, shift)

    fun vigenere(text: String, key: String, decrypt: Boolean): String {
        val k = normalize(key)
        if (k.isEmpty()) return text
        var i = 0
        return text.map { c ->
            if (c in 'a'..'z' || c in 'A'..'Z') {
                val shift = (k[i++ % k.length] - 'A') * if (decrypt) -1 else 1
                val base = if (c.isLowerCase()) 'a' else 'A'
                base + Math.floorMod(c - base + shift, 26)
            } else c
        }.joinToString("")
    }

    // ----- toy Enigma: rotors I, II, III and reflector B, no ring settings, no plugboard -----
    private val WIRING = listOf("EKMFLGDQVZNTOWYHXUSPAIBRCJ", "AJDKSIRUXBLHWTMCQGZNPYFVOE", "BDFHJLCPRTXVZNYEIWGAKMUSQO")
    private val NOTCH = charArrayOf('Q', 'E', 'V')
    private const val REFLECTOR = "YRUHQSLDPXNGOKMIEBFZCWVJAT"

    /** Symmetric: the same call with the same [start] positions (three letters, left to right) encrypts and decrypts. Letter case is kept, like [vigenere]. */
    fun enigma(text: String, start: String): String {
        val pos = normalize(start).padEnd(3, 'A').take(3).map { it - 'A' }.toIntArray()
        val inverse = WIRING.map { w -> IntArray(26).also { inv -> w.forEachIndexed { i, c -> inv[c - 'A'] = i } } }
        fun forward(c: Int, r: Int) = Math.floorMod(WIRING[r][Math.floorMod(c + pos[r], 26)] - 'A' - pos[r], 26)
        fun backward(c: Int, r: Int) = Math.floorMod(inverse[r][Math.floorMod(c + pos[r], 26)] - pos[r], 26)
        return text.map { ch ->
            if (ch !in 'a'..'z' && ch !in 'A'..'Z') return@map ch
            if (pos[1] == NOTCH[1] - 'A') { pos[0] = (pos[0] + 1) % 26; pos[1] = (pos[1] + 1) % 26 }
            else if (pos[2] == NOTCH[2] - 'A') pos[1] = (pos[1] + 1) % 26
            pos[2] = (pos[2] + 1) % 26
            var c = ch.uppercaseChar() - 'A'
            for (r in 2 downTo 0) c = forward(c, r)
            c = REFLECTOR[c] - 'A'
            for (r in 0..2) c = backward(c, r)
            ('A' + c).let { if (ch.isLowerCase()) it.lowercaseChar() else it }
        }.joinToString("")
    }

    // ----- toy RSA (letters A=1..Z=26, small primes) -----
    /** Largest modulus the toy RSA tools accept: products stay below 10^18 (no Long overflow) and trial division stays instant. */
    const val MAX_RSA_N = 1_000_000_000L

    /** [base]^[exp] mod [mod] for 1 <= mod <= [MAX_RSA_N] and exp >= 0; a negative base is reduced into 0 until mod first. */
    fun modPow(base: Long, exp: Long, mod: Long): Long {
        require(mod in 1..MAX_RSA_N && exp >= 0) { "modulus must be in 1..$MAX_RSA_N and the exponent not negative" }
        var result = 1L % mod; var b = Math.floorMod(base, mod); var e = exp
        while (e > 0) { if (e and 1L == 1L) result = result * b % mod; b = b * b % mod; e = e shr 1 }
        return result
    }

    /** Smallest prime factor pair of [n], by trial division; null when n is prime, too small or above [MAX_RSA_N]. */
    fun factor(n: Long): Pair<Long, Long>? {
        if (n < 4 || n > MAX_RSA_N) return null
        var p = 2L
        while (p * p <= n) { if (n % p == 0L) return p to n / p; p++ }
        return null
    }

    private fun isPrime(x: Long): Boolean = x >= 2 && (x < 4 || factor(x) == null)

    fun modInverse(a: Long, m: Long): Long? {
        var r0 = m; var r1 = Math.floorMod(a, m)
        var t0 = 0L; var t1 = 1L
        while (r1 != 0L) {
            val q = r0 / r1
            val r2 = r0 - q * r1; r0 = r1; r1 = r2
            val t2 = t0 - q * t1; t0 = t1; t1 = t2
        }
        return if (r0 == 1L) Math.floorMod(t0, m) else null
    }

    fun rsaPrivateExponent(n: Long, e: Long): Long? {
        val (p, q) = factor(n) ?: return null
        if (!isPrime(q)) return null // n has three or more prime factors: p*q is not a valid toy-RSA modulus
        val phi = if (p == q) p * (p - 1) else (p - 1) * (q - 1)
        return modInverse(e, phi)
    }

    fun rsaEncrypt(text: String, n: Long, e: Long): String =
        if (n !in 27..MAX_RSA_N || e <= 0) "" else normalize(text).map { modPow((it - 'A' + 1).toLong(), e, n).toString() }.joinToString(" ")

    /** Null when the numbers cannot be decrypted (not numbers, n not factorable, or values out of range). */
    fun rsaDecrypt(numbers: String, n: Long, e: Long): String? {
        if (n !in 27..MAX_RSA_N || e <= 0) return null
        val d = rsaPrivateExponent(n, e) ?: return null
        val parts = numbers.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for (p in parts) {
            val c = p.toLongOrNull() ?: return null
            if (c !in 0 until n) return null
            val m = modPow(c, d, n)
            if (m !in 1..26) return null
            sb.append('A' + (m - 1).toInt())
        }
        return sb.toString()
    }

    // ----- analysis tools -----
    /** Letters of [text] with their counts, most frequent first. */
    fun frequency(text: String): List<Pair<Char, Int>> =
        normalize(text).groupingBy { it }.eachCount().toList().sortedWith(compareBy({ -it.second }, { it.first }))

    private fun ioc(s: String): Double {
        if (s.length < 2) return 0.0
        val counts = s.groupingBy { it }.eachCount().values
        return counts.sumOf { it * (it - 1.0) } / (s.length * (s.length - 1.0))
    }

    /** Average index of coincidence for key lengths 1..[max]; the true length scores near 0.065 (English), others near 0.04. */
    fun keyLengthScores(text: String, max: Int = 8): List<Pair<Int, Double>> {
        val t = normalize(text)
        return (1..max).map { len ->
            len to (0 until len).map { c -> ioc(t.filterIndexed { i, _ -> i % len == c }) }.average()
        }
    }

    /** Decrypts by cipher name; [key] is a shift (caesar), a word (vigenere), three letters (enigma) or "n,e" (rsa). */
    fun decrypt(cipher: String, key: String, ciphertext: String): String? = when (cipher) {
        "caesar" -> key.trim().toIntOrNull()?.let { caesar(ciphertext, -it) }
        "vigenere" -> if (normalize(key).isEmpty()) null else vigenere(ciphertext, key, decrypt = true)
        "enigma" -> enigma(ciphertext, key)
        "rsa" -> key.split(',').map { it.trim().toLongOrNull() }.let { if (it.size == 2 && it.all { v -> v != null }) rsaDecrypt(ciphertext, it[0]!!, it[1]!!) else null }
        else -> null
    }
}
