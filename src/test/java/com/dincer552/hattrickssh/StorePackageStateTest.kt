package com.dincer552.hattrickssh

import org.junit.Assert.assertEquals
import org.junit.Test

class StorePackageStateTest {
    @Test fun missingPackageIsNotInstalled() {
        assertEquals(StorePackageState.NOT_INSTALLED, resolveStorePackageState(null, 12L))
    }

    @Test fun lowerInstalledVersionNeedsUpdate() {
        assertEquals(StorePackageState.UPDATE_AVAILABLE, resolveStorePackageState(11L, 12L))
    }

    @Test fun sameVersionIsCurrent() {
        assertEquals(StorePackageState.UP_TO_DATE, resolveStorePackageState(12L, 12L))
    }

    @Test fun higherInstalledVersionIsNotDowngraded() {
        assertEquals(StorePackageState.UP_TO_DATE, resolveStorePackageState(13L, 12L))
    }

    @Test fun installedPackageWithoutReleaseIsStillReportedAsInstalled() {
        assertEquals(StorePackageState.INSTALLED, resolveStorePackageState(12L, null))
    }

    @Test fun missingPackageWithoutReleaseIsNotInstalled() {
        assertEquals(StorePackageState.NOT_INSTALLED, resolveStorePackageState(null, null))
    }
}
