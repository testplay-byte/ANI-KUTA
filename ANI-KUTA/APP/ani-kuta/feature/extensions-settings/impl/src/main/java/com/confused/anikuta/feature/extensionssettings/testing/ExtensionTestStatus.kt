package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ROUND 96 (D-660) — THE EXTENSION-STATUS VERDICTS, shared by the testing
 * page's new Options section and the Extensions page's status dots.
 *
 * The user's spec: while "Show Status on Extensions" is ON, every trusted
 * extension row on the Extensions page carries a small COLOR DOT at the very
 * right of the delete button, colored by its LATEST persisted testing
 * verdict — "the status will be shown in a cleaner way, like no pass text
 * will be shown or anything like that… the colors will be shown. There are
 * two colors for pass, and there are two colors for fail, and there are two
 * colors for new." Those six colors are the testing palette's existing
 * per-verdict, per-system pairs (D-594): Aniyomi pass = emerald, CloudStream
 * pass = sky; Aniyomi fail = red, CloudStream fail = orange; the two grays
 * for never-tested — the SAME hues the suite-health ring and its legend
 * already use, so the dot speaks the app's established color language.
 *
 * THE VERDICT RULES (one stored run per target id — the store keeps only
 * the latest): a finished, non-aborted run with zero failures and at least
 * one pass is PASS; any run holding a FAILED result is FAIL; everything
 * else (never tested, aborted mid-run, nothing decided yet) reads as the
 * neutral "new" gray — an aborted run is not a failure, and it is not a
 * pass either.
 *
 * THE ROW AGGREGATION: an extension (or plugin) maps to the SOURCE IDS it
 * contributes to the testing system — aniyomi extensions expose their
 * [com.confused.anikuta.data.extension.model.AnimeExtension.Installed.sources]
 * ids directly; CloudStream plugins re-derive each provider's STABLE
 * synthetic id through [CsSourceIds.idFor] (the same deterministic mint the
 * bridge registers, so the ids match the stored runs bit-for-bit). Any
 * failing source fails the row; otherwise any healthy source passes it;
 * otherwise the row is "new". Only TRUSTED rows render a dot — untrusted,
 * errored and available rows carry no source identity the testing system
 * can see (and are untestable in those states anyway).
 */

/** The three dot verdicts, in the palette's own vocabulary. */
internal enum class ExtensionTestVerdict {
    PASS,
    FAIL,
    NEW,
}

/** The latest stored run's own verdict (PASS / FAIL / neutral-NEW). */
internal fun ExtensionTestResultStore.StoredTargetRun.verdict(): ExtensionTestVerdict {
    val failed = results.values.any { it.status == TestStatus.FAILED }
    val passed = results.values.any { it.status == TestStatus.PASSED }
    return when {
        failed -> ExtensionTestVerdict.FAIL
        finished && !abortedByUser && !failed && passed -> ExtensionTestVerdict.PASS
        else -> ExtensionTestVerdict.NEW
    }
}

/**
 * One extension row's aggregated verdict across all the source ids it owns.
 * [runs] is the store's latest-per-target map; [sourceIds] may be empty (the
 * extension contributes no testable source — a disabled extension keeps its
 * ids, an untrusted one never had them in the registry).
 */
internal fun aggregateVerdict(
    runs: Map<Long, ExtensionTestResultStore.StoredTargetRun>,
    sourceIds: List<Long>,
): ExtensionTestVerdict {
    if (sourceIds.isEmpty()) return ExtensionTestVerdict.NEW
    val verdicts = sourceIds.mapNotNull { runs[it] }
    if (verdicts.isEmpty()) return ExtensionTestVerdict.NEW
    if (verdicts.any { it.verdict() == ExtensionTestVerdict.FAIL }) return ExtensionTestVerdict.FAIL
    if (verdicts.any { it.verdict() == ExtensionTestVerdict.PASS }) return ExtensionTestVerdict.PASS
    return ExtensionTestVerdict.NEW
}

/** The palette's verdict color for one system (the ring + legend pair). */
internal fun verdictColor(verdict: ExtensionTestVerdict, ecosystem: TestEcosystem): Color =
    when (verdict) {
        ExtensionTestVerdict.PASS -> if (ecosystem == TestEcosystem.ANIYOMI) TestingPalette.PassA else TestingPalette.PassB
        ExtensionTestVerdict.FAIL -> if (ecosystem == TestEcosystem.ANIYOMI) TestingPalette.FailA else TestingPalette.FailB
        ExtensionTestVerdict.NEW -> if (ecosystem == TestEcosystem.ANIYOMI) TestingPalette.NewA else TestingPalette.NewB
    }

/**
 * THE STATUS DOT — a quiet 9dp circle in the verdict's system color, placed
 * by the caller just right of the row's delete button. No text, no border,
 * no ring: the color IS the status (the suite-health legend already teaches
 * the vocabulary).
 */
@Composable
internal fun TestStatusDot(
    verdict: ExtensionTestVerdict,
    ecosystem: TestEcosystem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(verdictColor(verdict, ecosystem)),
    )
}
