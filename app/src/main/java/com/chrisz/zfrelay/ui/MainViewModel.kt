package com.chrisz.zfrelay.ui

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisz.zfrelay.camera.CameraRepository
import com.chrisz.zfrelay.ptp.DeviceInfo
import com.chrisz.zfrelay.ptp.PhotoObject
import com.chrisz.zfrelay.ptp.PtpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
internal enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

internal data class DownloadItem(
    val photo: PhotoObject,
    val transferred: Long = 0,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val error: String? = null,
    val uri: String? = null,
)

internal data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceInfo: DeviceInfo? = null,
    val photos: List<PhotoObject> = emptyList(),
    val totalPhotoCount: Int = 0,
    val loadingPhotos: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val downloads: List<DownloadItem> = emptyList(),
) {
    val downloading: Boolean get() = downloads.any { it.status == DownloadStatus.DOWNLOADING }
}

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CameraRepository(application)
    private val thumbnailCache = LruCache<Long, ByteArray>(24)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun connect(manualHost: String) {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTING) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    error = null,
                    photos = emptyList(),
                    deviceInfo = null,
                )
            }
            runCatching { withContext(Dispatchers.IO) { repository.connect(manualHost) } }
                .onSuccess { info ->
                    _state.update {
                        it.copy(connectionStatus = ConnectionStatus.CONNECTED, deviceInfo = info, selectedTab = 0)
                    }
                    refreshPhotos()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(connectionStatus = ConnectionStatus.ERROR, error = humanError(error))
                    }
                }
        }
    }

    fun refreshPhotos() {
        if (_state.value.connectionStatus != ConnectionStatus.CONNECTED || _state.value.loadingPhotos) return
        viewModelScope.launch {
            _state.update { it.copy(loadingPhotos = true, error = null) }
            runCatching { withContext(Dispatchers.IO) { repository.listPhotos() } }
                .onSuccess { (total, photos) ->
                    _state.update {
                        it.copy(
                            loadingPhotos = false,
                            totalPhotoCount = total,
                            photos = photos.filter { photo -> photo.isJpeg || photo.isRaw },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingPhotos = false, error = humanError(error)) }
                }
        }
    }

    suspend fun thumbnail(handle: Long): ByteArray = withContext(Dispatchers.IO) {
        synchronized(thumbnailCache) { thumbnailCache.get(handle) }?.let { return@withContext it }
        val bytes = repository.thumbnail(handle)
        synchronized(thumbnailCache) { thumbnailCache.put(handle, bytes) }
        bytes
    }

    fun download(photo: PhotoObject) {
        if (_state.value.downloading || !photo.isJpeg) return
        val item = DownloadItem(photo)
        _state.update { state ->
            state.copy(downloads = listOf(item) + state.downloads.filterNot { it.photo.handle == photo.handle })
        }
        viewModelScope.launch {
            runCatching {
                repository.downloader().download(photo) { transferred ->
                    updateDownload(photo.handle) { it.copy(transferred = transferred) }
                }
            }.onSuccess { uri ->
                updateDownload(photo.handle) {
                    it.copy(
                        transferred = photo.size,
                        status = DownloadStatus.COMPLETED,
                        uri = uri.toString(),
                    )
                }
            }.onFailure { error ->
                updateDownload(photo.handle) {
                    it.copy(status = DownloadStatus.FAILED, error = humanError(error))
                }
            }
        }
    }

    private fun updateDownload(handle: Long, transform: (DownloadItem) -> DownloadItem) {
        _state.update { state ->
            state.copy(downloads = state.downloads.map { item ->
                if (item.photo.handle == handle) transform(item) else item
            })
        }
    }

    fun selectTab(index: Int) = _state.update { it.copy(selectedTab = index) }

    fun disconnect() {
        repository.close()
        _state.update {
            UiState(downloads = it.downloads)
        }
    }

    private fun humanError(error: Throwable): String = when (error) {
        is PtpException -> error.message ?: "连接失败"
        is SocketTimeoutException -> "连接超时，请重试"
        is UnknownHostException -> "相机 IP 无效"
        is IOException -> error.message ?: "连接中断，请重试"
        else -> "操作失败，请重试"
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
