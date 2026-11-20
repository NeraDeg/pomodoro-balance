package com.munkberries.tikhayapolyanka

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.munkberries.tikhayapolyanka.core.*
import com.munkberries.tikhayapolyanka.data.*
import com.munkberries.tikhayapolyanka.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.UUID

fun russianContext(base: Context): Context = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
    setLocales(LocaleList(RussianDates.locale))
})
class PolyankaApplication : Application() {
    val runtime: TimerRuntime by lazy { TimerRuntime(russianContext(this)) }
}
class TimerRuntime(context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val clock=AndroidClock(context)
    val scheduler=AlarmScheduler(context,clock)
    val notifier=CompletionNotifier(context)
    val reminders=TaskReminders(context)
    private val settingsStore=SettingsStore(context)
    private val db=LocalDatabase(context)
    private val mutex=Mutex()
    private val mutableState=MutableStateFlow(Snapshot())
    private val mutableHistory=MutableStateFlow<List<Record>>(emptyList())
    private val mutableSettings=MutableStateFlow(TimerSettings())
    private val mutableNotes=MutableStateFlow<List<Note>>(emptyList())
    private val mutableFolders=MutableStateFlow<List<Folder>>(emptyList())
    private val mutableTasks=MutableStateFlow<List<PlannedTask>>(emptyList())
    val state=mutableState.asStateFlow()
    val history=mutableHistory.asStateFlow()
    val settings=mutableSettings.asStateFlow()
    val notes=mutableNotes.asStateFlow()
    val folders=mutableFolders.asStateFlow()
    val tasks=mutableTasks.asStateFlow()
    private var historyGeneration: Long?=null
    private var organizerLoaded=false
    private var allTasks=listOf<PlannedTask>()
    private val controller=TimerController(db,clock) { notifier.show(it,mutableSettings.value) }
    init { notifier.ensureChannels() }
    private suspend fun loadSettings() { mutableSettings.value=settingsStore.flow.first() }
    private fun publish(snapshot: Snapshot,force: Boolean=false) {
        mutableState.value=snapshot
        if(historyGeneration!=db.historyGeneration) {
            mutableHistory.value=controller.history();historyGeneration=db.historyGeneration
        }
        scheduler.sync(snapshot,force)
    }
    private fun loadOrganizer() {
        mutableNotes.value=db.notes();mutableFolders.value=db.folders()
        allTasks=db.tasks(includeDeleted=true);mutableTasks.value=allTasks.filterNot { it.deleted }
        organizerLoaded=true
    }
    suspend fun refresh(forceAlarm: Boolean=false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings();publish(controller.reconcile(),forceAlarm)
            val firstLoad=!organizerLoaded
            if(firstLoad) loadOrganizer()
            if(forceAlarm || firstLoad) reminders.sync(allTasks,settings.value.taskReminders,force=true)
        }
    }
    suspend fun action(block: (Snapshot,Stamp)->Change) = withContext(Dispatchers.IO) {
        mutex.withLock { loadSettings();publish(controller.dispatch(block)) }
    }
    suspend fun start() = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings()
            val before=controller.snapshot()
            val next=controller.dispatch { s,n -> TimerEngine.start(s,settings.value.duration(s.phase),n) }
            if(before.session==null && next.session!=null) notifier.clear()
            publish(next)
        }
    }
    suspend fun timerTap() = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings()
            val before=controller.snapshot()
            val next=controller.dispatch { current,n -> TimerEngine.tap(current,settings.value.duration(current.phase),n) }
            if(before.session==null && next.session!=null)notifier.clear()
            publish(next)
        }
    }
    suspend fun startOfferedBreak()=withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings()
            val previous=controller.snapshot()
            val next=controller.dispatch { state,now -> TimerPresentation.startOfferedBreak(state,
                settings.value.duration(Phase.SHORT_BREAK),settings.value.duration(Phase.LONG_BREAK),now) }
            if(next.session?.id!=previous.session?.id)notifier.clear()
            publish(next)
        }
    }
    suspend fun reset()=withContext(Dispatchers.IO) {
        mutex.withLock {
            val id=controller.snapshot().session?.id
            publish(controller.dispatch { current,_ -> TimerEngine.reset(current) },force=true)
            if(id!=null)notifier.cancelSession(id)
        }
    }
    suspend fun hideHistory(id: String?=null)=withContext(Dispatchers.IO) {
        mutex.withLock { db.hideHistory(id);publish(controller.snapshot())
            if(id!=null)notifier.cancelSession(id) else notifier.clear()
        }
    }
    suspend fun startTask(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings()
            val task=db.task(id) ?: return@withLock false
            val before=controller.snapshot()
            if(before.session?.status in listOf(Status.RUNNING,Status.PAUSED)) return@withLock false
            val next=controller.dispatch { s,n -> TimerEngine.startTask(s,task,settings.value.duration(Phase.FOCUS),n) }
            val started=next.session?.id!=before.session?.id
            if(started) notifier.clear()
            publish(next);started
        }
    }
    suspend fun alarm(id: String,revision: Int)=action { s,n -> TimerEngine.alarm(s,id,revision,n) }
    suspend fun saveSettings(update: (TimerSettings)->TimerSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings();settingsStore.save(update(settings.value));loadSettings()
            if(!organizerLoaded) loadOrganizer()
            reminders.sync(allTasks,settings.value.taskReminders,force=true)
        }
    }
    suspend fun getNote(id: String): Note?=withContext(Dispatchers.IO) { mutex.withLock { db.note(id) } }
    suspend fun createNote(type: BlockType,folder: String?): Note = withContext(Dispatchers.IO) {
        mutex.withLock {
            val note=Note(folderId=folder?.takeUnless { it=="__none" },blocks=listOf(NoteBlock(type=type)),updatedAt=clock.now().wall)
            db.createNote(note);note
        }
    }
    suspend fun saveNote(note: Note): Boolean=withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved=db.saveNote(note)
            if(saved) {
                val visible=note.copy(
                    folderId=note.folderId?.takeIf { id -> mutableFolders.value.any { it.id==id } })
                mutableNotes.value=NoteRules.sorted(mutableNotes.value.filterNot { it.id==note.id }+
                    if(!visible.draft || NoteRules.meaningful(visible)) listOf(visible) else emptyList())
            }
            saved
        }
    }
    suspend fun finishNote(id: String)=withContext(Dispatchers.IO) {
        mutex.withLock { db.finishNote(id);mutableNotes.value=db.notes() }
    }
    suspend fun deleteNote(id: String)=withContext(Dispatchers.IO) {
        mutex.withLock { db.deleteNote(id);mutableNotes.value=db.notes() }
    }
    suspend fun folder(id: String?=null,name: String)=withContext(Dispatchers.IO) {
        mutex.withLock {
            val created=id ?: UUID.randomUUID().toString()
            db.saveFolder(created,name);mutableFolders.value=db.folders();created
        }
    }
    suspend fun deleteFolder(id: String)=withContext(Dispatchers.IO) {
        mutex.withLock { db.deleteFolder(id);mutableFolders.value=db.folders();mutableNotes.value=db.notes() }
    }
    suspend fun getTask(id: String): PlannedTask?=withContext(Dispatchers.IO) { mutex.withLock { db.task(id) } }
    suspend fun saveTask(task: PlannedTask,validate: Boolean=true,reminderEdited: Boolean=false): PlannedTask = withContext(Dispatchers.IO) {
        mutex.withLock {
            if(validate) require(PlannerRules.validate(task,Instant.ofEpochMilli(clock.now().wall),ZoneId.systemDefault(),if(reminderEdited)null else db.task(task.id))==null)
            val saved=db.saveTask(task)
            reminders.cancel(task.id)
            loadOrganizer()
            reminders.sync(allTasks,settings.value.taskReminders)
            saved
        }
    }
    suspend fun deliverReminder(id: String,revision: Long)=withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSettings()
            val claimed=db.claimReminder(id,revision,Instant.ofEpochMilli(clock.now().wall),ZoneId.systemDefault(),settings.value.taskReminders)
            if(claimed!=null) reminders.show(claimed)
            loadOrganizer();reminders.sync(allTasks,settings.value.taskReminders,force=true)
        }
    }

    suspend fun exportTransfer(): ByteArray=withContext(Dispatchers.IO) { mutex.withLock {
        loadSettings()
        val at=clock.now();val current=controller.dispatch { state,_ -> TimerEngine.reconcile(state,at) };publish(current)
        // Only the copy is paused. The live timer and its alarm continue normally.
        val pausedCopy=TimerEngine.pause(current,at).snapshot
        val prefs=settings.value
        val json=org.json.JSONObject().put("applicationId","com.munkberries.tikhayapolyanka")
            .put("format",1).put("databaseVersion",4).put("createdAt",clock.now().wall)
            .put("tables",db.exportTables(pausedCopy)).put("settings",org.json.JSONObject()
                .put("focus",prefs.focus).put("shortBreak",prefs.shortBreak).put("longBreak",prefs.longBreak)
                .put("sound",prefs.sound).put("vibration",prefs.vibration).put("notifications",prefs.notifications)
                .put("taskReminders",prefs.taskReminders))
        TransferFile.encode(json.toString())
    } }
    private fun transferSettings(json: org.json.JSONObject,current: TimerSettings): TimerSettings {
        require(json.getString("applicationId")=="com.munkberries.tikhayapolyanka" && json.getInt("format")==1 && json.getInt("databaseVersion")==4)
        val p=json.getJSONObject("settings")
        val focus=p.getInt("focus");val short=p.getInt("shortBreak");val long=p.getInt("longBreak")
        require(focus in 1..120 && short in 1..30 && long in 1..60)
        return TimerSettings(focus,short,long,p.getBoolean("sound"),p.getBoolean("vibration"),p.getBoolean("notifications"),
            current.permissionAsked,p.getBoolean("taskReminders"))
    }
    suspend fun inspectTransfer(file: ByteArray): org.json.JSONObject=withContext(Dispatchers.IO) { mutex.withLock {
        check(db.pristineForTransfer()) { "Transfer target is not empty" }
        val json=org.json.JSONObject(TransferFile.decode(file));transferSettings(json,settings.value)
        db.validateTransferTables(json.getJSONArray("tables"));json
    } }
    suspend fun importTransfer(json: org.json.JSONObject)=withContext(Dispatchers.IO) { mutex.withLock {
        loadSettings();val previous=settings.value;val incoming=transferSettings(json,previous)
        try {
            db.restoreTransferTables(json.getJSONArray("tables")) { runBlocking { settingsStore.save(incoming) } }
        }catch(e: Exception) {
            try {settingsStore.save(previous)}catch(restore: Exception){e.addSuppressed(restore)}
            throw e
        }
        loadSettings();loadOrganizer();publish(controller.snapshot(),force=true)
        reminders.sync(allTasks,settings.value.taskReminders,force=true)
    } }
}



