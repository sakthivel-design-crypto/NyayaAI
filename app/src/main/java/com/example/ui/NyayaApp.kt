package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
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
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    object Map : Screen("map", "Safety Map", { Icon(Icons.Default.EmergencyShare, contentDescription = "Safety Map") })
    object Complaints : Screen("complaints", "Complaints", { Icon(Icons.Default.Assignment, contentDescription = "Complaints") })
    object Laws : Screen("laws", "Browse Laws", { Icon(Icons.Default.Gavel, contentDescription = "Browse Laws") })
    object Forum : Screen("forum", "Forum", { Icon(Icons.Default.Forum, contentDescription = "Community Forum") })
    object Profile : Screen("profile", "Profile", { Icon(Icons.Default.Person, contentDescription = "My Profile") })
}

sealed class AuthorityScreen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Reports : AuthorityScreen("auth_reports", "Incidents", { Icon(Icons.Default.Notifications, contentDescription = "Incident Reports") })
    object Requests : AuthorityScreen("auth_requests", "Inquiries", { Icon(Icons.Default.QuestionAnswer, contentDescription = "Citizen Inquiries") })
    object Dispatch : AuthorityScreen("auth_dispatch", "Dispatch", { Icon(Icons.Default.AssignmentLate, contentDescription = "Dispatch") })
    object Analytics : AuthorityScreen("auth_analytics", "Stats", { Icon(Icons.Default.BarChart, contentDescription = "Dispatch Stats") })
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
    
    // Auth sub-navigation state (defaults to LANDING for premium entry)
    var authScreenState by remember { mutableStateOf("LANDING") } 

    // Load local knowledge JSON at startup
    LaunchedEffect(Unit) {
        viewModel.loadLegalDatabase(context)
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
                onNavigateToForgot = { authScreenState = "FORGOT_PASSWORD_AUTHORITY" },
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
            "FORGOT_PASSWORD_ADMIN" -> ForgotPasswordScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authScreenState = "ADMIN_LOGIN" }
            )
            else -> {
                authScreenState = "LANDING"
            }
        }
    } else {
        when (userProfile.role) {
            "Authority" -> AuthorityDashboard(viewModel)
            "Admin" -> AdminDashboard(viewModel)
            else -> CitizenDashboard(viewModel)
        }
    }
}

