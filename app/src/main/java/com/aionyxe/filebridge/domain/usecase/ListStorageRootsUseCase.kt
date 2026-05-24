package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.storage.StorageLocation
import com.aionyxe.filebridge.data.storage.StorageRepository
import javax.inject.Inject

class ListStorageRootsUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
) {
    operator fun invoke(): List<StorageLocation> = storageRepository.listStorageRoots()
}
