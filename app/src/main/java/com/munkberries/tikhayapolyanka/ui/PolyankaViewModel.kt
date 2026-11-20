package com.munkberries.tikhayapolyanka.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.munkberries.tikhayapolyanka.*
import com.munkberries.tikhayapolyanka.core.*
import com.munkberries.tikhayapolyanka.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.time.*

data class Destination(val tab: Int?=null,val route: String?=null)
class PolyankaViewModel(application: Application,private val saved: SavedStateHandle): AndroidViewModel(application) {
    val runtime=(application as PolyankaApplication).runtime
    val state=runtime.state;val history=runtime.history;val settings=runtime.settings
    val notes=runtime.notes;val folders=runtime.folders;val tasks=runtime.tasks
    val now=MutableStateFlow(runtime.clock.now())
    val ready=MutableStateFlow(false)
    val errors=OperationErrors()
    val error=errors.message
    val notice=MutableStateFlow<Int?>(null)
    val tab=saved.getStateFlow("tab",0)
    val calendarDate=saved.getStateFlow("calendarDate",LocalDate.now().toString())
    val calendarMonth=saved.getStateFlow("calendarMonth",YearMonth.now().toString())
    val query=saved.getStateFlow("notesQuery","")
    val folder=saved.getStateFlow<String?>("folder",null)
    val editor=MutableStateFlow<Note?>(null)
    val focusBlock=MutableStateFlow<String?>(saved["focusBlock"])
    val selectionRevision=MutableStateFlow(0)
    val closingNote=MutableStateFlow(false)
    val taskSaving=MutableStateFlow(false)
    val creatingNote=MutableStateFlow(false)
    val manualSaving=MutableStateFlow(false)
    val noteSave=MutableStateFlow(SaveProgress())
    val removedBlock=MutableStateFlow<RemovedBlock?>(null)
    val folderSaving=MutableStateFlow(false)
    val folderError=MutableStateFlow<Int?>(null)
    val timerBusy=MutableStateFlow(false)
    private val tapGate=TapGate()
    val saveCount=MutableStateFlow(0)
    val taskDraft=MutableStateFlow<PlannedTask?>(saved.get<String>("taskDraft")?.let(OrganizerCodec::task))
    private var taskOriginal=saved.get<String>("taskOriginal")?.let(OrganizerCodec::task)
    val formProblem=MutableStateFlow<Int?>(null)
    val currentSessionDialog=MutableStateFlow(false)
    val pendingDestination=MutableStateFlow<Destination?>(null)
    val navigationRevision=MutableStateFlow(0)
    private var ticker: Job?=null
    private var savedNoticeJob: Job?=null
    private data class Command(val key: String,val error: Int,val action: suspend ()->Unit)
    private val commands=Channel<Command>(Channel.UNLIMITED)
    init {
        viewModelScope.launch { for(command in commands) {
            errors.run(command.key,command.error) { command.action();now.value=runtime.clock.now() }
        } }
        saved.get<String>("noteId")?.let { id -> run("read",R.string.error_read) {
            val note=runtime.getNote(id)
            editor.value=note
            noteSave.value=SaveProgress(note?.revision ?: -1)
            if(note==null) saved["noteId"]=null
        } }
    }
    private fun run(key: String="write",error: Int=R.string.error_save,action: suspend ()->Unit) {
        commands.trySend(Command(key,error,action))
    }
    fun visible(visible: Boolean) {
        ticker?.cancel()
        if(visible) ticker=viewModelScope.launch {
            var first=true
            while(true) {
                errors.run("read",R.string.error_read) {
                    runtime.refresh(forceAlarm=first);first=false
                    now.value=runtime.clock.now();ready.value=true
                }
                delay(500)
            }
        }
    }
    fun refresh()=run("read",R.string.error_read) { runtime.refresh(forceAlarm=true) }
    fun start()=run { runtime.start() }
    fun pause()=run { runtime.action(TimerEngine::pause) }
    fun resume()=run { runtime.action(TimerEngine::resume) }
    fun stop()=run { runtime.action(TimerEngine::stop) }
    fun timerTap() {
        if(timerBusy.value || !tapGate.accept(runtime.clock.now()))return
        timerBusy.value=true
        run { try {runtime.timerTap()} finally {timerBusy.value=false} }
    }
    fun resetTimer()=run {runtime.reset()}
    fun deleteHistory(id: String?=null)=run {runtime.hideHistory(id)}

