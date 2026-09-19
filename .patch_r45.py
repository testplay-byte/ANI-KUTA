from pathlib import Path

# ── 1) the pill window gets REAL slack (D-468) ──
p = Path('ANI-KUTA/APP/ani-kuta/core/ads/src/main/java/com/confused/anikuta/core/ads/SmartLinkReturnPillController.kt')
t = p.read_text(encoding='utf-8')
old = """            // The D-454 jitter fix: the window is sized ONCE to the pill's
            // pre-measured READY shape (padding headroom included) — the
            // surface never resizes for the pill's whole life.
            width = view.measuredWidth
            height = view.measuredHeight"""
new = """            // The D-454 jitter fix: the window is sized ONCE — the surface
            // never resizes for the pill's whole life. D-468: the size is the
            // pre-measured READY shape PLUS generous slack — the v1.1.7
            // device round still showed the ready pill's sides and the pop's
            // top/bottom clipped, so an exact-fit window is too tight (sub-pixel
            // text-measure drift compounds). The capsule is centered in the
            // bigger window; the extra margin is non-interactive while
            // counting (FLAG_NOT_TOUCHABLE) and inert when ready.
            width = view.measuredWidth + (40 * density).toInt()
            height = view.measuredHeight + (24 * density).toInt()"""
assert old in t, "pill slack"; t = t.replace(old, new)
p.write_text(t, encoding='utf-8')
print('1) pill slack done')

# ── 2) the seekbar thumb border: thinner + darker (D-469) — MPV ──
p = Path('ANI-KUTA/APP/ani-kuta/core/player/src/main/java/com/confused/anikuta/core/player/controls/FullscreenControls.kt')
t = p.read_text(encoding='utf-8')
old = """            // D-463: the thumb carries the DARKER shadow now (moved here
            // from the track) + the light border ring stays.
            val halo = 4.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(thumbX - halo, thumbY - halo + 1.dp.toPx()),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            // The light border ring.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(thumbX - halo, thumbY - halo),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((4 + 3).dp.toPx(), (4 + 3).dp.toPx()),
            )"""
new = """            // D-463/D-469: the thumb carries the DARK shadow; the border ring
            // is now THINNER (2dp) and DARKER (black 40%) per the device round.
            val halo = 4.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(thumbX - halo, thumbY - halo + 1.dp.toPx()),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            val border = 2.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(thumbX - border, thumbY - border),
                size = Size(thumbSize + border * 2, thumbSize + border * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )"""
assert old in t, "mpv thumb ring"; t = t.replace(old, new)

# ── 3) exit-fs button: standalone + theme color (D-467) — MPV ──
old = """                                                FSSkipIconButton(onClick = onSkipForward)
                                                // D-463: the exit joins the SAME tray as
                                                // its neighbours with the plain chip
                                                // style, no shadow.
                                                FSSmallButton(icon = Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", onClick = onMinimize)
                                            }
                                        }"""
new = """                                                FSSkipIconButton(onClick = onSkipForward)
                                            }
                                        }
                                        // D-467: standalone again (NOT inside the
                                        // cluster tray) with its THEME-COLOR chip.
                                        FSExitButton(onClick = onMinimize)"""
assert old in t, "mpv exit call"; t = t.replace(old, new)

old = """@Composable
private fun FSInfoPill(text: String) {"""
new = """@Composable
private fun FSExitButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        // D-467: the exit-fullscreen button is STANDALONE (separate from the
        // bottom-right cluster tray, exactly like the other chips but on its
        // own) with its THEME-COLOR background — the user's round-45 spec.
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FSInfoPill(text: String) {"""
assert old in t, "mpv exit composable"; t = t.replace(old, new)

# ── 4) the seek pill padding per orientation (D-470) — MPV shared component ──
old = """@Composable
fun DoubleTapSeekIndicator(
    state: DoubleTapSeekState,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp),"""
new = """@Composable
fun DoubleTapSeekIndicator(
    state: DoubleTapSeekState,
    modifier: Modifier = Modifier,
    /** Distance from the screen edge. The FULLSCREEN surface passes 96dp
     * (clearly inward of the sides — D-462); the minimized/portrait surface
     * keeps the original 40dp (the user wants the pill AT the sides in
     * portrait — D-470). */
    sidePadding: androidx.compose.ui.unit.Dp = 40.dp,
) {
    if (!state.visible) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sidePadding),"""
