package com.munkberries.tikhayapolyanka.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NoteFlushTest {
    @Test fun failed_write_keeps_editor_open_and_successful_retry_finishes_latest_revision()=runBlocking {
        var stored=Note(id="note",revision=1)
        val latest=stored.copy(title="Последний ввод",revision=2)
        var finished=false
        try {
            flushNote(latest,{stored},{throw IllegalStateException("I/O")},{finished=true})
            fail()
        }catch(_:IllegalStateException){}
        assertFalse(finished);assertEquals(1L,stored.revision)
        flushNote(latest,{stored},{stored=it;true},{finished=true})
        assertTrue(finished);assertEquals(latest,stored)
    }
    @Test fun already_persisted_revision_is_not_overwritten_and_cleanup_runs_after_verification()=runBlocking {
        val note=Note(id="note",revision=4)
        var writes=0;var finished=false
        flushNote(note,{note},{writes++;true},{finished=true})
        assertEquals(0,writes);assertTrue(finished)
    }
}
