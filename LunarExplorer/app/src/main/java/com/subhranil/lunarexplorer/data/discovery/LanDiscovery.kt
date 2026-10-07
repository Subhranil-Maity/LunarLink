package com.subhranil.lunarexplorer.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.subhranil.lunarexplorer.data.servers.normalizeBaseUrl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** A LunarLink server announced on the local network. */
data class DiscoveredServer(
    val name: String,
    /** An IPv4 address, or an IPv6 address in brackets, ready to use in a URL. */
    val host: String,
    val port: Int,
    /** From the announcement; `null` if the server didn't say. */
    val authRequired: Boolean?,
    val osType: String?,
) {
    val baseUrl: String? get() = normalizeBaseUrl(host, port.toString())
}

/** Finds LunarLink servers on the local network. */
fun interface ServerDiscovery {
    /** Servers currently announced. Searching only happens while this is collected. */
    fun servers(): Flow<List<DiscoveredServer>>
}

/** [ServerDiscovery] using Android's DNS-SD (`_lunarlink._tcp`). Needs no runtime permission. */
class NsdServerDiscovery(context: Context) : ServerDiscovery {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override fun servers(): Flow<List<DiscoveredServer>> = callbackFlow {
        val found = linkedMapOf<String, DiscoveredServer>()
        fun publish() = trySend(found.values.sortedBy { it.name.lowercase() })
        publish()

        // Resolve one service at a time: before Android 14 only one resolve may be active.
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val lost = Channel<String>(Channel.UNLIMITED)
        launch {
            for (info in toResolve) {
                val server = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(info) }?.toServer() ?: continue
                found[info.serviceName] = server
                publish()
            }
        }
        launch {
            for (name in lost) {
                if (found.remove(name) != null) publish()
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.trySend(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                lost.trySend(info.serviceName)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed to start: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            toResolve.close()
            lost.close()
        }
    }

    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) resolveModern(info) else resolveLegacy(info)

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                if (cont.isActive) cont.resume(resolved)
            }

            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveModern(info: NsdServiceInfo): NsdServiceInfo? {
        var callback: NsdManager.ServiceInfoCallback? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val registered = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceUpdated(updated: NsdServiceInfo) {
                        if (updated.hostAddresses.isNotEmpty() && cont.isActive) cont.resume(updated)
                    }

                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                callback = registered
                nsd.registerServiceInfoCallback(info, EXECUTOR, registered)
            }
        } finally {
            // One answer is enough; stop listening for updates (also on timeout or cancellation).
            callback?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    private fun NsdServiceInfo.toServer(): DiscoveredServer? {
        val address = pickAddress(addresses()) ?: return null
        val txt = attributes.orEmpty().mapValues { (_, v) -> v?.decodeToString() }
        return DiscoveredServer(
            name = serviceName,
            host = urlHost(address) ?: return null,
            port = port,
            authRequired = txt["auth"]?.let { it == "1" },
            osType = txt["os"],
        )
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.addresses(): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) hostAddresses else listOfNotNull(host)

    companion object {
        const val SERVICE_TYPE = "_lunarlink._tcp"
        private const val TAG = "LanDiscovery"
        private const val RESOLVE_TIMEOUT_MS = 5_000L
        private val EXECUTOR = Executors.newSingleThreadExecutor()

        /** The address as a URL host: IPv6 goes in brackets, without a scope id. */
        fun urlHost(address: InetAddress): String? {
            val text = address.hostAddress?.substringBefore('%') ?: return null
            return if (address is Inet6Address) "[$text]" else text
        }

        /** IPv4 first; IPv6 only if it isn't link-local (those need a scope id the URL can't carry). */
        fun pickAddress(addresses: List<InetAddress>): InetAddress? {
            val usable = addresses.filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
            return usable.firstOrNull { it is Inet4Address } ?: usable.firstOrNull()
        }
    }
}
