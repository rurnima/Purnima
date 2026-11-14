package com.rurnima.purnima

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

private val VoiceNight = Color(0xFF05020D)
private val VoicePurple = Color(0xFFB52CFF)
private val VoiceLilac = Color(0xFFE7C5FF)
private val VoiceMuted = Color(0xFFC9B9DF)

@Composable
fun VoiceScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liveRoom by remember { mutableStateOf<Room?>(null) }
    var eventJob by remember { mutableStateOf<Job?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var microphoneEnabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap to Talk") }
    var errorMessage by remember { mutableStateOf("") }
    var pendingPermissionConnect by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingPermissionConnect = true
        } else {
            status = "Microphone permission is needed to talk"
            errorMessage = "Allow microphone access in Android app settings."
        }
    }

    LaunchedEffect(pendingPermissionConnect) {
        if (pendingPermissionConnect) {
            pendingPermissionConnect = false
            connectToPurnima()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            eventJob?.cancel()
            liveRoom?.disconnect()
        }
    }

    fun connectToPurnima() {
        if (connecting || connected) return
        val endpoint = BuildConfig.LIVEKIT_TOKEN_ENDPOINT
        val devServerId = BuildConfig.LIVEKIT_DEV_TOKEN_SERVER_ID
        if (endpoint.isBlank() && devServerId.isBlank()) {
            status = "Voice service is not configured yet"
            errorMessage = "Add LIVEKIT_TOKEN_ENDPOINT in your secure build configuration."
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            status = "Microphone permission is required"
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        scope.launch {
            connecting = true
            errorMessage = ""
            status = "Connecting to Purnima…"
            try {
                val source = if (endpoint.isNotBlank()) {
                    TokenSource.fromEndpoint(endpoint)
                } else {
                    // Development token servers are intentionally insecure and must not be used in production.
                    TokenSource.fromDevelopmentTokenServer(devServerId)
                }
                val roomName = "purnima-voice-" + UUID.randomUUID().toString()
                val credentials = source.fetch(
                    TokenRequestOptions(
                        roomName = roomName,
                        participantName = "Purnima User",
                        participantIdentity = "purnima-user-" + UUID.randomUUID().toString(),
                        agentName = BuildConfig.LIVEKIT_AGENT_NAME
                    )
                ).getOrThrow()

                val room = LiveKit.create(appContext = context.applicationContext)
                liveRoom = room
                eventJob = launch {
                    room.events.collect { event ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                connected = true
                                connecting = false
                                status = "Connected — Purnima is ready"
                            }
                            is RoomEvent.Reconnecting -> status = "Reconnecting…"
                            is RoomEvent.Reconnected -> status = "Connection restored"
                            is RoomEvent.ParticipantConnected -> {
                                if (connected) status = "Purnima joined — you can talk now"
                            }
                            is RoomEvent.Disconnected -> {
                                connected = false
                                connecting = false
                                microphoneEnabled = false
                                status = "Call ended"
                            }
                            else -> Unit
                        }
                    }
                }
                room.connect(credentials.serverUrl, credentials.participantToken)
                room.localParticipant.setMicrophoneEnabled(true)
                microphoneEnabled = true
                connected = true
                connecting = false
                status = "Connected — speak to Purnima"
            } catch (error: Exception) {
                liveRoom?.disconnect()
                liveRoom = null
                connecting = false
                connected = false
                microphoneEnabled = false
                status = "Couldn't connect"
                errorMessage = error.message?.take(180) ?: "Check your LiveKit token endpoint and agent deployment."
            }
        }
    }

    fun onMicTap() {
        val room = liveRoom
        if (!connected || room == null) {
            connectToPurnima()
            return
        }
        scope.launch {
            try {
                val nextEnabled = !microphoneEnabled
                room.localParticipant.setMicrophoneEnabled(nextEnabled)
                microphoneEnabled = nextEnabled
                status = if (nextEnabled) "You're live — Purnima is listening" else "Microphone muted"
            } catch (error: Exception) {
                errorMessage = error.message?.take(180) ?: "Couldn't change microphone state."
            }
        }
    }

    fun endCall() {
        eventJob?.cancel()
        eventJob = null
        liveRoom?.disconnect()
        liveRoom = null
        connecting = false
        connected = false
        microphoneEnabled = false
        status = "Tap to Talk"
        errorMessage = ""
    }

    Box(Modifier.fillMaxSize().background(VoiceNight)) {
        VoiceGalaxyBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Voice",
                    color = Color.White,
                    fontSize = 43.sp,
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.shadow(18.dp, ambientColor = VoicePurple, spotColor = VoicePurple)
                )
            }

            Spacer(Modifier.weight(0.55f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceSideWave(Modifier.width(82.dp).height(130.dp), active = connected && microphoneEnabled)
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .shadow(28.dp, CircleShape, ambientColor = VoicePurple, spotColor = VoicePurple)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFF391078), Color(0xFF17052D), Color(0xFF080313))),
                            CircleShape
                        )
                        .border(2.dp, VoicePurple, CircleShape)
                        .padding(11.dp)
                        .border(4.dp, VoiceLilac, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = !connecting) { onMicTap() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(82.dp)) {
                        val cx = size.width / 2f
                        val top = size.height * .12f
                        val micW = size.width * .32f
                        val micH = size.height * .48f
                        drawRoundRect(
                            Color.White,
                            topLeft = Offset(cx - micW / 2f, top),
                            size = Size(micW, micH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(micW / 2f)
                        )
                        val arc = Path().apply {
                            moveTo(cx - size.width * .31f, size.height * .43f)
                            cubicTo(cx - size.width * .31f, size.height * .89f, cx + size.width * .31f, size.height * .89f, cx + size.width * .31f, size.height * .43f)
                        }
                        drawPath(arc, Color.White, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                        drawLine(Color.White, Offset(cx, size.height * .82f), Offset(cx, size.height * .97f), 5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(cx - size.width * .20f, size.height * .97f), Offset(cx + size.width * .20f, size.height * .97f), 5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                Spacer(Modifier.width(4.dp))
                VoiceSideWave(Modifier.width(82.dp).height(130.dp), active = connected && microphoneEnabled)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                if (connecting) "Connecting…" else if (connected && microphoneEnabled) "You're live" else if (connected) "Microphone muted" else status,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage,
                    color = VoiceLilac,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
            if (connected) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "End call",
                    color = VoiceLilac,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { endCall() }.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.weight(0.48f))
            VoiceWaveform(Modifier.fillMaxWidth().height(112.dp), active = connected && microphoneEnabled)
            Spacer(Modifier.weight(0.45f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = VoicePurple, spotColor = VoicePurple)
                    .border(1.dp, Color(0xFF7625C7), RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF12091F), Color(0xFF07040D))), RoundedCornerShape(24.dp))
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceNavItem("⌂", "Home", false) { onNavigate("Home") }
                VoiceNavItem("▢", "Chat", false) { onNavigate("Chat") }
                VoiceNavItem("♩", "Voice", true) { onNavigate("Voice") }
                VoiceNavItem("◴", "History", false) { onNavigate("History") }
                VoiceNavItem("⚙", "Settings", false) { onNavigate("Settings") }
            }
        }
    }
}

