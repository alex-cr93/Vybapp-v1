package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ActiveChatTracker
import com.example.core.CallSoundManager
import com.example.core.ConnectivityObserver
import com.example.core.NotificationHelper
import com.example.core.NotificationNavTarget
import com.example.core.PreferencesStore
import com.example.core.PushNotificationRegistrar
import com.example.core.VybPermissionsHelper
import com.example.core.configureAppCheck
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.repository.AiRepository
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.ConfettiOverlay
import com.example.ui.components.OfflineIndicator
import com.example.ui.components.VybLogoBadge
import com.example.ui.screens.AssistantModal
import com.example.ui.screens.AssistantToolTab
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallModal
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.OnboardingProfileScreen
import com.example.ui.screens.PermissionsOnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VYBTheme
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMagenta
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatFilter
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.RegistrationViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object RouteInicio

@Serializable
object RouteChats

@Serializable
object RouteLlamadas

@Serializable
object RoutePerfil

private data class VybNavItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private data class AssistantLaunchRequest(
    val tab: AssistantToolTab = AssistantToolTab.CHAT,
    val prompt: String? = null,
    val draft: String = "",
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList()
)

private sealed interface PendingAnyCallRequest {
    val isVideo: Boolean

    data class Direct(
        val calleeUid: String,
        val calleeName: String,
        val calleeAvatar: String?,
        override val isVideo: Boolean
    ) : PendingAnyCallRequest

    data class Group(
        val chat: Chat,
        override val isVideo: Boolean
    ) : PendingAnyCallRequest

    data class Join(
        val session: CallSession,
        override val isVideo: Boolean
    ) : PendingAnyCallRequest
}

private data class InAppNotificationBannerData(
    val id: String,
    val title: String,
    val body: String,
    val channelType: String,
    val targetChatId: String? = null,
    val openRequestsTab: Boolean = false
)

class MainActivity : ComponentActivity() {

    private var pendingNavTarget by mutableStateOf<NotificationNavTarget?>(null)
    private var permissionCheckNonce by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        permissionCheckNonce++
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
        NotificationHelper.extractNavTarget(intent)?.let {
            pendingNavTarget = it
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST run before any Firebase AI / Firestore call
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        NotificationHelper.extractNavTarget(intent)?.let {
            pendingNavTarget = it
        }

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val windowSizeClass = calculateWindowSizeClass(this)
            val preferencesStore = remember { PreferencesStore(context.applicationContext) }
            val connectivityObserver = remember { ConnectivityObserver(context.applicationContext) }

            val themeMode by preferencesStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val isOnline by connectivityObserver.observeIsOnline().collectAsStateWithLifecycle(initialValue = true)

            val auth = remember { Firebase.auth }
            var currentFirebaseUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    currentFirebaseUser = firebaseAuth.currentUser
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            val authViewModel: AuthViewModel = viewModel()

            var showLaunchSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1250L)
                showLaunchSplash = false
            }

            VYBTheme(themeMode = themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val fbUser = currentFirebaseUser
                    if (fbUser == null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OfflineIndicator(isOnline = isOnline)
                            AuthScreen(
                                authViewModel = authViewModel,
                                themeMode = themeMode,
                                onSelectTheme = { mode ->
                                    scope.launch { preferencesStore.setThemeMode(mode) }
                                }
                            )
                        }
                    } else {
                        key(fbUser.uid) {
                            AuthenticatedVybShell(
                                fbUser = fbUser,
                                widthSizeClass = windowSizeClass.widthSizeClass,
                                isOnline = isOnline,
                                permissionCheckNonce = permissionCheckNonce,
                                onRefreshPermissions = { permissionCheckNonce++ },
                                preferencesStore = preferencesStore,
                                authViewModel = authViewModel,
                                pendingNavTarget = pendingNavTarget,
                                onConsumeNavTarget = { pendingNavTarget = null }
                            )
                        }
                    }

                    VybLaunchSplashOverlay(visible = showLaunchSplash)
                }
            }
        }
    }
}

