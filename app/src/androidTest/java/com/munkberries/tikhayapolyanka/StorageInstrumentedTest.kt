package com.munkberries.tikhayapolyanka

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.munkberries.tikhayapolyanka.core.*
import com.munkberries.tikhayapolyanka.data.*
import com.munkberries.tikhayapolyanka.system.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.*

/** Every test uses a uniquely named database. Never opens the author's polyanka.db. */
@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private val context: Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val databases=mutableListOf<String>()
    private val opened=mutableListOf<LocalDatabase>()
    private fun name()=("stage2-test-"+UUID.randomUUID()+".db").also{databases+=it}
    private fun db(name: String=name())=LocalDatabase(russianContext(context),name).also{opened+=it}
    @After fun cleanup(){opened.forEach{it.close()};databases.forEach{context.deleteDatabase(it)}}

    @Test fun migrate_v1_running_and_paused_without_changing_any_timer_payload() {
        for(status in listOf(Status.RUNNING,Status.PAUSED)) {
            val name=name()
            val session=Session("old-"+status,Phase.FOCUS,status,1_500_000,900_000,
                if(status==Status.RUNNING) Stamp(1_000_000,50_000,1) else null,
                intervals=listOf(Interval(0,600_000)),title="Старая задача")
            val snapshot=Snapshot(session=session,cycle=2,draft="Черновик")
            val payload=JSONObject(Codec.encode(snapshot)).apply{getJSONObject("session").remove("taskId")}.toString()
            val historic=Codec.session(session.copy(id="history",status=Status.FINISHED,completed=true,
                endedAt=600_000,anchor=null,remaining=0)).apply{remove("taskId")}.toString()
            val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path,null).use{old->
                old.execSQL("CREATE TABLE timer_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
                old.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, ended_at INTEGER NOT NULL, payload TEXT NOT NULL, signal_claimed INTEGER NOT NULL)")
                old.execSQL("INSERT INTO timer_state VALUES(1,?)",arrayOf(payload))
                old.execSQL("INSERT INTO history VALUES('history',600000,?,1)",arrayOf(historic))
                old.version=1
            }
            val migrated=db(name)
            assertEquals(snapshot,migrated.read());assertEquals(1,migrated.history().size)
            assertEquals("Старая задача",migrated.history().single().session.title)
            assertNull(migrated.read().session!!.taskId)
            migrated.readableDatabase.rawQuery("SELECT payload FROM timer_state",null).use{it.moveToFirst();assertEquals(payload,it.getString(0))}
            assertEquals(3,migrated.folders().size);assertEquals(4,migrated.readableDatabase.version)
            val expected=setOf("timer_state","history","folders","notes","note_blocks","tasks")
            val tables=migrated.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null).use{c->
                buildSet{while(c.moveToNext())add(c.getString(0))}}
            assertTrue(tables.containsAll(expected))
            migrated.deleteFolder("study");migrated.close()
            assertEquals(2,db(name).folders().size)
        }
    }
    @Test fun fresh_schema_matches_migrated_schema_and_folder_seeding_is_one_time() {
        val fresh=db()
        assertEquals(3,fresh.folders().size)
        fresh.deleteFolder("ideas");assertEquals(2,fresh.folders().size)
        fresh.writableDatabase // schema version 2, not reseeded on access
        assertEquals(2,fresh.folders().size)
    }
    @Test fun note_roundtrip_format_search_order_delete_folder_and_late_autosave() {
        val db=db()
        val blocks=listOf(NoteBlock(id="h",type=BlockType.HEADING,text="Клетка",bold=true),
            NoteBlock(id="p",text="БИОЛОГИЯ",marks=listOf(BoldRange(0,4))),
            NoteBlock(id="c",type=BlockType.CHECK,text="Выучить",checked=true),
            NoteBlock(id="n",type=BlockType.NUMBERED,text="Пункт"))
        val original=Note(title="Конспект",folderId="study",blocks=blocks,updatedAt=10,revision=1,draft=false)
        db.createNote(original)
        assertEquals(original,db.note(original.id))
        assertEquals(original.id,db.notes("биология","study").single().id)
        assertTrue(db.notes("биология","ideas").isEmpty())
        assertTrue(db.notes("%' OR 1=1 --").isEmpty())
        val newer=original.copy(title="Новая версия",revision=3,updatedAt=20,pinned=true)
        assertTrue(db.saveNote(newer));assertFalse(db.saveNote(original.copy(revision=2)))
        assertEquals("Новая версия",db.note(original.id)!!.title)
        db.deleteFolder("study")
        assertNull(db.note(original.id)!!.folderId)
        assertEquals(blocks,db.note(original.id)!!.blocks)
        db.deleteNote(original.id)
        assertFalse(db.saveNote(newer.copy(revision=999)))
        assertNull(db.note(original.id));assertTrue(db.notes().isEmpty())
    }
    @Test fun blank_new_note_is_discarded_after_latest_save_but_nonempty_is_retained() {
        val db=db();val empty=Note(updatedAt=1)
        db.createNote(empty);db.finishNote(empty.id);assertNull(db.note(empty.id))
        val erased=Note(updatedAt=1);db.createNote(erased)
        db.saveNote(erased.copy(revision=1,blocks=listOf(NoteBlock(text="Временный текст"))))
        db.saveNote(erased.copy(revision=2,blocks=listOf(NoteBlock())))
        db.finishNote(erased.id);assertNull(db.note(erased.id))
        val note=Note(updatedAt=1);db.createNote(note)
        db.saveNote(note.copy(revision=1,blocks=listOf(NoteBlock(text="Последний ввод"))))
        db.finishNote(note.id);assertEquals(1,db.notes().size)
    }
    @Test fun folders_are_unicode_unique_and_deleted_folder_does_not_reappear() {
        val db=db()
        try {db.saveFolder("duplicate","УЧЁБА");fail()}catch(_:android.database.sqlite.SQLiteConstraintException){}
        db.saveFolder("new","Своё");db.saveFolder("new","Другое")
        assertEquals("Другое",db.folders().first{it.id=="new"}.name)
        db.deleteFolder("new");assertFalse(db.folders().any{it.id=="new"})
    }
    @Test fun task_lifecycle_claim_and_linked_history_survive_rename_and_deletion() {
        val db=db();val date=LocalDate.of(2026,10,25);val local=date.atTime(12,0);val zone=ZoneId.of("UTC")
        val initial=db.saveTask(PlannedTask(title="Дело",date=date,reminder=local))
        assertNull(db.claimReminder(initial.id,initial.revision,local.toInstant(ZoneOffset.UTC),zone,false))
        assertNotNull(db.claimReminder(initial.id,initial.revision,local.toInstant(ZoneOffset.UTC),zone,true))
        assertNull(db.claimReminder(initial.id,initial.revision,local.toInstant(ZoneOffset.UTC),zone,true))
        val snapshot=TimerEngine.startTask(Snapshot(),initial,60_000,Stamp(0,0,1)).snapshot
        val completed=TimerEngine.reconcile(snapshot,Stamp(60_000,60_000,1))
        db.commit(completed)
        val changed=db.saveTask(initial.copy(title="Новое имя",date=date.plusDays(1)))
        assertNull(db.claimReminder(initial.id,initial.revision,local.toInstant(ZoneOffset.UTC),zone,true))
        val done=db.saveTask(changed.copy(completed=true))
        assertTrue(db.task(done.id)!!.completed)
        db.saveTask(done.copy(deleted=true))
        assertNull(db.task(done.id))
        assertEquals("Дело",db.history().single().session.title)
        assertEquals(initial.id,db.history().single().session.taskId)
    }
    @Test fun russian_resources_and_native_picker_widgets_under_english_german_and_russian_contexts() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for(language in listOf("en-US","de-DE","ru-RU")) {
                val foreign=context.createConfigurationContext(Configuration(context.resources.configuration).apply{
                    setLocales(LocaleList(Locale.forLanguageTag(language)))})
                val localized=russianContext(foreign)
                assertEquals("ru",localized.resources.configuration.locales[0].language)
                assertEquals("Отмена",localized.getString(android.R.string.cancel))
                assertEquals("1 минута",localized.resources.getQuantityString(R.plurals.minutes,1,1))
                assertEquals("2 минуты",localized.resources.getQuantityString(R.plurals.minutes,2,2))
                assertEquals("5 минут",localized.resources.getQuantityString(R.plurals.minutes,5,5))
                val theme=android.view.ContextThemeWrapper(localized,R.style.Theme_Polyanka)
                val picker=DatePicker(theme);picker.updateDate(2026,9,25)
                fun texts(view: View): List<String> = when(view) {
                    is ViewGroup -> (0 until view.childCount).flatMap{texts(view.getChildAt(it))}
                    is TextView -> listOf(view.text.toString())
                    else->emptyList()
                }
                val visible=texts(picker).joinToString(" ").lowercase(RussianDates.locale)
                assertFalse(visible.contains("october"))
                assertFalse(visible.contains("oktober"))
                assertTrue(visible.contains("окт"))
                val time=TimePicker(theme);time.setIs24HourView(true);assertTrue(time.is24HourView)
            }
        }
    }
    @Test fun task_pending_intents_and_timer_have_independent_identity() {
        val suffix=UUID.randomUUID().toString()
        val reminders=TaskReminders(russianContext(context))
        fun task(id: String)=PendingIntent.getBroadcast(context,0,reminders.alarmIntent(id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val first=task("a-"+suffix);val second=task("b-"+suffix)
        val timer=PendingIntent.getBroadcast(context,0,Intent(context,TimerReceiver::class.java)
            .setAction("test-timer-"+suffix),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {assertNotEquals(first,second);assertNotEquals(first,timer);assertNotEquals(second,timer)}
        finally {first.cancel();second.cancel();timer.cancel()}
    }
    @Test fun calendar_and_notes_filters_restore_from_saved_state_without_touching_user_database() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app=context.applicationContext as android.app.Application
            val handle=androidx.lifecycle.SavedStateHandle()
            val first=com.munkberries.tikhayapolyanka.ui.PolyankaViewModel(app,handle)
            val store=androidx.lifecycle.ViewModelStore()
            store.put("first",first)
            first.selectDate(LocalDate.of(2028,2,29));first.selectMonth(YearMonth.of(2028,2))
            first.search("БИОЛОГИЯ");first.filter("study")
            val restoredHandle=androidx.lifecycle.SavedStateHandle(mapOf(
                "calendarDate" to first.calendarDate.value,"calendarMonth" to first.calendarMonth.value,
                "notesQuery" to first.query.value,"folder" to first.folder.value))
            val restored=com.munkberries.tikhayapolyanka.ui.PolyankaViewModel(app,restoredHandle)
            store.put("second",restored)
            assertEquals("2028-02-29",restored.calendarDate.value)
            assertEquals("2028-02",restored.calendarMonth.value)
            assertEquals("БИОЛОГИЯ",restored.query.value);assertEquals("study",restored.folder.value)
            store.clear()
        }
    }

    @Test fun upgrade_v2_preserves_notes_tasks_timer_history_and_defaults_colors() {
        val name=name();val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val paused=Session("v2-active",Phase.FOCUS,Status.PAUSED,1_500_000,900_000,null,
            intervals=listOf(Interval(0,600_000)),title="Старое занятие")
        val state=Codec.encode(Snapshot(session=paused,draft="Черновик",cycle=2))
        val previous=paused.copy(id="v2-history",status=Status.FINISHED,remaining=0,completed=true,endedAt=600_000)
        val task=PlannedTask(id="old-task",title="Дело",revision=1)
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            old.execSQL("CREATE TABLE timer_state (id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)")
            old.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY,ended_at INTEGER NOT NULL,payload TEXT NOT NULL,signal_claimed INTEGER NOT NULL)")
            context.assets.open("db/migration_1_2.sql").bufferedReader().useLines{lines->
                lines.filter{it.isNotBlank()}.forEach{old.execSQL(it.trim().removeSuffix(";"))}}
            old.execSQL("INSERT INTO timer_state VALUES(1,?)",arrayOf(state))
            old.execSQL("INSERT INTO history VALUES('v2-history',600000,?,1)",arrayOf(Codec.session(previous).toString()))
            old.execSQL("INSERT INTO notes VALUES('old-note','Конспект','study',1,100,1,0,0,'конспект',1)")
            old.execSQL("INSERT INTO note_blocks VALUES('old-block','old-note',0,'CHECK','Текст',1,1,'[[0,2]]')")
            old.execSQL("INSERT INTO tasks VALUES('old-task',?, ?,1,0,0,NULL,-1)",arrayOf(task.date.toString(),OrganizerCodec.task(task)))
            old.version=2
        }
        val migrated=db(name)
        val note=migrated.note("old-note")!!
        assertEquals(NoteBackground.CREAM,note.background)
        assertEquals("Конспект",note.title);assertEquals("study",note.folderId);assertTrue(note.pinned)
        assertTrue(note.blocks.single().checked);assertTrue(note.blocks.single().bold)
        assertEquals(TextColor.DARK,note.blocks.single().color);assertTrue(note.blocks.single().colors.isEmpty())
        assertEquals("Дело",migrated.task("old-task")!!.title)
        assertEquals(2,migrated.read().cycle);assertEquals("Черновик",migrated.read().draft)
        assertEquals(paused,migrated.read().session);assertEquals(previous,migrated.history().single().session)
    }
    @Test fun enter_color_save_reopen_keeps_one_note_and_exact_structure() {
        val db=db();val original=Note(title="Моя запись",folderId="study")
        db.createNote(original)
        val initial=original.blocks.single()
        val colored=initial.copy(text="Русский текст",colors=listOf(ColorRange(0,7,TextColor.BLUE)),bold=true)
        val edit=DocumentRules.edit(original.copy(blocks=listOf(colored),background=NoteBackground.LAVENDER),initial.id,"Русский\n текст",8,8)
        val saved=edit.note.copy(revision=1)
        assertEquals(original.id,saved.id);assertEquals(0,edit.start)
        assertTrue(db.saveNote(saved));db.finishNote(saved.id)
        val reopened=db.note(saved.id)!!
        assertEquals(NoteBackground.LAVENDER,reopened.background)
        assertEquals(saved.blocks,reopened.blocks);assertEquals(original.id,reopened.id)
        assertEquals(1,db.notes().size)
    }
    @Test fun deleted_history_retains_completion_guard_without_changing_active_timer_or_other_data() {
        val db=db()
        val started=TimerEngine.start(Snapshot(),60_000,Stamp(0,0,1)).snapshot
        val finished=TimerEngine.reconcile(started,Stamp(60_000,60_000,1))
        db.commit(finished)
        val fresh=TimerEngine.start(TimerEngine.prepare(finished.snapshot,Phase.FOCUS).snapshot,60_000,Stamp(61_000,61_000,1))
        db.commit(fresh)
        val before=db.read()
        db.hideHistory(finished.record!!.session.id)
        assertTrue(db.history().isEmpty());assertEquals(before,db.read())
        val controller=TimerController(db,com.munkberries.tikhayapolyanka.core.Clock{Stamp(62_000,62_000,1)}){fail("No repeated signal")}
        controller.dispatch{s,n->TimerEngine.alarm(s,finished.record.session.id,started.session!!.revision,n)}
        assertTrue(db.history().isEmpty());assertEquals(before,db.read())
        assertFalse(db.commit(Change(before,finished.record)))
        assertTrue(db.history().isEmpty())
        db.readableDatabase.rawQuery("SELECT signal_claimed,hidden,payload FROM history",null).use{
            assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0));assertEquals(1,it.getInt(1));assertEquals("{}",it.getString(2))}
    }
    @Test fun timer_reset_persists_selected_link_and_invalidates_old_alarm() {
        val db=db();val clock=Stamp(0,0,1)
        val started=TimerEngine.startTask(Snapshot(cycle=2),PlannedTask(id="task",title="Дело"),60_000,clock)
        db.commit(started)
        db.commit(TimerEngine.reset(db.read()))
        val restored=db.read()
        assertNull(restored.session);assertEquals("task",restored.selectedTaskId);assertEquals(2,restored.cycle)
        val controller=TimerController(db,com.munkberries.tikhayapolyanka.core.Clock{Stamp(100_000,100_000,1)}){fail()}
        controller.dispatch{s,n->TimerEngine.alarm(s,started.snapshot.session!!.id,1,n)}
        assertEquals(restored,db.read());assertTrue(db.history().isEmpty())
    }

    @Test fun upgrade_v3_preserves_legacy_colors_timer_tasks_and_history() {
        val name=name();val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val state=Snapshot(cycle=2,draft="Сохранено",session=Session("active-v3",Phase.FOCUS,Status.PAUSED,60000,30000,null))
        val done=Session("done-v3",Phase.FOCUS,Status.FINISHED,60000,0,null,completed=true,endedAt=60000)
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            old.execSQL("CREATE TABLE timer_state (id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)")
            old.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY,ended_at INTEGER NOT NULL,payload TEXT NOT NULL,signal_claimed INTEGER NOT NULL)")
            for(asset in listOf("db/migration_1_2.sql","db/migration_2_3.sql"))
                context.assets.open(asset).bufferedReader().useLines{lines->lines.filter{it.isNotBlank()}.forEach{old.execSQL(it.trim().removeSuffix(";"))}}
            old.execSQL("INSERT INTO timer_state VALUES(1,?)",arrayOf(Codec.encode(state)))
            old.execSQL("INSERT INTO history VALUES('done-v3',60000,?,1,0)",arrayOf(Codec.session(done).toString()))
            old.execSQL("INSERT INTO notes VALUES('n','Заголовок','study',1,100,1,0,0,'заголовок',1)")
            old.execSQL("INSERT INTO note_blocks VALUES('b','n',0,'CHECK','Текст',1,1,'[[0,2]]','BLUE',?)",
                arrayOf(OrganizerCodec.colors(listOf(ColorRange(0,2,TextColor.PLUM)))))
            val task=PlannedTask(id="t",title="Дело",revision=1)
            old.execSQL("INSERT INTO tasks VALUES('t',?,?,1,0,0,NULL,-1)",arrayOf(task.date.toString(),OrganizerCodec.task(task)))
            old.version=3
        }
        val migrated=db(name);val n=migrated.note("n")!!
        assertEquals(NoteBackground.CREAM,n.background)
        assertEquals("Текст",n.blocks.single().text);assertTrue(n.blocks.single().checked);assertTrue(n.blocks.single().bold)
        assertEquals(TextColor.BLUE,n.blocks.single().color)
        assertEquals(listOf(ColorRange(0,2,TextColor.PLUM)),n.blocks.single().colors)
        assertEquals(state,migrated.read());assertEquals(done,migrated.history().single().session)
        assertEquals("Дело",migrated.task("t")!!.title)
    }
    @Test fun six_backgrounds_save_reopen_move_and_stale_save_keep_last_text_and_note_id() {
        val name=name();var database=db(name)
        var note=Note(title="Цвет",draft=false,blocks=listOf(NoteBlock(text="Последние буквы я",bold=true)))
        database.createNote(note)
        for(color in NoteBackground.entries) {
            val stale=note
            note=note.copy(background=color,folderId=if(color.ordinal%2==0)"study" else null,revision=note.revision+1)
            assertTrue(database.saveNote(note));assertFalse(database.saveNote(stale))
            database.close();database=db(name)
            assertEquals(note,database.note(note.id));assertEquals(color,database.notes().single().background)
        }
    }

    @Test fun finished_focus_reopens_as_same_offer_without_another_record_or_countdown() {
        val name=name();val first=db(name)
        val running=TimerEngine.start(Snapshot(cycle=3),60_000,Stamp(0,0,1),"stage3-focus").snapshot
        val finished=TimerEngine.reconcile(running,Stamp(60_000,60_000,1))
        assertTrue(first.commit(finished));first.close()
        val reopened=db(name);var signals=0
        val controller=TimerController(reopened,com.munkberries.tikhayapolyanka.core.Clock{Stamp(900_000,900_000,1)}){signals++}
        controller.reconcile()
        val restored=controller.snapshot()
        assertEquals(finished.snapshot,restored);assertEquals(1,reopened.history().size);assertEquals(0,signals)
        assertEquals(Phase.LONG_BREAK,TimerPresentation.breakOffer(restored,180_000,1_020_000)!!.phase)
        controller.dispatch{s,n->TimerPresentation.startOfferedBreak(s,180_000,1_020_000,n)}
        val rest=controller.snapshot().session!!
        controller.dispatch{s,n->TimerPresentation.startOfferedBreak(s,180_000,1_020_000,n)}
        assertEquals(rest,controller.snapshot().session);assertEquals(1,reopened.history().size)
    }
    @Test fun blackbird_is_focus_only_and_all_off_variants_keep_silent_channel() {
        val notifier=CompletionNotifier(context)
        val audible=com.munkberries.tikhayapolyanka.data.TimerSettings(sound=true,vibration=true)
        val silent=audible.copy(sound=false)
        assertEquals("timer_blackbird_soft_2_v1",notifier.channelId(audible,Phase.FOCUS))
        assertEquals("timer_s1_v1",notifier.channelId(audible,Phase.SHORT_BREAK))
        assertEquals("timer_s0_v1",notifier.channelId(silent,Phase.FOCUS))
        assertEquals("android.resource://"+context.packageName+"/raw/tp_blackbird_soft",notifier.blackbirdUri().toString())
        context.resources.openRawResource(com.munkberries.tikhayapolyanka.R.raw.tp_blackbird_soft).use {
            val header=ByteArray(12);assertEquals(12,it.read(header));assertEquals("RIFF",String(header.copyOfRange(0,4),Charsets.US_ASCII))
        }
    }

    @Test fun transfer_roundtrip_preserves_notes_tasks_history_and_pauses_only_the_copy() {
        val source=db()
        val note=Note(id="transfer-note",title="Тестовый конспект",folderId="study",draft=false,revision=1,
            background=NoteBackground.SAGE,blocks=listOf(NoteBlock(id="transfer-block",text="Текст ёжик",type=BlockType.CHECK,checked=true,bold=true)))
        source.createNote(note)
        val task=source.saveTask(PlannedTask(id="transfer-task",title="Тестовое дело"))
        val active=TimerEngine.start(Snapshot(cycle=1),60_000,Stamp(0,0,1),"transfer-active").snapshot
        source.commit(Change(active))
        val completed=TimerEngine.reconcile(TimerEngine.start(Snapshot(),60_000,Stamp(0,0,1),"transfer-done").snapshot,Stamp(60_000,60_000,1))
        source.commit(Change(active,completed.record))
        val paused=TimerEngine.pause(active,Stamp(10_000,10_000,1)).snapshot
        val tables=source.exportTables(paused)
        assertEquals(active,source.read())
        val target=db();target.restoreTransferTables(tables){}
        assertEquals(note,target.note(note.id));assertEquals(task,target.task(task.id));assertEquals(paused,target.read())
        assertEquals(1,target.history().size);assertFalse(target.commit(Change(paused,completed.record)))
    }
    @Test fun transfer_does_not_replace_existing_records() {
        val source=db();source.createNote(Note(title="Входящая",draft=false))
        val target=db();val original=Note(title="Существующая",draft=false);target.createNote(original)
        try {target.restoreTransferTables(source.exportTables(Snapshot())){fail("No settings write")};fail()}
        catch(_: IllegalStateException){}
        assertEquals(original,target.note(original.id));assertEquals(1,target.notes().size)
    }
    @Test fun transfer_settings_failure_rolls_back_database_and_default_folders() {
        val source=db();source.createNote(Note(title="Входящая",draft=false))
        val target=db()
        try {target.restoreTransferTables(source.exportTables(Snapshot())){throw IllegalStateException("Simulated settings failure")};fail()}
        catch(_: IllegalStateException){}
        assertTrue(target.pristineForTransfer());assertTrue(target.notes().isEmpty());assertEquals(3,target.folders().size)
    }
}

