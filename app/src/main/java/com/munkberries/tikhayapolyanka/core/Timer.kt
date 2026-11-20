package com.munkberries.tikhayapolyanka.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class Status { IDLE, RUNNING, PAUSED, FINISHED }
data class Stamp(val wall: Long, val elapsed: Long, val boot: Int)
fun interface Clock { fun now(): Stamp }
data class Interval(val start: Long, val end: Long)
data class Session(
    val id: String, val phase: Phase, val status: Status, val duration: Long,
    val remaining: Long, val anchor: Stamp?, val revision: Int = 1,
    val intervals: List<Interval> = emptyList(), val title: String = "",
    val completed: Boolean = false, val endedAt: Long? = null, val taskId: String? = null,
)
data class Snapshot(
    val session: Session? = null, val cycle: Int = 0, val draft: String = "",
    val phase: Phase = Phase.FOCUS, val recoveryNote: String? = null, val selectedTaskId: String? = null,
)
data class Record(val session: Session)
data class Change(val snapshot: Snapshot, val record: Record? = null)
data class DailyTotal(val millis: Long, val completed: Int)

/** Pure state transitions: no Android, sleeping, UI, or tick counters. */
object TimerEngine {
    fun remaining(session: Session, now: Stamp): Long {
        if (session.status != Status.RUNNING) return session.remaining
        val anchor = requireNotNull(session.anchor)
        val delta = if (anchor.boot == now.boot) now.elapsed - anchor.elapsed else now.wall - anchor.wall
        return (session.remaining - delta.coerceAtLeast(0)).coerceIn(0, session.remaining)
    }

    fun reconcile(s: Snapshot, now: Stamp): Change {
        val session = s.session ?: return Change(s)
        if (session.status != Status.RUNNING) return Change(s)
        val anchor = requireNotNull(session.anchor)
        if (anchor.boot != now.boot && now.wall < anchor.wall) {
            return Change(s.copy(session = session.copy(status = Status.PAUSED, anchor = null,
                revision = session.revision + 1),
                recoveryNote = "После перезагрузки часы изменились. Занятие на паузе; оставшееся время сохранено."))
        }
        if (remaining(session, now) == 0L) return finish(s, now, completed = true)
        if (anchor.boot != now.boot) {
            val closed = closeInterval(session, now)
            return Change(s.copy(session = closed.copy(anchor = now, revision = session.revision + 1),
                recoveryNote = "После перезагрузки остаток восстановлен по часам устройства."))
        }
        val wallDrift = (now.wall - anchor.wall) - (now.elapsed - anchor.elapsed)
        if (kotlin.math.abs(wallDrift) > 2_000) {
            val closed = closeInterval(session, now)
            return Change(s.copy(session = closed.copy(anchor = now, revision = session.revision + 1),
                recoveryNote = "Часы устройства изменились; длительность занятия сохранена."))
        }
        return Change(s)
    }

    fun start(s: Snapshot, duration: Long, now: Stamp, id: String = UUID.randomUUID().toString()): Change {
        if (s.session != null || duration <= 0) return Change(s)
        return Change(s.copy(session = Session(id, s.phase, Status.RUNNING, duration, duration, now,
            title = s.draft.trim(),taskId=s.selectedTaskId), recoveryNote = null,
            cycle=if(s.phase==Phase.FOCUS && s.cycle==4)0 else s.cycle))
    }

    fun startTask(s: Snapshot, task: PlannedTask, duration: Long, now: Stamp): Change {
        if (s.session?.status == Status.RUNNING || s.session?.status == Status.PAUSED || task.deleted) return Change(s)
        val prepared = prepare(s, Phase.FOCUS).snapshot.copy(draft=task.title)
        val started = start(prepared,duration,now)
        return started.copy(snapshot=started.snapshot.copy(session=started.snapshot.session?.copy(taskId=task.id)))
    }

    fun reset(s: Snapshot): Change = Change(s.copy(
        session=null,phase=s.session?.phase ?: s.phase,recoveryNote=null,
        selectedTaskId=s.session?.taskId ?: s.selectedTaskId,
    ))
    fun tap(s: Snapshot,duration: Long,now: Stamp): Change = when(s.session?.status) {
        null,Status.IDLE->start(s,duration,now)
        Status.RUNNING->pause(s,now)
        Status.PAUSED->resume(s,now)
        Status.FINISHED->Change(s)
    }

    fun pause(s: Snapshot, now: Stamp): Change {
        val checked = reconcile(s, now)
        if (checked.record != null) return checked
        val session = checked.snapshot.session ?: return checked
        if (session.status != Status.RUNNING) return checked
        return Change(checked.snapshot.copy(session = closeInterval(session, now).copy(
            status = Status.PAUSED, anchor = null, revision = session.revision + 1)))
    }

