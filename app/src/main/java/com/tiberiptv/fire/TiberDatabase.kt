package com.tiberiptv.fire

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TiberDatabase.FavoriteEntity::class,
        TiberDatabase.ResumeEntity::class,
        TiberDatabase.CacheEntity::class,
        TiberDatabase.HistoryEntity::class,
        TiberDatabase.CatalogItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class TiberDatabase : RoomDatabase() {
    abstract fun stateDao(): StateDao

    @Dao
    interface StateDao {
        @Query("SELECT * FROM favorites ORDER BY title COLLATE NOCASE")
        fun favorites(): List<FavoriteEntity>

        @Query("SELECT item_json FROM favorites WHERE item_key = :key LIMIT 1")
        fun favoriteJson(key: String): String?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertFavorite(entity: FavoriteEntity)

        @Query("DELETE FROM favorites WHERE item_key = :key")
        fun deleteFavorite(key: String)

        @Query("SELECT position_ms FROM resumes WHERE item_key = :key LIMIT 1")
        fun resumePosition(key: String): Long?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertResume(entity: ResumeEntity)

        @Query("DELETE FROM resumes WHERE item_key = :key")
        fun deleteResume(key: String)

        @Query("SELECT * FROM cache_entries WHERE scope = :scope LIMIT 1")
        fun cache(scope: String): CacheEntity?

        @Query("SELECT saved_at FROM cache_entries WHERE scope = :scope LIMIT 1")
        fun cacheSavedAt(scope: String): Long?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertCache(entity: CacheEntity)

        @Query("DELETE FROM cache_entries WHERE scope = :scope")
        fun deleteCacheScope(scope: String)

        @Query("DELETE FROM catalog_items WHERE scope = :scope")
        fun deleteCatalogScope(scope: String)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertCatalogItem(entity: CatalogItemEntity)

        @Query("SELECT * FROM catalog_items WHERE scope = :scope ORDER BY row_index ASC, item_index ASC")
        fun catalogItems(scope: String): List<CatalogItemEntity>

        @Query("SELECT COUNT(*) FROM catalog_items WHERE scope = :scope")
        fun catalogItemCount(scope: String): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertHistory(entity: HistoryEntity)

        @Query("SELECT * FROM history ORDER BY played_at DESC LIMIT :limit")
        fun history(limit: Int): List<HistoryEntity>
    }

    @Entity(tableName = "favorites")
    class FavoriteEntity {
        @PrimaryKey
        @JvmField
        var item_key: String = ""

        @JvmField
        var title: String? = null

        @JvmField
        var item_json: String? = null
    }

    @Entity(tableName = "resumes")
    class ResumeEntity {
        @PrimaryKey
        @JvmField
        var item_key: String = ""

        @JvmField
        var position_ms: Long = 0L

        @JvmField
        var updated_at: Long = 0L
    }

    @Entity(tableName = "cache_entries")
    class CacheEntity {
        @PrimaryKey
        @JvmField
        var scope: String = ""

        @JvmField
        var json: String? = null

        @JvmField
        var saved_at: Long = 0L
    }

    @Entity(tableName = "history")
    class HistoryEntity {
        @PrimaryKey
        @JvmField
        var item_key: String = ""

        @JvmField
        var title: String? = null

        @JvmField
        var type: String? = null

        @JvmField
        var item_json: String? = null

        @JvmField
        var position_ms: Long = 0L

        @JvmField
        var played_at: Long = 0L
    }

    @Entity(tableName = "catalog_items", primaryKeys = ["scope", "item_key"])
    class CatalogItemEntity {
        @JvmField
        var scope: String = ""

        @JvmField
        var item_key: String = ""

        @JvmField
        var row_title: String? = null

        @JvmField
        var row_index: Int = 0

        @JvmField
        var item_index: Int = 0

        @JvmField
        var title: String? = null

        @JvmField
        var search_text: String? = null

        @JvmField
        var item_json: String? = null

        @JvmField
        var saved_at: Long = 0L
    }

    companion object {
        @Volatile
        private var instance: TiberDatabase? = null

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_items` " +
                        "(`scope` TEXT NOT NULL, `item_key` TEXT NOT NULL, `row_title` TEXT, " +
                        "`row_index` INTEGER NOT NULL, `item_index` INTEGER NOT NULL, `title` TEXT, " +
                        "`search_text` TEXT, `item_json` TEXT, `saved_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scope`, `item_key`))"
                )
            }
        }

        @JvmStatic
        fun get(context: Context): TiberDatabase {
            instance?.let { return it }
            return synchronized(TiberDatabase::class.java) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TiberDatabase::class.java,
                    "tiber_state.db"
                )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
