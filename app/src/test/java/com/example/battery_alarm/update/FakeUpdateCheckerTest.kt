package com.example.battery_alarm.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [FakeUpdateChecker].
 *
 * These tests verify that the fake behaves correctly so that other tests
 * relying on it can trust its behavior.
 */
class FakeUpdateCheckerTest {

    /** When a version string is provided, getLatestVersion returns it. */
    @Test
    fun `returns configured version`() = runTest {
        val checker = FakeUpdateChecker("2.0")
        assertEquals("2.0", checker.getLatestVersion())
    }

    /** When null is provided, getLatestVersion returns null (simulates network failure). */
    @Test
    fun `returns null when configured with null`() = runTest {
        val checker = FakeUpdateChecker(null)
        assertNull(checker.getLatestVersion())
    }
}
