package com.munkberries.tikhayapolyanka.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.munkberries.tikhayapolyanka.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON payloads preserve interval precision; DB unique ID protects history. */
object Codec {
    fun session(s: Session): JSONObject = JSONObject().apply {
        put("id", s.id); put("phase", s.phase.name); put("status", s.status.name)
        put("duration", s.duration); put("remaining", s.remaining); put("revision", s.revision)
        put("taskId", s.taskId); put("title", s.title); put("completed", s.completed); put("endedAt", s.endedAt)
        s.anchor?.let { put("anchor", JSONObject().put("wall", it.wall).put("elapsed", it.elapsed).put("boot", it.boot)) }
        put("intervals", JSONArray().apply { s.intervals.forEach { put(JSONArray().put(it.start).put(it.end)) } })
    }
    fun session(j: JSONObject): Session {
        val a = j.optJSONObject("anchor")
        val intervals = j.getJSONArray("intervals")
        return Session(j.getString("id"), Phase.valueOf(j.getString("phase")), Status.valueOf(j.getString("status")),
            j.getLong("duration"), j.getLong("remaining"),
            a?.let { Stamp(it.getLong("wall"), it.getLong("elapsed"), it.getInt("boot")) },
            j.getInt("revision"), (0 until intervals.length()).map {
                val item = intervals.getJSONArray(it); Interval(item.getLong(0), item.getLong(1))
            }, j.optString("title"), j.getBoolean("completed"),
            if (j.has("endedAt") && !j.isNull("endedAt")) j.getLong("endedAt") else null, j.nullable("taskId"))
    }
    fun encode(s: Snapshot): String = JSONObject().apply {
        put("selectedTaskId",s.selectedTaskId); put("schema", 1); put("cycle", s.cycle); put("draft", s.draft); put("phase", s.phase.name)
        put("recoveryNote", s.recoveryNote); s.session?.let { put("session", session(it)) }
    }.toString()
    fun decode(raw: String): Snapshot = JSONObject(raw).let {
        check(it.getInt("schema") == 1) { "Unsupported timer schema" }
        Snapshot(it.optJSONObject("session")?.let(::session), it.getInt("cycle"), it.optString("draft"),
            Phase.valueOf(it.getString("phase")), it.optString("recoveryNote").takeUnless { v -> v.isBlank() || v == "null" }, it.nullable("selectedTaskId"))
    }
}

