package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.storage.StorageRepository
import javax.inject.Inject

class IsSdCardPresentUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
) {
    operator fun invoke(): Boolean = storageRepository.isSdCardPresent()
}
