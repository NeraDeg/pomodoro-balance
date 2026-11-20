package com.munkberries.tikhayapolyanka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import com.munkberries.tikhayapolyanka.data.TimerSettings
import java.time.*
import kotlin.math.ceil

@Composable fun PolyankaApp(model: PolyankaViewModel) {
    val ready by model.ready.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val now by model.now.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val task by model.taskDraft.collectAsStateWithLifecycle()
    val pending by model.pendingDestination.collectAsStateWithLifecycle()
    val current by model.currentSessionDialog.collectAsStateWithLifecycle()
    val taskSaving by model.taskSaving.collectAsStateWithLifecycle()
    val closingNote by model.closingNote.collectAsStateWithLifecycle()
    val navigation by model.navigationRevision.collectAsStateWithLifecycle()
    val pageStates=rememberSaveableStateHolder()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(navigation) { showSettings=false;showHistory=false }
    BackHandler(showSettings || showHistory || tab!=0 || tab==1 && editor!=null || tab==2 && task!=null) {
        if(taskSaving || closingNote) return@BackHandler
        when {
            showSettings->showSettings=false
            showHistory->showHistory=false
            tab==2 && task!=null->model.requestCloseTask()
            tab==1 && editor!=null->model.closeNote()
            else->model.requestTab(0)
        }
    }
    Scaffold(containerColor=Paper,bottomBar={
        if(!showSettings && !showHistory) NavigationBar(containerColor=CardPaper,modifier=Modifier.border(BorderStroke(1.dp,Line))) {
            val labels=listOf(R.string.timer_tab,R.string.notes,R.string.calendar)
            val icons=listOf(R.drawable.tp_ic_focus_leaf,R.drawable.tp_ic_notes,R.drawable.tp_ic_calendar)
            val active=listOf(R.drawable.tp_ic_focus_leaf_active,R.drawable.tp_ic_notes_active,R.drawable.tp_ic_calendar_active)
            labels.forEachIndexed { i,label ->
                NavigationBarItem(selected=tab==i,onClick={model.requestTab(i)},enabled=!taskSaving&&!closingNote,
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=Sage,selectedTextColor=Sage,indicatorColor=PaleSage,unselectedIconColor=Ink,unselectedTextColor=Muted),
                    label={Text(stringResource(label),maxLines=2,textAlign=TextAlign.Center)},
                    icon={ArtIcon(if(tab==i)active[i] else icons[i],tint=if(tab==i)Sage else Ink)})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding(),horizontalAlignment=Alignment.CenterHorizontally) {
            error?.let { Text(stringResource(it),Modifier.fillMaxWidth().background(PaleSage).padding(16.dp),
                style=MaterialTheme.typography.bodyMedium) }
            notice?.let { message -> Row(Modifier.fillMaxWidth().background(PaleSage),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(message),Modifier.weight(1f).padding(12.dp))
                IconButton({model.notice.value=null}) { ArtIcon(R.drawable.tp_ic_close,stringResource(R.string.close)) }
            } }
            when {
                !ready->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                showSettings->SettingsScreen(settings,model,model.runtime.scheduler.exactAvailable(),
                    model.runtime.notifier.available(settings),onBack={showSettings=false})
                showHistory->HistoryScreen(history,model,onBack={showHistory=false})
                else->pageStates.SaveableStateProvider(tab) {
                    when(tab) {
                        0->FocusScreen(state,history,settings,now,model,{showSettings=true},{showHistory=true})
                        1->NotesScreen(model)
                        else->CalendarScreen(model,history,state.session,now)
                    }
                }
            }
        }
    }
    if(pending!=null) AlertDialog(onDismissRequest={model.pendingDestination.value=null},
        title={Text(stringResource(R.string.form_close_question))},
        confirmButton={TextButton({model.confirmLeave(true)}){Text(stringResource(R.string.save))}},
        dismissButton={ Column {
            TextButton({model.confirmLeave(false)}){Text(stringResource(R.string.discard))}
            TextButton({model.pendingDestination.value=null}){Text(stringResource(R.string.cancel))}
        } })
    if(current) AlertDialog(onDismissRequest={model.currentSessionDialog.value=false},
        title={Text(stringResource(R.string.current_session_question))},text={Text(stringResource(R.string.current_session_hint))},
        confirmButton={TextButton(model::goCurrent){Text(stringResource(R.string.go_current_session))}},
        dismissButton={TextButton({model.currentSessionDialog.value=false}){Text(stringResource(R.string.cancel))}})
}

