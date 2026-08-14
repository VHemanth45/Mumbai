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
import com.citymemory.`data`.local.dao.UserPlaceStateDao
import com.citymemory.`data`.local.dao.UserPlaceStateDao_Impl
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

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "0167d70d870175661a54ca080b5b40f9", "c63857323397bbb36ce9141b4b9bab53") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `country` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `cityId` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `imageUrl` TEXT, `displayOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`cityId`) REFERENCES `cities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_places_cityId` ON `places` (`cityId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_places_category` ON `places` (`category`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `user_place_state` (`placeId` TEXT NOT NULL, `isVisited` INTEGER NOT NULL, `isWishlisted` INTEGER NOT NULL, `visitedAt` INTEGER, `wishlistedAt` INTEGER, PRIMARY KEY(`placeId`), FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0167d70d870175661a54ca080b5b40f9')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `cities`")
        connection.execSQL("DROP TABLE IF EXISTS `places`")
        connection.execSQL("DROP TABLE IF EXISTS `user_place_state`")
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
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "cities", "places",
        "user_place_state")
  }

  public override fun clearAllTables() {
    super.performClear(true, "cities", "places", "user_place_state")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(CityDao::class, CityDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PlaceDao::class, PlaceDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(UserPlaceStateDao::class, UserPlaceStateDao_Impl.getRequiredConverters())
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
}
