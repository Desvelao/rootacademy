package app.root_academy.puzzle

import app.root_academy.core.terminal.Crypto
import org.junit.Test
import org.junit.Assert.assertEquals

class PuzzleToolTest {
    @Test fun decodesAndHashes() {
        assertEquals("flag{x}", PuzzleTool.BASE64.apply(Crypto.base64Encode("flag{x}"), 0))
        assertEquals("flag{x}", PuzzleTool.HEX.apply(Crypto.hexEncode("flag{x}"), 0))
        assertEquals("flag{x}", PuzzleTool.ROT13.apply(Crypto.rot13("flag{x}"), 0))
        assertEquals("flag{x}", PuzzleTool.CAESAR.apply(Crypto.caesar("flag{x}", 3), -3))
        assertEquals("a b", PuzzleTool.URL.apply("a%20b", 0))
        assertEquals(Crypto.sha256("a"), PuzzleTool.SHA256.apply("a", 0))
    }

    @Test fun badInputShowsPlaceholder() {
        assertEquals("?", PuzzleTool.BASE64.apply("***", 0))
        assertEquals("?", PuzzleTool.HEX.apply("zz", 0))
    }

    @Test fun lookupById() {
        assertEquals(PuzzleTool.MD5, PuzzleTool.byId("md5"))
    }
}
