package com.munkberries.tikhayapolyanka.system

import android.app.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.munkberries.tikhayapolyanka.MainActivity
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import com.munkberries.tikhayapolyanka.data.TimerSettings

/** Channels are the only sound path, also in foreground; never bypass DND. */
class CompletionNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun legacyChannelId(settings: TimerSettings) = "timer_s${if (settings.sound) 1 else 0}_v${if (settings.vibration) 1 else 0}"
    fun channelId(settings: TimerSettings,phase: Phase=Phase.FOCUS) =
        if(phase==Phase.FOCUS && settings.sound) "timer_blackbird_soft_2_v${if(settings.vibration)1 else 0}"
        else legacyChannelId(settings)
    private fun previousBirdChannelId(settings: TimerSettings) = "timer_blackbird_1_v${if(settings.vibration)1 else 0}"
    fun blackbirdUri(): android.net.Uri = android.net.Uri.parse("android.resource://${context.packageName}/raw/tp_blackbird_soft")
    fun ensureChannels() {
        for (sound in listOf(false, true)) for (vibration in listOf(false, true)) {
            val s = TimerSettings(sound = sound, vibration = vibration)
            val name = context.getString(when { sound && vibration -> R.string.timer_channel_both; sound -> R.string.timer_channel_sound; vibration -> R.string.timer_channel_vibrate; else -> R.string.timer_channel_silent })
            manager.createNotificationChannel(NotificationChannel(legacyChannelId(s), name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(if (sound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(vibration)
                if (vibration) vibrationPattern = longArrayOf(0, 180, 100, 180)
                setShowBadge(false)
            })
        }
        for(vibration in listOf(false,true)) {
            val settings=TimerSettings(sound=true,vibration=vibration)
            val id=channelId(settings)
            if(manager.getNotificationChannel(id)!=null)continue
            val previous=manager.getNotificationChannel(previousBirdChannelId(settings))
                ?: manager.getNotificationChannel(legacyChannelId(settings))
            val name=context.getString(if(vibration)R.string.blackbird_channel_both else R.string.blackbird_channel_sound)
            val channel=NotificationChannel(id,name,previous?.importance ?: NotificationManager.IMPORTANCE_DEFAULT).apply {
                description=context.getString(R.string.blackbird_channel_description)
                // Carry a system mute/custom tone forward; never restore sound or importance after a user disabled it.
                val previousDefault=android.net.Uri.parse("android.resource://${context.packageName}/raw/tp_blackbird_focus")
                val custom=Build.VERSION.SDK_INT>=30 && previous?.hasUserSetSound()==true &&
                    previous.sound!=previousDefault && previous.sound!=blackbirdUri()
                val sound=when {previous!=null && previous.sound==null->null;custom->previous?.sound;else->blackbirdUri()}
                setSound(sound,previous?.audioAttributes ?: AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                val vibrate=previous?.shouldVibrate() ?: vibration
                if(vibrate)vibrationPattern=previous?.vibrationPattern ?: longArrayOf(0,180,100,180)
                enableVibration(vibrate)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
    fun available(settings: TimerSettings,phase: Phase=Phase.FOCUS): Boolean = manager.areNotificationsEnabled() &&
        (manager.getNotificationChannel(channelId(settings,phase))?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE
    fun show(record: Record, settings: TimerSettings) {
        if (!record.session.completed || !settings.notifications || !available(settings,record.session.phase)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setData(android.net.Uri.parse("polyanka://focus")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val focus = record.session.phase == Phase.FOCUS
        val notification = Notification.Builder(context, channelId(settings,record.session.phase))
            .setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(context.getString(if(focus) R.string.rest_offer else R.string.rest_finished))
            .setContentText(context.getString(if(focus) R.string.focus_notification_body else R.string.rest_notification_body))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER).build()
        try { manager.notify("timer", record.session.id.hashCode(), notification) } catch (_: SecurityException) { /* permission changed */ }
    }
    fun cancelSession(id: String) {
        manager.cancel("timer",id.hashCode())
        manager.cancel(null,id.hashCode())
    }
    fun clear() {
        manager.activeNotifications.filter {
            NotificationIdentity.timer(it.tag,it.notification.channelId)
        }.forEach { manager.cancel(it.tag,it.id) }
    }
}



