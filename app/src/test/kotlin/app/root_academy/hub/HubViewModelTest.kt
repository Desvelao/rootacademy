package app.root_academy.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.root_academy.core.content.ContentSource
import app.root_academy.core.progress.ProgressExport
import app.root_academy.data.AppDatabase
import app.root_academy.data.Clock
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import app.root_academy.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Test provider that records the mode a caller opens a document with and serves a file from the cache dir. */
class RecordingProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        lastMode = mode
        val file = File(context!!.cacheDir, "export.json")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    companion object { @Volatile var lastMode: String? = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubViewModelTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var hub: HubViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val content = ContentRepository(object : ContentSource {
            private val root = File("../content")
            override fun list(dir: String) = File(root, dir).list()?.toList() ?: emptyList()
            override fun read(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        })
        hub = HubViewModel(context, content, ProgressRepository(db, Clock { 20_000L }), SettingsRepository(context), Clock { 20_000L })
    }

    @After fun tearDown() { runCatching { db.close() }; Dispatchers.resetMain() }

    private fun <T> await(block: (onDone: (T) -> Unit) -> Unit): T = runBlocking {
        val d = CompletableDeferred<T>()
        block { d.complete(it) }
        withTimeout(10_000) { d.await() }
    }

    @Test fun exportOpensTheDocumentTruncating() {
        Robolectric.setupContentProvider(RecordingProvider::class.java, "test.auth")
        val file = File(context.cacheDir, "export.json").apply { writeText("x".repeat(5000)) }
        val result = await<TransferResult> { done -> hub.export(Uri.parse("content://test.auth/doc"), done) }
        assertEquals(TransferResult.EXPORTED, result)
        assertEquals("wt", RecordingProvider.lastMode)
        assertEquals(true, file.length() < 5000)
    }

    @Test fun importFailureIsReportedNotThrown() {
        db.close()
        val result = await<TransferResult> { done -> hub.applyImport(ProgressExport(), done) }
        assertEquals(TransferResult.FAILED, result)
    }

    @Test fun importSuccessIsReported() {
        val result = await<TransferResult> { done -> hub.applyImport(ProgressExport(), done) }
        assertEquals(TransferResult.IMPORTED, result)
    }

    @Test fun nextMissionSkipsCampaignLockedOnes() {
        val open = hub.nextMission(emptySet())
        assertEquals(true, open != null && hub.lockedBy(open.value.id, emptySet()).isEmpty())
        // Every mission before it in order is either locked or already completed.
        val skipped = hub.catalog.missions.takeWhile { it !== open }
        assertEquals(true, skipped.all { hub.lockedBy(it.value.id, emptySet()).isNotEmpty() })
    }

    @Test fun readImportReportsBadFilesAsInvalid() {
        val file = File(context.cacheDir, "bad.json").apply { writeText("not json") }
        val bad = await<app.root_academy.core.progress.ImportResult> { done -> hub.readImport(Uri.fromFile(file), done) }
        assertEquals(app.root_academy.core.progress.ImportResult.Invalid, bad)
        val missing = await<app.root_academy.core.progress.ImportResult> { done -> hub.readImport(Uri.fromFile(File(context.cacheDir, "nope.json")), done) }
        assertEquals(app.root_academy.core.progress.ImportResult.Invalid, missing)
    }

    @Test fun readImportAcceptsAnExportAndNewerVersionsAreFlagged() {
        val ok = File(context.cacheDir, "ok.json").apply { writeText(ProgressExport().encode()) }
        assertEquals(true, await<app.root_academy.core.progress.ImportResult> { done -> hub.readImport(Uri.fromFile(ok), done) } is app.root_academy.core.progress.ImportResult.Ok)
        val newer = File(context.cacheDir, "newer.json").apply { writeText("""{"version":99}""") }
        assertEquals(app.root_academy.core.progress.ImportResult.UnsupportedVersion(99), await<app.root_academy.core.progress.ImportResult> { done -> hub.readImport(Uri.fromFile(newer), done) })
    }

    @Test fun resetProgressClearsEverything() {
        val ok = await<Boolean> { done -> hub.resetProgress(done) }
        assertEquals(true, ok)
    }
}
