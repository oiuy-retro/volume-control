package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescingWriterTest {
    private val time = FakeTime()
    private val written = mutableListOf<Int>()
    private val writer = CoalescingWriter({ time.now }, 30L, time) { written += it }

    @Test
    fun firstWriteIsImmediate() {
        writer.submit(5)
        assertEquals(listOf(5), written)
    }

    @Test
    fun burstCoalescesToLatestWithOneTimer() {
        writer.submit(1)
        time.advance(5); writer.submit(2)
        time.advance(5); writer.submit(3)
        time.advance(5); writer.submit(4)
        assertEquals(listOf(1), written)
        assertEquals(1, time.scheduleCalls)
        time.advance(15)
        assertEquals(listOf(1, 4), written)
    }

    @Test
    fun sameTargetIsNotRewritten() {
        writer.submit(7)
        time.advance(100)
        writer.submit(7)
        assertEquals(listOf(7), written)
    }

    @Test
    fun pendingBackToLastWrittenWritesNothing() {
        writer.submit(7)
        time.advance(5); writer.submit(8)
        time.advance(5); writer.submit(7)
        time.advance(100)
        assertEquals(listOf(7), written)
    }

    @Test
    fun flushWritesPendingNow() {
        writer.submit(1)
        time.advance(5); writer.submit(9)
        writer.flush()
        assertEquals(listOf(1, 9), written)
        time.advance(100)
        assertEquals(listOf(1, 9), written)
    }

    @Test
    fun resetDropsPendingWithoutWriting() {
        writer.submit(1)
        time.advance(5); writer.submit(9)
        writer.reset(3)
        time.advance(100)
        writer.flush()
        assertEquals(listOf(1), written)
        writer.submit(3)
        assertEquals(listOf(1), written)
    }

    @Test
    fun spacedSubmitsWriteImmediately() {
        writer.submit(1)
        time.advance(30); writer.submit(2)
        time.advance(31); writer.submit(3)
        assertEquals(listOf(1, 2, 3), written)
        assertEquals(0, time.scheduleCalls)
    }
}
