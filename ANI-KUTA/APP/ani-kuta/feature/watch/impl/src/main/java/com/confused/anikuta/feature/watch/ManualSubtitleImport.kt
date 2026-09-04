package com.confused.anikuta.feature.watch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * D-407 (round 31): the MANUAL SUBTITLE IMPORT plumbing for the watch
 * screen — the SAF multi-file picker contract + the display-name resolver.
 *
 * The report: *"add a permanent option there: the option to add subtitles
 * manually. When the user clicks that option, he will be led to the device's
 * file picker, where the user can pick any kind of subtitle files (VTT, SRT,
 * or any other relevant ones). After selecting those files, those subtitles
 * will start to show up properly."*
 *
 * @see com.confused.anikuta.core.download.DownloadManager.importManualSubtitle
 *   for the persistence half (downloaded episodes keep the file in their
 *   dedicated `subtitles/` folder + DB + `.data.json`).
 */

/**
 * The multi-select SAF subtitle picker: `ACTION_OPEN_DOCUMENT` +
 * `EXTRA_ALLOW_MULTIPLE`, the ANY-file type (deliberately — see the note)
 * with EXTRA_MIME_TYPES hinting the known subtitle mime types.
 *
 * ## Why the ANY-file type + post-pick validation (not a mime filter)
 * SAF documents frequently carry no better mime than
 * `application/octet-stream` (most .srt/.ass files on internal storage) — a
 * mime-filtered picker would HIDE the user's subtitle files. The contract
 * therefore shows every file; [com.confused.anikuta.core.download.SUBTITLE_EXTENSIONS]
 * validates the picked extensions and the caller toasts on rejects.
 *
 * Returns EVERY picked `content://` URI (single or multiple — the clip data
 * carries both); an empty list on cancel.
 */
class PickSubtitleFiles : ActivityResultContract<Array<String>, List<Uri>>() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            // A soft hint for pickers that respect it — never a hard filter
            // (see the class doc: octet-stream subtitle files must stay
            // selectable).
            putExtra(Intent.EXTRA_MIME_TYPES, input)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData ?: return listOfNotNull(intent.data)
        return buildList {
            for (i in 0 until clip.itemCount) {
                add(clip.getItemAt(i).uri)
            }
        }
    }
}

/** The mime-type hints passed to [PickSubtitleFiles] (display-order only). */
val SUBTITLE_MIME_HINTS = arrayOf(
    "application/x-subrip", // .srt
    "text/vtt", // .vtt
    "text/plain", // .vtt/.srt as plain text
    "application/x-ass", // .ass
    "application/x-ssa", // .ssa
    "application/octet-stream", // the realistic mime of most subtitle files
)

/**
 * Resolves a picked document's DISPLAY NAME (e.g. "demon slayer - 01.srt")
 * via the SAF columns query; falls back to the URI's last path segment.
 * IO-safe (call from Dispatchers.IO); `null` when nothing derivable.
 */
suspend fun resolveSubtitleDisplayName(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
                ?: uri.lastPathSegment
        }.getOrNull()
    }
