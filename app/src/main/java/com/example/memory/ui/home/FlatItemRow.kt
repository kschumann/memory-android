package com.example.memory.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.memory.data.ItemWithListName

// Column 1: the sub-item's own text. Column 2: the parent list it belongs to. Dragging this
// row only reorders ItemEntity.globalSortOrder (see HomeViewModel.onReorderFlatItems) - it never
// touches sortOrder, so each item's position within its own list is unaffected.
@Composable
fun FlatItemRow(
    entry: ItemWithListName,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.outline,
                modifier = dragHandleModifier.padding(12.dp)
            )
            Text(
                text = entry.item.text.ifBlank { "Empty note" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Text(
                text = entry.listName.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}
