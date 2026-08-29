// Task 47 (playback session): unit tests for the real P.A.C.K.E.R. unpacker —
// the foundation of the whole jwplayer-family extractor engine.
package com.lagradost.cloudstream3.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsUnpackerTest {

    /** Builds a packer invocation exactly in the documented format. */
    private fun packed(payload: String, radix: Int, tokens: List<String>): String {
        val functionBody = "function(p,a,c,k,e,d){while(c--)d[c.toString($radix)]=k[c]||c.toString($radix);" +
            "return p.replace(/\\b\\w+\\b/g,function(c){return d[c]})}"
        return "eval($functionBody('$payload',$radix,${tokens.size},'${tokens.joinToString("|")}'" +
            ".split('|'),0,{}))"
    }

    @Test
    fun `detects packed javascript`() {
        val js = packed("0.1(2)", 10, listOf("log", "console", "hello"))
        assertTrue(JsUnpacker(js).detect())
        assertFalse(JsUnpacker("var x = 1;").detect())
    }

    @Test
    fun `unpacks base10 tokens`() {
        // payload words map: 0→log, 1→console, 2→hello, 3→(empty → stays "3")
        val js = packed("1.0(2 + 3)", 10, listOf("log", "console", "hello", ""))
        val unpacked = JsUnpacker(js).unpack()
        assertEquals("console.log(hello + 3)", unpacked)
    }

    @Test
    fun `unpacks base36 tokens`() {
        // radix 36: index 10 = "a", 11 = "b"
        val js = packed("a(b)", 36, List(10) { "t$it" } + listOf("alert", "hi"))
        assertEquals("alert(hi)", JsUnpacker(js).unpack())
    }

    @Test
    fun `unpacks multiple blocks in one script`() {
        val block1 = packed("0(1)", 10, listOf("first", "call"))
        val block2 = packed("0(1)", 10, listOf("second", "go"))
        val js = "var a = $block1; var b = $block2;"
        val unpacked = JsUnpacker(js).unpack()
        assertTrue(unpacked!!.contains("first(call)"))
        assertTrue(unpacked.contains("second(go)"))
    }

    @Test
    fun `unescapes payload quotes and backslashes`() {
        // Real packer output escapes every quote INSIDE the payload as \' —
        // the raw text is: x(\'It\'s here\')
        val js = packed("x(\\'It\\'s here\\')", 10, listOf("sources"))
        val unpacked = JsUnpacker(js).unpack()
        assertEquals("sources('It's here')", unpacked)
    }

    @Test
    fun `returns null when nothing is packed`() {
        assertNull(JsUnpacker("console.log('plain')").unpack())
    }

    @Test
    fun `a real streamwish-shaped packed sources block`() {
        // Hand-built in the packer's own format: sources array with an m3u8 URL.
        val tokens = listOf("", "sources", "file", "label")
        // Build the payload by substituting words: 1:[{2:"https://…",3:"720p"}]
        val payload = "1:[{2:\"https://cdn.example/hls/master.m3u8\",3:\"720p\"}]"
        val js = packed(payload, 10, tokens)
        val unpacked = JsUnpacker(js).unpack()
        assertEquals(
            "sources:[{file:\"https://cdn.example/hls/master.m3u8\",label:\"720p\"}]",
            unpacked,
        )
    }
}
