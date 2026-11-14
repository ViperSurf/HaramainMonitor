package com.haramain.monitor.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The one control used for EVERY DSP parameter:
 * smooth slider + precision [-]/[+] stepper buttons + numeric readout,
 * double-tap anywhere on the control resets to [defaultValue].
 * [showCenterSnap] adds a snap-to-center button (Stereo Balance).
 */
/**
 * A small (i) button that explains a control or section in one or two
 * plain sentences. Self-contained: owns its dialog.
 */
@Composable
fun InfoTip(title: String, text: String, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }, modifier = modifier.size(28.dp)) {
        Icon(
            Icons.Filled.Info,
            contentDescription = "About $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Got it") } },
        )
    }
}

@Composable
fun PrecisionControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showCenterSnap: Boolean = false,
    enabled: Boolean = true,
    help: String? = null,
) {
    fun set(v: Float) = onValueChange(v.coerceIn(valueRange.start, valueRange.endInclusive))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(defaultValue) {
                detectTapGestures(onDoubleTap = { set(defaultValue) })
            }
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (help != null) InfoTip(label, help)
            }
            Text(
                text = format(value),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepperButton(enabled = enabled && value > valueRange.start, onClick = { set(value - step) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Slider(
                value = value,
                onValueChange = { set(it) },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            if (showCenterSnap) {
                StepperButton(enabled = enabled && value != 0f, onClick = { set(0f) }) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Snap to center")
                }
            }
            StepperButton(enabled = enabled && value < valueRange.endInclusive, onClick = { set(value + step) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@Composable
private fun StepperButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
    ) { content() }
}
