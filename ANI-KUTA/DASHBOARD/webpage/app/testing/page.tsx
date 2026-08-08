"use client";

import { Card, CardHeader } from "@/components/Card";
import { TestingChecklist } from "@/components/TestingChecklist";

/* ---------------------------------------------------------------------------
 * Static data — the test plan items, log capture table, concerns.
 * Kept inline (not in lib/data.ts) because this is the only page that uses
 * them and bundling them keeps the page self-contained.
 * ------------------------------------------------------------------------- */

const PREREQS = [
  {
    text: "Download the APK artifact from GitHub Actions run 31228426792",
    detail:
      "https://github.com/testplay-byte/ANI-KUTA/actions/runs/31228426792 → Artifacts → anikuta-apk.zip",
  },
  {
    text: "Uninstall any previous ANI-KUTA build (clean state)",
    detail:
      "Settings → Apps → ANI-KUTA → Uninstall. This avoids stale DB schema conflicts.",
  },
  {
    text: "Install the new APK",
    detail:
      "adb install app-arm64-v8a-debug.apk (or tap the APK in your file manager)",
  },
  {
    text: "Open the app and grant notification permission + select download folder when prompted",
    detail:
      "The first-run dialog asks for both. Both must be granted for downloads to work.",
  },
  {
    text: "Have an anime in mind with known episodes for testing",
    detail:
      "Pick one from your library or browse. You'll need one with 5+ episodes for a thorough test.",
  },
  {
    text: "(Optional) Connect device via USB for logcat",
    detail:
      "adb logcat enables real-time log capture. See the Log Capture Guide below.",
  },
];

const FIX1_METADATA = [
  {
    text: "Open an anime from the Browse/Library page",
    detail:
      "Navigate to an anime details page. Note the episode titles, descriptions, and thumbnails.",
  },
  {
    text: "Wait for episodes to load (watch the episode list populate)",
    detail:
      "You should see episode titles, air dates, and the metadata spinner (if AniList-linked) briefly.",
  },
  {
    text: "Press Back to return to the previous screen",
    detail: "Leave the details page completely.",
  },
  {
    text: "Reopen the SAME anime",
    detail: "Tap it again from Browse/Library.",
  },
  {
    text: "VERIFY: Episode metadata appears instantly and STAYS visible",
    detail:
      "PASS: titles/descriptions/thumbnails appear immediately and do NOT disappear after a second. FAIL: metadata flashes briefly then vanishes (the old bug).",
  },
  {
    text: "Check logcat for the skip message",
    detail:
      "Filter: `Anikuta:Feature:Details`. Look for: 'Background refresh: no new episodes (same N URLs) — cache + display untouched, metadata preserved'",
  },
  {
    text: "Repeat steps 1-5 with a DIFFERENT anime to confirm consistency",
    detail: "Test at least 2 different anime to rule out one-off issues.",
  },
  {
    text: "Pull-to-refresh and verify metadata still persists after refresh",
    detail: "After PTR, close + reopen. Metadata should still be there.",
  },
];

const FIX2_EPISODE_LIST = [
  {
    text: "Go to Downloads → Downloaded tab",
    detail: "Bottom nav → Downloads → tap 'Downloaded' at top-left.",
  },
  {
    text: "Tap a downloaded episode",
    detail: "Tap the play icon or the episode row.",
  },
  {
    text: "VERIFY: Watch screen opens and the episode list shows ALL episodes (not just 1)",
    detail:
      "PASS: the episode list below the player shows every episode of that anime. FAIL: only the downloaded episode appears.",
  },
  {
    text: "Scroll the episode list — confirm all episodes are tappable",
    detail: "Each episode row should be clickable.",
  },
  {
    text: "Check logcat for the cache load message",
    detail:
      "Filter: `Anikuta:MainActivity`. Look for: 'Downloads→Watch: loaded N episodes from cache for episode list'",
  },
];

