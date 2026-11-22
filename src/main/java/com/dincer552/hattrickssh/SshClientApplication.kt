package com.dincer552.hattrickssh

import android.app.Application

class SshClientApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Drop stale PackageInstaller session records after process death / reboot.
        reconcileStoreInstallSessions(this)
    }
}
