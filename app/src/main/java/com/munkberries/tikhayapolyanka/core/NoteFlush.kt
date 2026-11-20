package com.munkberries.tikhayapolyanka.core

/** A failed autosave must be retried before leaving, never hidden by a successful close. */
suspend fun flushNote(
    latest: Note,read: suspend (String)->Note?,write: suspend (Note)->Boolean,finish: suspend (String)->Unit,
) {
    val stored=read(latest.id) ?: throw IllegalStateException("Note deleted")
    check(stored.revision<=latest.revision) { "Note changed externally" }
    if(stored.revision<latest.revision) check(write(latest)) { "Note could not be saved" }
    finish(latest.id)
}
