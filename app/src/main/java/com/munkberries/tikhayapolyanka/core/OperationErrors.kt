package com.munkberries.tikhayapolyanka.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** Cancellation is control flow, while a successful retry clears only its own error. */
class OperationErrors {
    private val failures=linkedMapOf<String,Int>()
    val message=MutableStateFlow<Int?>(null)
    suspend fun run(key: String,error: Int,block: suspend () -> Unit): Boolean {
        return try {
            block()
            synchronized(failures) { failures.remove(key);message.value=failures.values.lastOrNull() }
            true
        } catch(cancellation: CancellationException) { throw cancellation }
        catch(_: Exception) {
            synchronized(failures) { failures[key]=error;message.value=failures.values.lastOrNull() }
            false
        }
    }
}
