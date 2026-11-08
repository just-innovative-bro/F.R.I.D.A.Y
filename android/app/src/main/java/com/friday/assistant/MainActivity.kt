package com.friday.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF090910)
private val Panel = Color(0xFF151521)
private val Violet = Color(0xFFAD8AFF)
private val SoftViolet = Color(0xFF6446A8)
private val Silver = Color(0xFFE0DCEC)
private val Muted = Color(0xFF858194)

private data class FridayModule(
    val name: String,
    val description: String,
    val symbol: String
)

private val modules = listOf(
    FridayModule("ENGINEER", "Development, coding and technical problem-solving.", "E"),
    FridayModule("SCOUT", "Research, information discovery and analysis.", "S"),
    FridayModule("SENTINEL", "System awareness, safety and permission monitoring.", "N"),
    FridayModule("LOGISTICS", "Planning, organisation and task coordination.", "L"),
    FridayModule("DESIGNER", "Creative work, visual concepts and design.", "D"),
    FridayModule("SPATIAL", "Spatial interface and environment interaction.", "P")
)

private val nodePositions = listOf(
    0.17f to 0.25f,
    0.50f to 0.13f,
    0.83f to 0.25f,
    0.17f to 0.75f,
    0.50f to 0.87f,
    0.83f to 0.75f
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                FridayHome()
            }
        }
    }
}

@Composable
private fun FridayHome() {
    var selectedModule by remember { mutableStateOf<FridayModule?>(null) }

    val motion = rememberInfiniteTransition(label = "friday_motion")

    val particleProgress by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neural_particles"
    )

    val breathing by motion.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_breathing"
    )

    val ringRotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "F.R.I.D.A.Y.",
                        color = Silver,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        "PERSONAL ASSISTANT",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "FOUNDATION",
                        color = Violet,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "CORE INITIALIZED",
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "NEURAL INTERFACE",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val centerX = maxWidth * 0.5f
                val centerY = maxHeight * 0.5f

                // Neural branches and travelling light signals.
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f

                    nodePositions.forEachIndexed { index, position ->
                        val nx = size.width * position.first
                        val ny = size.height * position.second
                        val start = Offset(cx, cy)
                        val end = Offset(nx, ny)

                        drawLine(
                            color = SoftViolet.copy(alpha = 0.65f),
                            start = start,
                            end = end,
                            strokeWidth = 1.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val progress = (particleProgress + index * 0.17f) % 1f
                        val particle = Offset(
                            cx + (nx - cx) * progress,
                            cy + (ny - cy) * progress
                        )

                        drawCircle(
                            color = Violet.copy(alpha = 0.16f),
                            radius = 10.dp.toPx(),
                            center = particle
                        )
                        drawCircle(
                            color = Silver.copy(alpha = 0.95f),
                            radius = 2.8.dp.toPx(),
                            center = particle
                        )

                        drawCircle(
                            color = Violet.copy(alpha = 0.10f),
                            radius = 25.dp.toPx(),
                            center = end
                        )
                    }
                }

                // Slowly rotating energy rings around the core.
                Canvas(
                    modifier = Modifier
                        .offset(x = centerX - 59.dp, y = centerY - 59.dp)
                        .size(118.dp)
                        .graphicsLayer(rotationZ = ringRotation)
                ) {
                    drawArc(
                        color = Violet.copy(alpha = 0.9f),
                        startAngle = 15f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawArc(
                        color = Silver.copy(alpha = 0.65f),
                        startAngle = 195f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                        size = Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx()),
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Interactive module nodes.
                modules.forEachIndexed { index, module ->
                    val (x, y) = nodePositions[index]

                    ModuleNode(
                        module = module,
                        selected = selectedModule == module,
                        modifier = Modifier.offset(
                            x = maxWidth * x - 39.dp,
                            y = maxHeight * y - 39.dp
                        ),
                        onClick = { selectedModule = module }
                    )
                }

                // Breathing central assistant core.
                Box(
                    modifier = Modifier
                        .offset(x = centerX - 43.dp, y = centerY - 43.dp)
                        .size(86.dp)
                        .graphicsLayer {
                            scaleX = breathing
                            scaleY = breathing
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF49316F),
                                    Color(0xFF211833),
                                    Color(0xFF110F1A)
                                )
                            )
                        )
                        .border(1.dp, Violet, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "F",
                            color = Silver,
                            fontSize = 37.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            "CORE",
                            color = Violet,
                            fontSize = 8.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    SoftViolet.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        selectedModule?.name ?: "SYSTEM OVERVIEW",
                        color = Silver,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        selectedModule?.description
                            ?: "Select a neural node to explore a F.R.I.D.A.Y. module.",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        if (selectedModule == null) {
                            "6 MODULES  •  INTERFACE PROTOTYPE"
                        } else {
                            "MODULE SELECTED  •  FUNCTIONALITY NOT YET CONNECTED"
                        },
                        color = Violet,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "F.R.I.D.A.Y.  •  ANDROID FOUNDATION",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ModuleNode(
    module: FridayModule,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .size(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF302347) else Panel)
            .border(
                1.dp,
                if (selected) Violet else SoftViolet.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            module.symbol,
            color = Silver,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(3.dp))
        Text(
            module.name,
            color = if (selected) Violet else Muted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}
