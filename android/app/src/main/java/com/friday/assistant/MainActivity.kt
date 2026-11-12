package com.friday.assistant

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.assistant.conversation.ConversationConnectionSettings
import com.friday.assistant.conversation.ConversationRepository
import com.friday.assistant.conversation.ConversationStatus
import com.friday.assistant.conversation.ConversationViewModel
import com.friday.assistant.conversation.HttpJsonConversationBackend
import com.friday.assistant.conversation.JsonFileConversationStore
import com.friday.assistant.conversation.MessageRole
import com.friday.assistant.conversation.SecureTokenException
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Background = Color(0xFF08070D)
private val Silver = Color(0xFFE5DFF2)
private val Violet = Color(0xFFBB98FF)
private val SoftViolet = Color(0xFF8060C7)
private val Muted = Color(0xFF81798F)
private const val MIN_ORB_ZOOM = 0.78f
private const val MAX_ORB_ZOOM = 1.22f

private data class BootParticle(
    val angle: Float,
    val startRadius: Float,
    val endRadius: Float,
    val size: Float,
    val isSilver: Boolean
)

class MainActivity : ComponentActivity() {
    private val connectionSettings by lazy {
        ConversationConnectionSettings(applicationContext)
    }

    private val conversationViewModel: ConversationViewModel by viewModels {
        ConversationViewModel.Factory(
            ConversationRepository(
                store = JsonFileConversationStore(File(filesDir, "friday-conversation.json")),
                backend = HttpJsonConversationBackend(
                    configuration = connectionSettings::backendConfiguration
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                FridayApp(conversationViewModel, connectionSettings)
            }
        }
    }
}

@Composable
private fun FridayApp(
    viewModel: ConversationViewModel,
    connectionSettings: ConversationConnectionSettings
) {
    var bootStage by remember { mutableStateOf(0) }
    var bootComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350)
        bootStage = 1

        delay(750)
        bootStage = 2

        delay(850)
        bootStage = 3

        delay(550)
        bootComplete = true
    }

    val bootProgress by animateFloatAsState(
        targetValue = bootStage / 3f,
        animationSpec = tween(550),
        label = "boot_progress"
    )

    val bootAlpha by animateFloatAsState(
        targetValue = if (bootComplete) 0f else 1f,
        animationSpec = tween(700),
        label = "boot_fade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        FridayHome(viewModel, connectionSettings)

        if (!bootComplete || bootAlpha > 0.01f) {
            BootSequence(
                stage = bootStage,
                progress = bootProgress,
                alpha = bootAlpha
            )
        }
    }
}

@Composable
private fun BootSequence(
    stage: Int,
    progress: Float,
    alpha: Float
) {
    val message = when (stage) {
        0 -> "INITIALIZING INTERFACE"
        1 -> "ASSEMBLING NEURAL PATHWAYS"
        2 -> "CALIBRATING VISUAL SYSTEM"
        else -> "INTERFACE READY"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF20132E),
                        Color(0xFF100D18),
                        Background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "F.R.I.D.A.Y.",
                color = Silver,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "NEURAL INTELLIGENCE SYSTEM",
                color = Violet,
                fontSize = 9.sp,
                letterSpacing = 2.5.sp
            )

            Spacer(Modifier.height(20.dp))

            BootParticleAssembly(
                progress = progress,
                modifier = Modifier.size(210.dp)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = message,
                color = Silver,
                fontSize = 10.sp,
                letterSpacing = 1.7.sp
            )

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Color(0xFF30243F),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    SoftViolet,
                                    Violet,
                                    Silver
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "LOCAL STARTUP SEQUENCE",
                    color = Muted,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    "${(progress * 100).toInt()}%",
                    color = Violet,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun BootParticleAssembly(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        val random = Random(21_754)
        List(180) {
            BootParticle(
                angle = random.nextFloat() * 6.2831855f,
                startRadius = 0.34f + random.nextFloat() * 0.12f,
                endRadius = 0.04f + sqrt(random.nextFloat()) * 0.22f,
                size = 0.003f + random.nextFloat() * 0.004f,
                isSilver = random.nextFloat() > 0.48f
            )
        }
    }

    val assembly = progress.coerceIn(0f, 1f)
    val easedAssembly = assembly * assembly * (3f - 2f * assembly)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x665A3E82).copy(alpha = easedAssembly * 0.75f),
                    Color(0x00301946)
                ),
                center = center,
                radius = radius * 0.66f
            ),
            radius = radius * 0.66f,
            center = center
        )

        particles.forEachIndexed { index, particle ->
            val stagger = (index % 24) / 24f * 0.14f
            val particleProgress = ((easedAssembly - stagger) / (1f - stagger))
                .coerceIn(0f, 1f)
            val currentRadius = particle.startRadius +
                (particle.endRadius - particle.startRadius) * particleProgress
            val angle = particle.angle + particleProgress * (if (index % 2 == 0) 0.32f else -0.32f)
            val position = Offset(
                x = center.x + cos(angle) * radius * currentRadius,
                y = center.y + sin(angle) * radius * currentRadius
            )
            val color = if (particle.isSilver) Silver else Violet
            val particleRadius = size.minDimension * particle.size

            drawCircle(
                color = color.copy(alpha = 0.15f + particleProgress * 0.72f),
                radius = particleRadius * 2.4f,
                center = position
            )
            drawCircle(
                color = color.copy(alpha = 0.4f + particleProgress * 0.6f),
                radius = particleRadius,
                center = position
            )
        }
    }
}

