# 20 — CS DASH: the PARSER FACTORY fix (D-546) — the real "Manifest could not be parsed" root cause

> Round 59 (v1.1.21 device feedback). Companion to 19 (D-543, the transport fix — necessary
> but NOT sufficient) and 18 (the pipeline). The v1.1.21 diagnostics built in D-543 exposed
> this layer; this doc records the definitive root cause, the proof, and the fix.

## 1. What the device actually showed (v1.1.21, 2026-09-20 logcat)

Attempt 1: manifest GET → `SocketTimeoutException: timeout` at exactly **10006ms** → retryable,
5s backoff. Attempt 2: GET → **200 application/octet-stream len=3641 (823ms)** — the D-543
byte-first fetch working as designed, then:

```
DashDownloader — manifest response: type=application/octet-stream encoding=none bytes=3641
launchDownload — non-retryable error (attempt 2/3) for task 18: Manifest could not be parsed:
  This parser does not support specification "Unknown" version "0.0"
  (body starts with: "<?xml version="1.0" encoding="utf-8"?> <MPD xmlns:xsi="http://www.w3.org/2001/XM")
```

The D-543 diagnostics were the payoff: **the body is VALID XML** (a clean `<?xml …?>` prolog)
and the error text has nothing to do with the body's content. A parser "specification version"
error for a valid UTF-8 manifest is not a parsing failure at all.

## 2. The root cause (proven, not theorized)

`DashManifestPlanner.newHardenedFactory()` (and the identical copy in
`core/cloudstream-api` `MpdParser.newHardenedFactory()`) did:

```kotlin
DocumentBuilderFactory.newInstance().apply {
    runCatching { setFeature(…disallow-doctype-decl…) }   // ×4, wrapped ✓
    isXIncludeAware = false          // ← THE BUG
    isExpandEntityReferences = false
}
```

On Android, **libcore's `javax.xml.parsers.DocumentBuilderFactory` base class throws
`UnsupportedOperationException` — verbatim "This parser does not support specification
"Unknown" version "0.0"" — from `setXIncludeAware`, `isXIncludeAware`, `setSchema` and
`getSchema`, UNCONDITIONALLY, even for `setXIncludeAware(false)`**. Verified against AOSP
libcore source (luni `DocumentBuilderFactory.java`): those four methods are
always-throw placeholders ("for backward compatibility, when implementations for earlier
versions of JAXP is used…"); the `getPackage()` spec title/version are unset on Android,
rendered as "Unknown"/"0.0". `setExpandEntityReferences` and the four `setFeature`s are
normal supported calls.

So the factory construction exploded **before a single byte of XML was read — on EVERY
device, EVERY time, since the pipeline existed**. `parse()`'s `runCatching` folded it into
the empty-plan path, the queue printed the honest-but-misleading "Manifest could not be
parsed", and the task died non-retryable (correctly — a deterministic failure). Consequences:

- v1.1.20's failure and v1.1.21's failure were the SAME root cause. D-543's UTF-16/gzip
  theory was plausible but unverified (the v1.1.20 code discarded the cause's message);
  the byte-first contract it shipped remains correct and is what made the truth visible.
- `MpdParser.parse` (the CS bridge's DASH link surfacing) swallowed the same crash into
  `MpdInfo(dynamic=false, …empty)` — meaning on-device, **every "DASH link hidden" verdict
  it ever logged was this silent factory crash, not a real unsupported shape**. Progressive
  (BaseURL/SegmentBase) DASH links that could have been surfaced for MPV playback never were.

Why the runCatching placement hid it so well: `newHardenedFactory()` is called INSIDE
`parse()`'s `runCatching` lambda, and the four `setFeature`s around the offending line were
each individually wrapped — creating the illusion that the whole factory build was
guarded-by-design. The one line that always throws was the one line not wrapped.

## 3. The fix (D-546)

1. **`DashManifestPlanner.newHardenedFactory()`** — `isXIncludeAware = false` removed (the
   platform parser does not implement XInclude at all, so "not XInclude-aware" is already
   the default; the assignment was a guaranteed crash posing as hardening). DOCTYPE/
   external-entity feature refusals stay (wrapped, unknown-feature safe);
   `isExpandEntityReferences = false` stays (supported).
2. **`MpdParser.newHardenedFactory()`** — same removal, same reasoning.
3. **`DashManifestPlanner.parse` diagnostics** — the failure line now always carries the
   exception CLASS (`"Manifest could not be parsed: ${e.javaClass.simpleName}: ${e.message
   ?: "no detail"}"`); the old `e.message ?: simpleName` dropped the class whenever a
   message existed — exactly why this round needed a device round-trip to identify a
   one-line bug.
4. **`DashDownloader` manifest fetch timeouts** — attempt 1's SocketTimeoutException at
   10.0s was the CS runtime client's timeout meeting a CDN cold start (attempt 2: 823ms).
   The manifest is ONE small request whose latency is dominated by edge/TLS setup, so the
   fetch now rides `client.newBuilder()` (shared pool + interceptors — CS cookies/clearance
   still ride) with connect/read 20s + call 45s. Segments/subtitles keep the shared
   client's snappier stall detection (a mid-segment stall is a genuine stall).

Blast radius: `core:download` (planner + downloader) and `core:cloudstream-api` (MpdParser
factory). The streaming player, queue, retry policy, storage, notifications: untouched.

## 4. Failure matrix (updated)

| Symptom | Version | Real layer | Status |
| --- | --- | --- | --- |
| "Manifest could not be parsed" (no cause logged) | v1.1.20 | `setXIncludeAware` always-throw (hidden by discarded cause) | FIXED by D-546 |
| "Manifest could not be parsed: This parser does not support specification "Unknown" version "0.0"" | v1.1.21 | SAME throw, now visible via D-543 body-peek diagnostics | FIXED by D-546 |
| Manifest GET SocketTimeoutException @10s on CDN cold start | v1.1.21 (attempt 1/3) | CS client timeout, retryable (correctly recovered on attempt 2) | MITIGATED by D-546 patient manifest client |
| `body.string()` forced-UTF-8 decode of octet-stream manifest | v1.1.20 | transport (real, secondary) | already fixed by D-543 |
| `findContentFolder — NO folder` WARN during download | v1.1.20/21 | the notification cover-thumbnail lookup BEFORE publish (SAF folder exists only after) | benign, documented (19 §7) |

## 5. Lessons

- **Never call `setXIncludeAware`/`setSchema`/`getSchema`/`isXIncludeAware` on Android's
  `DocumentBuilderFactory` — any of them throws, always.** The only safe hardening knobs are
  the `setFeature` URIs (wrapped) and `setExpandEntityReferences`.
- Wrap the WHOLE factory build + parse in one `runCatching`, but keep the exception class
  name in every surfaced message (`simpleName: message`) — message-only strings cost a
  device round-trip per bug.
- A "parse failed" error over a body that diagnostics show to be VALID and well-formed means
  the failure is in the parser's CONSTRUCTION, not the parse — read the error text literally.
