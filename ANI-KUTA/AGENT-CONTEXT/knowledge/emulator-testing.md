# Emulator Testing — The Sandbox Android Environment

> **What this is:** the complete, verified guide to running + testing the ANI-KUTA app
> inside the agent sandbox on an Android emulator — setup from scratch, the sandbox
> rules that WILL bite you, the daily workflow commands, app-specific testing tricks,
> and what is/isn't testable from here.
>
> **Status:** fully working (first verified 2026-08-23, D-246 session). A fresh sandbox
> needs ~15 min of setup (Part 1) — everything else is copy-paste.
>
> **Authorization:** user-approved exceptions to CORE_RULES §8 (see that file):
> (1) test-only x86_64 APK artifact from CI, (2) SDK/emulator tooling in the sandbox
> for RUN + INSPECT. Gradle/javac/build-tools remain forbidden — the APK always comes
> from CI.

---

## What this buys you (verified)

✅ Install + launch the real CI-built app · ✅ full startup pipeline (Koin, workers,
channels) · ✅ AniList browse/search/details over the live network · ✅ extension
repo → install → trust lifecycle · ✅ source picker + extension search · ✅ UI
automation (tap/type/swipe via bounds) · ✅ live `Anikuta:*` logcat debugging ·
✅ screenshots + UI hierarchy dumps.