const FIX3_SUBTITLES = [
  {
    text: "Download an episode that HAS subtitles (check the extension provides them)",
    detail:
      "Some extensions provide subtitle tracks. AniKotoS usually does. Download such an episode.",
  },
  {
    text: "Go to Downloads → Downloaded → tap the episode to play it",
    detail: "Open the watch screen for the downloaded episode.",
  },
  {
    text: "Tap the subtitles icon (CC) in the player controls",
    detail: "The subtitle tracks sheet should open.",
  },
  {
    text: "VERIFY: Local subtitle tracks appear in the list",
    detail:
      "PASS: you see 'Subtitle 1' (or similar) entries. FAIL: the list is empty or only shows 'No subtitles'.",
  },
  {
    text: "Select a subtitle track and verify it displays",
    detail: "Tap a track. Subtitles should appear on the video.",
  },
  {
    text: "Check logcat for subtitle loading",
    detail:
      "Filter: `Anikuta:Core:Player:Subtitles`. Look for: 'Loading local subtitle (content://)' and 'Downloaded subtitle: Subtitle 1'",
  },
  {
    text: "Check logcat for the subtitle count",
    detail:
      "Filter: `Anikuta:MainActivity`. Look for: 'Downloads→Watch: passing N local subtitle track(s)'",
  },
];

const FIX4_SWITCHING = [
  {
    text: "From the watch screen (opened from Downloads), tap a DIFFERENT episode in the list",
    detail: "Tap an episode that is ALSO downloaded.",
  },
  {
    text: "VERIFY: The new episode plays (offline, no network needed)",
    detail:
      "PASS: video starts playing. FAIL: 'Cannot switch episode: source not available' error.",
  },
  {
    text: "Check logcat for offline switching",
    detail:
      "Filter: `Anikuta:Feature:Watch`. Look for: 'Episode switch — episode is DOWNLOADED, playing offline (fd://)'",
  },
  {
    text: "Tap a NON-downloaded episode (if sourceId is available)",
    detail:
      "This tests the network fallback path. May fail if the extension's proxy has churned — that's a known limitation, not a regression.",
  },
  {
    text: "Use the skip-forward button (⏭) to go to the next episode",
    detail: "Verify the next-episode button works from the downloads-launched watch screen.",
  },
];

const REGRESSION = [
  {
    text: "Online playback still works (play a non-downloaded episode from Details)",
    detail:
      "Go to an anime details page, tap an episode, verify it plays from the network.",
  },
  {
    text: "Download queue still works (start a new download)",
    detail:
      "From Details, tap the download icon on an episode. Verify it queues + downloads.",
  },
  {
    text: "Library still shows correct covers",
    detail: "Go to Library. Covers should display.",
  },
  {
    text: "Browse page loads",
    detail: "Browse tab should show trending/popular sections.",
  },
  {
    text: "Pull-to-refresh works on Details page",
    detail: "Pull down on the details page. The 3-stage refresh should trigger.",
  },
  {
    text: "Dark mode toggle works",
    detail: "Settings → Appearance → toggle dark mode.",
  },
];

const LOG_TABLE: { feature: string; tag: string; cmd: string }[] = [
  {
    feature: "Metadata caching",
    tag: "Anikuta:Feature:Details",
    cmd: "adb logcat -s Anikuta:Feature:Details",
  },
  {
    feature: "Episode list / downloads→watch",
    tag: "Anikuta:MainActivity",
    cmd: "adb logcat -s Anikuta:MainActivity",
  },
  {
    feature: "Subtitles",
    tag: "Anikuta:Core:Player:Subtitles",
    cmd: "adb logcat -s Anikuta:Core:Player:Subtitles",
  },
  {
    feature: "Watch screen / episode switch",
    tag: "Anikuta:Feature:Watch",
    cmd: "adb logcat -s Anikuta:Feature:Watch",
  },
  {
    feature: "Download queue",
    tag: "Anikuta:Core:Download",
    cmd: "adb logcat -s Anikuta:Core:Download",
  },
  {
    feature: "Data cache (DB)",
    tag: "Anikuta:Core:DataCache",
    cmd: "adb logcat -s Anikuta:Core:DataCache",
  },
  {
    feature: "Multiple tags at once",
    tag: "(combine)",
    cmd: "adb logcat -s Anikuta:Feature:Details Anikuta:MainActivity Anikuta:Feature:Watch",
  },
];

type RiskLevel = "Low" | "Medium" | "High";

interface Concern {
  title: string;
  detail: string;
  risk: RiskLevel;
  /** Whether this is an open question (rendered with a Q marker) */
  isQuestion?: boolean;
}

