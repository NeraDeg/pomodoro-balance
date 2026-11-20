package com.munkberries.tikhayapolyanka.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R
import java.time.*

@Composable fun ArtIcon(@DrawableRes id: Int,description: String?=null,tint: androidx.compose.ui.graphics.Color=Ink) {
    Icon(painterResource(id),description,modifier=Modifier.size(24.dp),tint=tint)
}
/** Keep the Activity-backed WindowManager. A configuration Context is for resources,
 * and must not replace the Activity base when showing a window. MainActivity already
 * supplies Russian resources through attachBaseContext. Return the actual dialog so
 * instrumentation can verify show/confirm/dismiss, not just inflate a picker widget.
 */
fun chooseDate(context: Context,date: LocalDate,onDate: (LocalDate)->Unit): DatePickerDialog {
    val localized=android.view.ContextThemeWrapper(context,R.style.Theme_Polyanka)
    return DatePickerDialog(localized,{ _,year,month,day -> onDate(LocalDate.of(year,month+1,day)) },
        date.year,date.monthValue-1,date.dayOfMonth).apply {
        setButton(DatePickerDialog.BUTTON_POSITIVE,localized.getString(R.string.choose),this)
        setButton(DatePickerDialog.BUTTON_NEGATIVE,localized.getString(R.string.cancel)) { _,_ -> }
        datePicker.firstDayOfWeek=java.util.Calendar.MONDAY
        show()
    }
}
fun chooseTime(context: Context,time: LocalTime,onTime: (LocalTime)->Unit): TimePickerDialog {
    val localized=android.view.ContextThemeWrapper(context,R.style.Theme_Polyanka)
    return TimePickerDialog(localized,{ _,hour,minute -> onTime(LocalTime.of(hour,minute)) },time.hour,time.minute,true).apply {
        setButton(TimePickerDialog.BUTTON_POSITIVE,localized.getString(R.string.choose),this)
        setButton(TimePickerDialog.BUTTON_NEGATIVE,localized.getString(R.string.cancel)) { _,_ -> }
        show()
    }
}
@Composable fun NotificationPermissionButton(model: PolyankaViewModel) {
    val context=LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.settings { s -> s.copy(permissionAsked=true) };model.refresh()
    }
    TextButton({
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
            !settings.permissionAsked) {
            model.settings { it.copy(permissionAsked=true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    },modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.enable_notifications)) }
}
@Composable fun minuteText(millis: Long): String {
    val minutes=(millis/60_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(R.plurals.minutes,minutes,minutes)
}
@Composable fun sessionText(count: Int): String = pluralStringResource(R.plurals.sessions,count,count)
@Composable fun Header(title: String,onBack: ()->Unit,actions: @Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onBack) { ArtIcon(R.drawable.tp_ic_back,stringResource(R.string.back)) }
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        actions()
    }
}

