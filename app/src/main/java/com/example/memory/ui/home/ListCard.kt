package com.example.memory.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import com.example.memory.data.LIST_NAME_MAX_LENGTH
import com.example.memory.data.ListEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListCard(
    list: ListEntity,
    isEditing: Boolean,
    onOpen: () -> Unit,
    onStartRename: () -> Unit,
    onCommitEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        if (isEditing) {
            var fieldValue by remember(list.id) {
                mutableStateOf(TextFieldValue(list.name, selection = TextRange(list.name.length)))
            }
            var hasFocusedOnce by remember(list.id) { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            TextField(
                value = fieldValue,
                onValueChange = { if (it.text.length <= LIST_NAME_MAX_LENGTH) fieldValue = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                colors = TextFieldDefaults.colors(),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = onStartRename)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = list.name.ifBlank { "Untitled" }, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = dragHandleModifier.padding(12.dp)
                )
            }
        }
    }
}
