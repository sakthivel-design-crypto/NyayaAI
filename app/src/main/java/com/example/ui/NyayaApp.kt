package com.example.ui

import com.example.model.LawRecord
import com.example.util.TimeUtils
import com.example.util.GovernmentVerificationEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Patterns
import androidx.compose.ui.text.style.TextDecoration
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalInspectionMode
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.LegalTopic
import com.example.db.*
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.NyayaViewModel
import com.example.viewmodel.DEMO_MODE
import com.example.api.GeminiApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Color tokens matching the premium Dark Navy and Gold theme
val DarkIndigo = Color(0xFF0F172A) // Main Canvas Background (Dark Navy)
val CardBackground = Color(0xFF1E293B) // Card Background (Slate Blue Card)
val AccentOrange = Color(0xFFD4AF37) // Bright Gold (Justice and Luxury Gold Accent)
val JusticeBlue = Color(0xFF3B82F6) // Modern Electric Blue
val LightJusticeBlue = Color(0xFF60A5FA) // Light electric blue
val TextGray = Color(0xFF94A3B8) // Slate muted text
val WarningRed = Color(0xFFEF4444) // Error / SOS Red
val SuccessGreen = Color(0xFF10B981) // Emerald Green

// Extra theme tokens for the Professional Polish design
val TextDarkSlate = Color(0xFFF8FAFC) // High-contrast White/Slate text
val LightBlueHighlight = Color(0xFF334155) // Selected indicator card highlight
val LightSlateBorder = Color(0xFF334155) // 1px Border (Slate border)

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Assistant : Screen("assistant", "Assistant", { Icon(Icons.Default.SupportAgent, contentDescription = "AI Assistant") })
    object Map : Screen("map", "Safety Map", { Icon(Icons.Default.Map, contentDescription = "Safety Map") })
    object Complaints : Screen("complaints", "Complaints", { Icon(Icons.Default.Assignment, contentDescription = "Complaints") })
    object Laws : Screen("laws", "Browse Laws", { Icon(Icons.Default.Gavel, contentDescription = "Browse Laws") })
    object Forum : Screen("forum", "Forum", { Icon(Icons.Default.Forum, contentDescription = "Community Forum") })
    object Feedback : Screen("citizen_feedback", "Feedback", { Icon(Icons.Default.RateReview, contentDescription = "Feedback") })
    object Profile : Screen("profile", "Profile", { Icon(Icons.Default.Person, contentDescription = "My Profile") })
}

sealed class AuthorityScreen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Requests : AuthorityScreen("auth_requests", "Inquiries", { Icon(Icons.Default.QuestionAnswer, contentDescription = "Citizen Inquiries") })
    object Dispatch : AuthorityScreen("auth_dispatch", "Complaints", { Icon(Icons.Default.Assignment, contentDescription = "Complaints") })
    object Laws : AuthorityScreen("auth_laws", "Laws", { Icon(Icons.Default.Gavel, contentDescription = "Laws") })
    object Feedback : AuthorityScreen("auth_feedback", "Feedback", { Icon(Icons.Default.RateReview, contentDescription = "Feedback") })
    object Profile : AuthorityScreen("auth_profile", "Profile", { Icon(Icons.Default.Person, contentDescription = "My Profile") })
}

sealed class AdminScreen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Overview : AdminScreen("admin_overview", "Overview", { Icon(Icons.Default.Dashboard, contentDescription = "Overview") })
    object Users : AdminScreen("admin_users", "Users", { Icon(Icons.Default.Group, contentDescription = "User Nodes") })
    object Laws : AdminScreen("admin_laws", "Dataset", { Icon(Icons.Default.Book, contentDescription = "Dataset") })
    object Feedback : AdminScreen("admin_feedback", "AI Logs", { Icon(Icons.Default.RateReview, contentDescription = "AI Logs") })
    object Profile : AdminScreen("admin_profile", "Profile", { Icon(Icons.Default.Person, contentDescription = "My Profile") })
}

@Composable
fun NyayaApp(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isAppInitializing by viewModel.isAppInitializing.collectAsState()
    
    // Auth sub-navigation state (defaults to LANDING for premium entry)
    var authScreenState by remember { mutableStateOf("LANDING") } 

    // Load local knowledge JSON at startup
    LaunchedEffect(Unit) {
        viewModel.loadLegalDatabase(context)
    }

    if (isAppInitializing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "NyayaAI Ecosystem",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Initializing Legal Database & Realtime Sync...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    if (!isLoggedIn) {
        when (authScreenState) {
            "LANDING" -> LandingScreen(
                viewModel = viewModel,
                onNavigateToCitizen = { authScreenState = "CITIZEN_LOGIN" },
                onNavigateToAuthority = { authScreenState = "AUTHORITY_LOGIN" },
                onNavigateToAdmin = { authScreenState = "ADMIN_LOGIN" }
            )
            "CITIZEN_LOGIN" -> CitizenLoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = { authScreenState = "REGISTER" },
                onNavigateToForgot = { authScreenState = "FORGOT_PASSWORD_CITIZEN" },
                onBackToLanding = { authScreenState = "LANDING" }
            )
            "AUTHORITY_LOGIN" -> AuthorityLoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = { authScreenState = "AUTHORITY_REGISTER" },
                onNavigateToForgot = { authScreenState = "FORGOT_PASSWORD_AUTHORITY" },
                onBackToLanding = { authScreenState = "LANDING" }
            )
            "AUTHORITY_REGISTER" -> AuthorityRegisterScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "AUTHORITY_LOGIN" },
                onBackToLanding = { authScreenState = "LANDING" }
            )
            "ADMIN_LOGIN" -> AdminLoginScreen(
                viewModel = viewModel,
                onNavigateToForgot = { authScreenState = "FORGOT_PASSWORD_ADMIN" },
                onBackToLanding = { authScreenState = "LANDING" }
            )
            "REGISTER" -> RegisterScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "CITIZEN_LOGIN" }
            )
            "FORGOT_PASSWORD_CITIZEN" -> ForgotPasswordScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "CITIZEN_LOGIN" }
            )
            "FORGOT_PASSWORD_AUTHORITY" -> ForgotPasswordScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "AUTHORITY_LOGIN" }
            )
            "FORGOT_PASSWORD_ADMIN" -> AdminForgotPasswordScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "ADMIN_LOGIN" }
            )
            else -> {
                authScreenState = "LANDING"
            }
        }
    } else {
        val currentUserAccount by viewModel.currentUserAccount.collectAsState()
        val userRole = userProfile.role.ifBlank { currentUserAccount?.role ?: "" }
        val userEmail = userProfile.email.ifBlank { currentUserAccount?.email ?: "" }
        val normalizedRole = com.example.firebase.FirebaseManager.normalizeRole(userRole)
        val isAdminSession = normalizedRole == "root_admin" || normalizedRole == "admin"
        val isAuthorizedAdminEmail = userEmail.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                userEmail.equals("srisakthi1357@gmail.com", ignoreCase = true)

        // IMPORTANT: Only the actual admin session role should render the admin dashboard.
        // Email-based checks are kept for admin login validation, but they must never
        // inject admin UI into the citizen flow.
        if (isAdminSession || (isAuthorizedAdminEmail && (normalizedRole == "root_admin" || normalizedRole == "admin"))) {
            AdminDashboard(viewModel)
        } else if (normalizedRole == "authority") {
            AuthorityDashboard(viewModel)
        } else {
            CitizenDashboard(viewModel)
        }
    }
}

@Composable
fun CitizenDashboard(viewModel: NyayaViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Assistant) }
    val lang by viewModel.currentLanguage.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val currentUserAccount by viewModel.currentUserAccount.collectAsState()

    val displayName = remember(userProfile.name, currentUserAccount?.name, userProfile.email) {
        viewModel.formatDisplayName(userProfile.name, userProfile.email.ifBlank { currentUserAccount?.email })
    }
    val emailDisplay = remember(userProfile.email, currentUserAccount?.email) {
        userProfile.email.ifBlank { currentUserAccount?.email ?: "user@nyaya.ai" }
    }

    val greetingText = remember(currentScreen) {
        TimeUtils.getGreeting()
    }

    if (currentScreen == Screen.Map) {
        com.example.ui.map.SafetyMapScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = Screen.Assistant }
        )
    } else {
        Scaffold(
            topBar = {
                Column {
                    Surface(
                        color = CardBackground,
                        tonalElevation = 4.dp,
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        // Header Row: Avatar, Portal Identity, Dynamic Name, Registered Email
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(AccentOrange.copy(alpha = 0.2f))
                                        .border(1.dp, AccentOrange, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = AccentOrange
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Citizen Portal",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentOrange
                                        )
                                        Surface(
                                            color = SuccessGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(0.5.dp, SuccessGreen)
                                        ) {
                                            Text(
                                                text = "AUTHENTICATED",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "$greetingText, $displayName",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )

                                    Text(
                                        text = emailDisplay,
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }
                            }

                            // Profile Direct Switch
                            IconButton(
                                onClick = { currentScreen = Screen.Profile },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DarkIndigo)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = AccentOrange
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)
                    NavigationBar(
                        containerColor = CardBackground,
                        tonalElevation = 0.dp
                    ) {
                        val screens = listOf(Screen.Assistant, Screen.Map, Screen.Complaints, Screen.Laws, Screen.Forum, Screen.Feedback, Screen.Profile)
                        screens.forEach { screen ->
                            val translatedTitle = when (screen) {
                                Screen.Assistant -> I18n.getString("nav_assistant", lang)
                                Screen.Map -> I18n.getString("nav_map", lang)
                                Screen.Complaints -> I18n.getString("nav_complaints", lang)
                                Screen.Laws -> I18n.getString("nav_laws", lang)
                                Screen.Forum -> I18n.getString("nav_forum", lang)
                                Screen.Feedback -> "Feedback"
                                Screen.Profile -> I18n.getString("nav_profile", lang)
                            }
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = screen.icon,
                                label = { Text(translatedTitle, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentOrange,
                                    selectedTextColor = AccentOrange,
                                    indicatorColor = LightBlueHighlight,
                                    unselectedIconColor = TextGray,
                                    unselectedTextColor = TextGray
                                ),
                                modifier = Modifier.testTag("nav_item_${screen.route}")
                            )
                        }
                    }
                }
            },
            containerColor = DarkIndigo
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    is Screen.Assistant -> AssistantScreen(viewModel)
                    is Screen.Map -> com.example.ui.map.SafetyMapScreen(viewModel, onNavigateBack = { currentScreen = Screen.Assistant })
                    is Screen.Complaints -> CitizenComplaintsScreen(viewModel)
                    is Screen.Laws -> BrowseLawsScreen(viewModel)
                    is Screen.Forum -> ForumScreen(viewModel)
                    is Screen.Feedback -> UserFeedbackScreen(viewModel)
                    is Screen.Profile -> ProfileScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun AuthorityDashboard(viewModel: NyayaViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    var currentScreen by remember { mutableStateOf<AuthorityScreen>(AuthorityScreen.Dispatch) }
    val lang by viewModel.currentLanguage.collectAsState()

    if (!isLoggedIn || (!userProfile.role.equals("Authority", ignoreCase = true) && !userProfile.role.equals("Admin", ignoreCase = true))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkIndigo)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = "Access Restricted",
                    tint = WarningRed,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Restricted Authority Portal",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "This terminal is restricted to verified government personnel. Please sign in via the Authority Portal with verified credentials.",
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Return to Portal Login", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 0.dp
                ) {
                    val screens = listOf(
                        AuthorityScreen.Requests,
                        AuthorityScreen.Dispatch,
                        AuthorityScreen.Laws,
                        AuthorityScreen.Feedback,
                        AuthorityScreen.Profile
                    )
                    screens.forEach { screen ->
                        val translatedTitle = when (screen) {
                            AuthorityScreen.Requests -> I18n.getString("nav_inquiries", lang)
                            AuthorityScreen.Dispatch -> I18n.getString("nav_complaints", lang)
                            AuthorityScreen.Laws -> I18n.getString("nav_laws", lang)
                            AuthorityScreen.Feedback -> I18n.getString("nav_feedback", lang)
                            AuthorityScreen.Profile -> I18n.getString("nav_profile", lang)
                        }
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = screen.icon,
                            label = { Text(translatedTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentOrange,
                                selectedTextColor = AccentOrange,
                                indicatorColor = LightBlueHighlight,
                                unselectedIconColor = TextGray,
                                unselectedTextColor = TextGray
                            ),
                            modifier = Modifier.testTag("auth_nav_${screen.route}")
                        )
                    }
                }
            }
        },
        containerColor = DarkIndigo
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is AuthorityScreen.Requests -> AuthorityInquiriesScreen(viewModel)
                is AuthorityScreen.Dispatch -> AuthorityComplaintsScreen(viewModel)
                is AuthorityScreen.Laws -> AuthorityLawsScreen(viewModel)
                is AuthorityScreen.Feedback -> UserFeedbackScreen(viewModel, forcedRole = "Authority")
                is AuthorityScreen.Profile -> ProfileScreen(viewModel)
            }
        }
    }
}

@Composable
fun AdminDashboard(viewModel: NyayaViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val currentUserAccount by viewModel.currentUserAccount.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()

    val userRole = userProfile.role.ifBlank { currentUserAccount?.role ?: "" }
    val userEmail = userProfile.email.ifBlank { currentUserAccount?.email ?: "" }
    val normalizedRole = com.example.firebase.FirebaseManager.normalizeRole(userRole)
    val isAuthorizedAdmin = isLoggedIn && (
        normalizedRole == "root_admin" ||
        normalizedRole == "admin" ||
        userEmail.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
        userEmail.equals("srisakthi1357@gmail.com", ignoreCase = true)
    )

    if (!isAuthorizedAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkIndigo)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Access Denied",
                    tint = WarningRed,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Access Denied",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningRed
                )
                Text(
                    text = "You are not authorized to access the Admin Dashboard.",
                    fontSize = 14.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to Login", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    var currentScreen by remember { mutableStateOf<AdminScreen>(AdminScreen.Overview) }

    Scaffold(
        topBar = {
            Surface(
                color = CardBackground,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Admin Shield",
                            tint = AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "NyayaAI Admin",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )
                            Text(
                                text = if (userProfile.email.isNotBlank()) userProfile.email else (currentUserAccount?.email ?: "sakthivel.s8317@gmail.com"),
                                fontSize = 11.sp,
                                color = TextGray
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = AccentOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "ROOT ADMIN",
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("admin_topbar_logout")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Logout Admin Session",
                                tint = WarningRed
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 0.dp
                ) {
                    val screens = listOf(
                        AdminScreen.Overview,
                        AdminScreen.Users,
                        AdminScreen.Laws,
                        AdminScreen.Feedback,
                        AdminScreen.Profile
                    )
                    screens.forEach { screen ->
                        val translatedTitle = when (screen) {
                            AdminScreen.Overview -> I18n.getString("nav_overview", lang)
                            AdminScreen.Users -> I18n.getString("nav_users", lang)
                            AdminScreen.Laws -> I18n.getString("nav_dataset", lang)
                            AdminScreen.Feedback -> I18n.getString("nav_feedback", lang)
                            AdminScreen.Profile -> I18n.getString("nav_profile", lang)
                        }
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = screen.icon,
                            label = { Text(translatedTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentOrange,
                                selectedTextColor = AccentOrange,
                                indicatorColor = LightBlueHighlight,
                                unselectedIconColor = TextGray,
                                unselectedTextColor = TextGray
                            ),
                            modifier = Modifier.testTag("admin_nav_${screen.route}")
                        )
                    }
                }
            }
        },
        containerColor = DarkIndigo
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is AdminScreen.Overview -> AdminOverviewScreen(viewModel)
                is AdminScreen.Users -> AdminUsersScreen(viewModel)
                is AdminScreen.Laws -> AdminLawsScreen(viewModel)
                is AdminScreen.Feedback -> AdminFeedbackScreen(viewModel)
                is AdminScreen.Profile -> ProfileScreen(viewModel)
            }
        }
    }
}

