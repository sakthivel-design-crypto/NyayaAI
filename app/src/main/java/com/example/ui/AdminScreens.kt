package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.*
import com.example.model.LegalTopic
import com.example.viewmodel.NyayaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Helper Composable for Statistic Card
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    icon: @Composable () -> Unit,
    color: Color = AccentOrange,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metric_card_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtext, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Redesigned System Admin Dashboard screen
@Composable
fun AdminOverviewScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Realtime Activity Log Listener Lifecycle
    DisposableEffect(viewModel) {
        val listenerRegistration = viewModel.startListeningToActivityLogs()
        onDispose {
            listenerRegistration?.remove()
        }
    }
    
    // DB flows
    val allAccounts by viewModel.allUserAccounts.collectAsState()
    val allComplaints by viewModel.allComplaints.collectAsState()
    val allRatings by viewModel.appRatings.collectAsState()
    val userFeedbacks by viewModel.userFeedbacks.collectAsState()
    val allLaws by viewModel.allLaws.collectAsState()
    val systemActivityLogs by viewModel.systemActivityLogs.collectAsState()
    val allAuditLogs by viewModel.allAuditLogs.collectAsState()
    
    // Internal Navigation State: null = dashboard, otherwise detail pages
    var activeDetailTab by remember { mutableStateOf<String?>(null) }

    // Statistics calculations
    val totalUsers = allAccounts.size
    val totalCitizens = allAccounts.count { it.role == "Citizen" }
    val totalAuthorities = allAccounts.count { it.role == "Authority" }
    
    val totalComplaintsCount = allComplaints.size
    val pendingComplaints = allComplaints.count { it.status == "Submitted" }
    val resolvedComplaints = allComplaints.count { it.status == "Resolved" }
    val activeComplaints = totalComplaintsCount - resolvedComplaints - pendingComplaints

    // Dynamic AI Accuracy & Rating calculations
    val ratingValues = remember(allRatings, userFeedbacks) {
        val validAppRatings = allRatings.map { it.rating }.filter { it in 1..5 }
        val validFbRatings = userFeedbacks.map { it.rating }.filter { it in 1..5 }
        (validAppRatings + validFbRatings).map { it.toDouble() }
    }
    val totalRatingsCount = allRatings.size
    val totalFeedbackCount = userFeedbacks.size
    val avgRating = if (ratingValues.isNotEmpty()) ratingValues.average() else 0.0
    val aiQualityScore = if (ratingValues.isNotEmpty()) ((avgRating / 5.0) * 100.0).toInt() else 0

    // Combined Activity Logs (Realtime Firestore activity logs + actual database events)
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val combinedActivityLogs = remember(systemActivityLogs, userFeedbacks, allComplaints, allLaws, allAuditLogs) {
        val list = mutableListOf<com.example.db.ActivityLog>()
        list.addAll(systemActivityLogs)

        userFeedbacks.forEach { fb ->
            val roleCap = if (fb.role.equals("Authority", ignoreCase = true)) "Authority" else "Citizen"
            list.add(
                com.example.db.ActivityLog(
                    id = "fb_${fb.feedbackId}",
                    timestamp = fb.createdAt,
                    actorRole = roleCap,
                    actorName = fb.userName.ifBlank { fb.email },
                    eventType = "FEEDBACK_SUBMITTED",
                    message = "Feedback submitted: ${fb.category} (${if (fb.rating in 1..5) "${fb.rating}★" else "Unrated"})",
                    relatedId = fb.feedbackId
                )
            )
            if (fb.adminReply.isNotBlank() && fb.repliedAt > 0) {
                list.add(
                    com.example.db.ActivityLog(
                        id = "fb_reply_${fb.feedbackId}",
                        timestamp = fb.repliedAt,
                        actorRole = "Admin",
                        actorName = fb.adminName.ifBlank { "System Admin" },
                        eventType = "FEEDBACK_REPLIED",
                        message = "Replied to feedback by ${fb.userName}",
                        relatedId = fb.feedbackId
                    )
                )
            }
        }

        allComplaints.forEach { c ->
            list.add(
                com.example.db.ActivityLog(
                    id = "cmp_${c.id}",
                    timestamp = c.timestamp,
                    actorRole = "Citizen",
                    actorName = c.citizenName.ifBlank { "Citizen" },
                    eventType = "COMPLAINT_SUBMITTED",
                    message = "Grievance complaint filed #${c.id.takeLast(6)}",
                    relatedId = c.id
                )
            )
        }

        allAuditLogs.forEach { audit ->
            list.add(
                com.example.db.ActivityLog(
                    id = "audit_${audit.id}",
                    timestamp = audit.timestamp,
                    actorRole = "Authority",
                    actorName = audit.officerName,
                    eventType = "STATUS_CHANGED",
                    message = "Complaint #${audit.complaintId.takeLast(6)} status changed to ${audit.newStatus}",
                    relatedId = audit.complaintId
                )
            )
        }

        allLaws.forEach { law ->
            list.add(
                com.example.db.ActivityLog(
                    id = "law_${law.lawId}",
                    timestamp = law.updatedAt,
                    actorRole = "Admin",
                    actorName = "Legal Ops",
                    eventType = "LAW_PUBLISHED",
                    message = "Law record updated: ${law.title.take(30)}",
                    relatedId = law.lawId
                )
            )
        }

        list.distinctBy { it.id }.sortedByDescending { it.timestamp }.take(50)
    }

    // Handle nested sub-pages
    if (activeDetailTab != null) {
        when (activeDetailTab) {
            "CITIZENS" -> {
                AdminCitizensDetailScreen(
                    citizens = allAccounts.filter { it.role == "Citizen" },
                    onBack = { activeDetailTab = null }
                )
            }
            "AUTHORITIES" -> {
                AdminAuthoritiesDetailScreen(
                    viewModel = viewModel,
                    authorities = allAccounts.filter { it.role == "Authority" },
                    allComplaints = allComplaints,
                    onBack = { activeDetailTab = null }
                )
            }
            "COMPLAINTS" -> {
                AdminComplaintsDetailScreen(
                    complaints = allComplaints,
                    onBack = { activeDetailTab = null }
                )
            }
            "AI" -> {
                AdminAnalyticsDetailScreen(
                    viewModel = viewModel,
                    onBack = { activeDetailTab = null }
                )
            }
        }
        return
    }

    // Default Dashboard overview layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Admin Nodes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
                Text(
                    text = "Real-Time India Legal & Safety Operations Hub",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }
            IconButton(
                onClick = { Toast.makeText(context, "Handshake verified with India Central Nodes", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.border(1.dp, LightSlateBorder, CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Nodes", tint = Color.White)
            }
        }

        // Metrics Grid (Reflowed 2x2 + full row)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "Total Citizens",
                    value = totalCitizens.toString(),
                    subtext = "Active Citizen Nodes",
                    icon = { Icon(Icons.Default.People, contentDescription = null, tint = AccentOrange) },
                    onClick = { activeDetailTab = "CITIZENS" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "Authority Nodes",
                    value = totalAuthorities.toString(),
                    subtext = "Active Officer Nodes",
                    icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = LightJusticeBlue) },
                    onClick = { activeDetailTab = "AUTHORITIES" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "Grievance Cases",
                    value = totalComplaintsCount.toString(),
                    subtext = "$pendingComplaints Pending / $resolvedComplaints Resolved",
                    icon = { Icon(Icons.Default.Assignment, contentDescription = null, tint = Color.White) },
                    onClick = { activeDetailTab = "COMPLAINTS" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "AI Accuracy Audit",
                    value = "${String.format("%.1f", avgRating)} / 5 ($aiQualityScore%)",
                    subtext = "Ratings: $totalRatingsCount | Feedback: $totalFeedbackCount",
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = SuccessGreen) },
                    color = SuccessGreen,
                    onClick = { activeDetailTab = "AI" }
                )
            }
        }

        MetricCard(
            title = "Legal Dataset Size",
            value = "${allLaws.size} Laws",
            subtext = "Centralized Firestore Collection",
            icon = { Icon(Icons.Default.Book, contentDescription = null, tint = AccentOrange) },
            onClick = { Toast.makeText(context, "Manage dataset in Dataset tab above", Toast.LENGTH_SHORT).show() }
        )

        // Live Health Terminal Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Node Activity logs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("REALTIME SYNC", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .background(Color.Black)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (combinedActivityLogs.isEmpty()) {
                        Text(
                            text = "[SYSTEM] Realtime audit node active. Awaiting user & system activity events...",
                            color = TextGray,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    } else {
                        combinedActivityLogs.forEach { log ->
                            val timeStr = sdf.format(Date(log.timestamp))
                            val roleDisplay = when (log.actorRole) {
                                "Authority" -> "Authority"
                                "Admin" -> "Admin"
                                "System" -> "System"
                                else -> "Citizen"
                            }
                            val nameTag = if (log.actorName.isNotBlank()) " (${log.actorName})" else ""
                            TerminalLogText("[$timeStr] $roleDisplay$nameTag — ${log.message}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogText(text: String) {
    Text(
        text = text,
        color = SuccessGreen,
        fontSize = 11.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

// ---------------- CITIZENS DETAIL SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCitizensDetailScreen(
    citizens: List<UserAccount>,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Name (A-Z)") }
    
    var pageNumber by remember { mutableStateOf(1) }
    val recordsPerPage = 5

    // Filtering & Sorting
    val filtered = citizens.filter {
        val matchesSearch = it.name.lowercase().contains(searchQuery.lowercase().trim()) || it.email.lowercase().contains(searchQuery.lowercase().trim())
        val matchesStatus = if (filterStatus == "Active") !it.isDisabled else if (filterStatus == "Disabled") it.isDisabled else true
        matchesSearch && matchesStatus
    }.sortedWith { a, b ->
        if (sortBy == "Name (A-Z)") a.name.lowercase().compareTo(b.name.lowercase()) else b.name.lowercase().compareTo(a.name.lowercase())
    }

    // Pagination
    val totalPages = maxOf(1, (filtered.size + recordsPerPage - 1) / recordsPerPage)
    val paginated = filtered.drop((pageNumber - 1) * recordsPerPage).take(recordsPerPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        // Top Navigation
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Citizens Node Index", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid indicators
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallIndicatorCard(title = "Total Citizen Nodes", value = citizens.size.toString(), modifier = Modifier.weight(1f))
            SmallIndicatorCard(title = "Active Nodes", value = citizens.count { !it.isDisabled }.toString(), color = SuccessGreen, modifier = Modifier.weight(1f))
            SmallIndicatorCard(title = "Suspended", value = citizens.count { it.isDisabled }.toString(), color = WarningRed, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search + Filters
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; pageNumber = 1 },
            placeholder = { Text("Search by name, email...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = LightSlateBorder,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Filter
            Box(modifier = Modifier.weight(1f)) {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text("Status: $filterStatus", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("All", "Active", "Disabled").forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { filterStatus = it; expanded = false; pageNumber = 1 })
                    }
                }
            }
            // Sort
            Box(modifier = Modifier.weight(1f)) {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text("Sort: $sortBy", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("Name (A-Z)", "Name (Z-A)").forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { sortBy = it; expanded = false; pageNumber = 1 })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Table List
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (paginated.isEmpty()) {
                item {
                    Text("No records matched filters.", color = TextGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(paginated) { user ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(user.email, color = TextGray, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (user.isDisabled) WarningRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (user.isDisabled) "DISABLED" else "ACTIVE",
                                    color = if (user.isDisabled) WarningRed else SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pagination row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page $pageNumber of $totalPages (${filtered.size} records)", color = TextGray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (pageNumber > 1) pageNumber-- },
                    enabled = pageNumber > 1,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Prev")
                }
                OutlinedButton(
                    onClick = { if (pageNumber < totalPages) pageNumber++ },
                    enabled = pageNumber < totalPages,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
fun SmallIndicatorCard(title: String, value: String, color: Color = AccentOrange, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ---------------- AUTHORITIES MANAGEMENT DETAIL SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuthoritiesDetailScreen(
    viewModel: NyayaViewModel,
    authorities: List<UserAccount>,
    allComplaints: List<CitizenComplaint>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterDept by remember { mutableStateOf("All") }
    var filterStatus by remember { mutableStateOf("All") } // "All", "Pending", "Approved", "Rejected"
    
    // Add/Edit forms
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedForEdit by remember { mutableStateOf<UserAccount?>(null) }
    var previewProofOfficer by remember { mutableStateOf<UserAccount?>(null) }
    
    // Pagination
    var pageNumber by remember { mutableStateOf(1) }
    val recordsPerPage = 5

    // Filters
    val filtered = authorities.filter {
        val matchesSearch = it.name.lowercase().contains(searchQuery.lowercase().trim()) || 
            it.email.lowercase().contains(searchQuery.lowercase().trim()) ||
            it.employeeId.lowercase().contains(searchQuery.lowercase().trim())
        val matchesDept = filterDept == "All" || it.department.equals(filterDept, ignoreCase = true)
        val matchesStatus = when (filterStatus) {
            "Pending" -> it.approvalStatus == "PENDING_VERIFICATION" || it.approvalStatus == "PENDING_APPROVAL" || (!it.isApproved && it.approvalStatus != "REJECTED")
            "Approved" -> it.approvalStatus == "APPROVED" || it.isApproved
            "Rejected" -> it.approvalStatus == "REJECTED"
            else -> true
        }
        matchesSearch && matchesDept && matchesStatus
    }

    val totalPages = maxOf(1, (filtered.size + recordsPerPage - 1) / recordsPerPage)
    val paginated = filtered.drop((pageNumber - 1) * recordsPerPage).take(recordsPerPage)

    // Proof Document Fullscreen Preview Modal Dialog with Zoom & Fit
    if (previewProofOfficer != null) {
        val officer = previewProofOfficer!!
        FullScreenProofViewerDialog(
            account = officer,
            onDismiss = { previewProofOfficer = null },
            onActivate = {
                viewModel.activateAuthorityAccount(officer.email) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDisable = {
                viewModel.disableAuthorityAccount(officer.email) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Ledger Authorities & Verification", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action row
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Register New Authority Node", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Pending", "Approved", "Rejected").forEach { statusTab ->
                val isSelected = filterStatus == statusTab
                Surface(
                    onClick = { filterStatus = statusTab; pageNumber = 1 },
                    color = if (isSelected) AccentOrange else CardBackground,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) AccentOrange else LightSlateBorder)
                ) {
                    val count = when (statusTab) {
                        "Pending" -> authorities.count { it.approvalStatus == "PENDING_VERIFICATION" || it.approvalStatus == "PENDING_APPROVAL" || (!it.isApproved && it.approvalStatus != "REJECTED") }
                        "Approved" -> authorities.count { it.approvalStatus == "APPROVED" || it.isApproved }
                        "Rejected" -> authorities.count { it.approvalStatus == "REJECTED" }
                        else -> authorities.size
                    }
                    Text(
                        text = "$statusTab ($count)",
                        color = if (isSelected) Color.Black else TextDarkSlate,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; pageNumber = 1 },
            placeholder = { Text("Search authorities by name, email, ID...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = LightSlateBorder,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // List
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(paginated) { officer ->
                // Calculate dynamic metrics
                val assignedCount = allComplaints.count { it.assignedOfficer.equals(officer.name, ignoreCase = true) }
                val resolvedCount = allComplaints.count { it.assignedOfficer.equals(officer.name, ignoreCase = true) && it.status == "Resolved" }

                val isApproved = officer.approvalStatus == "APPROVED" || officer.isApproved
                val isRejected = officer.approvalStatus == "REJECTED"
                val isPending = officer.approvalStatus == "PENDING_VERIFICATION" || officer.approvalStatus == "PENDING_APPROVAL" || (!isApproved && !isRejected)
                val isBlocked = officer.isDisabled || officer.status.equals("Disabled", ignoreCase = true)

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, when {
                        isPending -> AccentOrange
                        isApproved -> SuccessGreen.copy(alpha = 0.6f)
                        isRejected || isBlocked -> WarningRed.copy(alpha = 0.6f)
                        else -> LightSlateBorder
                    }),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(officer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    // Status Badge
                                    Surface(
                                        color = when {
                                            isBlocked -> WarningRed.copy(alpha = 0.2f)
                                            isPending -> AccentOrange.copy(alpha = 0.2f)
                                            isApproved -> SuccessGreen.copy(alpha = 0.2f)
                                            isRejected -> WarningRed.copy(alpha = 0.2f)
                                            else -> LightJusticeBlue.copy(alpha = 0.2f)
                                        },
                                        border = BorderStroke(1.dp, when {
                                            isBlocked -> WarningRed
                                            isPending -> AccentOrange
                                            isApproved -> SuccessGreen
                                            isRejected -> WarningRed
                                            else -> LightJusticeBlue
                                        }),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isBlocked -> "DISABLED"
                                                isPending -> "PENDING VERIFICATION"
                                                isApproved -> "APPROVED"
                                                isRejected -> "REJECTED"
                                                else -> officer.approvalStatus.ifEmpty { "ACTIVE" }
                                            },
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isBlocked -> WarningRed
                                                isPending -> AccentOrange
                                                isApproved -> SuccessGreen
                                                isRejected -> WarningRed
                                                else -> LightJusticeBlue
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(officer.email, color = TextGray, fontSize = 11.sp)
                                val regDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(if (officer.createdAt > 0) officer.createdAt else System.currentTimeMillis()))
                                val regTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(if (officer.createdAt > 0) officer.createdAt else System.currentTimeMillis()))
                                Text("Registered: $regDateStr at $regTimeStr", color = TextGray, fontSize = 10.sp)
                                if (officer.employeeId.isNotBlank()) {
                                    Text("Emp ID: ${officer.employeeId}", color = AccentOrange, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(onClick = { selectedForEdit = officer }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AccentOrange)
                            }
                        }

                        Divider(color = LightSlateBorder)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Department", color = TextGray, fontSize = 9.sp)
                                Text(officer.department, color = LightJusticeBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Designation", color = TextGray, fontSize = 9.sp)
                                Text(officer.designation.ifEmpty { "Officer" }, color = Color.White, fontSize = 11.sp)
                            }
                            Column {
                                Text("District", color = TextGray, fontSize = 9.sp)
                                Text(officer.district.ifEmpty { "National" }, color = Color.White, fontSize = 11.sp)
                            }
                        }

                        // Staff Proof Section
                        if (officer.proofImage.isNotBlank()) {
                            Surface(
                                color = DarkIndigo.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, LightSlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Mini thumbnail
                                        val miniBitmap = remember(officer.proofImage) {
                                            try {
                                                if (officer.proofImage.startsWith("data:image")) {
                                                    val base64Data = officer.proofImage.substringAfter("base64,")
                                                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                                } else {
                                                    null
                                                }
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (miniBitmap != null) {
                                            Image(
                                                bitmap = miniBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Badge, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(28.dp))
                                        }
                                        Column {
                                            Text("Staff Proof Document", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("Official Government Proof Image", color = TextGray, fontSize = 9.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { previewProofOfficer = officer },
                                        border = BorderStroke(1.dp, AccentOrange),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("View Proof", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (officer.rejectionReason.isNotBlank()) {
                            Text("Rejection Note: ${officer.rejectionReason}", color = WarningRed, fontSize = 10.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Assigned Cases: $assignedCount", color = TextGray, fontSize = 10.sp)
                            Text("Resolved: $resolvedCount", color = SuccessGreen, fontSize = 10.sp)
                        }

                        // Authority Verification / Approval Admin Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isApproved) {
                                Button(
                                    onClick = {
                                        viewModel.approveAuthorityAccount(officer.email) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(32.dp).testTag("approve_authority_${officer.email}")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!isRejected) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.rejectAuthorityAccount(officer.email, "Verification rejected by Administrator") { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, WarningRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(32.dp).testTag("reject_authority_${officer.email}")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = WarningRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject / Cancel", color = WarningRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (officer.isDisabled) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.reEnableAuthorityAccount(officer.email) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, LightJusticeBlue),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LightJusticeBlue),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Re-enable", fontSize = 10.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disableAuthorityAccount(officer.email) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, TextGray),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Disable", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pagination
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page $pageNumber of $totalPages", color = TextGray, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { if (pageNumber > 1) pageNumber-- }, enabled = pageNumber > 1) { Text("Prev") }
                OutlinedButton(onClick = { if (pageNumber < totalPages) pageNumber++ }, enabled = pageNumber < totalPages) { Text("Next") }
            }
        }
    }

    // Register Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var dept by remember { mutableStateOf("Municipal Administration") }
        var district by remember { mutableStateOf("Chennai") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Register Authority", color = Color.White) },
            containerColor = CardBackground,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
                    OutlinedTextField(value = dept, onValueChange = { dept = it }, label = { Text("Department") })
                    OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("District") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "Name, Email and Password are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addAuthorityAccount(name, email, password, dept, district, "") { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Register", color = Color.Black)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Dialog
    if (selectedForEdit != null) {
        val officer = selectedForEdit!!
        var name by remember { mutableStateOf(officer.name) }
        var dept by remember { mutableStateOf(officer.department) }
        var district by remember { mutableStateOf(officer.district) }
        var score by remember { mutableStateOf(officer.performanceScore) }
        var disabled by remember { mutableStateOf(officer.isDisabled) }

        AlertDialog(
            onDismissRequest = { selectedForEdit = null },
            title = { Text("Edit Authority Node", color = Color.White) },
            containerColor = CardBackground,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    OutlinedTextField(value = dept, onValueChange = { dept = it }, label = { Text("Department") })
                    OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("District") })
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Disable Account", color = Color.White)
                        Switch(checked = disabled, onCheckedChange = { disabled = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAuthorityAccount(officer.email, name, dept, district, "", disabled, score) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) selectedForEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedForEdit = null }) { Text("Cancel") }
            }
        )
    }
}

// ---------------- COMPLAINTS LIST DETAIL SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminComplaintsDetailScreen(
    complaints: List<CitizenComplaint>,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("All") }
    var filterPriority by remember { mutableStateOf("All") }
    
    var pageNumber by remember { mutableStateOf(1) }
    val recordsPerPage = 5

    val filtered = complaints.filter {
        val matchesSearch = it.id.lowercase().contains(searchQuery.lowercase().trim()) || it.title.lowercase().contains(searchQuery.lowercase().trim())
        val matchesStatus = filterStatus == "All" || it.status.equals(filterStatus, ignoreCase = true)
        val matchesPriority = filterPriority == "All" || it.priority.equals(filterPriority, ignoreCase = true)
        matchesSearch && matchesStatus && matchesPriority
    }

    val totalPages = maxOf(1, (filtered.size + recordsPerPage - 1) / recordsPerPage)
    val paginated = filtered.drop((pageNumber - 1) * recordsPerPage).take(recordsPerPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Ledger Cases Index", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; pageNumber = 1 },
            placeholder = { Text("Search by ID or Subject...", color = TextGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = LightSlateBorder,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                var exp1 by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { exp1 = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Status: $filterStatus", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = exp1, onDismissRequest = { exp1 = false }) {
                    listOf("All", "Submitted", "Under Review", "Assigned", "In Progress", "Resolved", "Rejected").forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { filterStatus = it; exp1 = false; pageNumber = 1 })
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                var exp2 by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { exp2 = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Priority: $filterPriority", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = exp2, onDismissRequest = { exp2 = false }) {
                    listOf("All", "Critical", "High", "Medium", "Low").forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { filterPriority = it; exp2 = false; pageNumber = 1 })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(paginated) { cmp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, LightSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cmp.id, color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(cmp.priority.uppercase(), color = if (cmp.priority == "Critical") WarningRed else AccentOrange, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text(cmp.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(cmp.description, color = TextGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Reporter: ${if (cmp.isAnonymous) "Anonymous" else cmp.reporterName}", color = TextGray, fontSize = 11.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(JusticeBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(cmp.status, color = JusticeBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Pagination
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page $pageNumber of $totalPages", color = TextGray, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { if (pageNumber > 1) pageNumber-- }, enabled = pageNumber > 1) { Text("Prev") }
                OutlinedButton(onClick = { if (pageNumber < totalPages) pageNumber++ }, enabled = pageNumber < totalPages) { Text("Next") }
            }
        }
    }
}



// ---------------- ANALYTICS DASHBOARD SCREEN ----------------
@Composable
fun AdminAnalyticsDetailScreen(
    viewModel: NyayaViewModel,
    onBack: () -> Unit
) {
    val allRatings by viewModel.appRatings.collectAsState()
    val userFeedbacks by viewModel.userFeedbacks.collectAsState()

    val validFeedbackRatings = remember(userFeedbacks) {
        userFeedbacks.filter { it.rating in 1..5 }
    }
    val validAppRatings = remember(allRatings) {
        allRatings.filter { it.rating in 1..5 }
    }

    val combinedRatingValues = remember(validFeedbackRatings, validAppRatings) {
        validFeedbackRatings.map { it.rating } + validAppRatings.map { it.rating }
    }

    val totalResponses = userFeedbacks.size
    val totalRatedResponses = combinedRatingValues.size

    val averageScore = if (totalRatedResponses > 0) combinedRatingValues.average() else 0.0
    val accuracyPercentage = if (totalRatedResponses > 0) ((averageScore / 5.0) * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("AI Accuracy & Platform Analytics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }

        // Top Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("AI Accuracy Audit", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (totalRatedResponses > 0) {
                        Text("$accuracyPercentage%", color = SuccessGreen, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("Score: ${String.format("%.2f", averageScore)} / 5", color = AccentOrange, fontSize = 10.sp)
                    } else {
                        Text("0%", color = WarningRed, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("No ratings available yet", color = TextGray, fontSize = 10.sp)
                    }
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Total Responses", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$totalResponses", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Citizen: ${userFeedbacks.count { it.role.equals("Citizen", ignoreCase = true) }} | Auth: ${userFeedbacks.count { it.role.equals("Authority", ignoreCase = true) }}", color = LightJusticeBlue, fontSize = 10.sp)
                }
            }
        }

        // A. Overall Rating Trend (Line Chart based on timestamped ratings)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A. Overall Rating Trend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Trajectory of user satisfaction score over time", color = TextGray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val timestampedRatings = remember(userFeedbacks, allRatings) {
                    val list = mutableListOf<Pair<Long, Int>>()
                    userFeedbacks.filter { it.rating in 1..5 }.forEach { list.add(it.createdAt to it.rating) }
                    allRatings.filter { it.rating in 1..5 }.forEach { list.add(it.createdAt to it.rating) }
                    list.sortBy { it.first }
                    list
                }

                if (timestampedRatings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(DarkIndigo.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No rating trend data available yet.", color = TextGray, fontSize = 11.sp)
                    }
                } else {
                    val runningAverages = remember(timestampedRatings) {
                        var sum = 0.0
                        timestampedRatings.mapIndexed { index, pair ->
                            sum += pair.second
                            (sum / (index + 1)).toFloat()
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val points = runningAverages
                        if (points.size == 1) {
                            val y = size.height - ((points[0] - 1.0f) / 4.0f * size.height)
                            drawLine(
                                color = AccentOrange,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawCircle(color = AccentOrange, radius = 6.dp.toPx(), center = Offset(size.width / 2f, y))
                        } else {
                            val widthStep = size.width / (points.size - 1)
                            val maxVal = 5.0f
                            val minVal = 1.0f
                            val path = Path().apply {
                                val firstY = size.height - ((points[0] - minVal) / (maxVal - minVal) * size.height).coerceIn(0f, size.height)
                                moveTo(0f, firstY)
                                for (i in 1 until points.size) {
                                    val x = i * widthStep
                                    val y = size.height - ((points[i] - minVal) / (maxVal - minVal) * size.height).coerceIn(0f, size.height)
                                    lineTo(x, y)
                                }
                            }
                            drawPath(path, color = AccentOrange, style = Stroke(width = 3.dp.toPx()))

                            for (i in points.indices) {
                                val x = i * widthStep
                                val y = size.height - ((points[i] - minVal) / (maxVal - minVal) * size.height).coerceIn(0f, size.height)
                                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = AccentOrange, radius = 2.5.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                        val firstDate = dateFormat.format(Date(timestampedRatings.first().first))
                        val lastDate = dateFormat.format(Date(timestampedRatings.last().first))
                        Text("First: $firstDate (${timestampedRatings.first().second}★)", color = TextGray, fontSize = 9.sp)
                        Text("Latest: $lastDate (${String.format("%.2f", runningAverages.last())} avg)", color = AccentOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // B. Star Rating Distribution (Bar Chart)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("B. Star Rating Distribution", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                (5 downTo 1).forEach { star ->
                    val count = combinedRatingValues.count { it == star }
                    val fraction = if (totalRatedResponses > 0) count.toFloat() / totalRatedResponses else 0f
                    val percentage = if (totalRatedResponses > 0) (fraction * 100).toInt() else 0

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$star ★", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        LinearProgressIndicator(
                            progress = fraction,
                            color = AccentOrange,
                            trackColor = LightSlateBorder,
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text("$count ($percentage%)", color = TextGray, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                    }
                }
            }
        }

        // C. Feedback Category Distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("C. Feedback Category Distribution", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                val categories = listOf("Bug Report", "Feature Request", "Performance", "AI Suggestion", "UI/UX", "Complaint", "Other")
                val totalFb = userFeedbacks.size

                categories.forEach { cat ->
                    val count = userFeedbacks.count { it.category.equals(cat, ignoreCase = true) }
                    val fraction = if (totalFb > 0) count.toFloat() / totalFb else 0f
                    val percentage = if (totalFb > 0) (fraction * 100).toInt() else 0

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cat, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(110.dp))
                        LinearProgressIndicator(
                            progress = fraction,
                            color = LightJusticeBlue,
                            trackColor = LightSlateBorder,
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text("$count ($percentage%)", color = TextGray, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                    }
                }
            }
        }

        // D. Daily Feedback Trend
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("D. Daily Feedback Trend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Volume of feedback received per day", color = TextGray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val dailyCounts = remember(userFeedbacks) {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    val map = mutableMapOf<String, Int>()
                    val sortedList = userFeedbacks.sortedBy { it.createdAt }
                    sortedList.forEach { fb ->
                        val dateStr = sdf.format(Date(fb.createdAt))
                        map[dateStr] = (map[dateStr] ?: 0) + 1
                    }
                    map.toList()
                }

                if (dailyCounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(DarkIndigo.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No daily feedback trend data available yet.", color = TextGray, fontSize = 11.sp)
                    }
                } else {
                    val maxVal = maxOf(1f, dailyCounts.maxOf { it.second }.toFloat())
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        val points = dailyCounts.map { it.second.toFloat() }
                        if (points.size == 1) {
                            val y = size.height - (points[0] / maxVal * size.height * 0.8f)
                            drawLine(
                                color = SuccessGreen,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawCircle(color = SuccessGreen, radius = 6.dp.toPx(), center = Offset(size.width / 2f, y))
                        } else {
                            val widthStep = size.width / (points.size - 1)
                            val path = Path().apply {
                                val firstY = size.height - (points[0] / maxVal * size.height * 0.8f)
                                moveTo(0f, firstY)
                                for (i in 1 until points.size) {
                                    val x = i * widthStep
                                    val y = size.height - (points[i] / maxVal * size.height * 0.8f)
                                    lineTo(x, y)
                                }
                            }
                            drawPath(path, color = SuccessGreen, style = Stroke(width = 3.dp.toPx()))

                            for (i in points.indices) {
                                val x = i * widthStep
                                val y = size.height - (points[i] / maxVal * size.height * 0.8f)
                                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = SuccessGreen, radius = 2.5.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("First: ${dailyCounts.first().first} (${dailyCounts.first().second})", color = TextGray, fontSize = 9.sp)
                        Text("Latest: ${dailyCounts.last().first} (${dailyCounts.last().second} items)", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // E. Citizen vs Authority Feedback
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("E. Citizen vs Authority Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                val citizenFbCount = userFeedbacks.count { it.role.equals("Citizen", ignoreCase = true) }
                val authorityFbCount = userFeedbacks.count { it.role.equals("Authority", ignoreCase = true) }
                val totalRoles = citizenFbCount + authorityFbCount

                val citizenFrac = if (totalRoles > 0) citizenFbCount.toFloat() / totalRoles else 0f
                val authorityFrac = if (totalRoles > 0) authorityFbCount.toFloat() / totalRoles else 0f
                val citizenPercent = if (totalRoles > 0) (citizenFrac * 100).toInt() else 0
                val authorityPercent = if (totalRoles > 0) (100 - citizenPercent) else 0

                if (totalRoles == 0) {
                    Text("No feedback data available yet.", color = TextGray, fontSize = 11.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Citizen: $citizenFbCount ($citizenPercent%)", color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Authority: $authorityFbCount ($authorityPercent%)", color = LightJusticeBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                        if (citizenFrac > 0) {
                            Box(modifier = Modifier.weight(citizenFrac).fillMaxHeight().background(AccentOrange))
                        }
                        if (authorityFrac > 0) {
                            Box(modifier = Modifier.weight(authorityFrac).fillMaxHeight().background(LightJusticeBlue))
                        }
                    }
                }
            }
        }

        // F. Real-Time AI & System Activity Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("F. Real-Time AI Feedback Logs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("LIVE AUDIT", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val logList = remember(userFeedbacks) {
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        userFeedbacks.sortedByDescending { it.createdAt }.take(8).map { fb ->
                            val timeStr = sdf.format(Date(fb.createdAt))
                            val roleCapitalized = if (fb.role.equals("Authority", ignoreCase = true)) "Authority" else "Citizen"
                            "[$timeStr] $roleCapitalized feedback submitted | Category: ${fb.category} | Rating: ${if (fb.rating in 1..5) "${fb.rating}★" else "Unrated"}"
                        }
                    }

                    if (logList.isEmpty()) {
                        TerminalLogText("[SYSTEM] No user feedback submitted yet.")
                    } else {
                        logList.forEach { logText ->
                            TerminalLogText(logText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenProofViewerDialog(
    account: UserAccount,
    onDismiss: () -> Unit,
    onActivate: () -> Unit = {},
    onDisable: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = LightJusticeBlue.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = LightJusticeBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "Official Government Staff Proof",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${account.name} • ${account.department.ifEmpty { "Authority" }} • ${account.designation.ifEmpty { "Officer" }}",
                                color = TextGray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_proof_viewer")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zoom Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { scale = (scale / 1.25f).coerceAtLeast(0.5f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = "${(scale * 100).toInt()}%",
                                color = AccentOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = { scale = (scale * 1.25f).coerceAtMost(5f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            HorizontalDivider(modifier = Modifier.width(1.dp).height(18.dp), color = LightSlateBorder)

                            TextButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Fit Screen", fontSize = 11.sp, color = LightJusticeBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Image display container with pinch to zoom & pan
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, LightSlateBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset = if (scale > 1f) {
                                    Offset(offset.x + pan.x, offset.y + pan.y)
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (proofBitmap != null) {
                        Image(
                            bitmap = proofBitmap.asImageBitmap(),
                            contentDescription = "Full-Screen Official Government Staff Proof",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } else if (account.proofImage.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(48.dp))
                            Text("Official Proof Record Attached", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(account.proofImage.take(120), color = TextGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                            Text("Official Proof Not Available", color = TextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("No government staff proof document was uploaded for this record.", color = TextGray.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Bar inside Fullscreen Viewer
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LightSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onActivate()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).testTag("fullscreen_activate_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Activate", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDisable()
                                onDismiss()
                            },
                            border = BorderStroke(1.dp, AccentOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).testTag("fullscreen_disable_button")
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disable", color = AccentOrange, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, LightSlateBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Close", color = TextGray)
                        }
                    }
                }
            }
        }
    }
}
