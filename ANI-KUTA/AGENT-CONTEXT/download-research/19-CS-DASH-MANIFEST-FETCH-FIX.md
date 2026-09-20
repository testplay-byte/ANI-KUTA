# 19 — CS DASH downloads: the manifest-fetch fix (round 58, D-543)

> Status: SHIPPED (the round-58 fix of the doc-18 pipeline's step 1). Doc 18 remains
> the durable design + operations record for the DASH artifact model; this doc records
> the v1.1.20 device failure, its root cause, and the fetch-layer hardening.

## 1. The device report (v1.1.20, MovieBox / "Today's Asuka Show" EP 5)

The user's logcat (2026-09-20 22:09–22:10):

1. The resolve sheet resolves 2 DASH links (English sub / Original audio, 1080p,
   `index.mpd` URLs on `sacdn.hakunaymatata.com`) — the D-539 picker works.
2. Pick → `enqueueDownload` → `DashDownloader` fetches the manifest:
   `http: ← 200 application/octet-stream len=4012 (1494ms)` — **the fetch succeeds
   and the full body arrives** (a transport failure would have produced
   "Could not fetch the DASH manifest: …", a different message).
3. ~5 ms later: `non-retryable error (attempt 1/3) … Manifest could not be parsed`
   — thrown at `DashDownloader.kt:87`, which re-throws
   `plan.unsupportedReason` set by `DashManifestPlanner.parse`'s
   `getOrElse { empty(manifestUrl, "Manifest could not be parsed") }`.
4. Non-fatal noise in the same capture: `findContentFolder — NO folder with
   mainId=… found` — benign (see §5).

## 2. Root cause (code-verified; the CDN is geo-blocked from the sandbox — R9-C —
so no direct byte reproduction was possible)

`DashDownloader.fetchText` decoded the manifest body to a `String`
(`body.string()` → forced UTF-8, because `application/octet-stream` declares no
charset) and re-encoded it via `byteInputStream()` before
`DocumentBuilder.parse`. The STREAMING path never does this: media3's
`DashMediaSource` hands its parser the **raw byte stream** and the XML parser
performs the spec's own encoding detection. The two paths diverged exactly where
the body's bytes are not plain UTF-8 XML:

- **UTF-16 / non-UTF-8 manifest bodies** (identity-served, BOM + prolog
  declaration) → `body.string()` decodes them as UTF-8 → NUL-riddled mojibake →
  DOM parse throws → "Manifest could not be parsed". (A plain UTF-8 body — BOM
  or not — round-trips byte-identically and was never the failure mode.)
- **Gzip bodies**: OkHttp only transparently gunzips when IT supplied the
  request's `Accept-Encoding`; provider header maps can carry their own (or a
  CDN gzips unconditionally). The visible `len=4012` proves no transparent
  decompression happened on this fetch (application interceptors run
  post-BridgeInterceptor, which strips Content-Length when it decompresses).
