package app.root_academy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private val repo = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    private fun current() = runBlocking { repo.settings.first() }

    @Test fun terminalFontIsClampedToItsRange() = runBlocking {
        repo.setTerminalFont(99)
        assertEquals(22, current().terminalFontSp)
        repo.setTerminalFont(1)
        assertEquals(10, current().terminalFontSp)
        repo.setTerminalFont(16)
        assertEquals(16, current().terminalFontSp)
    }

    @Test fun themeAndTextSizeRoundTrip() = runBlocking {
        repo.setTheme(ThemeMode.DARK)
        repo.setTextSize(TextSize.XLARGE)
        repo.setReduceMotion(true)
        repo.setHighContrast(true)
        val s = current()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(TextSize.XLARGE, s.textSize)
        assertTrue(s.reduceMotion && s.highContrast)
    }

    @Test fun tipsAreRememberedAndCanBeReset() = runBlocking {
        repo.resetTips()
        repo.markTipSeen("a"); repo.markTipSeen("b"); repo.markTipSeen("a")
        assertEquals(setOf("a", "b"), current().seenTips)
        repo.resetTips()
        assertTrue(current().seenTips.isEmpty())
        assertFalse("a" in current().seenTips)
    }
}
