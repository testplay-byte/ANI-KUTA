# 07 — SHELL SYSTEMS (the wizard, notifications, ads, the updater, permissions)

## 1. The setup wizard (feature/onboarding, 12 steps)

WELCOME → THEME → PERSONALIZE → DETAILS_UI → PLAYER_UI → NOTIF_UI → STORAGE → RESTORE →
NOTIFICATIONS → BATTERY → OVERLAY → FINISH.

- The navigation: `steps.indexOf(...)` EVERYWHERE (the round-145 C-1 lesson: five stale
  magic ints looped STORAGE→DETAILS_UI forever and never reached FINISH on the v1.1.86
  build — fixed + locked by an OnboardingStepTest chain test).
- The PERSONALIZE gate: "Make it yours" — Lets check them out (→ DETAILS_UI) / Later in the
  app (→ STORAGE). `personalizeEntered` feeds the FINISH summary's Details-page card.
- The three config steps are THIN HOSTS: the config UIs are composable SLOTS provided by
  :app (onboarding/WizardConfigContent.kt) — the module rules keep feature/onboarding from
  importing the renderers. The previews reuse the REAL renderers (EpisodeListEntry /
  PlayerEpisodeListEntry / EpisodeBannerComposer) over the bundled stills
  (ep_preview_still_1/2.jpg) so a fresh install paints without network art.
- The permission steps verify REAL system state on ON_RESUME + say "all permissions are
  optional — skip any you don't want."
- Completion: `appPreferences.onboardingCompleted = true` ONLY via the FINISH CTA.
- ⚠ State is `remember` (not rememberSaveable) — a rotation mid-wizard resets to WELCOME
  (09-KNOWN-ISSUES M-7).

## 2. Notifications

- **Episode banners** (core/notifications/NotificationManager): the per-anime config + the
  tri-state triggers (schedule/watchable/immediate) + the sub/dub gates + the dedup store +
  the poster banners (5 templates through EpisodeBannerComposer — the REAL composer renders
  previews in the settings + the wizard). Channel: `anikuta_new_episodes_high` (heads-up).
- **The check lifecycle** (app/notifications/UpdateProgressNotifierImpl): ONE channel
  (`anikuta_update_activity`): the live card (2001, one audible start, swipeable, 15-min
  auto-expiry) + the RESULT card (2002 — the round-143 minimal redesign: "N new episodes
  found!" + one line, the cover kept, tap → the UPDATES screen; failures → the history page)
  + the D-426 background silence + the honest manual caught-up card.
- **The engine** (core/updates/UpdateEngine): the due selection, the smart release one-shots
  (the learned offsets), the 3-strike backoff, the boot re-arm (BootCompletedReceiver).
- The 90-day sent-log sweep runs at app start (the D-521-era gap fixed).

## 3. Ads (core/ads)

The gate order in AdsCoordinator.requestNavigation: always-sponsor (the debug toggle) →
disabled → **the 3-DAY launch grace** (firstOpenTimestamp, round 143 — supersedes the D-407
one-shot; consumed graces migrate 3-days-into-the-past) → the cooldown → the offline
deferral (no cooldown recorded) → the interstitial. The return pill (SYSTEM_ALERT_WINDOW)
floats over the browser. Zero code on the official repo is touched by ads.

## 4. The updater (core/app-update)

The debug builds read testplay-byte releases; the release builds read
Confused-Creature-180 (D-440). The ABI-aware pick (D-423): walk SUPPORTED_ABIS →
-universal → the first APK. The failed checks throw (the real-error contract, D-815) → the
About error card. The update sheet surfaces on the root (the update-available dot).

## 5. The permissions hub (Settings → Permissions, round 143)

POST_NOTIFICATIONS, the SAF download folder, the battery exemption, the overlay, the legacy
gallery save (pre-29 only) — live state (ON_RESUME re-verified), what-it-affects lines,
one-tap Allow, the App-info fallback.

## 6. The error-handling posture (the audit's summary)

- The app installs AnikutaCrashHandler FIRST (last_crash.txt → ErrorActivity → kill).
- The startup sweeps (the heal, the reconcile, the retention, the scan) are ALL
  runCatching-wrapped + the download scope has a CoroutineExceptionHandler backstop
  (round 145, F3).
- The known gaps live in 09-KNOWN-ISSUES.md (the synchronous DB reads on the nav clicks
  M-2, the toggleLibrary main-thread write F9, the wizard's rotation state M-7, the
  OAuth-poller stale capture M-8).
