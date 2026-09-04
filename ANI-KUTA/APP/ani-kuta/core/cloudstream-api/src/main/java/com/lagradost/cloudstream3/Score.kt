// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3

import com.fasterxml.jackson.annotation.JsonAutoDetect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Primary way to store score/rating. Use [from] or [from10] to parse the score —
 * there is no public constructor. Internally stores an int scaled to 10^9
 * (10 significant digits): a fixed-point decimal class specifically for ratings.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Serializable
class Score private constructor(
    /** Decimal between [MIN] and [MAX] representing the min score and max score respectively. */
    @SerialName("data") private val data: Int,
) {
    override fun hashCode(): Int = data.hashCode()

    override fun equals(other: Any?): Boolean = other is Score && other.data == data

    @Deprecated(
        "toOld() is deprecated. Use other Score methods instead.",
        level = DeprecationLevel.ERROR,
    )
    fun toOld(): Int = toInt(10000)

    fun toByte(maxScore: Int): Byte = toInt(maxScore).toByte()

    fun toInt(maxScore: Int = 10): Int = ((data.toLong() * maxScore) / MAX).toInt()

    fun toLong(maxScore: Int = 10): Long = (data.toLong() * maxScore) / MAX

    fun toFloat(maxScore: Int = 10): Float = toDouble(maxScore).toFloat()

    fun toDouble(maxScore: Int = 10): Double = data.toDouble() * maxScore / MAX

    override fun toString(): String = toString(10)

    /**
     * Formats the rating to a human readable format (with no rounding).
     * May return null if the score is less than the minimum score.
     */
    @Throws(IllegalArgumentException::class)
    fun toStringNull(
        minScore: Double,
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.',
    ): String? {
        val current = toDouble(maxScore)
        if (current < minScore) return null
        return toString(maxScore, decimals, removeTrailingZeros, decimalChar)
    }

    /** Formats the rating to a human readable format (with no rounding). */
    @Throws(IllegalArgumentException::class)
    fun toString(
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.',
    ): String {
        require(maxScore != 0) { "maxScore must not be 0" }
        val formatted = String.format(
            Locale.ROOT,
            "%.${decimals}f",
            toDouble(maxScore),
        )
        val cleaned = if (removeTrailingZeros) formatted.trimEnd('0').trimEnd('.') else formatted
        return if (decimalChar == '.') cleaned else cleaned.replace('.', decimalChar)
    }

    companion object {
        const val MAX: Int = 1000_000_000
        const val MIN: Int = 0
        const val MAX_ZEROS: Int = 9
        private const val TAG: String = "Score"

        @Deprecated(
            "Score.fromOld is deprecated. Use other Score.from* methods instead.",
            level = DeprecationLevel.ERROR,
        )
        fun fromOld(value: Int?): Score? {
            if (value == null) return null
            if (value !in 0..10000) return null
            return Score(value * (MAX / 10000))
        }

        /** `value ∈ [0, maxScore]` — null when out of range. */
        fun from(value: Int?, maxScore: Int): Score? {
            if (value == null || maxScore <= 0) return null
            if (value < 0 || value > maxScore) return null
            return Score(((value.toLong() * MAX) / maxScore).toInt())
        }

        fun from(value: Double?, maxScore: Int): Score? {
            if (value == null || maxScore <= 0) return null
            if (value < 0.0 || value > maxScore) return null
            return Score((value * MAX / maxScore).toInt())
        }

        fun from(value: Float?, maxScore: Int): Score? =
            from(value?.toDouble(), maxScore)

        fun from(value: String?, maxScore: Int): Score? {
            if (value == null) return null
            val cleaned = value.trim().replace(',', '.')
            val parsed = cleaned.toDoubleOrNull() ?: return null
            return from(parsed, maxScore)
        }

        fun from5(value: Int?): Score? = from(value, 5)
        fun from10(value: Int?): Score? = from(value, 10)
        fun from100(value: Int?): Score? = from(value, 100)
        fun from5(value: Double?): Score? = from(value, 5)
        fun from10(value: Double?): Score? = from(value, 10)
        fun from100(value: Double?): Score? = from(value, 100)
        fun from5(value: Float?): Score? = from(value, 5)
        fun from10(value: Float?): Score? = from(value, 10)
        fun from100(value: Float?): Score? = from(value, 100)
        fun from5(value: String?): Score? = from(value, 5)
        fun from10(value: String?): Score? = from(value, 10)
        fun from100(value: String?): Score? = from(value, 100)
    }
}

/** enum class holds search quality. [Movie release types](https://en.wikipedia.org/wiki/Pirated_movie_release_types) */
@Suppress("UNUSED_PARAMETER")
enum class SearchQuality(value: Int?) {
    Cam(1),
    CamRip(2),
    HdCam(3),
    Telesync(4), // TS
    WorkPrint(5),
    Telecine(6), // TC
    HQ(7),
    HD(8),
    HDR(9), // high dynamic range
    BlueRay(10),
    DVD(11),
    SD(12),
    FourK(13),
    UHD(14),
    SDR(15), // standard dynamic range
    WebRip(16),
}
