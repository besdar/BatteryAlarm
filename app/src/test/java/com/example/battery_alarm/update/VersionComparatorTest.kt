package com.example.battery_alarm.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VersionComparator.isNewerVersion].
 *
 * These tests verify the version comparison logic that determines whether
 * a GitHub release is newer than the currently installed app version.
 *
 * ## Why These Tests Matter
 * Incorrect version comparison could cause:
 * - **False positives**: Showing an update icon when no update exists (annoying).
 * - **False negatives**: Not showing an update icon when one is available (dangerous).
 *
 * ## Test Categories
 * 1. Basic comparisons (newer, same, older)
 * 2. Different segment counts (e.g., "1.0" vs "1.0.1")
 * 3. Edge cases (empty strings, non-numeric segments, large numbers)
 */
class VersionComparatorTest {

    // ─────────────────────────────────────────────────────────
    //  Basic comparisons: newer, same, older
    // ─────────────────────────────────────────────────────────

    /** When the latest version has a higher minor number, update is available. */
    @Test
    fun `newer minor version returns true`() {
        assertTrue(VersionComparator.isNewerVersion("1.0", "1.1"))
    }

    /** When the latest version has a higher major number, update is available. */
    @Test
    fun `newer major version returns true`() {
        assertTrue(VersionComparator.isNewerVersion("1.0", "2.0"))
    }

    /** When both versions are identical, no update is available. */
    @Test
    fun `same version returns false`() {
        assertFalse(VersionComparator.isNewerVersion("1.0", "1.0"))
    }

    /** When the current version is newer than the latest, no update is available. */
    @Test
    fun `older latest version returns false`() {
        assertFalse(VersionComparator.isNewerVersion("2.0", "1.9"))
    }

    // ─────────────────────────────────────────────────────────
    //  Different segment counts
    // ─────────────────────────────────────────────────────────

    /** "1.0.1" is newer than "1.0" (missing segment treated as 0). */
    @Test
    fun `patch version newer than no-patch version returns true`() {
        assertTrue(VersionComparator.isNewerVersion("1.0", "1.0.1"))
    }

    /** "1.0" is the same as "1.0.0" (missing segment treated as 0). */
    @Test
    fun `version with trailing zero equals shorter version`() {
        assertFalse(VersionComparator.isNewerVersion("1.0", "1.0.0"))
    }

    /** "1.0.0" is the same as "1.0" from the other direction. */
    @Test
    fun `longer current version with zero patch equals shorter latest`() {
        assertFalse(VersionComparator.isNewerVersion("1.0.0", "1.0"))
    }

    // ─────────────────────────────────────────────────────────
    //  Three-segment versions
    // ─────────────────────────────────────────────────────────

    /** Full semantic version comparison: "1.2.4" > "1.2.3". */
    @Test
    fun `newer patch version returns true`() {
        assertTrue(VersionComparator.isNewerVersion("1.2.3", "1.2.4"))
    }

    /** The current version's major segment is higher: "2.0.0" > "1.9.9". */
    @Test
    fun `higher major beats higher minor and patch`() {
        assertFalse(VersionComparator.isNewerVersion("2.0.0", "1.9.9"))
    }

    // ─────────────────────────────────────────────────────────
    //  Edge cases
    // ─────────────────────────────────────────────────────────

    /** Single-segment versions: "2" > "1". */
    @Test
    fun `single segment newer returns true`() {
        assertTrue(VersionComparator.isNewerVersion("1", "2"))
    }

    /** Single-segment versions: same value. */
    @Test
    fun `single segment same returns false`() {
        assertFalse(VersionComparator.isNewerVersion("1", "1"))
    }

    /** Non-numeric segments should be treated safely (default to 0). */
    @Test
    fun `non-numeric segment defaults to zero`() {
        // "1.beta" → segments [1, 0], "1.1" → segments [1, 1]
        // So "1.1" > "1.0" → true
        assertTrue(VersionComparator.isNewerVersion("1.beta", "1.1"))
    }

    /** Both versions with non-numeric segments: treated as equal (both 0). */
    @Test
    fun `both non-numeric segments returns false`() {
        // "alpha" → [0], "beta" → [0] → equal → false
        assertFalse(VersionComparator.isNewerVersion("alpha", "beta"))
    }

    /** Large version numbers should work correctly. */
    @Test
    fun `large version numbers compare correctly`() {
        assertTrue(VersionComparator.isNewerVersion("1.0.999", "1.1.0"))
    }

    /** Empty string edge case: both empty → same → false. */
    @Test
    fun `empty strings returns false`() {
        assertFalse(VersionComparator.isNewerVersion("", ""))
    }

    /** Empty current vs valid latest: latest is "newer" (0 vs 1). */
    @Test
    fun `empty current vs valid latest returns true`() {
        assertTrue(VersionComparator.isNewerVersion("", "1.0"))
    }
}