const CONCERNS: Concern[] = [
  {
    title: "Subtitle file naming may not match across re-downloads",
    detail:
      "Subtitles are stored as `.subtitle_E00001_0.vtt` (per-episode, per-track-index). If you download the same episode twice, the old subtitle file is deleted and replaced. But if the extension changes the track order between downloads, the index mapping may shift. If subtitles don't appear after a re-download, delete the episode and download it fresh.",
    risk: "Low",
  },
  {
    title: "Episode switching to non-downloaded episodes may fail",
    detail:
      "When you open the watch screen from the Downloads page and tap a non-downloaded episode, the app tries to re-resolve via the extension's source. This requires the extension's local proxy server to still be alive. If the proxy has churned (port rotation), the resolve will fail with 'source not available' or a network error. This is a known limitation of the extension system, not a regression. Downloaded-to-downloaded switching always works (fd:// path).",
    risk: "Medium",
  },
  {
    title: "MPV fd:// delay is device-dependent",
    detail:
      "The 500ms delay before `loadfile` for `fd://` URLs was tuned to prevent the `vo->opts->WinID != 0` SIGABRT. On some slower devices, 500ms may not be enough. If you still get a crash when playing a downloaded episode, we may need to increase the delay to 800ms or 1000ms. Report the crash log if this happens.",
    risk: "Medium",
  },
  {
    title: "The `currentMainId` lookup in episode switching uses videoUri matching",
    detail:
      "In `WatchScreen.onEpisodeSwitch`, I find the current anime's mainId by matching `watchKey.videoUrl` against the downloaded episodes' `videoUri`. This works because the initial video URL IS the downloaded file's content:// URI. But if the video URL changes (e.g. after a quality switch via QualitySheet), this lookup could fail and fall back to network resolution. If you switch quality then try to switch episodes, it may not find the offline path.",
    risk: "Low",
  },
  {
    title: "Cache comparison uses URL sets, not content hashes",
    detail:
      "The background refresh compares fresh episode URLs with cached URLs to detect changes. If an extension changes an episode's URL WITHOUT adding new episodes (e.g. URL scheme change), the cache will be overwritten. This is rare but possible. The metadata is preserved per-episode-number, so titles/descriptions survive — only the episodeUrl changes.",
    risk: "Low",
  },
  {
    title: "No request cancellation when leaving the details page (pre-existing)",
    detail:
      "When you leave the details page while the background refresh is still fetching episodes, the fetch continues in the background (the viewModelScope is cancelled on ViewModel clear, but the extension's network call may already be in-flight). This was a pre-existing issue and is NOT fixed in this build. It's harmless (the result is just discarded) but wastes a bit of bandwidth.",
    risk: "Low",
  },
  {
    title: "Testing coverage gap: HLS streams",
    detail:
      "The fixes were tested against direct MP4/MKV downloads. HLS (.m3u8) streams use a different downloader (HlsDownloader) and may have different subtitle handling. If you test with an HLS source and subtitles don't work, report the extension name + the logcat.",
    risk: "Low",
  },
  {
    title: "Should the metadata refresh be configurable?",
    detail:
      "Currently the background refresh runs on every details page open. You mentioned wanting configurable refresh options (refresh only episodes, only metadata, or both) in a future iteration. This is NOT in the current build — it's a planned feature. Should we prioritize this for the next build, or focus on stability first?",
    risk: "Low",
    isQuestion: true,
  },
  {
    title: "Notification with episode name + quality + server info",
    detail:
      "The download notification currently shows generic text. You mentioned wanting it to show the episode name + quality + server. This is a planned feature not yet implemented. Should this be in the next build?",
    risk: "Low",
    isQuestion: true,
  },
  {
    title: "When should we merge `download-system-plan` → `main`?",
    detail:
      "The branch has been stable across the last 4 CI builds. Once you confirm the 4 fixes work on-device, we can merge to main. Do you want to merge immediately after testing, or wait for the configurable refresh + notification improvements?",
    risk: "Low",
    isQuestion: true,
  },
];

const RISK_STYLES: Record<
  RiskLevel,
  { bg: string; color: string; border: string; label: string }
