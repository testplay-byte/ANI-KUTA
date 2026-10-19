# Round 130 — The debug release v1.1.74 (D-766)

> **Status:** RELEASED and VERIFIED LIVE (2026-10-19). Decision: **D-766**. The routine: **Routine A** (RELEASE-PLAYBOOK §1 — the per-round debug release).
> **The order (the user):** "you did not handle the version properly — the version was downgraded to version 1.1.20 while the latest version was 1.1.73-debug… the debug apk was supposed to be signed properly too so just make sure of it… and lastly you were supposed to do a debug release so that I can directly download the latest updated version from the app itself."
> **Release:** `v1.1.74` — https://github.com/testplay-byte/ANI-KUTA/releases/tag/v1.1.74 — stable, `--latest`, asset `ani-kuta-v1.1.74-debug-arm64-v8a.apk` (60.7 MB) + `SHA256SUMS.txt`.

---

## 1 · The version (the user's first point)

The CI debug artifact carries **1.1.20/10120** because the **mainline is the dev line** and always has — D-430, and the playbook's invariant #1 says it plainly: *"the mainline always carries 1.1.20/10120; don't 'fix' that"*. A dev-line artifact can never install over the shipped debug build (Android refuses a downgrade), so the installable, updater-visible build is the **release**.

**The release carries 1.1.74 / 10174:**

| | |
|---|---|
| Branch | `release/1.1.74`, cut from the green head (`1d077d8e`) |
| The bump | ONE commit (`f23eac34`) — `AndroidConfig.kt`: `versionCode 10120 → 10174`, `versionName "1.1.20" → "1.1.74"` (the D-430 comment block left intact ✓) |
| The tag | the **annotated** `v1.1.74` on the bump commit; its body is the user-facing "what's new" list (D-466: clean bullets, no hashes, no tables ✓) |
| Monotonic | **10174 > 10173** ✓ — the in-app updater installs it IN PLACE over the v1.1.73 build on the device |
| The mainline | unchanged — still 1.1.20/10120 (the dev line ✓) |

## 2 · The signing (the user's second point)

The debug line signs with the **committed `anikuta-debug.keystore`** (the `anikutaDebug` config) — unchanged since the Phase-D.0 commit, so every debug build has always installed over every other one. The release workflow's apksigner gate hard-fails an unsigned APK, and it printed:

```
V2 Signer: certificate DN: CN=ANI-KUTA Debug, OU=Development, O=Confused, L=Debug, ST=Debug, C=US
V2 Signer: certificate SHA-256 digest: b4e35ea5479d6559a9f3a90e3ed9ea5bb1cd2da8512da3636086f1da442e0eae
```

**The published asset was compared against the v1.1.73 one (the build on the device): the same DN and the same certificate digest** ✓ — so the updater's install is an in-place upgrade, not a reinstall (a different key would have forced an uninstall).

Independent check on the published asset itself (no Android tooling — the APK's own signing block was read over HTTP range requests): the **APK Signing Block is present** with the **v2** scheme block (0x7109871a) and a 264-byte X.509 signer certificate ✓. The workflow's ABI gate reported `lib ABIs: [arm64-v8a]` ✓ (D-445/D-447: the debug line is arm64-v8a only).

## 3 · The release (the user's third point)

`release-apk.yml` (tag-driven) built the debug APK, ran the tag↔versionName gate (`tag=v1.1.74 … config_versionName=1.1.74` ✓), the ABI gate, the signature gate, and published the release **stable + `--latest`** (the in-app updater reads `/releases/latest` ✓).

| | |
|---|---|
| Run | **39328048109** — GREEN |
| Release | `v1.1.74` — stable, not a prerelease, not a draft ✓ |
| Published | 2026-10-19T21:34:06Z |
| Assets | `ani-kuta-v1.1.74-debug-arm64-v8a.apk` (60.7 MB) + `SHA256SUMS.txt` ✓ |
| The updater's pick | the asset name carries `-arm64-v8a.` ✓ — the ABI-aware picker matches it (D-440) |
| Branch | `release/1.1.74` left in place (the `release/1.1.73` precedent ✓) |

## 4 · What the user does now

1. Open the app → it finds **v1.1.74** in the update sheet → install → it replaces v1.1.73 in place (same signature ✓, higher versionCode ✓).
2. Or download the asset directly: https://github.com/testplay-byte/ANI-KUTA/releases/download/v1.1.74/ani-kuta-v1.1.74-debug-arm64-v8a.apk
3. Then work through the **checklists** in `ROUNDS-120-129.html` §6 — **the database migration first** (checklist A: the library, the watch progress, the downloads and the settings must all still be there).

## 5 · The ledger

| Run | What | Verdict |
|-----|------|---------|
| 39322176393 | the final Build APK + unit-test gate on the finished tree | GREEN |
| **39328048109** | **the Release APK run (the tag-driven debug release)** | **GREEN** |
| — | the release's live verification (the assets, the stable/latest flags, the certificate identity, the ABI) | VERIFIED |
