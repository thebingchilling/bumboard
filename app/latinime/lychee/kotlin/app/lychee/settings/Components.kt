@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lychee.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The settings' building blocks, in Material 2's style (like LatinIME's own preference screens):
 * plain full-width lists on the page's colour, each group under a small heading in the accent
 * colour with a thin divider between groups, icons in the list's grey on a 72 dp text keyline,
 * Material 2 switches and sliders, and a setting with several choices opening a dialog of radio
 * buttons.
 */

/** Rows inside a [Group]. */
class GroupScope internal constructor(private val rows: MutableList<@Composable (Shape) -> Unit>) {
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

/** The page behind the lists. */
@Composable
fun pageColor(): Color = MaterialTheme.colorScheme.surface

/** The shape of a row (rectangular, as Material 2's lists are). */
val CardShape: Shape = RectangleShape

/** Material 2's card and dialog corners. */
val M2Corner = RoundedCornerShape(4.dp)

/**
 * Counts the groups on a page, so the first has no divider above it (the settings screen gives
 * every page a fresh one).
 */
class GroupCounter {
    var count = 0
}

val LocalGroupCounter = androidx.compose.runtime.staticCompositionLocalOf { GroupCounter() }

/** A thin full-width line between parts of a page. */
@Composable
fun SectionDivider() = HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun Group(title: String? = null, build: GroupScope.() -> Unit) {
    val rows = mutableListOf<@Composable (Shape) -> Unit>()
    GroupScope(rows).build()
    if (rows.isEmpty()) return
    val counter = LocalGroupCounter.current
    val index = remember(counter) { counter.count++ }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (index > 0) SectionDivider()
        if (title != null) GroupHeader(title)
        else if (index > 0) Spacer(Modifier.height(8.dp))
        rows.forEach { row -> row(CardShape) }
    }
}

/** A group's heading: small, in the accent colour, on the rows' text keyline. */
@Composable
fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** A short explanation at the top of a page or above a list, in the list's grey. */
@Composable
fun PageNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/**
 * A Material 2 card for what isn't a list (setup, notices, messages): 4 dp corners, lifted by a
 * soft shadow. ([shape] is kept for callers; every card has Material 2's corners.)
 */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    @Suppress("UNUSED_PARAMETER") shape: Shape = M2Corner,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = M2Corner,
        color = color,
        contentColor = contentColor,
        shadowElevation = 2.dp,
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/**
 * Every row's layout, as in Material 2's lists: an icon (24 dp, in the list's grey) or a control
 * at the start, the title and a short summary on the 72 dp keyline (16 dp without either), and
 * whatever goes at the end.
 */
@Composable
fun CompactRow(
    @DrawableRes icon: Int?,
    title: String,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    summaryColor: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (summary != null) 72.dp else 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
                Spacer(Modifier.size(16.dp))
            }
            icon != null -> {
                IconBadge(icon)
                Spacer(Modifier.size(32.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = if (trailing != null) 16.dp else 0.dp),
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

/** A list icon: 24 dp, in the list's grey (Material 2 has no badge behind it). */
@Composable
fun IconBadge(@DrawableRes icon: Int) {
    Icon(
        painterResource(icon), contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
fun NavRow(shape: Shape, title: String, summary: String? = null, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    RowSurface(shape, onClick) { CompactRow(icon, title, summary) }
}

/**
 * Material 2's switch: a thin rounded track with a round raised thumb, in the accent colour
 * when on.
 */
@Composable
fun M2Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switch")
    val thumbOff = if (dark) Color(0xFFBDBDBD) else Color(0xFFFAFAFA)
    val trackOff = scheme.onSurface.copy(alpha = 0.38f)
    val trackOn = scheme.primary.copy(alpha = 0.54f)
    val thumb = androidx.compose.ui.graphics.lerp(thumbOff, scheme.primary, on)
    val track = androidx.compose.ui.graphics.lerp(trackOff, trackOn, on)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked, interactionSource = interaction,
                    indication = ripple(bounded = false, radius = 20.dp),
                    enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 40.dp, height = 22.dp)) {
            val alpha = if (enabled) 1f else 0.5f
            val r = 10.dp.toPx()
            val trackH = 14.dp.toPx()
            val inset = 3.dp.toPx()
            drawRoundRect(
                track.copy(alpha = track.alpha * alpha),
                topLeft = Offset(inset, (size.height - trackH) / 2),
                size = Size(size.width - inset * 2, trackH),
                cornerRadius = CornerRadius(trackH / 2, trackH / 2),
            )
            val cx = r + (size.width - r * 2) * on
            val cy = size.height / 2
            // the thumb's shadow, then the thumb
            drawCircle(Color.Black.copy(alpha = 0.22f * alpha), r + 0.6.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
            drawCircle(thumb.copy(alpha = if (enabled) 1f else 0.85f), r, Offset(cx, cy))
        }
    }
}

@Composable
fun SwitchRow(shape: Shape, title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    IconSwitchRow(shape, title, summary, checked, null, onChange)

/** A [SwitchRow] with an icon in front. */
@Composable
fun IconSwitchRow(
    shape: Shape, title: String, summary: String?, checked: Boolean,
    @DrawableRes icon: Int?, onChange: (Boolean) -> Unit,
) {
    RowSurface(shape, { onChange(!checked) }) {
        CompactRow(icon, title, summary, trailing = { M2Switch(checked = checked, onCheckedChange = onChange) })
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
                M2Switch(checked = checked, onCheckedChange = onChange)
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
 * A setting with a few choices: the title with the current choice under it; a tap drops a
 * Material 2 menu down from the row (growing from it), the current choice ticked, and picking one
 * closes it.
 */
@Composable
fun <T> ChoiceRow(
    shape: Shape, title: String, summary: String? = null, options: List<Pair<T, String>>, selected: T,
    /** An icon for each option (shown before its label), or null for text only. */
    @DrawableRes icons: List<Int>? = null,
    /** The menu stays open after a choice (to try each one, as with voices), closed by Done. */
    keepOpen: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val current = options.indexOfFirst { it.first == selected }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        RowSurface(shape, { open = true }) {
            CompactRow(icons?.getOrNull(current), title, options.getOrNull(current)?.second ?: summary)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            // under the row's text, as a menu dropped from it
            offset = androidx.compose.ui.unit.DpOffset(16.dp, 0.dp),
            shape = M2Corner,
            containerColor = colors.surfaceContainer,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(min = 224.dp, max = 320.dp),
        ) {
            if (summary != null) {
                Text(
                    summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            options.forEachIndexed { i, (value, label) ->
                val chosen = value == selected
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            color = if (chosen) colors.primary else colors.onSurface)
                    },
                    onClick = {
                        onSelect(value)
                        if (!keepOpen) open = false
                    },
                    leadingIcon = icons?.getOrNull(i)?.let { res ->
                        { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(20.dp)) }
                    },
                    trailingIcon = if (chosen) {
                        {
                            Icon(painterResource(com.android.inputmethod.latin.R.drawable.ic_sym_check), contentDescription = null,
                                tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                    } else null,
                    modifier = Modifier.background(if (chosen) colors.primary.copy(alpha = 0.08f) else Color.Transparent),
                )
            }
            if (keepOpen) {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { open = false }) {
                        Text(androidx.compose.ui.res.stringResource(com.android.inputmethod.latin.R.string.lychee_addons_done))
                    }
                }
            }
        }
    }
}

/**
 * A submenu's page: what the setting does, then its choices as a list of radio buttons; picking
 * one ticks it and goes back.
 */
@Composable
fun SubmenuPage(menu: Submenu, onDone: () -> Unit) {
    var picked by remember(menu) { androidx.compose.runtime.mutableIntStateOf(menu.selected) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (menu.summary != null) PageNote(menu.summary)
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
                        null, label,
                        leading = { androidx.compose.material3.RadioButton(selected = picked == i, onClick = null) },
                    )
                }
            }
        }
    }
}

