package com.munkberries.tikhayapolyanka.core

data class DocumentEdit(val note: Note,val focus: String,val start: Int,val end: Int)
data class RemovedBlock(val noteId: String,val block: NoteBlock,val index: Int,val placeholder: String?=null)
data class SaveProgress(val saved: Long=-1,val failed: Long?=null) {
    fun success(revision: Long)=copy(saved=maxOf(saved,revision),failed=failed?.takeIf{it>revision})
    fun failure(revision: Long,force: Boolean=false)=if(!force && revision<=saved)this else copy(failed=maxOf(failed ?: -1,revision))
    fun current(revision: Long)=saved>=revision && failed==null
}
class TapGate {
    private var last: Stamp?=null
    fun accept(now: Stamp): Boolean {
        val old=last
        if(old!=null && old.boot==now.boot && now.elapsed-old.elapsed in 0..349)return false
        last=now;return true
    }
}
object DocumentRules {
    fun edit(note: Note,id: String,text: String,start: Int,end: Int): DocumentEdit {
        val index=note.blocks.indexOfFirst{it.id==id}
        require(index>=0)
        val block=note.blocks[index]
        val list=block.type in listOf(BlockType.BULLET,BlockType.NUMBERED,BlockType.CHECK)
        if(list && block.text.isEmpty() && text=="\n") {
            val changed=block.copy(type=BlockType.PARAGRAPH,checked=false)
            return DocumentEdit(note.copy(blocks=note.blocks.map{if(it.id==id)changed else it}),id,0,0)
        }
        val split=NoteRules.split(block,text).mapIndexed { i,b ->
            if(i>0 && block.type==BlockType.HEADING)b.copy(type=BlockType.PARAGRAPH) else b
        }
        fun locate(position: Int): Pair<Int,Int> {
            val p=position.coerceIn(0,text.length)
            val line=text.take(p).count{it=='\n'}
            val offset=text.lastIndexOf('\n',p-1).let{if(p==0)-1 else it}+1
            return line.coerceAtMost(split.lastIndex) to (p-offset).coerceAtLeast(0)
        }
        val a=locate(start);val b=locate(end);val selected=split[b.first]
        return DocumentEdit(note.copy(blocks=note.blocks.take(index)+split+note.blocks.drop(index+1)),selected.id,
            if(a.first==b.first)a.second else b.second,b.second)
    }
    fun colorAt(block: NoteBlock,position: Int): TextColor =
        block.colors.lastOrNull{position>=it.start && position<it.end}?.color ?: block.color
    fun currentColor(block: NoteBlock,start: Int,end: Int): TextColor? {
        val a=minOf(start,end).coerceIn(0,block.text.length);val b=maxOf(start,end).coerceIn(a,block.text.length)
        if(a==b)return block.color
        val colors=(a until b).map{colorAt(block,it)}.distinct()
        return colors.singleOrNull()
    }
    fun color(block: NoteBlock,start: Int,end: Int,color: TextColor): NoteBlock {
        val a=minOf(start,end).coerceIn(0,block.text.length);val b=maxOf(start,end).coerceIn(a,block.text.length)
        if(a==b)return block.copy(color=color,colors=emptyList())
        val retained=block.colors.flatMap {
            if(it.end<=a||it.start>=b)listOf(it)
            else listOfNotNull(if(it.start<a)it.copy(end=a) else null,if(it.end>b)it.copy(start=b) else null)
        }
        return block.copy(colors=retained+ColorRange(a,b,color))
    }
    fun remove(note: Note,id: String): Pair<Note,RemovedBlock> {
        val index=note.blocks.indexOfFirst{it.id==id};require(index>=0)
        val blocks=note.blocks.filterNot{it.id==id}.toMutableList()
        val placeholder=if(blocks.isEmpty())NoteBlock().also{blocks+=it}.id else null
        return note.copy(blocks=blocks) to RemovedBlock(note.id,note.blocks[index],index,placeholder)
    }
    fun undo(note: Note,removed: RemovedBlock): Note {
        if(note.id!=removed.noteId || note.blocks.any{it.id==removed.block.id})return note
        val blocks=note.blocks.filterNot{it.id==removed.placeholder && it.text.isEmpty() && !it.checked}.toMutableList()
        blocks.add(removed.index.coerceIn(0,blocks.size),removed.block)
        return note.copy(blocks=blocks)
    }
}
