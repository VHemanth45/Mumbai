package com.citymemory.`data`.local.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.citymemory.`data`.local.dao.CityDao
import com.citymemory.`data`.local.dao.CityDao_Impl
import com.citymemory.`data`.local.dao.PlaceDao
import com.citymemory.`data`.local.dao.PlaceDao_Impl
import com.citymemory.`data`.local.dao.PlacePhotoDao
import com.citymemory.`data`.local.dao.PlacePhotoDao_Impl
import com.citymemory.`data`.local.dao.UserPlaceStateDao
import com.citymemory.`data`.local.dao.UserPlaceStateDao_Impl
import com.citymemory.`data`.local.dao.VisitSuggestionDao
import com.citymemory.`data`.local.dao.VisitSuggestionDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class CityMemoryDatabase_Impl : CityMemoryDatabase() {
  private val _cityDao: Lazy<CityDao> = lazy {
    CityDao_Impl(this)
  }

  private val _placeDao: Lazy<PlaceDao> = lazy {
    PlaceDao_Impl(this)
  }

  private val _userPlaceStateDao: Lazy<UserPlaceStateDao> = lazy {
    UserPlaceStateDao_Impl(this)
  }

  private val _placePhotoDao: Lazy<PlacePhotoDao> = lazy {
    PlacePhotoDao_Impl(this)
  }

  private val _visitSuggestionDao: Lazy<VisitSuggestionDao> = lazy {
    VisitSuggestionDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(5,
        "2bca0a284ccf9a02b49613df4f40d227", "a54f447624d87a87fae69d301c45cac7") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `country` TEXT NOT NULL, `catalogStamp` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `cityId` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `imageUrl` TEXT, `displayOrder` INTEGER NOT NULL, `address` TEXT, `isUserAdded` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`cityId`) REFERENCES `cities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_places_cityId` ON `places` (`cityId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_places_category` ON `places` (`category`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `user_place_state` (`placeId` TEXT NOT NULL, `isVisited` INTEGER NOT NULL, `isWishlisted` INTEGER NOT NULL, `visitedAt` INTEGER, `wishlistedAt` INTEGER, `rating` INTEGER, `note` TEXT, PRIMARY KEY(`placeId`), FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `place_photos` (`id` TEXT NOT NULL, `placeId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_place_photos_placeId` ON `place_photos` (`placeId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `visit_suggestions` (`id` TEXT NOT NULL, `placeId` TEXT NOT NULL, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `detectedAt` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `photoUri` TEXT, `createdAt` INTEGER NOT NULL, `resolvedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_suggestions_placeId` ON `visit_suggestions` (`placeId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_suggestions_status` ON `visit_suggestions` (`status`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2bca0a284ccf9a02b49613df4f40d227')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `cities`")
        connection.execSQL("DROP TABLE IF EXISTS `places`")
        connection.execSQL("DROP TABLE IF EXISTS `user_place_state`")
        connection.execSQL("DROP TABLE IF EXISTS `place_photos`")
        connection.execSQL("DROP TABLE IF EXISTS `visit_suggestions`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsCities: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCities.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCities.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCities.put("country", TableInfo.Column("country", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCities.put("catalogStamp", TableInfo.Column("catalogStamp", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCities: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCities: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCities: TableInfo = TableInfo("cities", _columnsCities, _foreignKeysCities,
            _indicesCities)
        val _existingCities: TableInfo = read(connection, "cities")
        if (!_infoCities.equals(_existingCities)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |cities(com.citymemory.data.local.entities.CityEntity).
              | Expected:
              |""".trimMargin() + _infoCities + """
              |
              | Found:
              |""".trimMargin() + _existingCities)
        }
        val _columnsPlaces: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlaces.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("cityId", TableInfo.Column("cityId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("description", TableInfo.Column("description", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("longitude", TableInfo.Column("longitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("imageUrl", TableInfo.Column("imageUrl", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("displayOrder", TableInfo.Column("displayOrder", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("address", TableInfo.Column("address", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaces.put("isUserAdded", TableInfo.Column("isUserAdded", "INTEGER", true, 0, "0",
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlaces: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysPlaces.add(TableInfo.ForeignKey("cities", "CASCADE", "NO ACTION",
            listOf("cityId"), listOf("id")))
        val _indicesPlaces: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPlaces.add(TableInfo.Index("index_places_cityId", false, listOf("cityId"),
            listOf("ASC")))
        _indicesPlaces.add(TableInfo.Index("index_places_category", false, listOf("category"),
            listOf("ASC")))
        val _infoPlaces: TableInfo = TableInfo("places", _columnsPlaces, _foreignKeysPlaces,
            _indicesPlaces)
        val _existingPlaces: TableInfo = read(connection, "places")
        if (!_infoPlaces.equals(_existingPlaces)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |places(com.citymemory.data.local.entities.PlaceEntity).
              | Expected:
              |""".trimMargin() + _infoPlaces + """
              |
              | Found:
              |""".trimMargin() + _existingPlaces)
        }
        val _columnsUserPlaceState: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsUserPlaceState.put("placeId", TableInfo.Column("placeId", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("isVisited", TableInfo.Column("isVisited", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("isWishlisted", TableInfo.Column("isWishlisted", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("visitedAt", TableInfo.Column("visitedAt", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("wishlistedAt", TableInfo.Column("wishlistedAt", "INTEGER",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("rating", TableInfo.Column("rating", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsUserPlaceState.put("note", TableInfo.Column("note", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysUserPlaceState: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysUserPlaceState.add(TableInfo.ForeignKey("places", "CASCADE", "NO ACTION",
            listOf("placeId"), listOf("id")))
        val _indicesUserPlaceState: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoUserPlaceState: TableInfo = TableInfo("user_place_state", _columnsUserPlaceState,
            _foreignKeysUserPlaceState, _indicesUserPlaceState)
        val _existingUserPlaceState: TableInfo = read(connection, "user_place_state")
        if (!_infoUserPlaceState.equals(_existingUserPlaceState)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |user_place_state(com.citymemory.data.local.entities.UserPlaceStateEntity).
              | Expected:
              |""".trimMargin() + _infoUserPlaceState + """
              |
              | Found:
              |""".trimMargin() + _existingUserPlaceState)
        }
        val _columnsPlacePhotos: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlacePhotos.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlacePhotos.put("placeId", TableInfo.Column("placeId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlacePhotos.put("fileName", TableInfo.Column("fileName", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPlacePhotos.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlacePhotos: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysPlacePhotos.add(TableInfo.ForeignKey("places", "CASCADE", "NO ACTION",
            listOf("placeId"), listOf("id")))
        val _indicesPlacePhotos: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPlacePhotos.add(TableInfo.Index("index_place_photos_placeId", false,
            listOf("placeId"), listOf("ASC")))
        val _infoPlacePhotos: TableInfo = TableInfo("place_photos", _columnsPlacePhotos,
            _foreignKeysPlacePhotos, _indicesPlacePhotos)
        val _existingPlacePhotos: TableInfo = read(connection, "place_photos")
        if (!_infoPlacePhotos.equals(_existingPlacePhotos)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |place_photos(com.citymemory.data.local.entities.PlacePhotoEntity).
              | Expected:
              |""".trimMargin() + _infoPlacePhotos + """
              |
              | Found:
              |""".trimMargin() + _existingPlacePhotos)
        }
        val _columnsVisitSuggestions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsVisitSuggestions.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("placeId", TableInfo.Column("placeId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("source", TableInfo.Column("source", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("detectedAt", TableInfo.Column("detectedAt", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("longitude", TableInfo.Column("longitude", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("photoUri", TableInfo.Column("photoUri", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVisitSuggestions.put("resolvedAt", TableInfo.Column("resolvedAt", "INTEGER", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysVisitSuggestions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysVisitSuggestions.add(TableInfo.ForeignKey("places", "CASCADE", "NO ACTION",
            listOf("placeId"), listOf("id")))
        val _indicesVisitSuggestions: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesVisitSuggestions.add(TableInfo.Index("index_visit_suggestions_placeId", false,
            listOf("placeId"), listOf("ASC")))
        _indicesVisitSuggestions.add(TableInfo.Index("index_visit_suggestions_status", false,
            listOf("status"), listOf("ASC")))
        val _infoVisitSuggestions: TableInfo = TableInfo("visit_suggestions",
            _columnsVisitSuggestions, _foreignKeysVisitSuggestions, _indicesVisitSuggestions)
        val _existingVisitSuggestions: TableInfo = read(connection, "visit_suggestions")
        if (!_infoVisitSuggestions.equals(_existingVisitSuggestions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |visit_suggestions(com.citymemory.data.local.entities.VisitSuggestionEntity).
              | Expected:
              |""".trimMargin() + _infoVisitSuggestions + """
              |
              | Found:
              |""".trimMargin() + _existingVisitSuggestions)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "cities", "places",
        "user_place_state", "place_photos", "visit_suggestions")
  }

  public override fun clearAllTables() {
    super.performClear(true, "cities", "places", "user_place_state", "place_photos",
        "visit_suggestions")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(CityDao::class, CityDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PlaceDao::class, PlaceDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(UserPlaceStateDao::class, UserPlaceStateDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PlacePhotoDao::class, PlacePhotoDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(VisitSuggestionDao::class,
        VisitSuggestionDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun cityDao(): CityDao = _cityDao.value

  public override fun placeDao(): PlaceDao = _placeDao.value

  public override fun userPlaceStateDao(): UserPlaceStateDao = _userPlaceStateDao.value

  public override fun placePhotoDao(): PlacePhotoDao = _placePhotoDao.value

  public override fun visitSuggestionDao(): VisitSuggestionDao = _visitSuggestionDao.value
}
