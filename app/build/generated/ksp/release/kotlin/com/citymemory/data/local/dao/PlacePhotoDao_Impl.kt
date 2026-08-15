package com.citymemory.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.citymemory.`data`.local.entities.PlacePhotoEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PlacePhotoDao_Impl(
  __db: RoomDatabase,
) : PlacePhotoDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPlacePhotoEntity: EntityInsertAdapter<PlacePhotoEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPlacePhotoEntity = object : EntityInsertAdapter<PlacePhotoEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `place_photos` (`id`,`placeId`,`fileName`,`addedAt`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlacePhotoEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.placeId)
        statement.bindText(3, entity.fileName)
        statement.bindLong(4, entity.addedAt)
      }
    }
  }

  public override suspend fun insert(photo: PlacePhotoEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPlacePhotoEntity.insert(_connection, photo)
  }

  public override fun observePhotos(placeId: String): Flow<List<PlacePhotoEntity>> {
    val _sql: String = "SELECT * FROM place_photos WHERE placeId = ? ORDER BY addedAt ASC"
    return createFlow(__db, false, arrayOf("place_photos")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "fileName")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<PlacePhotoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlacePhotoEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = PlacePhotoEntity(_tmpId,_tmpPlaceId,_tmpFileName,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun photosFor(placeId: String): List<PlacePhotoEntity> {
    val _sql: String = "SELECT * FROM place_photos WHERE placeId = ? ORDER BY addedAt ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "fileName")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<PlacePhotoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlacePhotoEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = PlacePhotoEntity(_tmpId,_tmpPlaceId,_tmpFileName,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun photo(photoId: String): PlacePhotoEntity? {
    val _sql: String = "SELECT * FROM place_photos WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, photoId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "fileName")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: PlacePhotoEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = PlacePhotoEntity(_tmpId,_tmpPlaceId,_tmpFileName,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun countFor(placeId: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM place_photos WHERE placeId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
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

  public override suspend fun delete(photoId: String) {
    val _sql: String = "DELETE FROM place_photos WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, photoId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
