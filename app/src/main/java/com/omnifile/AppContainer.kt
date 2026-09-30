package com.omnifile

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.room.Room
import com.omnifile.files.FilesRepository
import com.omnifile.archive.ArchiveExtractor
import com.omnifile.archive.ArchiveRepository
import com.omnifile.operations.OperationManager
import com.omnifile.operations.OperationRepository
import com.omnifile.operations.persistence.OperationDatabase
import com.omnifile.operations.persistence.OperationStore
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.SafRootCandidate
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.SafTreeGrantStore
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageTransferProvider
import com.omnifile.storage.SupportedRootRegistry
import com.omnifile.preview.AndroidPdfRendererClientFactory
import com.omnifile.preview.PdfPreviewController
import com.omnifile.preview.PdfSnapshotStore
import com.omnifile.preview.PreviewEngine

class OmniFileApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(PDF_INIT_TAG, "application_create_started")
        if (isPdfRendererProcess()) {
            Log.i(PDF_INIT_TAG, "worker_process_skip_graph")
            return
        }
        Log.i(PDF_INIT_TAG, "normal_graph_initialization_started")
        val snapshots = PdfSnapshotStore(noBackupFilesDir)
        container = AppContainer(this, snapshots)
        Log.i(PDF_INIT_TAG, "normal_graph_initialization_finished")
    }

    /** Exact manifest process name check; the isolated worker skips the normal app graph. */
    private fun isPdfRendererProcess(): Boolean =
        isPdfRendererProcessName(Application.getProcessName(), packageName)
}

private const val PDF_INIT_TAG = "OmniPdfInit"

internal fun isPdfRendererProcessName(processName: String, packageName: String): Boolean =
    processName == "$packageName:pdf_renderer"

class AppContainer(
    context: Context,
    pdfSnapshots: PdfSnapshotStore = PdfSnapshotStore(context.applicationContext.noBackupFilesDir),
) {
    private val appContext = context.applicationContext
    val grantStore = SafTreeGrantStore(appContext)
    val localProvider = LocalStorageProvider(
        appContext.filesDir.toPath(),
        com.omnifile.storage.ProviderId("local-app-files"),
    )
    val repository = FilesRepository(mapOf(localProvider.id to localProvider))
    val archiveRepository = ArchiveRepository(repository)
    val archiveExtractor = ArchiveExtractor(repository)
    val previewEngine = PreviewEngine(
        PdfPreviewController(pdfSnapshots, AndroidPdfRendererClientFactory(appContext)),
    )

    /** Process-scoped guard for the one notification-permission prompt on first Play. */
    var notificationPermissionRequested = false

    /** Process-scoped single playback entry point; survives Activity recreation. */
    val playbackCoordinator = com.omnifile.media.PlaybackCoordinator(
        appContext,
        repository::playbackSourceProvider,
    )
    val operationDatabase: OperationDatabase = Room.databaseBuilder(
        appContext,
        OperationDatabase::class.java,
        "operations.db",
    ).build()
    val operationStore: OperationStore = OperationStore(operationDatabase.operationDao())
    lateinit var operationManager: OperationManager
        private set
    val rootRegistry: SupportedRootRegistry

    private val safProviders = mutableMapOf<String, SafStorageProvider>()
    private var reconciliationStarted = false

    init {
        val transferProviders = mutableMapOf<com.omnifile.storage.ProviderId, StorageTransferProvider>()
        (localProvider as? StorageTransferProvider)?.let { transferProviders[it.id] = it }
        val candidates = buildSafCandidates()
        candidates.forEach { candidate ->
            candidate.provider?.let { provider ->
                repository.register(provider)
                (provider as? StorageTransferProvider)?.let { transferProviders[it.id] = it }
            }
        }
        operationManager = OperationManager(operationStore, transferProviders)
        rootRegistry = SupportedRootRegistry(
            localProvider = localProvider,
            safCandidates = ::buildSafCandidates,
            contentResolver = appContext.contentResolver,
        )
    }

    fun safProviderFor(uri: Uri): StorageProvider {
        val key = uri.toString()
        return safProviders[key] ?: run {
            val grant = grantStore.grantFor(uri) ?: throw SecurityException("No persisted SAF grant")
            val provider = SafStorageProvider(
                contentResolver = appContext.contentResolver,
                treeUri = uri,
                id = SafStorageProvider.providerIdFor(uri),
                grantFlags = grant.modeFlags,
            )
            safProviders[key] = provider
            repository.register(provider)
            if (this@AppContainer::operationManager.isInitialized) {
                (provider as? StorageTransferProvider)?.let { operationManager.registerProvider(it) }
            }
            provider
        }
    }

    suspend fun reconcileOperationsOnce() {
        if (reconciliationStarted) return
        synchronized(this) {
            if (reconciliationStarted) return
            reconciliationStarted = true
        }
        operationManager.reconcileNonTerminal()
    }

    fun currentRootCandidates(): List<SafRootCandidate> = buildSafCandidates()

    private fun buildSafCandidates(): List<SafRootCandidate> {
        val current = grantStore.restoredGrants().associateBy { it.uri.toString() }
        val uris = (current.keys.map(Uri::parse) + grantStore.candidateTreeUris()).distinctBy { it.toString() }
        return uris.distinctBy { it.toString() }.map { uri ->
            val grant = current[uri.toString()] ?: grantStore.grantFor(uri)
            if (grant?.canRead != true) {
                SafRootCandidate(
                    id = SupportedRootRegistry.safRootId(uri),
                    label = "SAF folder",
                    uri = uri,
                    provider = null,
                    initialError = com.omnifile.storage.StorageError.PermissionDenied,
                )
            } else {
                val provider = try {
                    val existing = safProviders[uri.toString()]
                    existing ?: run {
                        val created = SafStorageProvider(
                            contentResolver = appContext.contentResolver,
                            treeUri = uri,
                            id = SafStorageProvider.providerIdFor(uri),
                            grantFlags = grant.modeFlags,
                        )
                        safProviders[uri.toString()] = created
                        created
                    }
                } catch (_: SecurityException) {
                    null
                }
                SafRootCandidate(
                    id = SupportedRootRegistry.safRootId(uri),
                    label = provider?.let { "SAF · ${uri.lastPathSegment ?: "folder"}" } ?: "SAF folder",
                    uri = uri,
                    provider = provider,
                )
            }
        }
    }
}
