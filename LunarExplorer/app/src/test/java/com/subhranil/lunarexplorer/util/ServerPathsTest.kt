package com.subhranil.lunarexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPathsTest {

    @Test
    fun detectsStyle() {
        assertEquals(PathStyle.POSIX, PathStyle.of("/home/me"))
        assertEquals(PathStyle.WINDOWS, PathStyle.of("C:\\Users"))
        assertEquals(PathStyle.WINDOWS, PathStyle.of("d:/data"))
        assertEquals(PathStyle.WINDOWS, PathStyle.of("C:"))
    }

    @Test
    fun joinUsesTheServersSeparator() {
        assertEquals("/home/me/a.txt", ServerPaths.join("/home/me", "a.txt"))
        assertEquals("/home/me/a.txt", ServerPaths.join("/home/me/", "a.txt"))
        assertEquals("/a", ServerPaths.join("/", "a"))
        assertEquals("C:\\Users\\me", ServerPaths.join("C:\\Users", "me"))
        assertEquals("C:\\me", ServerPaths.join("C:\\", "me"))
    }

    @Test
    fun parentAndName() {
        assertEquals("/home", ServerPaths.parent("/home/me"))
        assertEquals("/", ServerPaths.parent("/home"))
        assertNull(ServerPaths.parent("/"))
        assertEquals("C:\\", ServerPaths.parent("C:\\Users"))
        assertNull(ServerPaths.parent("C:\\"))
        assertEquals("me", ServerPaths.name("/home/me/"))
        assertEquals("/", ServerPaths.name("/"))
    }

    @Test
    fun containment() {
        assertTrue(ServerPaths.isSameOrInside("/home/me/a", "/home/me"))
        assertTrue(ServerPaths.isSameOrInside("/home/me", "/home/me/"))
        assertTrue(ServerPaths.isSameOrInside("/etc", "/"))
        assertFalse(ServerPaths.isSameOrInside("/home/me2", "/home/me"))
        assertTrue(ServerPaths.isSameOrInside("C:\\Users\\me", "C:\\"))
    }

    @Test
    fun crumbs() {
        val crumbs = ServerPaths.crumbs("/home/me", "Home", "/home/me/a/b")
        assertEquals(listOf("Home", "a", "b"), crumbs.map { it.name })
        assertEquals(listOf("/home/me", "/home/me/a", "/home/me/a/b"), crumbs.map { it.path })

        val win = ServerPaths.crumbs("C:\\", "C", "C:\\Users\\me")
        assertEquals(listOf("C:\\", "C:\\Users", "C:\\Users\\me"), win.map { it.path })
    }

    @Test
    fun validatesNames() {
        assertNull(validateName("notes.txt", PathStyle.POSIX))
        assertEquals(NameError.EMPTY, validateName("  ", PathStyle.POSIX))
        assertEquals(NameError.RESERVED, validateName("..", PathStyle.POSIX))
        assertEquals(NameError.INVALID_CHARACTER, validateName("a/b", PathStyle.POSIX))
        assertEquals(NameError.INVALID_CHARACTER, validateName("a\\b", PathStyle.POSIX))
        assertNull(validateName("what?", PathStyle.POSIX))
        assertEquals(NameError.INVALID_CHARACTER, validateName("what?", PathStyle.WINDOWS))
        assertEquals(NameError.INVALID_CHARACTER, validateName("trailing.", PathStyle.WINDOWS))
        assertEquals(NameError.TOO_LONG, validateName("x".repeat(256), PathStyle.POSIX))
    }

    @Test
    fun uniqueNames() {
        assertEquals("a.txt", uniqueName("a.txt", setOf("b.txt")))
        assertEquals("a (1).txt", uniqueName("a.txt", setOf("a.txt")))
        assertEquals("a (2).txt", uniqueName("a.txt", setOf("a.txt", "a (1).txt")))
        assertEquals("folder (1)", uniqueName("folder", setOf("folder")))
        assertEquals(".bashrc (1)", uniqueName(".bashrc", setOf(".bashrc")))
    }
}
