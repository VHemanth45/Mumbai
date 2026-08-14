package com.citymemory.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.citymemory.`data`.local.entities.UserPlaceStateEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class UserPlaceStateDao_Impl(
  __db: RoomDatabase,
) : UserPlaceStateDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfUserPlaceStateEntity: EntityUpsertAdapter<UserPlaceStateEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfUserPlaceStateEntity = EntityUpsertAdapter<UserPlaceStateEntity>(object :
        EntityInsertAdapter<UserPlaceStateEntity>() {
      protected override fun createQuery(): String =
          "INSERT INTO `user_place_state` (`placeId`,`isVisited`,`isWishlisted`,`visitedAt`,`wishlistedAt`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: UserPlaceStateEntity) {
        statement.bindText(1, entity.placeId)
        val _tmp: Int = if (entity.isVisited) 1 else 0
        statement.bindLong(2, _tmp.toLong())
        val _tmp_1: Int = if (entity.isWishlisted) 1 else 0
        statement.bindLong(3, _tmp_1.toLong())
        val _tmpVisitedAt: Long? = entity.visitedAt
        if (_tmpVisitedAt == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpVisitedAt)
        }
        val _tmpWishlistedAt: Long? = entity.wishlistedAt
        if (_tmpWishlistedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpWishlistedAt)
        }
      }
    }, object : EntityDeleteOrUpdateAdapter<UserPlaceStateEntity>() {
      protected override fun createQuery(): String =
          "UPDATE `user_place_state` SET `placeId` = ?,`isVisited` = ?,`isWishlisted` = ?,`visitedAt` = ?,`wishlistedAt` = ? WHERE `placeId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: UserPlaceStateEntity) {
        statement.bindText(1, entity.placeId)
        val _tmp: Int = if (entity.isVisited) 1 else 0
        statement.bindLong(2, _tmp.toLong())
        val _tmp_1: Int = if (entity.isWishlisted) 1 else 0
        statement.bindLong(3, _tmp_1.toLong())
        val _tmpVisitedAt: Long? = entity.visitedAt
        if (_tmpVisitedAt == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpVisitedAt)
        }
        val _tmpWishlistedAt: Long? = entity.wishlistedAt
        if (_tmpWishlistedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpWishlistedAt)
        }
        statement.bindText(6, entity.placeId)
      }
    })
  }

  public override suspend fun upsert(state: UserPlaceStateEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __upsertAdapterOfUserPlaceStateEntity.upsert(_connection, state)
  }

  public override suspend fun getState(placeId: String): UserPlaceStateEntity? {
    val _sql: String = "SELECT * FROM user_place_state WHERE placeId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, placeId)
        val _columnIndexOfPlaceId: Int = getColumnIndexOrThrow(_stmt, "placeId")
        val _columnIndexOfIsVisited: Int = getColumnIndexOrThrow(_stmt, "isVisited")
        val _columnIndexOfIsWishlisted: Int = getColumnIndexOrThrow(_stmt, "isWishlisted")
        val _columnIndexOfVisitedAt: Int = getColumnIndexOrThrow(_stmt, "visitedAt")
        val _columnIndexOfWishlistedAt: Int = getColumnIndexOrThrow(_stmt, "wishlistedAt")
        val _result: UserPlaceStateEntity?
        if (_stmt.step()) {
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
          _result =
              UserPlaceStateEntity(_tmpPlaceId,_tmpIsVisited,_tmpIsWishlisted,_tmpVisitedAt,_tmpWishlistedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun delete(placeId: String) {
    val _sql: String = "DELETE FROM user_place_state WHERE placeId = ?"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
