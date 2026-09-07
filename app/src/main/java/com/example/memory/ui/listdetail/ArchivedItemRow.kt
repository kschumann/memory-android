package com.example.memory.ui.listdetail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.memory.data.ItemEntity

// Archived notes sit directly on the screen background (no card), in light/muted text with a
// strikethrough, to read as visually "put away" next to the active cards above. Long-press
// restores - there's no swipe here and no confirm dialog, matching the spec for undoing an
// archive.
@Composable
fun ArchivedItemRow(
    item: ItemEntity,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = item.text.ifBlank { "Empty note" },
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = TextDecoration.LineThrough,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(item.id) {
                detectTapGestures(onLongPress = { onRestore() })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
