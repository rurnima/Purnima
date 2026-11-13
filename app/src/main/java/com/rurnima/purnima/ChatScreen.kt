package com.rurnima.purnima

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChatNight = Color(0xFF05030B)
private val ChatViolet = Color(0xFFB22CFF)
private val ChatBright = Color(0xFFE5B8FF)
private val ChatMuted = Color(0xFFBDB0D2)

@Composable
fun ChatScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit = {}) {
    val messages = remember {
        mutableStateListOf(ChatMessage("Hello! 💕\nI'm always here for you.\nWhat do you want to know?", false, "9:41 PM"))
    }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            messages.add(ChatMessage(text, true, "9:42 PM"))
            draft = ""
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = ChatNight) {
        Box(Modifier.fillMaxSize()) {
            GalaxyBackdrop(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(78.dp)
                        .border(1.dp, Brush.verticalGradient(listOf(ChatBright, Color(0x663F1475))), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF160824), Color(0xAA080411))), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Chat", color = Color.White, fontSize = 36.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold,
                        modifier = Modifier.shadow(12.dp, ambientColor = ChatViolet, spotColor = ChatViolet))
                }

                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f)
                        .border(1.dp, Color(0x883F1475), RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message -> ChatMessageRow(message) }
                }

                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { draft += "😊" },
                        modifier = Modifier.size(44.dp).border(1.dp, Color(0xFF8535D8), CircleShape).background(Color(0xFF13091F), CircleShape),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("☺", color = ChatBright, fontSize = 24.sp) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…", color = ChatMuted, fontSize = 14.sp) },
                        shape = RoundedCornerShape(28.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = ChatViolet, unfocusedBorderColor = Color(0xFF6630A0),
                            cursorColor = ChatBright, focusedContainerColor = Color(0xEE10091B),
                            unfocusedContainerColor = Color(0xEE10091B)
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick = { sendMessage() },
                        modifier = Modifier.size(48.dp).shadow(12.dp, CircleShape, ambientColor = ChatViolet, spotColor = ChatViolet)
                            .border(1.5.dp, ChatBright, CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF9D26FF), Color(0xFF3C087E))), CircleShape),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("➤", color = Color.White, fontSize = 25.sp) }
                }

                Row(
                    Modifier.fillMaxWidth().border(1.dp, Color(0xFF6927A8), RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF10091B), Color(0xFF07040C))), RoundedCornerShape(22.dp))
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatNavItem("⌂", "Home", onNavigate)
                    ChatNavItem("▢", "Chat", onNavigate, true)
                    ChatNavItem("♩", "Voice", onNavigate)
                    ChatNavItem("◴", "History", onNavigate)
                    ChatNavItem("⚙", "Settings", onNavigate)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        if (!message.fromUser) {
            PurnimaAvatar(Modifier.size(42.dp))
            Spacer(Modifier.width(7.dp))
        }
        Column(
            Modifier.widthIn(max = 286.dp).shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = ChatViolet, spotColor = ChatViolet)
                .background(Brush.verticalGradient(if (message.fromUser) listOf(Color(0xFF7720C9), Color(0xFF300B67)) else listOf(Color(0xFF241039), Color(0xFF10091E))),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.fromUser) 20.dp else 5.dp, bottomEnd = if (message.fromUser) 5.dp else 20.dp))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFCA7BFF), Color(0xFF6D20B8))),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.fromUser) 20.dp else 5.dp, bottomEnd = if (message.fromUser) 5.dp else 20.dp))
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(message.text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(3.dp))
            Text("${message.time}  ${if (message.fromUser) "✓✓" else ""}", color = Color(0xFFDCC5FF), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
        if (message.fromUser) Spacer(Modifier.width(5.dp))
    }
}

@Composable
private fun PurnimaAvatar(modifier: Modifier = Modifier) {
    Image(painter = painterResource(id = R.drawable.purnima_avatar), contentDescription = "Purnima avatar",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.clip(CircleShape).border(1.5.dp, ChatBright, CircleShape)
            .shadow(9.dp, CircleShape, ambientColor = ChatViolet, spotColor = ChatViolet))
}

@Composable
private fun GalaxyBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stars = listOf(.04f to .08f, .14f to .17f, .28f to .06f, .38f to .21f, .53f to .10f, .67f to .04f,
            .82f to .15f, .94f to .23f, .09f to .31f, .22f to .42f, .44f to .35f, .61f to .28f, .77f to .39f,
            .91f to .47f, .06f to .56f, .19f to .65f, .35f to .53f, .49f to .69f, .72f to .58f, .86f to .72f,
            .11f to .81f, .29f to .91f, .58f to .86f, .76f to .94f, .96f to .83f)
        stars.forEachIndexed { index, point ->
            drawCircle(if (index % 4 == 0) Color(0xFFE7B9FF) else Color(0x99D8A4FF),
                radius = if (index % 4 == 0) 2.2.dp.toPx() else 1.1.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * point.first, size.height * point.second))
        }
        drawCircle(brush = Brush.radialGradient(listOf(Color(0x333C0B7A), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .46f), radius = size.width * .55f),
            radius = size.width * .55f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .46f))
    }
}

@Composable
private fun ChatNavItem(symbol: String, label: String, onNavigate: (String) -> Unit, selected: Boolean = false) {
    TextButton(onClick = { onNavigate(label) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = if (selected) Modifier.shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = ChatViolet, spotColor = ChatViolet)
            .background(Color(0x552A0751), RoundedCornerShape(14.dp)) else Modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = if (selected) Color(0xFFE8BFFF) else ChatMuted, fontSize = 22.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(2.dp))
            Text(label, color = if (selected) Color(0xFFE8BFFF) else ChatMuted, fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean, val time: String)
