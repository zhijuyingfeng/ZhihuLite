package org.nigao.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nigao.zhihuLite.business_logic.feed.data.FeedQuery
import org.nigao.zhihuLite.business_logic.feed.data.RoomFeedStorage
import org.nigao.zhihuLite.business_logic.feed.data.ZhihuDatabase
import java.io.File
import org.robolectric.RobolectricTestRunner

/**
 * A whole-table wipe must give the disk space back.
 *
 * Asserted on real files, because the interesting part is platform-specific: Android's SQLite runs
 * with `auto_vacuum = FULL`, so the database file shrinks on delete by itself — it is the
 * **write-ahead log** that keeps its high-water mark until a checkpoint runs. Measuring the file
 * sizes is therefore the only assertion that can notice a missing checkpoint.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseCompactionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ZhihuDatabase

    private val query = FeedQuery("recommend", "https://www.zhihu.com/api/v3/feed/topstory/recommend")

    @Before
    fun setUp() {
        context.deleteDatabase(NAME)
        db = Room.databaseBuilder(context, ZhihuDatabase::class.java, NAME).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(NAME)
        walFile().delete()
    }

    private fun dbBytes(): Long = context.getDatabasePath(NAME).length()

    private fun walFile(): File = File(context.getDatabasePath(NAME).path + "-wal")

    @Test
    fun `clearing every feed gives the disk space back and leaves the database usable`() = runBlocking {
        val storage = RoomFeedStorage(db)
        // Enough payload that the write-ahead log is unmistakably large.
        val items = (1..200).map { index ->
            testFeedItem(
                targetId = "answer-$index",
                target = testTarget("answer-$index").copy(content = "x".repeat(2_000)),
            )
        }
        storage.replacePaged(query, items, "next", false)

        val bytesBefore = dbBytes() + walFile().length()
        assertTrue(
            "expected the write-ahead log to hold the insert, was ${walFile().length()} bytes",
            walFile().length() > 50_000,
        )

        storage.clearAll()

        assertTrue(
            "the WAL must be truncated by the checkpoint, still ${walFile().length()} bytes",
            walFile().length() < 4_096,
        )
        assertTrue(
            "expected the wipe to give space back: before=$bytesBefore after=${dbBytes() + walFile().length()}",
            dbBytes() + walFile().length() < bytesBefore / 2,
        )
        assertEquals(emptyList<String>(), storage.observe(query).first())

        // The file is still writable afterwards — a checkpoint that left the connection unusable
        // would be worse than the wasted space.
        storage.replacePaged(query, listOf(testFeedItem("answer-1")), "next-2", false)
        assertEquals(listOf("answer-1"), storage.observe(query).first().map { it.target?.id })
        assertEquals("next-2", storage.cursor(query)?.next)
    }

    private companion object {
        const val NAME = "compaction-test.db"
    }
}
