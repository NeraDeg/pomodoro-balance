package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FixesRegressionTest {
    private fun note(type: BlockType=BlockType.PARAGRAPH,text: String="Привет мир") =
        Note(id="one-note",title="Название",folderId="study",revision=7,blocks=listOf(NoteBlock(id="block",type=type,text=text)))
    @Test fun enter_at_beginning_middle_and_end_keeps_note_and_places_cursor_after_newline() {
        for(position in listOf(0,6,10)) {
            val n=note();val text=n.blocks[0].text
            val changed=text.take(position)+"\n"+text.drop(position)
            val result=DocumentRules.edit(n,"block",changed,position+1,position+1)
            assertEquals(n.id,result.note.id);assertEquals(n.title,result.note.title);assertEquals(n.folderId,result.note.folderId)
            assertEquals(2,result.note.blocks.size);assertEquals(0,result.start);assertEquals(0,result.end)
            assertEquals(text.drop(position),result.note.blocks.last().text)
            assertEquals(result.note.blocks.last().id,result.focus)
        }
    }
    @Test fun repeated_enter_and_multiline_paste_do_not_create_another_note_or_duplicate_text() {
        var n=note(text="абв")
        val first=DocumentRules.edit(n,"block","а\nбв",2,2)
        val second=DocumentRules.edit(first.note,first.focus,"\nбв",1,1)
        assertEquals("one-note",second.note.id)
        assertEquals(listOf("а","","бв"),second.note.blocks.map{it.text})
        val pasted=DocumentRules.edit(n,"block","аРаз\nДвабв",8,8)
        assertEquals("аРаз\nДвабв",pasted.note.blocks.joinToString("\n"){it.text})
        assertEquals(3,pasted.start)
    }
    @Test fun checklist_continues_unchecked_and_empty_list_item_ends_list() {
        for(type in listOf(BlockType.BULLET,BlockType.NUMBERED,BlockType.CHECK)) {
            val n=note(type,"пункт").let{it.copy(blocks=listOf(it.blocks[0].copy(checked=true)))}
            val next=DocumentRules.edit(n,"block","пункт\n",6,6)
            assertEquals(type,next.note.blocks[1].type);assertFalse(next.note.blocks[1].checked)
            val exit=DocumentRules.edit(next.note,next.focus,"\n",1,1)
            assertEquals(2,exit.note.blocks.size);assertEquals(BlockType.PARAGRAPH,exit.note.blocks.last().type)
        }
    }
    @Test fun split_keeps_bold_and_color_ranges_and_independent_undo_restores_original_block() {
        val b=NoteBlock(id="x",text="абвг",marks=listOf(BoldRange(0,4)),colors=listOf(ColorRange(1,4,TextColor.BLUE)))
        val n=Note(id="note",blocks=listOf(b))
        val result=DocumentRules.edit(n,"x","аб\nвг",3,3)
        assertEquals(listOf(ColorRange(0,2,TextColor.BLUE)),result.note.blocks[1].colors)
        assertEquals(listOf(BoldRange(0,2)),result.note.blocks[1].marks)
        val (without,removed)=DocumentRules.remove(result.note,result.note.blocks[0].id)
        val restored=DocumentRules.undo(without,removed)
        assertEquals(result.note.blocks,restored.blocks);assertEquals(n.id,restored.id)
    }
    @Test fun delete_last_block_leaves_one_input_and_undo_preserves_text_typed_after_delete() {
        val n=note()
        val (changed,removed)=DocumentRules.remove(n,"block")
        assertEquals(1,changed.blocks.size);assertEquals("",changed.blocks.single().text)
        assertEquals(n.blocks,DocumentRules.undo(changed,removed).blocks)
        val typed=changed.copy(blocks=listOf(changed.blocks.single().copy(text="новое")))
        assertEquals(2,DocumentRules.undo(typed,removed).blocks.size)
    }
    @Test fun color_and_bold_are_independent_support_mixed_selection_and_default_reset() {
        val b=NoteBlock(text="абвг",bold=true)
        val colored=DocumentRules.color(b,0,2,TextColor.GREEN)
        assertTrue(colored.bold)
        assertEquals(TextColor.GREEN,DocumentRules.currentColor(colored,0,2))
        assertNull(DocumentRules.currentColor(colored,0,4))
        val plain=DocumentRules.color(colored,0,4,TextColor.DARK)
        assertTrue(plain.bold);assertEquals(TextColor.DARK,DocumentRules.currentColor(plain,0,4))
        val entire=DocumentRules.color(colored,1,1,TextColor.PLUM)
        assertEquals(TextColor.PLUM,entire.color);assertTrue(entire.colors.isEmpty())
    }
    @Test fun inserted_and_deleted_text_rebases_color_and_bold_ranges() {
        val b=NoteBlock(text="абвг",marks=listOf(BoldRange(1,4)),colors=listOf(ColorRange(1,4,TextColor.BLUE)))
        val inserted=NoteRules.replaceText(b,"абXXвг")
        assertEquals(listOf(ColorRange(1,6,TextColor.BLUE)),inserted.colors)
        assertEquals(listOf(BoldRange(1,6)),inserted.marks)
        val deleted=NoteRules.replaceText(inserted,"аг")
        assertEquals(listOf(ColorRange(1,2,TextColor.BLUE)),deleted.colors)
    }
    @Test fun saved_status_requires_successful_latest_revision_and_exposes_retry_failure() {
        var status=SaveProgress(2).failure(4)
        assertFalse(status.current(4))
        status=status.success(3);assertFalse(status.current(4));assertEquals(4L,status.failed)
        status=status.success(4);assertTrue(status.current(4))
        status=status.failure(4,force=true);assertFalse(status.current(4))
        assertTrue(status.success(4).current(4))
    }
    @Test fun reset_running_or_paused_drops_only_current_progress_keeps_link_and_cycle() {
        val task=PlannedTask(id="task",title="Дело")
        val running=TimerEngine.startTask(Snapshot(cycle=2),task,60_000,Stamp(0,0,1)).snapshot
        val paused=TimerEngine.pause(running,Stamp(10_000,10_000,1)).snapshot
        for(s in listOf(running,paused)) {
            val reset=TimerEngine.reset(s)
            assertNull(reset.record);assertNull(reset.snapshot.session);assertEquals(2,reset.snapshot.cycle)
            assertEquals("Дело",reset.snapshot.draft);assertEquals("task",reset.snapshot.selectedTaskId)
            val new=TimerEngine.start(reset.snapshot,60_000,Stamp(20_000,20_000,1)).snapshot
            assertEquals(60_000L,new.session!!.remaining);assertEquals("task",new.session.taskId)
            assertNotEquals(s.session!!.id,new.session.id)
            assertEquals(new,TimerEngine.alarm(new,s.session.id,s.session.revision,Stamp(60_000,60_000,1)).snapshot)
        }
    }
    @Test fun reset_finished_keeps_record_and_cycle_and_idle_reset_never_starts() {
        val running=TimerEngine.start(Snapshot(cycle=3),60_000,Stamp(0,0,1)).snapshot
        val finished=TimerEngine.reconcile(running,Stamp(60_000,60_000,1))
        assertNotNull(finished.record)
        val reset=TimerEngine.reset(finished.snapshot)
        assertEquals(4,reset.snapshot.cycle);assertNull(reset.record);assertNull(reset.snapshot.session)
        val next=TimerEngine.start(reset.snapshot,60_000,Stamp(61_000,61_000,1))
        assertEquals(0,next.snapshot.cycle)
        assertNull(TimerEngine.reset(Snapshot()).snapshot.session)
    }
    @Test fun circle_controls_state_and_rapid_taps_are_gated_without_finished_auto_transition() {
        val gate=TapGate()
        assertTrue(gate.accept(Stamp(0,0,1)));assertFalse(gate.accept(Stamp(50,50,1)))
        assertTrue(gate.accept(Stamp(400,400,1)))
        val started=TimerEngine.tap(Snapshot(),60_000,Stamp(0,0,1)).snapshot
        val paused=TimerEngine.tap(started,60_000,Stamp(1_000,1_000,1)).snapshot
        assertEquals(Status.PAUSED,paused.session!!.status)
        val resumed=TimerEngine.tap(paused,60_000,Stamp(2_000,2_000,1)).snapshot
        assertEquals(started.session!!.id,resumed.session!!.id)
        val finished=TimerEngine.reconcile(resumed,Stamp(100_000,100_000,1)).snapshot
        assertEquals(finished,TimerEngine.tap(finished,60_000,Stamp(100_001,100_001,1)).snapshot)
    }
    @Test fun editing_unchanged_past_reminder_is_allowed_but_assignment_or_reenable_is_not() {
        val zone=ZoneId.of("UTC");val now=Instant.parse("2026-10-25T12:00:00Z")
        val old=PlannedTask(title="Дело",reminder=LocalDateTime.of(2026,10,25,11,0),revision=2)
        assertNull(PlannerRules.validate(old.copy(title="Новое имя"),now,zone,old))
        assertEquals(TaskProblem.REMINDER_IN_PAST,PlannerRules.validate(old,now,zone))
        assertEquals(TaskProblem.REMINDER_IN_PAST,PlannerRules.validate(old.copy(reminder=old.reminder!!.minusMinutes(1)),now,zone,old))
        assertEquals(TaskProblem.REMINDER_IN_PAST,PlannerRules.validate(PlannerRules.move(old,old.date.minusDays(1)),now,zone,old))
        assertNull(PlannerRules.scheduled(old,now,zone))
    }
}
