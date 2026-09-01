package com.confused.anikuta.data.cloudstream.installer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Task 58 (round 18) / Task 59 (round 19) / Task 60 (round 20): locks the
 * shared-plugin file format — the `<internalName>.WHITECAT` naming (Task 60:
 * `.WHITECAT` is the ONLY recognized extension — the round-18
 * `.moviebox.WHITECAT` name is deliberately NOT a shared-plugin name
 * anymore, per the user's round-20 "no legacy compatibility" spec), the
 * case-insensitive stem extraction, the classloader-free manifest.json
 * reader, and the round-19 EXPORT METADATA (the `anikuta/export.json` +
 * `anikuta/icon.png` entries the share writes and the import reads).
 */
class CsSharedPluginFormatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Unique counter — newFile() rejects duplicate names within one test. */
    private var zipCounter = 0

    private fun writePluginZip(manifestJson: String?): File {
        val file = tmp.newFile("plugin-${zipCounter++}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            if (manifestJson != null) {
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestJson.toByteArray())
                zip.closeEntry()
            }
            // A plausible dex entry so the rewrite copies non-meta entries.
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `shared file name is internalName + custom extension`() {
        assertEquals(
            "MovieBoxProvider.WHITECAT",
            CsSharedPluginFormat.sharedFileName("MovieBoxProvider"),
        )
    }

    @Test
    fun `isSharedPluginFile matches only the WHITECAT extension case-insensitively`() {
        // The round-19 extension (.WHITECAT).
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("MovieBoxProvider.WHITECAT"))
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("x.whitecat"))
        assertTrue(CsSharedPluginFormat.isSharedPluginFile("X.WHITECAT"))
        // Task 60: the round-18 legacy name is NOT recognized — a file
        // carrying it is "just a renamed file" (content-first import, no
        // shared-name semantics).
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("MovieBoxProvider.moviebox.WHITECAT"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("x.moviebox.whitecat"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("MovieBoxProvider.cs3"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile("moviebox.WHITECAT.txt"))
        assertFalse(CsSharedPluginFormat.isSharedPluginFile(""))
    }

    @Test
    fun `internalName is the stem regardless of extension case`() {
        assertEquals(
            "MovieBoxProvider",
            CsSharedPluginFormat.internalNameFrom("MovieBoxProvider.WHITECAT"),
        )
        assertEquals(
            "MovieBoxProvider",
            CsSharedPluginFormat.internalNameFrom("MovieBoxProvider.whitecat"),
        )
        // Task 60: no old-format handling — the legacy double tail is
        // REJECTED (internalNameFrom → null; the content-first path then
        // derives the identity from the manifest, never the old stem).
        assertNull(CsSharedPluginFormat.internalNameFrom("MovieBoxProvider.moviebox.WHITECAT"))
        assertEquals(
            "Some Plugin",
            CsSharedPluginFormat.internalNameFrom("Some Plugin.WhiteCat"),
        )
        // Blank stem → null; wrong extension → null.
        assertNull(CsSharedPluginFormat.internalNameFrom(".WHITECAT"))
        assertNull(CsSharedPluginFormat.internalNameFrom("plain.cs3"))
        assertNull(CsSharedPluginFormat.internalNameFrom("plain.bin"))
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
        // Not a zip — the .bin content-analysis path lands here.
        val notZip = tmp.newFile("fake.bin")
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
            CsSharedPluginFormat.internalNameFor("StemName.WHITECAT", manifest),
        )
        // Opaque display name (the .bin path) → the manifest's name (sanitized).
        assertEquals(
            "MovieBox",
            CsSharedPluginFormat.internalNameFor("content://downloads/42.bin", manifest),
        )
        // Neither source available.
        assertNull(CsSharedPluginFormat.internalNameFor("plain.txt", null))
    }

    // ── Task 59: the export metadata (anikuta/export.json + anikuta/icon.png) ──

    @Test
    fun `writeSharedFile copies the cs3 entries and appends the export metadata`() {
        val source = writePluginZip("""{"name": "MovieBox", "pluginClassName": "c.X"}""")
        val target = tmp.newFile("export.WHITECAT")

        CsSharedPluginFormat.writeSharedFile(
            source = source,
            target = target,
            info = CsExportInfo(
                repoUrl = "https://example.com/repo.json",
                iconUrl = "https://example.com/icon.png",
                name = "MovieBox",
                version = 7,
                authors = listOf("author1"),
            ),
            iconBytes = byteArrayOf(9, 8, 7),
        )

        // The original entries survive byte-identical (a zip re-read view).
        ZipFile(target).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            assertNotNull(zip.getEntry("classes.dex"))
            assertNotNull(zip.getEntry(CsSharedPluginFormat.EXPORT_INFO_ENTRY))
            assertNotNull(zip.getEntry(CsSharedPluginFormat.EXPORT_ICON_ENTRY))
            assertArrayEquals(
                byteArrayOf(9, 8, 7),
                zip.getInputStream(zip.getEntry(CsSharedPluginFormat.EXPORT_ICON_ENTRY))
                    .use { it.readBytes() },
            )
        }
        // The manifest still parses from the REWRITTEN zip.
        assertEquals("MovieBox", CsSharedPluginFormat.readManifest(target)?.name)
    }

    @Test
    fun `writeSharedFile without icon bytes omits the icon entry`() {
        val source = writePluginZip("""{"name": "X", "pluginClassName": "c.X"}""")
        val target = tmp.newFile("export-noicon.WHITECAT")

        CsSharedPluginFormat.writeSharedFile(target = target, source = source, info = CsExportInfo(repoUrl = "u"), iconBytes = null)

        ZipFile(target).use { zip ->
            assertNull(zip.getEntry(CsSharedPluginFormat.EXPORT_ICON_ENTRY))
            assertNotNull(zip.getEntry(CsSharedPluginFormat.EXPORT_INFO_ENTRY))
        }
    }

    @Test
    fun `writeSharedFile replaces stale metadata from an earlier share`() {
        // A source that is ITSELF a shared file (round-18 v0.4.6 output can
        // carry an old export entry) — the re-share writes FRESH metadata.
        val source = writePluginZip("""{"name": "X", "pluginClassName": "c.X"}""")
        val first = tmp.newFile("first.WHITECAT")
        CsSharedPluginFormat.writeSharedFile(source, first, CsExportInfo(repoUrl = "old"), byteArrayOf(1))
        val target = tmp.newFile("second.WHITECAT")
        CsSharedPluginFormat.writeSharedFile(first, target, CsExportInfo(repoUrl = "new"), null)

        assertEquals("new", CsSharedPluginFormat.readExportInfo(target)?.repoUrl)
        // The stale icon entry did not survive the re-share.
        ZipFile(target).use { zip ->
            assertNull(zip.getEntry(CsSharedPluginFormat.EXPORT_ICON_ENTRY))
        }
    }

    @Test
    fun `readExportInfo round-trips the metadata and tolerates absent entries`() {
        val source = writePluginZip("""{"name": "X", "pluginClassName": "c.X"}""")
        val target = tmp.newFile("roundtrip.WHITECAT")
        val info = CsExportInfo(
            repoUrl = "https://example.com/repo.json",
            iconUrl = "https://example.com/icon.png",
            name = "MovieBox",
            version = 7,
            language = "en",
            authors = listOf("a", "b"),
            description = "desc",
            tvTypes = listOf("TvSeries"),
            exportedAtMs = 123L,
        )
        CsSharedPluginFormat.writeSharedFile(source, target, info, byteArrayOf(1))

        val read = CsSharedPluginFormat.readExportInfo(target)
        assertEquals(info, read)
        // A plain .cs3 (no metadata) → null (the round-18 behavior).
        assertNull(CsSharedPluginFormat.readExportInfo(source))
        // Unknown future keys in the entry are ignored.
        val future = writePluginZip("""{"name": "Y", "pluginClassName": "c.Y"}""")
        val futureTarget = tmp.newFile("future.WHITECAT")
        ZipOutputStream(futureTarget.outputStream()).use { zip ->
            ZipFile(future).use { original ->
                for (entry in original.entries().asSequence()) {
                    zip.putNextEntry(ZipEntry(entry.name))
                    original.getInputStream(entry).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry(CsSharedPluginFormat.EXPORT_INFO_ENTRY))
            zip.write("""{"repoUrl": "u", "futureField": 1}""".toByteArray())
            zip.closeEntry()
        }
        assertEquals("u", CsSharedPluginFormat.readExportInfo(futureTarget)?.repoUrl)
    }

    @Test
    fun `readExportIcon returns the bytes or null`() {
        val source = writePluginZip("""{"name": "X", "pluginClassName": "c.X"}""")
        val withIcon = tmp.newFile("icon.WHITECAT")
        CsSharedPluginFormat.writeSharedFile(source, withIcon, CsExportInfo(), byteArrayOf(5, 6))
        assertArrayEquals(byteArrayOf(5, 6), CsSharedPluginFormat.readExportIcon(withIcon))

        val withoutIcon = tmp.newFile("noicon.WHITECAT")
        CsSharedPluginFormat.writeSharedFile(source, withoutIcon, CsExportInfo(), null)
        assertNull(CsSharedPluginFormat.readExportIcon(withoutIcon))
        // A plain .cs3 → null.
        assertNull(CsSharedPluginFormat.readExportIcon(source))
    }
}
