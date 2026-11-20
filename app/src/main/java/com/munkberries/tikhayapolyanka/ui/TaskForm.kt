package com.munkberries.tikhayapolyanka.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import java.time.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TaskForm(model: PolyankaViewModel,task: PlannedTask) {
    val saving by model.taskSaving.collectAsStateWithLifecycle()
    if(saving){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    val reminderLabel=stringResource(R.string.reminder)
    val context=LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val problem by model.formProblem.collectAsStateWithLifecycle()
    val now by model.now.collectAsStateWithLifecycle()
    // Re-read system capabilities on foreground refresh, including permission/channel changes.
    val available=remember(now){model.runtime.reminders.available()}
    val exact=remember(now){model.runtime.reminders.exactAvailable()}
    Column(Modifier.widthIn(max=620.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Header(stringResource(if(task.revision==0L)R.string.add_task else R.string.edit_task),model::requestCloseTask)
        problem?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TaskKind.entries.forEach{kind->FilterChip(task.kind==kind,{model.changeTask{it.copy(kind=kind)}},
                label={Text(stringResource(kindLabel(kind)))},modifier=Modifier.heightIn(min=48.dp))}
        }
        OutlinedTextField(task.title,{text->model.changeTask{it.copy(title=text)}},label={Text(stringResource(R.string.task_title))},
            modifier=Modifier.fillMaxWidth(),maxLines=4,isError=problem==R.string.task_title_required)
        OutlinedTextField(task.description,{text->model.changeTask{it.copy(description=text)}},
            label={Text(stringResource(R.string.description))},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)
        Text(stringResource(R.string.date),style=MaterialTheme.typography.labelLarge)
        SecondaryButton({chooseDate(context,task.date){d->model.changeTask{PlannerRules.move(it,d)}}},
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(RussianDates.full(task.date))}
        Text(stringResource(R.string.time),style=MaterialTheme.typography.labelLarge)
        SecondaryButton({chooseTime(context,task.time ?: LocalTime.now()){t->model.changeTask{it.copy(time=t)}}},
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text(task.time?.let(RussianDates::time) ?: stringResource(R.string.optional_time))
        }
        if(task.time!=null)TextButton({model.changeTask{it.copy(time=null)}},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.clear_time))}
        PaperCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.reminder),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Switch(task.reminder!=null,{on->
                    if(!on)model.changeTask{it.copy(reminder=null)}
                    else chooseDate(context,task.date){date->
                        chooseTime(context,task.time ?: LocalTime.now().plusMinutes(10)){time->
                            model.changeTask{it.copy(reminder=LocalDateTime.of(date,time))}
                        }
                    }
                },modifier=Modifier.semantics{contentDescription=reminderLabel})
            }
            task.reminder?.let{reminder->
                Text(stringResource(R.string.reminder_at,RussianDates.reminder(reminder)))
                SecondaryButton({chooseDate(context,reminder.toLocalDate()){date->
                    model.changeTask{it.copy(reminder=LocalDateTime.of(date,reminder.toLocalTime()))}
                }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.reminder_date))}
                SecondaryButton({chooseTime(context,reminder.toLocalTime()){time->
                    model.changeTask{it.copy(reminder=LocalDateTime.of(reminder.toLocalDate(),time))}
                }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.reminder_time))}
                if(!settings.taskReminders)Text(stringResource(R.string.reminder_disabled_hint),color=Muted)
                if(!available){Text(stringResource(R.string.reminder_permission_hint),color=Muted);NotificationPermissionButton(model)}
                if(!exact)Text(stringResource(R.string.reminder_inexact_hint),color=Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
        PrimaryButton(stringResource(R.string.save),{model.saveTask()})
        TextButton(model::requestCloseTask,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.cancel))}
    }
}