    fun draft(text: String)=run { runtime.action { s,_ -> Change(s.copy(draft=text)) } }
    fun select(phase: Phase)=run { runtime.action { s,_ -> TimerEngine.select(s,phase) } }
    fun startOfferedBreak() {
        if(timerBusy.value)return
        timerBusy.value=true
        run { try {runtime.startOfferedBreak()} finally {timerBusy.value=false} }
    }
    fun prepare(phase: Phase,start: Boolean=false)=run {
        runtime.action { s,_ -> TimerEngine.prepare(s,phase) };if(start)runtime.start()
    }
    fun settings(update: (TimerSettings)->TimerSettings)=run { runtime.saveSettings(update) }
    fun selectDate(date: LocalDate) { saved["calendarDate"]=date.toString() }
    fun selectMonth(month: YearMonth) { saved["calendarMonth"]=month.toString() }
    fun today() { val day=LocalDate.now();selectDate(day);selectMonth(YearMonth.from(day)) }
    fun search(text: String) { saved["notesQuery"]=text }
    fun filter(id: String?) { saved["folder"]=id }
    private fun navigate(value: Int) { saved["tab"]=value;navigationRevision.value++ }
    fun requestTab(value: Int) {
        if(taskDirty()) pendingDestination.value=Destination(tab=value)
        else { clearTaskForm();navigate(value) }
    }
    fun route(uri: Uri?) {
        if(uri==null) return
        if(taskDirty()) pendingDestination.value=Destination(route=uri.toString())
        else { clearTaskForm();applyRoute(uri) }
    }
    private fun applyRoute(uri: Uri) {
        if(uri.host=="task") {
            val id=uri.pathSegments.firstOrNull()
            val fallback=runCatching { LocalDate.parse(uri.getQueryParameter("date")) }.getOrDefault(LocalDate.now())
            run("read",R.string.error_read) {
                val task=id?.let { runtime.getTask(it) }
                val date=task?.date ?: fallback
                selectDate(date);selectMonth(YearMonth.from(date));navigate(2)
                if(task!=null) setTaskForm(task) else notice.value=R.string.task_missing
            }
        } else navigate(0)
    }

