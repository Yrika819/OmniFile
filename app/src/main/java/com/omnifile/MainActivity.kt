package com.omnifile

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.omnifile.files.FilesUiState
import com.omnifile.files.FilesViewModel
import com.omnifile.search.SearchScope
import com.omnifile.search.SearchUiState
import com.omnifile.search.SearchViewModel
import com.omnifile.ui.files.FilesScreen
import com.omnifile.ui.home.HomeUiState
import com.omnifile.ui.home.HomeViewModel
import com.omnifile.ui.home.HomeScreen
import com.omnifile.ui.music.MusicScreen
import com.omnifile.ui.operations.OperationsPanel
import com.omnifile.ui.search.SearchScreen
import com.omnifile.ui.settings.SettingsScreen
import com.omnifile.ui.shell.AppNavigationState
import com.omnifile.ui.shell.AppShell
import com.omnifile.ui.shell.DetailSurface
import com.omnifile.ui.shell.FilesOrigin
import com.omnifile.ui.shell.TopLevelDestination
import com.omnifile.ui.theme.OmniFileTheme

class MainActivity : ComponentActivity() {
    private lateinit var app: AppContainer
    private lateinit var filesViewModel: FilesViewModel
    private lateinit var searchViewModel: SearchViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var operationsViewModel: com.omnifile.operations.OperationsViewModel
    private var navigation by mutableStateOf(AppNavigationState())

    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val returned = result.data ?: return@registerForActivityResult
        val uri = returned.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK || !DocumentsContract.isTreeUri(uri)) return@registerForActivityResult
        val destinationPicker = filesViewModel.isDestinationPicker
        if (app.grantStore.persistGrant(uri, returned.flags)) {
            if (destinationPicker) {
                filesViewModel.selectDestinationSaf(uri)
            } else {
                app.safProviderFor(uri)
                app.repository.register(app.safProviderFor(uri))
                filesViewModel.selectSaf(uri)
                homeViewModel.refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        app = (application as OmniFileApplication).container
        val restoredTopLevel = savedInstanceState?.getString(KEY_TOP_LEVEL)?.let { value ->
            runCatching { TopLevelDestination.valueOf(value) }.getOrNull()
        } ?: TopLevelDestination.HOME
        val restoredDetail = savedInstanceState?.getString(KEY_DETAIL)?.let {
            runCatching { DetailSurface.valueOf(it) }.getOrNull()
        }
        val restoredFilesOrigin = savedInstanceState?.getString(KEY_FILES_ORIGIN)?.let {
            runCatching { FilesOrigin.valueOf(it) }.getOrNull()
        }
        navigation = AppNavigationState(restoredTopLevel, restoredDetail, restoredFilesOrigin)

        operationsViewModel = ViewModelProvider(this, Factory { com.omnifile.operations.OperationsViewModel(app.operationStore, app.operationManager) })[com.omnifile.operations.OperationsViewModel::class.java]
        filesViewModel = ViewModelProvider(this, Factory {
            FilesViewModel(
                repository = app.repository,
                localProviderId = app.localProvider.id,
                safProviderFor = app::safProviderFor,
                restoredSafUri = app.grantStore::restoredReadTree,
                operationManager = app.operationManager,
                onOperationsCreated = { operationsViewModel.refresh() },
            )
        })[FilesViewModel::class.java]
        searchViewModel = ViewModelProvider(this, Factory {
            SearchViewModel(
                listChildren = app.repository::children,
                resolveRoots = { scope ->
                    when (scope) {
                        SearchScope.ThisDevice -> {
                            val snapshot = app.rootRegistry.snapshot()
                            com.omnifile.search.SearchRootResolution(
                                roots = snapshot.aggregationRoots.map { it.entry },
                                failures = snapshot.failures.map { failure ->
                                    com.omnifile.search.SearchRootFailure(failure.id, failure.label, failure.error)
                                },
                                rootLabels = snapshot.aggregationRoots.associate { it.entry.ref.identityKey to it.label },
                            )
                        }
                        is SearchScope.CurrentFolder -> com.omnifile.search.SearchRootResolution(
                            roots = listOf(scope.directory),
                            rootLabels = mapOf(scope.directory.ref.identityKey to scope.directory.displayName),
                        )
                    }
                },
            )
        })[SearchViewModel::class.java]
        homeViewModel = ViewModelProvider(this, Factory { HomeViewModel(app.rootRegistry) })[HomeViewModel::class.java]

        if (navigation.detail != null && filesViewModel.uiState.value == FilesUiState.SourceSelection) {
            // A process-death recreation cannot restore an in-memory Files path safely.
            navigation = navigation.closeDetail()
        }

        if (navigation.topLevel == TopLevelDestination.SEARCH && navigation.detail == null) {
            searchViewModel.restoreRequest(SearchScope.ThisDevice, savedInstanceState?.getString(KEY_SEARCH_QUERY).orEmpty())
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!consumeBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        setContent {
            val filesState by filesViewModel.uiState.collectAsState()
            val searchState by searchViewModel.state.collectAsState()
            val homeState by homeViewModel.state.collectAsState()
            val operations by operationsViewModel.operations.collectAsState()
            val mutationInFlight by filesViewModel.mutationInFlight.collectAsState()
            val currentNavigation = navigation
            val currentTop = currentNavigation.topLevel
            val currentDetail = currentNavigation.detail
            val showNavigation = currentDetail == null
            OmniFileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell(
                        selected = currentTop,
                        showPrimaryNavigation = showNavigation,
                        onDestinationSelected = ::selectTopLevel,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                when (currentDetail) {
                                    DetailSurface.FILES -> FilesSurface(filesState, mutationInFlight)
                                    DetailSurface.CONTEXTUAL_SEARCH -> SearchSurface(searchState, contextual = true)
                                    null -> when (currentTop) {
                                        TopLevelDestination.HOME -> HomeScreen(homeState, ::openRoot, ::launchTreePicker)
                                        TopLevelDestination.SEARCH -> SearchSurface(searchState, contextual = false)
                                        TopLevelDestination.MUSIC -> MusicScreen()
                                        TopLevelDestination.SETTINGS -> SettingsScreen((homeState as? HomeUiState.Ready)?.snapshot)
                                    }
                                }
                            }
                            OperationsPanel(
                                operations = operations,
                                onCancel = operationsViewModel::cancel,
                                applyNavigationBarsPadding = !showNavigation,
                            )
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                app.reconcileOperationsOnce()
                operationsViewModel.refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::homeViewModel.isInitialized) homeViewModel.refresh()
    }

    @Composable
    private fun FilesSurface(state: FilesUiState, mutationInFlight: Boolean) {
        FilesScreen(
            state = state,
            onSelectLocal = filesViewModel::selectLocal,
            onPickTree = ::launchTreePicker,
            onOpenDirectory = filesViewModel::openDirectory,
            onOpenSearch = ::openContextualSearch,
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
            onBack = ::consumeFilesBack,
            onRetry = filesViewModel::retry,
        )
    }

    @Composable
    private fun SearchSurface(state: SearchUiState, contextual: Boolean) {
        SearchScreen(
            state = state,
            onBack = if (contextual) ::closeContextualSearch else ::finish,
            onQueryChanged = searchViewModel::queryChanged,
            onSubmitQuery = searchViewModel::submitQuery,
            onClearQuery = searchViewModel::clearQuery,
            onOpenResult = ::openSearchResult,
        )
    }

    private fun selectTopLevel(destination: TopLevelDestination) {
        if (destination == navigation.topLevel && navigation.detail == null) return
        navigation = navigation.selectTopLevel(destination)
        if (destination == TopLevelDestination.SEARCH && searchViewModel.scope != SearchScope.ThisDevice) {
            searchViewModel.setScope(SearchScope.ThisDevice)
        }
    }

    private fun openRoot(root: com.omnifile.storage.SupportedRoot) {
        filesViewModel.openRoot(root.entry)
        navigation = navigation.openFiles(FilesOrigin.HOME)
    }

    private fun openContextualSearch() {
        val scope = filesViewModel.currentSearchScope() ?: return
        searchViewModel.setScope(scope)
        navigation = navigation.openContextualSearch()
    }

    private fun closeContextualSearch() {
        searchViewModel.stop()
        navigation = navigation.returnToFilesFromContextualSearch()
    }

    private fun openSearchResult(hit: com.omnifile.search.SearchHit) {
        if (!filesViewModel.openSearchResult(hit)) return
        navigation = navigation.openFiles(
            when {
                navigation.detail == DetailSurface.CONTEXTUAL_SEARCH -> FilesOrigin.CONTEXTUAL_SEARCH
                navigation.topLevel == TopLevelDestination.SEARCH -> FilesOrigin.TOP_LEVEL_SEARCH
                else -> FilesOrigin.HOME
            },
        )
    }

    private fun consumeFilesBack(): Boolean {
        if (filesViewModel.isDestinationPicker || filesViewModel.isSelectionMode || !filesViewModel.isAtProviderRoot()) {
            filesViewModel.handleBack()
            return true
        }
        filesViewModel.goBack()
        navigation = navigation.closeFilesAtRoot()
        return true
    }

    private fun consumeBack(): Boolean = when (navigation.detail) {
        DetailSurface.FILES -> consumeFilesBack()
        DetailSurface.CONTEXTUAL_SEARCH -> {
            closeContextualSearch()
            true
        }
        null -> false
    }

    private fun launchTreePicker() {
        val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            if (filesViewModel.isDestinationPicker) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(accessFlags) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TOP_LEVEL, navigation.topLevel.name)
        outState.putString(KEY_DETAIL, navigation.detail?.name)
        outState.putString(KEY_FILES_ORIGIN, navigation.filesOrigin?.name)
        outState.putString(KEY_SEARCH_QUERY, searchViewModel.query)
        super.onSaveInstanceState(outState)
    }

    private inline fun <reified T : Enum<T>> valueOfOrNull(value: String): T? = runCatching { enumValueOf<T>(value) }.getOrNull()

    companion object {
        private const val KEY_TOP_LEVEL = "omnifile.top-level"
        private const val KEY_DETAIL = "omnifile.detail"
        private const val KEY_FILES_ORIGIN = "omnifile.files-origin"
        private const val KEY_SEARCH_QUERY = "omnifile.search-query"
    }
}

private class Factory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
