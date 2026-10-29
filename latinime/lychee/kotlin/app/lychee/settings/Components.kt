package app.lychee.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Rows inside a [Group]: each row on its own rounded segment, the segments stacked with a small
 * gap (the group's outer corners round, the inner ones nearly square), like Android's own
 * settings. Each row is given its segment's shape.
 */
class GroupScope internal constructor(private val rows: MutableList<@Composable (Shape) -> Unit>) {
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

/** The shape of row [i] of [n] in a group: round outer corners, small inner ones. */
private fun segment(i: Int, n: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (i == 0) outer else inner, topEnd = if (i == 0) outer else inner,
        bottomStart = if (i == n - 1) outer else inner, bottomEnd = if (i == n - 1) outer else inner,
    )
}

@Composable
fun Group(title: String? = null, build: GroupScope.() -> Unit) {
    val rows = mutableListOf<@Composable (Shape) -> Unit>()
    GroupScope(rows).build()
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = if (title != null) 24.dp else 12.dp, bottom = 4.dp)
            .animateContentSize(),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, row ->
                val shape = segment(i, rows.size)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) { row(shape) }
            }
        }
    }
}

@Composable
private fun RowSurface(shape: Shape, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/**
 * Every row's layout: an icon in a tinted badge when it has one (rows without one start at the
 * edge, no empty space), the title and a short summary, and whatever goes at the end.
 */
@Composable
fun CompactRow(
    @DrawableRes icon: Int?,
    title: String,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    summaryColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null || icon != null) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when {
                    leading != null -> leading()
                    icon != null -> IconBadge(icon)
                }
            }
            Spacer(Modifier.size(16.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun IconBadge(@DrawableRes icon: Int) {
    // a rounded, tinted badge; the three accent families take turns so the list has some colour
    val scheme = MaterialTheme.colorScheme
    val (back, fore) = when (Math.floorMod(icon, 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(back),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon), contentDescription = null,
            tint = fore,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun NavRow(shape: Shape, title: String, summary: String? = null, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    RowSurface(shape, onClick) {
        CompactRow(icon, title, summary, trailing = {
            Icon(
                painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_chevron_right), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        })
    }
}

@Composable
fun SwitchRow(shape: Shape, title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    IconSwitchRow(shape, title, summary, checked, null, onChange)

/** A [SwitchRow] with an icon badge in front. */
@Composable
fun IconSwitchRow(
    shape: Shape, title: String, summary: String?, checked: Boolean,
    @DrawableRes icon: Int?, onChange: (Boolean) -> Unit,
) {
    RowSurface(shape, { onChange(!checked) }) {
        CompactRow(icon, title, summary, trailing = { Switch(checked = checked, onCheckedChange = onChange) })
    }
}

/** A switch row whose item can also move up or down a list (the arrows show when it can). */
@Composable
fun ReorderSwitchRow(
    shape: Shape, title: String, checked: Boolean, @DrawableRes icon: Int?,
    onUp: (() -> Unit)?, onDown: (() -> Unit)?, onChange: (Boolean) -> Unit,
) {
    RowSurface(shape, { onChange(!checked) }) {
        CompactRow(icon, title, trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                    Icon(
                        painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_keyboard_arrow_up),
                        contentDescription = androidx.compose.ui.res.stringResource(com.android.inputmethod.latin.R.string.lychee_move_up),
                    )
                }
                androidx.compose.material3.IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                    Icon(
                        painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_keyboard_arrow_down),
                        contentDescription = androidx.compose.ui.res.stringResource(com.android.inputmethod.latin.R.string.lychee_move_down),
                    )
                }
                Switch(checked = checked, onCheckedChange = onChange)
            }
        })
    }
}

/**
 * A setting with a few choices: its title and the current choice in full; a tap opens the
 * choices right under it (an expanding list, no dialog), and picking one closes it again.
 */
@Composable
fun <T> ChoiceRow(
    shape: Shape, title: String, summary: String? = null, options: List<Pair<T, String>>, selected: T,
    /** An icon for each option (shown before its label), or null for text only. */
    @DrawableRes icons: List<Int>? = null,
    onSelect: (T) -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val current = options.indexOfFirst { it.first == selected }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "turn")
    Column {
        RowSurface(shape, { open = !open }) {
            CompactRow(
                icons?.getOrNull(current), title,
                listOfNotNull(options.getOrNull(current)?.second, summary?.takeIf { open }).joinToString("\n").ifEmpty { null },
                summaryColor = MaterialTheme.colorScheme.primary,
                trailing = {
                    Icon(
                        painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_keyboard_arrow_down), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = turn },
                    )
                },
            )
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                options.forEachIndexed { i, (value, label) ->
                    val chosen = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); open = false }
                            .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icons?.getOrNull(i)?.let { res ->
                            Icon(painterResource(res), contentDescription = null, modifier = Modifier.padding(end = 10.dp).size(18.dp))
                        }
                        Text(
                            label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                        if (chosen) {
                            Icon(
                                painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_check), contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