    fun newNote(type: BlockType=BlockType.PARAGRAPH) {
        if(creatingNote.value || editor.value!=null)return
        creatingNote.value=true
        run("note",R.string.error_save) {
            try {openEditor(runtime.createNote(type,folder.value))}
            finally {creatingNote.value=false}
        }
    }
    fun openNote(id: String)=run("note",R.string.error_read) {
        editor.value?.takeIf { it.id!=id }?.let { runtime.finishNote(it.id) }
        runtime.getNote(id)?.let(::openEditor)
    }
    private fun openEditor(note: Note) {
        editor.value=note;saved["noteId"]=note.id;noteSave.value=SaveProgress(note.revision);removedBlock.value=null
        focusBlock.value=note.blocks.firstOrNull()?.id;saved["focusBlock"]=focusBlock.value
    }
    fun focus(id: String) { focusBlock.value=id;saved["focusBlock"]=id }
    fun selection(id: String,start: Int,end: Int) { saved["selection_"+id]=intArrayOf(start,end);selectionRevision.value++ }
    fun selection(id: String): IntArray = saved["selection_"+id] ?: intArrayOf(0,0)
    fun editNote(transform: (Note)->Note) {
        if(closingNote.value) return
        val old=editor.value ?: return
        val next=transform(old).copy(revision=old.revision+1,updatedAt=runtime.clock.now().wall)
        editor.value=next;saveCount.value++
        val captured=next
        run("note",R.string.error_save) {
            try {
                val success=runtime.saveNote(captured)
                if(!success && editor.value?.id==captured.id)check((runtime.getNote(captured.id)?.revision ?: -1)>=captured.revision)
                if(editor.value?.id==captured.id)noteSave.value=noteSave.value.success(captured.revision)
            } catch(c: CancellationException) {throw c}
            catch(e: Exception) {if(editor.value?.id==captured.id)noteSave.value=noteSave.value.failure(captured.revision);throw e}
            finally { saveCount.value-- }
        }
    }
    fun blockText(id: String,text: String,start: Int,end: Int) {
        val note=editor.value ?: return
        val result=DocumentRules.edit(note,id,text,start,end)
        editNote { result.note }
        selection(result.focus,result.start,result.end);focus(result.focus)
    }
    fun noteBackground(color: NoteBackground) {
        editNote { it.copy(background=color) }
    }
    fun saveCurrentNote() {
        if(manualSaving.value || closingNote.value || editor.value==null)return
        manualSaving.value=true
        run("note",R.string.error_save) {
            val latest=editor.value
            try {
                if(latest!=null) {
                    flushNote(latest,runtime::getNote,runtime::saveNote,{})
                    if(editor.value?.id==latest.id) {
                        noteSave.value=noteSave.value.success(latest.revision)
                        if(noteSave.value.current(editor.value!!.revision)) {
                            notice.value=R.string.saved
                            savedNoticeJob?.cancel()
                            savedNoticeJob=viewModelScope.launch{delay(2500);if(notice.value==R.string.saved)notice.value=null}
                        }
                    }
                }
            }catch(c: CancellationException){throw c}
            catch(e: Exception){if(latest!=null && editor.value?.id==latest.id)noteSave.value=noteSave.value.failure(latest.revision,force=true);throw e}
            finally{manualSaving.value=false}
        }
    }
    fun blockType(type: BlockType) {
        val id=focusBlock.value ?: return
        editNote { n -> n.copy(blocks=n.blocks.map { if(it.id==id) it.copy(type=type) else it }) }
    }
    fun bold() {
        val id=focusBlock.value ?: return
        val range=selection(id)
        editNote { n -> n.copy(blocks=n.blocks.map {
            if(it.id==id) NoteRules.toggleBold(it,range[0],range[1]) else it
        }) }
    }
    fun checkBlock(id: String,value: Boolean)=editNote { n -> n.copy(blocks=n.blocks.map {
        if(it.id==id) it.copy(checked=value) else it
    }) }
    fun addBlock() {
        val block=NoteBlock()
        editNote { it.copy(blocks=it.blocks+block) };focus(block.id)
    }
    fun removeBlock(id: String?=focusBlock.value) {
        val n=editor.value ?: return
        val target=id ?: return
        if(n.blocks.none{it.id==target})return
        val (changed,removed)=DocumentRules.remove(n,target)
        editNote {changed}
        if(removed.block.text.isNotEmpty() || removed.block.checked)removedBlock.value=removed
        val neighbor=changed.blocks[(removed.index-1).coerceIn(0,changed.blocks.lastIndex)]
        selection(neighbor.id,neighbor.text.length,neighbor.text.length);focus(neighbor.id)
    }
    fun undoBlock() {
        val removed=removedBlock.value ?: return
        editNote {DocumentRules.undo(it,removed)}
        removedBlock.value=null;selection(removed.block.id,0,0);focus(removed.block.id)
    }
    fun closeNote() {
        val latest=editor.value ?: return
        val id=latest.id
        if(closingNote.value) return
        closingNote.value=true
        run("note",R.string.error_save) {
            try {
                flushNote(latest,runtime::getNote,runtime::saveNote,runtime::finishNote)
                if(editor.value?.id==id){editor.value=null;saved["noteId"]=null;removedBlock.value=null}
            }catch(c: CancellationException){throw c}
            catch(e: Exception){noteSave.value=noteSave.value.failure(latest.revision,force=true);throw e}
            finally {closingNote.value=false}
        }
    }
    fun deleteNote(id: String) {
        if(editor.value?.id==id){editor.value=null;saved["noteId"]=null}
        run("note",R.string.error_save) { runtime.deleteNote(id) }
    }
    fun pin(note: Note) {
        if(editor.value?.id==note.id) editNote { it.copy(pinned=!it.pinned) }
        else run("note",R.string.error_save) {
            val latest=runtime.getNote(note.id) ?: return@run
            check(runtime.saveNote(latest.copy(pinned=!latest.pinned,revision=latest.revision+1,updatedAt=runtime.clock.now().wall)))
        }
    }
    fun saveFolder(id: String?,name: String,selectForNote: Boolean=false,onSuccess: ()->Unit={}) {
        if(folderSaving.value)return
        val cleaned=name.trim()
        if(cleaned.isBlank() || folders.value.any{it.id!=id && NoteRules.normalized(it.name)==NoteRules.normalized(cleaned)}) {
            folderError.value=R.string.error_folder;return
        }
        folderSaving.value=true;folderError.value=null
        run("folder",R.string.error_folder) {
            try {
                val created=runtime.folder(id,cleaned)
                if(selectForNote)editNote{it.copy(folderId=created)}
                onSuccess()
            }catch(c: CancellationException){throw c}
            catch(e: Exception){folderError.value=R.string.error_folder;throw e}
            finally {folderSaving.value=false}
        }
    }
    fun deleteFolder(id: String) {
        if(editor.value?.folderId==id) editNote { it.copy(folderId=null) }
        if(folder.value==id) filter(null)
        run("folder",R.string.error_save) { runtime.deleteFolder(id) }
    }

