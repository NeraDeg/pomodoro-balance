package com.munkberries.tikhayapolyanka.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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

@Composable fun FocusScreen(
    state: Snapshot,history: List<Record>,settings: TimerSettings,now: Stamp,
    model: PolyankaViewModel,onSettings: ()->Unit,onHistory: ()->Unit,
) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val busy by model.timerBusy.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf(state.draft) }
    LaunchedEffect(state.session?.id) { draft=state.draft }
    val session=state.session
    val status=session?.status ?: Status.IDLE
    val phase=session?.phase ?: state.phase
    val remaining=session?.let{TimerEngine.remaining(it,now)} ?: settings.duration(phase)
    val total=session?.duration ?: settings.duration(phase)
    val offer=TimerPresentation.breakOffer(state,settings.duration(Phase.SHORT_BREAK),settings.duration(Phase.LONG_BREAK))
    val today=Instant.ofEpochMilli(now.wall).atZone(ZoneId.systemDefault()).toLocalDate()
    val daily=TimerEngine.daily(history,today,ZoneId.systemDefault(),session,now)
    val caption=cycleCaption(state)
    val captionText=stringResource(when(caption.kind){
        CycleCaptionKind.CURRENT->R.string.cycle_current;CycleCaptionKind.FINISHED->R.string.cycle_finished
        CycleCaptionKind.INTERRUPTED->R.string.cycle_interrupted;CycleCaptionKind.REST->R.string.cycle_rest
        CycleCaptionKind.MANUAL_REST->R.string.manual_rest
    },caption.number)
    BoxWithConstraints(Modifier.widthIn(max=620.dp).fillMaxSize()) {
        val compact=maxHeight<620.dp || LocalDensity.current.fontScale>1.4f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                IconButton(onSettings){ArtIcon(R.drawable.tp_ic_settings,stringResource(R.string.settings))}
            }
            if(offer!=null) {
                Surface(color=PaleSage,contentColor=Sage,shape=CircleShape,border=BorderStroke(1.dp,Line)) {
                    Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        ArtIcon(R.drawable.tp_ic_check)
                        Text(stringResource(R.string.focus_completed),style=MaterialTheme.typography.labelLarge,
                            modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
                    }
                }
                Text(stringResource(R.string.time_to_rest),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
                Text(stringResource(R.string.focus_behind,exactDuration(offer.completedFocusDuration)),color=Muted,textAlign=TextAlign.Center)
                TimerRing(offer.duration,offer.duration,offer.phase,Status.IDLE,false,{},preview=true)
                ForestScene(rest=true,height=if(compact)104.dp else 174.dp)
                PrimaryButton(stringResource(R.string.start_rest),model::startOfferedBreak,enabled=!busy)
                TextButton({model.prepare(Phase.FOCUS)},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.skip_rest)) }
                CycleDots(state.cycle)
                TextButton({model.prepare(Phase.FOCUS)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.finish_today))}
            } else {
                Text(stringResource(R.string.time_for_self),Modifier.align(Alignment.Start),color=Muted,style=MaterialTheme.typography.bodyMedium)
                OutlinedTextField(draft,{draft=it;model.draft(it)},label={Text(stringResource(R.string.task_prompt))},
                    modifier=Modifier.fillMaxWidth(),maxLines=3,shape=MaterialTheme.shapes.medium,
                    supportingText={if(status==Status.RUNNING || status==Status.PAUSED)Text(stringResource(R.string.session_title_saved))})
                if(status==Status.IDLE)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(Phase.FOCUS to R.string.focus,Phase.SHORT_BREAK to R.string.rest).forEach{(p,label)->
                        FilterChip(phase==p,{model.select(p)},label={Text(stringResource(label))},
                            leadingIcon={ArtIcon(if(p==Phase.FOCUS)R.drawable.tp_ic_focus_leaf else R.drawable.tp_ic_sun)},
                            modifier=Modifier.weight(1f).heightIn(min=48.dp))
                    }
                }
                TimerRing(remaining,total,phase,status,!busy,model::timerTap)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    TextButton({if(status==Status.RUNNING || status==Status.PAUSED)confirmReset=true else model.resetTimer()},
                        modifier=Modifier.heightIn(min=48.dp)) {
                        ArtIcon(R.drawable.ic_reset);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.reset_timer))
                    }
                    if(status==Status.RUNNING || status==Status.PAUSED)TextButton({confirmStop=true},modifier=Modifier.heightIn(min=48.dp)){
                        Text(stringResource(R.string.finish))
                    }
                }
                CycleDots(state.cycle)
                Text(captionText,style=MaterialTheme.typography.bodySmall,color=Muted,textAlign=TextAlign.Center)
                if(status==Status.FINISHED)PaperCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(if(session?.completed==true)R.string.rest_finished else R.string.session_interrupted),
                        style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
                    PrimaryButton(stringResource(R.string.next_focus),{model.prepare(Phase.FOCUS)})
                }
                state.recoveryNote?.let{message->Text(stringResource(when{
                    message.contains("на паузе")->R.string.recovery_rollback
                    message.contains("перезагрузки")->R.string.recovery_reboot
                    else->R.string.recovery_clock
                }),color=Muted,style=MaterialTheme.typography.bodySmall)}
                ForestScene(rest=phase!=Phase.FOCUS,height=if(compact)164.dp else 240.dp)
            }
            PaperCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.today),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.today_focus_total,minuteText(daily.millis),sessionText(daily.completed)),color=Muted)
                TextButton(onHistory,modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.history))}
            }
            if(!settings.notifications)Text(stringResource(R.string.focus_signal_hint),color=Muted,
                style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
        }
    }
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text(stringResource(R.string.reset_question))},
        text={Text(stringResource(R.string.reset_warning))},
        confirmButton={TextButton({confirmReset=false;model.resetTimer()}){Text(stringResource(R.string.reset_timer))}},
        dismissButton={TextButton({confirmReset=false}){Text(stringResource(R.string.cancel))}})
    if(confirmStop)AlertDialog(onDismissRequest={confirmStop=false},title={Text(stringResource(R.string.finish_question))},
        text={Text(stringResource(R.string.finish_explanation))},
        confirmButton={TextButton({confirmStop=false;model.stop()}){Text(stringResource(R.string.finish))}},
        dismissButton={TextButton({confirmStop=false}){Text(stringResource(R.string.cancel))}})
}

