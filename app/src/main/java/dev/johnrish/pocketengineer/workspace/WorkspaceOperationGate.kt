package dev.johnrish.pocketengineer.workspace

/**
 * Tracks the newest asynchronous operation for the selected workspace.
 *
 * ViewModel callers use this on the main thread. Refresh and file-read generations are separate so
 * refreshing the listing does not invalidate a file preview that is already being opened.
 */
internal class WorkspaceOperationGate {
    internal data class Token(
        val workspaceVersion: Long,
        val operationVersion: Long
    )

    private var workspaceVersion = 0L
    private var refreshVersion = 0L
    private var readVersion = 0L

    fun workspaceChanged() {
        workspaceVersion++
        refreshVersion++
        readVersion++
    }

    fun beginRefresh(): Token = Token(workspaceVersion, ++refreshVersion)

    fun isCurrentRefresh(token: Token): Boolean =
        token.workspaceVersion == workspaceVersion && token.operationVersion == refreshVersion

    fun beginRead(): Token = Token(workspaceVersion, ++readVersion)

    fun isCurrentRead(token: Token): Boolean =
        token.workspaceVersion == workspaceVersion && token.operationVersion == readVersion
}
