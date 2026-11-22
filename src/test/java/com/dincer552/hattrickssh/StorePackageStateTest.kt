package com.dincer552.hattrickssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun existingPackageOnAndroid12OrNewerUsesPackageInstallerUpdateFlow() {
        assertEquals(true, shouldAttemptPackageInstallerUpdate(31, true))
        assertEquals(true, shouldAttemptPackageInstallerUpdate(34, true))
    }

    @Test fun newInstallUsesRegularInstallerInsteadOfSilentUpdateFlow() {
        assertEquals(false, shouldAttemptPackageInstallerUpdate(34, false))
    }

    @Test fun android11UsesRegularInstallerForUpdates() {
        assertEquals(false, shouldAttemptPackageInstallerUpdate(30, true))
    }

    @Test fun postInstallVersionMatchesWhenEqual() {
        assertTrue(verifyInstalledVersionMatches(12L, 12L))
    }

    @Test fun postInstallVersionFailsWhenMismatchOrMissing() {
        assertFalse(verifyInstalledVersionMatches(12L, 11L))
        assertFalse(verifyInstalledVersionMatches(12L, null))
        assertFalse(verifyInstalledVersionMatches(null, 12L))
        assertFalse(verifyInstalledVersionMatches(null, null))
    }
}
