package com.sesliplanlayici.app.core.data

import android.database.SQLException
import com.sesliplanlayici.app.core.data.db.TaskDao
import com.sesliplanlayici.app.core.data.db.toEntity
import com.sesliplanlayici.app.core.domain.Task
import com.sesliplanlayici.app.core.domain.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomTaskRepository @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {

    override suspend fun addAll(tasks: List<Task>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.insertAll(tasks.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: SQLException) {
            // Disk full, corrupt file and the like: reported to the user, never a crash.
            Result.failure(e)
        }
    }
}
