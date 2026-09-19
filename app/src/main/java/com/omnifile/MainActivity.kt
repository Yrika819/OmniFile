package com.omnifile

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.room.Room
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.omnifile.files.FilesRepository
import com.omnifile.files.FilesUiState
import com.omnifile.files.FilesViewModel
import com.omnifile.operations.OperationManager
import com.omnifile.operations.OperationsViewModel
import com.omnifile.operations.persistence.OperationDatabase
import com.omnifile.operations.persistence.OperationStore
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.SafTreeGrantStore
import com.omnifile.ui.files.FilesScreen
import com.omnifile.ui.operations.OperationsPanel
import com.omnifile.ui.theme.OmniFileTheme

class MainActivity : ComponentActivity() {
    private lateinit var filesViewModel: FilesViewModel
    private lateinit var grantStore: SafTreeGrantStore
    private lateinit var operationDatabase: OperationDatabase
    private lateinit var operationManager: OperationManager
    private lateinit var operationsViewModel: OperationsViewModel

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && grantStore.persistReadGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)) {
            filesViewModel.selectSaf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        grantStore = SafTreeGrantStore(applicationContext)
        val localProviderId = ProviderId("local-app-files")
        val localProvider = LocalStorageProvider(filesDir.toPath(), localProviderId)
        val repository = FilesRepository(mapOf(localProviderId to localProvider))
        operationDatabase = Room.databaseBuilder(
            applicationContext,
            OperationDatabase::class.java,
            "operations.db",
        ).build()
        val operationStore = OperationStore(operationDatabase.operationDao())
        operationManager = OperationManager(operationStore, mapOf(localProviderId to localProvider))
        operationsViewModel = ViewModelProvider(this, OperationsViewModelFactory {
            OperationsViewModel(operationStore, operationManager)
        })[OperationsViewModel::class.java]
        filesViewModel = ViewModelProvider(this, FilesViewModelFactory {
            FilesViewModel(
                repository = repository,
                localProviderId = localProviderId,
                safProviderFor = { uri -> SafStorageProvider(contentResolver, uri, ProviderId("saf-tree")) },
                restoredSafUri = grantStore::restoredReadTree,
                operationManager = operationManager,
                onOperationsCreated = { operationsViewModel.refresh() },
            )
        })[FilesViewModel::class.java]
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (filesViewModel.uiState.value is FilesUiState.SourceSelection) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else {
                    filesViewModel.handleBack()
                }
            }
        })
        setContent {
            val state by filesViewModel.uiState.collectAsState()
            val mutationInFlight by filesViewModel.mutationInFlight.collectAsState()
            OmniFileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FilesScreen(
                            modifier = Modifier.weight(1f),
                            state = state,
                        onSelectLocal = filesViewModel::selectLocal,
                        onPickTree = { treePicker.launch(null) },
                        onOpenDirectory = filesViewModel::openDirectory,
                        onEnterSelection = filesViewModel::enterSelection,
                        onToggleSelection = filesViewModel::toggleSelection,
                        onClearSelection = filesViewModel::clearSelection,
                            onRenameSelected = filesViewModel::renameSelected,
                            onDeleteSelected = filesViewModel::deleteSelected,
                            onCopySelected = filesViewModel::copySelectedToCurrentDirectory,
                            onMoveSelected = filesViewModel::moveSelectedToCurrentDirectory,
                            onOpenDestinationDirectory = filesViewModel::openDestinationDirectory,
                            onConfirmDestination = filesViewModel::confirmDestination,
                            onCancelDestination = filesViewModel::cancelDestinationPicker,
                            mutationInFlight = mutationInFlight,
                            onBack = filesViewModel::handleBack,
                            onRetry = filesViewModel::retry,
                        )
                        OperationsPanel(
                            operations = operationsViewModel.operations.collectAsState().value,
                            onCancel = operationsViewModel::cancel,
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { filesViewModel.restorePersistedSaf() }
        }
    }

    override fun onDestroy() {
        operationDatabase.close()
        super.onDestroy()
    }
}

private class OperationsViewModelFactory(
    private val create: () -> OperationsViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

private class FilesViewModelFactory(
    private val create: () -> FilesViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
