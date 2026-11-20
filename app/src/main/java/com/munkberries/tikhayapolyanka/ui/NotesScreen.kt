package com.munkberries.tikhayapolyanka.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R
import com.munkberries.tikhayapolyanka.core.*
import java.time.ZoneId

@Composable fun NotesScreen(model: PolyankaViewModel) {
    val editor by model.editor.collectAsStateWithLifecycle()
    val closing by model.closingNote.collectAsStateWithLifecycle()
    if(closing) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    else if(editor!=null) NoteEditor(model,editor!!) else NoteList(model)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun NoteList(model: PolyankaViewModel) {
    val notes by model.notes.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val folder by model.folder.collectAsStateWithLifecycle()
    val results=remember(notes,query,folder){NoteRules.sorted(notes.filter{NoteRules.matches(it,query,folder)})}
    var manage by rememberSaveable{mutableStateOf(false)}
    var newFolder by rememberSaveable{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<Note?>(null)}
    LazyColumn(Modifier.widthIn(max=620.dp).fillMaxSize().padding(horizontal=20.dp),
        state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item(key="header"){Row(verticalAlignment=Alignment.CenterVertically){
            Text(stringResource(R.string.notes),Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
            IconButton({manage=true}){ArtIcon(R.drawable.tp_ic_folder,stringResource(R.string.folders))}
            CreateAction(stringResource(R.string.create_note),{model.newNote()})
        }}
        item(key="banner"){ForestBanner(stringResource(R.string.notes_banner),R.drawable.tp_squirrel_notes)}
        item(key="search"){OutlinedTextField(query,model::search,modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text(stringResource(R.string.search_notes))},leadingIcon={ArtIcon(R.drawable.tp_ic_search)})}
        item(key="filters"){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(folder==null,{model.filter(null)},label={Text(stringResource(R.string.all_folders))},modifier=Modifier.heightIn(min=48.dp))
            folders.forEach{f->FilterChip(folder==f.id,{model.filter(f.id)},label={Text(f.name)},modifier=Modifier.heightIn(min=48.dp))}
            FilterChip(folder=="__none",{model.filter("__none")},label={Text(stringResource(R.string.no_folder))},modifier=Modifier.heightIn(min=48.dp))
            AssistChip({newFolder=true},label={Text(stringResource(R.string.folder_chip))},modifier=Modifier.heightIn(min=48.dp))
        }}
        item(key="templates"){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SecondaryButton({model.newNote(BlockType.HEADING)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.note_template))}
            SecondaryButton({model.newNote(BlockType.CHECK)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.list_template))}
        }}
        if(results.isEmpty())item(key="empty"){
            val emptySearch=query.isNotBlank()
            PaperCard(Modifier.fillMaxWidth().then(if(!emptySearch)Modifier.clickable{model.newNote()} else Modifier)){
                if(!emptySearch){
                    Text(stringResource(R.string.notes_empty),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.notes_empty_hint),color=Muted)
                    TextButton({model.newNote()},modifier=Modifier.heightIn(min=48.dp)){
                        ArtIcon(R.drawable.tp_ic_plus);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.create_note))
                    }
                }else{
                    Text(stringResource(R.string.search_empty),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.search_empty_hint),color=Muted)
                    TextButton({model.search("")},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.reset_search))}
                }
            }
        }
        items(results,key={it.id}){n->PaperCard(Modifier.fillMaxWidth().clickable{model.openNote(n.id)},color=notePaint(n.background)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(n.title.ifBlank{stringResource(R.string.untitled)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=3)
                IconButton({model.pin(n)}){ArtIcon(R.drawable.tp_ic_pin,stringResource(if(n.pinned)R.string.unpin else R.string.pin))}
            }
            if(n.pinned)Text(stringResource(R.string.pinned),color=Sage,style=MaterialTheme.typography.labelMedium)
            val preview=n.blocks.filter{it.text.isNotBlank() || it.checked}.take(3)
            preview.forEach{block->
                Row(verticalAlignment=Alignment.Top) {
                    when(block.type) {
                        BlockType.CHECK->Checkbox(block.checked,onCheckedChange=null,modifier=Modifier.padding(end=8.dp))
                        BlockType.BULLET->Text("• ",color=Muted)
                        BlockType.NUMBERED->Text((NoteRules.numberAt(n.blocks,n.blocks.indexOf(block))).toString()+". ",color=Muted)
                        else->Unit
                    }
                    Text(block.text.take(180),maxLines=3,color=Ink,style=MaterialTheme.typography.bodyMedium,
                        textDecoration=if(block.type==BlockType.CHECK&&block.checked)androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
            }
            Text(RussianDates.history(n.updatedAt,ZoneId.systemDefault()),style=MaterialTheme.typography.bodySmall,color=Muted)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(folders.find{it.id==n.folderId}?.name ?: stringResource(R.string.no_folder),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                IconButton({deleting=n}){ArtIcon(R.drawable.tp_ic_trash,stringResource(R.string.delete))}
            }
        }}
        if(results.isNotEmpty())item(key="create"){PrimaryButton(stringResource(R.string.create_note),{model.newNote()})}
    }
    if(newFolder)FolderForm(model,null,false){newFolder=false}
    if(manage)FolderManager(model){manage=false}
    deleting?.let{n->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_note_question))},
        text={Text(stringResource(R.string.delete_note_hint))},
        confirmButton={TextButton({model.deleteNote(n.id);deleting=null}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton({deleting=null}){Text(stringResource(R.string.cancel))}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun NoteEditor(model: PolyankaViewModel,note: Note) {
    val folders by model.folders.collectAsStateWithLifecycle()
    val focused by model.focusBlock.collectAsStateWithLifecycle()
    val selectionRevision by model.selectionRevision.collectAsStateWithLifecycle()
    val pending by model.saveCount.collectAsStateWithLifecycle()
    val manual by model.manualSaving.collectAsStateWithLifecycle()
    val progress by model.noteSave.collectAsStateWithLifecycle()
    val removed by model.removedBlock.collectAsStateWithLifecycle()
    var folderMenu by remember{mutableStateOf(false)}
    var newFolder by remember{mutableStateOf(false)}
    var colorMenu by remember{mutableStateOf(false)}
    var delete by remember{mutableStateOf(false)}
    val selected=note.blocks.find{it.id==focused} ?: note.blocks.firstOrNull()
    val range=remember(selected,selectionRevision){selected?.let{model.selection(it.id)} ?: intArrayOf(0,0)}
    val bold=selected?.let{b->b.bold || range[0]!=range[1] &&
        (minOf(range[0],range[1]) until maxOf(range[0],range[1])).all{p->b.marks.any{p>=it.start&&p<it.end}}} ?: false
    val currentColor=note.background
    val status=when{
        pending>0||manual->R.string.saving
        progress.failed!=null->R.string.save_failed
        progress.current(note.revision)->R.string.saved
        else->R.string.unsaved
    }
    Column(Modifier.widthIn(max=620.dp).fillMaxSize().padding(horizontal=16.dp)) {
        Header(stringResource(R.string.notes),model::closeNote) {
            TextButton(model::saveCurrentNote,enabled=!manual,modifier=Modifier.heightIn(min=48.dp)) {
                Text(stringResource(if(manual)R.string.saving else R.string.save))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            val formats=listOf(
                Triple(BlockType.PARAGRAPH,R.string.paragraph,R.drawable.tp_ic_edit),
                Triple(BlockType.HEADING,R.string.heading,R.drawable.tp_ic_heading),
                Triple(BlockType.BULLET,R.string.bullets,R.drawable.tp_ic_list_bullets),
                Triple(BlockType.NUMBERED,R.string.numbered,R.drawable.tp_ic_list_numbers),
                Triple(BlockType.CHECK,R.string.checklist,R.drawable.tp_ic_checkbox))
            formats.forEach{(type,label,icon)->FilterChip(selected?.type==type,{model.blockType(type)},
                label={Text(stringResource(label))},leadingIcon={ArtIcon(icon)},modifier=Modifier.heightIn(min=48.dp))}
            FilterChip(bold,model::bold,label={Text(stringResource(R.string.bold))},
                leadingIcon={ArtIcon(R.drawable.tp_ic_bold)},modifier=Modifier.heightIn(min=48.dp))
            FilterChip(colorMenu,{colorMenu=true},modifier=Modifier.heightIn(min=48.dp),
                label={Text(stringResource(R.string.note_color))},
                leadingIcon={Box(Modifier.size(16.dp).background(notePaint(currentColor),androidx.compose.foundation.shape.CircleShape))})
        }
        Text(stringResource(R.string.current_color,stringResource(colorName(currentColor))),style=MaterialTheme.typography.bodySmall,color=Muted)
        HorizontalDivider(color=Line)
        val scroll=rememberLazyListState()
        LazyColumn(state=scroll,modifier=Modifier.weight(1f).fillMaxWidth().background(notePaint(note.background),MaterialTheme.shapes.medium),
            contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            item(key="metadata"){
                Column {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(status),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,
                            color=if(progress.failed!=null)MaterialTheme.colorScheme.error else Muted)
                        if(progress.failed!=null)TextButton(model::saveCurrentNote,enabled=!manual,
                            modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.retry))}
                    }
                    Box{
                        TextButton({folderMenu=true},modifier=Modifier.heightIn(min=48.dp)){
                            ArtIcon(R.drawable.tp_ic_folder);Spacer(Modifier.width(8.dp))
                            Text(folders.find{it.id==note.folderId}?.name ?: stringResource(R.string.no_folder))
                        }
                        DropdownMenu(folderMenu,{folderMenu=false}){
                            DropdownMenuItem(text={Text(stringResource(R.string.no_folder))},onClick={model.editNote{it.copy(folderId=null)};folderMenu=false})
                            folders.forEach{f->DropdownMenuItem(text={Text(f.name)},onClick={model.editNote{it.copy(folderId=f.id)};folderMenu=false})}
                            HorizontalDivider()
                            DropdownMenuItem(text={Text(stringResource(R.string.new_folder_action))},onClick={folderMenu=false;newFolder=true})
                        }
                    }
                    Row {
                        TextButton({model.pin(note)},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(if(note.pinned)R.string.unpin else R.string.pin))}
                        TextButton({delete=true},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.delete_note_action))}
                    }
                }
            }
            item(key="title"){OutlinedTextField(note.title,{text->model.editNote{it.copy(title=text)}},modifier=Modifier.fillMaxWidth(),
                label={Text(stringResource(R.string.note_title))},textStyle=MaterialTheme.typography.titleLarge,maxLines=5)}
            itemsIndexed(note.blocks,key={_,b->b.id}){index,b->BlockField(b,index,note.blocks,model,focused==b.id)}
            item(key="actions"){TextButton(model::addBlock,modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.add_block))}}
        }
        LaunchedEffect(focused){
            val index=note.blocks.indexOfFirst{it.id==focused}
            if(index>=0 && index+2 !in scroll.layoutInfo.visibleItemsInfo.map{it.index})scroll.animateScrollToItem(index+2)
        }
        removed?.takeIf{it.noteId==note.id}?.let{undo->
            Snackbar(action={TextButton(model::undoBlock){Text(stringResource(R.string.undo))}}){Text(stringResource(R.string.block_removed))}
            LaunchedEffect(undo){
                kotlinx.coroutines.delay(6000)
                if(model.removedBlock.value==undo)model.removedBlock.value=null
            }
        }
    }
    if(newFolder)FolderForm(model,null,true){newFolder=false}
    if(colorMenu)AlertDialog(onDismissRequest={colorMenu=false},title={Text(stringResource(R.string.note_color))},
        text={Column(Modifier.verticalScroll(rememberScrollState())){
            Text(stringResource(R.string.color_behavior),style=MaterialTheme.typography.bodySmall)
            NoteBackground.entries.forEach{c->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(currentColor==c,role=Role.RadioButton,onClick={model.noteBackground(c);colorMenu=false}),
                    verticalAlignment=Alignment.CenterVertically){
                    RadioButton(currentColor==c,onClick=null)
                    Box(Modifier.size(24.dp).background(notePaint(c),androidx.compose.foundation.shape.CircleShape).border(1.dp,Muted,androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(colorName(c)),color=Ink)
                }
            }
        }},confirmButton={TextButton({colorMenu=false}){Text(stringResource(R.string.close))}})
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text(stringResource(R.string.delete_note_question))},
        text={Text(stringResource(R.string.delete_note_hint))},
        confirmButton={TextButton({model.deleteNote(note.id);delete=false}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton({delete=false}){Text(stringResource(R.string.cancel))}})
}
fun notePaint(color: NoteBackground): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(color.argb)
fun colorName(color: NoteBackground): Int=when(color){
    NoteBackground.CREAM->R.string.background_cream;NoteBackground.SAND->R.string.background_sand
    NoteBackground.SAGE->R.string.background_sage;NoteBackground.LAVENDER->R.string.background_lavender
    NoteBackground.POWDER->R.string.background_powder;NoteBackground.PEACH->R.string.background_peach
}
@Composable private fun BlockField(block: NoteBlock,index: Int,blocks: List<NoteBlock>,model: PolyankaViewModel,focused: Boolean) {
    val stored=model.selection(block.id)
    var value by remember(block.id){mutableStateOf(TextFieldValue(block.text,TextRange(
        stored[0].coerceIn(0,block.text.length),stored[1].coerceIn(0,block.text.length))))}
    val requester=remember{FocusRequester()}
    LaunchedEffect(block.text){
        if(value.text!=block.text){
            val range=model.selection(block.id)
            value=TextFieldValue(block.text,TextRange(range[0].coerceIn(0,block.text.length),range[1].coerceIn(0,block.text.length)))
        }
    }
    LaunchedEffect(focused){
        if(focused){
            val range=model.selection(block.id)
            value=value.copy(selection=TextRange(range[0].coerceIn(0,value.text.length),range[1].coerceIn(0,value.text.length)))
            requester.requestFocus()
        }
    }
    val style=if(block.type==BlockType.HEADING)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
    val checkLabel=stringResource(R.string.check_item)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
        when(block.type){
            BlockType.CHECK->Checkbox(block.checked,{model.checkBlock(block.id,it)},modifier=Modifier.semantics{contentDescription=checkLabel})
            BlockType.BULLET->Text("•",Modifier.width(28.dp).padding(top=10.dp),style=style)
            BlockType.NUMBERED->Text(NoteRules.numberAt(blocks,index).toString()+".",Modifier.widthIn(min=32.dp).padding(top=10.dp),style=style)
            else->Unit
        }
        BasicTextField(value,{next->
            value=next;model.focus(block.id)
            if(next.text!=block.text)model.blockText(block.id,next.text,next.selection.start,next.selection.end)
            else model.selection(block.id,next.selection.start,next.selection.end)
        },modifier=Modifier.weight(1f).heightIn(min=48.dp).focusRequester(requester).onFocusChanged{if(it.isFocused)model.focus(block.id)}.padding(vertical=10.dp),
            cursorBrush=androidx.compose.ui.graphics.SolidColor(Ink),
            textStyle=style.copy(color=Ink,fontWeight=if(block.bold)FontWeight.SemiBold else style.fontWeight),
            visualTransformation=VisualTransformation{input->
                TransformedText(buildAnnotatedString{
                    append(input)
                    block.marks.forEach{r->val a=r.start.coerceIn(0,input.length);val b=r.end.coerceIn(a,input.length)
                        if(a<b)addStyle(SpanStyle(fontWeight=FontWeight.SemiBold),a,b)}

                },OffsetMapping.Identity)
            },decorationBox={inner->Box{
                if(value.text.isEmpty())Text(stringResource(R.string.note_body_hint),style=style,color=Muted)
                inner()
            }})
        if(focused)IconButton({model.removeBlock(block.id)}){
            ArtIcon(R.drawable.tp_ic_trash,stringResource(if(block.type in listOf(BlockType.CHECK,BlockType.BULLET,BlockType.NUMBERED))
                R.string.remove_item else R.string.remove_paragraph))
        }
    }
}
@Composable private fun FolderManager(model: PolyankaViewModel,onClose: ()->Unit) {
    val folders by model.folders.collectAsStateWithLifecycle()
    var form by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<Folder?>(null)}
    var deleting by remember{mutableStateOf<Folder?>(null)}
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.folders))},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){
            Button({editing=null;form=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.new_folder_action))}
            folders.forEach{f->Row(verticalAlignment=Alignment.CenterVertically){
                Text(f.name,Modifier.weight(1f))
                IconButton({editing=f;form=true}){ArtIcon(R.drawable.tp_ic_edit,stringResource(R.string.rename))}
                IconButton({deleting=f}){ArtIcon(R.drawable.tp_ic_trash,stringResource(R.string.delete))}
            }}
        }},confirmButton={TextButton(onClose){Text(stringResource(R.string.close))}})
    if(form)FolderForm(model,editing,false){form=false}
    deleting?.let{f->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_folder_question))},
        text={Text(stringResource(R.string.delete_folder_hint))},
        confirmButton={TextButton({model.deleteFolder(f.id);deleting=null}){Text(stringResource(R.string.delete))}},
        dismissButton={TextButton({deleting=null}){Text(stringResource(R.string.cancel))}})}
}
@Composable private fun FolderForm(model: PolyankaViewModel,folder: Folder?,selectForNote: Boolean,onClose: ()->Unit) {
    var name by rememberSaveable(folder?.id){mutableStateOf(folder?.name ?: "")}
    val busy by model.folderSaving.collectAsStateWithLifecycle()
    val error by model.folderError.collectAsStateWithLifecycle()
    LaunchedEffect(folder?.id){model.folderError.value=null}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(stringResource(if(folder==null)R.string.new_folder else R.string.rename))},
        text={Column{
            OutlinedTextField(name,{name=it;model.folderError.value=null},modifier=Modifier.fillMaxWidth(),
                label={Text(stringResource(R.string.folder_name))},enabled=!busy,singleLine=true,isError=error!=null)
            error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton({model.saveFolder(folder?.id,name,selectForNote,onClose)},enabled=!busy){
            Text(stringResource(if(busy)R.string.saving else R.string.save))
        }},dismissButton={TextButton({onClose()},enabled=!busy){Text(stringResource(R.string.cancel))}})
}

