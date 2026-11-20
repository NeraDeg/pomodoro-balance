package com.munkberries.tikhayapolyanka.ui

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.munkberries.tikhayapolyanka.R

@Composable fun PrivacySupportScreen(onBack: ()->Unit) {
    val context=LocalContext.current
    val text=remember{context.assets.open("legal/privacy_ru.txt").bufferedReader(Charsets.UTF_8).use{it.readText()}}
    var missingMail by remember{mutableStateOf(false)}
    BackHandler(onBack=onBack)
    Column(Modifier.widthIn(max=620.dp).fillMaxSize().padding(horizontal=20.dp)) {
        Header(stringResource(R.string.privacy_support),onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PaperCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.developer_credit),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.support_address))
                SecondaryButton({
                    try {context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto","munkberries@yandex.ru",null)))}
                    catch(_: ActivityNotFoundException){missingMail=true}
                },modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.contact_support))}
                if(missingMail)Text(stringResource(R.string.support_unavailable),color=Muted)
            }
            Text(text,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
        }
    }
}
