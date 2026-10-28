package com.chrisz.zfrelay.camera

import com.chrisz.zfrelay.ptp.PhotoObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhotoDownloaderTest {
    @Test
    fun `partial file resumes within one connection but never across reconnect`() {
        val directory = File("cache/downloads")
        val photo = PhotoObject(7, 1, 0x3801, 8_500_000, "DSC_0007.JPG", "", 24_000)

        val firstAttempt = partialFile(directory, "connection-A", photo)
        val sameConnectionRetry = partialFile(directory, "connection-A", photo)
        val afterReconnect = partialFile(directory, "connection-B", photo)

        assertEquals(firstAttempt, sameConnectionRetry)
        assertNotEquals(firstAttempt, afterReconnect)
    }
}
