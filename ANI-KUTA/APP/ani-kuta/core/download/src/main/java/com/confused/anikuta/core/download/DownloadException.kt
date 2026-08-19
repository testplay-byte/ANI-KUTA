package com.confused.anikuta.core.download

/**
 * Base exception for all download-related errors.
 *
 * Thrown by [DownloadManager], [DownloadQueue], [HttpDownloader], [HlsDownloader],
 * etc. when a download operation fails. Caught by the queue's error handler →
 * sets the task's state to ERROR + stores [message] as `last_error`.
 *
 * Subclasses:
 *  - [HttpException] — HTTP-level errors (4xx/5xx) with the status code.
 *  - (future) [HlsException] — HLS-specific errors (playlist parse, segment fetch).
 */
open class DownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
