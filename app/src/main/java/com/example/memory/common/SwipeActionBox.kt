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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.memory.R
import com.example.memory.ui.theme.ArchiveContainer
import com.example.memory.ui.theme.OnArchiveContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LOCK_FRACTION = 0.25f

// Swipe left always reveals Delete (with a confirm dialog). Swipe right reveals Archive - no
// confirm dialog - only when onArchive is supplied; otherwise right-drag is clamped to 0, so
// callers that only want delete (e.g. HomeScreen's list cards) behave exactly as before.
@Composable
fun SwipeActionBox(
    key: Any,
    onDelete: () -> Unit,
    confirmMessage: String,
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var lockedDirection by remember(key) { mutableStateOf(0) } // -1 = delete locked, 1 = archive locked, 0 = unlocked
    var showConfirmDialog by remember(key) { mutableStateOf(false) }
    val offsetAnim = remember(key) { Animatable(0f) }

    BoxWithConstraints(modifier = modifier) {
        val thresholdPx = with(density) { maxWidth.toPx() } * LOCK_FRACTION

        Box(
            modifier = Modifier
                .matchParentSize()
                // Card backgrounds are translucent, so without this the backdrop would show
                // through at rest. Fade it in with drag progress instead of always drawing it.
                .graphicsLayer { alpha = (kotlin.math.abs(offsetAnim.value) / thresholdPx).coerceIn(0f, 1f) }
                .background(
                    if (offsetAnim.value >= 0f) ArchiveContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = if (offsetAnim.value >= 0f) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (offsetAnim.value >= 0f && onArchive != null) {
                IconButton(
                    enabled = lockedDirection == 1,
                    onClick = {
                        onArchive()
                        scope.launch { offsetAnim.animateTo(0f) }
                        lockedDirection = 0
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = "Archive",
                        tint = OnArchiveContainer
                    )
                }
            } else {
                IconButton(
                    enabled = lockedDirection == -1,
                    onClick = { showConfirmDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                .pointerInput(key, thresholdPx, onArchive != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when (lockedDirection) {
                                    -1 -> {
                                        if (offsetAnim.value > -thresholdPx / 2f) {
                                            lockedDirection = 0
                                            offsetAnim.animateTo(0f)
                                        } else {
                                            offsetAnim.animateTo(-thresholdPx)
                                        }
                                    }
                                    1 -> {
                                        if (offsetAnim.value < thresholdPx / 2f) {
                                            lockedDirection = 0
                                            offsetAnim.animateTo(0f)
                                        } else {
                                            offsetAnim.animateTo(thresholdPx)
                                        }
                                    }
                                    else -> offsetAnim.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                if (lockedDirection == 0) {
                                    val maxOffset = if (onArchive != null) thresholdPx else 0f
                                    val newOffset = (offsetAnim.value + dragAmount).coerceIn(-thresholdPx, maxOffset)
                                    offsetAnim.snapTo(newOffset)
                                    if (newOffset <= -thresholdPx) {
                                        lockedDirection = -1
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (onArchive != null && newOffset >= thresholdPx) {
                                        lockedDirection = 1
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                } else if (lockedDirection == -1 && dragAmount > 0f) {
                                    offsetAnim.snapTo((offsetAnim.value + dragAmount).coerceAtMost(0f))
                                } else if (lockedDirection == 1 && dragAmount < 0f) {
                                    offsetAnim.snapTo((offsetAnim.value + dragAmount).coerceAtLeast(0f))
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