/**
 * Material 2's slider, drawn here: a thin track, the part before the thumb in the accent
 * colour, a round raised thumb; tapped or dragged anywhere along it, snapping to [steps].
 */
@Composable
fun M2Slider(
    value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int,
    onValueChangeFinished: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val latestChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val latestFinished by androidx.compose.runtime.rememberUpdatedState(onValueChangeFinished)
    // the value at x across a track of width w (the thumb's radius kept clear at each end)
    fun valueAt(x: Float, w: Float, inset: Float): Float {
        val f = ((x - inset) / (w - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val snapped = if (steps > 0) {
            val n = steps + 1
            kotlin.math.round(f * n) / n
        } else f
        return range.start + snapped * span
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(range, steps) {
                val inset = 10.dp.toPx()
                detectTapGestures { pos ->
                    latestChange(valueAt(pos.x, size.width.toFloat(), inset))
                    latestFinished?.invoke()
                }
            }
            .pointerInput(range, steps) {
                val inset = 10.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { pos -> latestChange(valueAt(pos.x, size.width.toFloat(), inset)) },
                    onDragEnd = { latestFinished?.invoke() },
                    onDragCancel = { latestFinished?.invoke() },
                ) { change, _ ->
                    change.consume()
                    latestChange(valueAt(change.position.x, size.width.toFloat(), inset))
                }
            },
    ) {
        val r = 10.dp.toPx()
        val y = size.height / 2
        val track = 4.dp.toPx()
        val x = r + (size.width - r * 2) * fraction
        drawLine(primary.copy(alpha = 0.24f), Offset(r, y), Offset(size.width - r, y), track, StrokeCap.Round)
        drawLine(primary, Offset(r, y), Offset(x, y), track, StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.2f), r + 0.6.dp.toPx(), Offset(x, y + 1.dp.toPx()))
        drawCircle(primary, r, Offset(x, y))
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(format(current.roundToInt()), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            M2Slider(
                value = current,
                onValueChange = {
                    current = it
                    onSlide?.invoke(it.roundToInt())
                },
                onValueChangeFinished = { onChange(current.roundToInt()) },
                range = range.first.toFloat()..range.last.toFloat(),
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
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
