package com.citymemory.`data`.local.dao

import androidx.collection.ArrayMap
import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchArrayMap
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.citymemory.`data`.local.entities.PlaceEntity
import com.citymemory.`data`.local.entities.PlaceWithState
import com.citymemory.`data`.local.entities.UserPlaceStateEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PlaceDao_Impl(
  __db: RoomDatabase,
) : PlaceDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfPlaceEntity: EntityUpsertAdapter<PlaceEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfPlaceEntity = EntityUpsertAdapter<PlaceEntity>(object :
        EntityInsertAdapter<PlaceEntity>() {
      protected override fun createQuery(): String =
          "INSERT INTO `places` (`id`,`cityId`,`name`,`category`,`description`,`latitude`,`longitude`,`imageUrl`,`displayOrder`,`address`,`isUserAdded`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlaceEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.cityId)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.category)
        statement.bindText(5, entity.description)
        statement.bindDouble(6, entity.latitude)
        statement.bindDouble(7, entity.longitude)
        val _tmpImageUrl: String? = entity.imageUrl
        if (_tmpImageUrl == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpImageUrl)
        }
        statement.bindLong(9, entity.displayOrder.toLong())
        val _tmpAddress: String? = entity.address
        if (_tmpAddress == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpAddress)
        }
        val _tmp: Int = if (entity.isUserAdded) 1 else 0
        statement.bindLong(11, _tmp.toLong())
      }
    }, object : EntityDeleteOrUpdateAdapter<PlaceEntity>() {
      protected override fun createQuery(): String =
          "UPDATE `places` SET `id` = ?,`cityId` = ?,`name` = ?,`category` = ?,`description` = ?,`latitude` = ?,`longitude` = ?,`imageUrl` = ?,`displayOrder` = ?,`address` = ?,`isUserAdded` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PlaceEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.cityId)
        statement.bindText(3, entity.name)
        statement.bindText(4, entity.category)
        statement.bindText(5, entity.description)
        statement.bindDouble(6, entity.latitude)
        statement.bindDouble(7, entity.longitude)
        val _tmpImageUrl: String? = entity.imageUrl
        if (_tmpImageUrl == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpImageUrl)
        }
        statement.bindLong(9, entity.displayOrder.toLong())
        val _tmpAddress: String? = entity.address
        if (_tmpAddress == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpAddress)
        }
        val _tmp: Int = if (entity.isUserAdded) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindText(12, entity.id)
      }
    })
  }

  public override suspend fun upsert(place: PlaceEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __upsertAdapterOfPlaceEntity.upsert(_connection, place)
  }

  public override suspend fun upsertAll(places: List<PlaceEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __upsertAdapterOfPlaceEntity.upsert(_connection, places)
  }

  public override fun observePlacesWithState(cityId: String): Flow<List<PlaceWithState>> {
    val _sql: String = "SELECT * FROM places WHERE cityId = ? ORDER BY displayOrder ASC"
    return createFlow(__db, true, arrayOf("user_place_state", "places")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCityId: Int = getColumnIndexOrThrow(_stmt, "cityId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "displayOrder")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfIsUserAdded: Int = getColumnIndexOrThrow(_stmt, "isUserAdded")
        val _collectionState: ArrayMap<String, UserPlaceStateEntity?> =
            ArrayMap<String, UserPlaceStateEntity?>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          _collectionState.put(_tmpKey, null)
        }
        _stmt.reset()
        __fetchRelationshipuserPlaceStateAscomCitymemoryDataLocalEntitiesUserPlaceStateEntity(_connection,
            _collectionState)
        val _result: MutableList<PlaceWithState> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlaceWithState
          val _tmpPlace: PlaceEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpCityId: String
          _tmpCityId = _stmt.getText(_columnIndexOfCityId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpImageUrl: String?
          if (_stmt.isNull(_columnIndexOfImageUrl)) {
            _tmpImageUrl = null
          } else {
            _tmpImageUrl = _stmt.getText(_columnIndexOfImageUrl)
          }
          val _tmpDisplayOrder: Int
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder).toInt()
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpIsUserAdded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserAdded).toInt()
          _tmpIsUserAdded = _tmp != 0
          _tmpPlace =
              PlaceEntity(_tmpId,_tmpCityId,_tmpName,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpImageUrl,_tmpDisplayOrder,_tmpAddress,_tmpIsUserAdded)
          val _tmpState: UserPlaceStateEntity?
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpState = _collectionState.get(_tmpKey_1)
          _item = PlaceWithState(_tmpPlace,_tmpState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observePlaceWithState(placeId: String): Flow<PlaceWithState?> {
    val _sql: String = "SELECT * FROM places WHERE id = ?"
    return createFlow(__db, true, arrayOf("user_place_state", "places")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCityId: Int = getColumnIndexOrThrow(_stmt, "cityId")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfDisplayOrder: Int = getColumnIndexOrThrow(_stmt, "displayOrder")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfIsUserAdded: Int = getColumnIndexOrThrow(_stmt, "isUserAdded")
        val _collectionState: ArrayMap<String, UserPlaceStateEntity?> =
            ArrayMap<String, UserPlaceStateEntity?>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          _collectionState.put(_tmpKey, null)
        }
        _stmt.reset()
        __fetchRelationshipuserPlaceStateAscomCitymemoryDataLocalEntitiesUserPlaceStateEntity(_connection,
            _collectionState)
        val _result: PlaceWithState?
        if (_stmt.step()) {
          val _tmpPlace: PlaceEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpCityId: String
          _tmpCityId = _stmt.getText(_columnIndexOfCityId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpImageUrl: String?
          if (_stmt.isNull(_columnIndexOfImageUrl)) {
            _tmpImageUrl = null
          } else {
            _tmpImageUrl = _stmt.getText(_columnIndexOfImageUrl)
          }
          val _tmpDisplayOrder: Int
          _tmpDisplayOrder = _stmt.getLong(_columnIndexOfDisplayOrder).toInt()
          val _tmpAddress: String?
          if (_stmt.isNull(_columnIndexOfAddress)) {
            _tmpAddress = null
          } else {
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          }
          val _tmpIsUserAdded: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserAdded).toInt()
          _tmpIsUserAdded = _tmp != 0
          _tmpPlace =
              PlaceEntity(_tmpId,_tmpCityId,_tmpName,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpImageUrl,_tmpDisplayOrder,_tmpAddress,_tmpIsUserAdded)
          val _tmpState: UserPlaceStateEntity?
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpState = _collectionState.get(_tmpKey_1)
          _result = PlaceWithState(_tmpPlace,_tmpState)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM places"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun exists(placeId: String): Boolean {
    val _sql: String = "SELECT EXISTS(SELECT 1 FROM places WHERE id = ?)"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _result: Boolean
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp != 0
        } else {
          _result = false
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun lowestDisplayOrder(cityId: String): Int? {
    val _sql: String = "SELECT MIN(displayOrder) FROM places WHERE cityId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cityId)
        val _result: Int?
        if (_stmt.step()) {
          val _tmp: Int?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0).toInt()
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteUserPlace(placeId: String) {
    val _sql: String = "DELETE FROM places WHERE id = ? AND isUserAdded = 1"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateAddress(placeId: String, address: String?) {
    val _sql: String = "UPDATE places SET address = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (address == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, address)
        }
        _argIndex = 2
        _stmt.bindText(_argIndex, placeId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private
      fun __fetchRelationshipuserPlaceStateAscomCitymemoryDataLocalEntitiesUserPlaceStateEntity(_connection: SQLiteConnection,
      _map: ArrayMap<String, UserPlaceStateEntity?>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, false) { _tmpMap ->
        __fetchRelationshipuserPlaceStateAscomCitymemoryDataLocalEntitiesUserPlaceStateEntity(_connection,
            _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `placeId`,`isVisited`,`isWishlisted`,`visitedAt`,`wishlistedAt`,`rating`,`note` FROM `user_place_state` WHERE `placeId` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "placeId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfPlaceId: Int = 0
      val _columnIndexOfIsVisited: Int = 1
      val _columnIndexOfIsWishlisted: Int = 2
      val _columnIndexOfVisitedAt: Int = 3
      val _columnIndexOfWishlistedAt: Int = 4
      val _columnIndexOfRating: Int = 5
      val _columnIndexOfNote: Int = 6
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        if (_map.containsKey(_tmpKey)) {
          val _item_1: UserPlaceStateEntity?
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpIsVisited: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVisited).toInt()
          _tmpIsVisited = _tmp != 0
          val _tmpIsWishlisted: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsWishlisted).toInt()
          _tmpIsWishlisted = _tmp_1 != 0
          val _tmpVisitedAt: Long?
          if (_stmt.isNull(_columnIndexOfVisitedAt)) {
            _tmpVisitedAt = null
          } else {
            _tmpVisitedAt = _stmt.getLong(_columnIndexOfVisitedAt)
          }
          val _tmpWishlistedAt: Long?
          if (_stmt.isNull(_columnIndexOfWishlistedAt)) {
            _tmpWishlistedAt = null
          } else {
            _tmpWishlistedAt = _stmt.getLong(_columnIndexOfWishlistedAt)
          }
          val _tmpRating: Int?
          if (_stmt.isNull(_columnIndexOfRating)) {
            _tmpRating = null
          } else {
            _tmpRating = _stmt.getLong(_columnIndexOfRating).toInt()
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          _item_1 =
              UserPlaceStateEntity(_tmpPlaceId,_tmpIsVisited,_tmpIsWishlisted,_tmpVisitedAt,_tmpWishlistedAt,_tmpRating,_tmpNote)
          _map.put(_tmpKey, _item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
