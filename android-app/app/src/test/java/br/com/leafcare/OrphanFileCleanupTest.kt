package br.com.leafcare

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Start-up cleanup removes only old leftovers that no analysis references. */
class OrphanFileCleanupTest {

    @Test fun removesOnlyOldUnreferencedLeftovers() = runTest {
        val root = Files.createTempDirectory("leafcare-cleanup").toFile()
        try {
            val photos = File(root, "photos").apply { mkdirs() }
            val cache = File(root, "cache").apply { mkdirs() }
            val now = 10 * ORPHAN_FILE_MIN_AGE_MS
            val old = now - ORPHAN_FILE_MIN_AGE_MS - 1
            fun file(dir: File, name: String, modified: Long) =
                File(dir, name).apply { writeText("x"); setLastModified(modified) }

            val referenced = file(photos, "kept.img", old)
            val orphan = file(photos, "orphan.img", old)
            val freshOrphan = file(photos, "fresh.img", now)
            val staleTmp = file(photos, "kept.img.tmp", old)
            val unknown = file(photos, "notes.txt", old)
            val oldCapture = file(cache, "capture-1.jpg", old)
            val freshCapture = file(cache, "capture-2.jpg", now)
            val otherCache = file(cache, "other.jpg", old)

            cleanupOrphanFiles(photos, cache, now) { it == "kept.img" }

            assertTrue(referenced.exists())
            assertFalse(orphan.exists())
            assertTrue(freshOrphan.exists())
            assertFalse(staleTmp.exists())
            assertTrue(unknown.exists())
            assertFalse(oldCapture.exists())
            assertTrue(freshCapture.exists())
            assertTrue(otherCache.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
