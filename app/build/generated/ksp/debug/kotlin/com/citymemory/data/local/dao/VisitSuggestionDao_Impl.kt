package com.citymemory.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.citymemory.`data`.local.entities.VisitSuggestionEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
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
public class VisitSuggestionDao_Impl(
  __db: RoomDatabase,
) : VisitSuggestionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfVisitSuggestionEntity: EntityInsertAdapter<VisitSuggestionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfVisitSuggestionEntity = object :
        EntityInsertAdapter<VisitSuggestionEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `visit_suggestions` (`id`,`placeId`,`source`,`status`,`detectedAt`,`latitude`,`longitude`,`photoUri`,`createdAt`,`resolvedAt`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: VisitSuggestionEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.placeId)
        statement.bindText(3, entity.source)
        statement.bindText(4, entity.status)
        statement.bindLong(5, entity.detectedAt)
        statement.bindDouble(6, entity.latitude)
        statement.bindDouble(7, entity.longitude)
        val _tmpPhotoUri: String? = entity.photoUri
        if (_tmpPhotoUri == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPhotoUri)
        }
        statement.bindLong(9, entity.createdAt)
        val _tmpResolvedAt: Long? = entity.resolvedAt
        if (_tmpResolvedAt == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpResolvedAt)
        }
      }
    }
  }

  public override suspend fun insert(suggestion: VisitSuggestionEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfVisitSuggestionEntity.insert(_connection, suggestion)
  }

  public override fun observePending(): Flow<List<VisitSuggestionEntity>> {
    val _sql: String =
        "SELECT * FROM visit_suggestions WHERE status = 'pending' ORDER BY detectedAt DESC"
    return createFlow(__db, false, arrayOf("visit_suggestions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDetectedAt: Int = getColumnIndexOrThrow(_stmt, "detectedAt")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfPhotoUri: Int = getColumnIndexOrThrow(_stmt, "photoUri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfResolvedAt: Int = getColumnIndexOrThrow(_stmt, "resolvedAt")
        val _result: MutableList<VisitSuggestionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VisitSuggestionEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpDetectedAt: Long
          _tmpDetectedAt = _stmt.getLong(_columnIndexOfDetectedAt)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpPhotoUri: String?
          if (_stmt.isNull(_columnIndexOfPhotoUri)) {
            _tmpPhotoUri = null
          } else {
            _tmpPhotoUri = _stmt.getText(_columnIndexOfPhotoUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpResolvedAt: Long?
          if (_stmt.isNull(_columnIndexOfResolvedAt)) {
            _tmpResolvedAt = null
          } else {
            _tmpResolvedAt = _stmt.getLong(_columnIndexOfResolvedAt)
          }
          _item =
              VisitSuggestionEntity(_tmpId,_tmpPlaceId,_tmpSource,_tmpStatus,_tmpDetectedAt,_tmpLatitude,_tmpLongitude,_tmpPhotoUri,_tmpCreatedAt,_tmpResolvedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun `get`(id: String): VisitSuggestionEntity? {
    val _sql: String = "SELECT * FROM visit_suggestions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDetectedAt: Int = getColumnIndexOrThrow(_stmt, "detectedAt")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfPhotoUri: Int = getColumnIndexOrThrow(_stmt, "photoUri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfResolvedAt: Int = getColumnIndexOrThrow(_stmt, "resolvedAt")
        val _result: VisitSuggestionEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpDetectedAt: Long
          _tmpDetectedAt = _stmt.getLong(_columnIndexOfDetectedAt)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpPhotoUri: String?
          if (_stmt.isNull(_columnIndexOfPhotoUri)) {
            _tmpPhotoUri = null
          } else {
            _tmpPhotoUri = _stmt.getText(_columnIndexOfPhotoUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpResolvedAt: Long?
          if (_stmt.isNull(_columnIndexOfResolvedAt)) {
            _tmpResolvedAt = null
          } else {
            _tmpResolvedAt = _stmt.getLong(_columnIndexOfResolvedAt)
          }
          _result =
              VisitSuggestionEntity(_tmpId,_tmpPlaceId,_tmpSource,_tmpStatus,_tmpDetectedAt,_tmpLatitude,_tmpLongitude,_tmpPhotoUri,_tmpCreatedAt,_tmpResolvedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun pendingCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM visit_suggestions WHERE status = 'pending'"
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

  public override suspend fun latestFor(placeId: String): VisitSuggestionEntity? {
    val _sql: String =
        "SELECT * FROM visit_suggestions WHERE placeId = ? ORDER BY detectedAt DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDetectedAt: Int = getColumnIndexOrThrow(_stmt, "detectedAt")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfPhotoUri: Int = getColumnIndexOrThrow(_stmt, "photoUri")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfResolvedAt: Int = getColumnIndexOrThrow(_stmt, "resolvedAt")
        val _result: VisitSuggestionEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPlaceId: String
          _tmpPlaceId = _stmt.getText(_columnIndexOfPlaceId)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpDetectedAt: Long
          _tmpDetectedAt = _stmt.getLong(_columnIndexOfDetectedAt)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpPhotoUri: String?
          if (_stmt.isNull(_columnIndexOfPhotoUri)) {
            _tmpPhotoUri = null
          } else {
            _tmpPhotoUri = _stmt.getText(_columnIndexOfPhotoUri)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpResolvedAt: Long?
          if (_stmt.isNull(_columnIndexOfResolvedAt)) {
            _tmpResolvedAt = null
          } else {
            _tmpResolvedAt = _stmt.getLong(_columnIndexOfResolvedAt)
          }
          _result =
              VisitSuggestionEntity(_tmpId,_tmpPlaceId,_tmpSource,_tmpStatus,_tmpDetectedAt,_tmpLatitude,_tmpLongitude,_tmpPhotoUri,_tmpCreatedAt,_tmpResolvedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setStatus(
    id: String,
    status: String,
    at: Long,
  ) {
    val _sql: String = "UPDATE visit_suggestions SET status = ?, resolvedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, status)
        _argIndex = 2
        _stmt.bindLong(_argIndex, at)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun dismissPendingFor(placeId: String, at: Long) {
    val _sql: String =
        "UPDATE visit_suggestions SET status = 'dismissed', resolvedAt = ? WHERE placeId = ? AND status = 'pending'"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, at)
        _argIndex = 2
        _stmt.bindText(_argIndex, placeId)
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
