package com.lukeneedham.poetrybook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database: books, the poems in them, and every saved version of each poem.
 * See docs/database.md for the schema and the reasons behind it.
 *
 * Room turns foreign key enforcement on, which the cascading deletes rely on. There is no
 * destructive-migration fallback on purpose: a schema change must come with a real migration, or
 * the user's poems would be wiped. The schema is exported to `app/schemas/` so migrations can be tested.
 */
@Database(
    entities = [BookEntity::class, PoemEntity::class, PoemVersionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PoetryDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun poemDao(): PoemDao

    companion object {
        @Volatile
        private var instance: PoetryDatabase? = null

        fun get(context: Context): PoetryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PoetryDatabase::class.java, "poetrybook.db")
                .build()
                .also { instance = it }
        }
    }
}
