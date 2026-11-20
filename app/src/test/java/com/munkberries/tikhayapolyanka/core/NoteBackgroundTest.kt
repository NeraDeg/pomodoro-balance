package com.munkberries.tikhayapolyanka.core

import org.junit.Assert.*
import org.junit.Test

class NoteBackgroundTest {
    @Test fun old_or_unknown_values_default_to_original_cream() {
        assertEquals(NoteBackground.CREAM,Note().background)
        for(raw in listOf(null,"","unknown"))assertEquals(NoteBackground.CREAM,NoteBackground.stored(raw))
        NoteBackground.entries.forEach{assertEquals(it,NoteBackground.stored(it.name))}
    }
    @Test fun background_does_not_change_blocks_identity_folder_or_formatting() {
        val original=Note(id="same",title="Русский текст",folderId="study",blocks=listOf(
            NoteBlock(id="b",text="Последний символ я",type=BlockType.CHECK,checked=true,bold=true,
                marks=listOf(BoldRange(0,2)),colors=listOf(ColorRange(1,4,TextColor.BLUE)))))
        for(color in NoteBackground.entries) {
            val next=original.copy(background=color)
            assertEquals(original,next.copy(background=original.background))
            assertEquals(color,next.copy(folderId=null,title="Другая папка").background)
        }
    }
    @Test fun enter_delete_and_undo_keep_whole_note_background() {
        for(color in NoteBackground.entries) {
            val original=Note(background=color,blocks=listOf(NoteBlock(id="b",text="абв")))
            val changed=DocumentRules.edit(original,"b","а\nбв",2,2)
            assertEquals(color,changed.note.background)
            val (removed,undo)=DocumentRules.remove(changed.note,changed.focus)
            assertEquals(color,removed.background)
            assertEquals(color,DocumentRules.undo(removed,undo).background)
        }
    }
    @Test fun all_six_surfaces_contrast_with_text_cursor_and_checkbox() {
        fun luminance(argb: Long): Double {
            fun channel(shift: Int): Double {
                val c=((argb shr shift) and 255)/255.0
                return if(c<=0.04045)c/12.92 else Math.pow((c+0.055)/1.055,2.4)
            }
            return .2126*channel(16)+.7152*channel(8)+.0722*channel(0)
        }
        assertEquals(6,NoteBackground.entries.map{it.argb}.distinct().size)
        for(background in NoteBackground.entries)for(foreground in listOf(0xFF3D291D,0xFF6B5C4B,0xFF526448)) {
            val ratio=(luminance(background.argb)+.05)/(luminance(foreground)+.05)
            assertTrue("$background foreground $foreground contrast $ratio",ratio>=4.5)
        }
    }
}
