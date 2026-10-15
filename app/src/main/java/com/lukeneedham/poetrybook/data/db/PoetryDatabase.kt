package com.lukeneedham.poetrybook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, PoemEntity::class, PoemVersionEntity::class],
    version = 1,
    exportSchema = false,
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
