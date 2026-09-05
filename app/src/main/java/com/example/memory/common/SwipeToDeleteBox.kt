package com.example.memory.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LOCK_FRACTION = 0.25f

@Composable
fun SwipeToDeleteBox(
    key: Any,
    onDelete: () -> Unit,
    confirmMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var locked by remember(key) { mutableStateOf(false) }
    var showConfirmDialog by remember(key) { mutableStateOf(false) }
    val offsetAnim = remember(key) { Animatable(0f) }

    BoxWithConstraints(modifier = modifier) {
        val thresholdPx = with(density) { maxWidth.toPx() } * LOCK_FRACTION

        Box(
            modifier = Modifier
                .matchParentSize()
                // Card backgrounds are translucent, so without this the delete backdrop would
                // show through at rest. Fade it in with drag progress instead of always drawing
                // it at full strength.
                .graphicsLayer { alpha = (-offsetAnim.value / thresholdPx).coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                enabled = locked,
                onClick = { showConfirmDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                .pointerInput(key, thresholdPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (locked) {
                                    if (offsetAnim.value > -thresholdPx / 2f) {
                                        locked = false
                                        offsetAnim.animateTo(0f)
                                    } else {
                                        offsetAnim.animateTo(-thresholdPx)
                                    }
                                } else {
                                    offsetAnim.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                if (!locked) {
                                    val newOffset = (offsetAnim.value + dragAmount).coerceIn(-thresholdPx, 0f)
                                    offsetAnim.snapTo(newOffset)
                                    if (newOffset <= -thresholdPx) {
                                        locked = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                } else if (dragAmount > 0f) {
                                    offsetAnim.snapTo((offsetAnim.value + dragAmount).coerceAtMost(0f))
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete?") },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
