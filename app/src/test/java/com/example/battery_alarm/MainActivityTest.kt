package com.example.battery_alarm

import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
    }

    @Test
    fun `launch does not request notification permission`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNull(shadowOf(activity).lastRequestedPermission)
    }
}
