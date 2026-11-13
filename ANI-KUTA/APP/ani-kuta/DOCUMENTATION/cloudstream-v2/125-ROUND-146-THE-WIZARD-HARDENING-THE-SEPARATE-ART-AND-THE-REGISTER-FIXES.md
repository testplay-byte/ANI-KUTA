# Record 125 — Round 146: the wizard hardening, the separate preview art, and the register's fixes

> The user's order: handle the highlighted issues properly (the wizard loop "look into it and
> improve it", the SEPARATE thumbnail/banner images), the other register items, then a master
> testing checklist — AND the debug release (explicitly ordered again).

## 1. The wizard hardening (C-1 follow-up + M-7's wizard slice)

- The C-1 enum-navigation fix was verified in place (the five stale indices gone in
  round 145); this round ADDS the resilience layer: **`stepIndex` + `personalizeEntered` are
  `rememberSaveable`** — a rotation or a process death mid-wizard RESUMES at the user's step
  instead of resetting to Welcome (the permission states already re-verify for real on
  resume).
- **The SEPARATE demo art roles (the user's explicit ask)**: `ep_preview_still_2` = the
  EPISODE THUMBNAIL art (the details/player preview rows + the composer's episode-thumb
  override), `ep_preview_still_1` = the BANNER art (the poster stage). The composer gained
  **`overrideBannerUrl`** (additive — it seeds the background chain ahead of the content
  lookup) so a fresh install (no library art) previews a REAL banner image distinct from the
  thumbnail image. The old interchangeable `demoThumb` helper is removed.

## 2. The register fixes (the crash/quality class)

- **P-H2 (HIGH)**: the initial `fd://` loadfile's `postDelayed(500)` was UNTRACKED —
  leaving a downloaded episode within the window posted a loadfile at a DESTROYED mpv
  handle + a closed fd (the native-crash class, SIGSEGV-unreachable). FIXED: the runnable is
  tracked (`pendingInitialLoadRef`) and `removeCallbacks`'d at dispose (the
  logObserverRef pattern).
- **P-H1 (HIGH)**: the CS OFFLINE DASH failures (the sidecar read / the manifest parse)
  emitted the error with the MEDIA URI (non-null) — `onEngineError`'s currentLink-mismatch
  gate dropped them (currentLink is null on offline loads) → a black screen with dead
  controls. FIXED: the media-uri match fails the phase honestly (the error text + the retry
  path).
- **H-3 (HIGH, both parts)**: (a) the torrent start gains the IDENTITY GUARD — our start
  must POPULATE the working dir; an echo answer with OUR dir empty = a foreign TorrServer on
  8090 → refused (the links stay hidden rather than riding another app's server); (b) the
  startup cache wipe moved OFF the main thread (`cleanupStaleCacheAsync` — the recursive
  delete over a heavy session was an ANR at launch).
- **P-M2 (MEDIUM)**: the CS Up Next holds at FULL until the last 10 SECONDS of actual
  playback (the round-123 MPV parity) — the countdown no longer fires at ≈95.7% and cuts
  the ending.
- **P-M4 (MEDIUM)**: the AniSkip AUTO latch is PER-VISIT — every episode switch clears the
  map (the intro key `<epKey>` + the outro `<epKey>|outro` together); a revisit re-arms the
  auto-skip instead of suppressing it forever. Seeking back within one viewing still never
  re-triggers (the latch's actual purpose).
- **P-M3 (MEDIUM)**: the DEAD Audio + Rotate fullscreen buttons removed from the MPV stack
  (the params too — the only call site never passed handlers; dead UI is worse than absent
  UI; the CS stack's own wired Audio button is unaffected).
- **M-3 (MEDIUM)**: the episode notification id folds the AUDIO VARIANT (sub=0/dub=1/
  unknown=2; the episode slot narrows ×2, the per-anime stride doubles to 20000 — the
  bound stays well inside Int.MAX). SUB and DUB of the same episode no longer replace each
  other's banner.
- **M-4 (MEDIUM)**: the episode counts store **TENTHS** (number*10) in the Long column —
  the fractional specials (12.5 → 125) stop re-detecting forever while the next REAL
  episode (13 → 130) still detects (the units-Long could break one or the other). The
  UpdateEngine (the no-new gate, the sub/dub insert gates, the stored counts) + the smart
  worker. DISCLOSED: the first check after this ships re-detects the latest episode ONCE
  (the feed badge re-arms; the notifications stay deduped by the sent log) — self-healing.

## 3. The verification + the ledger

- The brace/paren/NUL sanity across the 14 touched files: clean (the EpisodeBannerComposer's
  -2 paren delta is PRE-EXISTING string/comment content — verified against HEAD~1).
- CI: run 43461906085 (Build APK for 7b827f1b).
- **The release**: the user ordered the debug release again — **v1.1.87 / 10187** rides the
  round per Routine A (the section below the run verdict records the facts).
- Next decision: D-871; next record: 126.
