package app.root_academy.core.terminal

import java.security.MessageDigest
import java.util.Base64

/** Small encoding and hashing helpers shared by the terminal commands, puzzle tools and flag checks. */
object Crypto {
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun digest(algo: String, text: String): String =
        hex(MessageDigest.getInstance(algo).digest(text.toByteArray(Charsets.UTF_8)))

    fun sha256(text: String) = digest("SHA-256", text)

    fun base64Encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    /** Returns null when [text] is not valid Base64. */
    fun base64Decode(text: String): String? = try {
        String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    fun hexDecode(text: String): String? {
        val clean = text.filterNot { it.isWhitespace() }
        if (clean.length % 2 != 0 || !clean.all { it in "0123456789abcdefABCDEF" }) return null
        return String(ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, Charsets.UTF_8)
    }

    fun hexEncode(text: String) = hex(text.toByteArray(Charsets.UTF_8))

    fun caesar(text: String, shift: Int): String = text.map { c ->
        when (c) {
            in 'a'..'z' -> 'a' + Math.floorMod(c - 'a' + shift, 26)
            in 'A'..'Z' -> 'A' + Math.floorMod(c - 'A' + shift, 26)
            else -> c
        }
    }.joinToString("")

    fun rot13(text: String) = caesar(text, 13)

    fun urlDecode(text: String): String = java.net.URLDecoder.decode(text, Charsets.UTF_8)
}

/** Checks a submitted flag against its stored SHA-256, so plaintext flags never ship in the app. */
object FlagChecker {
    fun matches(submitted: String, sha256Hex: String) = Crypto.sha256(submitted.trim()).equals(sha256Hex, ignoreCase = true)
}