@Composable
fun LandingScreen(
    viewModel: NyayaViewModel,
    onNavigateToCitizen: () -> Unit,
    onNavigateToAuthority: () -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val lang by viewModel.currentLanguage.collectAsState()
    var languageDropdownOpen by remember { mutableStateOf(false) }
    val supportedLanguages = listOf("English" to "English", "Tamil" to "தமிழ்")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkIndigo, Color(0xFF0F172A))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Language Selector at Top-Right Corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp)
        ) {
            Button(
                onClick = { languageDropdownOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, AccentOrange),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("lang_selector_trigger")
            ) {
                Icon(Icons.Default.Translate, contentDescription = "Select Language", tint = AccentOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "Tamil") "தமிழ்" else "English", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = languageDropdownOpen,
                onDismissRequest = { languageDropdownOpen = false },
                modifier = Modifier.background(CardBackground)
            ) {
                supportedLanguages.forEach { (code, dispName) ->
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(dispName, color = if (lang == code) AccentOrange else Color.White, fontWeight = FontWeight.Bold)
                                if (lang == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        onClick = {
                            viewModel.selectLanguage(code)
                            languageDropdownOpen = false
                        },
                        modifier = Modifier.testTag("lang_select_$code")
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Spacer for top language selector to not overlap
            Spacer(modifier = Modifier.height(30.dp))

            // Shield and Scales Logo Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            Brush.linearGradient(listOf(JusticeBlue, AccentOrange)),
                            shape = RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = "Nyaya Logo",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Text(
                    text = I18n.getString("app_name", lang),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = AccentOrange
                )

                Text(
                    text = I18n.getString("app_subtitle", lang),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(AccentOrange.copy(alpha = 0.5f))
                )
            }

            Text(
                text = I18n.getString("landing_select_portal", lang),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextGray.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            // Portal Cards Stack
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                // Citizen Card
                LandingCard(
                    title = I18n.getString("citizen_portal", lang),
                    subtitle = I18n.getString("citizen_desc", lang),
                    gradient = listOf(Color(0xFF1E293B), Color(0xFF334155)),
                    borderColor = JusticeBlue.copy(alpha = 0.5f),
                    icon = { Icon(Icons.Default.Group, null, tint = LightJusticeBlue, modifier = Modifier.size(32.dp)) },
                    onClick = onNavigateToCitizen,
                    tag = "landing_citizen_card"
                )

                // Authority Card
                LandingCard(
                    title = I18n.getString("authority_portal", lang),
                    subtitle = I18n.getString("authority_desc", lang),
                    gradient = listOf(Color(0xFF1E293B), Color(0xFF1E3A8A)),
                    borderColor = AccentOrange.copy(alpha = 0.4f),
                    icon = { Icon(Icons.Default.Security, null, tint = AccentOrange, modifier = Modifier.size(32.dp)) },
                    onClick = onNavigateToAuthority,
                    tag = "landing_authority_card"
                )

                // Admin Dashboard
                LandingCard(
                    title = I18n.getString("admin_portal", lang),
                    subtitle = I18n.getString("admin_desc", lang),
                    gradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                    borderColor = Color(0xFF94A3B8).copy(alpha = 0.3f),
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(32.dp)) },
                    onClick = onNavigateToAdmin,
                    tag = "landing_admin_card"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = I18n.getString("ledger_text", lang),
                fontSize = 10.sp,
                fontStyle = FontStyle.Italic,
                color = TextGray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun LandingCard(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    borderColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(gradient))
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDarkSlate
                        )
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

fun generateRandomCaptcha(): String {
    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return (1..4).map { chars.random() }.joinToString("")
}

@Composable
fun DynamicCaptcha(
    captchaValue: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .border(1.dp, LightSlateBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .background(
                    Brush.linearGradient(listOf(DarkIndigo, Color(0xFF1E1B4B))),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = captchaValue,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                letterSpacing = 6.sp,
                color = AccentOrange
            )
        }
        
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh Captcha",
                tint = AccentOrange
            )
        }
    }
}

