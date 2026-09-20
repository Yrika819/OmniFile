package com.omnifile

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
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

    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val returned = result.data ?: return@registerForActivityResult
        val uri = returned.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK || !DocumentsContract.isTreeUri(uri)) return@registerForActivityResult
        val destinationPicker = filesViewModel.isDestinationPicker
        if (grantStore.persistGrant(uri, returned.flags) &&
            (destinationPicker || grantStore.rememberSelectedReadTree(uri))
        ) {
            if (destinationPicker) {
                filesViewModel.selectDestinationSaf(uri)
            } else {
                filesViewModel.selectSaf(uri)
            }
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
        grantStore.restoredGrants().forEach { grant ->
            val provider = SafStorageProvider(
                contentResolver = contentResolver,
                treeUri = grant.uri,
                id = SafStorageProvider.providerIdFor(grant.uri),
                grantFlags = grant.modeFlags,
            )
            repository.register(provider)
            operationManager.registerProvider(provider)
        }
        operationsViewModel = ViewModelProvider(this, OperationsViewModelFactory {
            OperationsViewModel(operationStore, operationManager)
        })[OperationsViewModel::class.java]
        filesViewModel = ViewModelProvider(this, FilesViewModelFactory {
            FilesViewModel(
                repository = repository,
                localProviderId = localProviderId,
                safProviderFor = { uri ->
                    val grant = grantStore.grantFor(uri)
                        ?: throw SecurityException("No persisted SAF grant")
                    SafStorageProvider(
                        contentResolver,
                        uri,
                        SafStorageProvider.providerIdFor(uri),
                        grant.modeFlags,
                    )
                },
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
                            onPickTree = ::launchTreePicker,
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
                            onSelectLocalDestination = filesViewModel::selectDestinationLocal,
                            onPickDestinationTree = ::launchTreePicker,
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
            LaunchedEffect(Unit) {
                filesViewModel.restorePersistedSaf()
                operationManager.reconcileNonTerminal()
                operationsViewModel.refresh()
            }
        }
    }

    private fun launchTreePicker() {
        val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            if (filesViewModel.isDestinationPicker) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(accessFlags)
        })
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