    fun resume(s: Snapshot, now: Stamp): Change {
        val session = s.session ?: return Change(s)
        if (session.status != Status.PAUSED) return Change(s)
        return Change(s.copy(session = session.copy(status = Status.RUNNING, anchor = now,
            revision = session.revision + 1), recoveryNote = null))
    }

    fun stop(s: Snapshot, now: Stamp): Change {
        val checked = reconcile(s, now)
        return if (checked.record != null) checked else finish(checked.snapshot, now, completed = false)
    }

    fun alarm(s: Snapshot, id: String, revision: Int, now: Stamp): Change {
        val session = s.session ?: return Change(s)
        if (session.id != id || session.revision != revision || session.status != Status.RUNNING) return Change(s)
        return reconcile(s, now)
    }

    fun select(s: Snapshot, phase: Phase): Change =
        if (s.session != null) Change(s) else Change(s.copy(phase = phase))

    /** Preparing the next phase always waits for a human to press Start. */
    fun prepare(s: Snapshot, phase: Phase): Change {
        if (s.session?.status == Status.RUNNING || s.session?.status == Status.PAUSED) return Change(s)
        return Change(s.copy(session = null, phase = phase,
            cycle = if (phase == Phase.FOCUS && s.cycle == 4) 0 else s.cycle, recoveryNote = null,selectedTaskId=null))
    }

    private fun closeInterval(session: Session, now: Stamp): Session {
        if (session.status != Status.RUNNING) return session
        val remaining = remaining(session, now)
        val used = session.remaining - remaining
        val anchor = requireNotNull(session.anchor)
        return session.copy(remaining = remaining, intervals = if (used > 0)
            session.intervals + Interval(anchor.wall, anchor.wall + used) else session.intervals)
    }

    private fun finish(s: Snapshot, now: Stamp, completed: Boolean): Change {
        val session = s.session ?: return Change(s)
        if (session.status == Status.FINISHED) return Change(s)
        val closed = closeInterval(session, now)
        val ended = if (completed && session.anchor != null) {
            val elapsed = if (session.anchor.boot == now.boot) now.elapsed - session.anchor.elapsed
                else now.wall - session.anchor.wall
            now.wall - (elapsed - session.remaining).coerceAtLeast(0)
        } else now.wall
        val finished = closed.copy(status = Status.FINISHED, anchor = null,
            remaining = if (completed) 0 else closed.remaining, completed = completed,
            endedAt = ended, revision = session.revision + 1)
        val nextCycle = if (completed && session.phase == Phase.FOCUS) (s.cycle + 1).coerceAtMost(4) else s.cycle
        return Change(s.copy(session = finished, cycle = nextCycle), Record(finished))
    }

    fun daily(records: List<Record>, date: LocalDate, zone: ZoneId, active: Session? = null, now: Stamp? = null): DailyTotal {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var millis = 0L
        var completed = 0
        val sessions = records.map { it.session }.toMutableList()
        if (active != null && active.status != Status.FINISHED) {
            sessions += if (now != null) closeInterval(active, now) else active
        }
        for (session in sessions.filter { it.phase == Phase.FOCUS }) {
            millis += session.intervals.sumOf { (minOf(it.end, to) - maxOf(it.start, from)).coerceAtLeast(0) }
            if (session.completed && session.endedAt != null &&
                Instant.ofEpochMilli(session.endedAt).atZone(zone).toLocalDate() == date) completed++
        }
        return DailyTotal(millis, completed)
    }
}

interface TimerStore {
    fun read(): Snapshot
    /** State, unique history insert, and signal claim share one DB transaction. */
    fun commit(change: Change): Boolean
    fun history(): List<Record>
}

/** Singleton per application process, shared by Activity and all receivers. */
class TimerController(private val store: TimerStore, private val clock: Clock,
    private val onCompletion: (Record) -> Unit = {}) {
    @Synchronized fun dispatch(action: (Snapshot, Stamp) -> Change): Snapshot {
        val previous = store.read()
        val change = action(previous, clock.now())
        if (change.snapshot == previous && change.record == null) return previous
        val inserted = store.commit(change)
        if (inserted && change.record?.session?.completed == true) onCompletion(change.record)
        return change.snapshot
    }
    fun reconcile(): Snapshot = dispatch(TimerEngine::reconcile)
    @Synchronized fun snapshot(): Snapshot = store.read()
    @Synchronized fun history(): List<Record> = store.history()
}



