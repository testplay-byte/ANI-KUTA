# 50 — RELEASE 1.1.21 BRANCH POINT (round 58)

`release/1.1.21` cut from the round-58 feature head `5f1796ad`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** — run 35527713450).
Version bump rides THIS branch as its first commit: **1.1.21 / 10121**.

## What v1.1.21 carries (the round-58 device round — the user's verdicts: the
poster toggle "properly handled as it should be", the CS background pause
confirmed, the auto-link skip persistence confirmed working)

| Decision | What shipped |
| --- | --- |
| **D-543** | **The DASH manifest fetch is BYTE-FIRST** — the v1.1.20 "Manifest could not be parsed" device failure (MovieBox EP-5: HTTP 200 + a full 4012-byte body, then the DOM parse died). Root cause: the old `fetchText` decoded the body to a String (forced UTF-8 — `application/octet-stream` declares no charset) and re-encoded it via `byteInputStream()` before `DocumentBuilder.parse`, while the STREAMING path hands media3's parser the RAW byte stream. A UTF-16 MPD arrived as NUL-riddled mojibake; a gzip body was binary either way. The fetch is now `fetchManifestBytes` (`body.bytes()`, empty/8 MB guards, gzip via the Content-Encoding header OR the 1F 8B magic with bounded `GZIPInputStream`), non-2xx throws `HttpException` (5xx/429 retry, 4xx fail fast), transport errors stay RAW IOExceptions (retryable + pause-for-network eligible), and every parse refusal carries the REAL parser message + a sanitized ≤80-char body-head peek. DRM/live/segment planning untouched. Full record + the failure matrix: `AGENT-CONTEXT/download-research/19-CS-DASH-MANIFEST-FETCH-FIX.md`. |
| **D-544** | **The repository branch cleanup** — 23 remote branches deleted AFTER per-branch verification (all 20 `release/1.1.x` are tag-preserved with 100% docs-only post-tag deltas; the 3 old feature branches' unmerged tails are docs-only LIVE-line records). KEPT: `main` (default), `feature/round-57-cloudstream-downloads` (the latest), `feature/test-controller-v5` (the user's requested keeper). All 87 tags + every GitHub release intact. |

## The CI ledger (this cycle)

- Implementation: run 35527713450 **GREEN** on `5f1796ad` (first run — the
  review round's verdict SHIP held; the unsigned-byte + KDoc nits applied
  pre-push).
- The release costs exactly ONE more run (the tag-driven release-apk.yml; the
  release-branch push is filtered from the Build APK trigger per D-472).

## The launch pad

The fetch-fix record: `AGENT-CONTEXT/download-research/19-CS-DASH-MANIFEST-FETCH-FIX.md`
(the failure matrix is §4). The DASH design + lifecycle rules: doc 18 (§7 is the
round-58 addendum). The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §15.
