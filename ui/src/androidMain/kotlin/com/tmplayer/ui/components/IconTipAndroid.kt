package com.tmplayer.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tmplayer.data.DeviceForm

/**
 * The name of an icon-only control. On a television, a [FocusTip] while the remote is on it; on
 * a phone, Material's tooltip on a long press, the way every phone app names its icons. The
 * phone's has no key to show, so [shortcut] is the desktop's alone.
 *
 * [modifier] is the control's place in its parent: the tip's box is what the parent lays out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTip(
    label: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    side: TipSide = TipSide.Above,
    content: @Composable () -> Unit,
) {
    if (deviceForm() == DeviceForm.Tv) {
        FocusTipBox(label, modifier, side = side, content = content)
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (side == TipSide.Below) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above,
        ),
        tooltip = { TipBubble(label) },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
