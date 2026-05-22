package com.tiberiptv.fire

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery

@Database(
    entities = [
        TiberDatabase.FavoriteEntity::class,
        TiberDatabase.ResumeEntity::class,
        TiberDatabase.CacheEntity::class,
        TiberDatabase.HistoryEntity::class,
        TiberDatabase.CatalogItemEntity::class,
        TiberDatabase.ChannelCatalogEntity::class,
        TiberDatabase.MovieCatalogEntity::class,
        TiberDatabase.SeriesCatalogEntity::class,
        TiberDatabase.ItemDetailEntity::class,
    ],
    version = 6,
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

        @Query("DELETE FROM catalog_channels")
        fun deleteChannelCatalog()

        @Query("DELETE FROM catalog_movies")
        fun deleteMovieCatalog()

        @Query("DELETE FROM catalog_series")
        fun deleteSeriesCatalog()

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertCatalogItem(entity: CatalogItemEntity)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertCatalogItems(entities: List<CatalogItemEntity>)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertChannelCatalogItems(entities: List<ChannelCatalogEntity>)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertMovieCatalogItems(entities: List<MovieCatalogEntity>)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertSeriesCatalogItems(entities: List<SeriesCatalogEntity>)

        @Query("SELECT * FROM catalog_items WHERE scope = :scope ORDER BY row_index ASC, item_index ASC")
        fun catalogItems(scope: String): List<CatalogItemEntity>

        @Query("SELECT COUNT(*) FROM catalog_items WHERE scope = :scope")
        fun catalogItemCount(scope: String): Int

        @Query("SELECT COUNT(*) FROM catalog_channels")
        fun channelCatalogItemCount(): Int

        @Query("SELECT COUNT(*) FROM catalog_movies")
        fun movieCatalogItemCount(): Int

        @Query("SELECT COUNT(*) FROM catalog_series")
        fun seriesCatalogItemCount(): Int

        @RawQuery
        fun catalogQuery(query: SupportSQLiteQuery): List<CatalogQueryRow>

        @Query("SELECT * FROM item_details WHERE item_key = :key LIMIT 1")
        fun itemDetail(key: String): ItemDetailEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertItemDetail(entity: ItemDetailEntity)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsertHistory(entity: HistoryEntity)

        @Query("SELECT * FROM history ORDER BY played_at DESC LIMIT :limit")
        fun history(limit: Int): List<HistoryEntity>
    }

    @Entity(
        tableName = "favorites",
        indices = [Index(value = ["title"])]
    )
    class FavoriteEntity {
        @PrimaryKey
        @JvmField
        var item_key: String = ""

        @JvmField
        var title: String? = null

        @JvmField
        var item_json: String? = null
    }

    @Entity(
        tableName = "resumes",
        indices = [Index(value = ["updated_at"])]
    )
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

    @Entity(
        tableName = "history",
        indices = [
            Index(value = ["played_at"]),
            Index(value = ["type", "played_at"])
        ]
    )
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

    @Entity(
        tableName = "catalog_items",
        primaryKeys = ["scope", "item_key"],
        indices = [
            Index(value = ["scope", "row_index", "item_index"]),
            Index(value = ["scope", "row_title"]),
            Index(value = ["scope", "added_epoch"]),
            Index(value = ["scope", "rating_value"]),
            Index(value = ["scope", "lower_title"])
        ]
    )
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
        var title_search: String? = null

        @JvmField
        var row_search: String? = null

        @JvmField
        var year_search: String? = null

        @JvmField
        var combined_search_text: String? = null

        @JvmField
        var lower_title: String? = null

        @JvmField
        var added_epoch: Long = 0L

        @JvmField
        var rating_value: Float = -1f

        @JvmField
        var year_value: Int? = null

        @JvmField
        var ultra_hd: Boolean = false

        @JvmField
        var item_json: String? = null

        @JvmField
        var saved_at: Long = 0L
    }

    @Entity(
        tableName = "catalog_channels",
        primaryKeys = ["item_key"],
        indices = [
            Index(value = ["row_index", "item_index"]),
            Index(value = ["row_title"]),
            Index(value = ["added_epoch"]),
            Index(value = ["rating_value"]),
            Index(value = ["lower_title"]),
            Index(value = ["ultra_hd"]),
            Index(value = ["year_value"])
        ]
    )
    class ChannelCatalogEntity : TypedCatalogEntity()

    @Entity(
        tableName = "catalog_movies",
        primaryKeys = ["item_key"],
        indices = [
            Index(value = ["row_index", "item_index"]),
            Index(value = ["row_title"]),
            Index(value = ["added_epoch"]),
            Index(value = ["rating_value"]),
            Index(value = ["lower_title"]),
            Index(value = ["ultra_hd"]),
            Index(value = ["year_value"])
        ]
    )
    class MovieCatalogEntity : TypedCatalogEntity()

    @Entity(
        tableName = "catalog_series",
        primaryKeys = ["item_key"],
        indices = [
            Index(value = ["row_index", "item_index"]),
            Index(value = ["row_title"]),
            Index(value = ["added_epoch"]),
            Index(value = ["rating_value"]),
            Index(value = ["lower_title"]),
            Index(value = ["ultra_hd"]),
            Index(value = ["year_value"])
        ]
    )
    class SeriesCatalogEntity : TypedCatalogEntity()

    open class TypedCatalogEntity {
        @JvmField
        var item_key: String = ""

        @JvmField
        var row_title: String? = null

        @JvmField
        var row_index: Int = 0

        @JvmField
        var item_index: Int = 0

        @JvmField
        var id: String = ""

        @JvmField
        var title: String? = null

        @JvmField
        var type: String = ""

        @JvmField
        var image_url: String? = null

        @JvmField
        var category_id: String? = null

        @JvmField
        var extension: String? = null

        @JvmField
        var playable: Boolean = true

        @JvmField
        var release_date: String? = null

        @JvmField
        var added_timestamp: String? = null

        @JvmField
        var rating: String? = null

        @JvmField
        var year: String? = null

        @JvmField
        var title_search: String? = null

        @JvmField
        var row_search: String? = null

        @JvmField
        var year_search: String? = null

        @JvmField
        var combined_search_text: String? = null

        @JvmField
        var lower_title: String? = null

        @JvmField
        var added_epoch: Long = 0L

        @JvmField
        var rating_value: Float = -1f

        @JvmField
        var year_value: Int? = null

        @JvmField
        var ultra_hd: Boolean = false

        @JvmField
        var premium_row: Boolean = false

        @JvmField
        var saved_at: Long = 0L
    }

    class CatalogQueryRow : TypedCatalogEntity() {
        @JvmField
        var resume_position_ms: Long = 0L
    }

    @Entity(
        tableName = "item_details",
        indices = [
            Index(value = ["item_type"]),
            Index(value = ["saved_at"])
        ]
    )
    class ItemDetailEntity {
        @PrimaryKey
        @JvmField
        var item_key: String = ""

        @JvmField
        var item_type: String? = null

        @JvmField
        var plot: String? = null

        @JvmField
        var genre: String? = null

        @JvmField
        var duration: String? = null

        @JvmField
        var rating: String? = null

        @JvmField
        var release_date: String? = null

        @JvmField
        var content_rating: String? = null

        @JvmField
        var cast: String? = null

        @JvmField
        var director: String? = null

        @JvmField
        var trailer: String? = null

        @JvmField
        var backdrop_url: String? = null

        @JvmField
        var series_json: String? = null

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

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createPerformanceIndexes(db)
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `title_search` TEXT")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `row_search` TEXT")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `year_search` TEXT")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `combined_search_text` TEXT")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `lower_title` TEXT")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `added_epoch` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `rating_value` REAL NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `year_value` INTEGER")
                db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `ultra_hd` INTEGER NOT NULL DEFAULT 0")
                createCatalogMetadataIndexes(db)
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTypedCatalogTables(db)
            }
        }

        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createItemDetailsTable(db)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
        }

        private fun createPerformanceIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_title` ON `favorites` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_resumes_updated_at` ON `resumes` (`updated_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_played_at` ON `history` (`played_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_type_played_at` ON `history` (`type`, `played_at`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_scope_row_index_item_index` " +
                    "ON `catalog_items` (`scope`, `row_index`, `item_index`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_scope_row_title` " +
                    "ON `catalog_items` (`scope`, `row_title`)"
            )
        }

        private fun createCatalogMetadataIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_items_scope_added_epoch` ON `catalog_items` (`scope`, `added_epoch`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_items_scope_rating_value` ON `catalog_items` (`scope`, `rating_value`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_items_scope_lower_title` ON `catalog_items` (`scope`, `lower_title`)")
        }

        private fun createTypedCatalogTables(db: SupportSQLiteDatabase) {
            createTypedCatalogTable(db, "catalog_channels")
            createTypedCatalogTable(db, "catalog_movies")
            createTypedCatalogTable(db, "catalog_series")
        }

        private fun createItemDetailsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `item_details` (" +
                    "`item_key` TEXT NOT NULL, " +
                    "`item_type` TEXT, " +
                    "`plot` TEXT, " +
                    "`genre` TEXT, " +
                    "`duration` TEXT, " +
                    "`rating` TEXT, " +
                    "`release_date` TEXT, " +
                    "`content_rating` TEXT, " +
                    "`cast` TEXT, " +
                    "`director` TEXT, " +
                    "`trailer` TEXT, " +
                    "`backdrop_url` TEXT, " +
                    "`series_json` TEXT, " +
                    "`saved_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`item_key`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_details_item_type` ON `item_details` (`item_type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_details_saved_at` ON `item_details` (`saved_at`)")
        }

        private fun createTypedCatalogTable(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `$tableName` (" +
                    "`item_key` TEXT NOT NULL, " +
                    "`row_title` TEXT, " +
                    "`row_index` INTEGER NOT NULL, " +
                    "`item_index` INTEGER NOT NULL, " +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT, " +
                    "`type` TEXT NOT NULL, " +
                    "`image_url` TEXT, " +
                    "`category_id` TEXT, " +
                    "`extension` TEXT, " +
                    "`playable` INTEGER NOT NULL, " +
                    "`release_date` TEXT, " +
                    "`added_timestamp` TEXT, " +
                    "`rating` TEXT, " +
                    "`year` TEXT, " +
                    "`title_search` TEXT, " +
                    "`row_search` TEXT, " +
                    "`year_search` TEXT, " +
                    "`combined_search_text` TEXT, " +
                    "`lower_title` TEXT, " +
                    "`added_epoch` INTEGER NOT NULL, " +
                    "`rating_value` REAL NOT NULL, " +
                    "`year_value` INTEGER, " +
                    "`ultra_hd` INTEGER NOT NULL, " +
                    "`premium_row` INTEGER NOT NULL, " +
                    "`saved_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`item_key`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_row_index_item_index` ON `$tableName` (`row_index`, `item_index`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_row_title` ON `$tableName` (`row_title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_added_epoch` ON `$tableName` (`added_epoch`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_rating_value` ON `$tableName` (`rating_value`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_lower_title` ON `$tableName` (`lower_title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_ultra_hd` ON `$tableName` (`ultra_hd`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tableName}_year_value` ON `$tableName` (`year_value`)")
        }

    }
}
