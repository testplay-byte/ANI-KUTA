// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

import java.net.URLDecoder
import java.net.URLEncoder

object StringUtils {
    fun String.decodeUrl(): String = URLDecoder.decode(this, Charsets.UTF_8)

    fun String.encodeUrl(): String = URLEncoder.encode(this, Charsets.UTF_8)

    @Deprecated(
        message = "Use Ktor 'Url' naming convention instead.",
        replaceWith = ReplaceWith("this.encodeUrl()"),
        level = DeprecationLevel.WARNING,
    )
    fun String.encodeUri(): String = encodeUrl()

    @Deprecated(
        message = "Use Ktor 'Url' naming convention instead.",
        replaceWith = ReplaceWith("this.decodeUrl()"),
        level = DeprecationLevel.WARNING,
    )
    fun String.decodeUri(): String = decodeUrl()
}
