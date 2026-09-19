package com.omnifile.operations.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.operations.DurableLocator
import com.omnifile.operations.OperationSnapshot
import com.omnifile.operations.OperationState
import com.omnifile.operations.OperationType
import com.omnifile.operations.SourceDeleteState
import com.omnifile.operations.TransferStage
import com.omnifile.storage.ProviderId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "operation-database-test.db"

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        OperationDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun activeOperationSurvivesCloseAndReopenWithLongCounters() {
        val operation = operation()
        val first = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
        first.operationDao().insert(OperationEntity.from(operation))
        first.close()

        val second = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
        val restored = second.operationDao().find(operation.operationId)?.toSnapshot()
        second.close()

        assertNotNull(restored)
        assertEquals(operation, restored)
        assertEquals(5_000_000_000L, restored?.expectedBytes)
        assertEquals(4_000_000_000L, restored?.bytesCompleted)
    }

    @Test
    fun compareAndSetRejectsStaleExpectedState() {
        val operation = operation()
        val database = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
        val dao = database.operationDao()
        dao.insert(OperationEntity.from(operation))

        assertEquals(
            1,
            dao.compareAndSetState(
                operationId = operation.operationId,
                expectedState = OperationState.PLANNED.name,
                nextState = OperationState.TRANSFERRING.name,
                nextStage = TransferStage.TRANSFERRING.name,
                sourceDeleteState = SourceDeleteState.NOT_REQUIRED.name,
                updatedAtEpochMillis = 2L,
            ),
        )
        assertEquals(
            0,
            dao.compareAndSetState(
                operationId = operation.operationId,
                expectedState = OperationState.PLANNED.name,
                nextState = OperationState.VERIFYING.name,
                nextStage = TransferStage.VERIFYING.name,
                sourceDeleteState = SourceDeleteState.NOT_REQUIRED.name,
                updatedAtEpochMillis = 3L,
            ),
        )
        assertEquals(OperationState.TRANSFERRING, dao.find(operation.operationId)?.toSnapshot()?.state)
        database.close()
    }

    @Test
    fun nonTerminalQueryExcludesTerminalTruth() {
        val active = operation("active")
        val complete = operation("complete").copy(
            state = OperationState.COMPLETE,
            stage = TransferStage.COMPLETE,
        )
        val database = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
        database.operationDao().insert(OperationEntity.from(active))
        database.operationDao().insert(OperationEntity.from(complete))

        val activeRows = database.operationDao().findNonTerminal()
        database.close()

        assertEquals(listOf("active"), activeRows.map { it.operationId })
    }

    @Test
    fun versionOneSchemaCanBeCreatedAndValidatedForFutureMigrations() {
        migrationHelper.createDatabase(databaseName, 1).close()
        val validated = migrationHelper.runMigrationsAndValidate(databaseName, 1, true)
        validated.close()
        assertTrue(true)
    }

    private fun operation(id: String = "operation") = OperationSnapshot(
        operationId = id,
        batchId = "batch-1",
        type = OperationType.COPY,
        state = OperationState.PLANNED,
        stage = TransferStage.PLANNED,
        source = DurableLocator(ProviderId("local"), "root-relative", "source.bin"),
        destinationParent = DurableLocator(ProviderId("local"), "root-relative", "destination"),
        intendedFinalName = "source.bin",
        partialDestination = null,
        expectedBytes = 5_000_000_000L,
        bytesCompleted = 4_000_000_000L,
        sourceVersion = "size:5000000000",
        verificationDescription = null,
        finalizationDescription = null,
        sourceDeleteState = SourceDeleteState.NOT_REQUIRED,
        cancellationRequested = false,
        errorCode = null,
        errorMessage = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
