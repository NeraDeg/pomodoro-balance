package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale
import kotlinx.coroutines.*

class OrganizerTest {
    @Test fun russian_dates_ignore_english_and_german_system_locales_for_all_months() {
        val previous=Locale.getDefault()
        try {
            for(locale in listOf(Locale.US,Locale.GERMANY,RussianDates.locale)) {
                Locale.setDefault(locale)
                val months=listOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
                months.forEachIndexed{i,name->assertEquals(name+" 2026",RussianDates.month(YearMonth.of(2026,i+1)))}
                assertEquals("25 октября, воскресенье",RussianDates.day(LocalDate.of(2026,10,25)))
                assertTrue(RussianDates.history(Instant.parse("2026-10-25T17:05:00Z").toEpochMilli(),ZoneId.of("UTC")).contains("17:05"))
                assertFalse(RussianDates.history(0,ZoneId.of("UTC")).contains("Jan"))
            }
        } finally{Locale.setDefault(previous)}
    }
    @Test fun grid_handles_leap_february_year_transition_and_monday_first() {
        val february=RussianDates.days(YearMonth.of(2024,2))
        assertEquals(29,february.count{it!=null})
        assertEquals(3,february.indexOfFirst{it!=null})
        assertEquals("Январь 2027",RussianDates.month(YearMonth.of(2026,12).plusMonths(1)))
        assertEquals(0,RussianDates.days(YearMonth.of(2026,6)).indexOfFirst{it!=null})
    }
    @Test fun search_is_cyrillic_case_insensitive_and_folder_combines_with_query() {
        val note=Note(id="a",title="БИОЛОГИЯ",folderId="study",blocks=listOf(NoteBlock(text="Клетка и Ёж")),draft=false)
        assertTrue(NoteRules.matches(note,"биология","study"))
        assertTrue(NoteRules.matches(note,"ёж",null))
        assertFalse(NoteRules.matches(note,"биология","ideas"))
        assertFalse(NoteRules.matches(note,"%' OR 1=1 --",null))
    }
    @Test fun pinned_and_equal_timestamps_sort_stably() {
        val a=Note(id="a",updatedAt=20);val b=Note(id="b",updatedAt=20);val pinned=Note(id="z",pinned=true,updatedAt=1)
        assertEquals(listOf("z","a","b"),NoteRules.sorted(listOf(b,pinned,a)).map{it.id})
    }
    @Test fun blank_new_templates_are_not_meaningful_but_text_or_checked_item_is() {
        assertFalse(NoteRules.meaningful(Note(blocks=listOf(NoteBlock(type=BlockType.HEADING)))))
        assertFalse(NoteRules.meaningful(Note(blocks=listOf(NoteBlock(type=BlockType.CHECK)))))
        assertTrue(NoteRules.meaningful(Note(blocks=listOf(NoteBlock(type=BlockType.CHECK,checked=true)))))
        assertTrue(NoteRules.meaningful(Note(title="Запись")))
    }
    @Test fun numbering_is_derived_from_contiguous_blocks_not_user_text() {
        val blocks=listOf(NoteBlock(type=BlockType.NUMBERED),NoteBlock(type=BlockType.NUMBERED),
            NoteBlock(),NoteBlock(type=BlockType.NUMBERED))
        assertEquals(1,NoteRules.numberAt(blocks,0));assertEquals(2,NoteRules.numberAt(blocks,1));assertEquals(1,NoteRules.numberAt(blocks,3))
        val withoutFirst=blocks.drop(1)
        assertEquals(1,NoteRules.numberAt(withoutFirst,0))
    }
    @Test fun bold_selection_can_be_toggled_without_markdown_and_tracks_insertions() {
        val original=NoteBlock(text="Моя биология")
        val bold=NoteRules.toggleBold(original,4,12)
        assertEquals(listOf(BoldRange(4,12)),bold.marks)
        val changed=NoteRules.replaceText(bold,"Моя супербиология")
        assertEquals(listOf(BoldRange(4,17)),changed.marks)
        assertEquals(emptyList<BoldRange>(),NoteRules.toggleBold(bold,4,12).marks)
        assertEquals(original.text,bold.text)
    }
    @Test fun split_keeps_first_id_and_formatting_with_independent_checkboxes() {
        val original=NoteBlock(id="first",type=BlockType.CHECK,text="Раздел",checked=true,bold=true)
        val split=NoteRules.split(original,"Раздел\nСледующий")
        assertEquals("first",split[0].id);assertNotEquals("first",split[1].id)
        assertTrue(split[0].checked);assertFalse(split[1].checked);assertTrue(split[1].bold)
        assertEquals(BlockType.CHECK,split[1].type)
    }
    @Test fun event_requires_time_deadline_can_omit_it_and_blank_titles_are_rejected() {
        val now=Instant.parse("2026-10-25T10:00:00Z");val zone=ZoneId.of("UTC")
        assertEquals(TaskProblem.TITLE_REQUIRED,PlannerRules.validate(PlannedTask(title="  "),now,zone))
        assertEquals(TaskProblem.EVENT_TIME_REQUIRED,PlannerRules.validate(PlannedTask(kind=TaskKind.EVENT,title="Звонок"),now,zone))
        assertNull(PlannerRules.validate(PlannedTask(kind=TaskKind.DEADLINE,title="Сдать"),now,zone))
        assertEquals(TaskProblem.REMINDER_IN_PAST,PlannerRules.validate(PlannedTask(title="Дело",
            reminder=LocalDateTime.of(2026,10,25,9,0)),now,zone))
    }
    @Test fun day_order_is_timed_untimed_then_completed_and_overdue_dates_stay_put() {
        val day=LocalDate.of(2026,10,25)
        val tasks=listOf(
            PlannedTask(id="untimed",title="А",date=day),
            PlannedTask(id="done",title="Б",date=day,time=LocalTime.of(8,0),completed=true),
            PlannedTask(id="late",title="В",date=day,time=LocalTime.of(17,0)),
            PlannedTask(id="early",title="Г",date=day,time=LocalTime.of(9,0)),
        )
        assertEquals(listOf("early","late","untimed","done"),PlannerRules.forDay(tasks,day).map{it.id})
        assertTrue(PlannerRules.forDay(tasks,day.plusDays(1)).isEmpty())
        assertTrue(tasks.all{it.date==day})
    }
    @Test fun task_focus_starts_atomically_with_link_and_snapshot_without_changing_the_task() {
        val task=PlannedTask(id="task",title="Прочитать")
        val start=TimerEngine.startTask(Snapshot(cycle=2),task,60_000,Stamp(0,0,1)).snapshot
        assertEquals(task.id,start.session!!.taskId);assertEquals("Прочитать",start.session.title)
        val renamed=task.copy(title="Другое",deleted=true)
        val finish=TimerEngine.reconcile(start,Stamp(60_000,60_000,1))
        assertEquals("Прочитать",finish.record!!.session.title);assertEquals(task.id,finish.record.session.taskId)
        assertFalse(task.completed);assertFalse(renamed.completed)
    }
    @Test fun task_focus_does_not_replace_running_or_paused_sessions() {
        val now=Stamp(0,0,1);val existing=TimerEngine.start(Snapshot(),60_000,now).snapshot
        assertEquals(existing,TimerEngine.startTask(existing,PlannedTask(title="Новое"),60_000,now).snapshot)
        val paused=TimerEngine.pause(existing,Stamp(5_000,5_000,1)).snapshot
        assertEquals(paused,TimerEngine.startTask(paused,PlannedTask(title="Новое"),60_000,now).snapshot)
    }
    @Test fun task_focus_after_fourth_result_starts_new_cycle() {
        val finished=Session("old",Phase.FOCUS,Status.FINISHED,60_000,0,null,completed=true)
        val next=TimerEngine.startTask(Snapshot(session=finished,cycle=4),PlannedTask(title="Следующее"),60_000,Stamp(0,0,1)).snapshot
        assertEquals(0,next.cycle);assertEquals(Phase.FOCUS,next.session!!.phase);assertEquals(Status.RUNNING,next.session.status)
    }
    @Test fun reminder_revision_completion_deletion_and_repeat_are_checked() {
        val zone=ZoneId.of("UTC");val at=LocalDateTime.of(2026,10,25,12,0)
        val task=PlannedTask(title="Дело",reminder=at,revision=2)
        val due=at.toInstant(ZoneOffset.UTC)
        assertTrue(PlannerRules.canDeliver(task,2,due,zone))
        assertFalse(PlannerRules.canDeliver(task,1,due,zone))
        assertFalse(PlannerRules.canDeliver(task.copy(remindedRevision=2),2,due,zone))
        assertFalse(PlannerRules.canDeliver(task.copy(completed=true),2,due,zone))
        assertFalse(PlannerRules.canDeliver(task.copy(deleted=true),2,due,zone))
        assertFalse(PlannerRules.canDeliver(task,2,due.plusSeconds(3601),zone))
        assertFalse(PlannerRules.canDeliver(task,2,due,zone,enabled=false))
    }
    @Test fun restore_schedules_only_future_and_time_zone_and_dst_are_predictable() {
        val local=LocalDateTime.of(2026,10,25,12,0);val task=PlannedTask(title="Дело",reminder=local,revision=1)
        val now=Instant.parse("2026-10-25T08:00:00Z")
        assertEquals(Instant.parse("2026-10-25T09:00:00Z"),PlannerRules.scheduled(task,now,ZoneId.of("Europe/Moscow")))
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"),PlannerRules.scheduled(task,now,ZoneId.of("UTC")))
        assertNull(PlannerRules.scheduled(task,now.plusSeconds(86400),ZoneId.of("UTC")))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"),PlannerRules.instant(LocalDateTime.of(2026,3,29,2,30),ZoneId.of("Europe/Berlin")))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"),PlannerRules.instant(LocalDateTime.of(2026,10,25,2,30),ZoneId.of("Europe/Berlin")))
    }
    @Test fun moving_or_disabling_reminder_changes_plan_but_preserves_its_local_meaning() {
        val zone=ZoneId.of("UTC");val now=Instant.parse("2026-10-25T10:00:00Z")
        val task=PlannedTask(title="А",reminder=LocalDateTime.of(2026,10,25,12,0),revision=1)
        val moved=task.copy(reminder=task.reminder!!.plusDays(1),revision=2)
        assertNotEquals(PlannerRules.scheduled(task,now,zone),PlannerRules.scheduled(moved,now,zone))
        assertNull(PlannerRules.scheduled(task.copy(reminder=null),now,zone))
    }
    @Test fun cancellation_is_not_a_storage_error_and_successful_retry_clears_real_error()=runBlocking {
        val errors=OperationErrors()
        try {errors.run("read",1){throw CancellationException()};fail()}catch(_:CancellationException){}
        assertNull(errors.message.value)
        assertFalse(errors.run("read",1){throw IllegalStateException("I/O")})
        assertEquals(1,errors.message.value)
        assertTrue(errors.run("read",1){})
        assertNull(errors.message.value)
    }
    @Test fun successful_tick_does_not_hide_an_unresolved_save_error()=runBlocking {
        val errors=OperationErrors()
        errors.run("note",2){throw IllegalStateException()}
        errors.run("read",1){}
        assertEquals(2,errors.message.value)
        errors.run("note",2){}
        assertNull(errors.message.value)
    }
    @Test fun focus_caption_distinguishes_current_result_and_rest() {
        assertEquals(CycleCaption(CycleCaptionKind.CURRENT,1),cycleCaption(Snapshot()))
        val finished=Session("one",Phase.FOCUS,Status.FINISHED,60_000,0,null,completed=true)
        assertEquals(CycleCaption(CycleCaptionKind.FINISHED,1),cycleCaption(Snapshot(session=finished,cycle=1)))
        assertEquals(CycleCaption(CycleCaptionKind.REST,1),cycleCaption(Snapshot(cycle=1,phase=Phase.SHORT_BREAK)))
        assertEquals(CycleCaptionKind.MANUAL_REST,cycleCaption(Snapshot(phase=Phase.SHORT_BREAK)).kind)
    }
    @Test fun timer_notification_cleanup_preserves_task_notifications_and_supports_v1() {
        assertTrue(NotificationIdentity.timer("timer","timer_s1_v1"))
        assertTrue(NotificationIdentity.timer(null,"timer_s0_v0"))
        assertFalse(NotificationIdentity.timer("task:a","task_reminders"))
        assertFalse(NotificationIdentity.timer("task:b","task_reminders"))
        assertFalse(NotificationIdentity.timer(null,"task_reminders"))
    }
    @Test fun manual_move_preserves_reminder_day_offset_and_time_without_touching_the_title() {
        val original=PlannedTask(title="Сдать",date=LocalDate.of(2026,10,25),
            reminder=LocalDateTime.of(2026,10,24,16,30))
        val moved=PlannerRules.move(original,LocalDate.of(2026,11,2))
        assertEquals(LocalDateTime.of(2026,11,1,16,30),moved.reminder)
        assertEquals(original.title,moved.title)
        assertEquals(original.id,moved.id)
        assertEquals(LocalDate.of(2026,10,25),original.date)
    }
}