- **Secondary defect**: the fetch call-site's `catch (Exception) →
  DownloadException` wrap swallowed **IOExceptions** — a genuine CDN blip was
  classified non-retryable (`RetryPolicy` retries raw IOExceptions, never
  `DownloadException`), burning the task to ERROR on the first hiccup.

## 3. The fix (D-543) — two files, one contract

### DashDownloader

- `fetchText(String)` → **`fetchManifestBytes`**: the response is read as
  `body.bytes()` — never decoded. A log line records
  `type/encoding/bytes` for every manifest response.
- Guards: empty body → honest `DownloadException`; body > 8 MB
  (`MAX_MANIFEST_BYTES`) → refused (MPDs are kilobytes; this is also the gzip
  decompression ceiling).
- **Gzip handled explicitly**: `Content-Encoding: gzip` header OR the
  `1F 8B` magic → `GZIPInputStream` decompression, bounded at 8 MB (the bomb
  cap is checked every 64 KB chunk); a failed decompression says so.
- **HTTP errors → `HttpException(code, …)`**: 5xx/429 now retry per the
  existing policy; 4xx fail fast with the status in the message.
- **Transport errors stay raw IOExceptions** → retryable (up to 3 attempts,
  5s/10s backoff) AND the queue's `pauseForNetwork` gate (offline /
  Wi-Fi-only) now applies to manifest fetches too — the old DownloadException
  wrap bypassed it.
- The planner receives the untouched bytes; `DashPart(url = manifestUrl,
  kind = "manifest")` and the CacheWriter pipeline are untouched.

### DashManifestPlanner

- `parse(ByteArray, manifestUrl, preferredHeight)` — parses
  `manifestBytes.inputStream()` directly; the DOM parser owns BOM/UTF-8/UTF-16
  detection, exactly like the streaming path.
- **Honest diagnostics**: a parse failure no longer discards the cause — the
  unsupported reason now names the REAL parser message plus a sanitized,
  log-safe peek at the body head (`bodyHeadDiagnostics`: unsigned bytes,
  control chars/DEL → spaces, whitespace-collapsed, ≤ 80 chars, max 120 bytes
  sampled). An HTML error page / JSON error payload / binary garbage becomes
  obvious from the task's error line alone — on-device, no logcat required.
- Empty body → "The manifest response was empty".
- Everything downstream (DRM refusal, live refusal, SegmentTemplate/List/
  BaseURL planning, quality selection, MAX_SEGMENTS) is byte-for-byte
  unchanged; valid UTF-8 manifests parse identically.

## 4. Failure-mode matrix after the fix

| Manifest fetch outcome | Classification | What the user sees |
| --- | --- | --- |
| Network blip (IOException) | RETRY (3 attempts, backoff) | RETRYING, then download |
| HTTP 5xx / 429 | RETRY | RETRYING, then download |
| HTTP 4xx | ERROR | "The DASH manifest returned HTTP 403" (+ the interceptor's error-body snippet in logcat) |
| Empty body | ERROR | "The DASH manifest response was empty" |
| gzip that won't decompress / > 8 MB | ERROR | the specific reason |
| Body that genuinely isn't XML | ERROR | "Manifest could not be parsed: <real parser message> (body starts with: "…")" |
| Valid manifest (UTF-8/UTF-16/BOM/gzip) | DOWNLOAD PROCEEDS | the episode downloads |

## 5. The findContentFolder warning (checked, benign)

`DownloadNotificationManager.loadThumbnail` asks the storage provider for the
content folder to load a cached `cover.jpg`; on a FIRST download the folder
does not exist yet (it is created at publish time), so the WARN line fires and
the thumbnail falls back to the network cover. No functional impact; left
untouched deliberately (touching the SAF walk for a log line is regression
risk with zero user value).

## 6. Verification

- Independent review (Task 99-a, general-purpose, review-only): **SHIP** —
  compile-validity, API-usage (against okhttp 5.0.0-alpha.14 shipped
  precedents), the retry matrix, the planner's untouched good paths, and the
  regression sweep all PASS. Review nits applied: unsigned-byte
  `(b.toInt() and 0xFF)` in `bodyHeadDiagnostics` (a raw `toInt().toChar()`
  sign-extends bytes ≥ 0x80 into 0xFF80–0xFFFF chars that slip past the
  filter), and the KDoc's BOM narrative corrected (a UTF-8 BOM round-trips
  byte-identically; the killers were the forced UTF-8 decode of non-UTF-8
  bodies + undecoded gzip).
- Known residual (pre-existing, acknowledged): `body.bytes()` buffers the
  whole response before the size guard — a hostile >8 MB response could
  allocate before refusing. Identical exposure to the old `body.string()`;
  a streamed-read budget is a future-round candidate.
- On-device verification (v1.1.21): re-run the MovieBox EP-5 download; the
  download must complete and play offline in airplane mode.