assert old in t, "mpv indicator param"; t = t.replace(old, new)

old = """            // D-456: the cumulative double-tap seek pill — fullscreen parity
            // with the minimized view (renders regardless of controlsVisible,
            // exactly like the portrait feedback did).
            DoubleTapSeekIndicator(state = seekFeedback)"""
new = """            // D-456/D-470: the cumulative double-tap seek pill — fullscreen
            // keeps the pill INWARD (96dp); renders regardless of controls.
            DoubleTapSeekIndicator(state = seekFeedback, sidePadding = 96.dp)"""
assert old in t, "mpv fs call"; t = t.replace(old, new)

p.write_text(t, encoding='utf-8')
print('2-4) MPV done')

# ── 5) CS replicas ──
p = Path('ANI-KUTA/APP/ani-kuta/feature/cs-watch/impl/src/main/java/com/confused/anikuta/feature/cswatch/impl/CsPlayerControls.kt')
t = p.read_text(encoding='utf-8')

old = """            // D-463: the thumb carries the DARKER shadow now (moved here
            // from the track) + the light border ring stays.
            val halo = 4.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(thumbX - halo, thumbY - halo + 1.dp.toPx()),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(thumbX - halo, thumbY - halo),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )"""
new = """            // D-463/D-469: the thumb carries the DARK shadow; the border ring
            // is now THINNER (2dp) and DARKER (black 40%) per the device round.
            val halo = 4.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(thumbX - halo, thumbY - halo + 1.dp.toPx()),
                size = Size(thumbSize + halo * 2, thumbSize + halo * 2),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            val border = 2.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(thumbX - border, thumbY - border),
                size = Size(thumbSize + border * 2, thumbSize + border * 2),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )"""
assert old in t, "cs thumb ring"; t = t.replace(old, new)

old = """                                        CsFsSpeedButton(speed = currentSpeed, onClick = onSpeedClick)
                                        CsFsSkipIconButton(onClick = onSkipForward)
                                        // D-463: the exit joins the SAME tray as
                                        // its neighbours (the user's round-44 spec)
                                        // with the plain chip style, no shadow.
                                        CsFsSmallButton(
                                            icon = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit fullscreen",
                                            onClick = onMinimize,
                                        )
                                    }
                                }"""
new = """                                        CsFsSpeedButton(speed = currentSpeed, onClick = onSpeedClick)
                                        CsFsSkipIconButton(onClick = onSkipForward)
                                    }
                                }
                                // D-467: standalone again (NOT inside the cluster
                                // tray) with its THEME-COLOR chip.
                                CsFsExitButton(onClick = onMinimize)"""
assert old in t, "cs exit call"; t = t.replace(old, new)

old = """@Composable
private fun CsFsInfoPill(text: String) {"""
new = """@Composable
private fun CsFsExitButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        // D-467: the exit-fullscreen button is STANDALONE (separate from the
        // bottom-right cluster tray) with its THEME-COLOR background.
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CsFsInfoPill(text: String) {"""
assert old in t, "cs exit composable"; t = t.replace(old, new)

old = """@Composable
private fun CsDoubleTapSeekIndicator(state: CsDoubleTapSeekState, modifier: Modifier = Modifier) {
    if (!state.visible) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp),"""
new = """@Composable
private fun CsDoubleTapSeekIndicator(
    state: CsDoubleTapSeekState,
    modifier: Modifier = Modifier,
    sidePadding: androidx.compose.ui.unit.Dp = 40.dp,
) {
    if (!state.visible) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sidePadding),"""
assert old in t, "cs indicator param"; t = t.replace(old, new)

# CS fullscreen call site — need the state's composable to pass 96dp
old = """            // D-458: the cumulative seek pill — fullscreen parity with the
            // minimized view (previously the fullscreen seeked silently).
            CsDoubleTapSeekIndicator(state = seekFeedback)"""
new = """            // D-458/D-470: the cumulative seek pill — fullscreen keeps the
            // pill INWARD (96dp).
            CsDoubleTapSeekIndicator(state = seekFeedback, sidePadding = 96.dp)"""
assert old in t, "cs fs call"; t = t.replace(old, new)

p.write_text(t, encoding='utf-8')
print('5) CS done')
