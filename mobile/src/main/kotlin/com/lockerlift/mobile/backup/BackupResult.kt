package com.lockerlift.mobile.backup

/** Sealed result type returned by [LocalBackupManager] operations. */
sealed class BackupResult {
    /** Export or restore completed successfully. [fileName] carries the written filename (or "Restore complete"). */
    data class Success(val fileName: String) : BackupResult()

    /** Operation failed with [message] describing the cause. */
    data class Error(val message: String) : BackupResult()
}
