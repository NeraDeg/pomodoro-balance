package com.munkberries.tikhayapolyanka.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.munkberries.tikhayapolyanka.core.*

class AndroidClock(private val context: Context) : Clock {
    override fun now() = Stamp(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
}

class AlarmScheduler(private val context: Context, private val clock: Clock) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private var scheduledKey: String? = null
    fun exactAvailable(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun intent(s: Session? = null): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, TimerReceiver::class.java).apply {
            action = "com.munkberries.tikhayapolyanka.TIMER_END"
            s?.let { putExtra("id", it.id); putExtra("revision", it.revision) }
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun sync(snapshot: Snapshot, force: Boolean = false) {
        val s = snapshot.session
        val key = if (s?.status == Status.RUNNING) "${s.id}:${s.revision}" else null
        if (!force && key == scheduledKey) return
        manager.cancel(intent())
        scheduledKey = null
        if (s?.status != Status.RUNNING) return
        val at = SystemClock.elapsedRealtime() + TimerEngine.remaining(s, clock.now()).coerceAtLeast(1)
        val pending = intent(s)
        try {
            if (exactAvailable()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
        scheduledKey = key
    }
}
