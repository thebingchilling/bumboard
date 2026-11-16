package app.lychee.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * Material 2's buttons for the settings (these names stand in for Material 3's in this package):
 * 4 dp corners, labels in medium with wide letter spacing; contained buttons raised by a shadow,
 * outlined ones with a thin grey border, text buttons flat in the accent colour. Material 2 has
 * no tonal button: FilledTonalButton is the outlined one.
 */

private val Padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

private fun Label(content: @Composable RowScope.() -> Unit): @Composable RowScope.() -> Unit = {
    ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
}

/** A contained button (the main action): the accent colour, raised. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = Padding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = M2Corner,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 8.dp, disabledElevation = 0.dp),
    contentPadding = contentPadding,
    content = Label(content),
)

/** An outlined button (a secondary action). */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = Padding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = M2Corner,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    contentPadding = contentPadding,
    content = Label(content),
)

/** Material 2 has no tonal button: the outlined one. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = Padding,
    content: @Composable RowScope.() -> Unit,
) = OutlinedButton(onClick, modifier, enabled, contentPadding, content)

/** A text button: flat, in the accent colour (dialogs' actions, small row actions). */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = M2Corner,
    contentPadding = contentPadding,
    content = Label(content),
)
