package com.munkberries.tikhayapolyanka.ui

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import java.time.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CalendarScreen(model: PolyankaViewModel,history: List<Record>,active: Session?,now: Stamp) {
    val draft by model.taskDraft.collectAsStateWithLifecycle()
    if(draft!=null){TaskForm(model,draft!!);return}
    val context=LocalContext.current
    val monthRaw by model.calendarMonth.collectAsStateWithLifecycle()
    val selectedRaw by model.calendarDate.collectAsStateWithLifecycle()
    val tasks by model.tasks.collectAsStateWithLifecycle()
    val zone=ZoneId.systemDefault()
    val today=Instant.ofEpochMilli(now.wall).atZone(zone).toLocalDate()
    val month=YearMonth.parse(monthRaw);val selectedDate=LocalDate.parse(selectedRaw)
    val days=remember(month){RussianDates.days(month)}
    val summary=TimerEngine.daily(history,selectedDate,zone,active,now)
    val plans=remember(tasks,selectedDate){PlannerRules.forDay(tasks,selectedDate)}
    val dayHistory=remember(history,month,zone){days.filterNotNull().associateWith{TimerEngine.daily(history,it,zone)}}
    var deleting by remember{mutableStateOf<PlannedTask?>(null)}
    LazyColumn(Modifier.widthIn(max=620.dp).fillMaxSize().padding(horizontal=20.dp),
        state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item(key="header") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar),Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                CreateAction(stringResource(R.string.plan_task),model::newTask)
            }
        }
        item(key="month") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton({model.selectMonth(month.minusMonths(1))}){ArtIcon(R.drawable.tp_ic_chevron_left,stringResource(R.string.previous_month))}
                Text(RussianDates.month(month),Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleMedium)
                IconButton({model.selectMonth(month.plusMonths(1))}){ArtIcon(R.drawable.tp_ic_chevron_right,stringResource(R.string.next_month))}
            }
        }
        item(key="grid") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gridWidth=maxOf(maxWidth,336.dp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(gridWidth)) {
                        Row {
                            listOf(R.string.weekday_mon,R.string.weekday_tue,R.string.weekday_wed,R.string.weekday_thu,
                                R.string.weekday_fri,R.string.weekday_sat,R.string.weekday_sun).forEach{label->
                                Text(stringResource(label),Modifier.weight(1f).padding(vertical=10.dp),
                                    textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=Muted)
                            }
                        }
                        days.chunked(7).forEach{week->Row {
                            week.forEach{date->
                                Box(Modifier.weight(1f).heightIn(min=60.dp),contentAlignment=Alignment.Center) {
                                    if(date!=null) {
                                        val dateTasks=tasks.filter{it.date==date&&!it.deleted}
                                        val allDone=dateTasks.isNotEmpty()&&dateTasks.all{it.completed}
                                        val hasFocus=(dayHistory[date]?.millis ?: 0)>0 ||
                                            date==today&&active?.phase==Phase.FOCUS&&TimerEngine.remaining(active,now)<active.duration
                                        val isToday=date==today;val chosen=date==selectedDate
                                        val todayText=if(isToday)stringResource(R.string.today_description,RussianDates.full(date)) else RussianDates.full(date)
                                        val planText=pluralStringResource(R.plurals.plans,dateTasks.size,dateTasks.size)
                                        val doneText=if(allDone)stringResource(R.string.done) else ""
                                        val focusText=if(hasFocus)stringResource(R.string.focus) else ""
                                        val description=listOf(todayText,planText,doneText,focusText).filter{it.isNotEmpty()}.joinToString(", ")
                                        TextButton({model.selectDate(date)},
                                            modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics {
                                                selected=chosen;contentDescription=description
                                            }.then(if(isToday)Modifier.border(1.5.dp,Sage,CircleShape) else Modifier),
                                            shape=CircleShape,contentPadding=PaddingValues(vertical=4.dp,horizontal=0.dp),
                                            colors=ButtonDefaults.textButtonColors(containerColor=if(chosen)Sage else Color.Transparent,
                                                contentColor=if(chosen)Color.White else Ink)) {
                                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                                Text(date.dayOfMonth.toString(),textDecoration=if(isToday)TextDecoration.Underline else null)
                                                Text((if(dateTasks.isNotEmpty()) if(allDone)"✓" else "•" else "")+
                                                    if(hasFocus)" ◦" else "",style=MaterialTheme.typography.labelSmall,
                                                    modifier=Modifier.clearAndSetSemantics{})
                                            }
                                        }
                                    }
                                }
                            }
                        }}
                    }
                }
            }
        }
        item(key="today") {
            TextButton(model::today,modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.today))}
            HorizontalDivider(color=Line)
            Text(RussianDates.day(selectedDate),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
        }
        if(plans.isEmpty())item(key="empty") {
            PaperCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.day_empty))
            }
        }
        items(plans,key={it.id}){task->
            PaperCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.Top) {
                    Checkbox(task.completed,{model.completeTask(task,it)},modifier=Modifier.semantics{contentDescription=task.title})
                    Column(Modifier.weight(1f)) {
                        Text(task.title,style=MaterialTheme.typography.titleMedium,
                            textDecoration=if(task.completed)TextDecoration.LineThrough else null)
                        Text(stringResource(kindLabel(task.kind)),color=Muted,style=MaterialTheme.typography.labelMedium)
                        Text(task.time?.let(RussianDates::time) ?: stringResource(R.string.without_time),color=Muted)
                        if(task.completed)Text(stringResource(R.string.done),color=Sage)
                        else if(task.date<today || task.date==today&&task.time!=null&&task.time<LocalTime.now())
                            Text(stringResource(R.string.overdue),color=Terracotta)
                    }
                }
                if(task.description.isNotBlank())Text(task.description,maxLines=4,overflow=TextOverflow.Ellipsis)
                task.reminder?.let{Text(stringResource(R.string.reminder_at,RussianDates.reminder(it)),style=MaterialTheme.typography.bodySmall,color=Muted)}
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton({model.openTask(task)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.edit))}
                    TextButton({chooseDate(context,task.date){model.moveTask(task,it)}},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.move))}
                    TextButton({deleting=task},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.delete))}
                    SecondaryButton({model.focusTask(task)},modifier=Modifier.heightIn(min=48.dp)){
                        ArtIcon(R.drawable.tp_ic_play);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.start_focus))
                    }
                }
            }
        }
        item(key="add") { PrimaryButton(stringResource(R.string.plan_task),model::newTask,leadingIcon=R.drawable.tp_ic_plus) }
        item(key="hedgehog"){ForestBanner(stringResource(R.string.calendar_banner),R.drawable.tp_hedgehog_planner,animalOnLeft=true)}
        item(key="summary") {
            PaperCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.focus_summary,minuteText(summary.millis)),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.completed_summary,sessionText(summary.completed)),color=Muted)
            }
        }
    }
    deleting?.let{task->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_task_question))},
        text={Text(stringResource(R.string.delete_task_hint))},
        confirmButton={TextButton({model.deleteTask(task);deleting=null}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton({deleting=null}){Text(stringResource(R.string.cancel))}})}
}
fun kindLabel(kind: TaskKind): Int=when(kind){TaskKind.TODO->R.string.kind_todo;TaskKind.EVENT->R.string.kind_event;TaskKind.DEADLINE->R.string.kind_deadline}

