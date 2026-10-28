package com.chrisz.zfrelay.camera

import android.content.Context
import com.chrisz.zfrelay.ptp.DeviceInfo
import com.chrisz.zfrelay.ptp.PhotoObject
import com.chrisz.zfrelay.ptp.PtpIpClient
import java.io.Closeable
import java.io.IOException

internal class CameraRepository(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val networkProvider = CameraNetworkProvider(appContext)
    private var networkHandle: CameraNetworkHandle? = null
    private var client: PtpIpClient? = null

    @Synchronized
    fun connect(manualHost: String): DeviceInfo {
        close()
        val network = networkProvider.acquire()
        networkHandle = network
        val candidates = buildList {
            if (manualHost.isNotBlank()) add(manualHost.trim())
            network.gateway?.let(::add)
            add("192.168.1.1")
        }.distinct()
        return try {
            var lastError: IOException? = null
            for (host in candidates) {
                val camera = PtpIpClient(host, socketFactory = network::newSocket)
                try {
                    val info = camera.connect()
                    client = camera
                    return info
                } catch (error: IOException) {
                    camera.close()
                    lastError = error
                }
            }
            throw lastError ?: IOException("未找到相机，请检查 Wi-Fi 连接")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun listPhotos(): Pair<Int, List<PhotoObject>> =
        requireClient().listPhotos()

    fun thumbnail(handle: Long): ByteArray = requireClient().getThumbnail(handle)

    fun downloader(): PhotoDownloader = PhotoDownloader(appContext, requireClient())

    private fun requireClient(): PtpIpClient = client ?: throw IOException("相机已断开，请重新连接")

    @Synchronized
    override fun close() {
        runCatching { client?.close() }
        runCatching { networkHandle?.close() }
        client = null
        networkHandle = null
    }
}