@Composable
private fun VybLaunchSplashOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(340, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 1.08f, animationSpec = tween(340, easing = FastOutSlowInEasing))
    ) {
        val logoScale = remember { Animatable(0.72f) }
        val haloAlpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            launch {
                haloAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        val infiniteTransition = rememberInfiniteTransition(label = "vyb_launch_glow")
        val sweepAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vyb_launch_sweep"
        )
        val auraPulse by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "vyb_launch_aura"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("launch_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        val s = logoScale.value
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f
                    val center = Offset(cx, cy)
                    val auraRadius = size.minDimension * 0.52f * auraPulse

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VybElectricBlue.copy(alpha = 0.30f * haloAlpha.value),
                                VybViolet.copy(alpha = 0.18f * haloAlpha.value),
                                Color.Transparent
                            ),
                            center = center,
                            radius = auraRadius
                        ),
                        radius = auraRadius,
                        center = center
                    )

                    val ringStroke = 5.dp.toPx()
                    val ringRadius = size.minDimension * 0.39f
                    rotate(degrees = sweepAngle, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    VybSkyBlue.copy(alpha = 0.90f * haloAlpha.value),
                                    VybElectricBlue.copy(alpha = haloAlpha.value),
                                    VybViolet.copy(alpha = 0.92f * haloAlpha.value),
                                    VybMagenta.copy(alpha = 0.78f * haloAlpha.value),
                                    Color.Transparent
                                ),
                                center = center
                            ),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                        )
                    }
                }
                VybLogoBadge(size = 96.dp)
            }
        }
    }
}

@Composable
private fun AuthenticatedVybShell(
    fbUser: FirebaseUser,
    widthSizeClass: WindowWidthSizeClass,
    isOnline: Boolean,
    permissionCheckNonce: Int,
    onRefreshPermissions: () -> Unit,
    preferencesStore: PreferencesStore,
    authViewModel: AuthViewModel,
    pendingNavTarget: NotificationNavTarget?,
    onConsumeNavTarget: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val vybColors = LocalVybColors.current
    val dbId = remember { context.applicationContext.getString(R.string.firestore_database_id) }
    val db = remember(dbId) { FirebaseFirestore.getInstance(dbId) }

    val userRepo = remember(db) { UserRepository(db) }
    val requestedPermissionsHistory by preferencesStore.requestedPermissions.collectAsStateWithLifecycle(initialValue = emptySet())
    val permissionsSnapshot = remember(permissionCheckNonce, requestedPermissionsHistory) {
        VybPermissionsHelper.snapshot(context, activity, requestedPermissionsHistory)
    }

    LaunchedEffect(fbUser.uid) {
        PushNotificationRegistrar.syncFcmToken(context, userRepo, preferencesStore)
    }

    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile_${fbUser.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(userRepo, preferencesStore, fbUser.uid) as T
        }
    )

    val publicProfileState by profileViewModel.publicProfileState.collectAsStateWithLifecycle()
    val privateUser by profileViewModel.privateProfileState.collectAsStateWithLifecycle()
    val profileActionMsg by profileViewModel.actionMessage.collectAsStateWithLifecycle()
    val aiModelChoice by profileViewModel.aiModelChoice.collectAsStateWithLifecycle()
    val privateLocation by profileViewModel.privateLocation.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profileActionMsg) {
        val msg = profileActionMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            profileViewModel.clearMessage()
        }
    }

    var loadingTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(publicProfileState) {
        if (publicProfileState is UiState.Loading) {
            loadingTimedOut = false
            delay(6000L)
            loadingTimedOut = true
        } else {
            loadingTimedOut = false
        }
    }

    when (val pState = publicProfileState) {
        UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VybLogoBadge(size = 76.dp)
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (loadingTimedOut) {
                            "La conexión con el servidor está tardando..."
                        } else {
                            "Cargando tu espacio VYB..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = vybColors.secondaryText
                    )
                    if (loadingTimedOut) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                loadingTimedOut = false
                                profileViewModel.retryProfileLoad()
                            }) {
                                Text("Reintentar")
                            }
                            TextButton(onClick = { authViewModel.signOut(context) }) {
                                Text("Cerrar sesión")
                            }
                        }
                    }
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VybLogoBadge(size = 64.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("No se pudo cargar tu perfil", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(pState.message, style = MaterialTheme.typography.bodySmall, color = vybColors.secondaryText)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { profileViewModel.retryProfileLoad() }) {
                            Text("Reintentar")
                        }
                        TextButton(onClick = { authViewModel.signOut(context) }) {
                            Text("Cerrar sesión")
                        }
                    }
                }
            }
        }
        is UiState.Success -> {
            val publicUser = pState.data
            if (publicUser == null) {
                val registrationViewModel: RegistrationViewModel = viewModel(
                    key = "reg_${fbUser.uid}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            RegistrationViewModel(userRepo, fbUser.uid) as T
                    }
                )
                OnboardingProfileScreen(
                    registrationViewModel = registrationViewModel,
                    initialDisplayName = fbUser.displayName ?: "",
                    initialEmail = fbUser.email,
                    initialPhone = fbUser.phoneNumber,
                    initialAvatarUrl = fbUser.photoUrl?.toString(),
                    isAccountVerified = fbUser.isEmailVerified || !fbUser.phoneNumber.isNullOrBlank(),
                    onSignOut = { authViewModel.signOut(context) }
                )
            } else if (!permissionsSnapshot.allRequiredGranted) {
                PermissionsOnboardingScreen(
                    snapshot = permissionsSnapshot,
                    preferencesStore = preferencesStore,
                    onRefreshPermissions = onRefreshPermissions,
                    onSignOut = { authViewModel.signOut(context) }
                )
            } else {
                VybMainScaffold(
                    db = db,
                    userRepo = userRepo,
                    preferencesStore = preferencesStore,
                    publicUser = publicUser,
                    privateUser = privateUser,
                    isAccountVerified = fbUser.isEmailVerified || !fbUser.phoneNumber.isNullOrBlank(),
                    widthSizeClass = widthSizeClass,
                    isOnline = isOnline,
                    aiModelChoice = aiModelChoice,
                    privateAreaLabel = privateLocation?.areaLabel,
                    profileViewModel = profileViewModel,
                    snackbarHostState = snackbarHostState,
                    pendingNavTarget = pendingNavTarget,
                    onConsumeNavTarget = onConsumeNavTarget,
                    onSignOut = { authViewModel.signOut(context) }
                )
            }
        }
    }
}

