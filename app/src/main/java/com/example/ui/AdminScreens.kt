package com.example.ui

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
    
    // DB flows
    val allAccounts by viewModel.allUserAccounts.collectAsState()
    val allComplaints by viewModel.allComplaints.collectAsState()
    val allReports by viewModel.allReports.collectAsState() // SOS category
    val allFeedbacks by viewModel.feedbacks.collectAsState()
    
    // Internal Navigation State: null = dashboard, otherwise detail pages
    var activeDetailTab by remember { mutableStateOf<String?>(null) }
    
    // Export simulation state
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var exportStage by remember { mutableStateOf("") }

    // Statistics calculations
    val totalUsers = allAccounts.size
    val totalCitizens = allAccounts.count { it.role == "Citizen" }
    val totalAuthorities = allAccounts.count { it.role == "Authority" }
    
    val totalComplaintsCount = allComplaints.size
    val pendingComplaints = allComplaints.count { it.status == "Submitted" }
    val resolvedComplaints = allComplaints.count { it.status == "Resolved" }
    val activeComplaints = totalComplaintsCount - resolvedComplaints - pendingComplaints
    
    // SOS alerts mapped from reports (IncidentReport with category SOS)
    val sosAlerts = allReports.filter { it.category == "SOS Alert" || it.title.lowercase().contains("sos") }
    val activeSosCount = sosAlerts.count { it.status == "Pending" || it.status == "In Investigation" }

    // AI calculations
    val totalAiQueries = allFeedbacks.size
    val helpfulCount = allFeedbacks.count { it.isHelpful }
    val aiQualityScore = if (totalAiQueries > 0) {
        val averageStars = allFeedbacks.map { it.starRating }.average()
        (averageStars / 5.0 * 100.0).toInt()
    } else 92

    // Functions to run simulation
    fun startExportSimulation(reportName: String) {
        scope.launch {
            exportProgress = 0f
            exportStage = "Initializing secure node handshake..."
            delay(800)
            exportProgress = 0.25f
            exportStage = "Compiling ledger records for $reportName..."
            delay(1000)
            exportProgress = 0.6f
            exportStage = "Structuring PDF / Excel visual grids..."
            delay(800)
            exportProgress = 0.85f
            exportStage = "Encrypting generated node document..."
            delay(700)
            exportProgress = 1.0f
            exportStage = "Transmission complete!"
            delay(500)
            exportProgress = null
            Toast.makeText(context, "Export of '$reportName' completed successfully!", Toast.LENGTH_LONG).show()
        }
    }

    if (exportProgress != null) {
        // Export Progress Dialog overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentOrange)
                    Text("Secure Ledger Export", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    LinearProgressIndicator(
                        progress = exportProgress ?: 0f,
                        color = AccentOrange,
                        trackColor = LightSlateBorder,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = exportStage,
                        color = TextGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Handle nested sub-pages
    if (activeDetailTab != null) {
        when (activeDetailTab) {
            "CITIZENS" -> {
                AdminCitizensDetailScreen(
                    citizens = allAccounts.filter { it.role == "Citizen" },
                    onBack = { activeDetailTab = null },
                    onExport = { startExportSimulation("Citizen Node Directory") }
                )
            }
            "AUTHORITIES" -> {
                AdminAuthoritiesDetailScreen(
                    viewModel = viewModel,
                    authorities = allAccounts.filter { it.role == "Authority" },
                    allComplaints = allComplaints,
                    onBack = { activeDetailTab = null },
                    onExport = { startExportSimulation("Ledger Authority Roster") }
                )
            }
            "COMPLAINTS" -> {
                AdminComplaintsDetailScreen(
                    complaints = allComplaints,
                    onBack = { activeDetailTab = null },
                    onExport = { startExportSimulation("Filed Grievance Ledger") }
                )
            }
            "SOS" -> {
                AdminSosDetailScreen(
                    viewModel = viewModel,
                    sosList = sosAlerts,
                    onBack = { activeDetailTab = null },
                    onExport = { startExportSimulation("SOS Emergency Activity Log") }
                )
            }
            "AI" -> {
                AdminAiQualityDetailScreen(
                    feedbacks = allFeedbacks,
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
            .padding(16.dp)
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

        // Metrics Grid (2x2 or responsive)
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
                    title = "Emergency SOS",
                    value = activeSosCount.toString(),
                    subtext = "$activeSosCount Unresolved Pings",
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningRed) },
                    color = WarningRed,
                    onClick = { activeDetailTab = "SOS" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "AI Accuracy Audit",
                    value = "$aiQualityScore%",
                    subtext = "Based on $totalAiQueries ratings",
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = SuccessGreen) },
                    color = SuccessGreen,
                    onClick = { activeDetailTab = "AI" }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                MetricCard(
                    title = "Legal Dataset Size",
                    value = "1.4K Documents",
                    subtext = "Nyaya Knowledgebase",
                    icon = { Icon(Icons.Default.Book, contentDescription = null, tint = AccentOrange) },
                    onClick = { Toast.makeText(context, "Manage dataset in Dataset tab above", Toast.LENGTH_SHORT).show() }
                )
            }
        }

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
                        Text("SERVER ONLINE", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Divider(color = LightSlateBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    TerminalLogText("[$timestamp] - Handshake successful with District Gwalior Central.")
                    TerminalLogText("[$timestamp] - Automated AI Classification triggered for CMP-12948.")
                    TerminalLogText("[$timestamp] - Cryptographic keys re-synced with 8 Local Authorities.")
                    TerminalLogText("[$timestamp] - Knowledge base embedding density verified (99.8%).")
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
    onBack: () -> Unit,
    onExport: () -> Unit
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
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange),
                border = BorderStroke(1.dp, AccentOrange)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export PDF", fontSize = 11.sp)
            }
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
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterDept by remember { mutableStateOf("All") }
    
    // Add/Edit forms
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedForEdit by remember { mutableStateOf<UserAccount?>(null) }
    
    // Pagination
    var pageNumber by remember { mutableStateOf(1) }
    val recordsPerPage = 5

    // Filters
    val filtered = authorities.filter {
        val matchesSearch = it.name.lowercase().contains(searchQuery.lowercase().trim()) || it.email.lowercase().contains(searchQuery.lowercase().trim())
        val matchesDept = filterDept == "All" || it.department.equals(filterDept, ignoreCase = true)
        matchesSearch && matchesDept
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
            Text("Ledger Authorities", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange),
                border = BorderStroke(1.dp, AccentOrange)
            ) {
                Text("Export PDF", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; pageNumber = 1 },
            placeholder = { Text("Search authorities...", color = TextGray) },
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

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, LightSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(officer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(officer.email, color = TextGray, fontSize = 11.sp)
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
                                Text("District", color = TextGray, fontSize = 9.sp)
                                Text(officer.district, color = Color.White, fontSize = 11.sp)
                            }
                            Column {
                                Text("Rating Nodes", color = TextGray, fontSize = 9.sp)
                                Text("${officer.performanceScore}% Audit", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Assigned Cases: $assignedCount", color = TextGray, fontSize = 10.sp)
                            Text("Resolved: $resolvedCount", color = SuccessGreen, fontSize = 10.sp)
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
        var dept by remember { mutableStateOf("Police") }
        var district by remember { mutableStateOf("Chennai") }
        var contact by remember { mutableStateOf("") }

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
                    OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Phone") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || contact.isEmpty()) {
                            Toast.makeText(context, "All parameters are mandatory", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addAuthorityAccount(name, email, password, dept, district, contact) { success, msg ->
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
        var contact by remember { mutableStateOf(officer.contact) }
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
                    OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Phone") })
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Disable Account", color = Color.White)
                        Switch(checked = disabled, onCheckedChange = { disabled = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAuthorityAccount(officer.email, name, dept, district, contact, disabled, score) { success, msg ->
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
    onBack: () -> Unit,
    onExport: () -> Unit
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
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange.copy(alpha = 0.15f), contentColor = AccentOrange)) {
                Text("Export PDF")
            }
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

// ---------------- ACTIVE SOS EMERGENCY DETAIL SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSosDetailScreen(
    viewModel: NyayaViewModel,
    sosList: List<IncidentReport>,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Tracking pulse animation state
    var pulseTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulseTrigger = !pulseTrigger
            delay(1000)
        }
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
            Text("Emergency Satellite Center", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = WarningRed.copy(alpha = 0.15f), contentColor = WarningRed),
                border = BorderStroke(1.dp, WarningRed)
            ) {
                Text("Export SOS Log")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live status widget
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, WarningRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (pulseTrigger) WarningRed else WarningRed.copy(alpha = 0.3f))
                )
                Text("LIVE SATELLITE RADAR ON - ACTIVE EMERGENCY BEACONS PINGING", color = WarningRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid representation
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sosList.isEmpty()) {
                item {
                    Text("No emergency signals active. All citizens are verified secure.", color = SuccessGreen, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(sosList) { sos ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, if (sos.status == "Pending") WarningRed else LightSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (sos.status == "Pending") WarningRed else SuccessGreen)
                                    )
                                    Text("ID: ${sos.id.uppercase()}", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("GPS: ${sos.locationLat}, ${sos.locationLng}", color = TextGray, fontSize = 10.sp)
                            }

                            Text("Title: ${sos.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(sos.description, color = TextGray, fontSize = 12.sp)

                            Divider(color = LightSlateBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateReportStatus(sos.id, "In Investigation", "Local unit dispatched from system.")
                                        Toast.makeText(context, "Police units dispatched immediately via Satellite Node!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dispatch Patrol", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        viewModel.updateReportStatus(sos.id, "Resolved", "Resolved by administration.")
                                        Toast.makeText(context, "SOS Beacon flagged as Resolved", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Resolve Signal", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- AI QUALITY DETAIL SCREEN ----------------
@Composable
fun AdminAiQualityDetailScreen(
    feedbacks: List<AiFeedback>,
    onBack: () -> Unit
) {
    var timeframe by remember { mutableStateOf("Weekly") }
    
    val total = feedbacks.size
    val averageStars = if (total > 0) feedbacks.map { it.starRating }.average() else 4.6
    val helpfulRatio = if (total > 0) feedbacks.count { it.isHelpful }.toFloat() / total * 100f else 88f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("AI Quality Analytics Audit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            
            // Timeframe toggle
            Row(modifier = Modifier.border(1.dp, LightSlateBorder, RoundedCornerShape(4.dp))) {
                listOf("Daily", "Weekly", "Monthly").forEach { tf ->
                    val active = timeframe == tf
                    Box(
                        modifier = Modifier
                            .background(if (active) AccentOrange else Color.Transparent)
                            .clickable { timeframe = tf }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(tf, color = if (active) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, LightSlateBorder), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Audit Sample Count", color = TextGray, fontSize = 11.sp)
                    Text(total.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, LightSlateBorder), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Average Rating Score", color = TextGray, fontSize = 11.sp)
                    Text(String.format("%.2f ★", averageStars), color = AccentOrange, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Star distribution bars
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audited Star Ratings Spread", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                (5 downTo 1).forEach { stars ->
                    val count = feedbacks.count { it.starRating == stars }
                    val fraction = if (total > 0) count.toFloat() / total else if (stars == 5) 0.65f else if (stars == 4) 0.20f else 0.05f
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$stars ★", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                        LinearProgressIndicator(
                            progress = fraction,
                            color = SuccessGreen,
                            trackColor = LightSlateBorder,
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Text("${(fraction * 100).toInt()}%", color = TextGray, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                    }
                }
            }
        }

        // AI Token Usage simulated Canvas Chart
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Token Consumption / API Latency Trend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val points = listOf(35f, 50f, 45f, 75f, 95f, 80f, 110f, 130f, 105f, 140f)
                    val widthStep = size.width / (points.size - 1)
                    val maxVal = 160f
                    val path = Path().apply {
                        moveTo(0f, size.height - (points[0] / maxVal * size.height))
                        for (i in 1 until points.size) {
                            lineTo(i * widthStep, size.height - (points[i] / maxVal * size.height))
                        }
                    }
                    drawPath(path, color = AccentOrange, style = Stroke(width = 3.dp.toPx()))
                }
                Text("Automated auditing points aligned in 24h cycle", color = TextGray, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
