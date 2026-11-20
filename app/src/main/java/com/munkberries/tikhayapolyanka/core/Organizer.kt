package com.munkberries.tikhayapolyanka.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object RussianDates {
    val locale: Locale = Locale.forLanguageTag("ru-RU")
    fun month(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy",locale))
        .replaceFirstChar { it.titlecase(locale) }
    fun day(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE",locale))
    fun full(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy",locale))
    fun history(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm",locale))
    fun time(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm",locale))
    fun reminder(value: LocalDateTime): String = value.format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm",locale))
    fun days(month: YearMonth): List<LocalDate?> {
        val offset=month.atDay(1).dayOfWeek.value-1
        return List(((offset+month.lengthOfMonth()+6)/7)*7) { index ->
            (index-offset+1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
        }
    }
}

enum class BlockType { PARAGRAPH, HEADING, BULLET, NUMBERED, CHECK }
data class BoldRange(val start: Int,val end: Int)
enum class TextColor { DARK, BROWN, TERRACOTTA, GREEN, BLUE, PLUM }
data class ColorRange(val start: Int,val end: Int,val color: TextColor)
data class NoteBlock(
    val id: String=UUID.randomUUID().toString(), val type: BlockType=BlockType.PARAGRAPH,
    val text: String="", val checked: Boolean=false, val bold: Boolean=false,
    val marks: List<BoldRange> = emptyList(),
    val color: TextColor=TextColor.DARK, val colors: List<ColorRange> = emptyList(),
)
// Legacy text-color fields remain readable; the visible tool now changes only this note property.
enum class NoteBackground(val argb: Long) {
    CREAM(0xFFFFF9EF), SAND(0xFFF3E4CC), SAGE(0xFFE5EBDD),
    LAVENDER(0xFFEDE7F5), POWDER(0xFFF5E3E7), PEACH(0xFFFFE8DA);
    companion object {
        fun stored(value: String?): NoteBackground = entries.find { it.name==value } ?: CREAM
    }
}
data class Folder(val id: String,val name: String)
data class Note(
    val id: String=UUID.randomUUID().toString(), val title: String="", val folderId: String?=null,
    val blocks: List<NoteBlock> = listOf(NoteBlock()), val pinned: Boolean=false,
    val updatedAt: Long=0, val revision: Long=0, val draft: Boolean=true,
    val background: NoteBackground=NoteBackground.CREAM,
)
object NoteRules {
    fun normalized(text: String): String = text.lowercase(RussianDates.locale)
    fun meaningful(note: Note): Boolean = note.title.isNotBlank() ||
        note.blocks.any { it.text.isNotBlank() || it.type==BlockType.CHECK && it.checked }
    fun searchText(note: Note): String = normalized(note.title+"\n"+note.blocks.joinToString("\n") { it.text })
    fun matches(note: Note,query: String,folder: String?): Boolean =
        (folder==null || if(folder=="__none") note.folderId==null else note.folderId==folder) &&
            searchText(note).contains(normalized(query.trim()))
    fun sorted(notes: List<Note>): List<Note> = notes.sortedWith(
        compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }.thenBy { it.id })
    fun numberAt(blocks: List<NoteBlock>,index: Int): Int {
        var i=index
        while(i>0 && blocks[i-1].type==BlockType.NUMBERED) i--
        return index-i+1
    }
    fun replaceText(block: NoteBlock,text: String): NoteBlock {
        val old=block.text
        var prefix=0
        while(prefix<minOf(old.length,text.length) && old[prefix]==text[prefix]) prefix++
        var suffix=0
        while(suffix<minOf(old.length-prefix,text.length-prefix) &&
            old[old.length-1-suffix]==text[text.length-1-suffix]) suffix++
        val oldEnd=old.length-suffix
        val newEnd=text.length-suffix
        val delta=text.length-old.length
        fun map(position: Int,start: Boolean): Int = when {
            position<=prefix -> position
            position>=oldEnd -> position+delta
            else -> if(start) prefix else newEnd
        }.coerceIn(0,text.length)
        return block.copy(text=text,marks=block.marks.mapNotNull {
            val a=map(it.start,true);val b=map(it.end,false)
            if(a<b) BoldRange(a,b) else null
        },colors=block.colors.mapNotNull {
            val a=map(it.start,true);val b=map(it.end,false)
            if(a<b) ColorRange(a,b,it.color) else null
        })
    }
    fun toggleBold(block: NoteBlock,start: Int,end: Int): NoteBlock {
        val from=minOf(start,end).coerceIn(0,block.text.length)
        val to=maxOf(start,end).coerceIn(from,block.text.length)
        if(from==to) return block.copy(bold=!block.bold)
        val covered=block.bold || (from until to).all { p -> block.marks.any { p>=it.start && p<it.end } }
        val existing=if(block.bold) listOf(BoldRange(0,block.text.length)) else block.marks
        val ranges=if(covered) existing.flatMap {
            if(it.end<=from || it.start>=to) listOf(it)
            else listOfNotNull(if(it.start<from) BoldRange(it.start,from) else null,
                if(it.end>to) BoldRange(to,it.end) else null)
        } else existing+BoldRange(from,to)
        return block.copy(bold=false,marks=ranges.sortedBy { it.start })
    }
    fun split(block: NoteBlock,text: String): List<NoteBlock> {
        val changed=replaceText(block,text)
        var offset=0
        return text.split('\n').mapIndexed { index,part ->
            val start=offset;offset+=part.length+1
            changed.copy(id=if(index==0) block.id else UUID.randomUUID().toString(),text=part,
                checked=if(index==0) block.checked else false,
                marks=changed.marks.mapNotNull {
                    val a=maxOf(it.start,start)-start;val b=minOf(it.end,start+part.length)-start
                    if(a<b) BoldRange(a,b) else null
                },colors=changed.colors.mapNotNull {
                    val a=maxOf(it.start,start)-start;val b=minOf(it.end,start+part.length)-start
                    if(a<b) ColorRange(a,b,it.color) else null
                })
        }
    }
}

enum class TaskKind { TODO, EVENT, DEADLINE }
data class PlannedTask(
    val id: String=UUID.randomUUID().toString(), val kind: TaskKind=TaskKind.TODO,
    val title: String="", val date: LocalDate=LocalDate.now(), val time: LocalTime?=null,
    val description: String="", val completed: Boolean=false, val reminder: LocalDateTime?=null,
    val revision: Long=0, val remindedRevision: Long=-1, val deleted: Boolean=false,
)
enum class TaskProblem { TITLE_REQUIRED, EVENT_TIME_REQUIRED, REMINDER_IN_PAST }
object PlannerRules {
    // java.time moves gaps forward by their duration; overlaps use the earlier offset.
    fun instant(local: LocalDateTime,zone: ZoneId): Instant = local.atZone(zone).toInstant()
    fun validate(task: PlannedTask,now: Instant,zone: ZoneId,previous: PlannedTask?=null): TaskProblem? = when {
        task.title.isBlank() -> TaskProblem.TITLE_REQUIRED
        task.kind==TaskKind.EVENT && task.time==null -> TaskProblem.EVENT_TIME_REQUIRED
        task.reminder!=null && (previous==null || task.reminder!=previous.reminder) && !instant(task.reminder,zone).isAfter(now) -> TaskProblem.REMINDER_IN_PAST
        else -> null
    }
    fun move(task: PlannedTask,date: LocalDate): PlannedTask {
        val days=java.time.temporal.ChronoUnit.DAYS.between(task.date,date)
        return task.copy(date=date,reminder=task.reminder?.plusDays(days))
    }
    fun forDay(tasks: List<PlannedTask>,date: LocalDate): List<PlannedTask> = tasks.filter {
        !it.deleted && it.date==date
    }.sortedWith(compareBy<PlannedTask> { it.completed }.thenBy { it.time==null }
        .thenBy { it.time ?: LocalTime.MAX }.thenBy { it.id })
    fun scheduled(task: PlannedTask,now: Instant,zone: ZoneId,enabled: Boolean=true): Instant? =
        task.reminder?.let { instant(it,zone) }?.takeIf {
            enabled && !task.completed && !task.deleted && task.remindedRevision!=task.revision && it.isAfter(now)
        }
    fun canDeliver(task: PlannedTask,revision: Long,now: Instant,zone: ZoneId,enabled: Boolean=true): Boolean {
        val at=task.reminder?.let { instant(it,zone) } ?: return false
        return enabled && !task.deleted && !task.completed && task.revision==revision &&
            task.remindedRevision!=revision && !now.isBefore(at) && now<=at.plusSeconds(3600)
    }
}

enum class CycleCaptionKind { CURRENT, FINISHED, INTERRUPTED, REST, MANUAL_REST }
data class CycleCaption(val kind: CycleCaptionKind,val number: Int)
fun cycleCaption(snapshot: Snapshot): CycleCaption {
    val s=snapshot.session
    val phase=s?.phase ?: snapshot.phase
    return when {
        phase!=Phase.FOCUS && snapshot.cycle==0 -> CycleCaption(CycleCaptionKind.MANUAL_REST,0)
        phase!=Phase.FOCUS -> CycleCaption(CycleCaptionKind.REST,snapshot.cycle)
        s?.status==Status.FINISHED && s.completed -> CycleCaption(CycleCaptionKind.FINISHED,snapshot.cycle)
        s?.status==Status.FINISHED -> CycleCaption(CycleCaptionKind.INTERRUPTED,(snapshot.cycle+1).coerceAtMost(4))
        else -> CycleCaption(CycleCaptionKind.CURRENT,(snapshot.cycle+1).coerceAtMost(4))
    }
}