@Composable
private fun VybMainScaffold(
    db: FirebaseFirestore,
    userRepo: UserRepository,
    preferencesStore: PreferencesStore,
    publicUser: PublicUser,
    privateUser: PrivateUser?,
    isAccountVerified: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    isOnline: Boolean,
    aiModelChoice: com.example.data.model.AiModelChoice,
    privateAreaLabel: String?,
    profileViewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    pendingNavTarget: NotificationNavTarget?,
    onConsumeNavTarget: () -> Unit,
    onSignOut: () -> Unit
) {
    val feedRepo = remember(db) { FeedRepository(db) }
    val chatRepo = remember(db, feedRepo) { ChatRepository(db, feedRepo) }
    val callRepo = remember(db) { CallRepository(db) }
    val aiRepo = remember { AiRepository() }

    val feedViewModel: FeedViewModel = viewModel(
        key = "feed_${publicUser.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedViewModel(feedRepo, userRepo, preferencesStore, publicUser.userId) as T
        }
    )

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_${publicUser.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(chatRepo, userRepo, publicUser.userId) as T
        }
    )

    val callViewModel: CallViewModel = viewModel(
        key = "call_${publicUser.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CallViewModel(callRepo, userRepo, publicUser.userId) as T
        }
    )

    val assistantViewModel: AssistantViewModel = viewModel(
        key = "ai_${publicUser.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AssistantViewModel(aiRepo) as T
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vybColors = LocalVybColors.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val totalUnread by chatViewModel.totalUnreadCount.collectAsStateWithLifecycle()
    val pendingRequestCount by chatViewModel.pendingIncomingCount.collectAsStateWithLifecycle()
    val incomingPendingRequests by chatViewModel.incomingPendingRequests.collectAsStateWithLifecycle()
    val chatRequestsState by chatViewModel.chatRequestsState.collectAsStateWithLifecycle()
    val chatError by chatViewModel.errorMessage.collectAsStateWithLifecycle()
    val chatActionMsg by chatViewModel.actionMessage.collectAsStateWithLifecycle()
    val feedError by feedViewModel.actionError.collectAsStateWithLifecycle()
    val feedActionMsg by feedViewModel.actionMessage.collectAsStateWithLifecycle()
    val selectedChat by chatViewModel.selectedChat.collectAsStateWithLifecycle()
    val sinpeConfetti by chatViewModel.sinpeConfettiTrigger.collectAsStateWithLifecycle()
    val activeCall by callViewModel.activeCall.collectAsStateWithLifecycle()
    val activeGroupCallByChatId by callViewModel.activeGroupCallByChatId.collectAsStateWithLifecycle()
    val rawChatsState by chatViewModel.rawChatsState.collectAsStateWithLifecycle()
    val totalChatBadges = totalUnread + pendingRequestCount.toLong()

    var assistantRequest by remember { mutableStateOf<AssistantLaunchRequest?>(null) }
    var pendingCall by remember { mutableStateOf<PendingAnyCallRequest?>(null) }
    var lastNotifiedCallId by remember { mutableStateOf<String?>(null) }
    var inAppBanner by remember { mutableStateOf<InAppNotificationBannerData?>(null) }

    var chatsHydrated by remember(publicUser.userId) { mutableStateOf(false) }
    var seenChatMsgTimestamps by remember(publicUser.userId) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var requestsHydrated by remember(publicUser.userId) { mutableStateOf(false) }
    var seenRequestIds by remember(publicUser.userId) { mutableStateOf<Set<String>>(emptySet()) }
    var notificationPermissionAsked by remember(publicUser.userId) { mutableStateOf(false) }

    val callSoundManager = remember(context) { CallSoundManager(context.applicationContext) }
    var hadActiveCall by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppInForeground by remember { mutableStateOf(true) }
    val cachedFcmToken by preferencesStore.fcmToken.collectAsStateWithLifecycle(initialValue = null)

    DisposableEffect(callSoundManager) {
        onDispose { callSoundManager.stopAll() }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isAppInForeground = true
                    ActiveChatTracker.isAppForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                    ActiveChatTracker.isAppForeground = false
                    PushNotificationRegistrar.triggerExpeditedSync(context)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ActiveChatTracker.isAppForeground = false
            ActiveChatTracker.activeChatId = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedChat?.id, currentDest?.route) {
        ActiveChatTracker.activeChatId = if (currentDest?.hasRoute<RouteChats>() == true) {
            selectedChat?.id
        } else {
            null
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                PushNotificationRegistrar.syncFcmToken(context, userRepo, preferencesStore)
            }
        }
    }

    LaunchedEffect(publicUser.userId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionAsked &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionAsked = true
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PushNotificationRegistrar.scheduleBackgroundSync(context)
        PushNotificationRegistrar.syncFcmToken(context, userRepo, preferencesStore)
    }

    LaunchedEffect(privateUser?.userId, cachedFcmToken) {
        val token = cachedFcmToken
        if (privateUser != null && !token.isNullOrBlank() && privateUser.fcmToken != token) {
            userRepo.saveFcmToken(token)
        }
    }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingCall
        pendingCall = null
        if (req != null) {
            val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true
            val videoOk = !req.isVideo || grants[Manifest.permission.CAMERA] == true
            if (audioOk && videoOk) {
                when (req) {
                    is PendingAnyCallRequest.Direct -> {
                        callViewModel.startOutgoingCall(
                            context = context,
                            caller = publicUser,
                            calleeUid = req.calleeUid,
                            calleeName = req.calleeName,
                            calleeAvatar = req.calleeAvatar,
                            isVideo = req.isVideo
                        )
                    }
                    is PendingAnyCallRequest.Group -> {
                        callViewModel.startOutgoingGroupCall(
                            context = context,
                            caller = publicUser,
                            chat = req.chat,
                            isVideo = req.isVideo
                        )
                    }
                    is PendingAnyCallRequest.Join -> {
                        callViewModel.joinGroupCall(
                            context = context,
                            session = req.session
                        )
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "Se requieren permisos de micrófono y cámara para iniciar la llamada.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun missingCallPermissions(isVideo: Boolean): List<String> = buildList {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            add(Manifest.permission.RECORD_AUDIO)
        }
        if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            add(Manifest.permission.CAMERA)
        }
    }

    fun launchCallWithPermissionCheck(
        calleeUid: String,
        calleeName: String,
        calleeAvatar: String?,
        isVideo: Boolean
    ) {
        val needed = missingCallPermissions(isVideo)
        if (needed.isEmpty()) {
            callViewModel.startOutgoingCall(
                context = context,
                caller = publicUser,
                calleeUid = calleeUid,
                calleeName = calleeName,
                calleeAvatar = calleeAvatar,
                isVideo = isVideo
            )
        } else {
            pendingCall = PendingAnyCallRequest.Direct(calleeUid, calleeName, calleeAvatar, isVideo)
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    fun launchGroupCallWithPermissionCheck(chat: Chat, isVideo: Boolean) {
        val needed = missingCallPermissions(isVideo)
        if (needed.isEmpty()) {
            callViewModel.startOutgoingGroupCall(
                context = context,
                caller = publicUser,
                chat = chat,
                isVideo = isVideo
            )
        } else {
            pendingCall = PendingAnyCallRequest.Group(chat, isVideo)
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    fun joinGroupCallWithPermissionCheck(session: CallSession) {
        val isVideo = session.callType == "video"
        val needed = missingCallPermissions(isVideo)
        if (needed.isEmpty()) {
            callViewModel.joinGroupCall(context = context, session = session)
        } else {
            pendingCall = PendingAnyCallRequest.Join(session, isVideo)
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(chatError) {
        val err = chatError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            chatViewModel.clearError()
        }
    }

    LaunchedEffect(chatActionMsg) {
        val msg = chatActionMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            chatViewModel.clearActionMessage()
        }
    }

    LaunchedEffect(feedError) {
        val err = feedError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            feedViewModel.clearError()
        }
    }

    LaunchedEffect(feedActionMsg) {
        val msg = feedActionMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            feedViewModel.clearActionMessage()
        }
    }

    // Manage ringing, ringback, and end call tones
    LaunchedEffect(
        activeCall.session?.id,
        activeCall.session?.status,
        activeCall.isIncoming,
        activeCall.isConnected,
        privateUser?.soundEnabled,
        privateUser?.callAlertsEnabled
    ) {
        val sess = activeCall.session
        val soundOk = privateUser?.soundEnabled != false
        val callAlertsOk = privateUser?.callAlertsEnabled != false
        when {
            sess == null -> {
                if (hadActiveCall) {
                    callSoundManager.playEndTone(allowSound = soundOk)
                } else {
                    callSoundManager.stopAll()
                }
                hadActiveCall = false
            }
            activeCall.isConnected || sess.status == "active" -> {
                hadActiveCall = true
                callSoundManager.stopAll()
            }
            sess.status == "ringing" && activeCall.isIncoming -> {
                hadActiveCall = true
                callSoundManager.startIncomingRingtone(
                    allowSound = soundOk && callAlertsOk,
                    allowVibrate = callAlertsOk
                )
            }
            sess.status == "ringing" && !activeCall.isIncoming -> {
                hadActiveCall = true
                callSoundManager.startOutgoingRingback(allowSound = soundOk)
            }
            else -> {
                hadActiveCall = true
                callSoundManager.stopAll()
            }
        }
    }

    // Auto-dismiss in-app notification banner
    LaunchedEffect(inAppBanner?.id) {
        val bannerId = inAppBanner?.id ?: return@LaunchedEffect
        delay(4500L)
        if (inAppBanner?.id == bannerId) {
            inAppBanner = null
        }
    }

    // Notify on incoming message / SINPE / Buzz (system notification + in-app floating alert)
    LaunchedEffect(rawChatsState, isOnline) {
        val chats = ((rawChatsState as? UiState.Success)?.data ?: return@LaunchedEffect)
            .filter { publicUser.userId !in it.hiddenForUids }
        if (isOnline) {
            chatViewModel.acknowledgeChatsDelivered(chats)
        }
        val currentMap = chats.associate { chat ->
            val millis = chat.lastMessageAt?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L } ?: 0L
            chat.id to millis
        }
        if (!chatsHydrated) {
            seenChatMsgTimestamps = currentMap
            chatsHydrated = true
            return@LaunchedEffect
        }

        val updatedUnreadChat = chats.firstOrNull { chat ->
            val unread = chat.unreadCounts[publicUser.userId] ?: 0L
            val newTs = currentMap[chat.id] ?: 0L
            val prevTs = seenChatMsgTimestamps[chat.id] ?: 0L
            val notVisibleInChat = !isAppInForeground || chat.id != selectedChat?.id
            unread > 0L &&
                chat.lastMessageSenderId != publicUser.userId &&
                notVisibleInChat &&
                newTs > prevTs
        }
        seenChatMsgTimestamps = currentMap

        if (updatedUnreadChat != null) {
            val isSinpe = updatedUnreadChat.lastMessageText.contains("SINPE", ignoreCase = true) ||
                updatedUnreadChat.lastMessageText.contains("₡")
            val isBuzz = ChatViewModel.isBuzzPreviewText(updatedUnreadChat.lastMessageText)
            val shouldAlert = if (isSinpe) {
                privateUser?.sinpeAlertsEnabled != false
            } else {
                privateUser?.soundEnabled != false
            }
            if (shouldAlert) {
                val msgTs = currentMap[updatedUnreadChat.id] ?: 0L
                val eventKey = "msg_${updatedUnreadChat.id}_$msgTs"
                val isNewEvent = preferencesStore.markEventNotifiedIfNew(eventKey)
                val title = ChatViewModel.resolveChatDisplayTitle(updatedUnreadChat, publicUser.userId)
                val body = if (isBuzz) {
                    "$title te envió un bip"
                } else {
                    updatedUnreadChat.lastMessageText.ifBlank { "Tienes un mensaje nuevo" }
                }
                val channelType = when {
                    isBuzz -> "buzz"
                    isSinpe -> "sinpe"
                    else -> "chat"
                }
                if (isNewEvent) {
                    NotificationHelper.showNotification(
                        context = context,
                        channelType = channelType,
                        title = title,
                        body = body,
                        notificationId = updatedUnreadChat.id.hashCode(),
                        targetChatId = updatedUnreadChat.id
                    )
                }
                if (isAppInForeground) {
                    inAppBanner = InAppNotificationBannerData(
                        id = eventKey,
                        title = title,
                        body = body,
                        channelType = channelType,
                        targetChatId = updatedUnreadChat.id
                    )
                }
            }
        }
    }

    // Notify on new incoming chat requests
    LaunchedEffect(chatRequestsState, incomingPendingRequests) {
        if (chatRequestsState !is UiState.Success) return@LaunchedEffect
        val currentIds = incomingPendingRequests.map { it.id }.toSet()
        if (!requestsHydrated) {
            seenRequestIds = currentIds
            requestsHydrated = true
            return@LaunchedEffect
        }
        val newReq = incomingPendingRequests.firstOrNull { it.id !in seenRequestIds }
        seenRequestIds = currentIds
        if (newReq != null && (privateUser?.soundEnabled != false)) {
            val eventKey = "req_${newReq.id}"
            val isNewEvent = preferencesStore.markEventNotifiedIfNew(eventKey)
            val title = "Nueva solicitud de chat"
            val body = "${newReq.fromName} (@${newReq.fromUsername}) quiere chatear contigo"
            if (isNewEvent) {
                NotificationHelper.showNotification(
                    context = context,
                    channelType = "request",
                    title = title,
                    body = body,
                    notificationId = newReq.id.hashCode(),
                    openRequestsTab = true
                )
            }
            if (isAppInForeground) {
                inAppBanner = InAppNotificationBannerData(
                    id = eventKey,
                    title = title,
                    body = body,
                    channelType = "request",
                    openRequestsTab = true
                )
            }
        }
    }

    // Notify on incoming P2P call
    LaunchedEffect(activeCall.session?.id, activeCall.isIncoming) {
        val sess = activeCall.session
        if (sess != null && activeCall.isIncoming && sess.id != lastNotifiedCallId) {
            lastNotifiedCallId = sess.id
            if (privateUser?.callAlertsEnabled != false) {
                val eventKey = "call_${sess.id}"
                if (preferencesStore.markEventNotifiedIfNew(eventKey)) {
                    val callTitle = if (sess.groupCall) {
                        "Llamada grupal: ${sess.groupTitle ?: sess.callerName}"
                    } else {
                        "Llamada entrante de ${sess.callerName}"
                    }
                    NotificationHelper.showNotification(
                        context = context,
                        channelType = "call",
                        title = callTitle,
                        body = if (sess.callType == "video") "Videollamada VYB" else "Llamada de voz VYB",
                        notificationId = sess.id.hashCode(),
                        openCallsTab = true
                    )
                }
            }
        }
    }

    val navItems = remember {
        listOf(
            VybNavItem(RouteInicio, "Inicio", Icons.Filled.Home, Icons.Outlined.Home, "nav_inicio"),
            VybNavItem(RouteChats, "Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
            VybNavItem(RouteLlamadas, "Llamadas", Icons.Filled.Call, Icons.Outlined.Call, "nav_llamadas"),
            VybNavItem(RoutePerfil, "Perfil", Icons.Filled.Person, Icons.Outlined.Person, "nav_perfil")
        )
    }

    fun navigateToRoute(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingNavTarget) {
        val target = pendingNavTarget ?: return@LaunchedEffect
        when {
            target.openCallsTab -> navigateToRoute(RouteLlamadas)
            target.openRequestsTab -> {
                chatViewModel.openChat(null)
                chatViewModel.setChatFilter(ChatFilter.REQUESTS)
                navigateToRoute(RouteChats)
            }
            !target.targetChatId.isNullOrBlank() -> {
                chatViewModel.setChatFilter(ChatFilter.ALL)
                chatViewModel.openChat(
                    chatId = target.targetChatId,
                    sendReadReceipt = privateUser?.readReceipts != false
                )
                navigateToRoute(RouteChats)
            }
        }
        onConsumeNavTarget()
    }

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isInChatRoom = currentDest?.hasRoute<RouteChats>() == true && selectedChat != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = !useNavRail && !isInChatRoom,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            tonalElevation = 4.dp,
                            shadowElevation = 6.dp
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp
                            ) {
                                navItems.forEach { item ->
                                    val selected = when (item.route) {
                                        RouteInicio -> currentDest?.hasRoute<RouteInicio>() == true
                                        RouteChats -> currentDest?.hasRoute<RouteChats>() == true
                                        RouteLlamadas -> currentDest?.hasRoute<RouteLlamadas>() == true
                                        RoutePerfil -> currentDest?.hasRoute<RoutePerfil>() == true
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToRoute(item.route) },
                                        modifier = Modifier.testTag(item.testTag),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = vybColors.secondaryText,
                                            unselectedTextColor = vybColors.secondaryText
                                        ),
                                        icon = {
                                            val iconVec = if (selected) item.selectedIcon else item.unselectedIcon
                                            if (item.route == RouteChats && totalChatBadges > 0L) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge { Text(totalChatBadges.coerceAtMost(99L).toString()) }
                                                    }
                                                ) {
                                                    Icon(iconVec, contentDescription = item.label)
                                                }
                                            } else {
                                                Icon(iconVec, contentDescription = item.label)
                                            }
                                        },
                                        label = {
                                            Text(
                                                item.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = vybColors.subtleSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        VybLogoBadge(size = 40.dp)
                        Spacer(Modifier.height(20.dp))
                        navItems.forEach { item ->
                            val selected = when (item.route) {
                                RouteInicio -> currentDest?.hasRoute<RouteInicio>() == true
                                RouteChats -> currentDest?.hasRoute<RouteChats>() == true
                                RouteLlamadas -> currentDest?.hasRoute<RouteLlamadas>() == true
                                RoutePerfil -> currentDest?.hasRoute<RoutePerfil>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(item.route) },
                                alwaysShowLabel = true,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = vybColors.secondaryText,
                                    unselectedTextColor = vybColors.secondaryText
                                ),
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    val iconVec = if (selected) item.selectedIcon else item.unselectedIcon
                                    if (item.route == RouteChats && totalChatBadges > 0L) {
                                        BadgedBox(
                                            badge = { Badge { Text(totalChatBadges.coerceAtMost(99L).toString()) } }
                                        ) {
                                            Icon(iconVec, contentDescription = item.label)
                                        }
                                    } else {
                                        Icon(iconVec, contentDescription = item.label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(vybColors.hairlineBorder)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = if (useNavRail) 0.dp else innerPadding.calculateBottomPadding() * 0f)
                ) {
                    OfflineIndicator(isOnline = isOnline)

                    NavHost(
                        navController = navController,
                        startDestination = RouteInicio,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<RouteInicio> {
                            HomeFeedScreen(
                                feedViewModel = feedViewModel,
                                currentUser = publicUser,
                                privateAreaLabel = privateAreaLabel,
                                widthSizeClass = widthSizeClass,
                                onReplyToStoryAuthor = { authorId, authorName, authorAvatar, replyText ->
                                    chatViewModel.startDirectChat(
                                        currentUser = publicUser,
                                        peerUid = authorId,
                                        peerName = authorName,
                                        peerAvatar = authorAvatar,
                                        initialMessage = replyText,
                                        onOpened = { navigateToRoute(RouteChats) }
                                    )
                                }
                            )
                        }
                        composable<RouteChats> {
                            ChatsScreen(
                                chatViewModel = chatViewModel,
                                feedViewModel = feedViewModel,
                                assistantViewModel = assistantViewModel,
                                aiModelChoice = aiModelChoice,
                                onSelectModelChoice = { choice -> profileViewModel.setAiModelChoice(choice) },
                                privateAreaLabel = privateAreaLabel,
                                currentUser = publicUser,
                                privateUser = privateUser,
                                blockedUids = privateUser?.blockedUids ?: emptyList(),
                                onBlockUser = { uid, name -> profileViewModel.blockUser(uid, name) },
                                onUnblockUser = { uid -> profileViewModel.unblockUser(uid) },
                                onBlockByIdentifier = { query, onResult ->
                                    profileViewModel.blockByIdentifier(query, onResult)
                                },
                                widthSizeClass = widthSizeClass,
                                activeGroupCallByChatId = activeGroupCallByChatId,
                                loadFullMediaFile = { mediaId ->
                                    feedViewModel.loadFullMediaFile(context, mediaId)
                                },
                                onOpenAssistant = { tab, prompt, draft, chat, msgs ->
                                    assistantRequest = AssistantLaunchRequest(
                                        tab = tab,
                                        prompt = prompt,
                                        draft = draft,
                                        chat = chat,
                                        messages = msgs
                                    )
                                },
                                onStartCall = { uid, name, avatar, isVideo ->
                                    launchCallWithPermissionCheck(uid, name, avatar, isVideo)
                                },
                                onStartGroupCall = { chat, isVideo ->
                                    launchGroupCallWithPermissionCheck(chat, isVideo)
                                },
                                onJoinGroupCall = { session ->
                                    joinGroupCallWithPermissionCheck(session)
                                }
                            )
                        }
                        composable<RouteLlamadas> {
                            CallsScreen(
                                callViewModel = callViewModel,
                                currentUser = publicUser,
                                widthSizeClass = widthSizeClass,
                                activeChats = ((rawChatsState as? UiState.Success)?.data ?: emptyList()),
                                onRequestCallWithPermissions = { uid, name, avatar, isVideo ->
                                    launchCallWithPermissionCheck(uid, name, avatar, isVideo)
                                },
                                onRequestGroupCallWithPermissions = { chat, isVideo ->
                                    launchGroupCallWithPermissionCheck(chat, isVideo)
                                },
                                onJoinGroupCallWithPermissions = { session ->
                                    joinGroupCallWithPermissionCheck(session)
                                },
                                onGoToChats = { navigateToRoute(RouteChats) }
                            )
                        }
                        composable<RoutePerfil> {
                            ProfileScreen(
                                profileViewModel = profileViewModel,
                                feedViewModel = feedViewModel,
                                publicUser = publicUser,
                                privateUser = privateUser,
                                isAccountVerified = isAccountVerified,
                                fcmToken = privateUser?.fcmToken ?: cachedFcmToken,
                                widthSizeClass = widthSizeClass,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        // Global SINPE confirmation confetti overlay
        ConfettiOverlay(triggerCount = sinpeConfetti)

        // Floating in-app active notification banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = inAppBanner != null,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(160)
                ) + fadeOut(tween(140))
            ) {
                val banner = inAppBanner
                if (banner != null) {
                    val accentColor = when (banner.channelType) {
                        "sinpe" -> vybColors.sinpeEmerald
                        "call" -> vybColors.sinpeEmerald
                        "buzz" -> Color(0xFF00B4FF)
                        else -> VybElectricBlue
                    }
                    val bannerIcon = when (banner.channelType) {
                        "sinpe" -> Icons.Filled.PhoneAndroid
                        "request" -> Icons.Filled.MarkChatUnread
                        "call" -> Icons.Filled.Call
                        "buzz" -> Icons.Filled.Bolt
                        else -> Icons.Filled.NotificationsActive
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                inAppBanner = null
                                when {
                                    banner.openRequestsTab -> {
                                        chatViewModel.openChat(null)
                                        chatViewModel.setChatFilter(ChatFilter.REQUESTS)
                                        navigateToRoute(RouteChats)
                                    }
                                    !banner.targetChatId.isNullOrBlank() -> {
                                        chatViewModel.setChatFilter(ChatFilter.ALL)
                                        chatViewModel.openChat(
                                            chatId = banner.targetChatId,
                                            sendReadReceipt = privateUser?.readReceipts != false
                                        )
                                        navigateToRoute(RouteChats)
                                    }
                                }
                            }
                            .testTag("in_app_notification_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(VybGradientBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = bannerIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = banner.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = banner.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { inAppBanner = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cerrar notificación",
                                    tint = vybColors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Global Assistant Modal
        assistantRequest?.let { req ->
            AssistantModal(
                assistantViewModel = assistantViewModel,
                modelChoice = aiModelChoice,
                onSelectModelChoice = { choice -> profileViewModel.setAiModelChoice(choice) },
                initialTab = req.tab,
                initialPrompt = req.prompt,
                initialDraft = req.draft,
                activeChat = req.chat,
                activeChatMessages = req.messages,
                privateAreaLabel = privateAreaLabel,
                onShareSummaryToChat = { summary ->
                    req.chat?.let { c ->
                        chatViewModel.shareAiSummary(c, publicUser, summary)
                    }
                },
                onInsertToneIntoChat = { rewrittenText ->
                    req.chat?.let { c ->
                        val effectiveBank = privateUser?.sinpeBank?.takeIf { it.isNotBlank() }
                            ?: publicUser.sinpeBank.ifBlank { "BAC Credomatic" }
                        chatViewModel.sendMessageOrCommand(
                            chat = c,
                            sender = publicUser,
                            senderPhone = privateUser?.phone,
                            rawText = rewrittenText,
                            senderBank = effectiveBank,
                            onMentionAssistant = {}
                        )
                    }
                },
                onSendSinpeFromReceipt = { totalAmount, splitCount, reason ->
                    req.chat?.let { c ->
                        val effectiveBank = privateUser?.sinpeBank?.takeIf { it.isNotBlank() }
                            ?: publicUser.sinpeBank.ifBlank { "BAC Credomatic" }
                        chatViewModel.sendSinpeCard(
                            chat = c,
                            sender = publicUser,
                            senderPhone = privateUser?.phone,
                            totalAmount = totalAmount,
                            splitCount = splitCount,
                            reason = reason,
                            senderBank = effectiveBank
                        )
                    }
                },
                onDismiss = {
                    assistantViewModel.clearTools()
                    assistantRequest = null
                }
            )
        }

        // Global WebRTC Call Overlay
        if (activeCall.session != null) {
            CallModal(
                activeCall = activeCall,
                currentUserId = publicUser.userId,
                callViewModel = callViewModel
            )
        }
    }
}