> = {
  Low: {
    bg: "color-mix(in srgb, var(--c-success) 12%, transparent)",
    color: "var(--c-success)",
    border: "color-mix(in srgb, var(--c-success) 30%, transparent)",
    label: "Low",
  },
  Medium: {
    bg: "color-mix(in srgb, var(--c-warning) 12%, transparent)",
    color: "var(--c-warning)",
    border: "color-mix(in srgb, var(--c-warning) 30%, transparent)",
    label: "Medium",
  },
  High: {
    bg: "color-mix(in srgb, var(--c-danger) 12%, transparent)",
    color: "var(--c-danger)",
    border: "color-mix(in srgb, var(--c-danger) 30%, transparent)",
    label: "High",
  },
};

/* ---------------------------------------------------------------------------
 * Page
 * ------------------------------------------------------------------------- */
export default function TestingPage() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── Hero ── */}
      <section className="mb-10">
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold uppercase tracking-widest"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-success) 15%, transparent)",
              color: "var(--c-success)",
            }}
          >
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: "var(--c-success)" }}
            />
            BUILD 234ea15 · download-system-plan
          </span>
          <span className="text-xs text-text-secondary">
            METADATA-FIX-v2 · 4 fixes to verify
          </span>
        </div>
        <h1 className="text-[28px] font-bold tracking-extra-tight text-text-primary leading-tight sm:text-[32px]">
          On-Device Testing
        </h1>
        <p className="mt-3 text-[15px] text-text-secondary sm:text-base max-w-3xl">
          Methodical test plan for the latest APK. Work through each section in
          order — check off items as you verify them. Your progress is saved
          locally.
        </p>

        {/* Quick-info pills */}
        <div className="mt-5 flex flex-wrap gap-2">
          {[
            { label: "APK", value: "anikuta-apk.zip" },
            { label: "Commit", value: "234ea15" },
            { label: "CI", value: "✅ passed" },
            { label: "Date", value: "Aug 8, 2026" },
          ].map((pill) => (
            <span
              key={pill.label}
              className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-2.5 py-1 text-[11px] font-medium"
            >
              <span className="text-text-secondary uppercase tracking-wide">
                {pill.label}
              </span>
              <span className="text-text-primary font-mono">{pill.value}</span>
            </span>
          ))}
        </div>
      </section>

      {/* ── Section 0 — Prerequisites ── */}
      <section className="mb-8">
        <SectionLabel number={0} title="Prerequisites" />
        <p className="mb-4 text-[13px] text-text-secondary">
          Do these before opening the app. Clean install avoids stale DB schema
          conflicts.
        </p>
        <TestingChecklist
          id="prereqs"
          kicker="Section 0"
          title="Prerequisites"
          items={PREREQS}
        />
      </section>

      {/* ── Section 1 — Fix 1: Metadata ── */}
      <section className="mb-8">
        <SectionLabel
          number={1}
          title="Fix 1 · Metadata Disappears on Reopen"
          accent="success"
        />
        <p className="mb-4 text-[13px] text-text-secondary">
          The most critical fix. The background refresh was destructively
          overwriting cached rich metadata. Now it skips when no new episodes
          are found.
        </p>
        <TestingChecklist
          id="fix1-metadata"
          kicker="Section 1 · Fix 1"
          title="Metadata Disappears on Reopen"
          items={FIX1_METADATA}
        />
      </section>

      {/* ── Section 2 — Fix 2: Episode List ── */}
      <section className="mb-8">
        <SectionLabel
          number={2}
          title="Fix 2 · Episode List from Downloads Page"
          accent="success"
        />
        <p className="mb-4 text-[13px] text-text-secondary">
          The episode list shown below the player was built from{" "}
          <em>downloaded</em> episodes only. Now it loads the full list from
          the data cache.
        </p>
        <TestingChecklist
          id="fix2-episode-list"
          kicker="Section 2 · Fix 2"
          title="Episode List from Downloads Page"
          items={FIX2_EPISODE_LIST}
        />
      </section>

      {/* ── Section 3 — Fix 3: Subtitles ── */}
      <section className="mb-8">
        <SectionLabel
          number={3}
          title="Fix 3 · Local Subtitles"
          accent="success"
        />
        <p className="mb-4 text-[13px] text-text-secondary">
          Downloaded episodes weren&apos;t passing subtitle URIs to the player,
          and the SubtitleEngine couldn&apos;t read{" "}
          <code className="font-mono text-[12px]">content://</code> URIs. Both
          fixed.
        </p>
        <TestingChecklist
          id="fix3-subtitles"
          kicker="Section 3 · Fix 3"
          title="Local Subtitles"
          items={FIX3_SUBTITLES}
        />
      </section>

      {/* ── Section 4 — Fix 3b: Switching ── */}
      <section className="mb-8">
        <SectionLabel
          number={4}
          title="Fix 3b · Episode Switching from Downloads"
          accent="success"
        />
        <p className="mb-4 text-[13px] text-text-secondary">
          When you opened the watch screen from Downloads and tapped another
          episode, it failed with &quot;source not available&quot;. Now
          downloaded-to-downloaded switching bypasses the network resolver
          entirely.
        </p>
        <TestingChecklist
          id="fix4-switching"
          kicker="Section 4 · Fix 3b"
          title="Episode Switching from Downloads"
          items={FIX4_SWITCHING}
        />
      </section>

      {/* ── Section 5 — Regression ── */}
      <section className="mb-8">
        <SectionLabel number={5} title="Regression Checks" />
        <p className="mb-4 text-[13px] text-text-secondary">
          Sanity checks to make sure the fixes didn&apos;t break anything
          unrelated.
        </p>
        <TestingChecklist
          id="regression"
          kicker="Section 5"
          title="Regression Checks"
          items={REGRESSION}
        />
      </section>

      {/* ── Section 6 — Log Capture Guide ── */}
      <section className="mb-8">
        <SectionLabel number={6} title="Log Capture Guide" />
        <Card className="mb-0">
          <CardHeader kicker="Reference" title="Log Capture Guide" />
          <p className="text-[13px] text-text-secondary leading-relaxed mb-5">
            Don&apos;t capture the entire logcat — it&apos;s too noisy.
            Instead, filter by the specific tags related to the feature
            you&apos;re testing. This makes it much easier to diagnose issues.
          </p>

          {/* Method A */}
          <div className="mb-5">
            <h3 className="text-[13px] font-semibold text-text-primary mb-2">
              Method A · USB + adb{" "}
              <span className="text-[11px] font-medium text-text-secondary ml-1.5">
                (recommended)
              </span>
            </h3>
            <ul className="space-y-1.5 text-[12.5px] text-text-secondary">
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>Connect your device via USB</span>
              </li>
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>Enable USB debugging (Settings → Developer options)</span>
              </li>
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>Run one of the filtered commands below</span>
              </li>
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>
                  Copy the output into a{" "}
                  <code className="font-mono text-[12px]">.txt</code> or{" "}
                  <code className="font-mono text-[12px]">.md</code> file and
                  share it
                </span>
              </li>
            </ul>
          </div>

          {/* Method B */}
          <div className="mb-5">
            <h3 className="text-[13px] font-semibold text-text-primary mb-2">
              Method B · On-device log viewer
            </h3>
            <ul className="space-y-1.5 text-[12.5px] text-text-secondary">
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>
                  If you don&apos;t have USB, use an app like &quot;LogCat
                  Reader&quot; or &quot;aLogcat&quot; from F-Droid
                </span>
              </li>
              <li className="flex gap-2">
                <span className="text-text-primary shrink-0">•</span>
                <span>Set the filter to the tag you need</span>
              </li>
            </ul>
          </div>

          {/* Filtered commands table */}
          <div className="mb-5">
            <h3 className="text-[13px] font-semibold text-text-primary mb-3">
              Filtered commands
            </h3>
            {/* Desktop: table-like grid. Mobile: stacked cards. */}
            <div className="hidden sm:block">
              <div className="rounded-[12px] border border-border overflow-hidden">
                <div className="grid grid-cols-[1.4fr_1.6fr_2fr] bg-canvas text-[11px] font-semibold uppercase tracking-wider text-text-secondary">
                  <div className="px-3 py-2">Feature</div>
                  <div className="px-3 py-2 border-l border-border">Log Tag</div>
                  <div className="px-3 py-2 border-l border-border">
                    adb command
                  </div>
                </div>
                {LOG_TABLE.map((row, i) => (
                  <div
                    key={row.feature}
                    className={`grid grid-cols-[1.4fr_1.6fr_2fr] text-[12px] ${
                      i % 2 === 0 ? "bg-surface" : "bg-canvas/40"
                    }`}
                  >
                    <div className="px-3 py-2.5 text-text-primary font-medium">
                      {row.feature}
                    </div>
                    <div className="px-3 py-2.5 border-l border-border">
                      <code className="font-mono text-[11.5px] text-text-secondary break-all">
                        {row.tag}
                      </code>
                    </div>
                    <div className="px-3 py-2.5 border-l border-border">
                      <code className="font-mono text-[11.5px] text-text-primary break-all">
                        {row.cmd}
                      </code>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            {/* Mobile: stacked */}
            <div className="sm:hidden space-y-2">
              {LOG_TABLE.map((row) => (
                <div
                  key={row.feature}
                  className="rounded-[12px] border border-border bg-canvas/40 p-3"
                >
                  <div className="text-[12px] font-semibold text-text-primary mb-1">
                    {row.feature}
                  </div>
                  <div className="text-[11px] text-text-secondary mb-1.5">
                    Tag:{" "}
                    <code className="font-mono text-text-secondary">
                      {row.tag}
                    </code>
                  </div>
                  <code className="block font-mono text-[11.5px] text-text-primary bg-surface rounded-[8px] px-2 py-1.5 break-all">
                    {row.cmd}
                  </code>
                </div>
              ))}
            </div>
          </div>

          {/* Tip box */}
          <div
            className="rounded-[12px] p-4 mb-4"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-success) 10%, transparent)",
              borderLeft: "4px solid var(--c-success)",
            }}
          >
            <div className="flex items-center gap-2 mb-2">
              <span className="text-[11px] font-bold uppercase tracking-widest text-[var(--c-success)]">
                Tip · metadata fix
              </span>
            </div>
            <p className="text-[12.5px] text-text-secondary leading-relaxed">
              For the metadata disappearing fix, the most important log line to
              look for is:{" "}
              <code className="font-mono text-[12px] text-text-primary bg-canvas px-1.5 py-0.5 rounded">
                Background refresh: no new episodes (same N URLs) — cache +
                display untouched, metadata preserved
              </code>
              . If you see this, the fix is working. If you see{" "}
              <code className="font-mono text-[12px] text-text-primary bg-canvas px-1.5 py-0.5 rounded">
                Background refresh: updated cache with N fresh episodes
              </code>{" "}
              instead, the fix is NOT working (the old destructive overwrite is
              still happening).
            </p>
          </div>

          {/* What NOT to capture */}
          <div
            className="rounded-[12px] p-4"
            style={{
              backgroundColor:
                "color-mix(in srgb, var(--c-danger) 8%, transparent)",
              borderLeft: "4px solid var(--c-danger)",
            }}
          >
            <div className="flex items-center gap-2 mb-2">
              <span className="text-[11px] font-bold uppercase tracking-widest text-[var(--c-danger)]">
                Don&apos;t · full logcat
              </span>
            </div>
            <p className="text-[12.5px] text-text-secondary leading-relaxed">
              Please don&apos;t send the full unfiltered logcat (
              <code className="font-mono text-[12px]">adb logcat</code> with no{" "}
              <code className="font-mono text-[12px]">-s</code> flag). It
              contains thousands of unrelated system lines and makes diagnosis
              much harder. Always use the{" "}
              <code className="font-mono text-[12px]">-s &lt;tag&gt;</code>{" "}
              filter.
            </p>
          </div>
        </Card>
      </section>

      {/* ── Section 7 — Reporting Results ── */}
      <section className="mb-8">
        <SectionLabel number={7} title="Reporting Results" />
        <Card>
          <CardHeader kicker="After testing" title="Reporting Results" />
          <p className="text-[13px] text-text-secondary leading-relaxed mb-4">
            When you finish testing, report back with:
          </p>
          <ol className="space-y-2.5">
            {[
              "Which checklist items PASSED (just the numbers is fine)",
              "Which items FAILED (describe what happened instead)",
              "For any failure, attach the filtered logcat for the relevant tag",
              "Note your device model + Android version (some issues are device-specific)",
            ].map((item, i) => (
              <li key={i} className="flex gap-3">
                <span className="shrink-0 w-5 h-5 rounded-full bg-canvas border border-border flex items-center justify-center text-[11px] font-mono font-medium text-text-primary">
                  {i + 1}
                </span>
                <span className="text-[12.5px] text-text-primary leading-relaxed pt-0.5">
                  {item}
                </span>
              </li>
            ))}
          </ol>
        </Card>
      </section>

      {/* ── Section 8 — Concerns & Open Questions ── */}
      <section className="mb-8">
        <SectionLabel number={8} title="Concerns & Open Questions" />
        <div
          className="rounded-[16px] border border-border shadow-card transition-all duration-200"
          style={{
            borderLeft: "4px solid var(--c-warning)",
            backgroundColor:
              "color-mix(in srgb, var(--c-warning) 4%, var(--c-surface))",
          }}
        >
          <div className="p-5 sm:p-6">
            <div className="flex items-start gap-3 mb-5">
              <span
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px]"
                style={{
                  backgroundColor:
                    "color-mix(in srgb, var(--c-warning) 15%, transparent)",
                  color: "var(--c-warning)",
                }}
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="w-5 h-5"
                  aria-hidden="true"
                >
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </span>
              <div className="min-w-0">
                <div className="text-[11px] font-medium uppercase tracking-widest text-[var(--c-warning)] mb-1">
                  Read before testing
                </div>
                <h2 className="text-[20px] font-bold tracking-extra-tight text-text-primary leading-tight">
                  Concerns &amp; Open Questions
                </h2>
              </div>
            </div>

            <p className="text-[13px] text-text-secondary leading-relaxed mb-5 max-w-3xl">
              Known limitations, edge cases, and decisions that need your
              input. None of these are blockers for testing — but they explain
              failures you might see, and the last three need your call before
              the next build.
            </p>

            <div className="space-y-3">
              {CONCERNS.map((c, i) => {
                const risk = RISK_STYLES[c.risk];
                return (
                  <div
                    key={i}
                    className="rounded-[12px] border border-border bg-surface p-4 transition-all duration-200"
                  >
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <div className="flex items-start gap-2 min-w-0">
                        {c.isQuestion && (
                          <span
                            className="shrink-0 mt-0.5 inline-flex items-center justify-center w-4 h-4 rounded-full text-[9px] font-bold"
                            style={{
                              backgroundColor:
                                "color-mix(in srgb, var(--c-secondary) 15%, transparent)",
                              color: "var(--c-secondary)",
                            }}
                          >
                            Q
                          </span>
                        )}
                        <h3 className="text-[13.5px] font-semibold text-text-primary leading-snug">
                          {c.title}
                        </h3>
                      </div>
                      <span
                        className="shrink-0 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest"
                        style={{
                          backgroundColor: risk.bg,
                          color: risk.color,
                          border: `1px solid ${risk.border}`,
                        }}
                      >
                        Risk: {risk.label}
                      </span>
                    </div>
                    <p className="text-[12.5px] text-text-secondary leading-relaxed">
                      {c.detail}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* Footer note */}
      <p className="text-center text-[11px] text-text-secondary pt-4 pb-2 font-mono tracking-wide">
        Testing page · Build 234ea15 · Generated for on-device verification of
        METADATA-FIX-v2
      </p>
    </div>
  );
}

/* ---------------------------------------------------------------------------
 * SectionLabel — small "N · Title" header used above each section.
 * ------------------------------------------------------------------------- */
function SectionLabel({
  number,
  title,
  accent,
}: {
  number: number;
  title: string;
  accent?: "success" | "warning";
}) {
  const accentColor =
    accent === "success"
      ? "var(--c-success)"
      : accent === "warning"
      ? "var(--c-warning)"
      : "var(--c-text-secondary)";

  return (
    <div className="flex items-center gap-2.5 mb-3">
      <span
        className="inline-flex items-center justify-center w-6 h-6 rounded-[8px] text-[11px] font-bold font-mono"
        style={{
          backgroundColor: `color-mix(in srgb, ${accentColor} 15%, transparent)`,
          color: accentColor,
        }}
      >
        {number}
      </span>
      <h2 className="text-[18px] font-bold tracking-extra-tight text-text-primary leading-tight">
        {title}
      </h2>
    </div>
  );
}
