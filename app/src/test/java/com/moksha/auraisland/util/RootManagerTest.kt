/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootManagerTest {

    @Test
    fun testIsRootAvailableRunsWithoutCrash() {
        val available = RootManager.isRootAvailable()
        assertNotNull(available)
    }

    @Test
    fun testDetectRootFlavorReturnsValidFlavor() {
        val flavor = RootManager.detectRootFlavor()
        assertNotNull(flavor)
        assertNotNull(flavor.displayName)
    }

    @Test
    fun testSusfsDetectionWithoutCrash() {
        val detected = RootManager.isSusfsDetected()
        assertNotNull(detected)
    }

    @Test
    fun testKernelSuDetectionWithoutCrash() {
        val detected = RootManager.isKernelSuDetected()
        assertNotNull(detected)
    }

    @Test
    fun testAPatchDetectionWithoutCrash() {
        val detected = RootManager.isAPatchDetected()
        assertNotNull(detected)
    }

    @Test
    fun testMagiskDetectionWithoutCrash() {
        val detected = RootManager.isMagiskDetected()
        assertNotNull(detected)
    }

    @Test
    fun testGetDiagnosticStatusReturnsNonEmptyString() {
        val status = RootManager.getDiagnosticStatus()
        assertTrue(status.isNotBlank())
    }

    @Test
    fun testIsRootGrantedReturnsBooleanWithoutCrash() = runBlocking {
        val granted = RootManager.isRootGranted()
        assertNotNull(granted)
    }

    @Test
    fun testRunRootCommandsFailsGracefullyWhenSuUnavailable() = runBlocking {
        if (!RootManager.isRootAvailable()) {
            val result = RootManager.runRootCommands(listOf("id"))
            assertTrue(result.isFailure)
        }
    }
}
