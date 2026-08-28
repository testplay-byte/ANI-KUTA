# Test Extensions (user-provided APKs)

Dedicated folder for the extension APKs the user shares for testing — saved here
per user request (2026-08-23) so they don't need to be re-shared every session.

| File | Source | Ext API | Priority |
|------|--------|---------|----------|
| `aniyomi-en.anikoto-v14.4.apk` | Anikoto (en) | v14 (Aniyomi classic) | **PRIMARY** — the source the user actually uses |
| `aniyomi-en.anikoto180-v16.9-release.apk` | Anikoto180 (en) | v16 (newer API) | Secondary — compat testing |

## Install on the sandbox emulator

```bash
export PATH=/home/z/android-sdk/platform-tools:$PATH
# adb install works directly (the app scans installed packages):
timeout -s KILL 120 adb install -r /home/z/ani-kuta-work/ANI-KUTA/USER-UPLOADS/extensions/aniyomi-en.anikoto-v14.4.apk < /dev/null
# then in the app: More → Settings → Extensions → the extension appears under
# "Untrusted" → tap Trust. Source then appears in Search's source picker.
```

See `AGENT-CONTEXT/knowledge/emulator-testing.md` for the full emulator workflow.
