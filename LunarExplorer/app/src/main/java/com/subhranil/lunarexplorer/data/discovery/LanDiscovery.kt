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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
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

    private sealed interface Event {
        data class Found(val info: NsdServiceInfo) : Event
        data class Resolved(val name: String, val server: DiscoveredServer?) : Event
        data class Lost(val name: String) : Event
    }

    override fun servers(): Flow<List<DiscoveredServer>> = callbackFlow {
        val found = linkedMapOf<String, DiscoveredServer>()
        fun publish() = trySend(found.values.sortedBy { it.name.lowercase() })
        publish()

        // Every change goes through one channel, so `found` is only touched by one coroutine.
        val events = Channel<Event>(Channel.UNLIMITED)
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        // Android 14+: one live watcher per service, so address changes (e.g. IPv4 arriving after IPv6) show up.
        val watchers = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()
        // Older versions: resolve one service at a time, as only one resolve may be active.
        val legacyQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        launch {
            for (event in events) {
                when (event) {
                    is Event.Found -> {
                        val name = event.info.serviceName
                        if (!modern) {
                            legacyQueue.trySend(event.info)
                        } else if (!watchers.containsKey(name)) {
                            watchers[name] = watch(event.info, events)
                        }
                    }
                    is Event.Resolved -> event.server?.let {
                        if (found[event.name] != it) {
                            found[event.name] = it
                            publish()
                        }
                    }
                    is Event.Lost -> {
                        watchers.remove(event.name)?.let(::unwatch)
                        if (found.remove(event.name) != null) publish()
                    }
                }
            }
        }
        if (!modern) {
            launch {
                for (info in legacyQueue) {
                    val server = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveLegacy(info) }?.toServer()
                    events.trySend(Event.Resolved(info.serviceName, server))
                }
            }
        }

        // Search again every few seconds while collected. Each search sends a fresh query, which is
        // answered with the server's IPv4 address; servers that start while the screen is open are
        // only noticed this way, because their start-up announcement isn't always picked up.
        var round = SearchRound(events)
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, round)
        launch {
            while (true) {
                delay(SEARCH_INTERVAL_MS)
                round.retire()
                runCatching { nsd.stopServiceDiscovery(round) }
                round = SearchRound(events)
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, round)
            }
        }
        awaitClose {
            round.retire()
            runCatching { nsd.stopServiceDiscovery(round) }
            watchers.values.forEach(::unwatch)
            watchers.clear()
            events.close()
            legacyQueue.close()
        }
    }

    /** One `discoverServices` run. Once retired, its late callbacks are ignored. */
    private class SearchRound(private val events: Channel<Event>) : NsdManager.DiscoveryListener {
        @Volatile
        private var retired = false

        fun retire() {
            retired = true
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            if (!retired) events.trySend(Event.Found(info))
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            // Stopping a round can report its services as lost; they haven't gone anywhere.
            if (!retired) events.trySend(Event.Lost(info.serviceName))
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "discovery failed to start: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
    }

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

    /** Follows one service until it's lost or discovery stops; every update re-reads its address. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun watch(info: NsdServiceInfo, events: Channel<Event>): NsdManager.ServiceInfoCallback {
        val name = info.serviceName
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(updated: NsdServiceInfo) {
                events.trySend(Event.Resolved(name, updated.toServer()))
            }

            override fun onServiceLost() {
                events.trySend(Event.Lost(name))
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.w(TAG, "can't follow $name: $errorCode")
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        nsd.registerServiceInfoCallback(info, EXECUTOR, callback)
        return callback
    }

    private fun unwatch(callback: NsdManager.ServiceInfoCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { nsd.unregisterServiceInfoCallback(callback) }
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
        private const val SEARCH_INTERVAL_MS = 15_000L
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
