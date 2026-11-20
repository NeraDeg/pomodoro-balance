package com.munkberries.tikhayapolyanka.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.munkberries.tikhayapolyanka.R

/** Separate transparent illustration layers; the title stays native and can increase the banner height. */
@Composable fun ForestBanner(title: String,@DrawableRes illustration: Int,animalOnLeft: Boolean=false) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        color=if(animalOnLeft)Paper else CardPaper,contentColor=Ink,
        border=if(animalOnLeft)null else BorderStroke(1.dp,Line)) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=152.dp)) {
            val animalWidth=minOf(maxWidth*.43f,154.dp)
            val groundHeight=maxWidth/3f
            val animalAlignment=if(animalOnLeft)Alignment.BottomStart else Alignment.BottomEnd
            // The ground, herbs and animal are not a flattened picture of the interface.
            Image(painterResource(R.drawable.tp_glade_day),null,
                Modifier.fillMaxWidth().height(groundHeight).align(Alignment.BottomCenter),
                contentScale=ContentScale.FillWidth,alignment=Alignment.BottomCenter)
            Image(painterResource(R.drawable.tp_folk_corner),null,
                Modifier.size(58.dp).align(Alignment.BottomEnd),contentScale=ContentScale.Fit)
            Image(painterResource(R.drawable.tp_daisies),null,
                Modifier.size(52.dp).align(Alignment.BottomStart),contentScale=ContentScale.Fit)
            Image(painterResource(R.drawable.tp_wild_strawberry),null,
                Modifier.size(46.dp).align(Alignment.BottomCenter),contentScale=ContentScale.Fit)
            Image(painterResource(illustration),null,
                Modifier.width(animalWidth).height(142.dp).align(animalAlignment)
                    .scale(scaleX=if(illustration==R.drawable.tp_squirrel_notes)-1f else 1f,scaleY=1f),contentScale=ContentScale.Fit)
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=16.dp,top=18.dp,bottom=groundHeight+6.dp),
                verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(animalOnLeft)Spacer(Modifier.width(animalWidth-18.dp))
                Text(title,Modifier.weight(1f),style=if(animalOnLeft)MaterialTheme.typography.bodyLarge else
                    MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
                if(!animalOnLeft)Spacer(Modifier.width(animalWidth-16.dp))
            }
        }
    }
}
@Composable fun CreateAction(label: String,onClick: ()->Unit) {
    FilledIconButton(onClick,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Sage,contentColor=Color.White)) {
        Icon(painterResource(R.drawable.tp_ic_plus),label,Modifier.size(24.dp))
    }
}