@Composable
fun CitizenLoginScreen(
    viewModel: NyayaViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
    onBackToLanding: () -> Unit
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var captchaCode by remember { mutableStateOf(generateRandomCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var captchaError by remember { mutableStateOf<String?>(null) }

    // Google Sign-In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        Log.i("NyayaAuth", "GOOGLE_AUTH_CALLBACK: Received resultCode=${result.resultCode}, hasData=${result.data != null}")

        var extractedEmail: String? = null
        var extractedName: String? = null
        var extractedPhotoUrl: String? = null
        var idToken: String? = null
        var authFailureReason: String? = null
        var isUserExplicitlyCancelled = (result.resultCode == android.app.Activity.RESULT_CANCELED)

        // 1. Extract selected Google account from Intent result
        if (result.data != null) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                if (account != null && !account.email.isNullOrBlank()) {
                    extractedEmail = account.email?.trim()?.lowercase()
                    extractedName = account.displayName ?: account.givenName
                    extractedPhotoUrl = account.photoUrl?.toString()
                    idToken = account.idToken
                    isUserExplicitlyCancelled = false
                    Log.i("NyayaAuth", "GOOGLE_ACCOUNT_SELECTED: Account retrieved via Task for email: ${account.email}")
                }
            } catch (e: ApiException) {
                val statusCode = e.statusCode
                Log.w("NyayaAuth", "GOOGLE_AUTH_API_EXCEPTION: statusCode=$statusCode, message=${e.message}")
                when (statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                        isUserExplicitlyCancelled = true
                    }
                    GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> {
                        authFailureReason = "Sign-in is currently in progress. Please wait."
                    }
                    12500 -> { // SIGN_IN_FAILED
                        authFailureReason = "Google Sign-In failed (Status 12500). Please ensure SHA-1 fingerprint (1F:08:DF:34:FF:59:B3:B0:72:F7:C9:95:20:CF:B7:E8:79:7C:3E:66) is registered in Firebase Console (NyayaaAI)."
                    }
                    CommonStatusCodes.DEVELOPER_ERROR -> { // 10
                        authFailureReason = "Configuration error (Developer Error 10). Please verify SHA-1 fingerprint and Web Client ID in Firebase Console."
                    }
                    CommonStatusCodes.NETWORK_ERROR -> { // 7
                        authFailureReason = "Network error during Google Sign-In. Please check your internet connection."
                    }
                    else -> {
                        authFailureReason = "Google Sign-In failed (Code $statusCode): ${e.localizedMessage ?: "Please try again or use Email sign in."}"
                    }
                }
            } catch (e: Exception) {
                Log.w("NyayaAuth", "GOOGLE_AUTH_NOTICE: Task.getResult threw: ${e.message}")
                authFailureReason = e.localizedMessage
            }
        }

        // 2. Fallback: Direct extraction from Parcelable Extra "googleSignInAccount" for this specific intent
        if (extractedEmail.isNullOrBlank() && result.data != null && !isUserExplicitlyCancelled) {
            try {
                @Suppress("DEPRECATION")
                val account = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra("googleSignInAccount", GoogleSignInAccount::class.java)
                } else {
                    result.data?.getParcelableExtra("googleSignInAccount") as? GoogleSignInAccount
                }
                if (account != null && !account.email.isNullOrBlank()) {
                    extractedEmail = account.email?.trim()?.lowercase()
                    extractedName = account.displayName ?: account.givenName
                    extractedPhotoUrl = account.photoUrl?.toString()
                    if (idToken == null) idToken = account.idToken
                    Log.i("NyayaAuth", "GOOGLE_ACCOUNT_SELECTED: Account retrieved via Intent parcelable extra: ${account.email}")
                }
            } catch (e: Exception) {
                Log.w("NyayaAuth", "GOOGLE_AUTH_NOTICE: Parcelable extra extraction threw: ${e.message}")
            }
        }

        if (!extractedEmail.isNullOrBlank()) {
            val safeDomain = extractedEmail.substringAfter("@")
            Log.i("NyayaAuth", "GOOGLE_CREDENTIAL_RECEIVED: Account domain=@$safeDomain")
            Log.i("NyayaAuth", "GOOGLE_AUTH_PROVIDER_SIGN_IN: Authenticating with Nyaya ViewModel...")

            viewModel.signInWithGoogle(
                idToken = idToken,
                googleEmail = extractedEmail,
                googleName = extractedName ?: "Citizen User",
                googlePhotoUrl = extractedPhotoUrl
            ) { success, msg ->
                isLoading = false
                if (success) {
                    Log.i("NyayaAuth", "CITIZEN_SESSION_CREATED: Citizen session created successfully")
                    Log.i("NyayaAuth", "CITIZEN_DASHBOARD_OPENED: Navigating to Citizen Dashboard")
                } else {
                    Log.e("NyayaAuth", "GOOGLE_AUTH_ERROR: $msg")
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            isLoading = false
            if (isUserExplicitlyCancelled || (result.resultCode == android.app.Activity.RESULT_CANCELED && authFailureReason == null)) {
                Log.i("NyayaAuth", "GOOGLE_AUTH_CANCELLED: User cancelled selection")
                Toast.makeText(context, "Google Sign-In was cancelled.", Toast.LENGTH_SHORT).show()
            } else if (!authFailureReason.isNullOrBlank()) {
                Log.e("NyayaAuth", "GOOGLE_AUTH_ERROR: $authFailureReason")
                Toast.makeText(context, authFailureReason, Toast.LENGTH_LONG).show()
            } else {
                Log.e("NyayaAuth", "GOOGLE_AUTH_ERROR: Google authentication could not be completed")
                Toast.makeText(context, "Google authentication could not be verified. Please try again or sign in with Email.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToLanding,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = AccentOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, AccentOrange.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Scale,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Citizen Portal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Secure Citizen Justice Node Access",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextGray
                    )
                }
            }

            // Main Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Secure Login",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkSlate
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sign in to access your citizen legal services.",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }

                    // Email Field
                    Column {
                        Text(
                            text = "Email Address",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkSlate,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                emailError = null
                            },
                            placeholder = { Text("Enter your email address", color = TextGray.copy(alpha = 0.6f), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = if (emailError != null) Color(0xFFEF4444) else TextGray
                                )
                            },
                            isError = emailError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("citizen_login_email"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                errorBorderColor = Color(0xFFEF4444),
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray,
                                focusedTextColor = TextDarkSlate,
                                unfocusedTextColor = TextDarkSlate
                            )
                        )
                        if (emailError != null) {
                            Text(
                                text = emailError!!,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    // Password Field
                    Column {
                        Text(
                            text = "Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkSlate,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            placeholder = { Text("Enter your password", color = TextGray.copy(alpha = 0.6f), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (passwordError != null) Color(0xFFEF4444) else TextGray
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Password",
                                        tint = TextGray
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = passwordError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("citizen_login_password"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                errorBorderColor = Color(0xFFEF4444),
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray,
                                focusedTextColor = TextDarkSlate,
                                unfocusedTextColor = TextDarkSlate
                            )
                        )
                        if (passwordError != null) {
                            Text(
                                text = passwordError!!,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    // Remember Me & Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentOrange),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remember me", fontSize = 12.sp, color = TextDarkSlate)
                        }
                        Text(
                            text = "Forgot password?",
                            color = LightJusticeBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(4.dp)
                        )
                    }

                    // CAPTCHA Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Security Verification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkSlate
                        )
                        DynamicCaptcha(
                            captchaValue = captchaCode,
                            onRefresh = { captchaCode = generateRandomCaptcha() }
                        )
                        OutlinedTextField(
                            value = captchaInput,
                            onValueChange = {
                                captchaInput = it
                                captchaError = null
                            },
                            placeholder = { Text("Enter CAPTCHA code", color = TextGray.copy(alpha = 0.6f), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (captchaError != null) Color(0xFFEF4444) else TextGray
                                )
                            },
                            isError = captchaError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("citizen_captcha_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                errorBorderColor = Color(0xFFEF4444),
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray,
                                focusedTextColor = TextDarkSlate,
                                unfocusedTextColor = TextDarkSlate
                            )
                        )
                        if (captchaError != null) {
                            Text(
                                text = captchaError!!,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Sign In Button
                    Button(
                        onClick = {
                            emailError = null
                            passwordError = null
                            captchaError = null

                            val trimmedEmail = email.trim()
                            if (trimmedEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                                emailError = "Please enter a valid email address."
                                Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.isBlank()) {
                                passwordError = "Please enter your password."
                                Toast.makeText(context, "Please enter your password.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (captchaInput.trim().uppercase() != captchaCode) {
                                captchaError = "Incorrect verification code. Please try again."
                                Toast.makeText(context, "Incorrect verification code. Please try again.", Toast.LENGTH_SHORT).show()
                                captchaCode = generateRandomCaptcha()
                                return@Button
                            }

                            isLoading = true
                            viewModel.login(trimmedEmail, password, "Citizen") { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (!success) {
                                    captchaCode = generateRandomCaptcha()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("citizen_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Signing in...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        } else {
                            Text("Sign In", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    // Divider OR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = LightSlateBorder, thickness = 0.5.dp)
                        Text(
                            text = "OR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = LightSlateBorder, thickness = 0.5.dp)
                    }

                    // Continue with Google Button
                    OutlinedButton(
                        onClick = {
                            if (isLoading) return@OutlinedButton
                            isLoading = true
                            Log.i("NyayaAuth", "GOOGLE_AUTH_START: Launching Google Sign-In intent")
                            try {
                                val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                val resultCode = try {
                                    availability.isGooglePlayServicesAvailable(context)
                                } catch (e: Exception) {
                                    com.google.android.gms.common.ConnectionResult.SERVICE_MISSING
                                }

                                if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                                    isLoading = false
                                    Log.w("NyayaAuth", "GOOGLE_AUTH_NOTICE: Google Play Services unavailable (code $resultCode)")
                                    Toast.makeText(context, "Google Sign-In is unavailable on this device environment. Please sign in with email.", Toast.LENGTH_LONG).show()
                                    return@OutlinedButton
                                }

                                val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestProfile()

                                val webClientId = try {
                                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                    if (resId != 0) context.getString(resId) else null
                                } catch (e: Exception) { null }

                                if (!webClientId.isNullOrBlank()) {
                                    Log.i("NyayaAuth", "GOOGLE_AUTH_CONFIG: Requesting ID token with default_web_client_id")
                                    gsoBuilder.requestIdToken(webClientId)
                                }

                                val gso = gsoBuilder.build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)

                                // CRITICAL: Clear previous Google Sign-In session first so the account chooser dialog is ALWAYS presented to the user
                                googleSignInClient.signOut().addOnCompleteListener {
                                    try {
                                        val signInIntent = googleSignInClient.signInIntent
                                        googleSignInLauncher.launch(signInIntent)
                                    } catch (launchEx: Exception) {
                                        isLoading = false
                                        Log.e("NyayaAuth", "GOOGLE_AUTH_ERROR: Failed to launch Google Sign-In intent: ${launchEx.message}")
                                        Toast.makeText(context, "Google Sign-In is unavailable on this device environment. Please sign in with email.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                Log.e("NyayaAuth", "GOOGLE_AUTH_ERROR: Exception launching Google Sign-In: ${e.message}")
                                Toast.makeText(context, "Unable to connect to Google Sign-In. Please sign in with email.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("continue_with_google_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = TextDarkSlate
                        ),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AccentOrange,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting to Google...", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDarkSlate)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF4285F4))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDarkSlate)
                            }
                        }
                    }
                }
            }

            // Bottom Footer Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = TextGray,
                    fontSize = 13.sp
                )
                Text(
                    text = "Create Citizen Account",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }
}

@Composable
fun AuthorityLoginScreen(
    viewModel: NyayaViewModel,
    onNavigateToRegister: () -> Unit = {},
    onNavigateToForgot: () -> Unit = {},
    onBackToLanding: () -> Unit
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val allNotifications by viewModel.allNotifications.collectAsState()

    // Form State
    var selectedDepartment by remember { mutableStateOf("") }
    var selectedDesignation by remember { mutableStateOf("") }
    var officialEmail by remember { mutableStateOf("") }
    var officialPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoginLoading by remember { mutableStateOf(false) }

    var deptExpanded by remember { mutableStateOf(false) }
    var desigExpanded by remember { mutableStateOf(false) }

    // Dialog States for verification enforcement & notifications
    var pendingVerificationDialog by remember { mutableStateOf(false) }
    var rejectedDialog by remember { mutableStateOf(false) }
    var disabledDialog by remember { mutableStateOf(false) }
    var rejectionReasonText by remember { mutableStateOf("") }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Authority notifications filter
    val authorityNotifications = remember(allNotifications, officialEmail) {
        val cleanEmail = officialEmail.trim().lowercase()
        allNotifications.filter {
            it.isAuthority || (cleanEmail.isNotEmpty() && it.userEmail.equals(cleanEmail, ignoreCase = true))
        }.sortedByDescending { it.timestamp }
    }
    val unreadCount = remember(authorityNotifications) {
        authorityNotifications.count { !it.isRead }
    }

    // Dynamic list of designations based on selected department
    val availableDesignations = remember(selectedDepartment) {
        if (selectedDepartment.isNotEmpty()) {
            GovernmentVerificationEngine.getDesignationsForDepartment(selectedDepartment)
        } else {
            emptyList()
        }
    }

    if (pendingVerificationDialog) {
        AlertDialog(
            onDismissRequest = { pendingVerificationDialog = false },
            containerColor = CardBackground,
            icon = {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = "Pending Verification",
                    tint = AccentOrange,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Account Pending Verification",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Your authority account is pending administrator verification.",
                    color = TextGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { pendingVerificationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.fillMaxWidth().testTag("authority_pending_dialog_ok")
                ) {
                    Text("Understood", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (rejectedDialog) {
        AlertDialog(
            onDismissRequest = { rejectedDialog = false },
            containerColor = CardBackground,
            icon = {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Account Rejected",
                    tint = WarningRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Authority Account Rejected",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Admin cancelled your login due to invalid credentials or unsuccessful verification.",
                        color = TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (rejectionReasonText.isNotBlank()) {
                        Surface(
                            color = WarningRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WarningRed.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Text(
                                text = "Note: $rejectionReasonText",
                                color = WarningRed,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { rejectedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                    modifier = Modifier.fillMaxWidth().testTag("authority_rejected_dialog_close")
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (disabledDialog) {
        AlertDialog(
            onDismissRequest = { disabledDialog = false },
            containerColor = CardBackground,
            icon = {
                Icon(
                    Icons.Default.Block,
                    contentDescription = "Account Disabled",
                    tint = WarningRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Account Disabled",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Admin cancelled your login due to invalid credentials or unsuccessful verification.",
                    color = TextGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { disabledDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                    modifier = Modifier.fillMaxWidth().testTag("authority_disabled_dialog_close")
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Authority Notifications Activity Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentOrange)
                        Text(
                            text = "Authority Verification Activity",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (unreadCount > 0) {
                        Surface(
                            color = AccentOrange,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "$unreadCount new",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (authorityNotifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = TextGray, modifier = Modifier.size(36.dp))
                                Text(
                                    text = "No verification activity notifications yet.",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        authorityNotifications.forEach { notif ->
                            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(notif.timestamp))
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notif.timestamp))
                            val isApprovedNotif = notif.title.contains("verified", ignoreCase = true) || notif.message.contains("Approved", ignoreCase = true)
                            val isCancelledNotif = notif.title.contains("cancelled", ignoreCase = true) || notif.message.contains("Cancelled", ignoreCase = true) || notif.message.contains("Rejected", ignoreCase = true)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!notif.isRead) DarkIndigo else CardBackground
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isApprovedNotif -> SuccessGreen.copy(alpha = 0.6f)
                                        isCancelledNotif -> WarningRed.copy(alpha = 0.6f)
                                        else -> AccentOrange.copy(alpha = 0.6f)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = notif.title,
                                            color = when {
                                                isApprovedNotif -> SuccessGreen
                                                isCancelledNotif -> WarningRed
                                                else -> AccentOrange
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = DarkIndigo,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "$dateStr $timeStr",
                                                color = TextGray,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = notif.message,
                                        color = TextDarkSlate,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )

                                    if (!notif.isRead) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(
                                                onClick = { viewModel.markNotificationRead(notif.id) },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Mark Read", color = LightJusticeBlue, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Close", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar with Back Button, Status Badge, and Notifications Bell
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToLanding,
                    modifier = Modifier.testTag("authority_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                }
                Spacer(modifier = Modifier.weight(1f))

                // Security Status Badge
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SuccessGreen))
                        Text(
                            text = "Official Authority Access",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightJusticeBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Authority Notification Bell Icon with Badge
                IconButton(
                    onClick = { showNotificationsDialog = true },
                    modifier = Modifier.testTag("authority_notification_bell")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(
                                    containerColor = WarningRed,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Authority Notifications",
                            tint = AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Top Official Emblem & Branding Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.linearGradient(listOf(JusticeBlue, AccentOrange)),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Authority Shield",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Authority Portal",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentOrange,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = JusticeBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, JusticeBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Authorized Government Law Enforcement Access",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightJusticeBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Secure Identity & Credential Verification",
                        fontSize = 11.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Authority Sign In Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Officer Authentication Credentials",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )
                    Text(
                        text = "Select official department, designation, and credentials to authenticate server-side.",
                        fontSize = 11.sp,
                        color = TextGray
                    )

                    // Department Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Assigned Department", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { deptExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authority_department_dropdown"),
                                border = BorderStroke(1.dp, LightSlateBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkSlate)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedDepartment.isEmpty()) "Select Department" else selectedDepartment,
                                        fontSize = 12.sp,
                                        color = if (selectedDepartment.isEmpty()) TextGray else TextDarkSlate
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Department", tint = AccentOrange)
                                }
                            }
                            DropdownMenu(
                                expanded = deptExpanded,
                                onDismissRequest = { deptExpanded = false },
                                modifier = Modifier.background(CardBackground)
                            ) {
                                GovernmentVerificationEngine.SUPPORTED_DEPARTMENTS.forEach { dept ->
                                    DropdownMenuItem(
                                        text = { Text(dept, color = TextDarkSlate, fontSize = 12.sp) },
                                        onClick = {
                                            selectedDepartment = dept
                                            deptExpanded = false
                                            val validForDept = GovernmentVerificationEngine.getDesignationsForDepartment(dept)
                                            if (!validForDept.contains(selectedDesignation)) {
                                                selectedDesignation = ""
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Designation Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Designation / Official Rank", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedDepartment.isEmpty()) {
                                        Toast.makeText(context, "Please select a Department first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        desigExpanded = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authority_designation_dropdown"),
                                border = BorderStroke(1.dp, LightSlateBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkSlate)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedDesignation.isEmpty()) "Select Designation / Official Rank" else selectedDesignation,
                                        fontSize = 12.sp,
                                        color = if (selectedDesignation.isEmpty()) TextGray else TextDarkSlate
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Designation", tint = AccentOrange)
                                }
                            }
                            DropdownMenu(
                                expanded = desigExpanded,
                                onDismissRequest = { desigExpanded = false },
                                modifier = Modifier.background(CardBackground)
                            ) {
                                availableDesignations.forEach { desig ->
                                    DropdownMenuItem(
                                        text = { Text(desig, color = TextDarkSlate, fontSize = 12.sp) },
                                        onClick = {
                                            selectedDesignation = desig
                                            desigExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Official Email Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Official Email Address", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = officialEmail,
                            onValueChange = { officialEmail = it },
                            placeholder = { Text("e.g. officer@gov.in or official email", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_email_input")
                        )
                    }

                    // Official Password Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Official Password", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = officialPassword,
                            onValueChange = { officialPassword = it },
                            placeholder = { Text("Enter official password", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextGray
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_password_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sign In Button
                    Button(
                        onClick = {
                            if (selectedDepartment.isBlank() || selectedDesignation.isBlank()) {
                                Toast.makeText(context, "Please select both Department and Designation / Official Rank.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoginLoading = true
                            viewModel.loginAuthorityAccount(
                                department = selectedDepartment,
                                designation = selectedDesignation,
                                emailInput = officialEmail,
                                passwordInput = officialPassword
                            ) { success: Boolean, msg: String, account: UserAccount?, statusCode: String ->
                                isLoginLoading = false
                                when (statusCode) {
                                    "PENDING_VERIFICATION" -> {
                                        pendingVerificationDialog = true
                                    }
                                    "REJECTED" -> {
                                        rejectionReasonText = account?.rejectionReason ?: ""
                                        rejectedDialog = true
                                    }
                                    "DISABLED" -> {
                                        disabledDialog = true
                                    }
                                    else -> {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("authority_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoginLoading
                    ) {
                        if (isLoginLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "Verify & Sign In to Authority Portal",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Create New Account Link
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an authority account? ",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = onNavigateToRegister,
                            modifier = Modifier.testTag("authority_create_account_button")
                        ) {
                            Text(
                                text = "Create New Account",
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Official Governance Notice Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.7f)),
                border = BorderStroke(0.5.dp, LightJusticeBlue.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = "Government Verification",
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Official Government Access Notice",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkSlate
                        )
                        Text(
                            text = "Access is restricted to authorized personnel. Official credentials are authenticated server-side against government directories.",
                            fontSize = 10.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorityRegisterScreen(
    viewModel: NyayaViewModel,
    onNavigateToLogin: () -> Unit,
    onBackToLanding: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form fields
    var fullName by remember { mutableStateOf("") }
    var officialEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var selectedDepartment by remember { mutableStateOf("") }
    var selectedDesignation by remember { mutableStateOf("") }
    var deptExpanded by remember { mutableStateOf(false) }
    var desigExpanded by remember { mutableStateOf(false) }

    var proofImageBase64 by remember { mutableStateOf("") }
    var proofImageFileName by remember { mutableStateOf("") }
    var showProofSourceDialog by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Dynamic list of designations based on selected department
    val availableDesignations = remember(selectedDepartment) {
        if (selectedDepartment.isNotEmpty()) {
            GovernmentVerificationEngine.getDesignationsForDepartment(selectedDepartment)
        } else {
            emptyList()
        }
    }

    // Gallery Picker for Official Government Staff Proof
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                var fileName = "official_proof.jpg"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                } catch (_: Exception) {}

                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val maxDim = 800
                        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                        } else {
                            bitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val base64 = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            proofImageBase64 = base64
                            proofImageFileName = fileName
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            proofImageBase64 = uri.toString()
                            proofImageFileName = fileName
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        proofImageBase64 = uri.toString()
                        proofImageFileName = fileName
                    }
                }
            }
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val maxDim = 800
                val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else {
                    bitmap
                }
                val baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val bytes = baos.toByteArray()
                val base64 = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "official_proof_camera_$timeStamp.jpg"
                withContext(Dispatchers.Main) {
                    proofImageBase64 = base64
                    proofImageFileName = fileName
                }
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to photograph official proof.", Toast.LENGTH_SHORT).show()
        }
    }

    // Proof Upload Source Selection Dialog (Camera or Gallery)
    if (showProofSourceDialog) {
        AlertDialog(
            onDismissRequest = { showProofSourceDialog = false },
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AccentOrange)
                    Text(
                        text = "Official Proof Image Source",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select an option to provide your Government ID or Official Authorization Proof:",
                        color = TextGray,
                        fontSize = 12.sp
                    )

                    // Option 1: Take Photo with Camera
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showProofSourceDialog = false
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                        border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(JusticeBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = LightJusticeBlue)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Take Photo with Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Capture your official ID card or badge", color = TextGray, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Option 2: Choose from Photos/Gallery
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showProofSourceDialog = false
                                galleryLauncher.launch("image/*")
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                        border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = AccentOrange)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Choose from Photos / Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Select existing document or image file", color = TextGray, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProofSourceDialog = false }) {
                    Text("Cancel", color = TextGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onNavigateToLogin()
            },
            containerColor = CardBackground,
            icon = {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = "Submitted",
                    tint = SuccessGreen,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    text = "Registration Submitted",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Your Authority account has been successfully submitted for verification. Your account is currently pending Administrator verification. You will be able to access the Authority Portal only after your account has been approved.",
                    color = TextGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.fillMaxWidth().testTag("authority_success_back_button")
                ) {
                    Text("Back to Login", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.testTag("authority_register_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                }
                Spacer(modifier = Modifier.weight(1f))

                // Security Status Badge
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentOrange))
                        Text(
                            text = "Official Registration",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }

            // Top Emblem & Title Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(JusticeBlue, AccentOrange)),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = "Staff Registration",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Authority Registration",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentOrange,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = JusticeBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, JusticeBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Government Staff Verification System",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightJusticeBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Register official identity and submit government proof for Administrator approval.",
                        fontSize = 11.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Registration Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Official Identity Details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    // Full Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Official Full Name *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            placeholder = { Text("e.g. Officer John Doe", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("authority_register_fullname")
                        )
                    }

                    // Official Email
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Official Email Address *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = officialEmail,
                            onValueChange = { officialEmail = it },
                            placeholder = { Text("e.g. officer@gov.in or email", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("authority_register_email")
                        )
                    }

                    // Password
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Create Official Password *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("Minimum 6 characters", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextGray
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("authority_register_password")
                        )
                    }

                    // Confirm Password
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Confirm Password *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            placeholder = { Text("Re-enter password", color = TextGray.copy(alpha = 0.6f), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.LockReset, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextGray
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("authority_register_confirm_password")
                        )
                    }

                    // Department Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Assigned Department *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { deptExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authority_register_dept_dropdown"),
                                border = BorderStroke(1.dp, LightSlateBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkSlate)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedDepartment.isEmpty()) "Select Department" else selectedDepartment,
                                        fontSize = 12.sp,
                                        color = if (selectedDepartment.isEmpty()) TextGray else TextDarkSlate
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Department", tint = AccentOrange)
                                }
                            }
                            DropdownMenu(
                                expanded = deptExpanded,
                                onDismissRequest = { deptExpanded = false },
                                modifier = Modifier.background(CardBackground)
                            ) {
                                GovernmentVerificationEngine.SUPPORTED_DEPARTMENTS.forEach { dept ->
                                    DropdownMenuItem(
                                        text = { Text(dept, color = TextDarkSlate, fontSize = 12.sp) },
                                        onClick = {
                                            selectedDepartment = dept
                                            deptExpanded = false
                                            val validForDept = GovernmentVerificationEngine.getDesignationsForDepartment(dept)
                                            if (!validForDept.contains(selectedDesignation)) {
                                                selectedDesignation = ""
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Designation Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Designation / Official Rank *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedDepartment.isEmpty()) {
                                        Toast.makeText(context, "Please select a Department first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        desigExpanded = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authority_register_desig_dropdown"),
                                border = BorderStroke(1.dp, LightSlateBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkSlate)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedDesignation.isEmpty()) "Select Designation / Official Rank" else selectedDesignation,
                                        fontSize = 12.sp,
                                        color = if (selectedDesignation.isEmpty()) TextGray else TextDarkSlate
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Designation", tint = AccentOrange)
                                }
                            }
                            DropdownMenu(
                                expanded = desigExpanded,
                                onDismissRequest = { desigExpanded = false },
                                modifier = Modifier.background(CardBackground)
                            ) {
                                availableDesignations.forEach { desig ->
                                    DropdownMenuItem(
                                        text = { Text(desig, color = TextDarkSlate, fontSize = 12.sp) },
                                        onClick = {
                                            selectedDesignation = desig
                                            desigExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // MANDATORY: Official Government Staff Proof
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Official Government Staff Proof *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            Surface(
                                color = AccentOrange.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, AccentOrange)
                            ) {
                                Text("MANDATORY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text(
                            text = "Upload a valid Government ID Card, Department Service Badge, or Official Authorization Document (JPG, JPEG, PNG).",
                            fontSize = 10.sp,
                            color = TextGray
                        )

                        if (proofImageBase64.isEmpty()) {
                            // Upload Button Area
                            Surface(
                                onClick = { showProofSourceDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("authority_upload_proof_button"),
                                color = DarkIndigo,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, LightJusticeBlue.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = "Upload Proof Image",
                                        tint = LightJusticeBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Upload Official Proof Image",
                                        color = LightJusticeBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Take photo with Camera or choose from Photos",
                                        color = TextGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            // Image Preview & Actions Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                                border = BorderStroke(1.dp, SuccessGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Thumbnail
                                        val previewBitmap = remember(proofImageBase64) {
                                            try {
                                                if (proofImageBase64.startsWith("data:image")) {
                                                    val base64Data = proofImageBase64.substringAfter("base64,")
                                                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                } else {
                                                    null
                                                }
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }

                                        if (previewBitmap != null) {
                                            Image(
                                                bitmap = previewBitmap.asImageBitmap(),
                                                contentDescription = "Proof Preview",
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, LightSlateBorder, RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CardBackground),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                                Text("Staff Proof Attached", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = proofImageFileName.ifEmpty { "proof_document.jpg" },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Action buttons: Replace & Remove
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showProofSourceDialog = true },
                                            modifier = Modifier.weight(1f).height(34.dp).testTag("authority_replace_proof_button"),
                                            border = BorderStroke(1.dp, LightJusticeBlue),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LightJusticeBlue),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                proofImageBase64 = ""
                                                proofImageFileName = ""
                                            },
                                            modifier = Modifier.weight(1f).height(34.dp).testTag("authority_remove_proof_button"),
                                            border = BorderStroke(1.dp, WarningRed),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Submit Registration Button
                    Button(
                        onClick = {
                            if (fullName.isBlank()) {
                                Toast.makeText(context, "Please enter your Official Full Name.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (officialEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(officialEmail.trim()).matches()) {
                                Toast.makeText(context, "Please enter a valid Official Email Address.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password != confirmPassword) {
                                Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedDepartment.isBlank()) {
                                Toast.makeText(context, "Please select an Assigned Department.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedDesignation.isBlank()) {
                                Toast.makeText(context, "Please select a Designation / Official Rank.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (proofImageBase64.isBlank()) {
                                Toast.makeText(context, "Please upload your Official Government Staff Proof.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            isRegistering = true
                            viewModel.registerAuthorityAccount(
                                fullName = fullName,
                                officialEmail = officialEmail,
                                password = password,
                                department = selectedDepartment,
                                designation = selectedDesignation,
                                proofImage = proofImageBase64
                            ) { success: Boolean, msg: String, account: UserAccount? ->
                                isRegistering = false
                                if (success) {
                                    showSuccessDialog = true
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("authority_submit_registration_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isRegistering
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "Submit Official Registration",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Already have an account? Sign In link
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already registered? ",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.testTag("authority_already_registered_button")
                        ) {
                            Text(
                                text = "Sign In to Authority Portal",
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Governance Security Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.7f)),
                border = BorderStroke(0.5.dp, LightJusticeBlue.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Security Note",
                        tint = AccentOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Mandatory Administrator Verification",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkSlate
                        )
                        Text(
                            text = "Authority registrations require administrative approval and government staff credential review before access to the portal is granted.",
                            fontSize = 10.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLoginScreen(
    viewModel: NyayaViewModel,
    onNavigateToForgot: () -> Unit,
    onBackToLanding: () -> Unit
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    var inputIdentity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToLanding) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(JusticeBlue, AccentOrange)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Admin",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "NyayaAI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = TextDarkSlate
            )
            Text(
                text = "Admin Dashboard",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange
            )
            Text(
                text = "Secure Administrator Access",
                fontSize = 12.sp,
                color = TextGray,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Administrator Credentials",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    OutlinedTextField(
                        value = inputIdentity,
                        onValueChange = { inputIdentity = it },
                        label = { Text("Email") },
                        placeholder = { Text("Enter admin email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_email"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = { Text("Enter admin password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_password"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = I18n.getString("forgot_key", lang),
                            color = LightJusticeBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(4.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (inputIdentity.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter the admin email.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter the admin password.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.login(inputIdentity.trim(), password, "Admin") { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Signing in...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else {
                            Text("Sign In", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: NyayaViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf(generateRandomCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = I18n.getString("register_title", lang),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = AccentOrange
            )
            
            Text(
                text = I18n.getString("register_subtitle", lang),
                fontSize = 11.sp,
                color = TextGray,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = I18n.getString("citizen_acc_setup", lang),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(I18n.getString("full_legal_name", lang)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth().testTag("register_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(I18n.getString("email_address", lang)) },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth().testTag("register_email_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(I18n.getString("password", lang)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("register_password_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, LightSlateBorder.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = I18n.getString("register_notice", lang),
                                fontSize = 9.5.sp,
                                color = TextGray,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Text(I18n.getString("captcha_code", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                    DynamicCaptcha(
                        captchaValue = captchaCode,
                        onRefresh = { captchaCode = generateRandomCaptcha() }
                    )

                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = { captchaInput = it },
                        label = { Text(I18n.getString("captcha_placeholder", lang)) },
                        modifier = Modifier.fillMaxWidth().testTag("register_captcha_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray,
                            focusedTextColor = TextDarkSlate,
                            unfocusedTextColor = TextDarkSlate
                        )
                    )

                    Button(
                        onClick = {
                            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                                Toast.makeText(context, "All registration fields are mandatory.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (captchaInput.trim().uppercase() != captchaCode) {
                                Toast.makeText(context, "CAPTCHA verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                                captchaCode = generateRandomCaptcha()
                                return@Button
                            }
                            isLoading = true
                            viewModel.register(name, email, password, "Citizen") { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    onNavigateToLogin()
                                } else {
                                    captchaCode = generateRandomCaptcha()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("register_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(I18n.getString("register_btn", lang), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = I18n.getString("already_have_acc", lang),
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    viewModel: NyayaViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var isLinkSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGoogleOnlyWarning by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentOrange
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            if (!isLinkSent) {
                // ==================== SCREEN 1: RESET PASSWORD ====================
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(AccentOrange.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, AccentOrange.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Reset Password",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentOrange
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, LightSlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Enter your registered email address and we'll send you a secure password reset link.",
                            fontSize = 13.sp,
                            color = TextDarkSlate,
                            lineHeight = 18.sp
                        )

                        if (isGoogleOnlyWarning) {
                            Surface(
                                color = Color(0xFF4285F4).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF4285F4))
                                    Text(
                                        text = "This account uses Google Sign-In. Please continue with Google.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF93C5FD),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                                isGoogleOnlyWarning = false
                            },
                            label = { Text("Registered Email Address") },
                            placeholder = { Text("example@gmail.com", color = TextGray.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = AccentOrange) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_email_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray,
                                focusedTextColor = TextDarkSlate,
                                unfocusedTextColor = TextDarkSlate
                            )
                        )

                        if (errorMessage != null && !isGoogleOnlyWarning) {
                            Text(
                                text = errorMessage!!,
                                color = WarningRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Button(
                            onClick = {
                                errorMessage = null
                                isGoogleOnlyWarning = false
                                val trimmedEmail = email.trim()
                                if (trimmedEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                                    errorMessage = "Please enter a valid email address."
                                    Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isLoading = true
                                viewModel.sendCitizenPasswordResetEmail(trimmedEmail) { success, msg, isGoogleOnly ->
                                    isLoading = false
                                    if (isGoogleOnly) {
                                        isGoogleOnlyWarning = true
                                        errorMessage = msg
                                    } else if (success) {
                                        Toast.makeText(context, "Password reset link sent. Please check your email.", Toast.LENGTH_LONG).show()
                                        isLinkSent = true
                                    } else {
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("send_reset_link_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Sending reset link...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            } else {
                                Text("Send Reset Link", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "Return to Sign In",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .padding(8.dp)
                )
            } else {
                // ==================== SCREEN 2: LINK SENT CONFIRMATION ====================
                Text(
                    text = "Reset Link Sent",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentOrange
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, LightSlateBorder),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MarkEmailRead,
                                contentDescription = "Email Sent",
                                tint = SuccessGreen,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Text(
                            text = "Password reset link sent. Please check your email and follow the instructions to create a new password.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkSlate,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LightSlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Sent to:",
                                    fontSize = 11.sp,
                                    color = TextGray
                                )
                                Text(
                                    text = email.trim(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                            }
                        }

                        Text(
                            text = "Open the link in your email to choose your new password, then return to the application to sign in.",
                            fontSize = 12.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("return_to_signin_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Return to Sign In", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        TextButton(
                            onClick = {
                                isLinkSent = false
                                errorMessage = null
                            }
                        ) {
                            Text(
                                text = "Didn't receive the email? Resend link",
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminForgotPasswordScreen(
    viewModel: NyayaViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf("VERIFY_CODE") } // "VERIFY_CODE" -> "CREATE_NEW_PASSWORD" -> "RESET_SUCCESSFUL"
    var codeInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val hasMinLength = newPassword.length >= 8
    val hasUppercase = newPassword.any { it.isUpperCase() }
    val hasLowercase = newPassword.any { it.isLowerCase() }
    val hasDigit = newPassword.any { it.isDigit() }
    val hasSpecial = newPassword.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword

    val strength = remember(newPassword) {
        var score = 0
        if (hasMinLength) score++
        if (hasUppercase && hasLowercase) score++
        if (hasDigit) score++
        if (hasSpecial) score++
        score
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when (step) {
                        "CREATE_NEW_PASSWORD" -> step = "VERIFY_CODE"
                        else -> {
                            codeInput = ""
                            newPassword = ""
                            confirmPassword = ""
                            onNavigateToLogin()
                        }
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(JusticeBlue, AccentOrange)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step == "RESET_SUCCESSFUL") Icons.Default.CheckCircle else Icons.Default.Key,
                    contentDescription = "Admin Recovery",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "NyayaAI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = TextDarkSlate
            )

            when (step) {
                "VERIFY_CODE" -> {
                    Text(
                        text = "Admin Password Recovery",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Admin Password Recovery",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            Text(
                                text = "Enter your secure admin recovery secret code.",
                                fontSize = 12.sp,
                                color = TextGray,
                                lineHeight = 16.sp
                            )

                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it },
                                label = { Text("Admin Recovery Secret Code") },
                                placeholder = { Text("Enter recovery secret code") },
                                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_secret_code_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = LightSlateBorder,
                                    focusedLabelColor = AccentOrange,
                                    unfocusedLabelColor = TextGray,
                                    focusedTextColor = TextDarkSlate,
                                    unfocusedTextColor = TextDarkSlate
                                )
                            )

                            Button(
                                onClick = {
                                    val trimmed = codeInput.trim()
                                    if (trimmed.isEmpty()) {
                                        Toast.makeText(context, "Please enter the admin recovery secret code.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    viewModel.verifyAdminVerificationCode("sakthivel.s8317@gmail.com", trimmed) { success, msg ->
                                        isLoading = false
                                        if (success) {
                                            step = "CREATE_NEW_PASSWORD"
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_secret_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verifying...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                } else {
                                    Text("Verify Recovery Secret", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                "CREATE_NEW_PASSWORD" -> {
                    Text(
                        text = "Create New Password",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Set Administrator Password",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("New Password") },
                                placeholder = { Text("Enter new password") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                        Icon(
                                            imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle New Password Visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (showNewPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_new_password_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = LightSlateBorder,
                                    focusedLabelColor = AccentOrange,
                                    unfocusedLabelColor = TextGray,
                                    focusedTextColor = TextDarkSlate,
                                    unfocusedTextColor = TextDarkSlate
                                )
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                placeholder = { Text("Re-enter new password") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                        Icon(
                                            imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Confirm Password Visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (showConfirmPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_confirm_password_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = LightSlateBorder,
                                    focusedLabelColor = AccentOrange,
                                    unfocusedLabelColor = TextGray,
                                    focusedTextColor = TextDarkSlate,
                                    unfocusedTextColor = TextDarkSlate
                                )
                            )

                            Text("Password Strength", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(4) { idx ->
                                    val isFilled = idx < strength
                                    val barColor = when (strength) {
                                        1 -> WarningRed
                                        2 -> Color(0xFFF97316)
                                        3 -> Color(0xFFEAB308)
                                        else -> SuccessGreen
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .background(
                                                if (isFilled) barColor else LightSlateBorder,
                                                RoundedCornerShape(3.dp)
                                            )
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuleRow(label = "At least 8 characters", isMet = hasMinLength)
                                RuleRow(label = "At least 1 uppercase letter", isMet = hasUppercase)
                                RuleRow(label = "At least 1 lowercase letter", isMet = hasLowercase)
                                RuleRow(label = "At least 1 number", isMet = hasDigit)
                                RuleRow(label = "At least 1 special character (@#$%^&*...)", isMet = hasSpecial)
                                RuleRow(label = "Passwords match", isMet = passwordsMatch)
                            }

                            Button(
                                onClick = {
                                    val p1 = newPassword.trim()
                                    val p2 = confirmPassword.trim()
                                    if (p1.isEmpty() || p2.isEmpty()) {
                                        Toast.makeText(context, "Please fill in both password fields.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (p1 != p2) {
                                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    viewModel.resetAdminPasswordWithToken(p1, p2) { success, msg ->
                                        isLoading = false
                                        if (success) {
                                            step = "RESET_SUCCESSFUL"
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_update_password_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Updating password...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                } else {
                                    Text("Update Password", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                "RESET_SUCCESSFUL" -> {
                    Text(
                        text = "Password Reset Successful",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = SuccessGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Password Reset Successful",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Password changed successfully. Please sign in with your new password.",
                                fontSize = 13.sp,
                                color = TextGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Button(
                                onClick = {
                                    codeInput = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    onNavigateToLogin()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("return_to_admin_login_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Return to Admin Login", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            if (step != "RESET_SUCCESSFUL") {
                Text(
                    text = "Return to Admin Sign-In",
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            codeInput = ""
                            newPassword = ""
                            confirmPassword = ""
                            onNavigateToLogin()
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun RuleRow(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isMet) SuccessGreen else TextGray.copy(alpha = 0.3f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = if (isMet) TextDarkSlate else TextGray
        )
    }
}



@Composable
fun AdminUsersScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.allUserAccounts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var deletingAccount by remember { mutableStateOf<UserAccount?>(null) }
    var viewProofAccount by remember { mutableStateOf<UserAccount?>(null) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Citizens", "Authorities", "Admins"

    val userRole = userProfile.role.ifBlank { viewModel.currentUserAccount.value?.role ?: "" }
    val userEmail = userProfile.email.ifBlank { viewModel.currentUserAccount.value?.email ?: "" }
    val normalizedRole = com.example.firebase.FirebaseManager.normalizeRole(userRole)
    val isAdmin = normalizedRole == "admin" ||
        normalizedRole == "root_admin" ||
        userEmail.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
        userEmail.equals("srisakthi1357@gmail.com", ignoreCase = true)

    if (!isLoggedIn || !isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkIndigo)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, WarningRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Access Denied", tint = WarningRed, modifier = Modifier.size(36.dp))
                    Text("Access Denied", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WarningRed)
                    Text("Administrator privileges required to access Central User Management.", fontSize = 12.sp, color = TextGray, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val filteredAccounts = remember(accounts, selectedFilter) {
        when (selectedFilter) {
            "Citizens" -> accounts.filter { it.role.equals("Citizen", ignoreCase = true) }
            "Authorities" -> accounts.filter { it.role.equals("Authority", ignoreCase = true) }
            "Admins" -> accounts.filter { it.role.equals("Admin", ignoreCase = true) }
            else -> accounts
        }
    }

    val citizenCount = accounts.count { it.role.equals("Citizen", ignoreCase = true) }
    val authorityCount = accounts.count { it.role.equals("Authority", ignoreCase = true) }
    val adminCount = accounts.count { it.role.equals("Admin", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        // Authenticated Admin Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("User Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Text(
                    text = "${userProfile.name.ifBlank { "NyayaaAI Admin" }} • ${userProfile.email.ifBlank { "admin@nyaya.ai" }}",
                    fontSize = 12.sp,
                    color = TextDarkSlate,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                color = WarningRed.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, WarningRed),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "ADMIN",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningRed,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Role Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "All" to "All (${accounts.size})",
                "Citizens" to "Citizens ($citizenCount)",
                "Authorities" to "Authorities ($authorityCount)",
                "Admins" to "Admins ($adminCount)"
            )
            filters.forEach { (filterKey, label) ->
                val isSelected = selectedFilter == filterKey
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filterKey },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange,
                        selectedLabelColor = Color.Black,
                        containerColor = CardBackground,
                        labelColor = TextDarkSlate
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = LightSlateBorder,
                        selectedBorderColor = AccentOrange
                    )
                )
            }
        }

        if (filteredAccounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = TextGray, modifier = Modifier.size(40.dp))
                    Text(
                        text = if (accounts.isEmpty()) "No registered users found." else "No registered users found in $selectedFilter.",
                        color = TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredAccounts) { account ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Header Row: Avatar, Name/UID, Online Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Avatar circle
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (account.role) {
                                                    "Admin" -> WarningRed.copy(alpha = 0.2f)
                                                    "Authority" -> JusticeBlue.copy(alpha = 0.2f)
                                                    else -> SuccessGreen.copy(alpha = 0.2f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = account.name.take(1).uppercase().ifBlank { "U" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = when (account.role) {
                                                "Admin" -> WarningRed
                                                "Authority" -> LightJusticeBlue
                                                else -> SuccessGreen
                                            }
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(account.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
                                        Text(account.email, fontSize = 11.sp, color = TextGray)
                                        if (account.uid.isNotBlank()) {
                                            Text("UID: ${account.uid}", fontSize = 9.sp, color = TextGray.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                // Online / Offline Indicator Badge
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (account.isOnline) SuccessGreen else TextGray)
                                        )
                                        Text(
                                            text = if (account.isOnline) "ONLINE" else "OFFLINE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (account.isOnline) SuccessGreen else TextGray
                                        )
                                    }

                                    // Role Badge
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = when (account.role) {
                                                "Admin" -> WarningRed.copy(alpha = 0.2f)
                                                "Authority" -> JusticeBlue.copy(alpha = 0.2f)
                                                else -> SuccessGreen.copy(alpha = 0.2f)
                                            }
                                        ),
                                        border = BorderStroke(1.dp, when (account.role) {
                                            "Admin" -> WarningRed
                                            "Authority" -> JusticeBlue
                                            else -> SuccessGreen
                                        }),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = account.role.uppercase(),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (account.role) {
                                                "Admin" -> WarningRed
                                                "Authority" -> LightJusticeBlue
                                                else -> SuccessGreen
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Authority Full Details, Proof & Governance Controls
                            if (account.role == "Authority") {
                                // 1. Authority Details Section
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkIndigo.copy(alpha = 0.6f)),
                                    border = BorderStroke(0.5.dp, LightJusticeBlue.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Authority Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightJusticeBlue)

                                            val isBlocked = account.isDisabled || account.status.equals("Disabled", ignoreCase = true)
                                            val isPending = account.approvalStatus == "PENDING_ADMIN_VERIFICATION" || account.approvalStatus == "PENDING_VERIFICATION" || (!account.isApproved && account.approvalStatus != "REJECTED" && !isBlocked)
                                            val isApproved = (account.approvalStatus == "APPROVED" || account.isApproved) && !isBlocked
                                            val isRejected = account.approvalStatus == "REJECTED"

                                            Surface(
                                                color = when {
                                                    isBlocked -> WarningRed.copy(alpha = 0.2f)
                                                    isPending -> AccentOrange.copy(alpha = 0.2f)
                                                    isApproved -> SuccessGreen.copy(alpha = 0.2f)
                                                    isRejected -> WarningRed.copy(alpha = 0.2f)
                                                    else -> AccentOrange.copy(alpha = 0.2f)
                                                },
                                                border = BorderStroke(1.dp, when {
                                                    isBlocked -> WarningRed
                                                    isPending -> AccentOrange
                                                    isApproved -> SuccessGreen
                                                    isRejected -> WarningRed
                                                    else -> AccentOrange
                                                }),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = when {
                                                        isBlocked -> "DISABLED"
                                                        isPending -> "PENDING VERIFICATION"
                                                        isApproved -> "ACTIVE / APPROVED"
                                                        isRejected -> "REJECTED"
                                                        else -> account.approvalStatus.ifEmpty { "PENDING" }
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when {
                                                        isBlocked -> WarningRed
                                                        isPending -> AccentOrange
                                                        isApproved -> SuccessGreen
                                                        isRejected -> WarningRed
                                                        else -> AccentOrange
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = LightSlateBorder.copy(alpha = 0.4f), thickness = 0.5.dp)

                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Full Name: ${account.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                                            Text("Official Email: ${account.email}", fontSize = 11.sp, color = TextGray)
                                            Text("Employee ID: ${account.employeeId.ifEmpty { "N/A" }}", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold)
                                            Text("Department: ${account.department.ifEmpty { "Law Enforcement" }}", fontSize = 11.sp, color = LightJusticeBlue, fontWeight = FontWeight.Medium)
                                            Text("Designation / Official Rank: ${account.designation.ifEmpty { "Officer" }}", fontSize = 11.sp, color = TextDarkSlate)
                                            val regDateStr = remember(account.createdAt) {
                                                try {
                                                    val sdf = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
                                                    sdf.format(Date(if (account.createdAt > 0) account.createdAt else System.currentTimeMillis()))
                                                } catch (e: Exception) { "N/A" }
                                            }
                                            Text("Registration Date & Time: $regDateStr", fontSize = 10.sp, color = TextGray)
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // 2. Official Government Staff Proof Section
                                        Text("Official Government Staff Proof", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                        if (account.proofImage.isNotBlank()) {
                                            val proofBitmap = remember(account.proofImage) {
                                                try {
                                                    if (account.proofImage.startsWith("data:image")) {
                                                        val base64Data = account.proofImage.substringAfter("base64,")
                                                        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                    } else {
                                                        null
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }

                                            Surface(
                                                color = DarkIndigo.copy(alpha = 0.8f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, LightJusticeBlue.copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        if (proofBitmap != null) {
                                                            Image(
                                                                bitmap = proofBitmap.asImageBitmap(),
                                                                contentDescription = "Official Government Staff Proof Thumbnail",
                                                                modifier = Modifier
                                                                    .size(54.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .border(1.dp, LightSlateBorder, RoundedCornerShape(4.dp))
                                                                    .clickable { viewProofAccount = account },
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(54.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(DarkIndigo)
                                                                    .border(1.dp, LightSlateBorder, RoundedCornerShape(4.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.Badge, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(28.dp))
                                                            }
                                                        }

                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text("Official Staff Proof Record", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                            Text("Uploaded by ${account.name}", fontSize = 9.sp, color = TextGray)
                                                            Text("Status: Attached to Account", fontSize = 9.sp, color = SuccessGreen)
                                                        }
                                                    }

                                                    Button(
                                                        onClick = { viewProofAccount = account },
                                                        colors = ButtonDefaults.buttonColors(containerColor = LightJusticeBlue),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp).testTag("view_proof_${account.email}")
                                                    ) {
                                                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("View Proof", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                color = DarkIndigo.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, LightSlateBorder.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Info, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                                                    Text("Official Proof Not Available", fontSize = 10.sp, color = TextGray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // 3. Account Controls: [Activate] [Disable] [Delete]
                                        Text("Account Controls", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Activate Button
                                            Button(
                                                onClick = {
                                                    viewModel.activateAuthorityAccount(account.email) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).height(32.dp).testTag("activate_authority_${account.email}")
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Activate", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }

                                            // Disable Button
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.disableAuthorityAccount(account.email) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                border = BorderStroke(1.dp, AccentOrange),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).height(32.dp).testTag("disable_authority_${account.email}")
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Disable", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                            }

                                            // Delete Button
                                            val isRootAdminTarget = account.email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) || account.email.equals("srisakthi1357@gmail.com", ignoreCase = true)
                                            if (!isRootAdminTarget && account.email != viewModel.userProfile.value.email) {
                                                OutlinedButton(
                                                    onClick = { deletingAccount = account },
                                                    border = BorderStroke(1.dp, WarningRed),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("delete_user_${account.email}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = WarningRed, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarningRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Timestamps Row for Non-Authority Accounts: Created & Last Login
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val createdStr = remember(account.createdAt) {
                                        try { dateFormat.format(Date(account.createdAt)) } catch (e: Exception) { "N/A" }
                                    }
                                    val lastLoginStr = remember(account.lastLogin) {
                                        try { dateFormat.format(Date(account.lastLogin)) } catch (e: Exception) { "N/A" }
                                    }
                                    val providerStr = if (account.passwordHash == "GOOGLE_AUTH") "Google" else "Email/Password"
                                    Text("Created: $createdStr", fontSize = 10.sp, color = TextGray)
                                    Text("Provider: $providerStr", fontSize = 10.sp, color = LightJusticeBlue, fontWeight = FontWeight.SemiBold)
                                    Text("Last Login: $lastLoginStr", fontSize = 10.sp, color = AccentOrange)
                                }

                                HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)

                                // Status Selection & Delete Action Row for Non-Authority Accounts
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Account Status:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val statuses = listOf("Active", "Disabled", "Blocked", "Inactive")
                                            statuses.forEach { st ->
                                                val isCurrentStatus = account.status.equals(st, ignoreCase = true)
                                                Card(
                                                    modifier = Modifier.clickable {
                                                        if (!isCurrentStatus) {
                                                            viewModel.updateUserStatus(account.email, st)
                                                            Toast.makeText(context, "Status changed to $st", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isCurrentStatus) {
                                                            when (st) {
                                                                "Active" -> SuccessGreen.copy(alpha = 0.2f)
                                                                "Disabled", "Blocked" -> WarningRed.copy(alpha = 0.2f)
                                                                else -> TextGray.copy(alpha = 0.2f)
                                                            }
                                                        } else DarkIndigo
                                                    ),
                                                    border = BorderStroke(1.dp, if (isCurrentStatus) {
                                                        when (st) {
                                                            "Active" -> SuccessGreen
                                                            "Disabled", "Blocked" -> WarningRed
                                                            else -> TextGray
                                                        }
                                                    } else LightSlateBorder),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = st,
                                                        fontSize = 9.sp,
                                                        fontWeight = if (isCurrentStatus) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrentStatus) {
                                                            when (st) {
                                                                "Active" -> SuccessGreen
                                                                "Disabled", "Blocked" -> WarningRed
                                                                else -> TextGray
                                                            }
                                                        } else TextGray,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Delete User Action Button
                                    val isRootAdminTarget = account.email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) || account.email.equals("srisakthi1357@gmail.com", ignoreCase = true)
                                    if (!isRootAdminTarget && account.email != viewModel.userProfile.value.email) {
                                        OutlinedButton(
                                            onClick = { deletingAccount = account },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                            border = BorderStroke(1.dp, WarningRed),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("delete_user_${account.email}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete User", modifier = Modifier.size(14.dp), tint = WarningRed)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Official Government Staff Proof Viewer Dialog
    viewProofAccount?.let { account ->
        FullScreenProofViewerDialog(
            account = account,
            onDismiss = { viewProofAccount = null },
            onActivate = {
                viewModel.activateAuthorityAccount(account.email) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDisable = {
                viewModel.disableAuthorityAccount(account.email) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Confirm Delete dialog
    deletingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("Delete User Account?", fontWeight = FontWeight.Bold, color = WarningRed) },
            text = {
                Text(
                    text = "Are you sure you want to delete this user?\n\nName: ${account.name}\nEmail: ${account.email}\nRole: ${account.role}\n\nThis will permanently purge the user document from Firestore and local database.",
                    fontSize = 12.sp,
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(account.email)
                        deletingAccount = null
                        Toast.makeText(context, "User ${account.name} successfully deleted.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) {
                    Text("Delete User", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingAccount = null }) {
                    Text("Cancel")
                }
            },
            containerColor = CardBackground,
            titleContentColor = WarningRed
        )
    }
}

@Composable
fun OfficialPortalSection(
    url: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawUrl = url?.trim() ?: ""

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Official Portal:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextGray
            )

            if (rawUrl.isNotBlank()) {
                Surface(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Official Portal Link", rawUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "✓ Link copied to clipboard.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to copy link.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = AccentOrange.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("copy_official_portal_link_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = AccentOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Copy Link",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }
        }

        if (rawUrl.isBlank()) {
            Text(
                text = "Not Available",
                fontSize = 11.sp,
                color = TextGray,
                fontStyle = FontStyle.Italic
            )
        } else {
            val formattedUrl = remember(rawUrl) {
                if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
            }

            Text(
                text = rawUrl,
                fontSize = 11.sp,
                color = JusticeBlue,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            if (!Patterns.WEB_URL.matcher(formattedUrl).matches() && !formattedUrl.contains(".")) {
                                Toast.makeText(context, "Invalid website link.", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Unable to open the official portal.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open the official portal.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .testTag("official_portal_hyperlink"),
                softWrap = true
            )
        }
    }
}

@Composable
fun LawDetailsDialog(law: LawRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, AccentOrange)
                    ) {
                        Text(
                            text = law.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (law.status.equals("active", ignoreCase = true)) SuccessGreen.copy(alpha = 0.2f) else WarningRed.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, if (law.status.equals("active", ignoreCase = true)) SuccessGreen else WarningRed)
                    ) {
                        Text(
                            text = law.status.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (law.status.equals("active", ignoreCase = true)) SuccessGreen else WarningRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(law.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                Text("Law ID: ${law.lawId}", fontSize = 10.sp, color = TextGray)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (law.reference.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = LightBlueHighlight)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Text("Citation: ${law.reference}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                        }
                    }
                }

                Text("Description & Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Text(law.description, fontSize = 12.sp, color = TextDarkSlate)

                if (law.content.isNotBlank() && law.content != law.description) {
                    Text("Legal Provisions & Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Text(law.content, fontSize = 12.sp, color = TextDarkSlate)
                }

                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                Text("Regulatory Information", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Authority:", fontSize = 11.sp, color = TextGray)
                    Text(law.officialAuthority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                }

                OfficialPortalSection(url = law.officialSourceUrl)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Created By:", fontSize = 11.sp, color = TextGray)
                    Text("${law.createdBy} (${TimeUtils.formatDate(law.createdAt)})", fontSize = 11.sp, color = TextDarkSlate)
                }
                if (law.updatedAt > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Last Updated:", fontSize = 11.sp, color = TextGray)
                        Text("${law.lastUpdatedBy} (${TimeUtils.formatDate(law.updatedAt)})", fontSize = 11.sp, color = TextDarkSlate)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CardBackground
    )
}

@Composable
fun AdminLawsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val allLaws by viewModel.allLaws.collectAsState()
    val isLawsLoading by viewModel.isLawsLoading.collectAsState()
    
    var showAddForm by remember { mutableStateOf(false) }
    var editingLaw by remember { mutableStateOf<LawRecord?>(null) }
    var viewingLaw by remember { mutableStateOf<LawRecord?>(null) }
    var lawToDelete by remember { mutableStateOf<LawRecord?>(null) }
    
    // Search & Filter State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") }

    // Form Inputs for Inject Law
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Civil Rights") }
    var description by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("The Constitution of India") }
    var officialAuthority by remember { mutableStateOf("Ministry of Law and Justice") }
    var officialSourceUrl by remember { mutableStateOf("https://legislative.gov.in") }
    var isPublishing by remember { mutableStateOf(false) }
    var currentLawId by remember { mutableStateOf("") }

    val categories = listOf("All", "Civil Rights", "Criminal Law", "Labor Laws", "Cybercrime", "Traffic", "General")

    val filteredLaws = remember(allLaws, searchQuery, selectedCategory, selectedStatus) {
        allLaws.filter { law ->
            val matchesCategory = selectedCategory == "All" || law.category.equals(selectedCategory, ignoreCase = true)
            val matchesStatus = selectedStatus == "All" || law.status.equals(selectedStatus, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    law.title.contains(searchQuery, ignoreCase = true) ||
                    law.lawId.contains(searchQuery, ignoreCase = true) ||
                    law.category.contains(searchQuery, ignoreCase = true) ||
                    law.description.contains(searchQuery, ignoreCase = true) ||
                    law.reference.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesStatus && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Legal Dataset Central Authority", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Text("Centralized source of truth synced in real-time to all portals", fontSize = 11.sp, color = TextGray)
            }
            
            Button(
                onClick = { showAddForm = !showAddForm },
                colors = ButtonDefaults.buttonColors(containerColor = if (showAddForm) WarningRed else AccentOrange),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("admin_add_law_btn")
            ) {
                Text(if (showAddForm) "Close" else "+ Inject Law", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showAddForm) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Inject Law to Central Database", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Law Title *") },
                    placeholder = { Text("e.g. Protection of Women from Domestic Violence Act") },
                    modifier = Modifier.fillMaxWidth().testTag("add_law_title_input")
                )

                Text("Category *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Civil Rights", "Criminal Law", "Labor Laws", "Cybercrime").forEach { cat ->
                        val active = category == cat
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { category = cat },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) AccentOrange.copy(alpha = 0.2f) else CardBackground
                            ),
                            border = BorderStroke(1.dp, if (active) AccentOrange else LightSlateBorder)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(cat, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) AccentOrange else TextDarkSlate)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description / Summary *") },
                    modifier = Modifier.fillMaxWidth().height(90.dp).testTag("add_law_description_input"),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Full Legal Text & Provisions") },
                    modifier = Modifier.fillMaxWidth().height(100.dp).testTag("add_law_content_input"),
                    maxLines = 4
                )

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Official Citation / Reference") },
                    placeholder = { Text("e.g. Section 498A IPC / Act No. 43 of 2005") },
                    modifier = Modifier.fillMaxWidth().testTag("add_law_reference_input")
                )

                OutlinedTextField(
                    value = officialAuthority,
                    onValueChange = { officialAuthority = it },
                    label = { Text("Regulatory Authority") },
                    modifier = Modifier.fillMaxWidth().testTag("add_law_authority_input")
                )

                OutlinedTextField(
                    value = officialSourceUrl,
                    onValueChange = { officialSourceUrl = it },
                    label = { Text("Official Source URL") },
                    modifier = Modifier.fillMaxWidth().testTag("add_law_url_input")
                )

                Button(
                    onClick = {
                        val t = title.trim()
                        val c = category.ifBlank { "General" }.trim()
                        val d = description.trim()
                        val cnt = content.ifBlank { d }.trim()
                        val r = reference.trim()
                        val auth = officialAuthority.trim()
                        val url = officialSourceUrl.trim()

                        if (t.isBlank() || c.isBlank() || d.isBlank() || cnt.isBlank() || r.isBlank() || auth.isBlank() || url.isBlank()) {
                            Toast.makeText(context, "Please complete all required fields.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // URL Validation
                        val isValidUrl = url.startsWith("http://", ignoreCase = true) ||
                                url.startsWith("https://", ignoreCase = true) ||
                                android.util.Patterns.WEB_URL.matcher(url).matches()
                        if (!isValidUrl) {
                            Toast.makeText(context, "Please enter a valid Official Source URL (e.g. https://...).", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Network connectivity check
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                        val isConnected = try {
                            val activeNet = cm?.activeNetworkInfo
                            activeNet != null && activeNet.isConnected
                        } catch (_: Exception) {
                            true
                        }

                        if (!isConnected) {
                            Toast.makeText(context, "Network connection unavailable. Please reconnect and try again.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (currentLawId.isBlank()) {
                            currentLawId = "LAW-${System.currentTimeMillis()}"
                        }

                        isPublishing = true
                        Log.d("NyayaApp", "[PUBLISH] Publish Law button clicked - initiating addLaw ID '$currentLawId' with title '$t'...")
                        try {
                            viewModel.addLaw(
                                title = t,
                                category = c,
                                description = d,
                                content = cnt,
                                reference = r,
                                status = "active",
                                officialAuthority = auth,
                                officialSourceUrl = url,
                                lawId = currentLawId
                            ) { success, msg ->
                                isPublishing = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    title = ""
                                    description = ""
                                    content = ""
                                    reference = "The Constitution of India"
                                    officialAuthority = "Ministry of Law and Justice"
                                    officialSourceUrl = "https://legislative.gov.in"
                                    currentLawId = ""
                                    showAddForm = false
                                }
                            }
                        } catch (e: Exception) {
                            isPublishing = false
                            Toast.makeText(context, "Publishing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_add_law_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        disabledContainerColor = SuccessGreen.copy(alpha = 0.5f)
                    )
                ) {
                    if (isPublishing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("Publishing to Central Firestore...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Text("Publish Law to Firestore (Single Source)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Search Bar & Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title, ID, category, or citation...", color = TextGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = TextGray)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = LightSlateBorder
                )
            )

            // Category Chips Row (Single row, horizontally scrollable, auto-sized)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentOrange,
                            selectedLabelColor = Color.Black,
                            containerColor = CardBackground,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == cat,
                            borderColor = LightSlateBorder,
                            selectedBorderColor = AccentOrange
                        )
                    )
                }
            }

            if (isLawsLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = AccentOrange)
                        Text("Syncing Legal Database with Firestore...", color = TextGray, fontSize = 12.sp)
                    }
                }
            } else if (filteredLaws.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No legal records found in central database.", color = TextGray, fontSize = 13.sp)
                    }
                }
            } else {
                Text("${filteredLaws.size} Records in Central Collection", fontSize = 11.sp, color = TextGray, modifier = Modifier.padding(bottom = 6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(filteredLaws, key = { it.lawId }) { law ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("law_item_${law.lawId}"),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            border = BorderStroke(1.dp, LightSlateBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(law.category, fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                                        Text("•", fontSize = 10.sp, color = TextGray)
                                        Text(law.lawId, fontSize = 10.sp, color = TextGray)
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (law.status.equals("active", ignoreCase = true)) SuccessGreen.copy(alpha = 0.2f) else WarningRed.copy(alpha = 0.2f)
                                        ),
                                        border = BorderStroke(1.dp, if (law.status.equals("active", ignoreCase = true)) SuccessGreen else WarningRed)
                                    ) {
                                        Text(
                                            text = law.status.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (law.status.equals("active", ignoreCase = true)) SuccessGreen else WarningRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(law.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
                                Text(law.description, fontSize = 11.sp, color = TextGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (law.reference.isNotBlank()) {
                                    Text("Ref: ${law.reference}", fontSize = 10.sp, color = JusticeBlue)
                                }

                                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Updated: ${TimeUtils.formatDate(law.updatedAt)}", fontSize = 9.sp, color = TextGray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(onClick = { viewingLaw = law }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Visibility, contentDescription = "View", tint = TextDarkSlate, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { editingLaw = law }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AccentOrange, modifier = Modifier.size(16.dp))
                                        }
                                        Button(
                                            onClick = {
                                                val nextStatus = if (law.status.equals("active", ignoreCase = true)) "inactive" else "active"
                                                viewModel.updateLawStatus(law.lawId, nextStatus) { _, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (law.status.equals("active", ignoreCase = true)) WarningRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(
                                                text = if (law.status.equals("active", ignoreCase = true)) "Deactivate" else "Activate",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (law.status.equals("active", ignoreCase = true)) WarningRed else SuccessGreen
                                            )
                                        }
                                        IconButton(onClick = { lawToDelete = law }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = WarningRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: View Full Law Details
    viewingLaw?.let { law ->
        LawDetailsDialog(law = law, onDismiss = { viewingLaw = null })
    }

    // Dialog: Edit Law
    editingLaw?.let { law ->
        var editTitle by remember { mutableStateOf(law.title) }
        var editCategory by remember { mutableStateOf(law.category) }
        var editDesc by remember { mutableStateOf(law.description) }
        var editContent by remember { mutableStateOf(law.content) }
        var editRef by remember { mutableStateOf(law.reference) }
        var editAuthority by remember { mutableStateOf(law.officialAuthority) }
        var editUrl by remember { mutableStateOf(law.officialSourceUrl) }
        var editStatus by remember { mutableStateOf(law.status) }

        AlertDialog(
            onDismissRequest = { editingLaw = null },
            title = { Text("Edit Law Document", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editCategory, onValueChange = { editCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(80.dp))
                    OutlinedTextField(value = editContent, onValueChange = { editContent = it }, label = { Text("Full Provisions") }, modifier = Modifier.fillMaxWidth().height(90.dp))
                    OutlinedTextField(value = editRef, onValueChange = { editRef = it }, label = { Text("Citation / Reference") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editAuthority, onValueChange = { editAuthority = it }, label = { Text("Authority") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("Source URL") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateLaw(
                            lawId = law.lawId,
                            title = editTitle,
                            category = editCategory,
                            description = editDesc,
                            content = editContent,
                            reference = editRef,
                            status = editStatus,
                            officialAuthority = editAuthority,
                            officialSourceUrl = editUrl
                        ) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) editingLaw = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingLaw = null }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }

    // Dialog: Delete Law Confirmation
    lawToDelete?.let { law ->
        AlertDialog(
            onDismissRequest = { lawToDelete = null },
            title = { Text("Confirm Delete Law", fontWeight = FontWeight.Bold, color = WarningRed, fontSize = 16.sp) },
            text = { Text("Are you sure you want to delete '${law.title}' (${law.lawId}) from the central Firestore collection? This action is permanent.", fontSize = 12.sp, color = TextDarkSlate) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLaw(law.lawId) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) lawToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) {
                    Text("Delete Law", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { lawToDelete = null }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeedbackScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val userFeedbacks by viewModel.userFeedbacks.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    var selectedFeedbackForReply by remember { mutableStateOf<com.example.db.UserFeedback?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("AI Logs & Feedback Management Center", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Real-time Citizen & Authority Feedback, Ratings and Support Dispatch", fontSize = 11.sp, color = TextGray)

        Spacer(modifier = Modifier.height(14.dp))

        if (userFeedbacks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No feedback logs submitted yet.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(userFeedbacks) { fb ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_feedback_card_${fb.feedbackId}"),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = fb.userName.ifBlank { "Anonymous User" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (fb.role == "Authority") LightJusticeBlue.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = fb.role.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (fb.role == "Authority") LightJusticeBlue else AccentOrange
                                        )
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (fb.status) {
                                            "Replied", "Reviewed" -> SuccessGreen.copy(alpha = 0.2f)
                                            "Resolved" -> LightJusticeBlue.copy(alpha = 0.2f)
                                            else -> WarningRed.copy(alpha = 0.2f)
                                        }
                                    ),
                                    border = BorderStroke(1.dp, when (fb.status) {
                                        "Replied", "Reviewed" -> SuccessGreen
                                        "Resolved" -> LightJusticeBlue
                                        else -> WarningRed
                                    })
                                ) {
                                    Text(
                                        text = fb.status.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (fb.status) {
                                            "Replied", "Reviewed" -> SuccessGreen
                                            "Resolved" -> LightJusticeBlue
                                            else -> WarningRed
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Category: ${fb.category}", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold)
                                StarRatingDisplay(rating = fb.rating)
                            }

                            Text("Subject: ${fb.subject}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                            Text(fb.message, fontSize = 12.sp, color = Color.White)
                            Text("Submitted: ${dateFormat.format(Date(fb.createdAt))}", fontSize = 10.sp, color = TextGray)

                            if (fb.adminReply.isNotBlank()) {
                                HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Admin Response (${fb.adminName} - ${fb.department}):", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    Text(fb.adminReply, fontSize = 11.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { selectedFeedbackForReply = fb },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("reply_feedback_button_${fb.feedbackId}"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Reply, contentDescription = "Reply", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for Admin Reply
    if (selectedFeedbackForReply != null) {
        val fb = selectedFeedbackForReply!!
        var adminName by remember { mutableStateOf("System Administrator") }
        var department by remember { mutableStateOf("Nyaya AI Support") }
        var responseText by remember { mutableStateOf("") }
        var isSending by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedFeedbackForReply = null },
            title = {
                Text("Reply to Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            containerColor = CardBackground,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("User: ${fb.userName.ifBlank { "Anonymous" }} (${fb.role})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                StarRatingDisplay(rating = fb.rating, starSize = 12.dp)
                            }
                            if (fb.email.isNotBlank()) {
                                Text("Email: ${fb.email}", fontSize = 10.sp, color = TextGray)
                            }
                            Text("Subject: ${fb.subject}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Message: ${fb.message}", fontSize = 11.sp, color = TextDarkSlate)
                            Text("Date: ${dateFormat.format(Date(fb.createdAt))}", fontSize = 9.sp, color = TextGray)
                        }
                    }

                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it },
                        label = { Text("Admin Name", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_reply_name_input"),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange, unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange, unfocusedLabelColor = TextGray
                        )
                    )

                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_reply_department_input"),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange, unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange, unfocusedLabelColor = TextGray
                        )
                    )

                    OutlinedTextField(
                        value = responseText,
                        onValueChange = { responseText = it },
                        label = { Text("Reply Message *", fontSize = 11.sp) },
                        placeholder = { Text("Enter your reply message here...", fontSize = 11.sp, color = TextGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("admin_reply_response_input"),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange, unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange, unfocusedLabelColor = TextGray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (responseText.isBlank()) {
                            Toast.makeText(context, "Reply message cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSending = true
                        viewModel.replyToFeedback(
                            feedbackId = fb.feedbackId,
                            adminName = adminName,
                            department = department,
                            replyText = responseText
                        ) {
                            isSending = false
                            Toast.makeText(context, "Reply submitted successfully!", Toast.LENGTH_SHORT).show()
                            selectedFeedbackForReply = null
                        }
                    },
                    modifier = Modifier.testTag("send_reply_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black),
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                    } else {
                        Text("Submit Reply", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedFeedbackForReply = null },
                    modifier = Modifier.testTag("cancel_reply_button")
                ) {
                    Text("Cancel", color = TextGray)
                }
            }
        )
    }
}

// ==========================================
// SCREEN 1: AI ASSISTANT (CHAT + RAG)
// ==========================================
@Composable
fun AssistantScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var textInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Suggestions list for RAG empty state/guided navigation
    val suggestions = listOf(
        I18n.getString("suggestion_1", lang),
        I18n.getString("suggestion_2", lang),
        I18n.getString("suggestion_3", lang),
        I18n.getString("suggestion_4", lang)
    )

    // Auto-scroll to the bottom when a new message is appended
    LaunchedEffect(chatHistory.size, isLoading) {
        if (chatHistory.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
    ) {
        // App header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(JusticeBlue, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = I18n.getString("assistant_title", lang),
                            color = TextDarkSlate,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = I18n.getString("assistant_subtitle", lang),
                            color = JusticeBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                IconButton(
                    onClick = { viewModel.clearChat() },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear Chat",
                        tint = WarningRed
                    )
                }
            }
        }
        HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(chatHistory) { message ->
                ChatBubble(message, viewModel)
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LightSlateBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = JusticeBlue
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Searching knowledge base & generating answer...",
                                    color = TextGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick suggestions bar
        if (chatHistory.size <= 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = I18n.getString("suggestions_title", lang),
                    color = JusticeBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                suggestions.forEach { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isLoading) {
                                if (!isLoading) {
                                    keyboardController?.hide()
                                    viewModel.sendChatMessage(suggestion)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = suggestion,
                                color = TextDarkSlate,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                enabled = !isLoading,
                placeholder = { Text(I18n.getString("assistant_placeholder", lang), color = TextGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextDarkSlate,
                    unfocusedTextColor = TextDarkSlate,
                    focusedContainerColor = DarkIndigo,
                    unfocusedContainerColor = DarkIndigo,
                    focusedBorderColor = JusticeBlue,
                    unfocusedBorderColor = LightSlateBorder
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isLoading && textInput.isNotBlank()) {
                        val messageToSend = textInput
                        textInput = ""
                        keyboardController?.hide()
                        viewModel.sendChatMessage(messageToSend)
                    }
                })
            )
            
            Spacer(modifier = Modifier.width(10.dp))

            FloatingActionButton(
                onClick = {
                    if (!isLoading && textInput.isNotBlank()) {
                        val messageToSend = textInput
                        textInput = ""
                        keyboardController?.hide()
                        viewModel.sendChatMessage(messageToSend)
                    }
                },
                containerColor = if (isLoading) Color.Gray else JusticeBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_message_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val isUser = message.sender == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(LightBlueHighlight)
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = "NyayaAI",
                    tint = JusticeBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) JusticeBlue else CardBackground
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 16.dp
                ),
                border = if (isUser) null else BorderStroke(1.dp, LightSlateBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 2.dp else 1.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = if (isUser) Color.White else TextDarkSlate,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Display verified reference info if loaded via local RAG
                    if (!isUser && message.sourceTitle != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = LightSlateBorder)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = "Verified Local Source",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Source: ${message.sourceTitle}",
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            if (message.sourceUrl != null) {
                                Text(
                                    text = "SOURCE ↗",
                                    color = JusticeBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.sourceUrl))
                                            context.startActivity(intent)
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Trust Meter & Feedback Mechanism for AI Answers
            if (!isUser && message.text != "Error: Could not retrieve a response. Please check your Gemini API Key in the Secrets panel.") {
                val hasSource = message.sourceTitle != null
                val hasUrl = message.sourceUrl != null
                val confidence = when {
                    hasSource && hasUrl -> 94
                    hasSource -> 82
                    message.isWarningNotLocal -> 45
                    else -> 65
                }
                val confidenceLevel = when {
                    confidence >= 90 -> "High"
                    confidence >= 70 -> "Good"
                    confidence >= 50 -> "Moderate"
                    else -> "Low"
                }
                val confidenceColor = when {
                    confidence >= 90 -> SuccessGreen
                    confidence >= 70 -> JusticeBlue
                    confidence >= 50 -> AccentOrange
                    else -> WarningRed
                }
                val blockText = buildString {
                    val filledBlocks = confidence / 10
                    for (i in 1..10) {
                        if (i <= filledBlocks) append("█") else append("░")
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🛡️ AI Trust Meter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextDarkSlate
                            )
                            Text(
                                text = "$confidence%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = confidenceColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = blockText,
                                fontSize = 12.sp,
                                color = confidenceColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$confidenceLevel Confidence",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = confidenceColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                confidence >= 90 -> "Verified using official legal documents & local RAG databases."
                                confidence >= 70 -> "Sourced from local legal guidelines with direct source references."
                                confidence >= 50 -> "Moderate matching score. Double check specific official portals."
                                else -> "General knowledge advice. Sourced outside the local legal database."
                            },
                            fontSize = 10.sp,
                            color = TextGray,
                            lineHeight = 12.sp
                        )
                    }
                }
                
                var feedbackSubmitted by remember { mutableStateOf(false) }
                var rating by remember { mutableStateOf(0) }
                var textFeedback by remember { mutableStateOf("") }
                var showTextField by remember { mutableStateOf(false) }
                
                if (!feedbackSubmitted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.widthIn(max = 300.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Was this helpful?",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDarkSlate
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {
                                            rating = 5
                                            showTextField = true
                                        },
                                        modifier = Modifier.size(24.dp).testTag("thumbs_up_${message.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbUp,
                                            contentDescription = "Helpful",
                                            tint = if (rating >= 4) SuccessGreen else TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            rating = 1
                                            showTextField = true
                                        },
                                        modifier = Modifier.size(24.dp).testTag("thumbs_down_${message.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbDown,
                                            contentDescription = "Not Helpful",
                                            tint = if (rating in 1..2) WarningRed else TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (star in 1..5) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rate $star Stars",
                                            tint = if (star <= rating) Color(0xFFF1C40F) else Color.LightGray,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    rating = star
                                                    showTextField = true
                                                }
                                                .testTag("star_${star}_${message.id}")
                                        )
                                    }
                                }
                            }
                            
                            if (showTextField) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = textFeedback,
                                    onValueChange = { textFeedback = it },
                                    placeholder = { Text("Optional text feedback...", fontSize = 11.sp, color = TextGray) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("feedback_text_${message.id}"),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JusticeBlue,
                                        unfocusedBorderColor = LightSlateBorder,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.submitFeedback(
                                            query = message.sourceTitle ?: "General Inquiry",
                                            response = message.text,
                                            isHelpful = rating >= 3,
                                            stars = rating,
                                            text = textFeedback.ifBlank { null }
                                        )
                                        feedbackSubmitted = true
                                        Toast.makeText(context, "Feedback submitted! +5 points added to profile.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = JusticeBlue),
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .height(26.dp)
                                        .testTag("submit_feedback_${message.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Submit", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .background(Color(0xFFE6F4EA), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = SuccessGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thank you for rating this response!",
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Subtitle source notice indicator
            Row(
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                    .widthIn(max = 300.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isUser) "You" else "NyayaAI",
                    color = TextGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}




// ==========================================
// LAWS DIRECTORY CONTENT
// ==========================================
@Composable
fun LawsDirectoryContent(
    activeLaws: List<LawRecord>,
    isLawsLoading: Boolean,
    lawsError: String? = null,
    onRetry: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onLawClick: (LawRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember(activeLaws) {
        val baseCategories = linkedSetOf(
            "All",
            "Citizen Rights",
            "Employee Rights",
            "Criminal Law",
            "Civil Rights",
            "Labor Laws",
            "Cybercrime Law",
            "Traffic",
            "General",
            "Property",
            "Tax Law",
            "Environmental",
            "Consumer Protection",
            "Women's Rights"
        )
        activeLaws.map { it.category }.filter { it.isNotBlank() }.forEach { baseCategories.add(it) }
        baseCategories.toList()
    }

    val filteredLaws = remember(activeLaws, searchQuery, selectedCategory) {
        activeLaws.filter { law ->
            val matchesCategory = selectedCategory == "All" ||
                    law.category.equals(selectedCategory, ignoreCase = true) ||
                    (selectedCategory == "Cybercrime Law" && law.category.contains("Cyber", ignoreCase = true)) ||
                    (selectedCategory == "Labor Laws" && law.category.contains("Employee", ignoreCase = true))
            val matchesSearch = searchQuery.isBlank() ||
                    law.title.contains(searchQuery, ignoreCase = true) ||
                    law.category.contains(searchQuery, ignoreCase = true) ||
                    law.description.contains(searchQuery, ignoreCase = true) ||
                    law.content.contains(searchQuery, ignoreCase = true) ||
                    law.reference.contains(searchQuery, ignoreCase = true) ||
                    law.officialAuthority.contains(searchQuery, ignoreCase = true) ||
                    law.keywords.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search active laws, citations, sections, acts...", color = TextGray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextGray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .testTag("directory_search_bar"),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = LightSlateBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Category Filter Chips (Single row, horizontally scrollable)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(cat) },
                    label = {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange,
                        selectedLabelColor = Color.Black,
                        containerColor = CardBackground,
                        labelColor = TextGray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == cat,
                        borderColor = LightSlateBorder,
                        selectedBorderColor = AccentOrange
                    )
                )
            }
        }

        // Header status bar if laws are present
        if (activeLaws.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredLaws.size} of ${activeLaws.size} Laws Available",
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )
                if (isLawsLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Text("Syncing...", fontSize = 10.sp, color = AccentOrange)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onRetry?.invoke() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = TextGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Sync", fontSize = 11.sp, color = TextGray)
                    }
                }
            }
        }

        // UI Body based on state
        if (isLawsLoading && activeLaws.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = AccentOrange)
                    Text("Fetching Central Legal Collection...", color = TextGray, fontSize = 12.sp)
                }
            }
        } else if (lawsError != null && activeLaws.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = WarningRed, modifier = Modifier.size(48.dp))
                    Text("Unable to load legal collection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(lawsError, color = TextGray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = { onRetry?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("laws_retry_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else if (filteredLaws.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCategory != "All") "No laws found matching your filter criteria." else "No active laws found.",
                        color = TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedCategory != "All") {
                        OutlinedButton(
                            onClick = {
                                onSearchQueryChange("")
                                onCategorySelect("All")
                            },
                            border = BorderStroke(1.dp, AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear Filters", color = AccentOrange, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { onRetry?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Refresh Legal Catalog", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredLaws, key = { it.lawId }) { law ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLawClick(law) }
                            .testTag("law_card_${law.lawId}"),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = law.category,
                                    fontSize = 11.sp,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, SuccessGreen),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = law.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextDarkSlate
                            )

                            Text(
                                text = law.description,
                                fontSize = 11.sp,
                                color = TextGray,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (law.reference.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = JusticeBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Citation: ${law.reference}",
                                        fontSize = 10.sp,
                                        color = JusticeBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (law.officialAuthority.isNotBlank()) {
                                Text(
                                    text = "Authority: ${law.officialAuthority}",
                                    fontSize = 9.sp,
                                    color = TextGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorityLawsScreen(viewModel: NyayaViewModel) {
    val activeLaws by viewModel.activeLaws.collectAsState()
    val isLawsLoading by viewModel.isLawsLoading.collectAsState()
    val lawsError by viewModel.lawsError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var viewingLaw by remember { mutableStateOf<LawRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("Central Legal Framework", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Verified law records synced in real-time from Admin Central Database", fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(bottom = 12.dp))

        LawsDirectoryContent(
            activeLaws = activeLaws,
            isLawsLoading = isLawsLoading,
            lawsError = lawsError,
            onRetry = { viewModel.refreshLaws(context) },
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            selectedCategory = selectedCategory,
            onCategorySelect = { selectedCategory = it },
            onLawClick = { viewingLaw = it }
        )
    }

    viewingLaw?.let { law ->
        LawDetailsDialog(law = law, onDismiss = { viewingLaw = null })
    }
}

// ==========================================
// SCREEN 3: BROWSE LAWS (CENTRAL FIRESTORE DATASET)
// ==========================================
@Composable
fun BrowseLawsScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val activeLaws by viewModel.activeLaws.collectAsState()
    val isLawsLoading by viewModel.isLawsLoading.collectAsState()
    val lawsError by viewModel.lawsError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allInquiries by viewModel.allCitizenRequests.collectAsState()
    val isInquiriesLoading by viewModel.isInquiriesLoading.collectAsState()
    val isSubmittingInquiry by viewModel.isSubmittingInquiry.collectAsState()
    val inquiryError by viewModel.inquiryError.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) } // 0: Laws Directory, 1: My Legal Inquiries
    var viewingLaw by remember { mutableStateOf<LawRecord?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showNewInquiryModal by remember { mutableStateOf(false) }
    var inquirySubject by remember { mutableStateOf("") }
    var inquiryDetails by remember { mutableStateOf("") }

    val currentAuthUid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val myInquiries = remember(allInquiries, userProfile, currentAuthUid) {
        allInquiries.filter { req ->
            (currentAuthUid.isNotEmpty() && req.citizenId == currentAuthUid) ||
            (userProfile.id.isNotEmpty() && req.citizenId == userProfile.id) ||
            (userProfile.email.isNotEmpty() && req.citizenEmail.equals(userProfile.email, ignoreCase = true)) ||
            (userProfile.email.isBlank() && currentAuthUid.isBlank())
        }.sortedByDescending { it.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text(
            text = I18n.getString("laws_title", lang),
            color = TextDarkSlate,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Browse verified legal framework synced in real-time from Central Authority",
            color = TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Top Switcher Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("⚖️ Legal Directory", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentOrange,
                    selectedLabelColor = Color.Black,
                    containerColor = CardBackground,
                    labelColor = TextGray
                )
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️ My Legal Inquiries", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (myInquiries.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${myInquiries.size}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentOrange,
                    selectedLabelColor = Color.Black,
                    containerColor = CardBackground,
                    labelColor = TextGray
                )
            )
        }

        if (selectedTab == 0) {
            LawsDirectoryContent(
                activeLaws = activeLaws,
                isLawsLoading = isLawsLoading,
                lawsError = lawsError,
                onRetry = { viewModel.refreshLaws(context) },
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                onLawClick = { viewingLaw = it }
            )
        } else {
            // MY LEGAL INQUIRIES TAB
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            inquirySubject = ""
                            inquiryDetails = ""
                            showNewInquiryModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_new_inquiry_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit New Legal Inquiry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = { viewModel.refreshInquiries() },
                        modifier = Modifier.testTag("refresh_inquiries_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Inquiries", tint = AccentOrange)
                    }
                }

                if (isInquiriesLoading && myInquiries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = AccentOrange, strokeWidth = 2.5.dp)
                            Text("Loading your legal inquiries...", color = TextGray, fontSize = 12.sp)
                        }
                    }
                } else if (inquiryError != null && myInquiries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningRed, modifier = Modifier.size(36.dp))
                            Text("Unable to load inquiries", color = TextDarkSlate, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(inquiryError ?: "Network error", color = TextGray, fontSize = 11.sp)
                            Button(
                                onClick = { viewModel.refreshInquiries() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (myInquiries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No legal inquiries submitted yet.", color = TextDarkSlate, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Submit an inquiry to receive official responses from authority officers.", color = TextGray, fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(myInquiries, key = { it.id }) { req ->
                            CitizenInquiryCard(req = req)
                        }
                    }
                }
            }
        }
    }

    // Modal to Submit New Legal Inquiry
    if (showNewInquiryModal) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingInquiry) {
                    showNewInquiryModal = false
                }
            },
            title = {
                Text("Submit Legal Inquiry", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ask a legal question directly to verified government authority officers.", fontSize = 11.sp, color = TextGray)

                    OutlinedTextField(
                        value = inquirySubject,
                        onValueChange = { inquirySubject = it },
                        placeholder = { Text("e.g. RTI escalation, Property dispute...", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Subject / Title *", fontSize = 11.sp) },
                        singleLine = true,
                        enabled = !isSubmittingInquiry,
                        modifier = Modifier.fillMaxWidth().testTag("inquiry_subject_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = inquiryDetails,
                        onValueChange = { inquiryDetails = it },
                        placeholder = { Text("Detail your question or issue clearly...", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Inquiry Details *", fontSize = 11.sp) },
                        enabled = !isSubmittingInquiry,
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("inquiry_details_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inquirySubject.trim().isBlank() || inquiryDetails.trim().isBlank()) {
                            Toast.makeText(context, "Subject and Inquiry details are required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.submitCitizenRequest(inquirySubject, inquiryDetails) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                showNewInquiryModal = false
                                inquirySubject = ""
                                inquiryDetails = ""
                            }
                        }
                    },
                    enabled = !isSubmittingInquiry,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.testTag("submit_inquiry_modal_btn")
                ) {
                    if (isSubmittingInquiry) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submitting...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Submit Inquiry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showNewInquiryModal = false },
                    enabled = !isSubmittingInquiry
                ) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }

    viewingLaw?.let { law ->
        LawDetailsDialog(law = law, onDismiss = { viewingLaw = null })
    }
}

/**
 * CITIZEN INQUIRY CARD DISPLAYING REAL-TIME OFFICIAL RESPONSE
 */
@Composable
fun CitizenInquiryCard(req: CitizenRequest) {
    val isAnswered = req.status.equals("Answered", ignoreCase = true) || req.reply.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAnswered) SuccessGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, if (isAnswered) SuccessGreen else AccentOrange)
                ) {
                    Text(
                        text = if (isAnswered) "Official Response Received" else "Pending Official Response",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAnswered) SuccessGreen else AccentOrange,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text("Inquiry ID: ${req.id} • ${TimeUtils.formatDate(req.timestamp)}", fontSize = 9.sp, color = TextGray)
            }

            Text(req.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
            Text(req.details, fontSize = 11.sp, color = TextGray)

            HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

            if (isAnswered && req.reply.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightBlueHighlight)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("⚖️ Official Legal Response:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            if (req.edited) {
                                Text("(Edited)", fontSize = 9.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(req.reply, fontSize = 12.sp, color = TextDarkSlate, fontWeight = FontWeight.Normal)

                        HorizontalDivider(color = LightSlateBorder.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        if (req.officerName.isNotEmpty()) {
                            Text(
                                "Responded by: ${req.officerName} (${req.officerDesignation})",
                                fontSize = 10.sp,
                                color = TextDarkSlate,
                                fontWeight = FontWeight.Bold
                            )
                            if (req.officerDepartment.isNotEmpty()) {
                                Text("Department: ${req.officerDepartment}", fontSize = 9.sp, color = TextGray)
                            }
                        }
                        if (req.respondedAt > 0) {
                            Text(
                                "Date: ${TimeUtils.formatDate(req.respondedAt)} ${TimeUtils.formatTime(req.respondedAt)}",
                                fontSize = 9.sp,
                                color = TextGray
                            )
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = AccentOrange, strokeWidth = 1.5.dp)
                    Text(
                        text = if (req.edited) "Official response updated by Authority. Pending review." else "Waiting for authority response • Inquiry is registered",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }
            }
        }
    }
}

@Composable
fun LawTopicCard(
    topic: LegalTopic,
    isExpanded: Boolean,
    onExpandClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            .testTag("law_card_${topic.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LightSlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Category chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (topic.category) {
                                    "Women's Rights" -> Color(0xFFFCE7F3)
                                    "Employee Rights" -> Color(0xFFDCFCE7)
                                    "Consumer Rights" -> Color(0xFFFEF3C7)
                                    "Cybercrime" -> Color(0xFFF3E8FF)
                                    else -> LightBlueHighlight
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = topic.category,
                            color = when (topic.category) {
                                "Women's Rights" -> Color(0xFFDB2777)
                                "Employee Rights" -> Color(0xFF15803D)
                                "Consumer Rights" -> Color(0xFFB45309)
                                "Cybercrime" -> Color(0xFF7E22CE)
                                else -> JusticeBlue
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = topic.title,
                        color = TextDarkSlate,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = TextDarkSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = topic.summary,
                color = TextGray,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Collapsible details panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    HorizontalDivider(color = LightSlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (topic.next_steps.isNotEmpty()) {
                        Text(
                            text = "👉 Next Steps for Citizens:",
                            color = AccentOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        topic.next_steps.forEachIndexed { idx, step ->
                            Text(
                                text = "${idx + 1}. $step",
                                color = TextDarkSlate,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "🏢 Competent Authority:",
                        color = JusticeBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = topic.official_authority,
                        color = TextDarkSlate,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "📜 Official Reference / Act:",
                        color = JusticeBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = topic.official_source,
                        color = TextDarkSlate,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(topic.official_source_url))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JusticeBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "Open in New Tab",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Launch Official Gov Portal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: CITIZEN FORUM (GAMIFICATION)
// ==========================================
@Composable
fun ForumScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val forumPosts by viewModel.forumPosts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    
    var isCreatingPost by remember { mutableStateOf(false) }
    var postTitle by remember { mutableStateOf("") }
    var postContent by remember { mutableStateOf("") }
    var postType by remember { mutableStateOf("Discussion") } // "Question", "Resource", "Discussion"
    var expandedPostId by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val bookmarkedPostIds = remember { mutableStateListOf<String>() }

    val filteredPosts = forumPosts.filter { post ->
        val matchesCategory = (selectedCategoryFilter == "All") || post.postType.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesSearch = searchQuery.isBlank() ||
                post.title.contains(searchQuery, ignoreCase = true) ||
                post.content.contains(searchQuery, ignoreCase = true) ||
                post.authorName.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        // Forum Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = I18n.getString("forum_title", lang),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = I18n.getString("forum_subtitle", lang),
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
            
            // Add post button
            Button(
                onClick = { isCreatingPost = !isCreatingPost },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCreatingPost) WarningRed else JusticeBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("toggle_create_post_button"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isCreatingPost) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "New Post",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCreatingPost) I18n.getString("cancel_button", lang) else I18n.getString("create_post", lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search & Category Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search community discussions...", color = TextGray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextGray)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("forum_search_input"),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = JusticeBlue,
                unfocusedBorderColor = LightSlateBorder,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedPlaceholderColor = TextGray,
                unfocusedPlaceholderColor = TextGray
            ),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Discussion", "Question", "Resource").forEach { filter ->
                val isSel = selectedCategoryFilter == filter
                FilterChip(
                    selected = isSel,
                    onClick = { selectedCategoryFilter = filter },
                    label = { Text(filter, fontSize = 11.sp, color = if (isSel) Color.White else TextGray) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JusticeBlue,
                        containerColor = CardBackground
                    ),
                    border = BorderStroke(1.dp, if (isSel) JusticeBlue else LightSlateBorder)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Create Post Form Panel
        AnimatedVisibility(
            visible = isCreatingPost,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("create_post_form"),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, JusticeBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Share with the Community", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = postTitle,
                        onValueChange = { postTitle = it },
                        placeholder = { Text("Enter a descriptive title...", color = TextGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("post_title_input"),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JusticeBlue,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedPlaceholderColor = TextGray,
                            unfocusedPlaceholderColor = TextGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = postContent,
                        onValueChange = { postContent = it },
                        placeholder = { Text("What legal topics or advice would you like to share or ask?", color = TextGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("post_content_input"),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JusticeBlue,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedPlaceholderColor = TextGray,
                            unfocusedPlaceholderColor = TextGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Type Chips selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val types = listOf("Discussion" to "💬", "Question" to "❓", "Resource" to "🛡️")
                        types.forEach { (type, emoji) ->
                            val isSelected = postType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { postType = type },
                                label = { Text("$emoji $type", fontSize = 10.sp, color = if (isSelected) Color.White else TextGray) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JusticeBlue,
                                    containerColor = DarkIndigo
                                ),
                                border = BorderStroke(1.dp, if (isSelected) JusticeBlue else LightSlateBorder),
                                modifier = Modifier.testTag("type_chip_$type")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (postTitle.isBlank() || postContent.isBlank()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.createPost(postTitle, postContent, postType)
                            val earned = if (postType == "Resource") 15 else 5
                            Toast.makeText(context, "Post created successfully! Earned +$earned points.", Toast.LENGTH_LONG).show()
                            
                            // Reset state
                            postTitle = ""
                            postContent = ""
                            postType = "Discussion"
                            isCreatingPost = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        modifier = Modifier.fillMaxWidth().testTag("submit_post_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Publish Post & Earn Points", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Posts List
        if (filteredPosts.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Chat, null, tint = TextGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No discussions found for '$searchQuery'" else "Be the first to start a conversation!",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPosts) { post ->
                    val isBookmarked = bookmarkedPostIds.contains(post.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forum_post_${post.id}"),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Card Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(JusticeBlue, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = post.authorName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = post.authorName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = post.authorRole,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // Category Badge
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (post.postType) {
                                            "Resource" -> Color(0xFF1E3A2B)
                                            "Question" -> Color(0xFF3E2723)
                                            else -> Color(0xFF1A237E)
                                        }
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = post.postType.uppercase(),
                                        color = when (post.postType) {
                                            "Resource" -> Color(0xFF81C784)
                                            "Question" -> Color(0xFFFFB74D)
                                            else -> Color(0xFF64B5F6)
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Post Title & Content
                            Text(text = post.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = post.content, color = Color(0xFFCBD5E1), fontSize = 13.sp, lineHeight = 18.sp)

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = LightSlateBorder)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Interactive bottom row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Like / Upvote Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { viewModel.toggleLikePost(post) }
                                            .padding(4.dp)
                                            .testTag("like_post_${post.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (post.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Upvote",
                                            tint = if (post.isLikedByMe) WarningRed else TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${post.upvotes} Upvotes",
                                            fontSize = 11.sp,
                                            color = if (post.isLikedByMe) WarningRed else TextGray
                                        )
                                    }

                                    // Comment Button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                expandedPostId = if (expandedPostId == post.id) null else post.id
                                            }
                                            .padding(4.dp)
                                            .testTag("comments_toggle_${post.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Comment,
                                            contentDescription = "Replies",
                                            tint = JusticeBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reply (+10 pts)", fontSize = 11.sp, color = JusticeBlue, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Bookmark
                                    IconButton(
                                        onClick = {
                                            if (isBookmarked) {
                                                bookmarkedPostIds.remove(post.id)
                                                Toast.makeText(context, "Removed from bookmarks", Toast.LENGTH_SHORT).show()
                                            } else {
                                                bookmarkedPostIds.add(post.id)
                                                Toast.makeText(context, "Post bookmarked!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) AccentOrange else TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Share
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Community link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Inline comments section
                            if (expandedPostId == post.id) {
                                val comments by viewModel.getComments(post.id).collectAsState(initial = emptyList())
                                var commentText by remember { mutableStateOf("") }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                                    border = BorderStroke(1.dp, LightSlateBorder)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Discussion Replies", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        comments.forEach { comment ->
                                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AccentOrange)
                                                    Text("•", fontSize = 10.sp, color = TextGray)
                                                    Text(comment.authorRole, fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                                }
                                                Text(comment.content, fontSize = 12.sp, color = Color.White)
                                                HorizontalDivider(color = LightSlateBorder, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Add Reply field
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = commentText,
                                                onValueChange = { commentText = it },
                                                placeholder = { Text("Write a supportive response...", fontSize = 11.sp, color = TextGray) },
                                                modifier = Modifier.weight(1f).height(44.dp).testTag("comment_input_${post.id}"),
                                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = JusticeBlue,
                                                    unfocusedBorderColor = LightSlateBorder,
                                                    focusedContainerColor = CardBackground,
                                                    unfocusedContainerColor = CardBackground,
                                                    focusedPlaceholderColor = TextGray,
                                                    unfocusedPlaceholderColor = TextGray
                                                ),
                                                singleLine = true
                                            )
                                            Button(
                                                onClick = {
                                                    if (commentText.isBlank()) return@Button
                                                    viewModel.addComment(post.id, commentText)
                                                    commentText = ""
                                                    Toast.makeText(context, "Response added! Earned +10 community points.", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.height(36.dp).testTag("comment_submit_${post.id}"),
                                                colors = ButtonDefaults.buttonColors(containerColor = JusticeBlue),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                            ) {
                                                Text("Send", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: MY PROFILE & GAMIFICATION BADGES
// ==========================================
@Composable
fun ProfileScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    
    var isEditingName by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(userProfile.name) }
    var editEmail by remember { mutableStateOf(userProfile.email) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        editName = userProfile.name
        editEmail = userProfile.email
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = I18n.getString("profile_title", lang),
            color = TextDarkSlate,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = I18n.getString("profile_subtitle", lang),
            color = TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Language Setting Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_language_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(LightJusticeBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Language",
                            tint = LightJusticeBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = I18n.getString("language_setting", lang),
                            color = TextDarkSlate,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == "Tamil") "தமிழ் (Tamil)" else "English",
                            color = AccentOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = { showLanguageDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkIndigo),
                    border = BorderStroke(1.dp, AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("change_language_button")
                ) {
                    Text(
                        text = I18n.getString("change_language", lang),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                containerColor = CardBackground,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = AccentOrange)
                        Text(
                            text = I18n.getString("select_language_title", lang),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        val languages = listOf("English" to "English", "Tamil" to "தமிழ்")
                        languages.forEach { (code, label) ->
                            val isSelected = (lang == code)
                            Card(
                                onClick = {
                                    viewModel.selectLanguage(code)
                                    showLanguageDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) AccentOrange.copy(alpha = 0.15f) else Color(0xFF0F172A)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentOrange else LightSlateBorder
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lang_option_$code")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.selectLanguage(code)
                                                showLanguageDialog = false
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = AccentOrange,
                                                unselectedColor = TextGray
                                            )
                                        )
                                        Text(
                                            text = label,
                                            color = if (isSelected) AccentOrange else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AccentOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(I18n.getString("cancel_button", lang), color = TextGray)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Detail Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_identity_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Circle Avatar representation
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(JusticeBlue, Color(0xFF5C6BC0))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userProfile.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isEditingName) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(I18n.getString("full_name", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                        textStyle = TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text(I18n.getString("email_address", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_email_input"),
                        textStyle = TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { isEditingName = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Text(I18n.getString("cancel_button", lang), fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.updateProfileName(editName, editEmail)
                                isEditingName = false
                                Toast.makeText(context, "Identity updated successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text(I18n.getString("save_button", lang), fontSize = 11.sp, color = Color.White)
                        }
                    }
                } else {
                    Text(text = userProfile.name, fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 18.sp)
                    Text(text = userProfile.email, color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Account Metadata Details
                    val currentUserAccount by viewModel.currentUserAccount.collectAsState()
                    val authProvider = if (currentUserAccount?.passwordHash == "GOOGLE_AUTH") "Google" else "Email & Password"
                    val accountStatus = currentUserAccount?.status?.ifEmpty { "Active" } ?: "Active"
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkIndigo.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, LightSlateBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(I18n.getString("role_label", lang), fontSize = 11.sp, color = TextGray)
                            Text(userProfile.role.ifEmpty { "Citizen" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(I18n.getString("account_status_label", lang), fontSize = 11.sp, color = TextGray)
                            Text(accountStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(I18n.getString("auth_provider_label", lang), fontSize = 11.sp, color = TextGray)
                            Text(authProvider, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LightJusticeBlue)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedButton(
                        onClick = { isEditingName = true },
                        modifier = Modifier.height(32.dp).testTag("edit_profile_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                        border = BorderStroke(1.dp, AccentOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(I18n.getString("edit_identity_btn", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gamification / Rewards Progress Card
        val displayPoints = userProfile.points.coerceAtLeast(300)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gamification_progress_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "🪙 " + I18n.getString("community_points", lang) + " & Level", fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Total Points:", fontSize = 12.sp, color = TextGray)
                    Text(
                        text = "$displayPoints pts",
                        fontWeight = FontWeight.Black,
                        color = JusticeBlue,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress to next milestone
                val nextMilestone = when {
                    displayPoints < 50 -> "Community Helper" to 50
                    displayPoints < 100 -> "Top Contributor" to 100
                    displayPoints < 200 -> "Legal Expert" to 200
                    else -> "Supreme Nyaya Guide" to 500
                }
                
                val progressFraction = (displayPoints.toFloat() / nextMilestone.second).coerceIn(0f, 1f)
                val pointsNeeded = (nextMilestone.second - displayPoints).coerceAtLeast(0)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Next Badge: ${nextMilestone.first}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDarkSlate
                    )
                    Text(
                        text = "$pointsNeeded pts left",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progressFraction,
                    color = JusticeBlue,
                    trackColor = Color(0xFFF1F3F5),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Badge Medals Grid Area
        Text(
            text = I18n.getString("medals_title", lang),
            fontWeight = FontWeight.Bold,
            color = TextDarkSlate,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val activeBadges = userProfile.badges.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val allMedals = listOf(
            Triple("Community Helper", "Unlocks at 50 points. Earned by giving supportive forum and AI rating answers.", "🤝"),
            Triple("Top Contributor", "Unlocks at 100 points. Earned by actively sharing vital resources.", "🏆"),
            Triple("Legal Expert", "Unlocks at 200 points. Earned by maintaining a high reputation.", "⚖️")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allMedals.forEach { (name, desc, emoji) ->
                val isUnlocked = activeBadges.contains(name)
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isUnlocked) CardBackground else CardBackground.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, if (isUnlocked) SuccessGreen else LightSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (isUnlocked) Color(0xFFE8F5E9) else Color(0xFFECEFF1),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isUnlocked) emoji else "🔒",
                                fontSize = 20.sp
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isUnlocked) TextDarkSlate else Color.Gray
                                )
                                if (isUnlocked) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "UNLOCKED",
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = desc,
                                fontSize = 11.sp,
                                color = TextGray,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Pre-configured Emergency Contacts Manager
        EmergencyContactsManagerCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                viewModel.logout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("logout_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53935)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Logout",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = I18n.getString("logout_btn", lang),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------- EMERGENCY SOS & CONTACTS COMPONENTS ----------------

@Composable
fun EmergencyContactsManagerCard(
    viewModel: NyayaViewModel,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.emergencyContacts.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val safeContacts = contacts.filter { 
        it.id != "em_1" && 
        !it.name.contains("Guardian", ignoreCase = true) && 
        !it.relationship.contains("Guardian", ignoreCase = true) 
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_contacts_manager"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, WarningRed.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(WarningRed.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚨", fontSize = 16.sp)
                    }
                    Column {
                        Text(
                            text = I18n.getString("emergency_contacts_title", lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkSlate
                        )
                        Text(
                            text = "${safeContacts.size} " + I18n.getString("emergency_contacts_subtitle", lang),
                            fontSize = 10.sp,
                            color = TextGray
                        )
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_emergency_contact_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(I18n.getString("add_button", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (safeContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = I18n.getString("no_emergency_contacts", lang),
                        fontSize = 11.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    safeContacts.forEach { contact ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, if (contact.isPrimary) AccentOrange else LightSlateBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (contact.isPrimary) AccentOrange.copy(alpha = 0.2f) else JusticeBlue.copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (contact.isPrimary) "⭐" else "👤",
                                            fontSize = 16.sp
                                        )
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = contact.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.2f)),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = contact.relationship,
                                                    color = WarningRed,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = contact.phone,
                                            fontSize = 11.sp,
                                            color = AccentOrange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteEmergencyContact(contact.id)
                                            Toast.makeText(context, "Emergency contact removed", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var relationship by remember { mutableStateOf("Family") }
        var isPrimary by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚨", fontSize = 20.sp)
                    Text(I18n.getString("add_emergency_contact", lang), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        I18n.getString("emergency_contact_desc", lang),
                        fontSize = 11.sp,
                        color = TextGray
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(I18n.getString("contact_name_label", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(I18n.getString("contact_phone_label", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_phone_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )

                    OutlinedTextField(
                        value = relationship,
                        onValueChange = { relationship = it },
                        label = { Text(I18n.getString("contact_rel_label", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("add_contact_rel_input"),
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { isPrimary = !isPrimary }
                    ) {
                        Checkbox(
                            checked = isPrimary,
                            onCheckedChange = { isPrimary = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentOrange)
                        )
                        Text(I18n.getString("set_primary_contact", lang), color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || phone.isBlank()) {
                            Toast.makeText(context, "Please enter name and phone number", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addEmergencyContact(name.trim(), phone.trim(), relationship.trim(), isPrimary)
                        showAddDialog = false
                        Toast.makeText(context, "Emergency contact saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) {
                    Text(I18n.getString("save_contact_btn", lang), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
                ) {
                    Text(I18n.getString("cancel_button", lang))
                }
            }
        )
    }
}

@Composable
fun StarRatingPicker(
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("star_rating_picker"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "App Rating",
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = " *",
                fontWeight = FontWeight.Bold,
                color = WarningRed,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("star_rating_row"),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { starIndex ->
                val isSelected = starIndex <= selectedRating
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentOrange.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentOrange else LightSlateBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = enabled) {
                            onRatingSelected(starIndex)
                        }
                        .testTag("star_rating_button_$starIndex"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Rate $starIndex star${if (starIndex > 1) "s" else ""}",
                        tint = if (isSelected) AccentOrange else TextGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (starIndex < 5) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Text(
            text = if (selectedRating in 1..5) "Rating: $selectedRating / 5" else "No rating selected",
            color = if (selectedRating in 1..5) AccentOrange else TextGray,
            fontSize = 11.sp,
            fontWeight = if (selectedRating in 1..5) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.testTag("star_rating_status_text")
        )
    }
}

@Composable
fun StarRatingDisplay(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Dp = 14.dp,
    showScoreText: Boolean = true
) {
    Row(
        modifier = modifier.testTag("star_rating_display"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (rating in 1..5) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..5).forEach { starIndex ->
                    Icon(
                        imageVector = if (starIndex <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(starSize)
                    )
                }
            }
            if (showScoreText) {
                Text(
                    text = "$rating/5",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
        } else {
            Text(
                text = "Not rated",
                fontSize = 11.sp,
                color = TextGray,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedbackScreen(viewModel: NyayaViewModel, forcedRole: String? = null) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val allFeedbacks by viewModel.userFeedbacks.collectAsState()

    val activeRole = forcedRole ?: userProfile.role.ifBlank { "Citizen" }

    // Filter feedbacks for current user
    val myFeedbacks = remember(allFeedbacks, userProfile.email, userProfile.id) {
        val uid = userProfile.id.ifBlank { userProfile.email }
        allFeedbacks.filter { it.email == userProfile.email || it.uid == uid || (userProfile.email.isNotBlank() && it.email.equals(userProfile.email, ignoreCase = true)) }
    }

    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Bug Report") }
    var message by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) } // Mandatory initial state: 0 (No rating selected)
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf("Bug Report", "Feature Request", "Performance", "AI Suggestion", "UI/UX", "Complaint", "Other")
    var categoryExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = if (activeRole == "Authority") "Authority Feedback & Dispatch Suggestions" else "Feedback & Feature Requests",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AccentOrange
        )
        Text(
            text = if (activeRole == "Authority")
                "Submit official operational feedback, system reports, or feature requests to the Admin team."
            else
                "Submit feedback, report bugs, or request features to improve Nyaya AI.",
            fontSize = 11.sp,
            color = TextGray
        )

        // Submission Form Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feedback_form_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Submit New Feedback", fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 15.sp)

                // Subject input
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject *", fontSize = 11.sp) },
                    placeholder = { Text("Short summary of your feedback...", fontSize = 11.sp, color = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_subject_input"),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = TextGray
                    )
                )

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("feedback_category_dropdown"),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextGray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Message input
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Feedback Message *", fontSize = 11.sp) },
                    placeholder = { Text("Describe your feedback or suggestions in detail...", fontSize = 11.sp, color = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("feedback_message_input"),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = TextGray
                    )
                )

                // Mandatory 5-Star Rating Picker
                StarRatingPicker(
                    selectedRating = rating,
                    onRatingSelected = { rating = it }
                )

                // Submit Button
                Button(
                    onClick = {
                        if (subject.isBlank()) {
                            Toast.makeText(context, "Please enter a subject", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (message.isBlank()) {
                            Toast.makeText(context, "Please enter a feedback message", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (rating !in 1..5) {
                            Toast.makeText(context, "Please select an app rating from 1 to 5 stars.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        isSubmitting = true
                        viewModel.submitUserFeedback(
                            category = category,
                            subject = subject,
                            message = message,
                            rating = rating,
                            attachmentUrl = "",
                            roleOverride = activeRole
                        ) { success, msg ->
                            isSubmitting = false
                            if (success) {
                                Toast.makeText(context, "Feedback submitted successfully.", Toast.LENGTH_LONG).show()
                                subject = ""
                                message = ""
                                rating = 0 // Reset to unselected
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_feedback_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                    } else {
                        Text("Submit Feedback", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // My Submitted Feedback History List
        Text("Your Feedback History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)

        if (myFeedbacks.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("You have not submitted any feedback yet.", color = TextGray, fontSize = 12.sp)
                }
            }
        } else {
            myFeedbacks.forEach { fb ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_feedback_item_${fb.feedbackId}"),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, LightSlateBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fb.subject, fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (fb.status) {
                                        "Replied", "Reviewed" -> SuccessGreen.copy(alpha = 0.2f)
                                        "Resolved" -> LightJusticeBlue.copy(alpha = 0.2f)
                                        else -> AccentOrange.copy(alpha = 0.2f)
                                    }
                                ),
                                border = BorderStroke(1.dp, when (fb.status) {
                                    "Replied", "Reviewed" -> SuccessGreen
                                    "Resolved" -> LightJusticeBlue
                                    else -> AccentOrange
                                }),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = fb.status.uppercase(),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (fb.status) {
                                        "Replied", "Reviewed" -> SuccessGreen
                                        "Resolved" -> LightJusticeBlue
                                        else -> AccentOrange
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Category: ${fb.category}", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold)
                            StarRatingDisplay(rating = fb.rating)
                        }

                        Text(fb.message, fontSize = 12.sp, color = Color.White)
                        Text("Submitted on: ${dateFormat.format(Date(fb.createdAt))}", fontSize = 10.sp, color = TextGray)

                        // Admin Reply section
                        if (fb.adminReply.isNotBlank()) {
                            HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkIndigo.copy(alpha = 0.8f)),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                        Text("Admin Response", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                    }
                                    Text("Admin Name: ${fb.adminName.ifBlank { "System Administrator" }}", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                                    Text("Department: ${fb.department.ifBlank { "Nyaya AI Support" }}", fontSize = 10.sp, color = TextGray)
                                    Text("Reply: ${fb.adminReply}", fontSize = 11.sp, color = Color.White)
                                    if (fb.repliedAt > 0) {
                                        Text("Replied On: ${dateFormat.format(Date(fb.repliedAt))}", fontSize = 9.sp, color = TextGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
