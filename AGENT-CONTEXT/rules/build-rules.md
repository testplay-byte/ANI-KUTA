# Build Rules (NON-NEGOTIABLE)

> How Android APKs are built for ANI-KUTA.

## 1. Never Build Locally
- ❌ **NEVER** build the APK on the local machine.
- ❌ Do not run `./gradlew assembleRelease` or `bundleRelease` locally.
- ✅ **ALWAYS** build via **GitHub Actions**.

## 2. Target ABIs (ONLY these two)
| ABI | Architecture |
|-----|--------------|
| `arm64-v8a` | ARM 64-bit (modern devices) |
| `armeabi-v7a` | ARM 32-bit (older devices) |

- ❌ Do NOT build `x86` or `x86_64`.
- Configure in `build.gradle.kts` via `ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")`.
- **Do NOT** use a custom `-PabiFilters=...` Gradle flag — it does nothing unless the build script reads it. ABI config lives in `build.gradle.kts` only.
- **CI must verify** the produced APKs contain only these two `lib/<abi>/` folders (see the "Verify ABIs" step in `.github/workflows/build-apk.yml`). Fails the build if any forbidden ABI appears.

## 3. GitHub Actions Workflow
- Workflow file: `ANI-KUTA/.github/workflows/build-apk.yml`
- Triggers: on push to `main`, on `pull_request` to `main`, on version tags, on manual dispatch.
  - PR builds catch breakage **before** merge, not after.
- The build job is **guarded** with `if: hashFiles('gradlew') != ''` so CI stays green during the scaffold phase (before the Gradle project exists).
- Uses `actions/setup-java` with JDK 17 (or 21 — confirm in Phase 1).
- Caches Gradle deps for speed.
- Uploads the built APK(s) as **artifacts**.
- Uses the scoped GitHub token (repo-only) — safe to use in secrets.

## 4. Signing
- Release signing keys stored as **GitHub Actions secrets** (not in the repo).
- Debug builds can use the default debug keystore.
- Signing config to be finalized in Phase 1.

## 5. Why This Matters
- Local builds are forbidden because the environment here is not an Android toolchain and APKs must be reproducible from CI.
- Restricting ABIs keeps APK size small and matches the target device scope.
