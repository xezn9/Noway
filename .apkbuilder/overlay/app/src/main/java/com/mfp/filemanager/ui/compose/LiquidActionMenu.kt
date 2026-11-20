package com.mfp.filemanager.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.mfp.filemanager.R

/**
 * The row of file actions (Copy / Move / Share / Rename / Info / Delete) made of the real
 * Backdrop [LiquidButton] (same component as the catalog one: lens + vibrancy + blur + press
 * "squish" and highlight).
 *
 * Every button owns a tiny hidden colourful "scene" that the glass refracts, so it keeps its liquid
 * look on the flat list background (a glass element must not sit inside the layer it samples).
 */
@Composable
fun LiquidActionMenu(
    dark: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelColor = if (dark) Color.White.copy(0.88f) else Color(0xFF1B1B1F)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        LiquidAction("Copy", R.drawable.ic_content_copy_24, Color(0xFF4DA3FF), labelColor, onCopy)
        LiquidAction("Move", R.drawable.ic_move_to_24, Color(0xFF9B6BFF), labelColor, onMove)
        LiquidAction("Share", R.drawable.ic_share_24, Color(0xFF34C759), labelColor, onShare)
        LiquidAction("Rename", R.drawable.ic_edit_24, Color(0xFFFFB020), labelColor, onRename)
        LiquidAction("Info", R.drawable.ic_info_24, Color(0xFF14C8C8), labelColor, onInfo)
        LiquidAction("Delete", R.drawable.ic_delete, Color(0xFFFF3B30), Color(0xFFFF5252), onDelete)
    }
}

@Composable
private fun LiquidAction(
    label: String,
    iconRes: Int,
    tint: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    val scene = rememberLayerBackdrop()

    Column(
        Modifier.size(width = 60.dp, height = 82.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            // hidden scene the glass looks at (bigger than the button so the lens has content to bend)
            Box(
                Modifier
                    .requiredSize(96.dp)
                    .alpha(0f)
                    .layerBackdrop(scene)
                    .drawBehind {
                        drawRect(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(0.85f).compositeOver(tint),
                                    tint,
                                    Color.Black.copy(0.35f).compositeOver(tint)
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            )
                        )
                        drawCircle(
                            Color.White.copy(0.55f),
                            radius = size.minDimension * 0.30f,
                            center = Offset(size.width * 0.28f, size.height * 0.24f)
                        )
                    }
            )
            LiquidButton(
                onClick = onClick,
                backdrop = scene,
                modifier = Modifier.size(56.dp),
                tint = tint
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
