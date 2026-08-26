package com.devomoikane.phoneautomations

import com.devomoikane.phoneautomations.automations.StayAwakeAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StayAwakeAutomationTest {

    @Test
    fun `enabled with pc connected keeps screen awake on ac and usb`() {
        assertEquals(
            StayAwakeAutomation.PLUGGED_MASK,
            StayAwakeAutomation.targetValue(featureEnabled = true, pcConnected = true)
        )
    }

    @Test
    fun `enabled without pc disables stay awake`() {
        assertEquals(0, StayAwakeAutomation.targetValue(featureEnabled = true, pcConnected = false))
    }

    @Test
    fun `disabled leaves the system value untouched`() {
        assertNull(StayAwakeAutomation.targetValue(featureEnabled = false, pcConnected = true))
        assertNull(StayAwakeAutomation.targetValue(featureEnabled = false, pcConnected = false))
    }
}
