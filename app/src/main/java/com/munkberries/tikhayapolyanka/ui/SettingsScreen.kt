package com.munkberries.tikhayapolyanka.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.data.TimerSettings

@Composable fun SettingsScreen(settings: TimerSettings,model: PolyankaViewModel,exact: Boolean,available: Boolean,onBack: ()->Unit) {
    var privacy by rememberSaveable{mutableStateOf(false)}
    if(privacy){PrivacySupportScreen{privacy=false};return}
    val context=LocalContext.current
    var licenses by rememberSaveable{mutableStateOf(false)}
    val runtime=model.runtime
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        model.settings{s->s.copy(permissionAsked=true)};model.refresh()
    }
    fun systemNotifications(){context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
    Column(Modifier.widthIn(max=620.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Header(stringResource(R.string.settings),onBack)
        PaperCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rhythm),style=MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.next_session_settings),color=Muted)
            DurationField(stringResource(R.string.focus),settings.focus,1..120){n->model.settings{it.copy(focus=n)}}
            DurationField(stringResource(R.string.short_rest),settings.shortBreak,1..30){n->model.settings{it.copy(shortBreak=n)}}
            DurationField(stringResource(R.string.long_rest),settings.longBreak,1..60){n->model.settings{it.copy(longBreak=n)}}
            Text(stringResource(R.string.four_focus_rest),color=Muted,style=MaterialTheme.typography.bodySmall)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.completion_signal),style=MaterialTheme.typography.titleLarge)
            SettingSwitch(stringResource(R.string.notifications),settings.notifications){enabled->
                model.settings{it.copy(notifications=enabled)}
                if(enabled && !available) {
                    if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
                        !settings.permissionAsked) {
                        model.settings{it.copy(permissionAsked=true)};launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else systemNotifications()
                }
            }
            SettingSwitch(stringResource(R.string.sound),settings.sound){v->model.settings{it.copy(sound=v)}}
            SettingSwitch(stringResource(R.string.vibration),settings.vibration){v->model.settings{it.copy(vibration=v)}}
            Text(stringResource(when{!settings.notifications->R.string.signal_off;!available->R.string.signal_denied;else->R.string.signal_on}),
                color=Muted,style=MaterialTheme.typography.bodyMedium)
            TextButton({systemNotifications()},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.android_notifications))}
            TextButton({context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,runtime.notifier.channelId(settings)))},
                modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.current_channel))}
        }
        PaperCard(Modifier.fillMaxWidth()) {
            TextButton({context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,
                    runtime.notifier.channelId(settings,com.munkberries.tikhayapolyanka.core.Phase.SHORT_BREAK)))},
                modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.rest_signal_channel))}
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.task_reminder_channel),style=MaterialTheme.typography.titleLarge)
            SettingSwitch(stringResource(R.string.task_reminder_channel),settings.taskReminders){v->model.settings{it.copy(taskReminders=v)}}
            Text(stringResource(if(runtime.reminders.available())R.string.signal_on else R.string.reminder_permission_hint),color=Muted)
            NotificationPermissionButton(model)
            TextButton({context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,
                    com.munkberries.tikhayapolyanka.system.TaskReminders.CHANNEL))},
                modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.current_channel))}
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Text(stringResource(if(exact)R.string.exact_on else R.string.exact_off),color=Muted)
            if(Build.VERSION.SDK_INT>=31&&!exact)SecondaryButton({
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+context.packageName)))
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.allow_exact))}
            Text(stringResource(R.string.permissions_optional),color=Muted,style=MaterialTheme.typography.bodySmall)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about),style=MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_text))
            Text(stringResource(R.string.developer_credit))
            TextButton({privacy=true},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.privacy_support))}
            TextButton({licenses=true},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.licenses))}
        }
        TransferSection(model)
    }
    if(licenses){
        val text=remember{context.assets.open("licenses/OFL.txt").bufferedReader().use{it.readText()}}
        AlertDialog(onDismissRequest={licenses=false},title={Text(stringResource(R.string.font_license_title))},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){
                Text(stringResource(R.string.font_license_description));Text(text,style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton({licenses=false}){Text(stringResource(R.string.close))}})
    }
}
@Composable private fun SettingSwitch(label: String,checked: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(label,Modifier.weight(1f));Switch(checked,onChange,modifier=Modifier.semantics{contentDescription=label})
    }
}
@Composable private fun DurationField(label: String,value: Int,range: IntRange,onValue: (Int)->Unit) {
    var text by rememberSaveable{mutableStateOf(value.toString())}
    var edited by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(value){if(!edited)text=value.toString()}
    val valid=text.toIntOrNull()?.let{it in range}==true
    OutlinedTextField(text,{input->
        text=input.filter(Char::isDigit).take(3);edited=true
        text.toIntOrNull()?.takeIf{it in range}?.let(onValue)
    },modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.duration_label,label))},
        singleLine=true,isError=!valid,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
        supportingText={Text(stringResource(if(valid)R.string.duration_range else R.string.invalid_duration,
            *if(valid)arrayOf(range.first,range.last) else emptyArray()))})
}
