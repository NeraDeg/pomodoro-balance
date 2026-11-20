package com.munkberries.tikhayapolyanka.core

/** A projection of the persisted completed session; never a second countdown. */
data class BreakOffer(val phase: Phase,val duration: Long,val completedFocusDuration: Long)
object TimerPresentation {
    fun breakOffer(state: Snapshot,shortDuration: Long,longDuration: Long): BreakOffer? {
        val session=state.session ?: return null
        if(session.phase!=Phase.FOCUS || session.status!=Status.FINISHED || !session.completed)return null
        val long=state.cycle>=4
        return BreakOffer(if(long)Phase.LONG_BREAK else Phase.SHORT_BREAK,
            if(long)longDuration else shortDuration,session.duration)
    }
    fun startOfferedBreak(state: Snapshot,shortDuration: Long,longDuration: Long,now: Stamp): Change {
        val offer=breakOffer(state,shortDuration,longDuration) ?: return Change(state)
        return TimerEngine.start(TimerEngine.prepare(state,offer.phase).snapshot,offer.duration,now)
    }
}
