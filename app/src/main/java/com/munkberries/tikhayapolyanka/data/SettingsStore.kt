package com.munkberries.tikhayapolyanka.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.munkberries.tikhayapolyanka.core.Phase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")
data class TimerSettings(
    val focus: Int = 25, val shortBreak: Int = 5, val longBreak: Int = 15,
    val sound: Boolean = true, val vibration: Boolean = true,
    val notifications: Boolean = false, val permissionAsked: Boolean = false, val taskReminders: Boolean = true,
) {
    fun duration(phase: Phase): Long = when (phase) {
        Phase.FOCUS -> focus; Phase.SHORT_BREAK -> shortBreak; Phase.LONG_BREAK -> longBreak
    } * 60_000L
}
class SettingsStore(private val context: Context) {
    private val focus = intPreferencesKey("focus")
    private val short = intPreferencesKey("short")
    private val long = intPreferencesKey("long")
    private val sound = booleanPreferencesKey("sound")
    private val vibration = booleanPreferencesKey("vibration")
    private val notifications = booleanPreferencesKey("notifications")
    private val reminders = booleanPreferencesKey("task_reminders")
    private val asked = booleanPreferencesKey("permission_asked")
    val flow: Flow<TimerSettings> = context.settingsDataStore.data.map { p ->
        TimerSettings((p[focus] ?: 25).coerceIn(1,120), (p[short] ?: 5).coerceIn(1,30),
            (p[long] ?: 15).coerceIn(1,60), p[sound] ?: true, p[vibration] ?: true,
            p[notifications] ?: false, p[asked] ?: false, p[reminders] ?: true)
    }
    suspend fun save(s: TimerSettings) {
        context.settingsDataStore.edit {
            it[focus] = s.focus.coerceIn(1,120); it[short] = s.shortBreak.coerceIn(1,30)
            it[long] = s.longBreak.coerceIn(1,60); it[sound] = s.sound; it[vibration] = s.vibration
            it[notifications] = s.notifications; it[asked] = s.permissionAsked; it[reminders] = s.taskReminders
        }
    }
}

