package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test

class Stage3TimerTest {
    private val at=Stamp(0,0,1)
    private fun completed(cycle: Int=0): Snapshot {
        val running=TimerEngine.start(Snapshot(cycle=cycle),17*60_000L,at,"focus").snapshot
        return TimerEngine.reconcile(running,Stamp(17*60_000L,17*60_000L,1)).snapshot
    }
    @Test fun offer_uses_actual_finished_duration_and_configured_short_break() {
        val state=completed()
        val offer=TimerPresentation.breakOffer(state,3*60_000L,22*60_000L)!!
        assertEquals(Phase.SHORT_BREAK,offer.phase);assertEquals(3*60_000L,offer.duration)
        assertEquals(17*60_000L,offer.completedFocusDuration)
        assertEquals(state,TimerEngine.tap(state,3*60_000L,at).snapshot)
    }
    @Test fun fourth_focus_offers_configured_long_break_without_starting_it() {
        val state=completed(3)
        val offer=TimerPresentation.breakOffer(state,180_000,1_320_000)!!
        assertEquals(Phase.LONG_BREAK,offer.phase);assertEquals(1_320_000L,offer.duration)
        repeat(10){assertEquals(state,TimerEngine.reconcile(state,Stamp(9_000_000,9_000_000,1)).snapshot)}
        assertEquals(Status.FINISHED,state.session!!.status);assertEquals(4,state.cycle)
    }
    @Test fun interrupted_reset_idle_and_completed_rest_do_not_offer_a_successful_focus() {
        val running=TimerEngine.start(Snapshot(),60_000,at).snapshot
        val stopped=TimerEngine.stop(running,Stamp(1000,1000,1)).snapshot
        val reset=TimerEngine.reset(running).snapshot
        val rest=TimerEngine.start(Snapshot(phase=Phase.SHORT_BREAK),60_000,at).snapshot
        val doneRest=TimerEngine.reconcile(rest,Stamp(60_000,60_000,1)).snapshot
        for(s in listOf(Snapshot(),running,stopped,reset,doneRest)) {
            assertNull(TimerPresentation.breakOffer(s,180_000,900_000))
            assertEquals(s,TimerPresentation.startOfferedBreak(s,180_000,900_000,at).snapshot)
        }
    }
    @Test fun start_is_atomic_and_repeated_click_keeps_the_same_rest_session() {
        val state=completed(3);val now=Stamp(2_000_000,2_000_000,1)
        val started=TimerPresentation.startOfferedBreak(state,180_000,1_320_000,now)
        val session=started.snapshot.session!!
        assertEquals(Phase.LONG_BREAK,session.phase);assertEquals(Status.RUNNING,session.status)
        assertEquals(1_320_000L,session.remaining);assertEquals(4,started.snapshot.cycle)
        assertNull(started.record)
        assertEquals(started.snapshot,TimerPresentation.startOfferedBreak(started.snapshot,180_000,1_320_000,now).snapshot)
        val pause=TimerEngine.pause(started.snapshot,now).snapshot
        assertEquals(pause,TimerPresentation.startOfferedBreak(pause,180_000,1_320_000,now).snapshot)
    }
    private class Store(var state: Snapshot): TimerStore {
        val records=linkedMapOf<String,Record>()
        override fun read()=state
        override fun commit(change: Change): Boolean {
            state=change.snapshot
            val record=change.record ?: return false
            if(record.session.id in records)return false
            records[record.session.id]=record;return true
        }
        override fun history()=records.values.toList()
    }
    @Test fun foreground_alarm_and_restored_controller_claim_completion_once() {
        val running=TimerEngine.start(Snapshot(),60_000,at,"one").snapshot
        val store=Store(running);var signals=0
        val clock=Clock{Stamp(60_000,60_000,1)}
        val ui=TimerController(store,clock){signals++}
        ui.reconcile()
        val finished=store.state
        val receiver=TimerController(store,clock){signals++}
        repeat(5){receiver.dispatch{s,n->TimerEngine.alarm(s,"one",1,n)};ui.reconcile()}
        assertEquals(1,signals);assertEquals(1,store.history().size);assertEquals(finished,store.state)
        assertNotNull(TimerPresentation.breakOffer(store.state,180_000,900_000))
        val recreated=TimerController(store,Clock{Stamp(600_000,600_000,1)}){signals++}
        recreated.reconcile();assertEquals(finished,store.state);assertEquals(1,signals)
    }
    @Test fun skip_only_prepares_next_focus_without_starting_or_modifying_old_record() {
        val state=completed();val old=state.session
        val skipped=TimerEngine.prepare(state,Phase.FOCUS)
        assertNull(skipped.record);assertNull(skipped.snapshot.session)
        assertEquals(Phase.FOCUS,skipped.snapshot.phase);assertEquals(1,skipped.snapshot.cycle)
        assertTrue(old!!.completed);assertEquals(Status.FINISHED,old.status)
    }
}
