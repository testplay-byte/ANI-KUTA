package com.confused.anikuta.data.cloudstream.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipOutputStream

/**
 * Task 58 (round 18): locks the shared-plugin file format — the
 * `<internalName>.moviebox.WHITECAT` naming, the case-insensitive stem
 * extraction, and the classloader-free manifest.json reader.
 */
class CsSharedPluginFormatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun writePluginZip(manifestJson: String?): File {
        val file = tmp.newFile("plugin.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            if (manifestJson != null) {
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(manifestJson.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `shared file name is internalName + custom extension`() {
        assertEquals(
            "MovieBoxProvider.moviebox.WHITECAT",
            CsSharedPluginFormat.sharedFileName("MovieBoxProvider"),
        )
    }

    @Test
    fun `isSharedPluginFile matches case-insensitively`() {
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("MovieBoxProvider.moviebox.WHITECAT"))
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("x.moviebox.whitecat"))
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("X.MOVIEBOX.WHITECAT"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("MovieBoxProvider.cs3"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("moviebox.WHITECAT.txt"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile(""))
    }

    @Test
    fun `internalName is the stem regardless of extension case`() {
        assertEquals(
            "MovieBoxProvider",
            CsSharedPluginFormat.internalNameFrom("MovieBoxProvider.moviebox.WHITECAT"),
        )
        assertEquals(
            "MovieBoxProvider",
            CsSharedPluginFormat.internalNameFrom("MovieBoxProvider.moviebox.whitecat"),
        )
        assertEquals(
            "Some Plugin",
            CsSharedPluginFormat.internalNameFrom("Some Plugin.moviebox.WhiteCat"),
        )
        // Blank stem → null; wrong extension → null.
        assertNull(CsSharedPluginFormat.internalNameFrom(".moviebox.WHITECAT"))
        assertNull(CsSharedPluginFormat.internalNameFrom("plain.cs3"))
    }

    @Test
    fun `readManifest parses a valid cs3 zip`() {
        val zip = writePluginZip(
            """
            {
              "name": "MovieBox",
              "pluginClassName": "com.example.MovieBoxProvider",
              "requiresResources": false,
              "version": 7
            }
            """.trimIndent(),
        )
        val manifest = CsSharedPluginFormat.readManifest(zip)
        assertNotNull(manifest)
        assertEquals("MovieBox", manifest?.name)
        assertEquals("com.example.MovieBoxProvider", manifest?.pluginClassName)
        assertEquals(7, manifest?.version)
        assertEquals(false, manifest?.requiresResources)
    }

    @Test
    fun `readManifest tolerates unknown keys like the loader`() {
        val zip = writePluginZip(
            """
            {
              "name": "X",
              "pluginClassName": "com.example.X",
              "unknownFutureField": {"a": 1}
            }
            """.trimIndent(),
        )
        val manifest = CsSharedPluginFormat.readManifest(zip)
        assertNotNull(manifest)
        assertEquals("X", manifest?.name)
    }

    @Test
    fun `readManifest returns null for missing entry, non-zip, or bad json`() {
        // No manifest.json entry at all.
        assertNull(CsSharedPluginFormat.readManifest(writePluginZip(null)))
        // Not a zip.
        val notZip = tmp.newFile("fake.zip")
        notZip.writeText("this is not a zip file")
        assertNull(CsSharedPluginFormat.readManifest(notZip))
        // A zip whose manifest.json is malformed JSON.
        assertNull(CsSharedPluginFormat.readManifest(writePluginZip("{not json")))
    }

    @Test
    fun `internalNameFor prefers the stem and falls back to the manifest name`() {
        val manifest = CsSharedPluginFormat.readManifest(
            writePluginZip("""{"name": "MovieBox", "pluginClassName": "c.X"}"""),
        )!!
        assertEquals(
            "StemName",
            CsSharedPluginFormat.internalNameFor("StemName.moviebox.WHITECAT", manifest),
        )
        // Opaque display name → the manifest's name (sanitized).
        assertEquals(
            "MovieBox",
            CsSharedPluginFormat.internalNameFor("content://downloads/42", manifest),
        )
        // Neither source available.
        assertNull(CsSharedPluginFormat.internalNameFor("plain.txt", null))
    }
}
