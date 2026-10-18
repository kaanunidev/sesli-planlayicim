package com.sesliplanlayici.app.capture.ui

/** Where the user stands with the microphone permission. */
enum class MicPermissionStatus {
    /** Never asked, or the system will show the dialog again. */
    NotGranted,
    Granted,

    /** Denied and the system will not show the dialog any more; only Settings can change it. */
    Blocked;

    companion object {
        /**
         * Status after the permission dialog closes. [canAskAgain] is
         * `shouldShowRequestPermissionRationale`, which is false once the user denied for good.
         */
        fun afterRequest(granted: Boolean, canAskAgain: Boolean): MicPermissionStatus = when {
            granted -> Granted
            canAskAgain -> NotGranted
            else -> Blocked
        }

        /** Status when the screen comes back to the foreground, e.g. after visiting Settings. */
        fun onResume(current: MicPermissionStatus, hasPermission: Boolean): MicPermissionStatus = when {
            hasPermission -> Granted
            // Revoked in Settings while we were away.
            current == Granted -> NotGranted
            else -> current
        }
    }
}
