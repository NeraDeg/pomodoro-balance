package com.munkberries.tikhayapolyanka.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.munkberries.tikhayapolyanka.R

@Composable fun TransferSection(model: PolyankaViewModel) {
    val busy by model.transferBusy.collectAsStateWithLifecycle()
    val candidate by model.transferPreview.collectAsStateWithLifecycle()
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->uri?.let(model::exportTransfer)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(model::inspectTransfer)}
    PaperCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.transfer_title),style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.transfer_help),style=MaterialTheme.typography.bodyMedium,color=Muted)
        SecondaryButton({save.launch("PomodoroBalance-backup.pmb")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.transfer_save))}
        SecondaryButton({open.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.transfer_open))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if(candidate!=null)AlertDialog(onDismissRequest={if(!busy)model.transferPreview.value=null},
        title={Text(stringResource(R.string.transfer_confirm))},text={Text(stringResource(R.string.transfer_confirm_help))},
        confirmButton={TextButton(model::importTransfer,enabled=!busy){Text(stringResource(R.string.transfer_restore))}},
        dismissButton={TextButton({model.transferPreview.value=null},enabled=!busy){Text(stringResource(R.string.cancel))}})
}
