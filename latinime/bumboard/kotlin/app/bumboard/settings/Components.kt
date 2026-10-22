package app.bumboard.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Rows inside a [Group]: the first and last get large corners, the middle ones small (M3 Expressive lists). */
class GroupScope internal constructor(private val rows: MutableList<@Composable (Shape) -> Unit>) {
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

@Composable
fun Group(title: String? = null, build: GroupScope.() -> Unit) {
    val rows = mutableListOf<@Composable (Shape) -> Unit>()
    GroupScope(rows).build()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, row ->
                val big = 24.dp
                val small = 4.dp
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) big else small,
                    topEnd = if (i == 0) big else small,
                    bottomStart = if (i == rows.lastIndex) big else small,
                    bottomEnd = if (i == rows.lastIndex) big else small,
                )
                row(shape)
            }
        }
    }
}

@Composable
private fun RowSurface(shape: Shape, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun IconBadge(@DrawableRes icon: Int) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun NavRow(shape: Shape, title: String, summary: String? = null, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    RowSurface(shape, onClick) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null) {
                { Text(summary) }
            } else null,
            leadingContent = if (icon != null) {
                { IconBadge(icon) }
            } else null,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
fun SwitchRow(shape: Shape, title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowSurface(shape, { onChange(!checked) }) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null) {
                { Text(summary) }
            } else null,
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
fun <T> ChoiceRow(shape: Shape, title: String, summary: String? = null, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    RowSurface(shape, null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    shape: Shape, title: String, value: Int, range: IntRange, step: Int = 1,
    format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    RowSurface(shape, null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(format(current.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = current,
                onValueChange = { current = it },
                onValueChangeFinished = { onChange(current.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
fun TextRow(shape: Shape, title: String, body: String) {
    RowSurface(shape, null) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
