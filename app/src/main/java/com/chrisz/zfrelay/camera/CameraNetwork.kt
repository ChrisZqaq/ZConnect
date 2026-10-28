package com.chrisz.zfrelay.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class CameraNetworkHandle(
    val gateway: String?,
    private val network: Network,
    private val connectivityManager: ConnectivityManager,
    private val callback: ConnectivityManager.NetworkCallback?,
) : Closeable {
    fun newSocket(): Socket = network.socketFactory.createSocket()

    fun canReach(host: String, port: Int = 15740): Boolean = runCatching {
        newSocket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1_200)
        }
    }.isSuccess

    override fun close() {
        val networkCallback = callback ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }
}

internal class CameraNetworkProvider(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun acquire(): CameraNetworkHandle {
        connectivityManager.activeNetwork?.let { active ->
            val capabilities = connectivityManager.getNetworkCapabilities(active)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return CameraNetworkHandle(gateway(active), active, connectivityManager, null)
            }
        }

        val latch = CountDownLatch(1)
        var cameraNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cameraNetwork == null) {
                    cameraNetwork = network
                    latch.countDown()
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        return try {
            connectivityManager.requestNetwork(request, callback)
            if (!latch.await(12, TimeUnit.SECONDS)) {
                throw IOException("未找到相机 Wi-Fi，请先连接")
            }
            val network = cameraNetwork ?: throw IOException("相机 Wi-Fi 不可用")
            CameraNetworkHandle(gateway(network), network, connectivityManager, callback)
        } catch (error: Throwable) {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            throw error
        }
    }

    private fun gateway(network: Network): String? =
        connectivityManager.getLinkProperties(network)?.routes
            ?.firstNotNullOfOrNull { route -> route.gateway?.hostAddress }
}
