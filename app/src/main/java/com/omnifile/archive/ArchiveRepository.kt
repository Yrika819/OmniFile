package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.StorageEntry

class ArchiveRepository(
    private val filesRepository: FilesRepository,
) {
    suspend fun open(container: StorageEntry): ArchiveOpenResult {
        if (!ArchiveSupport.isZip(container)) return ArchiveOpenResult.Failure(ArchiveError.NotAnArchive)
        val archive = ArchiveContainer(container)
        return ZipArchiveReader.read(
            open = { filesRepository.openSequentialRead(container) },
            container = archive,
        )
    }
}
