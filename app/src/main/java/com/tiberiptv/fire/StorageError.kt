package com.tiberiptv.fire

sealed class StorageError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object NoUsbDetected : StorageError("Aucun stockage USB détecté")
    data object SafNotAvailable : StorageError("Sélecteur de dossier indisponible")
    data object PermissionDenied : StorageError("Permission stockage refusée")
    data class WriteFailed(val error: Throwable) : StorageError("Écriture stockage impossible", error)
}
