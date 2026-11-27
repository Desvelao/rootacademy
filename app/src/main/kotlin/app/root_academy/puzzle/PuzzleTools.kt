package app.root_academy.puzzle

import androidx.annotation.StringRes
import app.root_academy.R
import app.root_academy.core.terminal.Crypto

/** Helper tools the puzzle screen can offer. Pure text in, text out. */
enum class PuzzleTool(val id: String, @StringRes val label: Int, val usesShift: Boolean = false) {
    BASE64("base64", R.string.tool_base64),
    ROT13("rot13", R.string.tool_rot13),
    HEX("hex", R.string.tool_hex),
    URL("url", R.string.tool_url),
    MD5("md5", R.string.tool_md5),
    SHA256("sha256", R.string.tool_sha256),
    CAESAR("caesar", R.string.tool_caesar, usesShift = true);

    fun apply(input: String, shift: Int): String = when (this) {
        BASE64 -> Crypto.base64Decode(input) ?: "?"
        ROT13 -> Crypto.rot13(input)
        HEX -> Crypto.hexDecode(input) ?: "?"
        URL -> try { Crypto.urlDecode(input) } catch (_: IllegalArgumentException) { "?" }
        MD5 -> Crypto.digest("MD5", input)
        SHA256 -> Crypto.sha256(input)
        CAESAR -> Crypto.caesar(input, shift)
    }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}
