package app.lychee.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Rows inside a [Group]: a plain Material 3 list on the page itself, no boxes, the rows set
 * apart by subtle inset dividers.
 */
class GroupScope internal constructor(private val rows: MutableList<@Composable (Shape) -> Unit>) {
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

/** The page behind the lists. */
@Composable
fun pageColor(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface

@Composable
fun Group(title: String? = null, build: GroupScope.() -> Unit) {
    val rows = mutableListOf<@Composable (Shape) -> Unit>()
    GroupScope(rows).build()
    if (rows.isEmpty()) return
    Column(
        Modifier
            .padding(top = if (title != null) 16.dp else 4.dp, bottom = 4.dp)
            .animateContentSize(),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            row(RectangleShape)
        }
    }
}

/**
 * A raised card for what isn't a list (setup, notices, messages): the page's own colour (or
 * [color]) lifted by a soft shadow, never a flat grey block.
 */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { content() }
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
            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
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

/** A submenu: a setting's choices on a page of their own (see [ChoiceRow]). */
class Submenu(
    val title: String,
    val summary: String?,
    val options: List<String>,
    @DrawableRes val icons: List<Int>?,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

/** Opens a [Submenu] over the current page (provided by the settings screen). */
val LocalSubmenu = androidx.compose.runtime.staticCompositionLocalOf<(Submenu) -> Unit> { {} }

/**
 * A setting with a few choices. Two or three short ones are Material 3 segmented buttons right
 * in the row; more (or longer) ones show the current choice under the title, and a tap opens a
 * dialog listing them, like the phone's own settings.
 */
@Composable
fun <T> ChoiceRow(
    shape: Shape, title: String, summary: String? = null, options: List<Pair<T, String>>, selected: T,
    /** An icon for each option (shown before its label), or null for text only. */
    @DrawableRes icons: List<Int>? = null,
    onSelect: (T) -> Unit,
) {
    // only when every label fits its button whole (no tick icon, which takes room): two short
    // ones, or three very short ones; anything longer is a dialog
    val longest = options.maxOfOrNull { it.second.length } ?: 0
    val segmented = icons == null && ((options.size == 2 && longest <= 11) || (options.size == 3 && longest <= 7))
    if (segmented) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {},
                        label = {
                            Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                        },
                    )
                }
            }
        }
        return
    }
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val current = options.indexOfFirst { it.first == selected }
    RowSurface(shape, { open = true }) {
        CompactRow(
            icons?.getOrNull(current), title, options.getOrNull(current)?.second ?: summary,
            summaryColor = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    if (summary != null) {
                        Text(
                            summary, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    options.forEachIndexed { i, (value, label) ->
                        val chosen = value == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelect(value)
                                    open = false
                                }
                                .heightIn(min = 52.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = chosen, onClick = null,
                                modifier = Modifier.padding(horizontal = 12.dp))
                            icons?.getOrNull(i)?.let { res ->
                                Icon(painterResource(res), contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * A submenu's page: what the setting does, then its choices as a list of radio buttons with
 * dividers; picking one ticks it and goes back.
 */
@Composable
fun SubmenuPage(menu: Submenu, onDone: () -> Unit) {
    var picked by remember(menu) { androidx.compose.runtime.mutableIntStateOf(menu.selected) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (menu.summary != null) {
        Text(
            menu.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
    }
    Group {
        menu.options.forEachIndexed { i, label ->
            row {
                RowSurface(it, {
                    picked = i
                    menu.onSelect(i)
                    // the tick shows for a moment before going back
                    scope.launch {
                        kotlinx.coroutines.delay(180)
                        onDone()
                    }
                }) {
                    CompactRow(
                        menu.icons?.getOrNull(i), label,
                        trailing = { androidx.compose.material3.RadioButton(selected = picked == i, onClick = null) },
                    )
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    shape: Shape, title: String, value: Int, range: IntRange, step: Int = 1,
    format: (Int) -> String = { it.toString() },
    /** Called as the slider moves (a live preview); [onChange] when it is let go. */
    onSlide: ((Int) -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    RowSurface(shape, null) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(format(current.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = current,
                onValueChange = {
                    current = it
                    onSlide?.invoke(it.roundToInt())
                },
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
        Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
