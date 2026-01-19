package com.example.contactv2.ui

import android. Manifest
import android.app.AlarmManager
import android.app. NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android. content.Intent
import android.content.IntentFilter
import android. content.pm.PackageManager
import android. graphics.Matrix
import android.graphics.SweepGradient
import android. net.Uri
import android.os. Build
import android.provider.CallLog
import android.provider.Settings
import android.util.Log
import android.widget. Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation. BorderStroke
import androidx.compose. foundation.Canvas
import androidx.compose.foundation. Image
import androidx.compose.foundation. background
import androidx.compose.foundation.border
import androidx.compose. foundation.clickable
import androidx. compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation. gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx. compose.foundation.layout.*
import androidx.compose.foundation.lazy. LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose. foundation.shape.RoundedCornerShape
import androidx.compose. foundation.text.KeyboardOptions
import androidx.compose. material. icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored. rounded.CallMissed
import androidx.compose. material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored. rounded.Message
import androidx.compose.material. icons.automirrored.rounded.PhoneCallback
import androidx.compose.material. icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui. draw.blur
import androidx.compose.ui. draw.clip
import androidx.compose.ui. draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui. geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui. graphics.*
import androidx.compose.ui. graphics.drawscope.clipPath
import androidx.compose.ui. graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll. NestedScrollConnection
import androidx.compose.ui.input.nestedscroll. NestedScrollSource
import androidx.compose.ui. input.nestedscroll.nestedScroll
import androidx.compose.ui. input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui. platform.LocalDensity
import androidx.compose.ui.platform. LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose. ui.text.font.FontFamily
import androidx.compose.ui. text.font.FontWeight
import androidx.compose.ui.text. input.KeyboardType
import androidx.compose. ui.text.input. PasswordVisualTransformation
import androidx.compose. ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx. compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content. ContextCompat
import androidx.core. net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.contactv2.FakeCallReceiver
import com.example.contactv2.R
import com.example.contactv2.model.Contact
import com.example. contactv2.ui.theme.*
import com.example.contactv2.viewmodel.ContactViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines. isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

// Constants to avoid magic numbers
private const val SCROLL_THRESHOLD = 15f
private const val ACTION_THRESHOLD = 0.8f
private const val MATRIX_DELAY = 40L
private const val TAG = "ContactApp"

