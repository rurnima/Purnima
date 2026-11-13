package com.rurnima.purnima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF080610)
private val Violet = Color(0xFF9B5CFF)
private val SoftViolet = Color(0xFF6E35C8)
private val Muted = Color(0xFFAAA2C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Night) {
                    PurnimaHomeScreen()
                }
            }
        }
    }
}

@Composable
private fun PurnimaHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF10091F), Night, Color(0xFF05040A))
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(12.dp, CircleShape, ambientColor = Violet, spotColor = Violet)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFBE8BFF), SoftViolet, Color(0xFF24103F))),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFFB98BFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Color.White, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Purnima", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("YOUR AI COMPANION", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFF8DFFB5), CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text("ONLINE", color = Color(0xFF8DFFB5), fontSize = 10.sp, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(Color(0xFF6D42A5), Color(0x332D1B49))),
                    shape = RoundedCornerShape(28.dp)
                )
                .background(
                    Brush.verticalGradient(listOf(Color(0x241C1231), Color(0x08080610))),
                    RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Avatar image and orb animation will be wired to the supplied assets.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(24.dp, CircleShape, ambientColor = Violet, spotColor = Violet)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFB88BFF), Color(0xFF7137C9), Color(0xFF180D2B))
                            ),
                            CircleShape
                        )
                        .border(1.dp, Color(0xFFB98BFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✧", color = Color.White, fontSize = 74.sp)
                }
                Spacer(Modifier.height(28.dp))
                Text("Hello, I'm Purnima", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "I'm here whenever you need me",
                    color = Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(30.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(0xFF6E42A0), RoundedCornerShape(50.dp))
                        .background(Color(0x221B0F30), RoundedCornerShape(50.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("✦  TAP TO START A CONVERSATION", color = Color(0xFFD7BFFF), fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF35234F), RoundedCornerShape(22.dp))
                .background(Color(0xFF100B1B), RoundedCornerShape(22.dp))
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("⌂", "Home", true)
            BottomNavItem("◌", "Chat", false)
            BottomNavItem("◎", "Voice", false)
            BottomNavItem("◷", "History", false)
            BottomNavItem("⚙", "Settings", false)
        }
    }
}

@Composable
private fun BottomNavItem(symbol: String, label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            symbol,
            color = if (selected) Color(0xFFC69AFF) else Muted,
            fontSize = 22.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) Color(0xFFC69AFF) else Muted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
