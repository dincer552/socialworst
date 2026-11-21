package com.dincer552.hattrickssh

/** Pure version-state logic shared by the store UI and background updater. */
enum class StorePackageState {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    UP_TO_DATE
}

fun resolveStorePackageState(installedVersionCode: Long?, latestVersionCode: Long?): StorePackageState {
    if (installedVersionCode == null) return StorePackageState.NOT_INSTALLED
    if (latestVersionCode != null && installedVersionCode < latestVersionCode) {
        return StorePackageState.UPDATE_AVAILABLE
    }
    return StorePackageState.UP_TO_DATE
}
