# 08 — RELEASE OPERATIONS (the disciplined path)

## 1. The two lines

| Line | Repo | Trigger | Artifact | Latest live |
|---|---|---|---|---|
| Debug | testplay-byte/ANI-KUTA | the annotated `v*` tag → release-apk.yml | ani-kuta-vN-debug-arm64-v8a.apk + SHA256SUMS.txt, STABLE+LATEST | **v1.1.86 / 10186** |
| Professional | Confused-Creature-180/ANI-KUTA | `release-build-once.yml` dispatch (tag+ref) | 5 release-signed APKs + 5 ZIPs + SHA256SUMS, STABLE+LATEST + the website chip + the README links | **v1.1.7 / 10107** |

## 2. Routine A (the debug release — the full checklist)

1. The round's code commit is CI GREEN on main (the Build APK run).
2. `git checkout -b release/1.1.NN <green-head>` (the docs commits may ride — docs-only
   deltas don't change the code tree).
3. ONE bump commit on the branch: AndroidConfig.kt → 101NN/1.1.NN + the release comment
   block (101NN > the previous debug release so devices update in place).
4. Push the BRANCH, then create the annotated tag **ON THE BUMP COMMIT** (NOT main's head —
   the workflow greps versionName from the checked-out tree; tagging main fails the gate —
   the round-144 wrinkle). `git tag -a v1.1.NN <bump-sha> -F body.txt` + push the tag.
5. release-apk.yml builds + PUBLISHES the release automatically (STABLE+LATEST).
6. Verify: the run GREEN → /releases/latest = the tag → download the APK → sha256 matches
   SHA256SUMS.txt → the size sanity.
7. The docs ledger: the round record's release section + D-entry + progress + changelog +
   SESSION.

## 3. The professional routine (B→C→D)

B: the all-ABI dispatch (tag+ref) → the ~500MB artifact → verify (sums + the ABI audits) →
C: stage 11 assets (5 renamed APKs byte-identical + 5 level-9 ZIPs with the APK at the zip
root + the sums in the `<sha> *<name>` shape) → the release on the official repo (STABLE +
make_latest) → the post-upload verification (the re-downloads) → D: the website chip +
the README links via a REAL CLONE (never the contents API — the D-801 lesson) → the Pages
build check → the updater simulation. THE CREDENTIALS: `credentials/official-repo-credentials`
(workspace, outside git).

## 4. The CI ledger discipline

- D-472: ≤2 runs per cycle, disclosed. Round 144 ran 8 (disclosed — the no-local-Gradle
  boundary made CI the compile loop). The lesson: **batch all fixes after the FULL error
  list lands** (the modules surface one per run: data → features → app).
- The docs-only pushes trigger NO CI.
- The workflow gates: the tag↔versionName match (hard fail), the per-APK lib/ ABI audits,
  apksigner verify (the release line).

## 5. The hotfix protocol

A release-blocking bug found on a LIVE release (the C-1 precedent): fix on main + CI green
+ ASK the user for the hotfix release order (D-738 stands — even hotfixes need the explicit
word; the round-144 C-1 was fixed on main and the v1.1.87 order awaited the user).