@Composable
private fun FridayHome(
    viewModel: ConversationViewModel,
    connectionSettings: ConversationConnectionSettings
) {
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var angularVelocityX by remember { mutableFloatStateOf(0f) }
    var angularVelocityY by remember { mutableFloatStateOf(0f) }
    var inertiaActive by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var showNewConversationDialog by remember { mutableStateOf(false) }
    var showConnectionSettingsDialog by remember { mutableStateOf(false) }
    var backendConfigurationRevision by remember { mutableStateOf(0) }
    var endpointInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var connectionDialogError by remember { mutableStateOf<String?>(null) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val backendConfigurationError = remember(viewModel, backendConfigurationRevision) {
        viewModel.backendConfigurationError()
    }
    val isBusy = state.status in setOf(
        ConversationStatus.SENDING,
        ConversationStatus.THINKING,
        ConversationStatus.RESPONDING
    )

    fun sendMessage() {
        viewModel.send(messageText)
        if (messageText.isNotBlank()) {
            messageText = ""
            keyboardController?.hide()
        }
    }

    LaunchedEffect(state.messages.size, state.status) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(inertiaActive) {
        if (inertiaActive) {
            var previousFrameNanos = 0L
            while (abs(angularVelocityX) > 0.04f || abs(angularVelocityY) > 0.04f) {
                val frameNanos = withFrameNanos { it }
                if (previousFrameNanos != 0L) {
                    val deltaSeconds = (frameNanos - previousFrameNanos) * 1e-9f
                    yaw += angularVelocityX * deltaSeconds
                    pitch = (pitch + angularVelocityY * deltaSeconds).coerceIn(-1.25f, 1.25f)
                    val damping = exp(-3.8f * deltaSeconds)
                    angularVelocityX *= damping
                    angularVelocityY *= damping
                }
                previousFrameNanos = frameNanos
            }
            angularVelocityX = 0f
            angularVelocityY = 0f
            inertiaActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        SceneView(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .align(Alignment.TopCenter)
                .pointerInput(touchSlop) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        inertiaActive = false
                        angularVelocityX = 0f
                        angularVelocityY = 0f

                        val downPosition = down.position
                        var previousSpan = 0f
                        var previousPointerCount = 1
                        var previousTime = down.uptimeMillis
                        var maxTravel = 0f
                        var multiTouch = false
                        var finalTime = down.uptimeMillis

                        do {
                            val event = awaitPointerEvent()
                            val eventTime = event.changes.first().uptimeMillis
                            finalTime = eventTime
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.size >= 2) {
                                multiTouch = true
                                val first = pressed[0].position
                                val second = pressed[1].position
                                val span = (first - second).getDistance()
                                if (previousPointerCount >= 2 && previousSpan > 0f && span > 0f) {
                                    zoom = (zoom * span / previousSpan)
                                        .coerceIn(MIN_ORB_ZOOM, MAX_ORB_ZOOM)
                                }
                                previousSpan = span
                                angularVelocityX = 0f
                                angularVelocityY = 0f
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                maxTravel = maxOf(maxTravel, (change.position - downPosition).getDistance())

                                if (previousPointerCount == 1) {
                                    val width = size.width.coerceAtLeast(1)
                                    val height = size.height.coerceAtLeast(1)
                                    val elapsedSeconds =
                                        ((eventTime - previousTime).coerceAtLeast(1L)) / 1000f
                                    val yawDelta = delta.x * (2f * Math.PI.toFloat() / width)
                                    val pitchDelta = delta.y * (2f * Math.PI.toFloat() / height)
                                    yaw += yawDelta
                                    pitch = (pitch + pitchDelta).coerceIn(-1.25f, 1.25f)
                                    val targetVelocityX =
                                        (yawDelta / elapsedSeconds).coerceIn(-5f, 5f)
                                    val targetVelocityY =
                                        (pitchDelta / elapsedSeconds).coerceIn(-5f, 5f)
                                    angularVelocityX += (targetVelocityX - angularVelocityX) * 0.35f
                                    angularVelocityY += (targetVelocityY - angularVelocityY) * 0.35f
                                }

                                previousSpan = 0f
                            }

                            pressed.forEach { it.consume() }
                            event.changes.filterNot { it.pressed }.forEach { it.consume() }
                            previousPointerCount = pressed.size
                            previousTime = eventTime
                        } while (event.changes.any { it.pressed })

                        val isTap = !multiTouch &&
                            maxTravel <= touchSlop &&
                            finalTime - down.uptimeMillis <= 350L
                        if (isTap) {
                            val isDoubleTap = finalTime - lastTapTime <= 320L &&
                                (downPosition - lastTapPosition).getDistance() <= touchSlop * 2f
                            if (isDoubleTap) {
                                yaw = 0f
                                pitch = 0f
                                zoom = 1f
                                angularVelocityX = 0f
                                angularVelocityY = 0f
                                inertiaActive = false
                                lastTapTime = 0L
                            } else {
                                lastTapTime = finalTime
                                lastTapPosition = downPosition
                            }
                        } else {
                            inertiaActive = abs(angularVelocityX) > 0.04f ||
                                abs(angularVelocityY) > 0.04f
                        }
                    }
                },
            renderQuality = RenderQuality.Cinematic,
            cameraManipulator = null
        ) {
            Node {
                val particleNode = remember(engine, materialLoader) {
                    FilamentParticleCloudNode(engine, materialLoader)
                }

                SideEffect {
                    particleNode.rotation = Float3(pitch, yaw, 0f)
                    particleNode.scale = Float3(zoom, zoom, zoom)
                }

                DisposableEffect(parentNode, particleNode) {
                    parentNode.addChildNode(particleNode)
                    onDispose {
                        parentNode.removeChildNode(particleNode)
                        particleNode.destroy()
                    }
                }
            }
        }

        // Clean interface framing around the neural scene.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "F.R.I.D.A.Y.",
                        color = Silver,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )

                    Text(
                        text = "PERSONAL ASSISTANT",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            endpointInput = connectionSettings.endpointUrl()
                            tokenInput = ""
                            connectionDialogError = null
                            showConnectionSettingsDialog = true
                        }
                    ) {
                        Text("CONNECTION", color = Violet, fontSize = 10.sp)
                    }
                    TextButton(
                        onClick = { showNewConversationDialog = true },
                        enabled = !isBusy && !state.isRestoring
                    ) {
                        Text("NEW CHAT", color = Violet, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val statusLabel = when (state.status) {
                    ConversationStatus.IDLE ->
                        if (backendConfigurationError == null) "CONFIGURED" else "BACKEND NOT CONFIGURED"
                    ConversationStatus.SENDING -> "SENDING"
                    ConversationStatus.THINKING -> "THINKING"
                    ConversationStatus.RESPONDING -> "RESPONDING"
                    ConversationStatus.ERROR -> "ERROR"
                }
                val statusColor = if (state.status == ConversationStatus.ERROR ||
                    (state.status == ConversationStatus.IDLE && backendConfigurationError != null)
                ) Color(0xFFE9A7A7) else Violet

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )
                    Text(
                        text = "  $statusLabel",
                        color = statusColor,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    )
                    if (state.status == ConversationStatus.SENDING ||
                        state.status == ConversationStatus.THINKING
                    ) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Violet,
                            strokeWidth = 1.5.dp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF282130), RoundedCornerShape(18.dp))
                        .background(Color(0xE6100D18), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        if (state.messages.isEmpty() && !state.isRestoring) {
                            item {
                                Text(
                                    text = if (backendConfigurationError == null) {
                                        "Send a message to begin."
                                    } else {
                                        "Configure the F.R.I.D.A.Y. backend to start a conversation."
                                    },
                                    color = Muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 14.dp)
                                )
                            }
                        }
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message.role, message.content, message.timestampMillis)
                        }
                        if (state.status == ConversationStatus.THINKING ||
                            state.status == ConversationStatus.RESPONDING
                        ) {
                            item(key = "thinking-indicator") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = Violet,
                                        strokeWidth = 1.5.dp
                                    )
                                    Text(
                                        text = if (state.status == ConversationStatus.THINKING) {
                                            "F.R.I.D.A.Y. is thinking"
                                        } else {
                                            "Saving response"
                                        },
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                state.errorMessage?.let { error ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                        color = Color(0xFF29191F),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFFFC6C6),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.canRetry) {
                                TextButton(onClick = viewModel::retry) {
                                    Text("RETRY", color = Violet, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy && !state.isRestoring,
                        placeholder = { Text("Message F.R.I.D.A.Y.", color = Muted) },
                        shape = RoundedCornerShape(18.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF17131F),
                            unfocusedContainerColor = Color(0xFF17131F),
                            disabledContainerColor = Color(0xFF17131F),
                            focusedTextColor = Silver,
                            unfocusedTextColor = Silver,
                            focusedIndicatorColor = Violet,
                            unfocusedIndicatorColor = Color(0xFF3A3146),
                            cursorColor = Violet
                        )
                    )
                    Button(
                        onClick = { sendMessage() },
                        enabled = messageText.isNotBlank() && !isBusy && !state.isRestoring,
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoftViolet,
                            disabledContainerColor = Color(0xFF342B41)
                        )
                    ) {
                        Text("SEND", fontSize = 11.sp, letterSpacing = 0.8.sp)
                    }
                }
            }
        }
    }

    if (showNewConversationDialog) {
        AlertDialog(
            onDismissRequest = { showNewConversationDialog = false },
            title = { Text("Start a new conversation?", color = Silver) },
            text = {
                Text(
                    "This clears the conversation saved on this device.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewConversationDialog = false
                        viewModel.startNewConversation()
                    }
                ) {
                    Text("CLEAR", color = Violet)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewConversationDialog = false }) {
                    Text("CANCEL", color = Muted)
                }
            },
            containerColor = Color(0xFF17131F)
        )
    }

    if (showConnectionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showConnectionSettingsDialog = false },
            title = { Text("Backend connection", color = Silver) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Enter the Windows host URL and the server access token. The token is encrypted with Android Keystore.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    TextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Backend URL", color = Muted) },
                        placeholder = { Text("http://192.168.1.20:8765/api/chat", color = Muted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF17131F),
                            unfocusedContainerColor = Color(0xFF17131F),
                            focusedTextColor = Silver,
                            unfocusedTextColor = Silver,
                            focusedIndicatorColor = Violet,
                            unfocusedIndicatorColor = Color(0xFF3A3146),
                            cursorColor = Violet
                        )
                    )
                    TextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Server access token", color = Muted) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF17131F),
                            unfocusedContainerColor = Color(0xFF17131F),
                            focusedTextColor = Silver,
                            unfocusedTextColor = Silver,
                            focusedIndicatorColor = Violet,
                            unfocusedIndicatorColor = Color(0xFF3A3146),
                            cursorColor = Violet
                        )
                    )
                    connectionDialogError?.let {
                        Text(it, color = Color(0xFFFFC6C6), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            connectionSettings.save(endpointInput, tokenInput)
                            backendConfigurationRevision += 1
                            val error = viewModel.backendConfigurationError()
                            if (error == null) {
                                showConnectionSettingsDialog = false
                            } else {
                                connectionDialogError = error
                            }
                        } catch (exception: SecureTokenException) {
                            connectionDialogError =
                                exception.message ?: "Backend settings could not be saved."
                        }
                    }
                ) {
                    Text("SAVE", color = Violet)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            try {
                                connectionSettings.clearToken()
                                tokenInput = ""
                                backendConfigurationRevision += 1
                                connectionDialogError = "Saved server access token cleared."
                            } catch (exception: SecureTokenException) {
                                connectionDialogError =
                                    exception.message ?: "The server access token could not be cleared."
                            }
                        }
                    ) {
                        Text("CLEAR TOKEN", color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = { showConnectionSettingsDialog = false }) {
                        Text("CANCEL", color = Muted, fontSize = 10.sp)
                    }
                }
            },
            containerColor = Color(0xFF17131F)
        )
    }
}

@Composable
private fun MessageBubble(role: MessageRole, content: String, timestampMillis: Long) {
    val isUser = role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            color = if (isUser) Color(0xFF302541) else Color(0xFF1C1824),
            shape = RoundedCornerShape(
                topStart = 15.dp,
                topEnd = 15.dp,
                bottomStart = if (isUser) 15.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 15.dp
            )
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isUser) "YOU" else "F.R.I.D.A.Y.",
                        color = if (isUser) Violet else Silver,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(timestampMillis)),
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = content,
                    color = Silver,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
