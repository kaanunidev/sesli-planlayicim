package com.sesliplanlayici.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TaskDao {
    /** One transaction: Room wraps a list insert so either every task is stored or none. */
    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>
}