@Composable private fun CycleDots(cycle: Int) {
    val progress=stringResource(R.string.cycle_progress,cycle)
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.clearAndSetSemantics{contentDescription=progress}) {
        repeat(4){i->Box(Modifier.size(10.dp).background(if(i<cycle)Sage else CardPaper,CircleShape).border(1.dp,Line,CircleShape))}
    }
}
@Composable fun ForestScene(rest: Boolean,height: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clearAndSetSemantics{}) {
        // Extend through the screen padding; all layers retain their natural aspect ratio.
        Box(Modifier.requiredWidth(maxWidth+40.dp).fillMaxHeight().align(Alignment.BottomCenter)) {
            Image(painterResource(R.drawable.tp_glade_day),null,Modifier.fillMaxWidth().align(Alignment.BottomCenter),contentScale=ContentScale.Fit)
            Image(painterResource(R.drawable.tp_folk_corner),null,
                Modifier.size(height*.68f).align(Alignment.BottomEnd),contentScale=ContentScale.Fit)
            Image(painterResource(R.drawable.tp_daisies),null,
                Modifier.size(height*.32f).align(Alignment.BottomStart),contentScale=ContentScale.Fit)
            Image(painterResource(if(rest)R.drawable.tp_fox_rest else R.drawable.tp_fox_focus),null,
                Modifier.height(height*.96f).widthIn(max=if(rest)320.dp else 250.dp)
                    .align(if(rest)Alignment.BottomCenter else Alignment.BottomStart),contentScale=ContentScale.Fit)
        }
    }
}
@Composable private fun exactDuration(millis: Long): String {
    val seconds=(millis/1000).toInt()
    val minutes=pluralStringResource(R.plurals.minutes,seconds/60,seconds/60)
    return if(seconds%60==0)minutes else minutes+" "+pluralStringResource(R.plurals.seconds,seconds%60,seconds%60)
}
@Composable private fun TimerRing(remaining: Long,total: Long,phase: Phase,status: Status,enabled: Boolean,onTap: ()->Unit,preview: Boolean=false) {
    val seconds=ceil(remaining/1000.0).toInt()
    val digits=String.format(RussianDates.locale,"%02d:%02d",seconds/60,seconds%60)
    val label=stringResource(when{
        preview&&phase==Phase.LONG_BREAK->R.string.long_break_label;preview->R.string.short_break_label
        status==Status.PAUSED->R.string.paused;status==Status.FINISHED->R.string.ready
        phase==Phase.FOCUS->R.string.focus_time;phase==Phase.LONG_BREAK->R.string.long_rest;else->R.string.rest_time
    })
    val description=stringResource(R.string.timer_description,label,
        pluralStringResource(R.plurals.minutes,seconds/60,seconds/60),pluralStringResource(R.plurals.seconds,seconds%60,seconds%60))
    val action=stringResource(when(status){
        Status.IDLE->R.string.tap_start;Status.RUNNING->R.string.tap_pause;Status.PAUSED->R.string.tap_resume;Status.FINISHED->R.string.ready
    })
    val buttonDescription=stringResource(R.string.timer_button,action,description)
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val diameter=minOf(maxWidth,304.dp)
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(diameter).then(if(preview)Modifier.semantics{contentDescription=description} else
                Modifier.clickable(enabled=enabled&&status!=Status.FINISHED,role=Role.Button,onClickLabel=action,onClick=onTap)
                    .semantics(mergeDescendants=true){contentDescription=buttonDescription}),contentAlignment=Alignment.Center) {
                Image(painterResource(R.drawable.tp_timer_wreath),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                if(!preview)Canvas(Modifier.fillMaxSize().padding(35.dp)) {
                    val inset=3.dp.toPx();val circle=Size(size.width-inset*2,size.height-inset*2)
                    drawArc(Line.copy(alpha=.5f),-90f,360f,false,Offset(inset,inset),circle,style=Stroke(1.dp.toPx()))
                    drawArc(Sage,-90f,360f*(remaining.toFloat()/total.coerceAtLeast(1)),false,
                        Offset(inset,inset),circle,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
                }
                Column(Modifier.padding(50.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(digits,modifier=Modifier.clearAndSetSemantics{},
                        fontSize=minOf(64f,(diameter.value-106f)/(digits.length*.64f*scale)).sp,
                        fontWeight=FontWeight.SemiBold,maxLines=1)
                    if(scale<=1.3f)Text(label,modifier=Modifier.clearAndSetSemantics{},style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
                    if(!preview&&status!=Status.FINISHED)Box(Modifier.size(42.dp).background(Sage,CircleShape),contentAlignment=Alignment.Center) {
                        Icon(painterResource(if(status==Status.RUNNING)R.drawable.ic_pause else R.drawable.tp_ic_play),null,
                            Modifier.size(22.dp),tint=Color.White)
                    }
                }
            }
            if(scale>1.3f)Text(label,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
            if(!preview&&status!=Status.FINISHED)Text(action,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)
        }
    }
}
