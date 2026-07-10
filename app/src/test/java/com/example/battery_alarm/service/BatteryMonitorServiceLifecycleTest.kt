package com.example.battery_alarm.service

import android.content.Context
import android.content.Intent
import com.example.battery_alarm.data.SettingsRepository
import com.example.battery_alarm.data.SharedPreferencesStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryMonitorServiceLifecycleTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = SettingsRepository(SharedPreferencesStorage(context))
        repository.isServiceEnabled = false
    }

    @After
    fun tearDown() {
        BatteryMonitorService.stop(context)
    }

    @Test
    fun `stop notification action clears persisted monitoring intent`() {
        repository.isServiceEnabled = true
        Robolectric.buildService(
            BatteryMonitorService::class.java,
            Intent(context, BatteryMonitorService::class.java)
                .setAction(BatteryMonitorService.ACTION_STOP_SERVICE)
        ).create().startCommand(0, 1)

        assertFalse(SettingsRepository(SharedPreferencesStorage(context)).isServiceEnabled)
    }

    @Test
    fun `reload sends a reload action only while monitoring is active`() {
        val controller = Robolectric.buildService(BatteryMonitorService::class.java).create()
        shadowOf(context as android.app.Application).clearStartedServices()

        BatteryMonitorService.reload(context)

        val startedIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(startedIntent)
        assertEquals(BatteryMonitorService.ACTION_RELOAD_SETTINGS, startedIntent.action)
        controller.destroy()

        shadowOf(context as android.app.Application).clearStartedServices()
        BatteryMonitorService.reload(context)

        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }

    @Test
    fun `repeated starts do not register the battery receiver more than once`() {
        val applicationShadow = shadowOf(context as android.app.Application)
        applicationShadow.clearRegisteredReceivers()
        val controller = Robolectric.buildService(BatteryMonitorService::class.java)
            .create()
            .startCommand(0, 1)
        val initialReceiverCount = applicationShadow.registeredReceivers.size

        controller.get().onStartCommand(Intent(context, BatteryMonitorService::class.java), 0, 2)

        assertEquals(initialReceiverCount, applicationShadow.registeredReceivers.size)
        controller.destroy()
    }
}
