// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Task 47 (playback session): the REAL P.A.C.K.E.R. unpacker. Nearly every
// jwplayer-style embed page (StreamWish / Filesim / Filemoon / Vidmoly /
// VidHide / Emturbovid mirrors) ships its `sources`/`tracks` payload inside
// `eval(function(p,a,c,k,e,d){...}('payload',radix,count,'tokens'.split('|')))`
// obfuscation — without unpacking, none of those hosts yield links.
//
// Algorithm (public, from the packer's own documented format): the payload is
// the original JS with words replaced by base-N placeholders; each placeholder
// maps to a token by index (an EMPTY token means "leave the placeholder in
// place"). Unpacking = a single left-to-right pass replacing every \w+ word
// with its token (multi-pass when the result itself contains another packed
// block).
package com.lagradost.cloudstream3.utils

/** Detects and unpacks P.A.C.K.E.R.-obfuscated JavaScript. */
class JsUnpacker(packedJS: String?) {
    private var packedJS: String? = packedJS

    /** Detects whether the javascript is P.A.C.K.E.R. coded. */
    fun detect(): Boolean {
        return getPacked(packedJS ?: return false) != null
    }

    /**
     * Unpack the javascript; @return the javascript unpacked or null when
     * nothing could be unpacked (the caller keeps the original input).
     */
    fun unpack(): String? {
        val js = packedJS ?: return null
        var current = js
        var changed = false
        // Multi-pass: packed blocks can nest (eval of an eval) or several can
        // sit side by side in one page.
        while (true) {
            val unpacked = unpackFirstBlock(current) ?: break
            current = unpacked
            changed = true
        }
        return if (changed) current else null
    }

    companion object {
        // Well-known marker strings used to detect packed ad-bootstraps.
        val c = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, 0x2e, 0x61, 0x6e, 0x64,
            0x72, 0x6f, 0x69, 0x64, 0x2e, 0x67, 0x6d, 0x73, 0x2e, 0x61, 0x64, 0x73, 0x2e, 0x4d,
            0x6f, 0x62, 0x69, 0x6c, 0x65, 0x41, 0x64, 0x73,
        )
        val z = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x66, 0x61, 0x63, 0x65, 0x62, 0x6f, 0x6f, 0x6b, 0x2e, 0x61,
            0x64, 0x73, 0x2e, 0x41, 0x64,
        )

        fun String.load(): String? = null

        /**
         * The packed invocation's argument list:
         * `}('payload',radix,count,'t1|t2|…'.split('|')`
         *
         * Payload and token strings cannot contain a RAW quote (quotes are
         * escaped as \' — matched by the `\\.` alternative), which keeps the
         * match inside ONE packed block even when several sit side by side.
         */
        private val packedArgsRegex = Regex(
            pattern = """\}\('((?:\\.|[^'])*)',(\d+),(\d+),'((?:\\.|[^'])*)'\.split\('\|'\)""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

        /** Base-N digits used by the packer (max radix 62). */
        private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

        /** Decodes a packer placeholder word; null when it is not a valid digit. */
        private fun decode(word: String, radix: Int): Int? {
            if (word.isEmpty()) return null
            var value = 0
            for (ch in word) {
                val digit = ALPHABET.indexOf(ch)
                if (digit < 0 || digit >= radix) return null
                value = value * radix + digit
            }
            return value
        }

        /** Unescapes the packer's string escapes (\\ and \'). */
        private fun unescapePacked(s: String): String =
            s.replace("\\\\", "\u0000").replace("\\'", "'").replace("\u0000", "\\")

        /**
         * Finds the FIRST packed block in [js] and returns the whole input
         * with that block replaced by its unpacked body (null = none found).
         */
        private fun unpackFirstBlock(js: String): String? {
            val argsMatch = packedArgsRegex.find(js) ?: return null
            // The invocation is wrapped in eval( — find it backwards from the
            // argument list, then locate its BALANCED close paren (the
            // function body + nested parens + strings sit in between).
            val evalStart = js.lastIndexOf("eval(", startIndex = argsMatch.range.first)
            if (evalStart < 0) return null
            val openParen = evalStart + "eval(".length - 1
            val evalEnd = findBalancedClose(js, openParen) ?: return null

            val payload = unescapePacked(argsMatch.groupValues[1])
            val radix = argsMatch.groupValues[2].toIntOrNull() ?: return null
            val count = argsMatch.groupValues[3].toIntOrNull() ?: return null
            val tokens = argsMatch.groupValues[4].split('|')

            // Single left-to-right pass — a token containing another
            // placeholder's word must NOT be re-replaced.
            val unpacked = Regex("""\b\w+\b""").replace(payload) { match ->
                val index = decode(match.value, radix)
                val token = index?.takeIf { it in 0 until count }?.let { tokens.getOrNull(it) }
                if (token.isNullOrEmpty()) match.value else token
            }

            return js.replaceRange(evalStart, evalEnd + 1, unpacked)
        }

        /**
         * Index of the ')' closing the paren opened at [openIndex], skipping
         * string literals and escape sequences; null when unbalanced.
         */
        private fun findBalancedClose(js: String, openIndex: Int): Int? {
            var depth = 0
            var inString: Char? = null
            var i = openIndex
            while (i < js.length) {
                val ch = js[i]
                if (inString != null) {
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == inString) inString = null
                } else {
                    when (ch) {
                        '\'', '"' -> inString = ch
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) return i
                        }
                    }
                }
                i++
            }
            return null
        }
    }
}
