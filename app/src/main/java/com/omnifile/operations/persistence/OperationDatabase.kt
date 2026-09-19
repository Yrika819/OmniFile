package com.omnifile.operations.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OperationDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao
}
