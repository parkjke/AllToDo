package com.example.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alltodo.data.TodoItem
import com.example.alltodo.ui.theme.AllToDoBlack
import com.example.alltodo.ui.theme.AllToDoBlue
import com.example.alltodo.ui.theme.AllToDoGreen
import com.example.alltodo.ui.theme.AllToDoRed

@Composable
fun TodoListContent(
    todoItems: List<TodoItem>,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (TodoItem) -> Unit,
    onDeleteTodo: (TodoItem) -> Unit
) {
    var newTodoText by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "To Do List",
                color = Color.DarkGray, 
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp) 
            )
            
            IconButton(onClick = { isAdding = !isAdding }) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.DarkGray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add Section
        if (isAdding) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = newTodoText,
                    onValueChange = { newTodoText = it },
                    placeholder = { Text("New Task...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newTodoText.isNotBlank()) {
                            onAddTodo(newTodoText)
                            newTodoText = ""
                            isAdding = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AllToDoGreen)
                ) {
                    Text("Add")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(400.dp) // Fixed height for scrolling within sheet
        ) {
            items(todoItems) { item ->
                TodoItemRow(item = item, onToggle = { onToggleTodo(item) })
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun TodoItemRow(
    item: TodoItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val checkboxColor = if (item.source == "external") AllToDoRed else AllToDoBlack

        Checkbox(
            checked = item.completed,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = checkboxColor,
                uncheckedColor = checkboxColor,
                checkmarkColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Text(
                text = item.text,
                fontSize = 16.sp,
                color = if (item.completed) Color.Gray else AllToDoBlack,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null
            )
            // Optional: Show source or location icon
        }
    }
}
