package com.rurnima.purnima

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChatNight = Color(0xFF080610)
private val ChatViolet = Color(0xFF9B5CFF)
private val ChatMuted = Color(0xFFAAA2C0)

/**
 * Purnima conversation screen UI.
 * Messages are held in Compose state for this screen session; AI service wiring is separate.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hello! কীভাবে সাহায্য করতে পারি?",
                fromUser = false
            )
        )
    }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ChatNight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF10091F), ChatNight, Color(0xFF05040A))
                    )
                )
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Header intentionally contains only the title: no avatar, name, or header card.
            Text(
                text = "Chat",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Write a message…", color = ChatMuted, fontSize = 14.sp)
                    },
                    shape = RoundedCornerShape(26.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                messages.add(ChatMessage(text, fromUser = true))
                                draft = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ChatViolet,
                        unfocusedBorderColor = Color(0xFF493363),
                        cursorColor = ChatViolet,
                        focusedContainerColor = Color(0xFF100B1B),
                        unfocusedContainerColor = Color(0xFF100B1B)
                    )
                )

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            messages.add(ChatMessage(text, fromUser = true))
                            draft = ""
                        }
                    },
                    modifier = Modifier
                        .height(54.dp)
                        .border(1.dp, ChatViolet, RoundedCornerShape(22.dp))
                        .background(Color(0xFF24103F), RoundedCornerShape(22.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("Send  ➤", color = Color(0xFFE5D2FF), fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF35234F), RoundedCornerShape(22.dp))
                    .background(Color(0xFF100B1B), RoundedCornerShape(22.dp))
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatNavItem("⌂", "Home", onNavigate)
                ChatNavItem("◌", "Chat", onNavigate, selected = true)
                ChatNavItem("◎", "Voice", onNavigate)
                ChatNavItem("◷", "History", onNavigate)
                ChatNavItem("⚙", "Settings", onNavigate)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (message.fromUser) Color(0xFF542A91) else Color(0xFF171121),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.fromUser) 20.dp else 5.dp,
                        bottomEnd = if (message.fromUser) 5.dp else 20.dp
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (message.fromUser) Color(0xFF8E5CE0) else Color(0xFF3A2A50),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.fromUser) 20.dp else 5.dp,
                        bottomEnd = if (message.fromUser) 5.dp else 20.dp
                    )
                )
                .padding(horizontal = 15.dp, vertical = 11.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun ChatNavItem(
    symbol: String,
    label: String,
    onNavigate: (String) -> Unit,
    selected: Boolean = false
) {
    TextButton(
        onClick = { onNavigate(label) },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                symbol,
                color = if (selected) Color(0xFFC69AFF) else ChatMuted,
                fontSize = 21.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = if (selected) Color(0xFFC69AFF) else ChatMuted,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private data class ChatMessage(
    val text: String,
    val fromUser: Boolean
)
