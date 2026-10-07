package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.discovery.DiscoveredServer
import com.subhranil.lunarexplorer.data.discovery.NsdServerDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class LanDiscoveryTest {
    private fun ip(literal: String) = InetAddress.getByName(literal)

    @Test
    fun prefersIpv4AndSkipsUnusableAddresses() {
        val pick = NsdServerDiscovery::pickAddress
        assertEquals(ip("192.168.1.20"), pick(listOf(ip("2001:db8::1"), ip("192.168.1.20"))))
        assertEquals(ip("2001:db8::1"), pick(listOf(ip("fe80::1"), ip("2001:db8::1"))))
        assertNull(pick(listOf(ip("127.0.0.1"), ip("fe80::1"))))
        assertNull(pick(emptyList()))
    }

    @Test
    fun baseUrlsMatchSavedServers() {
        assertEquals("http://192.168.1.20:1234", DiscoveredServer("x", "192.168.1.20", 1234, null, null).baseUrl)
        assertEquals("http://[2001:db8::1]:1234", DiscoveredServer("x", "[2001:db8::1]", 1234, null, null).baseUrl)
        assertEquals("[2001:db8:0:0:0:0:0:1]", NsdServerDiscovery.urlHost(ip("2001:db8::1")))
        assertEquals("10.0.0.2", NsdServerDiscovery.urlHost(ip("10.0.0.2")))
    }
}
