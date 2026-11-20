package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ClockChangeTest {
    @Test fun completion_date_follows_changed_wall_clock_without_extra_work() {
        val day=LocalDate.of(2026,10,25)
        val zone=ZoneId.of("Europe/Moscow")
        val wall=day.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
        val start=TimerEngine.start(Snapshot(),60_000,Stamp(wall,1_000,1)).snapshot
        val completed=TimerEngine.reconcile(start,Stamp(wall+86_460_000,61_000,1))
        assertEquals(60_000L,completed.record!!.session.intervals.sumOf { it.end-it.start })
        assertEquals(1,TimerEngine.daily(listOf(completed.record),day.plusDays(1),zone).completed)
    }
    @Test fun clock_change_checkpoint_rebases_without_resetting_remaining() {
        val start=TimerEngine.start(Snapshot(),60_000,Stamp(1_000_000,10_000,1)).snapshot
        val now=Stamp(5_000_000,30_000,1)
        val checked=TimerEngine.reconcile(start,now).snapshot
        assertEquals(40_000L,checked.session!!.remaining)
        assertEquals(now,checked.session.anchor)
        assertEquals(20_000L,checked.session.intervals.sumOf { it.end-it.start })
        assertEquals(2,checked.session.revision)
    }
}