@Composable
fun CitizenDashboard(viewModel: NyayaViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Assistant) }
    val lang by viewModel.currentLanguage.collectAsState()
    
    Scaffold(
        bottomBar = {
            Column {
                if (DEMO_MODE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = I18n.getString("demo_mode", lang),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 0.dp
                ) {
                    val screens = listOf(Screen.Assistant, Screen.Map, Screen.Complaints, Screen.Laws, Screen.Forum, Screen.Profile)
                    screens.forEach { screen ->
                        val translatedTitle = when (screen) {
                            Screen.Assistant -> I18n.getString("nav_assistant", lang)
                            Screen.Map -> I18n.getString("nav_map", lang)
                            Screen.Complaints -> I18n.getString("nav_complaints", lang)
                            Screen.Laws -> I18n.getString("nav_laws", lang)
                            Screen.Forum -> I18n.getString("nav_forum", lang)
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
                is Screen.Map -> EmergencyMapScreen(viewModel)
                is Screen.Complaints -> CitizenComplaintsScreen(viewModel)
                is Screen.Laws -> BrowseLawsScreen(viewModel)
                is Screen.Forum -> ForumScreen(viewModel)
                is Screen.Profile -> ProfileScreen(viewModel)
            }
        }
    }
}

@Composable
fun AuthorityDashboard(viewModel: NyayaViewModel) {
    var currentScreen by remember { mutableStateOf<AuthorityScreen>(AuthorityScreen.Reports) }
    val lang by viewModel.currentLanguage.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                if (DEMO_MODE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = I18n.getString("demo_mode", lang),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = CardBackground,
                    tonalElevation = 0.dp
                ) {
                    val screens = listOf(
                        AuthorityScreen.Reports,
                        AuthorityScreen.Requests,
                        AuthorityScreen.Dispatch,
                        AuthorityScreen.Analytics,
                        AuthorityScreen.Profile
                    )
                    screens.forEach { screen ->
                        val translatedTitle = when (screen) {
                            AuthorityScreen.Reports -> "Incidents" // keep as-is or localize
                            AuthorityScreen.Requests -> "Inquiries" // keep as-is or localize
                            AuthorityScreen.Dispatch -> I18n.getString("nav_complaints", lang)
                            AuthorityScreen.Analytics -> I18n.getString("filter_priority", lang) // or "Stats"
                            AuthorityScreen.Profile -> I18n.getString("nav_profile", lang)
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
                is AuthorityScreen.Reports -> AuthorityReportsScreen(viewModel)
                is AuthorityScreen.Requests -> AuthorityRequestsScreen(viewModel)
                is AuthorityScreen.Dispatch -> AuthorityDispatchScreen(viewModel)
                is AuthorityScreen.Analytics -> AuthorityAnalyticsScreen(viewModel)
                is AuthorityScreen.Profile -> ProfileScreen(viewModel)
            }
        }
    }
}

@Composable
fun AdminDashboard(viewModel: NyayaViewModel) {
    var currentScreen by remember { mutableStateOf<AdminScreen>(AdminScreen.Overview) }

    Scaffold(
        bottomBar = {
            Column {
                if (DEMO_MODE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DEMO MODE ACTIVE",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
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
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = screen.icon,
                            label = { Text(screen.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
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
    val supportedLanguages = listOf("English", "Tamil", "Hindi", "Telugu", "Kannada", "Malayalam")

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
                Text(lang, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = languageDropdownOpen,
                onDismissRequest = { languageDropdownOpen = false },
                modifier = Modifier.background(CardBackground)
            ) {
                supportedLanguages.forEach { languageName ->
                    val dispName = when(languageName) {
                        "English" -> "English"
                        "Tamil" -> "தமிழ்"
                        "Hindi" -> "हिन्दी"
                        "Telugu" -> "తెలుగు"
                        "Kannada" -> "ಕನ್ನಡ"
                        "Malayalam" -> "മലയാളം"
                        else -> languageName
                    }
                    DropdownMenuItem(
                        text = { Text(dispName, color = Color.White, fontWeight = FontWeight.Bold) },
                        onClick = {
                            viewModel.selectLanguage(languageName)
                            languageDropdownOpen = false
                        }
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
                .widthIn(max = 450.dp)
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
                    Icons.Default.Group,
                    contentDescription = "Citizen",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = I18n.getString("login_title_citizen", lang),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = AccentOrange
            )
            Text(
                text = I18n.getString("login_subtitle_citizen", lang),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = I18n.getString("auth_gate", lang),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(I18n.getString("email_address", lang)) },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth().testTag("citizen_login_email"),
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
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("citizen_login_password"),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentOrange)
                            )
                            Text(I18n.getString("remember_me", lang), fontSize = 11.sp, color = TextGray)
                        }
                        Text(
                            text = I18n.getString("forgot_password", lang),
                            color = LightJusticeBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(4.dp)
                        )
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
                        modifier = Modifier.fillMaxWidth().testTag("citizen_captcha_input"),
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
                            if (email.isEmpty() || password.isEmpty()) {
                                Toast.makeText(context, "Please enter all fields.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (captchaInput.trim().uppercase() != captchaCode) {
                                Toast.makeText(context, "CAPTCHA verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                                captchaCode = generateRandomCaptcha()
                                return@Button
                            }
                            isLoading = true
                            viewModel.login(email, password, "Citizen") { success, msg ->
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
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(I18n.getString("login_btn", lang), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = I18n.getString("no_account", lang),
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Demo Login Credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    Text(
                        text = "Easily test Citizen functionalities without registration.",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Email: citizen@demo.com", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                            Text("Password: Citizen@123", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                email = "citizen@demo.com"
                                password = "Citizen@123"
                                captchaInput = captchaCode
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange),
                            border = BorderStroke(1.dp, AccentOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("use_citizen_demo_button")
                        ) {
                            Text("Use Citizen Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorityLoginScreen(
    viewModel: NyayaViewModel,
    onNavigateToForgot: () -> Unit,
    onBackToLanding: () -> Unit
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    var inputIdentity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedDepartment by remember { mutableStateOf("Police") }
    var captchaCode by remember { mutableStateOf(generateRandomCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val departments = listOf(
        "Police", "Collector", "Tahsildar", "Municipality",
        "Health", "Fire", "Revenue", "Transport"
    )

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
                    Icons.Default.Security,
                    contentDescription = "Authority",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = I18n.getString("authority_portal", lang),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = AccentOrange
            )
            Text(
                text = I18n.getString("login_subtitle_authority", lang),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = I18n.getString("officer_credentials", lang),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    OutlinedTextField(
                        value = inputIdentity,
                        onValueChange = { inputIdentity = it },
                        label = { Text(I18n.getString("gov_id_placeholder", lang)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth().testTag("authority_login_id"),
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
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("authority_login_password"),
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

                    Text(
                        text = I18n.getString("assign_dept_jurisdiction", lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .border(1.dp, LightSlateBorder, RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(departments) { dept ->
                            val active = selectedDepartment == dept
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) AccentOrange.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { selectedDepartment = dept }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    dept,
                                    fontSize = 11.5.sp,
                                    color = if (active) AccentOrange else TextDarkSlate,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                                if (active) {
                                    Icon(Icons.Default.Check, null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = I18n.getString("forgot_pin", lang),
                            color = LightJusticeBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(4.dp)
                        )
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
                        modifier = Modifier.fillMaxWidth().testTag("authority_captcha_input"),
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
                            if (inputIdentity.isEmpty() || password.isEmpty()) {
                                Toast.makeText(context, "All credentials are required.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (captchaInput.trim().uppercase() != captchaCode) {
                                Toast.makeText(context, "CAPTCHA verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                                captchaCode = generateRandomCaptcha()
                                return@Button
                            }
                            isLoading = true
                            val finalEmail = if (inputIdentity.contains("@")) {
                                inputIdentity
                            } else {
                                when (inputIdentity) {
                                    "authority" -> "authority@nyaya.ai"
                                    "unapproved" -> "unapproved@nyaya.ai"
                                    else -> "$inputIdentity@nyaya.ai"
                                }
                            }
                            viewModel.login(finalEmail, password, "Authority") { success, msg ->
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
                            .testTag("authority_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(I18n.getString("login_btn", lang), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Demo Login Credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    Text(
                        text = "Easily test Authority terminal functionalities.",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ID: POLICE001", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                            Text("Password: Authority@123", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                            Text("Dept: Police", fontSize = 11.sp, color = TextGray)
                        }
                        Button(
                            onClick = {
                                inputIdentity = "POLICE001"
                                password = "Authority@123"
                                selectedDepartment = "Police"
                                captchaInput = captchaCode
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange),
                            border = BorderStroke(1.dp, AccentOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("use_authority_demo_button")
                        ) {
                            Text("Use Authority Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
    var captchaCode by remember { mutableStateOf(generateRandomCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
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
                .widthIn(max = 450.dp)
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
                text = I18n.getString("admin_portal", lang),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = AccentOrange
            )
            Text(
                text = I18n.getString("login_subtitle_admin", lang),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = I18n.getString("admin_verification", lang),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )

                    OutlinedTextField(
                        value = inputIdentity,
                        onValueChange = { inputIdentity = it },
                        label = { Text(I18n.getString("admin_id_placeholder", lang)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth().testTag("admin_login_id"),
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
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("admin_login_password"),
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(4.dp)
                        )
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
                        modifier = Modifier.fillMaxWidth().testTag("admin_captcha_input"),
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
                            if (inputIdentity.isEmpty() || password.isEmpty()) {
                                Toast.makeText(context, "Please enter all fields.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (captchaInput.trim().uppercase() != captchaCode) {
                                Toast.makeText(context, "CAPTCHA verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                                captchaCode = generateRandomCaptcha()
                                return@Button
                            }
                            isLoading = true
                            val finalEmail = if (inputIdentity.contains("@")) {
                                inputIdentity
                            } else {
                                if (inputIdentity == "admin") "admin@nyaya.ai" else "$inputIdentity@nyaya.ai"
                            }
                            viewModel.login(finalEmail, password, "Admin") { success, msg ->
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
                            .testTag("admin_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(I18n.getString("login_btn", lang), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Demo Login Credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    Text(
                        text = "Easily test Administrator Command Center functionalities.",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Email: admin@demo.com", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                            Text("Password: Admin@123", fontSize = 11.sp, color = TextDarkSlate, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                inputIdentity = "admin@demo.com"
                                password = "Admin@123"
                                captchaInput = captchaCode
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange),
                            border = BorderStroke(1.dp, AccentOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("use_admin_demo_button")
                        ) {
                            Text("Use Admin Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    var step by remember { mutableStateOf("EMAIL") } // "EMAIL", "OTP", "NEW_PASSWORD", "SUCCESS"
    var otpInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val hasMinLength = newPassword.length >= 8
    val hasUppercase = newPassword.any { it.isUpperCase() }
    val hasDigit = newPassword.any { it.isDigit() }
    val hasSpecial = newPassword.any { "@#$%^&+=!_".contains(it) }
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword

    val strength = remember(newPassword) {
        var score = 0
        if (hasMinLength) score++
        if (hasUppercase) score++
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                "EMAIL" -> {
                    Text(
                        text = "Access Restoration Node",
                        fontSize = 22.sp,
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
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Request Password Reset Pin",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            Text(
                                text = "Enter your registered email address. We will broadcast a security verification code to your terminal inbox.",
                                fontSize = 11.sp,
                                color = TextGray,
                                lineHeight = 16.sp
                            )

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Registered Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                modifier = Modifier.fillMaxWidth().testTag("forgot_email_input"),
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
                                    if (email.isEmpty()) {
                                        Toast.makeText(context, "Please enter your security email.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    viewModel.forgotPassword(email) { success, msg ->
                                        isLoading = false
                                        if (success) {
                                            Toast.makeText(context, "Security reset broadcast complete! OTP is: 123456", Toast.LENGTH_LONG).show()
                                            step = "OTP"
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("forgot_password_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate OTP Security Pin", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "OTP" -> {
                    Text(
                        text = "Verification Gateway",
                        fontSize = 22.sp,
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
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Submit Security OTP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            Text(
                                text = "Enter the 6-digit OTP code broadcasted to your terminal ($email). Hint: Use code 123456 for simulator clearance.",
                                fontSize = 11.sp,
                                color = TextGray,
                                lineHeight = 16.sp
                            )

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it },
                                label = { Text("6-Digit Pin") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                modifier = Modifier.fillMaxWidth().testTag("forgot_otp_input"),
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
                                    if (otpInput.trim() == "123456") {
                                        step = "NEW_PASSWORD"
                                    } else {
                                        Toast.makeText(context, "Invalid Security OTP pin. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_otp_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Verify Node Pin", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "NEW_PASSWORD" -> {
                    Text(
                        text = "Reset Node Master Keys",
                        fontSize = 22.sp,
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
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Create Brand New Password",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("New Security Password") },
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
                                modifier = Modifier.fillMaxWidth().testTag("forgot_new_password"),
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
                                label = { Text("Confirm Security Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("forgot_confirm_password"),
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

                            Text("Password Strength Level", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
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
                            
                            val strengthText = when (strength) {
                                1 -> "Weak Strength (Vulnerable)"
                                2 -> "Medium Strength (Fair)"
                                3 -> "Good Strength (Strong)"
                                4 -> "Excellent Complexity (Unbreakable)"
                                else -> "Enter Password"
                            }
                            Text(strengthText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (strength >= 3) SuccessGreen else WarningRed)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuleRow(label = "At least 8 alpha-numeric characters", isMet = hasMinLength)
                                RuleRow(label = "Contains at least 1 uppercase letter", isMet = hasUppercase)
                                RuleRow(label = "Contains at least 1 numeric digit", isMet = hasDigit)
                                RuleRow(label = "Contains at least 1 special character (@#$%^&+=!_)", isMet = hasSpecial)
                                RuleRow(label = "Passwords match exactly", isMet = passwordsMatch)
                            }

                            Button(
                                onClick = {
                                    if (strength < 3) {
                                        Toast.makeText(context, "Password is too weak. Please meet complexity standards.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (!passwordsMatch) {
                                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    viewModel.resetPassword(email, newPassword) { success, msg ->
                                        isLoading = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            step = "SUCCESS"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("finalize_password_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                Text("Re-Encrypt Access Credentials", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "SUCCESS" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                "Credentials Secured",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkSlate
                            )

                            Text(
                                "Your new security node master keys have been successfully encrypted and stored on the system database. You can now use these credentials to authenticate.",
                                fontSize = 11.sp,
                                color = TextGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )

                            Button(
                                onClick = onNavigateToLogin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("forgot_success_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Secure Access Portal Sign-In", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (step != "SUCCESS") {
                Text(
                    text = "Return to Sign-In",
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
fun AuthorityReportsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val reports by viewModel.allReports.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    
    // Dialog state
    var editingReport by remember { mutableStateOf<IncidentReport?>(null) }
    var notesInput by remember { mutableStateOf("") }
    var statusChoice by remember { mutableStateOf("Pending") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("Incident Reports Dispatch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Manage public alerts and legal compliance", fontSize = 11.sp, color = TextGray)

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search reported incidents...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categories = listOf("All", "SOS Alert", "Cybercrime", "Traffic", "Harassment")
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { Text(category, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange,
                        selectedLabelColor = Color.Black,
                        containerColor = CardBackground,
                        labelColor = TextGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Incidents List
        val filtered = reports.filter {
            (selectedCategory == "All" || it.category == selectedCategory) &&
            (it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true))
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No incidents reported in this scope.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered) { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingReport = report
                                notesInput = report.authorityNotes
                                statusChoice = report.status
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (report.category == "SOS Alert") WarningRed else JusticeBlue
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = report.category.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (report.status) {
                                            "Pending" -> AccentOrange.copy(alpha = 0.2f)
                                            "In Investigation" -> JusticeBlue.copy(alpha = 0.2f)
                                            else -> SuccessGreen.copy(alpha = 0.2f)
                                        }
                                    ),
                                    border = BorderStroke(1.dp, when (report.status) {
                                        "Pending" -> AccentOrange
                                        "In Investigation" -> JusticeBlue
                                        else -> SuccessGreen
                                    })
                                ) {
                                    Text(
                                        text = report.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (report.status) {
                                            "Pending" -> AccentOrange
                                            "In Investigation" -> LightJusticeBlue
                                            else -> SuccessGreen
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(report.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
                            Text(report.description, fontSize = 11.sp, color = TextGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            
                            HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("By: ${report.reporterName}", fontSize = 10.sp, color = TextGray)
                                Text("Notes: ${if (report.authorityNotes.isEmpty()) "None" else report.authorityNotes}", fontSize = 10.sp, color = AccentOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail & Status Edit Dialog
    editingReport?.let { report ->
        AlertDialog(
            onDismissRequest = { editingReport = null },
            title = { Text("Incident Security Audit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Title: ${report.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                    Text("Details: ${report.description}", fontSize = 12.sp, color = TextGray)
                    
                    if (report.locationLat != 0.0) {
                        Text("📍 Coordinates: ${report.locationLat}, ${report.locationLng}", fontSize = 11.sp, color = AccentOrange)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Set Resolution Status", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Pending", "In Investigation", "Resolved").forEach { status ->
                            val active = statusChoice == status
                            Button(
                                onClick = { statusChoice = status },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) AccentOrange else CardBackground
                                ),
                                border = BorderStroke(1.dp, if (active) AccentOrange else LightSlateBorder),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(status, fontSize = 9.sp, color = if (active) Color.Black else TextDarkSlate, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Authority Resolution Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateReportStatus(report.id, statusChoice, notesInput)
                        editingReport = null
                        Toast.makeText(context, "Incident updated in secure ledger.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Apply Updates", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingReport = null }) {
                    Text("Close")
                }
            },
            containerColor = CardBackground,
            titleContentColor = AccentOrange
        )
    }
}

@Composable
fun AuthorityRequestsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val requests by viewModel.allCitizenRequests.collectAsState()
    
    var replyingRequest by remember { mutableStateOf<CitizenRequest?>(null) }
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("Citizen Consultations", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Official legal assistance and advice responses", fontSize = 11.sp, color = TextGray)

        Spacer(modifier = Modifier.height(14.dp))

        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No consultation requests received.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(requests) { req ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                replyingRequest = req
                                replyText = req.reply
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (req.status == "Open") WarningRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                                    ),
                                    border = BorderStroke(1.dp, if (req.status == "Open") WarningRed else SuccessGreen)
                                ) {
                                    Text(
                                        text = req.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (req.status == "Open") WarningRed else SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text("Inquiry ID: ${req.id}", fontSize = 9.sp, color = TextGray)
                            }

                            Text(req.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
                            Text(req.details, fontSize = 11.sp, color = TextGray)

                            if (req.reply.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = LightBlueHighlight)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("⚖️ Official Response:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                        Text(req.reply, fontSize = 11.sp, color = TextDarkSlate)
                                    }
                                }
                            } else {
                                Text("⚠️ Awaiting legal council response.", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Official Reply Dialog
    replyingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { replyingRequest = null },
            title = { Text("Formulate Official Response", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Subject: ${req.subject}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                    Text("Query: ${req.details}", fontSize = 11.sp, color = TextGray)
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Write Legal Council Reply") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replyText.isEmpty()) {
                            Toast.makeText(context, "Response body cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.answerCitizenRequest(req.id, replyText)
                        replyingRequest = null
                        Toast.makeText(context, "Consultation answered.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Send Answer", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { replyingRequest = null }) {
                    Text("Dismiss")
                }
            },
            containerColor = CardBackground,
            titleContentColor = AccentOrange
        )
    }
}

@Composable
fun AuthorityAnalyticsScreen(viewModel: NyayaViewModel) {
    val reports by viewModel.allReports.collectAsState()
    val requests by viewModel.allCitizenRequests.collectAsState()

    val totalIncidents = reports.size
    val pendingIncidents = reports.count { it.status == "Pending" }
    val resolvedIncidents = reports.count { it.status == "Resolved" }
    
    val totalRequests = requests.size
    val unansweredRequests = requests.count { it.status == "Open" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Authority Dispatch Analytics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Performance logs, security metrics, and incident maps", fontSize = 11.sp, color = TextGray)

        // Metrics Grid Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🚨 Incidents", fontSize = 11.sp, color = TextGray)
                    Text("$totalIncidents", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AccentOrange)
                    Text("Total Files", fontSize = 9.sp, color = TextGray)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏳ Pending", fontSize = 11.sp, color = TextGray)
                    Text("$pendingIncidents", fontSize = 24.sp, fontWeight = FontWeight.Black, color = WarningRed)
                    Text("Awaiting Audit", fontSize = 9.sp, color = TextGray)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅ Resolved", fontSize = 11.sp, color = TextGray)
                    Text("$resolvedIncidents", fontSize = 24.sp, fontWeight = FontWeight.Black, color = SuccessGreen)
                    Text("Closed Files", fontSize = 9.sp, color = TextGray)
                }
            }
        }

        // Consultations Performance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Citizen Consultation Inquiries", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Legal Enquiries:", fontSize = 12.sp, color = TextGray)
                    Text("$totalRequests", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Unanswered Questions:", fontSize = 12.sp, color = TextGray)
                    Text("$unansweredRequests", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningRed)
                }

                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                val answerRate = if (totalRequests > 0) ((totalRequests - unansweredRequests).toFloat() / totalRequests) else 1.0f
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Response Efficiency Rate:", fontSize = 11.sp, color = TextGray)
                    Text("${(answerRate * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = SuccessGreen)
                }
                
                LinearProgressIndicator(
                    progress = answerRate,
                    color = SuccessGreen,
                    trackColor = LightBlueHighlight,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // Incidents Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Report Category Density", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)

                val categories = listOf("SOS Alert", "Cybercrime", "Traffic", "Harassment")
                categories.forEach { category ->
                    val count = reports.count { it.category == category }
                    val fraction = if (reports.isNotEmpty()) count.toFloat() / reports.size else 0f
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, fontSize = 11.sp, color = TextDarkSlate)
                            Text("$count reports", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                        LinearProgressIndicator(
                            progress = fraction,
                            color = JusticeBlue,
                            trackColor = LightBlueHighlight,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.allUserAccounts.collectAsState()
    var deletingAccount by remember { mutableStateOf<UserAccount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("User Node Administration", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("Regulate account clearance and access keys", fontSize = 11.sp, color = TextGray)

        Spacer(modifier = Modifier.height(14.dp))

        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No user accounts found.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(accounts) { account ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(account.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
                                Text(account.email, fontSize = 11.sp, color = TextGray)
                                
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
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.widthIn(max = 100.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = account.role.uppercase(),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (account.role) {
                                                "Admin" -> WarningRed
                                                "Authority" -> LightJusticeBlue
                                                else -> SuccessGreen
                                            }
                                        )
                                    }
                                }

                                if (account.email != "admin@nyaya.ai" && account.email != viewModel.userProfile.value.email) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isDisabled = account.isDisabled
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.toggleAccountDisabled(account.email, !isDisabled)
                                                Toast.makeText(context, if (!isDisabled) "Account disabled" else "Account enabled", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDisabled) WarningRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                                            ),
                                            border = BorderStroke(1.dp, if (isDisabled) WarningRed else SuccessGreen),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (isDisabled) "DISABLED" else "ACTIVE",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDisabled) WarningRed else SuccessGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (account.role == "Authority") {
                                            val isApproved = account.isApproved
                                            Card(
                                                modifier = Modifier.clickable {
                                                    viewModel.toggleAccountApproval(account.email, !isApproved)
                                                    Toast.makeText(context, if (!isApproved) "Authority approved" else "Authority unapproved", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isApproved) SuccessGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f)
                                                ),
                                                border = BorderStroke(1.dp, if (isApproved) SuccessGreen else AccentOrange),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isApproved) "APPROVED" else "PENDING APPROVAL",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isApproved) SuccessGreen else AccentOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Revoke / Delete access button
                            if (account.email != "admin@nyaya.ai" && account.email != viewModel.userProfile.value.email) {
                                IconButton(
                                    onClick = { deletingAccount = account },
                                    modifier = Modifier.testTag("delete_user_${account.email}")
                                ) {
                                    Icon(Icons.Default.Delete, "Revoke Access", tint = WarningRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Delete dialog
    deletingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("Revoke Security Credentials", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you absolutely sure you want to delete and purge the credentials for ${account.name} (${account.email})? This action is irreversible on the node ledger.", fontSize = 12.sp, color = TextGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(account.email)
                        deletingAccount = null
                        Toast.makeText(context, "Node authorization key successfully purged.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) {
                    Text("Purge Node", color = Color.White)
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
fun AdminLawsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val laws by viewModel.legalTopics.collectAsState()
    
    var showAddForm by remember { mutableStateOf(false) }
    
    // Form Inputs
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Civil Rights") }
    var summary by remember { mutableStateOf("") }
    var officialAuthority by remember { mutableStateOf("Ministry of Law and Justice") }
    var officialSource by remember { mutableStateOf("The Constitution of India") }
    var officialSourceUrl by remember { mutableStateOf("https://legislative.gov.in") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Legal Dataset Authority", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Text("Live-feed custom constitutional rules", fontSize = 11.sp, color = TextGray)
            }
            
            Button(
                onClick = { showAddForm = !showAddForm },
                colors = ButtonDefaults.buttonColors(containerColor = if (showAddForm) WarningRed else AccentOrange),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(if (showAddForm) "Close Form" else "+ Inject Law", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showAddForm) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Inject Live Legal Clause", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Law Title (e.g. Right to Education Act)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Clause Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Concise Law Summary & Penalties") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 4
                )

                OutlinedTextField(
                    value = officialAuthority,
                    onValueChange = { officialAuthority = it },
                    label = { Text("Regulatory Authority") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = officialSource,
                    onValueChange = { officialSource = it },
                    label = { Text("Official Citation/Document") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = officialSourceUrl,
                    onValueChange = { officialSourceUrl = it },
                    label = { Text("Official Source URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (title.isEmpty() || summary.isEmpty()) {
                            Toast.makeText(context, "Title and summary are mandatory legal assets.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addCustomLegalTopic(title, category, summary, officialAuthority, officialSource, officialSourceUrl)
                        Toast.makeText(context, "New legal clause injected into central database!", Toast.LENGTH_LONG).show()
                        
                        // Clear fields and close form
                        title = ""
                        summary = ""
                        showAddForm = false
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Publish to RAG Corpus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Display standard list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(laws) { law ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(law.category, fontSize = 9.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                                Text("ID: #${law.id}", fontSize = 9.sp, color = TextGray)
                            }
                            Text(law.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkSlate)
                            Text(law.summary, fontSize = 11.sp, color = TextGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminFeedbackScreen(viewModel: NyayaViewModel) {
    val feedbacks by viewModel.feedbacks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Text("AI Accuracy & Feedback Logs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
        Text("User-submitted AI helpfulness feedback and star ratings", fontSize = 11.sp, color = TextGray)

        Spacer(modifier = Modifier.height(14.dp))

        if (feedbacks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No AI feedback logs stored yet.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(feedbacks) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(5) { index ->
                                        val isFilled = index < log.starRating
                                        Text(
                                            text = "★",
                                            color = if (isFilled) AccentOrange else Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (log.isHelpful) SuccessGreen.copy(alpha = 0.2f) else WarningRed.copy(alpha = 0.2f)
                                    ),
                                    border = BorderStroke(1.dp, if (log.isHelpful) SuccessGreen else WarningRed)
                                ) {
                                    Text(
                                        text = if (log.isHelpful) "HELPFUL" else "UNHELPFUL",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.isHelpful) SuccessGreen else WarningRed,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text("Query: \"${log.query}\"", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                            Text("Response: \"${log.response}\"", fontSize = 11.sp, color = TextGray, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            
                            if (!log.textFeedback.isNullOrEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = LightBlueHighlight)
                                ) {
                                    Text(
                                        text = "Comment: \"${log.textFeedback}\"",
                                        fontSize = 11.sp,
                                        color = TextDarkSlate,
                                        modifier = Modifier.padding(8.dp)
                                    )
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
                            .clickable {
                                keyboardController?.hide()
                                viewModel.sendChatMessage(suggestion)
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
                    if (textInput.isNotBlank()) {
                        viewModel.sendChatMessage(textInput)
                        textInput = ""
                        keyboardController?.hide()
                    }
                })
            )
            
            Spacer(modifier = Modifier.width(10.dp))

            FloatingActionButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendChatMessage(textInput)
                        textInput = ""
                        keyboardController?.hide()
                    }
                },
                containerColor = JusticeBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_message_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    modifier = Modifier.size(20.dp)
                )
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
// SCREEN 2: EMERGENCY MAP (WEBVIEW + GPS)
// ==========================================
fun serializeReportsToJson(reports: List<com.example.db.IncidentReport>): String {
    val builder = StringBuilder()
    builder.append("[")
    reports.forEachIndexed { index, report ->
        val escapedTitle = report.title.replace("\"", "\\\"").replace("\n", " ")
        val escapedDesc = report.description.replace("\"", "\\\"").replace("\n", " ")
        val escapedReporter = report.reporterName.replace("\"", "\\\"").replace("\n", " ")
        builder.append("{")
        builder.append("\"id\":\"${report.id}\",")
        builder.append("\"reporterName\":\"$escapedReporter\",")
        builder.append("\"title\":\"$escapedTitle\",")
        builder.append("\"desc\":\"$escapedDesc\",")
        builder.append("\"category\":\"${report.category}\",")
        builder.append("\"status\":\"${report.status}\",")
        builder.append("\"timestamp\":${report.timestamp},")
        builder.append("\"lat\":${report.locationLat},")
        builder.append("\"lng\":${report.locationLng}")
        builder.append("}")
        if (index < reports.size - 1) {
            builder.append(",")
        }
    }
    builder.append("]")
    return builder.toString()
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmergencyMapScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasLocationPermission by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val nearbyServices by viewModel.nearbyServices.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    
    val reports by viewModel.allReports.collectAsState()
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTitle by remember { mutableStateOf("") }
    var reportDesc by remember { mutableStateOf("") }
    var reportCategory by remember { mutableStateOf("SOS Alert") }

    // Reactively inject complaint markers into the map
    LaunchedEffect(reports, webViewInstance) {
        if (webViewInstance != null) {
            delay(600)
            val json = serializeReportsToJson(reports)
            val escapedJsonForJs = json.replace("\\", "\\\\").replace("'", "\\'")
            webViewInstance?.loadUrl("javascript:loadComplaintMarkers('$escapedJsonForJs')")
        }
    }
    
    // GPS Fused Location Client
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Request permissions dynamically
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            Toast.makeText(context, "GPS Tracking enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Relying on fallback default location (New Delhi). Override with Search box.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission requests
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Fetch and sync GPS location periodically
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                // Initial immediate GPS fetch
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.updateLocation(location.latitude, location.longitude)
                        webViewInstance?.loadUrl("javascript:updateUserLocation(${location.latitude}, ${location.longitude})")
                    }
                }
                
                // Active continuous location request setup
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
                    .setMinUpdateIntervalMillis(8000)
                    .build()
                
                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        for (loc in result.locations) {
                            viewModel.updateLocation(loc.latitude, loc.longitude)
                            webViewInstance?.loadUrl("javascript:updateUserLocation(${loc.latitude}, ${loc.longitude})")
                        }
                    }
                }
                
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
            } catch (unsecured: SecurityException) {
                unsecured.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
    ) {
        // Top section: Map View taking 55% height
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
        ) {
            // Embed the map.html loaded from assets
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        // Hook Javascript interface callbacks to show Native Dialogs/Toasts when users interact
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onLocationUpdated(lat: Double, lng: Double) {
                                viewModel.updateLocation(lat, lng)
                            }

                            @JavascriptInterface
                            fun onFilterChanged(category: String) {
                                selectedCategory = category
                            }

                            @JavascriptInterface
                            fun onSearchSuccess(query: String, lat: Double, lng: Double) {
                                viewModel.updateLocation(lat, lng)
                            }

                            @JavascriptInterface
                            fun onRouteCalculated(destination: String, distance: Double, duration: Int) {
                                // Dynamic Toast describing path
                            }

                            @JavascriptInterface
                            fun onServicesUpdated(servicesJson: String) {
                                viewModel.updateNearbyServices(servicesJson)
                            }

                            @JavascriptInterface
                            fun openGoogleMapsNav(lat: Double, lng: Double) {
                                val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    ctx.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    // Fallback to browser Google Maps
                                    val webMapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
                                    webMapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(webMapIntent)
                                }
                            }
                        }, "NyayaNative")
                        
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("file:///android_asset/map.html")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("interactive_map_view"),
                update = { webView ->
                    webViewInstance = webView
                }
            )

            // SOS floating button
            FloatingActionButton(
                onClick = {
                    Toast.makeText(context, "Initiating emergency legal & police assistance request...", Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                    context.startActivity(intent)
                },
                containerColor = WarningRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(56.dp)
                    .testTag("sos_button")
            ) {
                Text(
                    text = "SOS",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }

        // Bottom section: Emergency Directory Card taking remaining height
        Card(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Category tabs inside directory
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categories = listOf("All" to "⚖️", "Police" to "🚨", "Hospital" to "🏥", "Legal Aid" to "🛡️", "Incidents" to "⚠️")
                    categories.forEach { (cat, emoji) ->
                        val isSelected = selectedCategory == cat
                        val dispName = when(cat) {
                            "All" -> I18n.getString("map_filter_all", lang)
                            else -> cat
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = cat
                                // Tell webview to filter
                                webViewInstance?.loadUrl("javascript:filterCategory('$cat')")
                            },
                            label = { Text("$emoji $dispName", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JusticeBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F3F5),
                                labelColor = TextDarkSlate
                            ),
                            border = null,
                            modifier = Modifier.testTag("filter_chip_${cat.lowercase().replace(" ", "_")}")
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "Incidents") "Recent Local Incidents" else I18n.getString("map_subtitle", lang),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSlate
                    )
                    
                    Button(
                        onClick = { showReportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp).testTag("map_report_incident_button")
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(I18n.getString("report_sos", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content Switcher based on category filter
                if (selectedCategory == "Incidents") {
                    if (reports.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, "No Incidents", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No recent community incidents reported.", color = TextGray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(reports) { report ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Center webview map and draw route
                                            webViewInstance?.loadUrl("javascript:getRouteTo(${report.locationLat}, ${report.locationLng}, '${report.title.replace("'", "\\'")}')")
                                        }
                                        .testTag("report_card_${report.id}"),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)),
                                    border = BorderStroke(1.dp, Color(0xFFFFF3E0))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(
                                                            color = when (report.status) {
                                                                "Resolved" -> Color(0xFFE8F5E9)
                                                                "In Investigation" -> Color(0xFFFFF3E0)
                                                                else -> Color(0xFFFFEBEE)
                                                            },
                                                            shape = RoundedCornerShape(8.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = when (report.category) {
                                                            "SOS Alert" -> "🚨"
                                                            "Cybercrime" -> "💻"
                                                            "Traffic" -> "🚗"
                                                            "Harassment" -> "🛡️"
                                                            else -> "⚠️"
                                                        }, 
                                                        fontSize = 18.sp
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = report.title,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextDarkSlate,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Status: ${report.status}",
                                                        color = when (report.status) {
                                                            "Resolved" -> Color(0xFF2E7D32)
                                                            "In Investigation" -> Color(0xFFE65100)
                                                            else -> WarningRed
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            
                                            // Category badge
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = LightBlueHighlight),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = report.category,
                                                    color = JusticeBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = report.description,
                                            color = TextDarkSlate.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "👤 ${report.reporterName}",
                                                color = TextGray,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "📍 (${String.format("%.4f", report.locationLat)}, ${String.format("%.4f", report.locationLng)})",
                                                color = TextGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Services List
                    val filteredServices = if (selectedCategory == "All") nearbyServices else nearbyServices.filter { it.category == selectedCategory }
                    
                    if (filteredServices.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationOff, "No Services", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Locating nearby support services...", color = TextGray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredServices) { service ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Center webview map and draw route
                                        webViewInstance?.loadUrl("javascript:getRouteTo(${service.lat}, ${service.lng}, '${service.name.replace("'", "\\'")}')")
                                    }
                                    .testTag("service_card_${service.id}"),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                border = BorderStroke(1.dp, Color(0xFFE9ECEF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        color = when (service.category) {
                                                            "Police" -> Color(0xFFFFEBEE)
                                                            "Hospital" -> Color(0xFFE3F2FD)
                                                            else -> Color(0xFFE8F5E9)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(service.icon, fontSize = 18.sp)
                                            }
                                            Column {
                                                Text(
                                                    text = service.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDarkSlate,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = service.details,
                                                    color = TextGray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        
                                        // Distance badge
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${service.distance} km",
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "📍 ${service.address}",
                                        color = TextGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Rating indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Star, "Rating", tint = Color(0xFFF1C40F), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("${service.rating}", color = TextDarkSlate, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        
                                        Spacer(modifier = Modifier.weight(1f))
                                        
                                        // Call Button
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.height(32.dp).testTag("call_service_${service.id}"),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JusticeBlue),
                                            border = BorderStroke(1.dp, JusticeBlue)
                                        ) {
                                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Call", fontSize = 11.sp)
                                        }
                                        
                                        // Navigate Button
                                        Button(
                                            onClick = {
                                                val gmmIntentUri = Uri.parse("google.navigation:q=${service.lat},${service.lng}")
                                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                                    setPackage("com.google.android.apps.maps")
                                                }
                                                try {
                                                    context.startActivity(mapIntent)
                                                } catch (e: Exception) {
                                                    // Fallback standard browser mapping or geo uri
                                                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${service.lat},${service.lng}"))
                                                    context.startActivity(fallbackIntent)
                                                }
                                            },
                                            modifier = Modifier.height(32.dp).testTag("navigate_service_${service.id}"),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                                        ) {
                                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Navigate", fontSize = 11.sp)
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

        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = {
                    Text(
                        text = "Report Community Incident",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Report local events, hazards, traffic blockades, or SOS events securely. Your GPS coordinates will be attached automatically.",
                            color = TextGray,
                            fontSize = 11.sp
                        )
                        
                        OutlinedTextField(
                            value = reportTitle,
                            onValueChange = { reportTitle = it },
                            label = { Text("Incident Title") },
                            placeholder = { Text("e.g., Road Obstruction / Water Logging") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("report_dialog_title_input")
                        )
                        
                        OutlinedTextField(
                            value = reportDesc,
                            onValueChange = { reportDesc = it },
                            label = { Text("Incident Description") },
                            placeholder = { Text("Please describe the incident details...") },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedLabelColor = AccentOrange,
                                unfocusedLabelColor = TextGray
                            ),
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("report_dialog_desc_input")
                        )

                        Text("Incident Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextGray)
                        
                        // Categories chips list
                        val reportCategories = listOf("SOS Alert", "Cybercrime", "Traffic", "Harassment", "Civil", "Criminal")
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            reportCategories.forEach { cat ->
                                val active = reportCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) WarningRed else Color.Black.copy(alpha = 0.25f))
                                        .border(1.dp, if (active) WarningRed else LightSlateBorder, RoundedCornerShape(6.dp))
                                        .clickable { reportCategory = cat }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 10.sp,
                                        color = if (active) Color.White else TextGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (reportTitle.isBlank() || reportDesc.isBlank()) {
                                Toast.makeText(context, "Please fill in all details.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val loc = viewModel.userLocation.value ?: Pair(28.6139, 77.2090)
                            viewModel.fileReport(
                                title = reportTitle,
                                description = reportDesc,
                                category = reportCategory,
                                lat = loc.first,
                                lng = loc.second
                            )
                            
                            Toast.makeText(context, "Incident filed! +20 Civic Points Earned.", Toast.LENGTH_LONG).show()
                            
                            // Reset form
                            reportTitle = ""
                            reportDesc = ""
                            reportCategory = "SOS Alert"
                            showReportDialog = false
                            
                            // Switch category to show reported list
                            selectedCategory = "Incidents"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Submit Incident", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = false }) {
                        Text("Cancel", color = TextGray)
                    }
                },
                containerColor = CardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}


// ==========================================
// SCREEN 3: BROWSE LAWS (JSON BROWSER)
// ==========================================
@Composable
fun BrowseLawsScreen(viewModel: NyayaViewModel) {
    val lang by viewModel.currentLanguage.collectAsState()
    val legalTopics by viewModel.legalTopics.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var expandedTopicId by remember { mutableStateOf<Int?>(null) }
    
    val filteredTopics = remember(legalTopics, searchQuery) {
        if (searchQuery.isBlank()) {
            legalTopics
        } else {
            legalTopics.filter { topic ->
                topic.title.contains(searchQuery, ignoreCase = true) ||
                topic.category.contains(searchQuery, ignoreCase = true) ||
                topic.summary.contains(searchQuery, ignoreCase = true) ||
                topic.keywords.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
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
            text = I18n.getString("laws_subtitle", lang),
            color = TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(I18n.getString("laws_search", lang), color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = TextGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextGray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextDarkSlate,
                unfocusedTextColor = TextDarkSlate,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = JusticeBlue,
                unfocusedBorderColor = LightSlateBorder
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("directory_search_bar")
        )

        if (filteredTopics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FindInPage,
                        contentDescription = "Not found",
                        tint = TextGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching laws found in database.",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTopics) { topic ->
                    LawTopicCard(
                        topic = topic,
                        isExpanded = expandedTopicId == topic.id,
                        onExpandClick = {
                            expandedTopicId = if (expandedTopicId == topic.id) null else topic.id
                        }
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
                    color = TextDarkSlate,
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
                Text(if (isCreatingPost) I18n.getString("cancel_button", lang) else I18n.getString("create_post", lang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LightSlateBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Share with the Community", fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = postTitle,
                        onValueChange = { postTitle = it },
                        placeholder = { Text("Enter a descriptive title...", color = TextGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("post_title_input"),
                        textStyle = TextStyle(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JusticeBlue,
                            unfocusedBorderColor = LightSlateBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = postContent,
                        onValueChange = { postContent = it },
                        placeholder = { Text("What legal topics or advice would you like to share or ask?", color = TextGray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("post_content_input"),
                        textStyle = TextStyle(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JusticeBlue,
                            unfocusedBorderColor = LightSlateBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Type Chips selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                        val types = listOf("Discussion" to "💬", "Question" to "❓", "Resource" to "🛡️")
                        types.forEach { (type, emoji) ->
                            val isSelected = postType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { postType = type },
                                label = { Text("$emoji $type", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JusticeBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F3F5),
                                    labelColor = TextDarkSlate
                                ),
                                border = null,
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
        if (forumPosts.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Chat, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Be the first to start a conversation!", color = TextGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(forumPosts) { post ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forum_post_${post.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(JusticeBlue, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = post.authorName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = post.authorName,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDarkSlate,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = post.authorRole,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                // Category Badge
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (post.postType) {
                                            "Resource" -> Color(0xFFE8F5E9)
                                            "Question" -> Color(0xFFFFF3E0)
                                            else -> Color(0xFFE3F2FD)
                                        }
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = post.postType.uppercase(),
                                        color = when (post.postType) {
                                            "Resource" -> Color(0xFF2E7D32)
                                            "Question" -> Color(0xFFE65100)
                                            else -> Color(0xFF1565C0)
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Post Title & Content
                            Text(text = post.title, fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = post.content, color = TextGray, fontSize = 12.sp, lineHeight = 16.sp)

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = LightSlateBorder)
                            Spacer(modifier = Modifier.height(4.dp))

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
                                        Text("${post.upvotes} Upvotes", fontSize = 11.sp, color = if (post.isLikedByMe) WarningRed else TextGray)
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
                                            tint = TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reply & Earn (+10)", fontSize = 11.sp, color = TextGray)
                                    }
                                }
                            }

                            // Inline comments section
                            if (expandedPostId == post.id) {
                                val comments by viewModel.getComments(post.id).collectAsState(initial = emptyList())
                                var commentText by remember { mutableStateOf("") }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                    border = BorderStroke(1.dp, Color(0xFFE9ECEF))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Discussion Replies", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        comments.forEach { comment ->
                                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = JusticeBlue)
                                                    Text("•", fontSize = 10.sp, color = Color.Gray)
                                                    Text("Citizen", fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                                }
                                                Text(comment.content, fontSize = 11.sp, color = TextDarkSlate)
                                                HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(top = 4.dp))
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
                                                placeholder = { Text("Write a supportive response...", fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f).height(44.dp).testTag("comment_input_${post.id}"),
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = JusticeBlue,
                                                    unfocusedBorderColor = LightSlateBorder,
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
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
                        label = { Text("Full Name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                        textStyle = TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address", fontSize = 11.sp) },
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
                            Text("Cancel", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.updateProfileName(editName, editEmail)
                                isEditingName = false
                                Toast.makeText(context, "Identity updated successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text("Save", fontSize = 11.sp, color = Color.White)
                        }
                    }
                } else {
                    Text(text = userProfile.name, fontWeight = FontWeight.Bold, color = TextDarkSlate, fontSize = 18.sp)
                    Text(text = userProfile.email, color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = { isEditingName = true },
                        modifier = Modifier.height(28.dp).testTag("edit_profile_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JusticeBlue),
                        border = BorderStroke(1.dp, JusticeBlue)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Name", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gamification / Rewards Progress Card
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
                        text = "${userProfile.points} pts",
                        fontWeight = FontWeight.Black,
                        color = JusticeBlue,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress to next milestone
                val nextMilestone = when {
                    userProfile.points < 50 -> "Community Helper" to 50
                    userProfile.points < 100 -> "Top Contributor" to 100
                    userProfile.points < 200 -> "Legal Expert" to 200
                    else -> "Supreme Nyaya Guide" to 500
                }
                
                val progressFraction = (userProfile.points.toFloat() / nextMilestone.second).coerceIn(0f, 1f)
                val pointsNeeded = (nextMilestone.second - userProfile.points).coerceAtLeast(0)

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

        // Language Preferences Settings Card
        Text(
            text = I18n.getString("select_language", lang),
            fontWeight = FontWeight.Bold,
            color = TextDarkSlate,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("language_settings_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Interface Language",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkSlate
                )
                Text(
                    text = "Switch language to translate all screens and AI guidelines.",
                    fontSize = 11.sp,
                    color = TextGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                var dropdownOpen by remember { mutableStateOf(false) }
                val supportedLanguages = listOf("English", "Tamil", "Hindi", "Telugu", "Kannada", "Malayalam")

                Box {
                    OutlinedButton(
                        onClick = { dropdownOpen = true },
                        modifier = Modifier.fillMaxWidth().testTag("profile_lang_selector"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JusticeBlue),
                        border = BorderStroke(1.dp, JusticeBlue)
                    ) {
                        Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lang, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(
                        expanded = dropdownOpen,
                        onDismissRequest = { dropdownOpen = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        supportedLanguages.forEach { languageName ->
                            val dispName = when(languageName) {
                                "English" -> "English"
                                "Tamil" -> "தமிழ்"
                                "Hindi" -> "हिन्दी"
                                "Telugu" -> "తెలుగు"
                                "Kannada" -> "ಕನ್ನಡ"
                                "Malayalam" -> "മലയാളም"
                                else -> languageName
                            }
                            DropdownMenuItem(
                                text = { Text(dispName, color = TextDarkSlate, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    viewModel.selectLanguage(languageName)
                                    dropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }
        
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
                text = "Logout Secure Session",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
