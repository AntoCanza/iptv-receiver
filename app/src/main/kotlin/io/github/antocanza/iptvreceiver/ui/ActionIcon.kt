package io.github.antocanza.iptvreceiver.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** One touch target, translated name and long-press help for every utility action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionIcon(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier,
    selected: Boolean? = null, stateLabel: String? = null, onVideo: Boolean = false, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = modifier.size(48.dp).then(
            if (onVideo) Modifier.background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small) else Modifier
        ).semantics {
            selected?.let { this.selected = it }
            stateLabel?.let { stateDescription = it }
        }) {
            Icon(painterResource(icon), contentDescription = label,
                tint = if (selected == true) MaterialTheme.colorScheme.primary else if (onVideo) Color.White else LocalContentColor.current)
        }
    }
}
