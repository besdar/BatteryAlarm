package com.example.battery_alarm.update

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckWorkerTest {

    @Test
    fun `periodic update check requires a connected network`() {
        val request = UpdateCheckWorker.createPeriodicWorkRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}
