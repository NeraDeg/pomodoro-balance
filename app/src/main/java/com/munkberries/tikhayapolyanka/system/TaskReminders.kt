package com.munkberries.tikhayapolyanka.system

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.munkberries.tikhayapolyanka.MainActivity
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import java.time.*
import kotlinx.coroutines.launch

/** Separate component + data URI per task: extras are never used as alarm identity. */
class TaskReminders(private val context: Context) {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private val scheduled=mutableMapOf<String,String>()
    companion object { const val CHANNEL="task_reminders";const val TAG_PREFIX="task:" }
    init {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.task_reminder_channel),NotificationManager.IMPORTANCE_DEFAULT).apply {
            description=context.getString(R.string.task_reminder_description)
            enableVibration(true);setShowBadge(false)
        })
    }
    fun available(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    fun exactAvailable(): Boolean = Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()
    internal fun alarmIntent(id: String,revision: Long=0): Intent =
        Intent(context,TaskReminderReceiver::class.java).apply {
            action="com.munkberries.tikhayapolyanka.TASK_REMINDER"
            data=Uri.Builder().scheme("polyanka").authority("reminder").appendPath(id).build()
            putExtra("id",id);putExtra("revision",revision)
        }
    private fun intent(id: String,revision: Long=0,create: Boolean=true): PendingIntent? =
        PendingIntent.getBroadcast(context,0,alarmIntent(id,revision),
            (if(create)PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(id: String,clearNotification: Boolean=true) {
        intent(id,create=false)?.let { alarms.cancel(it) }
        scheduled.remove(id)
        if(clearNotification) notifications.cancel(TAG_PREFIX+id,0)
    }
    fun sync(tasks: List<PlannedTask>,enabled: Boolean,force: Boolean=false) {
        val now=Instant.now();val zone=ZoneId.systemDefault()
        for(t in tasks) {
            val at=PlannerRules.scheduled(t,now,zone,enabled)
            val key=at?.let { t.revision.toString()+":"+it.toEpochMilli() }
            if(!force && key==scheduled[t.id]) continue
            cancel(t.id,clearNotification=!enabled || t.deleted || t.completed || t.reminder==null)
            if(at==null) continue
            val pending=requireNotNull(intent(t.id,t.revision))
            try {
                if(exactAvailable()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at.toEpochMilli(),pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at.toEpochMilli(),pending)
            } catch(_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at.toEpochMilli(),pending) }
            scheduled[t.id]=key!!
        }
    }
    fun show(task: PlannedTask) {
        if(!available()) return
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val route=Uri.Builder().scheme("polyanka").authority("task").appendPath(task.id)
            .appendQueryParameter("date",task.date.toString()).build()
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).apply {
            data=route;addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(task.title).setContentText(context.getString(R.string.task_reminder_body,RussianDates.full(task.date)))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER).build()
        try { notifications.notify(TAG_PREFIX+task.id,0,notification) } catch(_: SecurityException) { /* revoked */ }
    }
}

class TaskReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val id=intent.getStringExtra("id") ?: return
        val revision=intent.getLongExtra("revision",-1)
        val pending=goAsync()
        val runtime=(context.applicationContext as com.munkberries.tikhayapolyanka.PolyankaApplication).runtime
        runtime.scope.launchReminder(pending) { runtime.deliverReminder(id,revision) }
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchReminder(
    pending: BroadcastReceiver.PendingResult,block: suspend () -> Unit,
) = launch {
    try { block() }
    catch(c: kotlinx.coroutines.CancellationException) { throw c }
    catch(e: Exception) { android.util.Log.e("Polyanka","Task reminder failed",e) }
    finally { pending.finish() }
}


