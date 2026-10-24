import io
p = "AGENT-CONTEXT/SESSION.md"
t = io.open(p, encoding="utf-8").read()

start = t.find("- **Round 124 (doc 106, D-770..D-778):**")
end = t.find("\n", t.find("planning\\2026-10-24-round-123-device-test.html"))
assert start != -1 and end != -1, "bullet bounds"
new_bullet = (
    "- **Round 125 (doc 107, D-780..D-784):** the fifth device round (\"let's make it our final "
    "improvements in these areas\"). CONFIRMED by the user: the Up Next 10s window + replay "
    "re-offer, the honest watchdog, the scrim fade, the drawer centering, the skip-entry removal. "
    "THE CARD ON FAILURE: the round-124 arming kept a dur<=0 branch — a dead stream exploits both "
    "orderings (EOF pin before duration; a single pos=0) — arming is STRICT mid-file now and the "
    "poll band is 0f..10f. THE PORTRAIT REVERT (the round's process lesson — the user: \"properly "
    "revert this completely to the exact same how it was before\"): the drawers are FULLSCREEN-only "
    "via `asDrawer = playerMode == FULLSCREEN`; bodies byte-identical; portrait has the exact old "
    "bottom sheets back. THE SHELL: shared PlayerSideDrawerShell (core.player, fromLeft/width) — "
    "the fullscreen quality/subtitle drawers are wider (0.45/400dp) and the subtitle SETTINGS panel "
    "slides from the LEFT in fullscreen. THE TRUE DRAG PORT: the round-124 port's task.id gesture "
    "key KILLED the live gesture on every swap (the glitch root, mechanically) — now 1:1 with "
    "DragReorderableList (pointerInput(Unit) per slot, opaque dragged surface + shadow, tinted "
    "rows, no scroll wrapper); 'Paused — 0%' reads 'Paused'. THE DETAILS CHAIN: five writers fed "
    "blanks to a strict display (refresh/reopen raw assignments, the raw-int status link write, "
    "the one non-merging enrichment, the restore dropping year/score) — all fixed; the schema "
    "untouched; the user-ordered DATABASE AUDIT delivered as the workspace "
    "planning/2026-10-25-database-flaws-report.html (B1-B7 ranked, several awaiting the user's "
    "word/section-30). Commit 781b5a81; run 40143457156 GREEN first-try (ONE run, inside D-472). "
    "**The user's device round on this build is the next input** — the checklist: "
    "`C:\\Users\\khurr\\Desktop\\ANI-KUTA\\planning\\2026-10-25-round-125-device-test.html` "
    "(+ the DB report for their read)."
)
t = t[:start] + new_bullet + t[end:]

old = "latest = **D-778** (round 124, the notifications re-arm; D-770..D-777 beside it — the stale holder, the honest watchdog, the ten-second window, AniSkip's two killers, the drawer set, the scrim, the drag on the reference pattern, the persisted ext title)."
new = "latest = **D-784** (round 125, the details/display chain; D-780..D-783 beside it — the strict arming, the portrait revert, the wider drawer + left settings, the true drag port)."
assert old in t, "decisions line"
t = t.replace(old, new, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(t)
print("SESSION fully refreshed")