❌ **Video playback / cache-hit / download-from-extension testing** — the sandbox's
datacenter IP cannot pass Cloudflare challenges (most anime sources are CF-protected;
`animekai.to` doesn't even resolve). Those tests stay on the user's device. What IS
testable here: everything up to and including the CF-protection UI itself.

---

## Environment facts

| Item | Value |
|---|---|
| Host | x86_64, 2 cores, **no /dev/kvm** (software emulation / TCG only) |
| Memory | 4.1 GB total — **HARD 4 GB cgroup limit** (the OOM killer is the enforcement) |
| Disk | ~10 GB total; SDK + image ≈ 4.5 GB; keep >1.5 GB free |
| Java | OpenJDK 21 **JRE** at /usr/bin/java (no javac — irrelevant, no local builds) |
| Emulator | Android emulator 37.x, headless (`-no-window`), swiftshader GPU |
| Image | **AOSP API 30, x86_64** (`system-images;android-30;default;x86_64`) |
| AVD | `anikuta` — 720x1280 @320dpi, **1024 MB guest RAM** (do NOT raise), 2 cores, 2 GB data |
| Cold boot | ~8 minutes (TCG); warm restart faster |
| APK | `app-debug-x86_64-emulator.apk` from CI (native — no ARM translation) |

### Why exactly these choices (hard-won)

| Choice | Reason |
|---|---|
| x86_64 image, not ARM | The emulator **fatally refuses** ARM images on an x86_64 host: *"Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host"*. |
| AOSP `default`, not `google_apis` | google_apis boots + has libndk_translation (arm APKs run translated), but GMS services push qemu RSS to ~3.5 GB → **cgroup-OOM-killed** mid-session. AOSP is lean and stable at 1024 MB guest. |
| 1024 MB guest RAM | 1536 MB works at first, then the OOM killer takes qemu (guest RAM × ~3 = host RSS). 1024 is the verified ceiling. |
| Native x86_64 APK | The arm APK runs via translation but every frame is translated — glacial under TCG. CI builds the native one as a separate artifact (shipped APK unchanged). |

### Path map

```
/home/z/android-sdk/                     ← ANDROID_HOME
├── cmdline-tools/latest/                ← sdkmanager, avdmanager
├── platform-tools/adb
├── emulator/emulator
└── system-images/android-30/default/x86_64/
/home/z/.android/avd/anikuta.avd/        ← the AVD (+ config.ini)
/home/z/android-dl/                      ← downloads, boot logs, screenshots, APKs
/home/z/android-dl/apk2/debug/           ← app-debug.apk (arm) + app-debug-x86_64-emulator.apk
```

---

## Part 1 — Setup from scratch (fresh sandbox, ~15 min)

### 1.1 SDK core (cmdline-tools + platform-tools + emulator)

```bash
mkdir -p /home/z/android-sdk/cmdline-tools /home/z/android-dl
cd /home/z/android-dl
curl -sSL --retry 3 -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o cmdtools.zip -d /home/z/android-sdk/cmdline-tools
mv /home/z/android-sdk/cmdline-tools/cmdline-tools /home/z/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/home/z/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "emulator"
rm cmdtools.zip
```

### 1.2 System image — MANUAL download (sdkmanager is unreliable here)

`sdkmanager` failed twice with a spurious *"No space left on device"* (temp-file churn)
with 6 GB actually free. The proven path is a direct download + manual extraction:

```bash
cd /home/z/android-dl
curl -sSL --retry 3 -o aosp30.zip "https://dl.google.com/android/repository/sys-img/android/x86_64-30_r11.zip"
mkdir -p /home/z/android-sdk/system-images/android-30/default
cd /home/z/android-sdk/system-images/android-30/default
unzip -q -o /home/z/android-dl/aosp30.zip
rm /home/z/android-dl/aosp30.zip
```

(676 MB zip → ~3.1 GB extracted. The zip contains an `x86_64/` subdir — that's correct.)

### 1.3 Create + configure the AVD

```bash
export ANDROID_HOME=/home/z/android-sdk ANDROID_AVD_HOME=/home/z/.android/avd
echo "no" | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n anikuta \
  -k "system-images;android-30;default;x86_64" -d pixel_2 --force
```

Then apply these overrides to `/home/z/.android/avd/anikuta.avd/config.ini`
(replace-or-append each key — python one-liner):

```bash
python3 - << 'EOF'
path = "/home/z/.android/avd/anikuta.avd/config.ini"
overrides = {
    "hw.lcd.width": "720", "hw.lcd.height": "1280", "hw.lcd.density": "320",
    "hw.ramSize": "1024", "hw.vm.heapSize": "256",
    "disk.dataPartition.size": "2G", "hw.cpu.ncore": "2",
    "hw.keyboard": "yes", "hw.gpu.enabled": "yes", "hw.gpu.mode": "swiftshader_indirect",
    "skin.name": "720x1280", "skin.path": "720x1280", "showDeviceFrame": "no",
}
lines, seen = [], set()
try:
    for line in open(path):
        k = line.split("=")[0].strip()
        if k in overrides: lines.append(f"{k}={overrides[k]}\n"); seen.add(k)
        else: lines.append(line)
except FileNotFoundError: pass
for k, v in overrides.items():
    if k not in seen: lines.append(f"{k}={v}\n")
open(path, "w").writelines(lines)
print("CONFIG SET")
EOF
```

### 1.4 Get the x86_64 test APK from CI

CI produces `app-debug-x86_64-emulator.apk` in the `anikuta-apk` artifact on every
build of the branch (the shipped arm `app-debug.apk` is in the same artifact):

```bash
TOKEN=<github token>
# latest successful Build APK run on the branch (swap the branch name after merge
# to main — the x86 artifact is produced on every Build APK run):
RUN=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/testplay-byte/ANI-KUTA/actions/runs?branch=test-feature/video-cache-new-download&per_page=10" | \
  python3 -c "
import json,sys
for r in json.load(sys.stdin).get('workflow_runs',[]):
    if r['name']=='Build APK' and r.get('conclusion')=='success':
        print(r['id']); break")
# artifact id:
AID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/testplay-byte/ANI-KUTA/actions/runs/$RUN/artifacts" | \
  python3 -c "
import json,sys
for a in json.load(sys.stdin).get('artifacts',[]): print(a['id']); break")
curl -sL -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/testplay-byte/ANI-KUTA/actions/artifacts/$AID/zip" -o /home/z/android-dl/artifact.zip
cd /home/z/android-dl && unzip -o -q artifact.zip -d apk2 && ls -la apk2/debug/
```

If the branch predates the x86 artifact, install the arm `app-debug.apk` — it runs
via libndk_translation... **only on the google_apis image**. The AOSP image ships
**no ARM translation** (verified: no `ndk_translation`/`houdini` in its system+
vendor images) — the x86_64 artifact is REQUIRED with it.

---

## Part 2 — The sandbox rules (violating these costs hours)

### 2.1 The process reaper — double-fork detach

**Any process started in a Bash call — even `setsid nohup ... &` — is killed when
that tool call ends.** The sandbox reaps the whole process tree per command. The
escape hatch is a **double fork** (subshell + setsid) so the process reparents to
init mid-command:

```bash
cd /home/z && ( setsid nohup /home/z/android-sdk/emulator/emulator @anikuta \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off \
  -memory 1024 -cores 2 -no-snapshot > /home/z/android-dl/boot.log 2>&1 < /dev/null & )
```

(Single-fork `setsid nohup ... &` *appears* to work sometimes — it doesn't survive
reliably. Always double-fork. The adb server survives on its own — it self-daemonizes.)

### 2.2 adb discipline — timeout + closed stdin, ALWAYS

Plain `adb shell` **hangs the tool call** (adb inherits the persistent shell's
never-EOF stdin; SIGTERM doesn't reliably kill it). Every single adb invocation:

```bash
export PATH=/home/z/android-sdk/platform-tools:$PATH
timeout -s KILL 20 adb shell <command> < /dev/null
```

- `timeout -s KILL` (SIGKILL, not the default TERM)
- `< /dev/null` on EVERY adb command (host-side stdin closed)
- generous timeouts for slow ops: `install` 400-550s, `uiautomator dump` 110s,
  `logcat -d` 60-150s, `screencap` 45s
- if `adb devices` itself hangs: `kill -9 $(pgrep -f 'fork-server')` then
  `adb start-server`, then re-check (device may show `offline` briefly)

### 2.3 `input text` truncates at ~14 chars

`adb shell input text "long-url"` silently drops characters after ~14. Type in
**≤12-char chunks** with pauses — and even then verify what landed in the field via
the UI dump. For anything long (URLs, JSON), **skip typing entirely** and inject via
prefs files (Part 4.3). `input keyevent 67` = backspace (clear a field with a loop).

### 2.4 The memory ceiling

4 GB cgroup. qemu TCG RSS ≈ guest RAM × ~3 (plus graphics). Rules:
- Guest RAM **1024 MB max** — 1536 gets OOM-killed mid-session even when host
  RAM looks free.
- **Kill the idle Next.js preview server before booting** (it holds ~500 MB):
  `pkill -f next-server` — and restart it after testing:
  `cd /home/z/my-project && ( setsid nohup bun run dev > dev.log 2>&1 < /dev/null & )`
- Nothing else heavy alongside the emulator (no second JVMs).
- Emulator death signature: `pgrep -c qemu-system` → 0, and
  `dmesg | grep "Out of memory"` shows the kill. Just reboot it (data persists
  unless you pass `-wipe-data`).

### 2.5 TCG slowness — expected numbers

| Operation | Time |
|---|---|
| Cold boot to `sys.boot_completed=1` | ~8 min (up to 40 min worst case) |
| App cold start (first frame) | ~80 s (logcat: `Displayed ... +1m19s`) |
| `uiautomator dump` | 60–110 s |
| `logcat -d` full buffer | can exceed 120 s — always filter ON DEVICE: `adb shell "logcat -d | grep ... | tail -N"` |
| Every UI tap → next frame | 5–25 s — **wait 15-30 s after every tap before dumping** |

**System ANRs are NORMAL**: `system_server` / SystemUI / "Process system isn't
responding" dialogs appear under 2-core TCG, especially during app cold start.
Dismiss: tap **Wait** (lower button, ~[50,665][670,761] → tap ≈ (360, 713)), or
prevent background ones: `adb shell settings put global anr_show_background 0`.
These are emulator artifacts, NOT app bugs — check whose process the ANR names.

---

## Part 3 — Daily workflow

### 3.1 Start + boot-poll

```bash
pgrep -c qemu-system || \
  ( cd /home/z && setsid nohup /home/z/android-sdk/emulator/emulator @anikuta \
    -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off \
    -memory 1024 -cores 2 -no-snapshot > /home/z/android-dl/boot.log 2>&1 < /dev/null & )
```

Poll (repeat the call until `BOOTED`; ~1 poll/min):

```bash
export PATH=/home/z/android-sdk/platform-tools:$PATH
adb start-server >/dev/null 2>&1
for i in $(seq 1 9); do
  sleep 60
  B=$(timeout -s KILL 15 adb shell getprop sys.boot_completed < /dev/null 2>/dev/null | tr -d '\r')
  echo "poll $i: $B"
  [ "$B" = "1" ] && { echo BOOTED; break; }
done
```

Post-boot prep (once): disable animations + background ANR dialogs:

```bash
timeout -s KILL 20 adb shell settings put global window_animation_scale 0 < /dev/null
timeout -s KILL 20 adb shell settings put global transition_animation_scale 0 < /dev/null
timeout -s KILL 20 adb shell settings put global animator_duration_scale 0 < /dev/null
timeout -s KILL 20 adb shell settings put global anr_show_background 0 < /dev/null
```

### 3.2 Install + launch

```bash
timeout -s KILL 500 adb install -r /home/z/android-dl/apk2/debug/app-debug-x86_64-emulator.apk < /dev/null
timeout -s KILL 20 adb shell am start -n com.confused.anikuta/.MainActivity < /dev/null
sleep 30   # cold start ~80s on first launch; ~10-25s warm
timeout -s KILL 20 adb shell pidof com.confused.anikuta < /dev/null   # sanity: pid printed
# verify native ABI (should say x86_64):
timeout -s KILL 20 adb shell "dumpsys package com.confused.anikuta | grep primaryCpuAbi" < /dev/null
```

### 3.3 See — UI dump + parse + screenshot

```bash
timeout -s KILL 110 adb shell uiautomator dump /sdcard/ui.xml < /dev/null
timeout -s KILL 30 adb pull /sdcard/ui.xml /home/z/android-dl/ui.xml < /dev/null
```

Parse (clickables + labels + bounds — the coordinates you tap are the centers of
these bounds):

```bash
python3 -c "
import re
xml = open('/home/z/android-dl/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*?/?>', xml):
    n = m.group(0)
    t = re.search(r'text=\"([^\"]*)\"', n); d = re.search(r'content-desc=\"([^\"]*)\"', n)
    b = re.search(r'bounds=\"([^\"]*)\"', n); c = re.search(r'clickable=\"true\"', n)
    label = (t.group(1) if t and t.group(1) else '') or (d.group(1) if d and d.group(1) else '')
    if label.strip() or c:
        print(('[C] ' if c else '    ') + label[:60] + '  ' + (b.group(1) if b else ''))
"
```

Screenshot: `timeout -s KILL 45 adb exec-out "screencap -p" > /home/z/android-dl/shot.png < /dev/null`
(a ~30-1000 KB file = a real render; 0 bytes = the exec-out was killed — retry).

**Gotcha:** `uiautomator dump` sometimes returns a STALE dump right after a tap
(the accessibility tree lags under TCG) or "null root node". `rm` the local file,
wait 10 s, re-dump, and check the file size changed.

### 3.4 Logs

```bash
# app logs (tag-filtered, on-device grep — host-side piping of the full buffer is too slow):
timeout -s KILL 120 adb shell "logcat -d | grep 'Anikuta:' | tail -40" < /dev/null
# clear before a focused test:
timeout -s KILL 20 adb logcat -c < /dev/null
# crashes:
timeout -s KILL 60 adb shell "logcat -d -b crash | tail -30" < /dev/null
```

App tag reference for Android Studio's filter bar (CORE_RULES §20 format):

```
tag:Anikuta:Core:Download | tag:Anikuta:Core:PlaybackCache | tag:Anikuta:Feature:Watch message~:(?i)(cache|proxy|loadfile|FILE_LOADED|resum)
tag:Anikuta:Data:Extension | tag:Anikuta:Feature:Search | tag:Anikuta:Feature:Details
tag:Anikuta:Core:Download message~:(?i)(network|resume|pause|retry|onNetworkChanged)
tag:Anikuta:Core:PlaybackCache message~:(?i)(play\[|serve\[|learn\[|hls\[|seg\[|fill\[|complete|fail-open)
```

### 3.5 Interact

```bash
timeout -s KILL 60 adb shell input tap 360 713 < /dev/null      # tap (center of a bounds rect)
timeout -s KILL 90 adb shell input swipe 360 1000 360 400 400 < /dev/null   # scroll down
timeout -s KILL 90 adb shell input keyevent 4 < /dev/null       # BACK
timeout -s KILL 90 adb shell input keyevent 66 < /dev/null      # ENTER (submit search)
timeout -s KILL 90 adb shell input text "naruto" < /dev/null    # ≤14 chars! (see 2.3)
```

**Pace every interaction**: tap → `sleep 15-30` → dump. Rapid-fire taps get dropped
(the app is mid-frame). If a tap "didn't work", it usually fired while the UI was
busy — verify the current screen first, then re-tap.

### 3.6 Shutdown

```bash
timeout -s KILL 30 adb emu kill < /dev/null; sleep 8
pkill -9 -f qemu-system 2>/dev/null
```

---

## Part 4 — App-specific testing tricks (all verified)

### 4.1 One-time grants (skip the system dialogs)

```bash
# extension installs (unknown-sources) — skips the "not allowed to install unknown apps" wall:
timeout -s KILL 30 adb shell appops set com.confused.anikuta REQUEST_INSTALL_PACKAGES allow < /dev/null
# battery-optimization exemption (setup wizard step 3):
timeout -s KILL 30 adb shell dumpsys deviceidle whitelist +com.confused.anikuta < /dev/null
```

### 4.2 First-run setup bypass (prefs injection)

⚠️ **Known bug (logged):** the first-run dialog's "Skip for now" button has an empty
onClick — it does nothing. Bypass the whole dialog by satisfying its conditions via
prefs injection (debug builds are run-as-able):

```bash
# Download-folder pref (setup step 2) — write locally, push, copy in as the app:
cat > /tmp/prefs.xml << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="pref_dl_folder_uri">content://com.android.externalstorage.documents/tree/primary%3ADownload</string>
</map>
EOF
timeout -s KILL 30 adb shell am force-stop com.confused.anikuta < /dev/null; sleep 3
timeout -s KILL 30 adb push /tmp/prefs.xml /data/local/tmp/prefs.xml < /dev/null
timeout -s KILL 30 adb shell run-as com.confused.anikuta cp /data/local/tmp/prefs.xml /data/data/com.confused.anikuta/shared_prefs/anikuta_prefs.xml < /dev/null
```

(Note: this REPLACES anikuta_prefs.xml — merge the key into the existing file if the
app has state worth keeping. Battery exemption via 4.1 covers step 3; notification
permission (step 1) is auto-granted on API < 33... on API 30 there's no runtime
notification permission, so only steps 2+3 apply.)

### 4.3 Extension repo injection (the typing bypass)

Typing a repo URL into the dialog is unreliable (2.3). Repos live in a
SharedPreferences JSON file — inject directly:

```bash
cat > /tmp/repos.xml << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="repos_json">[{"baseUrl":"https://raw.githubusercontent.com/aniyomi-addons/anime-extensions-repo/repo","name":"Anime Extensions","website":""}]</string>
</map>
EOF
timeout -s KILL 30 adb shell am force-stop com.confused.anikuta < /dev/null; sleep 3
timeout -s KILL 30 adb push /tmp/repos.xml /data/local/tmp/repos.xml < /dev/null
timeout -s KILL 30 adb shell run-as com.confused.anikuta mkdir -p /data/data/com.confused.anikuta/shared_prefs < /dev/null
timeout -s KILL 30 adb shell run-as com.confused.anikuta cp /data/local/tmp/repos.xml /data/data/com.confused.anikuta/shared_prefs/anikuta_extension_repos.xml < /dev/null
```

Relaunch the app → Extensions screen → "Available Extensions (16)".

### 4.4 The extension install + trust flow (UI path)

More → Settings → Extensions → (scroll to) extension row → **Install** → system
dialog → **INSTALL** (bottom-right ≈ (567, 733)) → the extension appears under
**Untrusted** → **Trust** button on its row → it moves to **Trusted Sources**.
Logs that confirm each stage (`Anikuta:Data:Extension:Backend: Install succeeded`,
`Extension:Trust: isTrusted(...) = true`, `Registered source: <name>`).

### 4.5 Working extension sources from this sandbox

Verified repo: `https://raw.githubusercontent.com/aniyomi-addons/anime-extensions-repo/repo`
(16 extensions). DNS/CF status from the sandbox (2026-08-23): **JetAnime
(jetanime.co) resolves** but is Cloudflare-protected (the app correctly shows its
CF WebView-bypass UI); Adkami resolves; `animekai.to`, `ianime.cc`, `hds.stream`,
`voiranime.com` don't resolve at all. The official `aniyomiorg/aniyomi-extensions`
repo has only utility extensions (no anime sources). Re-check reachability with
`getent hosts <domain>` before planning a playback test.

---

## Part 5 — Smoke-test checklist (what "works" means here)

The full E2E-verified set (2026-08-23, x86_64 APK from commit `cf4a8a6f`, CI run 32631607584):

- [x] APK installs (`Success`, `primaryCpuAbi=x86_64`), launches, cold start ~80 s
- [x] Startup pipeline in logs: Koin init, `UpdateCheckWorker — complete`,
      notification channels, schedule fetch, **"Fetched 20 trending from network"**
- [x] Browse grid: real AniList data + covers + scores (screenshot-verified)
- [x] Details: banner/title, ★score + status + ep count, genres, synopsis,
      10-star rating row, Episodes section, "No source linked" (correct pre-extension)
- [x] Extension repo added → index fetched (16) → install ×2 → trust ×2 →
      "Loaded 2 trusted (2 sources)"
- [x] Search source picker lists trusted sources; extension search executes live
      (`Searching source AnimeKai for 'naruto'`); CF-protection UI renders with
      the WebView-bypass buttons
- [x] Schedule notifications scheduled for a releasing show (`on_schedule` logs)

**Cannot be tested here** (needs user device): actual video playback, cache-hit
replay, downloads from real sources (Cloudflare), download network-resilience
device behavior (D-246's auto-resume), MPV performance.

---

## Part 6 — Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `pgrep -c qemu-system` → 0 after booting | OOM-killed (check `dmesg \| grep "Out of memory"`) | Reboot at 1024 MB; kill next-server first (2.4) |
| Emulator dies within seconds of launch | Single-fork start — reaped | Use the double-fork command (2.1) exactly |
| `adb devices` hangs | adb server wedged | `kill -9 $(pgrep -f fork-server)`; `adb start-server` |
| `adb shell` never returns | Missing `timeout -s KILL` / `< /dev/null` | Wrap EVERY adb call (2.2) |
| Device shows `offline` | Transient after adb restart | Wait 10 s, re-check; `adb devices` twice |
| "System UI isn't responding" dialog | TCG slowness, NOT an app bug | Tap Wait ≈(360, 713); `anr_show_background 0` |
| Tap seems ignored | UI busy mid-frame under TCG | Wait 15–30 s, re-dump, re-tap; verify focus: `dumpsys window \| grep mCurrentFocus` |
| UI dump identical to last | Stale accessibility tree | `rm` local ui.xml, wait 10 s, re-dump |
| `input text` garbled/truncated | 14-char limit | Chunks ≤12 chars, or prefs injection (4.2/4.3) |
| App crashed | — | `adb shell "logcat -d -b crash \| tail -30"` + `pidof` to confirm |
| sdkmanager "No space left on device" (lie) | Temp churn | Manual image download+extract (1.2) |
| First-run dialog won't go away | Skip button is broken (known bug) | Prefs injection (4.2) |

---

## Related docs

- `memory/decisions.md` **D-243/D-245** (video cache), **D-244** (parallel downloads), **D-246** (network resilience + this environment)
- `APP/ani-kuta/DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` — the feature plan + session-2 addendum (root causes + logcat filters)
- `APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md` — device-side download testing
- CORE_RULES §8 — the authorization exceptions for this environment
