package com.citymemory.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.citymemory.data.local.dao.CityDao
import com.citymemory.data.local.dao.PlaceDao
import com.citymemory.data.local.dao.PlacePhotoDao
import com.citymemory.data.local.dao.UserPlaceStateDao
import com.citymemory.data.local.entities.CityEntity
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.entities.PlacePhotoEntity
import com.citymemory.data.local.entities.UserPlaceStateEntity

@Database(
    entities = [
        CityEntity::class,
        PlaceEntity::class,
        UserPlaceStateEntity::class,
        PlacePhotoEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CityMemoryDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun placeDao(): PlaceDao
    abstract fun userPlaceStateDao(): UserPlaceStateDao

    abstract fun placePhotoDao(): PlacePhotoDao

    companion object {
        const val DATABASE_NAME = "city_memory.db"

        /**
         * Adds the user's own verdict — a 1..5 rating and free text — to the
         * state table.
         *
         * Additive and nullable, so this is two `ALTER TABLE`s and nothing
         * else: every existing visit, wishlist and timestamp is left exactly
         * where it was, and an installed app's exploration survives the update.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_place_state ADD COLUMN rating INTEGER")
                db.execSQL("ALTER TABLE user_place_state ADD COLUMN note TEXT")
            }
        }

        /**
         * Makes room for a place the user added, and for a catalog big enough
         * that shipping a new one is a normal thing to do.
         *
         * `address` because the catalog now carries every mapped place in
         * Mumbai, chains included, and the line under the name is what tells
         * one Starbucks from the other thirty-one. `isUserAdded` so re-seeding
         * can tell a row it wrote from a row the user did. `catalogStamp` so
         * the app can notice an update shipped a different catalog at all.
         *
         * Three `ALTER TABLE`s and nothing else: additive, nullable or
         * defaulted, no table rebuilt and no row rewritten. Every existing
         * visit, wishlist, rating and note stays exactly where it was.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN address TEXT")
                db.execSQL(
                    "ALTER TABLE places ADD COLUMN isUserAdded INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE cities ADD COLUMN catalogStamp TEXT")
            }
        }

        /**
         * Adds somewhere to keep the photos the user takes at a place.
         *
         * A new table rather than a column, because a place has any number of
         * photos. Additive like the others — nothing existing is touched, and
         * the `IF NOT EXISTS` mirrors exactly what Room generates for the
         * entity, which is what its post-migration validation compares against.
         *
         * The foreign key matters: it is what makes removing a user-added place
         * take its photos' rows with it. The files beside them are the app's
         * problem, not SQLite's, which is why `deleteUserPlace` reads the file
         * names before letting the cascade run.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `place_photos` (" +
                        "`id` TEXT NOT NULL, `placeId` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_place_photos_placeId` " +
                        "ON `place_photos` (`placeId`)",
                )
            }
        }

        // Room turns on `PRAGMA foreign_keys` itself for schemas that declare
        // them, so the places -> user_place_state cascade needs no extra setup.
        fun build(context: Context): CityMemoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CityMemoryDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
