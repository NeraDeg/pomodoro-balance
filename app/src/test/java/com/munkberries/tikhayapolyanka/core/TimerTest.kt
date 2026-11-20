package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TimerTest {
    private class FakeClock(var wall:Long=1_000_000, var elapsed:Long=10_000, var boot:Int=1):Clock {
        override fun now()=Stamp(wall,elapsed,boot)
        fun advance(ms:Long) { wall+=ms; elapsed+=ms }
    }
    private class MemoryStore:TimerStore {
        var value=Snapshot()
        val records=linkedMapOf<String,Record>()
        override fun read()=value
        override fun commit(change:Change):Boolean {
            val newRecord=change.record
            val inserted=newRecord!=null && !records.containsKey(newRecord.session.id)
            if(inserted) records[newRecord!!.session.id]=newRecord
            value=change.snapshot
            return inserted
        }
        override fun history()=records.values.toList()
    }
    private class Fixture {
        val clock=FakeClock()
        val store=MemoryStore()
        val signals=AtomicInteger()
        val controller=TimerController(store,clock) { signals.incrementAndGet() }
        fun start(ms:Long=60_000)=controller.dispatch { s,n -> TimerEngine.start(s,ms,n) }
        fun prepare(phase:Phase=Phase.FOCUS)=controller.dispatch { s,_ -> TimerEngine.prepare(s,phase) }
        fun finish() { clock.advance(60_000); controller.reconcile() }
    }
    @Test fun pause_excludes_time_and_resume_keeps_duration_snapshot() {
        val f=Fixture()
        f.start(); f.clock.advance(20_000)
        f.controller.dispatch(TimerEngine::pause)
        assertEquals(40_000L,f.store.value.session!!.remaining)
        f.clock.advance(300_000)
        assertEquals(40_000L,TimerEngine.remaining(f.store.value.session!!,f.clock.now()))
        f.controller.dispatch(TimerEngine::resume)
        f.clock.advance(40_000); f.controller.reconcile()
        assertEquals(60_000L,f.store.history().single().session.intervals.sumOf { it.end-it.start })
        assertEquals(1,f.store.value.cycle)
    }
    @Test fun concurrent_ui_and_receiver_complete_and_signal_exactly_once() {
        val f=Fixture()
        val s=f.start().session!!
        f.clock.advance(70_000)
        val gate=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try {
            val futures=listOf(
                pool.submit { gate.await(); f.controller.reconcile() },
                pool.submit { gate.await(); f.controller.dispatch { a,n -> TimerEngine.alarm(a,s.id,s.revision,n) } },
            )
            gate.countDown()
            futures.forEach { it.get() }
            assertEquals(1,f.store.history().size)
            assertEquals(1,f.signals.get())
            assertEquals(1,f.store.value.cycle)
        } finally { pool.shutdownNow() }
    }
    @Test fun restore_after_deadline_finishes_once_and_does_not_start_another_phase() {
        val f=Fixture(); f.start(); f.clock.advance(6*60_000)
        val restored=TimerController(f.store,f.clock) { f.signals.incrementAndGet() }
        restored.reconcile(); restored.reconcile()
        assertEquals(Status.FINISHED,f.store.value.session!!.status)
        assertEquals(Phase.FOCUS,f.store.value.session!!.phase)
        assertEquals(1,f.store.history().size)
        assertEquals(1,f.signals.get())
        assertEquals(60_000L,f.store.history().single().session.intervals.sumOf { it.end-it.start })
    }
    @Test fun four_focus_sessions_offer_long_rest_and_new_cycle() {
        val f=Fixture()
        repeat(4) { i ->
            f.start(); f.finish()
            assertEquals(i+1,f.store.value.cycle)
            if(i<3) {
                f.prepare(Phase.SHORT_BREAK); f.start(); f.finish()
                assertEquals(i+1,f.store.value.cycle)
                f.prepare()
            }
        }
        f.prepare(Phase.LONG_BREAK); f.start(); f.finish()
        assertEquals(4,f.store.value.cycle)
        f.prepare()
        assertEquals(0,f.store.value.cycle)
        assertNull(f.store.value.session)
    }
    @Test fun manual_rest_and_early_stop_never_increase_progress() {
        val f=Fixture()
        f.start(); f.clock.advance(5_000); f.controller.dispatch(TimerEngine::stop)
        assertEquals(0,f.store.value.cycle)
        assertFalse(f.store.history().single().session.completed)
        assertEquals(0,f.signals.get())
        f.prepare(Phase.LONG_BREAK); f.start(); f.finish()
        assertEquals(0,f.store.value.cycle)
    }
    @Test fun stale_alarm_cannot_touch_paused_resumed_or_new_session() {
        val f=Fixture()
        val old=f.start().session!!
        f.clock.advance(5_000); f.controller.dispatch(TimerEngine::pause)
        f.controller.dispatch { s,n -> TimerEngine.alarm(s,old.id,old.revision,n) }
        assertEquals(Status.PAUSED,f.store.value.session!!.status)
        f.controller.dispatch(TimerEngine::resume)
        val resumed=f.store.value
        f.clock.advance(100_000)
        f.controller.dispatch { s,n -> TimerEngine.alarm(s,old.id,old.revision,n) }
        assertEquals(resumed,f.store.value)
        f.controller.reconcile(); f.prepare(); f.start()
        val fresh=f.store.value
        f.controller.dispatch { s,n -> TimerEngine.alarm(s,old.id,old.revision,n) }
        assertEquals(fresh,f.store.value)
    }
    @Test fun intervals_across_midnight_and_pause_split_real_work_between_dates() {
        val f=Fixture(); val zone=ZoneId.of("Europe/Moscow")
        val first=LocalDate.of(2026,10,25)
        f.clock.wall=first.atTime(23,59,30).atZone(zone).toInstant().toEpochMilli()
        f.start(120_000)
        f.clock.advance(40_000); f.controller.dispatch(TimerEngine::pause)
        f.clock.advance(60_000); f.controller.dispatch(TimerEngine::resume)
        f.clock.advance(80_000); f.controller.reconcile()
        val a=TimerEngine.daily(f.store.history(),first,zone)
        val b=TimerEngine.daily(f.store.history(),first.plusDays(1),zone)
        assertEquals(DailyTotal(30_000,0),a)
        assertEquals(DailyTotal(90_000,1),b)
    }
    @Test fun wall_clock_change_during_same_boot_does_not_change_remaining() {
        val f=Fixture(); f.start()
        f.clock.elapsed+=10_000; f.clock.wall-=7_200_000
        assertEquals(50_000L,TimerEngine.remaining(f.store.value.session!!,f.clock.now()))
        f.clock.elapsed+=50_000; f.controller.reconcile()
        assertEquals(1,f.store.value.cycle)
    }
    @Test fun reboot_reanchors_wall_time_and_never_compares_elapsed_from_different_boots() {
        val f=Fixture(); f.start()
        f.clock.wall+=20_000; f.clock.elapsed=1_000; f.clock.boot++
        f.controller.reconcile()
        assertEquals(40_000L,f.store.value.session!!.remaining)
        assertEquals(f.clock.now(),f.store.value.session!!.anchor)
        f.clock.advance(40_000); f.controller.reconcile()
        assertEquals(60_000L,f.store.history().single().session.intervals.sumOf { it.end-it.start })
    }
    @Test fun reboot_with_clock_rollback_pauses_instead_of_guessing() {
        val f=Fixture(); f.start()
        f.clock.wall-=60_000; f.clock.elapsed=1_000; f.clock.boot++
        f.controller.reconcile()
        assertEquals(Status.PAUSED,f.store.value.session!!.status)
        assertEquals(60_000L,f.store.value.session!!.remaining)
        assertNotNull(f.store.value.recoveryNote)
        assertTrue(f.store.history().isEmpty())
    }
    @Test fun daylight_saving_uses_local_day_boundaries() {
        val zone=ZoneId.of("Europe/Berlin")
        val date=LocalDate.of(2026,3,29)
        val start=date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val s=Session("dst",Phase.FOCUS,Status.FINISHED,end-start,0,null,
            intervals=listOf(Interval(start,end)),completed=true,endedAt=end-1)
        assertEquals(DailyTotal(23*3_600_000L,1),TimerEngine.daily(listOf(Record(s)),date,zone))
    }
    @Test fun double_start_and_recomposition_do_not_replace_a_running_session() {
        val f=Fixture(); val s=f.start()
        assertEquals(s,f.start())
        repeat(10) { f.controller.reconcile() }
        assertEquals(s,f.store.value)
        assertTrue(f.store.history().isEmpty())
    }
}
