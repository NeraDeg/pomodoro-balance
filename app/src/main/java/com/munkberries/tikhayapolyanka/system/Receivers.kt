package com.munkberries.tikhayapolyanka.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.munkberries.tikhayapolyanka.PolyankaApplication
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val revision = intent.getIntExtra("revision", -1)
        val pending = goAsync()
        val runtime = (context.applicationContext as PolyankaApplication).runtime
        runtime.scope.launch {
            try { runtime.alarm(id, revision) }
            catch (c: kotlinx.coroutines.CancellationException) { throw c }
            catch (e: Exception) { Log.e("Polyanka", "Alarm could not be committed", e) }
            finally { pending.finish() }
        }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        val runtime = (context.applicationContext as PolyankaApplication).runtime
        runtime.scope.launch {
            try { runtime.refresh(forceAlarm = true) }
            catch (c: kotlinx.coroutines.CancellationException) { throw c }
            catch (e: Exception) { Log.e("Polyanka", "Restore could not be committed", e) }
            finally { pending.finish() }
        }
    }
}

