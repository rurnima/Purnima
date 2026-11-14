package com.rurnima.purnima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val Night = Color(0xFF05020D)
private val Violet = Color(0xFFB52CFF)
private val Glow = Color(0xFF8A20FF)
private val Muted = Color(0xFFD8D0F0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("Home") }
                Surface(Modifier.fillMaxSize(), color = Night) {
                    when (currentScreen) {
                        "Chat" -> ChatScreen(onNavigate = { destination ->
                            if (destination == "Home") currentScreen = "Home"
                        })
                        else -> PurnimaHomeScreen(onNavigate = { destination ->
                            if (destination == "Chat") currentScreen = "Chat"
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PurnimaHomeScreen(onNavigate: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF100022), Color(0xFF05020D), Color(0xFF090014))
                )
            )
    ) {
        CosmicBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.purnima_avatar),
                    contentDescription = "Purnima",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Violet, CircleShape)
                        .shadow(12.dp, CircleShape, ambientColor = Violet, spotColor = Violet)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Purnima",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ParticleOrb(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1f)
                )
            }

            BottomNavigationBar(onNavigate)
        }
    }
}

@Composable
private fun ParticleOrb(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse = transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "orbPulse"
    )
    val twinkle = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "twinkle"
    )
    val points = remember {
        val random = Random(27)
        List(720) {
            val angle = random.nextFloat() * (Math.PI * 2).toFloat()
            val radius = kotlin.math.sqrt(random.nextFloat()) * 0.47f
            Triple(
                0.5f + kotlin.math.cos(angle) * radius,
                0.5f + kotlin.math.sin(angle) * radius,
                random.nextFloat()
            )
        }
    }
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.47f * pulse.value
        // Soft luminous sphere edge and inner glow
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x222D00FF), Color(0x11090020), Color.Transparent),
                center,
                radius * 1.18f
            ),
            radius = radius * 1.18f,
            center = center
        )
        drawCircle(
            color = Color(0x663D16FF),
            radius = radius,
            center = center,
            style = Stroke(width = size.minDimension * 0.004f)
        )
        points.forEach { (x, y, sparkle) ->
            val point = Offset(
                center.x + (x - 0.5f) * 2f * radius,
                center.y + (y - 0.5f) * 2f * radius
            )
            val distance = (point - center).getDistance()
            if (distance <= radius) {
                val depthAlpha = (1f - distance / radius) * 0.35f + 0.48f
                val dotRadius = size.minDimension * (0.002f + sparkle * 0.0042f)
                drawCircle(
                    color = Color(0xFFE9D8FF).copy(alpha = (depthAlpha * twinkle.value).coerceIn(0.15f, 1f)),
                    radius = dotRadius,
                    center = point
                )
                if (sparkle > 0.92f) {
                    drawCircle(
                        color = Violet.copy(alpha = 0.8f),
                        radius = dotRadius * 2.3f,
                        center = point
                    )
                }
            }
        }
        // Violet orbit trails around the particle globe
        drawArc(
            color = Violet.copy(alpha = 0.72f),
            startAngle = 18f,
            sweepAngle = 142f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.12f, center.y - radius * 0.72f),
            size = androidx.compose.ui.geometry.Size(radius * 2.24f, radius * 1.44f),
            style = Stroke(width = size.minDimension * 0.008f)
        )
        drawArc(
            color = Color(0xFF5D00FF).copy(alpha = 0.8f),
            startAngle = 198f,
            sweepAngle = 148f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.16f, center.y - radius * 0.42f),
            size = androidx.compose.ui.geometry.Size(radius * 2.32f, radius * 0.84f),
            style = Stroke(width = size.minDimension * 0.006f)
        )
    }
}

@Composable
private fun CosmicBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(11)
        List(100) { Offset(random.nextFloat(), random.nextFloat()) to random.nextFloat() }
    }
    Canvas(modifier) {
        stars.forEach { (position, brightness) ->
            drawCircle(
                Color(0xFFBDA4FF).copy(alpha = 0.15f + brightness * 0.55f),
                radius = 0.6f + brightness * 1.5f,
                center = Offset(position.x * size.width, position.y * size.height)
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(onNavigate: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .border(1.dp, Color(0xFF6F24C8), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(Color(0xE6090314), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem("⌂", "Home", true) { onNavigate("Home") }
        BottomNavItem("▢", "Chat", false) { onNavigate("Chat") }
        BottomNavItem("♩", "Voice", false) { }
        BottomNavItem("◴", "History", false) { }
        BottomNavItem("⚙", "Settings", false) { }
    }
}

@Composable
private fun RowScope.BottomNavItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x663D0878) else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            symbol,
            color = if (selected) Color(0xFFE6BFFF) else Muted,
            fontSize = 23.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            label,
            color = if (selected) Color(0xFFE6BFFF) else Muted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
