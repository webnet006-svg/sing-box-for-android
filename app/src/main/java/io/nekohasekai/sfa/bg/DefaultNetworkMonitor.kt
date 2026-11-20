package io.nekohasekai.sfa.bg

import android.net.Network
import android.os.Build
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.sfa.Application
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null
    private var networkChangedListener: (() -> Unit)? = null

    suspend fun start() {
        var started = false
        var networkWasLost = false
        DefaultNetworkListener.start(this) { network ->
            val previous = defaultNetwork
            defaultNetwork = network
            checkDefaultInterfaceUpdate(network)
            if (started && network == null) {
                networkWasLost = true
            } else if (started && network != null) {
                if (networkWasLost || (previous != null && previous != network)) {
                    networkWasLost = false
                    networkChangedListener?.invoke()
                }
            }
        }
        defaultNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.connectivity.activeNetwork
        } else {
            DefaultNetworkListener.get()
        }
        networkWasLost = false
        started = true
    }

    suspend fun stop() {
        networkChangedListener = null
        DefaultNetworkListener.stop(this)
    }

    /** Appelé quand l'interface réseau par défaut change réellement. */
    fun setNetworkChangedListener(listener: (() -> Unit)?) {
        networkChangedListener = listener
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) {
            return network
        }
        return DefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val listener = listener ?: return
        if (newNetwork != null) {
            for (times in 0 until 10) {
                val linkProperties = Application.connectivity.getLinkProperties(newNetwork)
                if (linkProperties == null) {
                    Thread.sleep(100)
                    continue
                }
                var interfaceIndex: Int
                try {
                    interfaceIndex = NetworkInterface.getByName(linkProperties.interfaceName).index
                } catch (e: Exception) {
                    Thread.sleep(100)
                    continue
                }
                listener.updateDefaultInterface(linkProperties.interfaceName, interfaceIndex, false, false)
                return
            }
        } else {
            listener.updateDefaultInterface("", -1, false, false)
        }
    }
}
