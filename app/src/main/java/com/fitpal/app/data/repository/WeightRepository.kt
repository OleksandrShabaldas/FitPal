package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.WeightDao
import com.fitpal.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val weightDao: WeightDao
) {
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    fun getLatest(): Flow<WeightEntryEntity?> = weightDao.getLatest()

    fun getRange(from: String, to: String): Flow<List<WeightEntryEntity>> =
        weightDao.getRange(from, to)

    fun getAll(): Flow<List<WeightEntryEntity>> = weightDao.getAll()

    suspend fun logWeight(weightKg: Float, date: LocalDate = LocalDate.now()) {
        if (weightKg <= 0f) return
        weightDao.upsertForDate(
            WeightEntryEntity(date = date.format(dateFormat), weightKg = weightKg)
        )
    }

    suspend fun delete(id: Long) = weightDao.deleteById(id)
}
