package com.oiuy.volumecontrol.buttons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFilterControllerTest {
    private val applied = mutableListOf<Boolean>()
    private var applyWorks = true
    private val changes = mutableListOf<Boolean>()
    private val c = KeyFilterController { on ->
        applied += on
        applyWorks
    }.apply { onFilteringChanged = { changes += it } }

    @Test fun policyNeedsAllThree() {
        assertTrue(KeyFilterPolicy.shouldFilter(screenOn = true, switchOn = true, fineAvailable = true))
        assertFalse(KeyFilterPolicy.shouldFilter(screenOn = false, switchOn = true, fineAvailable = true))
        assertFalse(KeyFilterPolicy.shouldFilter(screenOn = true, switchOn = false, fineAvailable = true))
        assertFalse(KeyFilterPolicy.shouldFilter(screenOn = true, switchOn = true, fineAvailable = false))
        assertFalse(KeyFilterPolicy.shouldFilter(screenOn = false, switchOn = false, fineAvailable = false))
    }

    @Test fun startsOffAndAppliesOffFirstWhenConditionsFail() {
        assertFalse(c.filtering)
        c.update(screenOn = false, switchOn = true, fineAvailable = true)
        assertEquals(listOf(false), applied)
        assertFalse(c.filtering)
        assertTrue(changes.isEmpty())
    }

    @Test fun turnsOnOnlyWhenAllConditionsHold() {
        c.update(screenOn = true, switchOn = true, fineAvailable = true)
        assertEquals(listOf(true), applied)
        assertTrue(c.filtering)
        assertEquals(listOf(true), changes)
    }

    @Test fun screenOffThenOnTogglesFilter() {
        c.update(true, true, true)
        c.update(false, true, true)
        assertFalse(c.filtering)
        c.update(true, true, true)
        assertTrue(c.filtering)
        assertEquals(listOf(true, false, true), applied)
        assertEquals(listOf(true, false, true), changes)
    }

    @Test fun switchOffDropsFilterImmediately() {
        c.update(true, true, true)
        c.update(true, false, true)
        assertFalse(c.filtering)
        assertEquals(listOf(true, false), applied)
    }

    @Test fun missingFineVolumeNeverFilters() {
        c.update(true, true, false)
        c.update(true, true, false)
        assertFalse(c.filtering)
        assertEquals(listOf(false), applied)
    }

    @Test fun repeatedSameStateDoesNotReapply() {
        c.update(true, true, true)
        assertFalse(c.update(true, true, true))
        c.update(false, true, true)
        assertFalse(c.update(false, false, false))
        assertEquals(listOf(true, false), applied)
    }

    @Test fun failedTurnOnLeavesFilteringOffAndRetries() {
        applyWorks = false
        c.update(true, true, true)
        assertFalse(c.filtering)
        applyWorks = true
        assertTrue(c.update(true, true, true))
        assertTrue(c.filtering)
        assertEquals(listOf(true, true), applied)
    }

    @Test fun failedTurnOffStillReportsNotFilteringAndRetries() {
        c.update(true, true, true)
        applyWorks = false
        c.update(false, true, true)
        // The safety net treats this as off, so keys are handed straight back.
        assertFalse(c.filtering)
        applyWorks = true
        assertTrue(c.update(false, true, true))
        assertEquals(listOf(true, false, false), applied)
    }

    @Test fun applyThrowingCountsAsFailure() {
        val throwing = KeyFilterController { error("binder died") }
        assertTrue(throwing.update(true, true, true))
        assertFalse(throwing.filtering)
    }

    @Test fun forceOffOnUnbind() {
        c.update(true, true, true)
        c.forceOff()
        assertFalse(c.filtering)
        assertEquals(listOf(true, false), applied)
        assertFalse(c.forceOff())
    }
}
