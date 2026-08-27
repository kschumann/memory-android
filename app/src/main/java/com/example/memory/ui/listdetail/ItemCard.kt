package com.example.memory.ui.listdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.memory.data.ITEM_TEXT_MAX_LENGTH
import com.example.memory.data.ItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemCard(
    item: ItemEntity,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onCommitEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        if (isEditing) {
            var fieldValue by remember(item.id) {
                mutableStateOf(TextFieldValue(item.text, selection = TextRange(item.text.length)))
            }
            var hasFocusedOnce by remember(item.id) { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            TextField(
                value = fieldValue,
                onValueChange = { if (it.text.length <= ITEM_TEXT_MAX_LENGTH) fieldValue = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hasFocusedOnce = true
                        } else if (hasFocusedOnce) {
                            onCommitEdit(fieldValue.text)
                        }
                    }
            )
        } else {
            Text(
                text = item.text.ifBlank { "Empty note" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartEdit)
                    .padding(12.dp)
            )
        }
    }
}