    fun newTask() { setTaskForm(PlannedTask(date=LocalDate.parse(calendarDate.value))) }
    fun openTask(task: PlannedTask) { setTaskForm(task) }
    private fun setTaskForm(task: PlannedTask) {
        saved["reminderEdited"]=false
        taskOriginal=task;taskDraft.value=task;formProblem.value=null
        saved["taskDraft"]=OrganizerCodec.task(task);saved["taskOriginal"]=OrganizerCodec.task(task)
    }
    fun changeTask(transform: (PlannedTask)->PlannedTask) {
        if(taskSaving.value) return
        val task=taskDraft.value ?: return
        val changed=transform(task)
        if(changed.reminder!=task.reminder)saved["reminderEdited"]=true
        taskDraft.value=changed;formProblem.value=null
        saved["taskDraft"]=OrganizerCodec.task(taskDraft.value!!)
    }
    fun taskDirty(): Boolean = taskDraft.value!=null && taskDraft.value!=taskOriginal
    fun requestCloseTask() {
        if(taskDirty()) pendingDestination.value=Destination() else clearTaskForm()
    }
    fun clearTaskForm() {
        taskDraft.value=null;taskOriginal=null;saved["taskDraft"]=null;saved["taskOriginal"]=null;formProblem.value=null
    }
    fun confirmLeave(saveChanges: Boolean) {
        val destination=pendingDestination.value ?: return
        pendingDestination.value=null
        fun proceed() {
            clearTaskForm()
            if(destination.route!=null) applyRoute(Uri.parse(destination.route))
            else destination.tab?.let(::navigate)
        }
        if(saveChanges) saveTask(::proceed) else proceed()
    }
    fun saveTask(after: ()->Unit={ clearTaskForm() }) {
        val task=taskDraft.value ?: return
        val problem=PlannerRules.validate(task,Instant.ofEpochMilli(runtime.clock.now().wall),ZoneId.systemDefault(),if(saved.get<Boolean>("reminderEdited")==true)null else taskOriginal)
        if(problem!=null) {
            formProblem.value=when(problem) {
                TaskProblem.TITLE_REQUIRED->R.string.task_title_required
                TaskProblem.EVENT_TIME_REQUIRED->R.string.event_time_required
                TaskProblem.REMINDER_IN_PAST->R.string.reminder_in_past
            };return
        }
        if(taskSaving.value) return
        taskSaving.value=true
        run("task",R.string.error_save) {
            try {
                runtime.saveTask(task,reminderEdited=saved.get<Boolean>("reminderEdited")==true)
                selectDate(task.date);selectMonth(YearMonth.from(task.date))
                after()
            } finally {taskSaving.value=false}
        }
    }
    fun completeTask(task: PlannedTask,value: Boolean)=run("task",R.string.error_save) {
        runtime.saveTask(task.copy(completed=value),validate=false)
    }
    fun deleteTask(task: PlannedTask)=run("task",R.string.error_save) { runtime.saveTask(task.copy(deleted=true),validate=false) }
    fun moveTask(task: PlannedTask,date: LocalDate)=run("task",R.string.error_save) {
        var moved=PlannerRules.move(task,date)
        if(moved.reminder?.let{!PlannerRules.instant(it,ZoneId.systemDefault()).isAfter(Instant.ofEpochMilli(runtime.clock.now().wall))}==true) {
            moved=moved.copy(reminder=null)
            notice.value=R.string.moved_reminder_past
        }
        runtime.saveTask(moved,validate=false);selectDate(date);selectMonth(YearMonth.from(date))
    }
    fun focusTask(task: PlannedTask) {
        if(state.value.session?.status in listOf(Status.RUNNING,Status.PAUSED)) {
            currentSessionDialog.value=true;return
        }
        run("task",R.string.error_save) {
            if(runtime.startTask(task.id)) navigate(0)
            else if(state.value.session?.status in listOf(Status.RUNNING,Status.PAUSED)) currentSessionDialog.value=true
            else notice.value=R.string.task_missing
        }
    }
    fun goCurrent() { currentSessionDialog.value=false;navigate(0) }

    val transferBusy=MutableStateFlow(false)
    val transferPreview=MutableStateFlow<org.json.JSONObject?>(null)
    fun exportTransfer(uri: Uri) {
        if(transferBusy.value)return
        transferBusy.value=true
        run("transfer",R.string.transfer_error) {
            try {
                val bytes=runtime.exportTransfer()
                withContext(Dispatchers.IO) { requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri,"wt")).use{it.write(bytes)} }
                notice.value=R.string.transfer_exported
            }finally{transferBusy.value=false}
        }
    }
    fun inspectTransfer(uri: Uri) {
        if(transferBusy.value)return
        transferBusy.value=true
        run("transfer",R.string.transfer_error) {
            try {
                val bytes=withContext(Dispatchers.IO) { requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use(TransferFile::read) }
                transferPreview.value=runtime.inspectTransfer(bytes)
            }finally{transferBusy.value=false}
        }
    }
    fun importTransfer() {
        val candidate=transferPreview.value ?: return
        if(transferBusy.value)return
        transferBusy.value=true
        run("transfer",R.string.transfer_error) {
            try {runtime.importTransfer(candidate);transferPreview.value=null;notice.value=R.string.transfer_imported}
            finally{transferBusy.value=false}
        }
    }
}







