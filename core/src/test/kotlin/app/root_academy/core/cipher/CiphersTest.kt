package app.root_academy.core.cipher

import kotlin.test.*

class CiphersTest {
    @Test fun vigenereRoundTrip() {
        val c = Ciphers.vigenere("Attack at dawn", "LEMON", decrypt = false)
        assertEquals("Lxfopv ef rnhr", c)
        assertEquals("Attack at dawn", Ciphers.vigenere(c, "LEMON", decrypt = true))
    }

    @Test fun enigmaIsSymmetricAndNeverMapsALetterToItself() {
        val plain = "WETTERBERICHTKEINEBESONDERENEREIGNISSE".repeat(3)
        val enc = Ciphers.enigma(plain, "QEV")
        assertEquals(plain, Ciphers.enigma(enc, "QEV"))
        assertTrue(plain.indices.none { plain[it] == enc[it] })
        assertNotEquals(enc, Ciphers.enigma(plain, "QEW"))
    }

    @Test fun enigmaKnownVector() {
        // Rotors I-II-III, reflector B, positions AAA: "AAAAA" encrypts to "BDZGO".
        assertEquals("BDZGO", Ciphers.enigma("AAAAA", "AAA"))
    }

    @Test fun rsaRoundTripAndFactoring() {
        assertEquals(53L to 61L, Ciphers.factor(3233))
        assertEquals(2753L, Ciphers.rsaPrivateExponent(3233, 17))
        val enc = Ciphers.rsaEncrypt("HELLO", 3233, 17)
        assertEquals("HELLO", Ciphers.rsaDecrypt(enc, 3233, 17))
        assertNull(Ciphers.rsaDecrypt("abc", 3233, 17))
    }

    @Test fun keyLengthHelperFindsTheLength() {
        val text = "the quick brown fox jumps over the lazy dog while the sleepy cat watches from the warm window and the farmer counts his sheep before dark ".repeat(4)
        val scores = Ciphers.keyLengthScores(Ciphers.vigenere(text, "ABCD", decrypt = false), 8).toMap()
        val best = scores.maxByOrNull { it.value }!!.key
        assertTrue(best % 4 == 0, "best=$best")
    }

    @Test fun frequencyAndDecrypt() {
        assertEquals('E', Ciphers.frequency("eee tt a").first().first)
        assertEquals("HELLO", Ciphers.normalize(Ciphers.decrypt("caesar", "3", Ciphers.caesar("hello", 3))!!))
        assertNull(Ciphers.decrypt("nope", "", "x"))
    }

    @Test fun rsaRejectsHugeNegativeAndOutOfRangeInput() {
        val big = 9_000_000_000_000_000_011L
        assertNull(Ciphers.factor(big))
        assertNull(Ciphers.factor(3))
        assertNull(Ciphers.decrypt("rsa", "$big,5", "1 2 3"))
        assertNull(Ciphers.rsaDecrypt("-5", 3233, 17))
        assertNull(Ciphers.rsaDecrypt("3233", 3233, 17))
        assertNull(Ciphers.rsaDecrypt("1", 20, 3))
        assertEquals("", Ciphers.rsaEncrypt("HI", 20, 3))
        assertEquals(Ciphers.modPow(2, 10, 1000), 24L)
        assertEquals(Ciphers.modPow(-2, 3, 7), 6L)
        // Largest accepted modulus: 31607 * 31627 is just below the cap and must not overflow.
        val n = 999_999_937L
        assertTrue(Ciphers.modPow(n - 1, n - 1, n) in 0 until n)
    }

    @Test fun vigenereEdgeCases() {
        assertNull(Ciphers.decrypt("vigenere", "", "ABC"))
        assertNull(Ciphers.decrypt("vigenere", "123", "ABC"))
        assertEquals("Hello, World!", Ciphers.vigenere(Ciphers.vigenere("Hello, World!", "key", false), "key", true))
        assertEquals("abc", Ciphers.caesar("abc", 26))
        assertEquals("xyz", Ciphers.caesar("abc", -3))
        assertEquals("Hello, World", Ciphers.caesar(Ciphers.caesar("Hello, World", 5), -5))
    }

    @Test fun enigmaLongRunCrossesMiddleNotchAndStaysReversible() {
        // 1,500 letters from AAA take the middle rotor through its notch (double stepping) more than once.
        val plain = "A".repeat(1500)
        val enc = Ciphers.enigma(plain, "AAA")
        assertEquals("BDZGO", enc.take(5))
        assertEquals(plain, Ciphers.enigma(enc, "AAA"))
        assertTrue(enc.none { it == 'A' })
    }

    @Test fun rsaRejectsModuliThatAreNotAProductOfTwoPrimes() {
        assertNull(Ciphers.rsaPrivateExponent(30, 7), "30 = 2*3*5")
        assertNull(Ciphers.rsaDecrypt("1", 30, 7))
        assertNull(Ciphers.rsaDecrypt("1", 4, 3), "below the 27 minimum, like rsaEncrypt")
        assertNull(Ciphers.rsaDecrypt("1", 9, 5))
        // n = p*p uses phi = p*(p-1)
        assertEquals("", Ciphers.rsaEncrypt("A", 9, 5))
        val d = Ciphers.rsaPrivateExponent(49, 5)
        assertEquals(1L, 5L * d!! % 42)
    }

    @Test fun enigmaKeepsLetterCase() {
        val enc = Ciphers.enigma("Hello", "AAA")
        assertTrue(enc[0].isUpperCase() && enc.drop(1).all { it.isLowerCase() })
        assertEquals("Hello", Ciphers.enigma(enc, "AAA"))
    }
}