class LocalDatabase(private val context: Context, name: String = "polyanka.db") : SQLiteOpenHelper(context, name, null, 4), TimerStore {
    var historyGeneration: Long = 0; private set
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE timer_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, ended_at INTEGER NOT NULL, payload TEXT NOT NULL, signal_claimed INTEGER NOT NULL)")
        addOrganizerSchema(db)
        addFixesSchema(db)
        addBackgroundSchema(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..3 && newVersion==4) { "Explicit migration required" }
        if(oldVersion<2) addOrganizerSchema(db)
        if(oldVersion<3) addFixesSchema(db)
        addBackgroundSchema(db)
    }
    override fun read(): Snapshot = readableDatabase.rawQuery("SELECT payload FROM timer_state WHERE id=1", null).use {
        if (it.moveToFirst()) Codec.decode(it.getString(0)) else Snapshot()
    }
    override fun commit(change: Change): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var inserted = false
            change.record?.let { record ->
                val values = ContentValues().apply {
                    put("id", record.session.id); put("ended_at", record.session.endedAt)
                    put("payload", Codec.session(record.session).toString())
                    // Claim before dispatch: receiver/UI cannot both produce a signal.
                    put("signal_claimed", 1)
                }
                inserted = db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            }
            db.insertWithOnConflict("timer_state", null, ContentValues().apply {
                put("id", 1); put("payload", Codec.encode(change.snapshot))
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            if (inserted) historyGeneration++
            return inserted
        } finally { db.endTransaction() }
    }
    override fun history(): List<Record> = readableDatabase.rawQuery(
        "SELECT payload FROM history WHERE hidden=0 ORDER BY ended_at DESC", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Record(Codec.session(JSONObject(cursor.getString(0))))) }
    }

    fun hideHistory(id: String?=null) {
        val changed=writableDatabase.update("history",ContentValues().apply { put("hidden",1);put("payload","{}");put("ended_at",0) },
            if(id==null)"hidden=0" else "id=? AND hidden=0",id?.let { arrayOf(it) })
        if(changed>0) historyGeneration++
    }
    private fun addBackgroundSchema(db: SQLiteDatabase) {
        context.assets.open("db/migration_3_4.sql").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { db.execSQL(it.trim().removeSuffix(";")) }
        }
    }
    private fun addFixesSchema(db: SQLiteDatabase) {
        context.assets.open("db/migration_2_3.sql").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { db.execSQL(it.trim().removeSuffix(";")) }
        }
    }
    private fun addOrganizerSchema(db: SQLiteDatabase) {
        context.assets.open("db/migration_1_2.sql").bufferedReader().useLines { lines ->
            lines.map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() }.forEach {
                db.execSQL(it.removeSuffix(";"))
            }
        }
    }
    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db=writableDatabase
        db.beginTransaction()
        try { val result=block(db);db.setTransactionSuccessful();return result }
        finally { db.endTransaction() }
    }
    fun folders(): List<Folder> = readableDatabase.rawQuery("SELECT id,name FROM folders ORDER BY rowid",null).use {
        buildList { while(it.moveToNext()) add(Folder(it.getString(0),it.getString(1))) }
    }
    fun saveFolder(id: String,name: String) {
        val cleaned=name.trim()
        require(cleaned.isNotEmpty())
        val values=ContentValues().apply { put("name",cleaned);put("normalized_name",NoteRules.normalized(cleaned)) }
        if(writableDatabase.update("folders",values,"id=?",arrayOf(id))==0) {
            values.put("id",id);writableDatabase.insertOrThrow("folders",null,values)
        }
    }
    fun deleteFolder(id: String) = transaction { db ->
        db.execSQL("UPDATE notes SET folder_id=NULL,revision=revision+1 WHERE folder_id=?",arrayOf(id))
        db.delete("folders","id=?",arrayOf(id));Unit
    }
    fun createNote(note: Note) = transaction { db ->
        db.insertOrThrow("notes",null,noteValues(note).apply { put("id",note.id);put("deleted",0) })
        writeBlocks(db,note);Unit
    }
    private fun noteValues(note: Note) = ContentValues().apply {
        put("title",note.title);put("folder_id",note.folderId?.takeIf { id -> folders().any { it.id==id } })
        put("pinned",if(note.pinned)1 else 0);put("updated_at",note.updatedAt);put("revision",note.revision)
        put("is_draft",if(note.draft)1 else 0);put("has_content",if(NoteRules.meaningful(note))1 else 0)
        put("search_text",NoteRules.searchText(note));put("background_color",note.background.name)
    }
    private fun writeBlocks(db: SQLiteDatabase,note: Note) {
        db.delete("note_blocks","note_id=?",arrayOf(note.id))
        note.blocks.forEachIndexed { index,b ->
            db.insertOrThrow("note_blocks",null,ContentValues().apply {
                put("id",b.id);put("note_id",note.id);put("position",index);put("kind",b.type.name)
                put("text",b.text);put("checked",if(b.checked)1 else 0);put("bold",if(b.bold)1 else 0)
                put("marks_json",OrganizerCodec.marks(b.marks));put("text_color",b.color.name);put("colors_json",OrganizerCodec.colors(b.colors))
            })
        }
    }
    /** UPDATE only, revision guard, tombstone: a delayed save can never resurrect deletion. */
    fun saveNote(note: Note): Boolean = transaction { db ->
        val values=noteValues(note).apply { remove("is_draft") }
        val count=db.update("notes",values,"id=? AND deleted=0 AND revision<?",
            arrayOf(note.id,note.revision.toString()))
        if(count==1) writeBlocks(db,note)
        count==1
    }
    fun note(id: String): Note? = readableDatabase.rawQuery("SELECT * FROM notes WHERE id=? AND deleted=0",arrayOf(id)).use { c ->
        if(!c.moveToFirst()) null else readNote(c)
    }
    private fun readNote(c: android.database.Cursor): Note {
        fun string(key: String)=c.getString(c.getColumnIndexOrThrow(key))
        fun long(key: String)=c.getLong(c.getColumnIndexOrThrow(key))
        val id=string("id")
        val blocks=readableDatabase.rawQuery("SELECT * FROM note_blocks WHERE note_id=? ORDER BY position",arrayOf(id)).use { b ->
            buildList {
                while(b.moveToNext()) {
                    fun text(key: String)=b.getString(b.getColumnIndexOrThrow(key))
                    fun yes(key: String)=b.getInt(b.getColumnIndexOrThrow(key))==1
                    add(NoteBlock(text("id"),BlockType.valueOf(text("kind")),text("text"),yes("checked"),yes("bold"),
                        OrganizerCodec.marks(text("marks_json")),TextColor.valueOf(text("text_color")),OrganizerCodec.colors(text("colors_json"))))
                }
            }
        }
        return Note(id,string("title"),string("folder_id"),blocks, long("pinned")==1L,
            long("updated_at"),long("revision"),long("is_draft")==1L,NoteBackground.stored(string("background_color")))
    }
    fun notes(query: String="",folder: String?=null): List<Note> {
        val args=mutableListOf(NoteRules.normalized(query.trim()))
        val filter=when(folder) {
            null -> ""
            "__none" -> " AND folder_id IS NULL"
            else -> { args+=folder;" AND folder_id=?" }
        }
        return readableDatabase.rawQuery(
            "SELECT * FROM notes WHERE deleted=0 AND (is_draft=0 OR has_content=1) AND instr(search_text,?)>0"+filter+
                " ORDER BY pinned DESC,updated_at DESC,id ASC",args.toTypedArray()).use { c ->
            buildList { while(c.moveToNext()) add(readNote(c)) }
        }
    }
    fun deleteNote(id: String) = transaction { db ->
        db.execSQL("UPDATE notes SET deleted=1,revision=revision+1 WHERE id=?",arrayOf(id))
        db.delete("note_blocks","note_id=?",arrayOf(id));Unit
    }
    fun finishNote(id: String) {
        val note=note(id) ?: return
        if(note.draft && !NoteRules.meaningful(note)) deleteNote(id)
        else if(note.draft) writableDatabase.update("notes",ContentValues().apply { put("is_draft",0) },
            "id=? AND deleted=0",arrayOf(id))
    }
    fun tasks(includeDeleted: Boolean=false): List<PlannedTask> = readableDatabase.rawQuery(
        "SELECT payload,reminded_revision FROM tasks"+if(includeDeleted)"" else " WHERE deleted=0",null).use { c ->
        buildList { while(c.moveToNext()) add(OrganizerCodec.task(c.getString(0)).copy(remindedRevision=c.getLong(1))) }
    }
    fun task(id: String): PlannedTask? = readableDatabase.rawQuery(
        "SELECT payload,reminded_revision FROM tasks WHERE id=? AND deleted=0",arrayOf(id)).use {
        if(it.moveToFirst()) OrganizerCodec.task(it.getString(0)).copy(remindedRevision=it.getLong(1)) else null
    }
    fun saveTask(task: PlannedTask): PlannedTask = transaction { db ->
        val updated=task.copy(title=task.title.trim(),revision=task.revision+1)
        val values=ContentValues().apply {
            put("date",updated.date.toString());put("payload",OrganizerCodec.task(updated))
            put("revision",updated.revision);put("completed",if(updated.completed)1 else 0)
            put("deleted",if(updated.deleted)1 else 0);put("reminder_local",updated.reminder?.toString())
            put("reminded_revision",updated.remindedRevision)
        }
        if(task.revision==0L) {
            values.put("id",updated.id);db.insertOrThrow("tasks",null,values)
        } else check(db.update("tasks",values,"id=? AND revision=? AND deleted=0",
            arrayOf(task.id,task.revision.toString()))==1) { "Task was changed or deleted" }
        updated
    }
    /** Claim before notifying, in a write transaction. Repeated delivery cannot claim twice. */
    fun claimReminder(id: String,revision: Long,now: java.time.Instant,zone: java.time.ZoneId,enabled: Boolean): PlannedTask? =
        transaction { db ->
            val task=task(id) ?: return@transaction null
            if(!PlannerRules.canDeliver(task,revision,now,zone,enabled)) return@transaction null
            val count=db.update("tasks",ContentValues().apply { put("reminded_revision",revision) },
                "id=? AND revision=? AND reminded_revision<>? AND completed=0 AND deleted=0",
                arrayOf(id,revision.toString(),revision.toString()))
            if(count==1) task.copy(remindedRevision=revision) else null
        }

    private val transferTables=listOf("folders","notes","note_blocks","tasks","history","timer_state")
    fun pristineForTransfer(): Boolean {
        if(transferTables.filterNot{it=="folders"||it=="timer_state"}.any { table ->
            readableDatabase.rawQuery("SELECT 1 FROM $table LIMIT 1",null).use{it.moveToFirst()} })return false
        val defaults=mapOf("study" to "Учёба","personal" to "Личное","ideas" to "Идеи")
        val snapshot=read()
        return folders().associate{it.id to it.name}==defaults && snapshot==Snapshot()
    }
    fun exportTables(timerCopy: Snapshot): JSONArray = JSONArray().apply {
        transferTables.forEach { table ->
            readableDatabase.rawQuery("SELECT * FROM $table",null).use { cursor ->
                val columns=cursor.columnNames.toList();val rows=JSONArray()
                if(table=="timer_state")rows.put(JSONArray().put(1).put(Codec.encode(timerCopy)))
                else while(cursor.moveToNext())rows.put(JSONArray().apply { columns.indices.forEach { i ->
                    put(when(cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL->JSONObject.NULL
                        android.database.Cursor.FIELD_TYPE_INTEGER->cursor.getLong(i)
                        android.database.Cursor.FIELD_TYPE_STRING->cursor.getString(i)
                        else->error("Unsupported backup value")
                    })
                } })
                put(JSONObject().put("name",table).put("columns",JSONArray(columns)).put("rows",rows))
            }
        }
    }
    fun validateTransferTables(tables: JSONArray) {
        require(tables.length()==transferTables.size)
        val names=(0 until tables.length()).map{tables.getJSONObject(it).getString("name")}
        require(names.toSet()==transferTables.toSet())
        for(i in 0 until tables.length()) {
            val table=tables.getJSONObject(i);val name=table.getString("name")
            val schema=readableDatabase.rawQuery("PRAGMA table_info($name)",null).use { c ->
                buildList {while(c.moveToNext())add(Triple(c.getString(1),c.getString(2),c.getInt(5)>0))} }
            val columns=table.getJSONArray("columns");require(columns.length()==schema.size)
            schema.forEachIndexed{index,column->require(columns.getString(index)==column.first)}
            val rows=table.getJSONArray("rows");require(rows.length()<=50_000)
            for(r in 0 until rows.length()) {
                val row=rows.getJSONArray(r);require(row.length()==schema.size)
                schema.forEachIndexed { index,column ->
                    val value=row.get(index)
                    if(value==JSONObject.NULL)require(!column.third)
                    else if(column.second=="INTEGER")require(value is Int || value is Long)
                    else require(value is String)
                }
            }
        }
    }
    /** Fresh installs only. Schema, JSON, foreign keys and settings must succeed before committing. */
    fun restoreTransferTables(tables: JSONArray,writeSettings: ()->Unit) {
        transaction { db ->
            check(pristineForTransfer()) { "Transfer target is not empty" }
            validateTransferTables(tables)
            val byName=(0 until tables.length()).associate{tables.getJSONObject(it).getString("name") to tables.getJSONObject(it)}
            transferTables.reversed().forEach{db.delete(it,null,null)}
            transferTables.forEach { name ->
                val table=byName.getValue(name);val columns=table.getJSONArray("columns");val rows=table.getJSONArray("rows")
                for(r in 0 until rows.length()) {
                    val row=rows.getJSONArray(r);val values=ContentValues()
                    for(c in 0 until columns.length()) {
                        val key=columns.getString(c);val value=row.get(c)
                        when(value) {JSONObject.NULL->values.putNull(key);is Int->values.put(key,value);is Long->values.put(key,value);is String->values.put(key,value);else->error("Invalid value")}
                    }
                    db.insertOrThrow(name,null,values)
                }
            }
            check(!db.rawQuery("PRAGMA foreign_key_check",null).use{it.moveToFirst()})
            db.rawQuery("SELECT * FROM notes",null).use { c -> while(c.moveToNext())readNote(c) }
            tasks(includeDeleted=true);history()
            val state=read();require(state.cycle in 0..4)
            state.session?.let{require(it.status!=Status.RUNNING && it.duration>0 && it.remaining in 0..it.duration)}
            writeSettings()
        }
        historyGeneration++
    }
}

