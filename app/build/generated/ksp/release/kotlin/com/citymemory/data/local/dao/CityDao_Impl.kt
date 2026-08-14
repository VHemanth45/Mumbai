package com.citymemory.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.citymemory.`data`.local.entities.CityEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class CityDao_Impl(
  __db: RoomDatabase,
) : CityDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfCityEntity: EntityUpsertAdapter<CityEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfCityEntity = EntityUpsertAdapter<CityEntity>(object :
        EntityInsertAdapter<CityEntity>() {
      protected override fun createQuery(): String =
          "INSERT INTO `cities` (`id`,`name`,`country`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CityEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.country)
      }
    }, object : EntityDeleteOrUpdateAdapter<CityEntity>() {
      protected override fun createQuery(): String =
          "UPDATE `cities` SET `id` = ?,`name` = ?,`country` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: CityEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.country)
        statement.bindText(4, entity.id)
      }
    })
  }

  public override suspend fun upsertAll(cities: List<CityEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __upsertAdapterOfCityEntity.upsert(_connection, cities)
  }

  public override fun observeCity(cityId: String): Flow<CityEntity?> {
    val _sql: String = "SELECT * FROM cities WHERE id = ?"
    return createFlow(__db, false, arrayOf("cities")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, cityId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCountry: Int = getColumnIndexOrThrow(_stmt, "country")
        val _result: CityEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCountry: String
          _tmpCountry = _stmt.getText(_columnIndexOfCountry)
          _result = CityEntity(_tmpId,_tmpName,_tmpCountry)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
