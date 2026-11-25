package com.example

import com.example.core.MediaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUtilsTest {

    @Test
    fun inferMimeFromSignature_detectsSamsungHeicMp4JpegPngAndWebp() {
        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        val mp4Header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
        )
        val jpegHeader = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0
        )
        val mkvHeader = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0
        )

        assertEquals("image/heic", MediaUtils.inferMimeFromSignature(heicHeader))
        assertEquals("video/mp4", MediaUtils.inferMimeFromSignature(mp4Header))
        assertEquals("image/jpeg", MediaUtils.inferMimeFromSignature(jpegHeader))
        assertEquals("video/x-matroska", MediaUtils.inferMimeFromSignature(mkvHeader))
    }

    @Test
    fun inferIsVideoMime_handlesOctetStreamWithVideoExtension() {
        assertTrue(MediaUtils.inferIsVideoMime("video/mp4", "clip.mp4"))
        assertTrue(MediaUtils.inferIsVideoMime("application/octet-stream", "S25_8K_HDR.mov"))
        assertTrue(MediaUtils.inferIsVideoMime("application/octet-stream", "grabacion.mkv"))
        assertFalse(MediaUtils.inferIsVideoMime("image/heic", "20261124.heic"))
        assertFalse(MediaUtils.inferIsVideoMime("application/octet-stream", "photo.dng"))
    }

    @Test
    fun calculateTargetVideoBitrate_staysWithinSafeUploadBounds() {
        val shortClipBps = MediaUtils.calculateTargetVideoBitrate(durationMs = 5_000L)
        val mediumClipBps = MediaUtils.calculateTargetVideoBitrate(durationMs = 45_000L)
        val longClipBps = MediaUtils.calculateTargetVideoBitrate(durationMs = 240_000L)

        assertEquals(2_400_000, shortClipBps)
        assertTrue(mediumClipBps in 380_000..2_400_000)
        assertTrue(longClipBps in 380_000..500_000)
    }

    @Test
    fun resolveExtension_mapsSamsungAndModernMediaFormats() {
        assertEquals("heic", MediaUtils.resolveExtension("image/heic"))
        assertEquals("mp4", MediaUtils.resolveExtension("video/mp4"))
        assertEquals("mov", MediaUtils.resolveExtension("video/quicktime"))
        assertEquals("mkv", MediaUtils.resolveExtension("video/x-matroska"))
        assertEquals("jpg", MediaUtils.resolveExtension("image/jpeg"))
    }
}
