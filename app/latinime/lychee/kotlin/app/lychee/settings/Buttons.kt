package app.lychee.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The settings' buttons: Material 3's own (pill shapes, tonal colours, no shadows). These names
 * stand in for Material 3's in this package so every page uses the same ones.
 */

/** A filled button (the main action). */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled,
    contentPadding = contentPadding, content = content)

/** An outlined button (a secondary action). */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled,
    contentPadding = contentPadding, content = content)

/** A tonal button (the secondary container colour). */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled,
    contentPadding = contentPadding, content = content)

/** A text button (dialogs' actions, small row actions). */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled,
    contentPadding = contentPadding, content = content)