@Composable
private fun VoiceSideWave(modifier: Modifier, active: Boolean) {
    val heights = listOf(18, 34, 48, 66, 88, 62, 42, 28, 16)
    Canvas(modifier) {
        val gap = size.width / (heights.size * 1.7f)
        val barWidth = gap * .38f
        heights.forEachIndexed { index, height ->
            val h = height * (if (active && index % 2 == 0) 1.18f else 1f) * density
            drawLine(
                brush = Brush.verticalGradient(listOf(Color.White, VoicePurple, VoiceLilac)),
                start = Offset(gap * index + gap / 2f, (size.height - h) / 2f),
                end = Offset(gap * index + gap / 2f, (size.height + h) / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun VoiceWaveform(modifier: Modifier, active: Boolean) {
    val bars = listOf(4, 4, 4, 4, 8, 18, 34, 52, 70, 48, 30, 18, 12, 25, 45, 66, 92, 118, 90, 64, 42, 24, 15, 30, 52, 72, 50, 32, 20, 12, 6, 4, 4, 4)
    Canvas(modifier) {
        val slot = size.width / bars.size
        bars.forEachIndexed { index, value ->
            val h = value * (if (active && index % 3 == 0) 1.22f else 1f) * density
            drawLine(
                color = if (index % 5 == 0) VoiceLilac else Color(0xFFDCA8FF),
                start = Offset(slot * index + slot / 2f, (size.height - h) / 2f),
                end = Offset(slot * index + slot / 2f, (size.height + h) / 2f),
                strokeWidth = maxOf(2.dp.toPx(), slot * .28f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun VoiceGalaxyBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF09021A), Color(0xFF03020A), Color(0xFF10051F))))
        val stars = listOf(
            .04f to .08f, .15f to .16f, .29f to .07f, .43f to .14f, .58f to .05f, .76f to .13f,
            .91f to .08f, .08f to .29f, .22f to .36f, .39f to .28f, .64f to .34f, .84f to .27f,
            .96f to .44f, .05f to .57f, .19f to .67f, .36f to .56f, .53f to .68f, .73f to .59f,
            .92f to .72f, .08f to .83f, .28f to .92f, .49f to .85f, .69f to .91f, .88f to .86f
        )
        stars.forEachIndexed { index, point ->
            drawCircle(if (index % 4 == 0) VoiceLilac else Color(0x99DCA8FF), radius = if (index % 4 == 0) 2.1.dp.toPx() else 1.dp.toPx(), center = Offset(size.width * point.first, size.height * point.second))
        }
        val ribbons = listOf(
            Path().apply { moveTo(-size.width * .1f, size.height * .22f); cubicTo(size.width * .28f, size.height * .04f, size.width * .54f, size.height * .42f, size.width * 1.1f, size.height * .18f) },
            Path().apply { moveTo(-size.width * .1f, size.height * .62f); cubicTo(size.width * .28f, size.height * .42f, size.width * .68f, size.height * .84f, size.width * 1.1f, size.height * .54f) },
            Path().apply { moveTo(-size.width * .1f, size.height * .91f); cubicTo(size.width * .3f, size.height * .72f, size.width * .65f, size.height * 1.04f, size.width * 1.1f, size.height * .83f) }
        )
        ribbons.forEach { path ->
            drawPath(path, Color(0x553B0C92), style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0xAA9C20FF), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0x99F0B7FF), style = Stroke(width = .8.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun VoiceNavItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brush.verticalGradient(listOf(Color(0xAA6F13C8), Color(0x55310A66))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = if (selected) Color.White else VoiceMuted, fontSize = 23.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(label, color = if (selected) VoiceLilac else VoiceMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
