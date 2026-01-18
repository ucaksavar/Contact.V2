package com.example.contactv2

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactv2.ui.theme.ContactV2Theme
import com.example.contactv2.viewmodel.ContactViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class FakeCallActivity : ComponentActivity(), SensorEventListener {
    private var ringtone: Ringtone? = null
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val viewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel.initPrefs(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        sendBroadcast(Intent("com.example.contactv2.ACTION_CALL_STARTED").apply {
            setPackage(packageName)
        })

        val name = intent.getStringExtra("EXTRA_NAME") ?: "Bilinmeyen Numara"
        val number = intent.getStringExtra("EXTRA_NUMBER") ?: ""
        
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
        ringtone?.play()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (proximitySensor != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ContactV2:FakeCall")
        }

        enableEdgeToEdge()
        setContent {
            ContactV2Theme {
                val isDark by viewModel.isFakeCallDark.collectAsState()
                GooglePixelDialerScreen(name, number, isDark, onHangup = { finish() }, onAnswer = { 
                    ringtone?.stop() 
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onDestroy() {
        ringtone?.stop()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            if (event.values[0] < (proximitySensor?.maximumRange ?: 0f)) {
                if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)
            } else {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun GooglePixelDialerScreen(name: String, number: String, isDark: Boolean, onHangup: () -> Unit, onAnswer: () -> Unit) {
    var isAnswered by remember { mutableStateOf(false) }
    var callDurationSeconds by remember { mutableIntStateOf(0) }

    val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FF)
    val contentColor = if (isDark) Color.White else Color(0xFF1B1B1F)
    val subTextColor = if (isDark) Color.LightGray else Color(0xFF44474F)
    val surfaceColor = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE1E2EC)

    LaunchedEffect(isAnswered) {
        if (isAnswered) {
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }

    val formatTime = { seconds: Int ->
        val mins = seconds / 60
        val secs = seconds % 60
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            Text(
                text = name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isAnswered) {
                Text(
                    text = formatTime(callDurationSeconds),
                    fontSize = 18.sp,
                    color = subTextColor
                )
            } else {
                Text(
                    text = if (number.isNotEmpty()) "Mobile • $number" else "Mobil",
                    fontSize = 18.sp,
                    color = subTextColor
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(surfaceColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.8f),
                    tint = subTextColor
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isAnswered) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CallActionSmall(Icons.AutoMirrored.Rounded.Message, "Message", isDark)
                }

                Spacer(modifier = Modifier.height(40.dp))

                SwipeToActionPill(
                    isDark = isDark,
                    onAnswer = { isAnswered = true; onAnswer() },
                    onDecline = onHangup
                )
                
                Spacer(modifier = Modifier.height(60.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PixelActionInCall(Icons.Default.MicOff, "Sessiz", surfaceColor, contentColor)
                    PixelActionInCall(Icons.Default.Dialpad, "Tuşlar", surfaceColor, contentColor)
                    PixelActionInCall(Icons.AutoMirrored.Filled.VolumeUp, "Hoparlör", surfaceColor, contentColor)
                }
                
                Spacer(modifier = Modifier.height(60.dp))
                
                FloatingActionButton(
                    onClick = onHangup,
                    containerColor = Color(0xFFBA1A1A), 
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp).padding(bottom = 10.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(38.dp))
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SwipeToActionPill(isDark: Boolean, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragX = with(density) { 100.dp.toPx() } 
    val swipeOffset = remember { Animatable(0f) }
    val surfaceColor = if (isDark) Color(0xFF2C2C2C).copy(alpha = 0.8f) else Color(0xFFE1E2EC).copy(alpha = 0.7f)

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f) 
            .height(90.dp),
        shape = CircleShape,
        color = surfaceColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Decline", 
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 30.dp)
                    .graphicsLayer { alpha = (1f - (swipeOffset.value / -maxDragX)).coerceIn(0.2f, 1f) },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFBA1A1A)
            )

            Text(
                "Answer", 
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .graphicsLayer { alpha = (1f - (swipeOffset.value / maxDragX)).coerceIn(0.2f, 1f) },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF386A20)
            )

            Surface(
                modifier = Modifier
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .size(80.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset.value >= maxDragX * 0.6f) {
                                    onAnswer()
                                } else if (swipeOffset.value <= -maxDragX * 0.6f) {
                                    onDecline()
                                } else {
                                    coroutineScope.launch {
                                        swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (swipeOffset.value + dragAmount)
                                        .coerceIn(-maxDragX, maxDragX)
                                    swipeOffset.snapTo(newOffset)
                                }
                            }
                        )
                    },
                shape = CircleShape,
                color = if (isDark) Color(0xFF44474F) else Color.White,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Call, 
                        contentDescription = null, 
                        tint = if (swipeOffset.value > 20) Color(0xFF386A20) else if (swipeOffset.value < -20) Color(0xFFBA1A1A) else Color(0xFF386A20),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallActionSmall(icon: ImageVector, label: String, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF2C2C2C) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFF74777F).copy(alpha = 0.1f)),
        modifier = Modifier.wrapContentWidth().height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (isDark) Color.LightGray else Color(0xFF44474F), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = if (isDark) Color.White else Color(0xFF1B1B1F), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PixelActionInCall(icon: ImageVector, label: String, surfaceColor: Color, contentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = surfaceColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = contentColor.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}
