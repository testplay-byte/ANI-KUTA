# 51 — RELEASE 1.1.22 BRANCH POINT (round 59)

`release/1.1.22` cut from the round-59 feature head `baa1ee12`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** — run 35530923924,
first run). Version bump rides THIS branch as its first commit:
**1.1.22 / 10122**.

## What v1.1.22 carries (the round-59 device round — the user's verdict:
the v1.1.21 DASH download "was kind of failing still" — "Manifest could not
be parsed: This parser does not support specification "Unknown" version
"0.0"" — and this release kills the root cause for good)

| Decision | What shipped |
| --- | --- |
| **D-546** | **The DASH parser factory fix — the REAL root cause.** The v1.1.21 body-peek diagnostics showed a VALID `<?xml …` manifest body failing with a spec-version error unrelated to its content. Verified against AOSP libcore (`luni …/javax/xml/parsers/DocumentBuilderFactory.java`): `setXIncludeAware`, `isXIncludeAware`, `setSchema`, `getSchema` throw `UnsupportedOperationException` — verbatim "This parser does not support specification "Unknown" version "0.0"" — UNCONDITIONALLY on every Android device, even for `setXIncludeAware(false)`. Both hardened factories (`DashManifestPlanner` + `MpdParser`) called `isXIncludeAware = false` UNWRAPPED inside the factory `.apply{}` → the factory construction exploded before one byte of XML was read, on EVERY device, since the pipeline existed; v1.1.20 and v1.1.21 died at the same line (D-543's byte-first transport fix was real but secondary — and its diagnostics are what exposed this layer). `MpdParser`'s silent swallow means its on-device "DASH hidden" verdicts were never real parse verdicts. THE FIX: the always-throwing line removed from BOTH factories (XInclude is unimplemented by the platform parser — "not XInclude-aware" is already the default; the supported hardening stays: the 4 wrapped `setFeature` DOCTYPE/external-entity refusals + `isExpandEntityReferences`); parse failures now ALWAYS carry the exception class (`simpleName: message`); the manifest fetch rides a derived patient client (`newBuilder()` — shared pool/interceptors so CS cookies/clearance still ride) with connect/read 20s + call 45s (the device round burned attempt 1/3 on a 10.0s CDN cold-start timeout; attempt 2 answered in 823ms); segments/subtitles keep the shared client. THE RULE: on Android NEVER call setXIncludeAware/setSchema/getSchema/isXIncludeAware on DocumentBuilderFactory — any of them throws, always. Full record + the updated failure matrix: `AGENT-CONTEXT/download-research/20-CS-DASH-PARSER-FACTORY-FIX.md`. |

## The CI ledger (this cycle)

- Implementation: run 35530923924 **GREEN** on `baa1ee12` (first run — the
  fix is two deleted lines plus diagnostics plus the patient manifest
  client; the blast radius is the two factories + the manifest fetch only).
- The release costs exactly ONE more run (the tag-driven release-apk.yml;
  the release-branch push is filtered from the Build APK trigger per
  D-472).

## The launch pad

The parser-factory record: `AGENT-CONTEXT/download-research/20-CS-DASH-PARSER-FACTORY-FIX.md`
(the updated failure matrix is §4). The fetch/byte-first record: doc 19.
The DASH design + lifecycle rules: doc 18. The handoff:
`AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §16.