@Composable
fun ContactApp(viewModel: ContactViewModel) {
    val navController = rememberNavController()
    var isSearchBarVisible by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showFakeCallDialog by remember { mutableStateOf(false) }
    var showHiddenContactsDialog by remember { mutableStateOf(false) }
    var activeFakeCallTime by remember { mutableStateOf<String?>(null) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val password by viewModel.password.collectAsState()
    var showEasterEgg by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        viewModel.initPrefs(context)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery == "#292853") {
            showEasterEgg = true
            delay(6000)
            showEasterEgg = false
            viewModel.updateSearchQuery("")
        }
    }

    // FIX 1: Memory Leak - Use applicationContext and proper unregister
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                activeFakeCallTime = null
            }
        }
        val filter = IntentFilter("com.example.contactv2.ACTION_CALL_STARTED")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES. TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
        } catch (e:  Exception) {
            Log.e(TAG, "Error registering receiver", e)
        }
        onDispose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log. e(TAG, "Error unregistering receiver", e)
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available:  Offset, source: NestedScrollSource): Offset {
                if (available.y < -SCROLL_THRESHOLD) isSearchBarVisible = false
                if (available.y > SCROLL_THRESHOLD) isSearchBarVisible = true
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Scaffold(
            containerColor = Color. Transparent,
            bottomBar = { BottomNavigationBar(navController) },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .then(if (showEasterEgg) Modifier.blur(12.dp) else Modifier)
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "contacts",
                modifier = Modifier. padding(padding)
            ) {
                composable("history") { HistoryScreen() }
                composable("contacts") {
                    ContactListScreen(
                        viewModel = viewModel,
                        isSearchBarVisible = isSearchBarVisible,
                        hasActiveFakeCall = activeFakeCallTime != null,
                        onMenuClick = { showMenu = true }
                    )
                }
                composable("add_contact") {
                    AddContactDialog(
                        viewModel = viewModel,
                        onDismiss = {
                            if (navController.currentDestination?. route == "add_contact") {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }

        if (showEasterEgg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment. Center
            ) {
                TerminalWindow(password ?: "----")
            }
        }

        if (showMenu) {
            MenuDialog(
                viewModel = viewModel,
                activeFakeCallTime = activeFakeCallTime,
                onDismiss = { showMenu = false },
                onFakeCallClick = {
                    showMenu = false
                    showFakeCallDialog = true
                },
                onCancelFakeCall = {
                    cancelScheduledFakeCall(context)
                    activeFakeCallTime = null
                    showMenu = false
                },
                onHiddenContactsClick = {
                    showMenu = false
                    showHiddenContactsDialog = true
                }
            )
        }

        if (showFakeCallDialog) {
            FakeCallSetupDialog(
                viewModel = viewModel,
                onDismiss = { showFakeCallDialog = false },
                onScheduled = { time ->
                    activeFakeCallTime = time
                    showFakeCallDialog = false
                }
            )
        }

        if (showHiddenContactsDialog) {
            HiddenContactsAuthDialog(
                viewModel = viewModel,
                onDismiss = { showHiddenContactsDialog = false }
            )
        }
    }
}

@Composable
fun TerminalWindow(password: String) {
    val matrixGreen = Color(0xFF00FF41)

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(450.dp),
        color = Color.Black,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.DarkGray),
        shadowElevation = 24.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "root@system: ~",
                    color = Color. Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            ) {
                TerminalMatrixEffect()

                Column(
                    modifier = Modifier. fillMaxSize(),
                    verticalArrangement = Arrangement. Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "RUNNING DECRYPTOR.. .",
                        color = matrixGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier. alpha(0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(Color. Black. copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .border(1.dp, matrixGreen. copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = password,
                            color = matrixGreen,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily. Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SECRET_KEY_FOUND",
                        color = matrixGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier. alpha(0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalMatrixEffect() {
    val matrixGreen = Color(0xFF00FF41)
    val characters = remember { "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$#@%&*". toList() }
    val columnCount = 18
    val streamStates = remember {
        List(columnCount) {
            mutableFloatStateOf(Random.nextInt(-20, 0).toFloat())
        }
    }

    // FIX 3 & 12: Cache Paint object for performance
    val paint = remember {
        android.graphics.Paint().apply {
            isFakeBoldText = true
        }
    }

    // FIX 2: CPU Overuse - isActive check to prevent memory leak
    LaunchedEffect(Unit) {
        while (isActive) {
            streamStates.forEach { state ->
                state.floatValue += 0.4f
                if (state.floatValue > 30) state.floatValue = Random.nextInt(-20, 0).toFloat()
            }
            delay(MATRIX_DELAY)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val charSize = size. width / columnCount
        streamStates.forEachIndexed { index, state ->
            val x = index * charSize
            for (i in 0.. 20) {
                val y = (state.floatValue + i) * charSize
                if (y < -charSize || y > size.height + charSize) continue

                val char = characters[Random.nextInt(characters.size)]
                val alpha = (1f - (i / 20f)).coerceIn(0f, 0.4f)

                drawContext.canvas.nativeCanvas.drawText(
                    char. toString(),
                    x,
                    y,
                    paint. apply {
                        color = matrixGreen.toArgb()
                        textSize = charSize * 0.7f
                        this.alpha = (alpha * 255).toInt()
                    }
                )
            }
        }
    }
}

@Composable
fun StarfieldBackground(listState: LazyListState) {
    val stars = remember {
        List(40) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 1.5f + 0.5f,
                alpha = Random.nextFloat() * 0.5f + 0.2f,
                speedMultiplier = Random.nextFloat() * 0.2f + 0.05f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "twinkle")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )

    Spacer(
        modifier = Modifier
            . fillMaxSize()
            .drawBehind {
                val scrollOffset = listState.firstVisibleItemIndex * 500f + listState.firstVisibleItemScrollOffset
                stars.forEach { star ->
                    val parallaxY = (star.y * size.height - (scrollOffset * star.speedMultiplier)) % size.height
                    val finalY = if (parallaxY < 0f) parallaxY + size.height else parallaxY
                    val center = Offset(star.x * size.width, finalY)

                    drawCircle(
                        color = Color.White. copy(alpha = star.alpha * twinkleAlpha),
                        radius = star.size,
                        center = center
                    )
                }
            }
    )
}

data class Star(val x: Float, val y: Float, val size: Float, val alpha: Float, val speedMultiplier: Float)

@Composable
fun BottomNavigationBar(navController:  NavHostController) {
    val items = remember {
        listOf(
            NavigationItem("history", "Sonlar", Icons. Rounded.History),
            NavigationItem("contacts", "Kişiler", Icons.Rounded. People),
            NavigationItem("add_contact", "Ekle", Icons.Rounded.PersonAdd)
        )
    }

    Surface(
        color = SurfaceGrey. copy(alpha = 0.8f),
        tonalElevation = 0.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White. copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?. route

            items.forEach { item ->
                val selected = currentRoute == item.route
                LiquidNavItem(item, selected) {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidNavItem(item: NavigationItem, selected: Boolean, onClick: () -> Unit) {
    val fillLevel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "fill"
    )

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                }
                if (fillLevel > 0.01f) {
                    this.clipPath(path) {
                        drawRect(
                            brush = Brush.verticalGradient(listOf(ElectricPurple, VividPink)),
                            topLeft = Offset(0f, size.height * (1f - fillLevel)),
                            size = size
                        )
                    }
                }
            }

            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(22.dp),
                tint = if (selected) DeepCharcoal else SoftGrey
            )
        }
        AnimatedVisibility(visible = selected) {
            Text(
                text = item.title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VividPink
            )
        }
    }
}

data class NavigationItem(val route: String, val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    viewModel: ContactViewModel,
    isSearchBarVisible: Boolean,
    hasActiveFakeCall: Boolean,
    onMenuClick: () -> Unit,
    showOnlyHidden: Boolean = false
) {
    val contacts by if (showOnlyHidden) viewModel.hiddenContacts. collectAsState() else viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        StarfieldBackground(listState)

        Column(modifier = Modifier.fillMaxSize()) {
            if (! showOnlyHidden) {
                AnimatedVisibility(
                    visible = isSearchBarVisible,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                . height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceGrey. copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (searchQuery. isEmpty()) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = "Logo",
                                        modifier = Modifier.height(24.dp).alpha(0.6f)
                                    )
                                }

                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onFocusChanged { focusState ->
                                            if (! focusState.isFocused) {
                                                focusManager.clearFocus()
                                            }
                                        },
                                    placeholder = null,
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(20.dp)) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color. Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = ElectricPurple,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box {
                            IconButton(
                                onClick = onMenuClick,
                                modifier = Modifier
                                    .size(44.dp)
                                    . background(SurfaceGrey.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Rounded. MoreVert, contentDescription = "Menü", tint = VividPink, modifier = Modifier.size(20.dp))
                            }
                            if (hasActiveFakeCall) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        . align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        . background(VividPink, CircleShape)
                                        . border(2.dp, DeepCharcoal, CircleShape)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Gizli Kişiler",
                    modifier = Modifier.padding(24.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VividPink
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (contacts.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                    ) {
                        itemsIndexed(contacts, key = { _, contact -> contact.id }) { _, contact ->
                            val isExpanded = expandedId == contact. id
                            QuickSwipeContactItem(
                                contact = contact,
                                isExpanded = isExpanded,
                                isScrolling = isScrolling,
                                viewModel = viewModel,
                                onExpand = { expandedId = if (isExpanded) null else contact.id }
                            )
                        }
                    }

                    if (expandedId == null && !showOnlyHidden) {
                        FastScrollSidebar(
                            contacts = contacts,
                            listState = listState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickSwipeContactItem(contact: Contact, isExpanded: Boolean, isScrolling: Boolean, viewModel: ContactViewModel, onExpand: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val maxSwipe = with(density) { 110.dp.toPx() }
    val swipeOffset = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    swipeOffset.value > 50f -> CallGreen. copy(alpha = 0.8f)
                    swipeOffset.value < -50f -> MsgBlue.copy(alpha = 0.8f)
                    else -> Color. Transparent
                }
            )
    ) {
        Row(
            modifier = Modifier. matchParentSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Rounded.Call,
                null,
                tint = Color.White.copy(alpha = (swipeOffset.value / maxSwipe).coerceIn(0f, 1f)),
                modifier = Modifier.size(24.dp)
            )
            Icon(
                Icons.AutoMirrored.Rounded.Message,
                null,
                tint = Color.White.copy(alpha = (-swipeOffset.value / maxSwipe).coerceIn(0f, 1f)),
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(isExpanded, isScrolling) {
                    if (isExpanded || isScrolling) return@pointerInput
                    coroutineScope {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                launch {
                                    if (swipeOffset.value > maxSwipe * ACTION_THRESHOLD) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission. CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_CALL).apply { data = "tel:${contact.phoneNumber}".toUri() })
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error making call", e)
                                                Toast.makeText(context, "Arama başlatılamadı", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast. makeText(context, "Arama izni gerekli", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (swipeOffset.value < -maxSwipe * ACTION_THRESHOLD) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = "smsto:${contact.phoneNumber}".toUri() })
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error sending SMS", e)
                                            Toast.makeText(context, "SMS gönderilemedi", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            },
                            onDragCancel = {
                                launch { swipeOffset.animateTo(0f) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                launch {
                                    swipeOffset.snapTo((swipeOffset.value + dragAmount * 0.4f).coerceIn(-maxSwipe * 1.05f, maxSwipe * 1.05f))
                                }
                            }
                        )
                    }
                }
        ) {
            ContactItemContent(contact, isExpanded, onExpand, viewModel)
        }
    }
}

@Composable
fun ContactItemContent(contact: Contact, isExpanded: Boolean, onExpand: () -> Unit, viewModel: ContactViewModel) {
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showEditDialog) {
        EditContactDialog(
            contact = contact,
            onDismiss = { showEditDialog = false },
            onSave = { updatedContact:  Contact ->
                viewModel.updateContact(context, updatedContact)
                showEditDialog = false
            }
        )
    }

    val scale by animateFloatAsState(if (isExpanded) 1.02f else 1f, label = "scale")

    val infiniteTransition = rememberInfiniteTransition(label = "neon")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val ledAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ledAlpha"
    )

    val neonBrush = remember(angle) {
        object :  ShaderBrush() {
            override fun createShader(size: Size): android.graphics.Shader {
                val shader = SweepGradient(
                    size.width / 2,
                    size.height / 2,
                    intArrayOf(
                        ElectricPurple. toArgb(),
                        Color.Black.toArgb(),
                        VividPink.toArgb(),
                        Color.Black.toArgb(),
                        Color. Cyan.toArgb(),
                        ElectricPurple.toArgb()
                    ),
                    null
                )
                val matrix = Matrix()
                matrix.postRotate(angle, size.width / 2, size.height / 2)
                shader.setLocalMatrix(matrix)
                return shader
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (ledAlpha > 0.1f) {
                    Modifier. border(BorderStroke(4.dp, neonBrush), RoundedCornerShape(16.dp))
                } else {
                    Modifier. border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onExpand() }
            .background(SurfaceGrey.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FancyAvatarSmall(isExpanded)

                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact. name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        if (contact.expiryTimestamp != null) {
                            Spacer(modifier = Modifier. width(6.dp))
                            Icon(Icons.Rounded.Timer, "Geçici", tint = VividPink, modifier = Modifier.size(14.dp))
                        }
                    }
                    AnimatedVisibility(visible = isExpanded) {
                        Text(
                            text = contact.phoneNumber,
                            color = SoftGrey,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded. ExpandLess else Icons.Rounded. ExpandMore,
                    contentDescription = null,
                    tint = SoftGrey. copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButtonSmall(Icons.Rounded.Call, "Ara", CallGreen) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_CALL).apply { data = "tel:${contact.phoneNumber}". toUri() })
                            } catch (e: Exception) {
                                Log.e(TAG, "Error making call", e)
                                Toast.makeText(context, "Arama başlatılamadı", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Arama izni gerekli", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionButtonSmall(Icons.AutoMirrored.Rounded. Message, "SMS", MsgBlue) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = "smsto:${contact.phoneNumber}".toUri() })
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending SMS", e)
                            Toast.makeText(context, "SMS gönderilemedi", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionButtonSmall(Icons. Rounded.AutoFixHigh, "Düzen", EditOrange) {
                        showEditDialog = true
                    }
                }
            }
        }
    }
}

@Composable
fun FancyAvatarSmall(isGlowActive: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by if (isGlowActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableFloatStateOf(0.15f) }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ElectricPurple.copy(alpha = if (isGlowActive) glowAlpha else 0.15f),
                        VividPink.copy(alpha = if (isGlowActive) glowAlpha else 0.15f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment. Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val headRadius = size.width * 0.25f
            drawCircle(
                color = ElectricPurple,
                radius = headRadius,
                center = Offset(size.width / 2, size. height * 0.3f)
            )
            val path = Path().apply {
                moveTo(size. width * 0.15f, size.height * 0.9f)
                quadraticTo(
                    size.width * 0.5f, size.height * 0.5f,
                    size.width * 0.85f, size.height * 0.9f
                )
            }
            drawPath(path = path, color = ElectricPurple)
        }
    }
}

@Composable
fun BoxScope.FastScrollSidebar(
    contacts: List<Contact>,
    listState: LazyListState
) {
    val alphabet = remember(contacts) {
        contacts.map { it. name.firstOrNull()?.uppercase() ?: "#" }. distinct().sorted()
    }
    val coroutineScope = rememberCoroutineScope()
    var barHeight by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 4.dp)
            .width(20.dp)
            .fillMaxHeight(0.6f)
            .onGloballyPositioned { barHeight = it.size.height. toFloat() }
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceGrey.copy(alpha = 0.3f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .pointerInput(alphabet) {
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y
                    if (barHeight > 0) {
                        val index = ((y / barHeight) * alphabet.size).toInt()
                            .coerceIn(0, alphabet.size - 1)
                        val char = alphabet[index]
                        val contactIndex = contacts.indexOfFirst { it.name. startsWith(char, ignoreCase = true) }
                        if (contactIndex != -1) {
                            coroutineScope.launch { listState.scrollToItem(contactIndex) }
                        }
                    }
                }
            },
        contentAlignment = Alignment. Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement. SpaceEvenly,
            modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
        ) {
            alphabet.forEach { char ->
                Text(
                    text = char,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricPurple. copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ActionButtonSmall(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "buttonScale")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                isPressed = true
                onClick()
            },
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(color. copy(alpha = 0.1f))
                .border(1.dp, color. copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        }

        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(100)
                isPressed = false
            }
        }

        Spacer(modifier = Modifier. height(4.dp))
        Text(label, fontSize = 10.sp, color = color. copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MenuDialog(
    viewModel: ContactViewModel,
    activeFakeCallTime: String?,
    onDismiss: () -> Unit,
    onFakeCallClick: () -> Unit,
    onCancelFakeCall: () -> Unit,
    onHiddenContactsClick: () -> Unit
) {
    val isFakeCallDark by viewModel.isFakeCallDark.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceGrey,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Ayarlar ve Araçlar", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                MenuItem(
                    icon = Icons.AutoMirrored.Rounded. PhoneCallback,
                    title = if (activeFakeCallTime != null) "Fake Call (Aktif:  $activeFakeCallTime)" else "Fake Call (Sahte Arama)",
                    sub = if (activeFakeCallTime != null) "Aramayı iptal etmek için tıklayın" else "Seni arayan biri varmış gibi yap",
                    color = VividPink,
                    onClick = { if (activeFakeCallTime != null) onCancelFakeCall() else onFakeCallClick() }
                )

                MenuItem(
                    icon = Icons.Rounded.Lock,
                    title = "Gizli Bölüm",
                    sub = "Kişileri gizle ve yönet",
                    color = ElectricPurple,
                    onClick = onHiddenContactsClick
                )

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleFakeCallTheme() }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(EditOrange. copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (isFakeCallDark) Icons.Rounded.DarkMode else Icons.Rounded. LightMode, contentDescription = null, tint = EditOrange, modifier = Modifier. size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Fake Call Teması", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(if (isFakeCallDark) "Koyu Tema Aktif" else "Açık Tema Aktif", fontSize = 11.sp, color = SoftGrey)
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, sub:  String, color: Color, onClick:  () -> Unit = {}) {
    Row(
        modifier = Modifier. fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(sub, fontSize = 11.sp, color = SoftGrey)
        }
    }
}

@Composable
fun HiddenContactsAuthDialog(viewModel: ContactViewModel, onDismiss: () -> Unit) {
    val savedPassword by viewModel.password.collectAsState()
    var passwordInput by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val isSettingPassword = savedPassword == null

    var showManagement by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }

    if (!isAuthenticated) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceGrey,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isSettingPassword) Icons.Rounded.Security else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = VividPink,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (isSettingPassword) "Şifre Belirle" else "Gizli Bölüm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.6.dp))
                    Text(
                        if (isSettingPassword) "Lütfen erişim için yeni bir şifre girin" else "Giriş için şifrenizi girin",
                        fontSize = 13.sp,
                        color = SoftGrey
                    )

                    Spacer(modifier = Modifier. height(20.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; showError = false },
                        label = { Text("Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VividPink,
                            unfocusedBorderColor = Color.White. copy(alpha = 0.1f)
                        )
                    )

                    if (showError) {
                        Text("Hatalı şifre!", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isSettingPassword) {
                                if (passwordInput.isNotBlank()) {
                                    viewModel.setPassword(passwordInput)
                                    isAuthenticated = true
                                }
                            } else {
                                if (passwordInput == savedPassword) {
                                    isAuthenticated = true
                                } else {
                                    showError = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividPink)
                    ) {
                        Text(if (isSettingPassword) "Şifreyi Kaydet" else "Giriş Yap")
                    }
                }
            }
        }
    } else if (showPasswordChange) {
        var newPassword by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showPasswordChange = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceGrey,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Şifre Değiştir", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Yeni Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType. NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VividPink,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (newPassword.isNotBlank()) {
                                viewModel. setPassword(newPassword)
                                showPasswordChange = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividPink)
                    ) {
                        Text("Güncelle")
                    }
                }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DeepCharcoal
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (showManagement) "Kişileri Yönet" else "Gizli Kişiler",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = VividPink
                        )
                        Row {
                            IconButton(onClick = { showPasswordChange = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Şifre Değiştir", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showManagement = ! showManagement }) {
                                Icon(
                                    if (showManagement) Icons.Rounded.Check else Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier. size(20.dp))
                            }
                        }
                    }

                    if (showManagement) {
                        val allContacts by viewModel.contacts.collectAsState()
                        val hiddenContactIds by viewModel.hiddenContactIds.collectAsState()

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(allContacts) { _, contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(contact.phoneNumber, color = SoftGrey, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = hiddenContactIds.contains(contact. id),
                                        onCheckedChange = { viewModel.toggleContactVisibility(contact.id) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = VividPink, checkedTrackColor = VividPink. copy(alpha = 0.5f)),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    } else {
                        ContactListScreen(
                            viewModel = viewModel,
                            isSearchBarVisible = false,
                            hasActiveFakeCall = false,
                            onMenuClick = {},
                            showOnlyHidden = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FakeCallSetupDialog(viewModel: ContactViewModel, onDismiss: () -> Unit, onScheduled: (String) -> Unit) {
    val contacts by viewModel.contacts.collectAsState()
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var callerName by remember { mutableStateOf("") }
    var delaySeconds by remember { mutableIntStateOf(10) }
    var useSchedule by remember { mutableStateOf(false) }
    var scheduledTime by remember { mutableStateOf("Saat Seçin") }
    var calendar by remember { mutableStateOf<Calendar?>(null) }

    val context = LocalContext.current

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
            calendar = cal
            scheduledTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            useSchedule = true
        },
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        Calendar.getInstance().get(Calendar.MINUTE),
        true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceGrey,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment. CenterHorizontally) {
                Text("Fake Call Planla", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(DeepCharcoal, RoundedCornerShape(12.dp)).padding(6.dp)) {
                    val hiddenContactIds by viewModel.hiddenContactIds.collectAsState()
                    LazyColumn {
                        itemsIndexed(contacts) { _, contact ->
                            if (! hiddenContactIds.contains(contact.id)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 14.sp,
                                    color = if (selectedContact?. id == contact.id) VividPink else Color.White,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedContact = contact
                                        callerName = contact.name
                                    }. padding(6.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Özel / Bilinmeyen",
                                fontSize = 14.sp,
                                color = if (selectedContact == null) VividPink else Color.White,
                                modifier = Modifier. fillMaxWidth().clickable {
                                    selectedContact = null
                                    callerName = ""
                                }.padding(6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CustomTextField(
                    value = callerName,
                    onValueChange = { callerName = it; selectedContact = null },
                    label = "Arayan İsmi",
                    icon = Icons.Rounded.Edit
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement. SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (useSchedule) "Zamanlı:  $scheduledTime" else "Gecikme: $delaySeconds sn", color = Color.White, fontSize = 14.sp)
                        Text(text = if (useSchedule) "Belirlenen saatte" else "Geri sayımla", fontSize = 10.sp, color = SoftGrey)
                    }
                    Button(
                        onClick = { timePickerDialog.show() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, VividPink. copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Saat Seç", color = VividPink, fontSize = 11.sp)
                    }
                }

                if (! useSchedule) {
                    Slider(
                        value = delaySeconds. toFloat(),
                        onValueChange = { delaySeconds = it. roundToInt() },
                        valueRange = 5f..60f,
                        colors = SliderDefaults.colors(thumbColor = VividPink, activeTrackColor = VividPink)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick. dp))

                MenuItem(
                    icon = Icons.AutoMirrored.Rounded.PhoneCallback,
                    title = if (activeFakeCallTime != null) "Fake Call (Aktif:  $activeFakeCallTime)" else "Fake Call (Sahte Arama)",
                    sub = if (activeFakeCallTime != null) "Aramayı iptal etmek için tıklayın" else "Seni arayan biri varmış gibi yap",
                    color = VividPink,
                    onClick = { if (activeFakeCallTime != null) onCancelFakeCall() else onFakeCallClick() }
                )

                MenuItem(
                    icon = Icons. Rounded.Lock,
                    title = "Gizli Bölüm",
                    sub = "Kişileri gizle ve yönet",
                    color = ElectricPurple,
                    onClick = onHiddenContactsClick
                )

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleFakeCallTheme() }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(EditOrange. copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (isFakeCallDark) Icons.Rounded.DarkMode else Icons.Rounded. LightMode, contentDescription = null, tint = EditOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Fake Call Teması", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(if (isFakeCallDark) "Koyu Tema Aktif" else "Açık Tema Aktif", fontSize = 11.sp, color = SoftGrey)
                    }
                }
            }
        }
    }
}

// ============ MENU ITEM ============
@Composable
fun MenuItem(icon: ImageVector, title: String, sub:  String, color: Color, onClick:  () -> Unit = {}) {
    Row(
        modifier = Modifier. fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier. size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(sub, fontSize = 11.sp, color = SoftGrey)
        }
    }
}

// ============ HIDDEN CONTACTS AUTH DIALOG ============
@Composable
fun HiddenContactsAuthDialog(viewModel: ContactViewModel, onDismiss: () -> Unit) {
    val savedPassword by viewModel.password.collectAsState()
    var passwordInput by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val isSettingPassword = savedPassword == null

    var showManagement by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }

    if (! isAuthenticated) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceGrey,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isSettingPassword) Icons.Rounded.Security else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = VividPink,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (isSettingPassword) "Şifre Belirle" else "Gizli Bölüm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color. White
                    )
                    Spacer(modifier = Modifier.height(6.6.dp))
                    Text(
                        if (isSettingPassword) "Lütfen erişim için yeni bir şifre girin" else "Giriş için şifrenizi girin",
                        fontSize = 13.sp,
                        color = SoftGrey
                    )

                    Spacer(modifier = Modifier. height(20.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; showError = false },
                        label = { Text("Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VividPink,
                            unfocusedBorderColor = Color.White. copy(alpha = 0.1f)
                        )
                    )

                    if (showError) {
                        Text("Hatalı şifre!", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isSettingPassword) {
                                if (passwordInput.isNotBlank()) {
                                    viewModel.setPassword(passwordInput)
                                    isAuthenticated = true
                                }
                            } else {
                                if (passwordInput == savedPassword) {
                                    isAuthenticated = true
                                } else {
                                    showError = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividPink)
                    ) {
                        Text(if (isSettingPassword) "Şifreyi Kaydet" else "Giriş Yap")
                    }
                }
            }
        }
    } else if (showPasswordChange) {
        var newPassword by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showPasswordChange = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceGrey,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Şifre Değiştir", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Yeni Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VividPink,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (newPassword.isNotBlank()) {
                                viewModel. setPassword(newPassword)
                                showPasswordChange = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividPink)
                    ) {
                        Text("Güncelle")
                    }
                }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DeepCharcoal
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (showManagement) "Kişileri Yönet" else "Gizli Kişiler",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = VividPink
                        )
                        Row {
                            IconButton(onClick = { showPasswordChange = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Şifre Değiştir", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showManagement = ! showManagement }) {
                                Icon(
                                    if (showManagement) Icons.Rounded.Check else Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (showManagement) {
                        val allContacts by viewModel.contacts.collectAsState()
                        val hiddenContactIds by viewModel.hiddenContactIds.collectAsState()

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(allContacts) { _, contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(contact.phoneNumber, color = SoftGrey, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = hiddenContactIds.contains(contact. id),
                                        onCheckedChange = { viewModel.toggleContactVisibility(contact.id) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = VividPink, checkedTrackColor = VividPink. copy(alpha = 0.5f)),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                                HorizontalDivider(color = Color.White. copy(alpha = 0.05f))
                            }
                        }
                    } else {
                        ContactListScreen(
                            viewModel = viewModel,
                            isSearchBarVisible = false,
                            hasActiveFakeCall = false,
                            onMenuClick = {},
                            showOnlyHidden = true
                        )
                    }
                }
            }
        }
    }
}

