package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.data.thumbs.Thumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailTest {
    private fun file(name: String, mime: String?) = Entry(name, "/share/$name", EntryKind.FILE, size = 10, modified = 5, mimeType = mime)

    @Test
    fun onlySupportedImagesArePreviewed() {
        assertTrue(Thumbnail.canPreview(file("a.jpg", "image/jpeg")))
        assertTrue(Thumbnail.canPreview(file("a.webp", "image/webp")))
        assertFalse(Thumbnail.canPreview(file("a.svg", "image/svg+xml")))
        assertFalse(Thumbnail.canPreview(file("a.txt", "text/plain")))
        assertFalse(Thumbnail.canPreview(file("a", null)))
        assertFalse(Thumbnail.canPreview(Entry("pics", "/share/pics", EntryKind.DIR, mimeType = "image/jpeg")))
    }

    @Test
    fun requestDataTracksFileChanges() {
        val before = Thumbnail.of("s1", file("a.jpg", "image/jpeg"), 128)
        val after = Thumbnail.of("s1", file("a.jpg", "image/jpeg").copy(modified = 6), 128)
        assertEquals("/share/a.jpg", before.path)
        assertFalse(before == after)
    }
}
