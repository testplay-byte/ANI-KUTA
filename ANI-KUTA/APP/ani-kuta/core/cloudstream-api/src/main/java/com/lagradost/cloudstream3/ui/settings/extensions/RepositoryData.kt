// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — Ultima reads RepositoryData.getUrl() (and the plugins'
// RepositoryManager surface passes it around). The upstream file also declares
// REPOSITORIES_KEY (its DataStore key); carried along for shape parity.
package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One saved repository (iconUrl + name + url; the two-arg constructor mirrors upstream). */
@Serializable
data class RepositoryData(
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String?,
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("url") @SerialName("url") val url: String,
) {
    constructor(name: String, url: String) : this(null, name, url)
}

const val REPOSITORIES_KEY = "REPOSITORIES_KEY"