// ============ FAKE CALL SETUP DIALOG ============
@Composable
fun FakeCallSetupDialog(viewModel: ContactViewModel, onDismiss: () -> Unit, onScheduled: (String) -> Unit) {
    val contacts by viewModel.contacts.collectAsState()
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var callerName by remember { mutableStateOf("") }
    var delaySeconds by remember { mutableIntStateOf(10) }
    var useSchedule by remember { mutableStateOf(false) }
    var scheduledTime by remember { mutableStateOf("Saat Seçin") }
    var calendar by remember { mutableStateOf<Calendar?>(null) }

    val context = LocalContext.current

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
            calendar = cal
            scheduledTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            useSchedule = true
        },
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        Calendar.getInstance().get(Calendar.MINUTE),
        true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceGrey,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment. CenterHorizontally) {
                Text("Fake Call Planla", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(DeepCharcoal, RoundedCornerShape(12.dp)).padding(6.dp)) {
                    val hiddenContactIds by viewModel.hiddenContactIds.collectAsState()
                    LazyColumn {
                        itemsIndexed(contacts) { _, contact ->
                            if (! hiddenContactIds.contains(contact.id)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 14.sp,
                                    color = if (selectedContact?. id == contact.id) VividPink else Color.White,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedContact = contact
                                        callerName = contact.name
                                    }. padding(6.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Özel / Bilinmeyen",
                                fontSize = 14.sp,
                                color = if (selectedContact == null) VividPink else Color.White,
                                modifier = Modifier. fillMaxWidth().clickable {
                                    selectedContact = null
                                    callerName = ""
                                }.padding(6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CustomTextField(
                    value = callerName,
                    onValueChange = { callerName = it; selectedContact = null },
                    label = "Arayan İsmi",
                    icon = Icons.Rounded.Edit
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (useSchedule) "Zamanlı:  $scheduledTime" else "Gecikme: $delaySeconds sn", color = Color.White, fontSize = 14.sp)
                        Text(text = if (useSchedule) "Belirlenen saatte" else "Geri sayımla", fontSize = 10.sp, color = SoftGrey)
                    }
                    Button(
                        onClick = { timePickerDialog.show() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, VividPink. copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Saat Seç", color = VividPink, fontSize = 11.sp)
                    }
                }

                if (! useSchedule) {
                    Slider(
                        value = delaySeconds. toFloat(),
                        onValueChange = { delaySeconds = it.roundToInt() },
                        valueRange = MIN_DELAY_SECONDS. toFloat()..MAX_DELAY_SECONDS.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = VividPink, activeTrackColor = VividPink)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (! Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error opening overlay settings", e)
                                Toast.makeText(context, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val name = callerName.ifBlank { "Bilinmeyen Numara" }
                            val number = selectedContact?.phoneNumber ?: ""

                            val intent = Intent(context, FakeCallReceiver::class.java).apply {
                                putExtra("EXTRA_NAME", name)
                                putExtra("EXTRA_NUMBER", number)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val triggerTime = if (useSchedule && calendar != null) calendar!! .timeInMillis else System. currentTimeMillis() + (delaySeconds * 1000)

                            try {
                                alarmManager. setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                                showFakeCallNotification(context)
                                val finalTimeStr = if(useSchedule) scheduledTime else "$delaySeconds sn"
                                onScheduled(finalTimeStr)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting alarm", e)
                                Toast.makeText(context, "Çağrı planlanamadı", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VividPink)
                ) {
                    Text("Aramayı Planla", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============ FAKE CALL NOTIFICATION ============
private fun showFakeCallNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "fake_call_waiting"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Sistem Güncelleme", NotificationManager. IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android. R.drawable.star_on)
        .setContentTitle("Bugün çok güzel bir gün")
        .setContentText("Her şey yolunda gidecek.")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setAutoCancel(false)
        .setSilent(true)
        .setVisibility(NotificationCompat. VISIBILITY_PUBLIC)

    try {
        notificationManager.notify(1, builder.build())
    } catch (e: Exception) {
        Log.e(TAG, "Error showing notification", e)
    }
}

// ============ CANCEL SCHEDULED FAKE CALL ============
private fun cancelScheduledFakeCall(context: Context) {
    try {
        val intent = Intent(context, FakeCallReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
    } catch (e: Exception) {
        Log.e(TAG, "Error canceling fake call", e)
    }
}

// ============ EMPTY STATE ============
@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement. Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = SurfaceGrey
            )
        }
        Spacer(modifier = Modifier. height(12.dp))
        Text("Aradığın kişiyi bulamadım.. .", color = SoftGrey, fontSize = 14.sp)
    }
}

// ============ ADD CONTACT DIALOG ============
@Composable
fun AddContactDialog(viewModel: ContactViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var isTemporary by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGrey)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Yeni Bağlantı", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                CustomTextField(value = name, onValueChange = { name = it }, label = "İsim", icon = Icons.Rounded.Badge)
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(value = number, onValueChange = { number = it }, label = "Telefon", icon = Icons. Rounded.Phone, keyboardType = KeyboardType.Phone)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isTemporary = !isTemporary },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isTemporary,
                        onCheckedChange = { isTemporary = it },
                        colors = CheckboxDefaults.colors(checkedColor = VividPink),
                        modifier = Modifier. scale(0.9f)
                    )
                    Text("24 Saat Sonra Silinsin", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (name. isNotBlank() && number.isNotBlank()) {
                            viewModel.addContact(context, name, number, isTemporary)
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                ) {
                    Text("Rehbere Ekle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============ EDIT CONTACT DIALOG ============
@Composable
fun EditContactDialog(contact: Contact, onDismiss:  () -> Unit, onSave: (Contact) -> Unit) {
    var name by remember { mutableStateOf(contact.name) }
    var number by remember { mutableStateOf(contact.phoneNumber) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGrey)
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FancyAvatarSmall(true)
                Spacer(modifier = Modifier. height(16.dp))
                CustomTextField(value = name, onValueChange = { name = it }, label = "İsim", icon = Icons.Rounded.Person)
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(value = number, onValueChange = { number = it }, label = "Numara", icon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onSave(contact. copy(name = name, phoneNumber = number)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============ CUSTOM TEXT FIELD ============
@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricPurple,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedTextColor = Color. White,
            unfocusedTextColor = Color.White
        )
    )
}

// ============ HISTORY SECTION ============

data class MockCall(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val callType: CallType,
    val location: String,
    val duration:  String,
    val ringCount: Int = 0,
    val frequency: Int = 1,
    val timestamp: String
)

enum class CallType {
    Incoming, Outgoing, Missed
}

// ✅ FIX 4 & 8: History Screen with proper null safety and cursor handling
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val calls = remember { mutableStateListOf<MockCall>() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission. READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val callList = mutableListOf<MockCall>()
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null, CallLog.Calls.DATE + " DESC"
                )

                cursor?.use { c ->
                    // ✅ FIX 9: Cache column indices to avoid repeated queries
                    val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                    val durationIdx = c.getColumnIndex(CallLog.Calls. DURATION)
                    val locationIdx = c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)

                    var count = 0
                    while (c.moveToNext() && count < MAX_CALL_LOG_ITEMS) {
                        try {
                            val num = if (numberIdx >= 0) c.getString(numberIdx) else null ?: "Gizli Numara"
                            val name = if (nameIdx >= 0) c.getString(nameIdx) else null ?: num
                            val type = if (typeIdx >= 0) c.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                            val date = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                            val durationSec = if (durationIdx >= 0) c.getInt(durationIdx) else 0
                            val location = if (locationIdx >= 0 && locationIdx != -1) c.getString(locationIdx) else null
                            val locationStr = location ?: "Bilinmiyor"

                            val callType = when (type) {
                                CallLog.Calls. INCOMING_TYPE -> CallType.Incoming
                                CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
                                CallLog. Calls.MISSED_TYPE -> CallType.Missed
                                else -> CallType.Incoming
                            }

                            val durationStr = if (durationSec > 60) "${durationSec / 60} dk ${durationSec % 60} sn" else "$durationSec sn"
                            val timeStr = android.text.format.DateFormat. format("HH:mm", Date(date)).toString()

                            callList.add(MockCall(
                                name = name,
                                phoneNumber = num,
                                callType = callType,
                                location = locationStr,
                                duration = durationStr,
                                timestamp = timeStr
                            ))
                            count++
                        } catch (e: Exception) {
                            Log.w(TAG, "Error reading call log entry", e)
                        }
                    }
                }
                calls.clear()
                calls.addAll(callList)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading call log", e)
                Toast.makeText(context, "Çağrı geçmişi okunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ FIX 10: Performance optimization - calculate stats only when needed
    val callStats by remember {
        derivedStateOf {
            var totalSec = 0L
            calls.filter { it.callType != CallType.Missed }.forEach { call ->
                try {
                    val valStr = call.duration.replace(" dk", "").replace(" sn", "").trim()
                    val parts = valStr.split(" ").filter { it.isNotEmpty() }
                    when (parts.size) {
                        2 -> {
                            val minutes = parts[0].toLongOrNull() ?: 0L
                            val seconds = parts[1].toLongOrNull() ?: 0L
                            totalSec += (minutes * 60) + seconds
                        }
                        1 -> {
                            totalSec += valStr.toLongOrNull() ?: 0L
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing duration:  ${call.duration}", e)
                }
            }
            val totalDuration = if (totalSec > 60) "${totalSec / 60} dk ${totalSec % 60} sn" else "$totalSec sn"
            val missed = calls.count { it.callType == CallType.Missed }
            val rate = if (calls.isNotEmpty()) "${(missed * 100) / calls.size}%" else "0%"
            Pair(totalDuration, rate)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepCharcoal)) {
        Text(
            "Arama Analitiği",
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        WeeklySummaryCard(stats = callStats)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
        ) {
            items(items = calls, key = { it.id }) { call ->
                HistoryItem(
                    call = call,
                    onDelete = { calls.remove(call) }
                )
            }
        }
    }
}

// ============ WEEKLY SUMMARY CARD ============
@Composable
fun WeeklySummaryCard(stats:  Pair<String, String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(100.dp),
        color = Color.White. copy(alpha = 0.05f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier. fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Toplam Konuşma", color = SoftGrey, fontSize = 11.sp)
                Text(stats.first, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White. copy(alpha = 0.1f)))
            Column(horizontalAlignment = Alignment.End) {
                Text("Cevapsız Oranı", color = SoftGrey, fontSize = 11.sp)
                Text(stats.second, color = VividPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============ HISTORY ITEM ============
@Composable
fun HistoryItem(call: MockCall, onDelete: () -> Unit) {
    val density = LocalDensity.current
    val swipeOffset = remember { Animatable(0f) }
    val maxDismiss = with(density) { -100. dp.toPx() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (swipeOffset.value < -20f) Color.Red. copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Icon(
            Icons. Rounded.DeleteSweep,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).alpha(0.6f)
        )

        Row(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(DeepCharcoal)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val threshold = 0.7f
                                if (swipeOffset.value < maxDismiss * threshold) {
                                    swipeOffset.animateTo(maxDismiss, spring(stiffness = Spring.StiffnessLow))
                                    onDelete()
                                } else {
                                    swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { swipeOffset.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(maxDismiss, 0f))
                            }
                        }
                    )
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (call.callType) {
                CallType. Incoming -> Icons.AutoMirrored.Rounded.CallReceived to CallGreen
                CallType.Outgoing -> Icons.AutoMirrored.Rounded.CallMade to MsgBlue
                CallType.Missed -> Icons.AutoMirrored.Rounded. CallMissed to Color.Red
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color. copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, color. copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment. Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier. width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (call.frequency > 1) "${call.name} (${call.frequency})" else call.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = call.location, color = SoftGrey, fontSize = 11.sp)
                Text(
                    text = if (call. callType == CallType.Missed) "Çaldırıldı • ${call.ringCount} kez" else call.duration,
                    color = color. copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = call.timestamp,
                color = SoftGrey,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
