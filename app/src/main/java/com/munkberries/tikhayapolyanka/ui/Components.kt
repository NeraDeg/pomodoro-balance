package com.munkberries.tikhayapolyanka.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource

@Composable fun PaperCard(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = CardPaper, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape=RoundedCornerShape(16.dp), color=color, contentColor=Ink,
        border=BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
    }
}
@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: Int? = null) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min=52.dp), enabled=enabled,
        shape=RoundedCornerShape(16.dp), contentPadding=PaddingValues(horizontal=20.dp, vertical=14.dp)) {
        if(leadingIcon!=null) {
            Icon(painterResource(leadingIcon),null,Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable fun SecondaryButton(onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    content: @Composable RowScope.()->Unit) {
    OutlinedButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,Line),colors=ButtonDefaults.outlinedButtonColors(contentColor=Ink),content=content)
}
