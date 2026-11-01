package com.confused.anikuta.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats a count with US-grouped thousands separators ("1,838") — the ONE
 * formatter for every user-facing count in the app (the backup/restore
 * stats, the toasts, the onboarding restore summary). Locale-pinned so the
 * grouping is consistent with the rest of the UI's US-pinned dates.
 */
fun formatCount(n: Int): String = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US)).format(n)
