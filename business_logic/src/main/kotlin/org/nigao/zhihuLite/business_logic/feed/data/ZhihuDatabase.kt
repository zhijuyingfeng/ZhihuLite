package org.nigao.zhihuLite.business_logic.feed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local store for feeds.
 *
 * Version 1 is the initial schema; because the payload lives in `feed_item.json`, Zhihu field
 * changes do **not** require a version bump (see [FeedItemEntity]). Only changes to the queryable
 * columns do — and then a real migration must be written: `fallbackToDestructiveMigration` is
 * deliberately NOT enabled, since silently dropping the user's cached feed is worse than a build
 * error.
 */
@Database(
    entities = [FeedItemEntity::class, FeedQueryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ZhihuDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao

    companion object {
        private const val NAME = "zhihu-lite.db"

        fun create(context: Context): ZhihuDatabase =
            Room.databaseBuilder(context.applicationContext, ZhihuDatabase::class.java, NAME)
                // Queries run from IO coroutines already; keeping the default executor avoids
                // surprises in tests and keeps the SQLite connection count predictable.
                .build()

        /** In-memory instance for tests; avoids touching disk and needs no cleanup. */
        fun createInMemory(context: Context): ZhihuDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, ZhihuDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
