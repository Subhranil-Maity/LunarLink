package com.subhranil.lunarexplorer.util

import com.subhranil.lunarexplorer.data.servers.normalizeBaseUrl
import com.subhranil.lunarexplorer.data.servers.splitBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test
    fun formatsBytesWithOneDecimal() {
        assertEquals("0 B", formatBytes(0, Locale.US))
        assertEquals("1023 B", formatBytes(1023, Locale.US))
        assertEquals("1.0 KB", formatBytes(1024, Locale.US))
        assertEquals("1.5 KB", formatBytes(1536, Locale.US))
        assertEquals("1.9 GB", formatBytes((1.9 * 1024 * 1024 * 1024).toLong(), Locale.US))
    }

    @Test
    fun categorizesFiles() {
        assertEquals(FileCategory.IMAGE, fileCategory("photo.JPG"))
        assertEquals(FileCategory.PDF, fileCategory("doc.pdf"))
        assertEquals(FileCategory.OTHER, fileCategory("Makefile"))
        assertEquals(FileCategory.OTHER, fileCategory(".bashrc"))
        assertEquals(FileCategory.OTHER, fileCategory("weird."))
    }

    @Test
    fun normalizesHostInput() {
        assertEquals("http://192.168.1.20:1234", normalizeBaseUrl("192.168.1.20", "1234"))
        assertEquals("http://192.168.1.20", normalizeBaseUrl(" 192.168.1.20/ ", ""))
        assertEquals("https://files.example.com", normalizeBaseUrl("https://files.example.com", ""))
        assertEquals("https://files.example.com:8443", normalizeBaseUrl("HTTPS://files.example.com:8443", ""))
        assertEquals("http://host:99", normalizeBaseUrl("http://host:1234", "99"))
        assertEquals("http://[::1]:1234", normalizeBaseUrl("[::1]", "1234"))
        assertNull(normalizeBaseUrl("", "1234"))
        assertNull(normalizeBaseUrl("host", "70000"))
        assertNull(normalizeBaseUrl("ftp://host", ""))
        assertNull(normalizeBaseUrl("host/path", ""))
        assertNull(normalizeBaseUrl("bad host", ""))
    }

    @Test
    fun splitsBaseUrlForEditing() {
        assertEquals("192.168.1.20" to "1234", splitBaseUrl("http://192.168.1.20:1234"))
        assertEquals("https://files.example.com" to "", splitBaseUrl("https://files.example.com"))
    }
}
