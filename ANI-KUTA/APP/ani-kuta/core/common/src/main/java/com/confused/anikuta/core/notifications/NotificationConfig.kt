package com.confused.anikuta.core.notifications

/**
 * Notification trigger state (tri-state).
 *
 * Stored in the DB as INTEGER: 0 = OFF, 1 = ON, 2 = SILENT. Backward-compatible
 * with the previous boolean schema (old 0/1 data still maps to OFF/ON).
 *
 * - [ON] — post the notification with default priority (sound + popup).
 * - [SILENT] — post the notification with low priority (no sound, banner only).
 * - [OFF] — don't post for this trigger.
 *
 * The 3-way lets the user pick per-trigger how loud the alert should be, and
 * gives the description-adapting UI three distinct labels to show.
 */
enum class TriggerState(val dbValue: Long) {
    OFF(0L),
    ON(1L),
    SILENT(2L);

    companion object {
        fun fromDb(value: Long): TriggerState = when (value.toInt()) {
            1 -> ON
            2 -> SILENT
            else -> OFF
        }
    }
}

/**
 * Audio-variant notification preference (tri-state, derived from the two DB
 * booleans `notify_sub` + `notify_dub`).
 *
 * - [SUB] — notify for subbed releases only (sub=1, dub=0).
 * - [DUB] — notify for dubbed releases only (sub=0, dub=1).
 * - [BOTH] — notify for both sub and dub releases (sub=1, dub=1).
 *
 * Stored as two booleans (no schema change); this enum is the UI-facing
 * representation. (sub=0, dub=0 is treated as BOTH as a sensible default —
 * "notify for nothing" is never what a user who enabled notifications wants.)
 */
enum class AudioPref {
    SUB, DUB, BOTH;

    companion object {
        /** Derive the audio pref from the two stored booleans. (0,0) → BOTH. */
        fun fromBooleans(notifySub: Boolean, notifyDub: Boolean): AudioPref = when {
            notifySub && notifyDub -> BOTH
            notifyDub -> DUB
            else -> SUB
        }

        /** The sub boolean for this pref. */
        fun subBoolean(): Boolean = this != DUB

        /** The dub boolean for this pref. */
        fun dubBoolean(): Boolean = this != SUB
    }
}

/**
 * Per-content notification config (Phase NOTIF).
 *
 * Triggers are tri-state ([TriggerState]); audio is a tri-state ([AudioPref])
 * derived from the two stored booleans. [enabled] is the per-anime master toggle.
 */
data class NotificationConfig(
    val mainId: String,
    val enabled: Boolean = true,
    val notifyOnSchedule: TriggerState = TriggerState.OFF,
    val notifyOnWatchable: TriggerState = TriggerState.ON,
    val notifyOnImmediate: TriggerState = TriggerState.OFF,
    val audioPref: AudioPref = AudioPref.SUB,
) {
    /** The two DB booleans for audio (backward-compat with the schema). */
    val notifySub: Boolean get() = audioPref.subBoolean()
    val notifyDub: Boolean get() = audioPref.dubBoolean()
}
