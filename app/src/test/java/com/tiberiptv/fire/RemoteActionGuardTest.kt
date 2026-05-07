package com.tiberiptv.fire

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteActionGuardTest {
    @After
    fun tearDown() {
        RemoteActionGuard.release(null)
    }

    @Test
    fun acquireBlocksSecondRemoteActionUntilReleased() {
        assertTrue(RemoteActionGuard.tryAcquire(RemoteLabels.PLAYBACK))
        assertEquals(RemoteLabels.PLAYBACK, RemoteActionGuard.activeLabel())

        assertFalse(RemoteActionGuard.tryAcquire(RemoteLabels.DOWNLOAD))

        RemoteActionGuard.release(RemoteLabels.PLAYBACK)
        assertTrue(RemoteActionGuard.tryAcquire(RemoteLabels.DOWNLOAD))
        assertEquals(RemoteLabels.DOWNLOAD, RemoteActionGuard.activeLabel())
    }

    @Test
    fun releaseWithWrongLabelKeepsActiveActionLocked() {
        assertTrue(RemoteActionGuard.tryAcquire(RemoteLabels.BUFFER))

        RemoteActionGuard.release(RemoteLabels.DOWNLOAD)

        assertTrue(RemoteActionGuard.isActive())
        assertEquals(RemoteLabels.BUFFER, RemoteActionGuard.activeLabel())
        assertFalse(RemoteActionGuard.tryAcquire(RemoteLabels.PLAYBACK))
    }

    @Test
    fun emptyAcquireUsesRemoteFallbackLabel() {
        assertTrue(RemoteActionGuard.tryAcquire(null))

        assertEquals("remote", RemoteActionGuard.activeLabel())
    }
}