@Composable fun HistoryScreen(history: List<Record>,model: PolyankaViewModel,onBack: ()->Unit) {
    var deleting by remember{mutableStateOf<Record?>(null)}
    var clear by remember{mutableStateOf(false)}
    Column(Modifier.widthIn(max=620.dp).fillMaxSize().padding(horizontal=20.dp)) {
        Header(stringResource(R.string.history),onBack)
        TextButton({clear=true},enabled=history.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.clear_history))}
        if(history.isEmpty())PaperCard(Modifier.padding(top=24.dp)) {
            Text(stringResource(R.string.history_empty),style=MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_empty_hint),color=Muted)
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
            items(history,key={it.session.id}){record->
                val s=record.session
                PaperCard(Modifier.fillMaxWidth()) {
                    Text(s.title.ifBlank{stringResource(when(s.phase){Phase.FOCUS->R.string.time_for_self;Phase.SHORT_BREAK->R.string.short_rest;Phase.LONG_BREAK->R.string.long_rest})},
                        style=MaterialTheme.typography.titleMedium)
                    Text(RussianDates.history(s.endedAt!!,ZoneId.systemDefault()),color=Muted,style=MaterialTheme.typography.bodySmall)
                    val seconds=(s.intervals.sumOf{it.end-it.start}/1000).toInt()
                    Text(stringResource(R.string.history_duration,pluralStringResource(R.plurals.minutes,seconds/60,seconds/60),
                        pluralStringResource(R.plurals.seconds,seconds%60,seconds%60),stringResource(if(s.completed)R.string.fully else R.string.early)))
                    if(s.phase!=Phase.FOCUS)Text(stringResource(R.string.rest_not_work),color=Muted,style=MaterialTheme.typography.bodySmall)
                    TextButton({deleting=record},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.delete_session))}
                    s.taskId?.let{id->TextButton({
                        val date=Instant.ofEpochMilli(s.endedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        model.route(Uri.Builder().scheme("polyanka").authority("task").appendPath(id).appendQueryParameter("date",date.toString()).build())
                    },modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.linked_task))}}
                }
            }
        }
    }
    deleting?.let{record->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_session_question))},
        text={Text(stringResource(R.string.delete_session_hint))},
        confirmButton={TextButton({model.deleteHistory(record.session.id);deleting=null}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton({deleting=null}){Text(stringResource(R.string.cancel))}})}
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text(stringResource(R.string.clear_history_question))},
        text={Text(stringResource(R.string.clear_history_hint))},
        confirmButton={TextButton({model.deleteHistory();clear=false}){Text(stringResource(R.string.clear_history))}},
        dismissButton={TextButton({clear=false}){Text(stringResource(R.string.cancel))}})
}

