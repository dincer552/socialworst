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

    @Test fun android14RequiresInstalledTargetSdk31ForNoUserActionUpdate() {
        assertEquals(31, minimumTargetSdkForNoUserActionUpdate(34))
        assertEquals(false, supportsNoUserActionUpdateTarget(34, 30))
        assertEquals(true, supportsNoUserActionUpdateTarget(34, 31))
    }

    @Test fun android15RequiresInstalledTargetSdk33ForNoUserActionUpdate() {
        assertEquals(33, minimumTargetSdkForNoUserActionUpdate(35))
        assertEquals(false, supportsNoUserActionUpdateTarget(35, 32))
        assertEquals(true, supportsNoUserActionUpdateTarget(35, 33))
    }

    @Test fun android13RequiresInstalledTargetSdk30ForNoUserActionUpdate() {
        assertEquals(30, minimumTargetSdkForNoUserActionUpdate(33))
        assertEquals(false, supportsNoUserActionUpdateTarget(33, 29))
        assertEquals(true, supportsNoUserActionUpdateTarget(33, 30))
    }

    @Test fun android11DoesNotSupportNoUserActionUpdateApi() {
        assertEquals(null, minimumTargetSdkForNoUserActionUpdate(30))
        assertEquals(false, supportsNoUserActionUpdateTarget(30, 35))
    }
}
