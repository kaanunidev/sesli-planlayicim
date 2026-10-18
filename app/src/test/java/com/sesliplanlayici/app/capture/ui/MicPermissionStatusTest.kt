package com.sesliplanlayici.app.capture.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MicPermissionStatusTest {

    @Test
    fun grantedAfterRequest() {
        assertEquals(MicPermissionStatus.Granted, MicPermissionStatus.afterRequest(granted = true, canAskAgain = false))
    }

    @Test
    fun deniedOnceCanBeAskedAgain() {
        assertEquals(MicPermissionStatus.NotGranted, MicPermissionStatus.afterRequest(granted = false, canAskAgain = true))
    }

    @Test
    fun deniedForGoodIsBlocked() {
        assertEquals(MicPermissionStatus.Blocked, MicPermissionStatus.afterRequest(granted = false, canAskAgain = false))
    }

    @Test
    fun grantingInSettingsUnblocksOnResume() {
        assertEquals(MicPermissionStatus.Granted, MicPermissionStatus.onResume(MicPermissionStatus.Blocked, hasPermission = true))
    }

    @Test
    fun revokingInSettingsResetsOnResume() {
        assertEquals(MicPermissionStatus.NotGranted, MicPermissionStatus.onResume(MicPermissionStatus.Granted, hasPermission = false))
    }

    @Test
    fun blockedStaysBlockedOnResumeWithoutPermission() {
        assertEquals(MicPermissionStatus.Blocked, MicPermissionStatus.onResume(MicPermissionStatus.Blocked, hasPermission = false))
    }
}
