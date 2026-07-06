package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.db.CitizenComplaint
import com.example.viewmodel.NyayaViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorityDispatchScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lang by viewModel.currentLanguage.collectAsState()
    
    val allComplaints by viewModel.allComplaints.collectAsState()
    val allNotifications by viewModel.allNotifications.collectAsState()

    // Screen State
    var selectedComplaintForDispatch by remember { mutableStateOf<CitizenComplaint?>(null) }
    var showAnalyticsDashboard by remember { mutableStateOf(false) }

    // Search and Filters
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }
    var filterStatus by remember { mutableStateOf("All") }
    var filterDistrict by remember { mutableStateOf("All") }
    var filterPriority by remember { mutableStateOf("All") }

    // Dropdowns for filters
    var categoryDropdownOpen by remember { mutableStateOf(false) }
    var statusDropdownOpen by remember { mutableStateOf(false) }
    var districtDropdownOpen by remember { mutableStateOf(false) }
    var priorityDropdownOpen by remember { mutableStateOf(false) }

    // Categories list for filters
    val categories = listOf("All", "Police", "Cyber Crime", "Women Safety", "Consumer Protection", "Traffic", "Municipality", "Water Supply", "Electricity", "Revenue", "Land Dispute", "Health", "Education", "Environment", "Public Grievance", "Others")
    val statuses = listOf("All", "Submitted", "Under Review", "Assigned", "In Progress", "Resolved", "Rejected")
    val districts = listOf("All", "New Delhi", "North Delhi", "South Delhi", "West Delhi", "Central Delhi", "East Delhi", "Chennai", "Coimbatore", "Bengaluru", "Mysore", "Thiruvananthapuram", "Kochi")
    val priorities = listOf("All", "Critical", "High", "Medium", "Low")

    // Filter logic
    val filteredComplaints = allComplaints.filter { cmp ->
        val idMatches = cmp.id.lowercase().contains(searchQuery.lowercase().trim()) || cmp.title.lowercase().contains(searchQuery.lowercase().trim())
        val catMatches = filterCategory == "All" || cmp.category == filterCategory
        val statusMatches = filterStatus == "All" || cmp.status == filterStatus
        val districtMatches = filterDistrict == "All" || cmp.district.equals(filterDistrict, ignoreCase = true)
        val priorityMatches = filterPriority == "All" || cmp.priority == filterPriority
        idMatches && catMatches && statusMatches && districtMatches && priorityMatches
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showAnalyticsDashboard) {
                            I18n.getString("analytics_dashboard", lang)
                        } else if (selectedComplaintForDispatch != null) {
                            "Dispatch Action"
                        } else {
                            I18n.getString("dispatch_dashboard", lang)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (selectedComplaintForDispatch != null || showAnalyticsDashboard) {
                        IconButton(onClick = {
                            selectedComplaintForDispatch = null
                            showAnalyticsDashboard = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (selectedComplaintForDispatch == null && !showAnalyticsDashboard) {
                        // Toggle Analytics button
                        IconButton(onClick = { showAnalyticsDashboard = true }) {
                            Icon(Icons.Default.BarChart, contentDescription = "Analytics", tint = AccentOrange)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkIndigo)
            )
        },
        containerColor = DarkIndigo
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                showAnalyticsDashboard -> {
                    AuthorityAnalyticsView(complaints = allComplaints, lang = lang)
                }
                selectedComplaintForDispatch != null -> {
                    DispatchActionView(
                        complaint = selectedComplaintForDispatch!!,
                        lang = lang,
                        viewModel = viewModel,
                        onBack = { selectedComplaintForDispatch = null }
                    )
                }
                else -> {
                    // Dispatch Hub List & Interactive Filters
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stats summary card header
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            border = BorderStroke(1.dp, LightSlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val submitted = allComplaints.count { it.status == "Submitted" }
                                val underReview = allComplaints.count { it.status == "Under Review" || it.status == "Assigned" || it.status == "In Progress" }
                                val resolved = allComplaints.count { it.status == "Resolved" }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("New Files", color = TextGray, fontSize = 11.sp)
                                    Text(submitted.toString(), color = LightJusticeBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Box(modifier = Modifier.width(1.dp).height(30.dp).background(LightSlateBorder))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("In Review", color = TextGray, fontSize = 11.sp)
                                    Text(underReview.toString(), color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Box(modifier = Modifier.width(1.dp).height(30.dp).background(LightSlateBorder))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Resolved", color = TextGray, fontSize = 11.sp)
                                    Text(resolved.toString(), color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }

                        // Search
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(I18n.getString("search_complaint_placeholder", lang), color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentOrange,
                                unfocusedBorderColor = LightSlateBorder,
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_search_bar")
                        )

                        // Filters Chips Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Category Filter Chip
                            FilterSelectorChip(
                                label = "Cat: $filterCategory",
                                isOpen = categoryDropdownOpen,
                                onToggle = { categoryDropdownOpen = !categoryDropdownOpen },
                                dropdownContent = {
                                    DropdownMenu(
                                        expanded = categoryDropdownOpen,
                                        onDismissRequest = { categoryDropdownOpen = false },
                                        modifier = Modifier.background(CardBackground)
                                    ) {
                                        categories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat, color = Color.White) },
                                                onClick = {
                                                    filterCategory = cat
                                                    categoryDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )

                            // Status Filter Chip
                            FilterSelectorChip(
                                label = "Status: $filterStatus",
                                isOpen = statusDropdownOpen,
                                onToggle = { statusDropdownOpen = !statusDropdownOpen },
                                dropdownContent = {
                                    DropdownMenu(
                                        expanded = statusDropdownOpen,
                                        onDismissRequest = { statusDropdownOpen = false },
                                        modifier = Modifier.background(CardBackground)
                                    ) {
                                        statuses.forEach { stat ->
                                            DropdownMenuItem(
                                                text = { Text(stat, color = Color.White) },
                                                onClick = {
                                                    filterStatus = stat
                                                    statusDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )

                            // District Filter Chip
                            FilterSelectorChip(
                                label = "Dist: $filterDistrict",
                                isOpen = districtDropdownOpen,
                                onToggle = { districtDropdownOpen = !districtDropdownOpen },
                                dropdownContent = {
                                    DropdownMenu(
                                        expanded = districtDropdownOpen,
                                        onDismissRequest = { districtDropdownOpen = false },
                                        modifier = Modifier.background(CardBackground)
                                    ) {
                                        districts.forEach { dist ->
                                            DropdownMenuItem(
                                                text = { Text(dist, color = Color.White) },
                                                onClick = {
                                                    filterDistrict = dist
                                                    districtDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )

                            // Priority Filter Chip
                            FilterSelectorChip(
                                label = "Priority: $filterPriority",
                                isOpen = priorityDropdownOpen,
                                onToggle = { priorityDropdownOpen = !priorityDropdownOpen },
                                dropdownContent = {
                                    DropdownMenu(
                                        expanded = priorityDropdownOpen,
                                        onDismissRequest = { priorityDropdownOpen = false },
                                        modifier = Modifier.background(CardBackground)
                                    ) {
                                        priorities.forEach { prio ->
                                            DropdownMenuItem(
                                                text = { Text(prio, color = Color.White) },
                                                onClick = {
                                                    filterPriority = prio
                                                    priorityDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        // Complaints list
                        if (filteredComplaints.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(1.dp, LightSlateBorder, RoundedCornerShape(8.dp))
                                    .background(CardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Inbox, contentDescription = "Empty", tint = TextGray, modifier = Modifier.size(50.dp))
                                    Text("No matching complaints dispatch nodes", color = TextGray)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredComplaints) { complaint ->
                                    AuthorityComplaintRowCard(
                                        complaint = complaint,
                                        lang = lang,
                                        onClick = { selectedComplaintForDispatch = complaint }
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

@Composable
fun FilterSelectorChip(
    label: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    dropdownContent: @Composable () -> Unit
) {
    Box {
        AssistChip(
            onClick = onToggle,
            label = { Text(label, color = Color.White, fontSize = 11.sp) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = AccentOrange, modifier = Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder)
        )
        dropdownContent()
    }
}

@Composable
fun AuthorityComplaintRowCard(
    complaint: CitizenComplaint,
    lang: String,
    onClick: () -> Unit
) {
    val statusColor = when (complaint.status) {
        "Submitted" -> Color(0xFF60A5FA)
        "Under Review" -> Color(0xFFFBBF24)
        "Assigned" -> Color(0xFFA78BFA)
        "In Progress" -> Color(0xFFFB923C)
        "Resolved" -> SuccessGreen
        "Rejected" -> WarningRed
        else -> TextGray
    }

    val statusKey = when (complaint.status) {
        "Submitted" -> "status_submitted"
        "Under Review" -> "status_under_review"
        "Assigned" -> "status_assigned"
        "In Progress" -> "status_in_progress"
        "Resolved" -> "status_resolved"
        "Rejected" -> "status_rejected"
        else -> ""
    }
    val displayStatus = if (statusKey.isNotEmpty()) I18n.getString(statusKey, lang) else complaint.status

    val priorityKey = when (complaint.priority) {
        "Low" -> "priority_low"
        "Medium" -> "priority_medium"
        "High" -> "priority_high"
        "Critical" -> "priority_critical"
        else -> ""
    }
    val displayPriority = if (priorityKey.isNotEmpty()) I18n.getString(priorityKey, lang) else complaint.priority

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (complaint.priority == "Critical") WarningRed else LightSlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // First Row: ID + Priority + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(complaint.id, color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    
                    if (complaint.priority == "Critical" || complaint.priority == "High") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(WarningRed.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(displayPriority.uppercase(), color = WarningRed, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(displayStatus, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = complaint.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val anonymousText = I18n.getString("anonymous_citizen", lang)
            Text(
                text = "Reporter: ${if (complaint.isAnonymous) anonymousText else complaint.reporterName}",
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            val aiDeptLabel = I18n.getString("ai_predicted_dept", lang)
            Text(
                text = "$aiDeptLabel: ${complaint.aiPredictedDepartment}",
                color = LightJusticeBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = TextGray, modifier = Modifier.size(12.dp))
                    Text(complaint.district, color = TextGray, fontSize = 11.sp)
                }

                // Time
                val date = Date(complaint.timestamp)
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                Text(sdf.format(date), color = TextGray, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchActionView(
    complaint: CitizenComplaint,
    lang: String,
    viewModel: NyayaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val replies by viewModel.getRepliesForComplaint(complaint.id).collectAsState(initial = emptyList())

    var status by remember { mutableStateOf(complaint.status) }
    var officer by remember { mutableStateOf(complaint.assignedOfficer) }
    var messageReply by remember { mutableStateOf("") }
    var attachmentPath by remember { mutableStateOf("") }

    var statusDropdownOpen by remember { mutableStateOf(false) }
    val statuses = listOf("Under Review", "Assigned", "In Progress", "Resolved", "Rejected")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Row with Back and header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Update Complaint Dispatch Nodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // Details Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ID: ${complaint.id}", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Priority: ${complaint.priority}", color = if (complaint.priority == "Critical") WarningRed else AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Divider(color = LightSlateBorder)

                Text(complaint.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(complaint.description, color = TextGray, fontSize = 13.sp)

                Divider(color = LightSlateBorder)

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Category", color = TextGray, fontSize = 10.sp)
                        Text(complaint.category, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Predicted Dept", color = TextGray, fontSize = 10.sp)
                        Text(complaint.aiPredictedDepartment, color = LightJusticeBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Location", color = TextGray, fontSize = 10.sp)
                        Text("${complaint.address}, ${complaint.district}, ${complaint.state}", color = Color.White, fontSize = 12.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reporter Nodes", color = TextGray, fontSize = 10.sp)
                        Text(
                            text = if (complaint.isAnonymous) "Anonymous" else "${complaint.reporterName} (${complaint.reporterEmail})",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                if (complaint.imageUri != null) {
                    Divider(color = LightSlateBorder)
                    Text("Evidence Attachment", color = TextGray, fontSize = 10.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        MockImageRenderer(drawableName = complaint.imageUri, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Conversation History Section
        if (replies.isNotEmpty()) {
            Text("Conversation & Reply History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, LightSlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    replies.forEach { reply ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = reply.authorityName,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(reply.timestamp))
                                Text(
                                    text = dateStr,
                                    color = TextGray,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = reply.message,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            if (!reply.attachmentPath.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = LightJusticeBlue, modifier = Modifier.size(12.dp))
                                    Text("Attachment: ${reply.attachmentPath}", color = LightJusticeBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(JusticeBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(reply.updatedStatus, color = JusticeBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Divider(color = LightSlateBorder, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // Form Actions / Reply to Citizen Section
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Reply & Dispatch Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // Status dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Complaint Status", color = TextGray) },
                        trailingIcon = {
                            IconButton(onClick = { statusDropdownOpen = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = AccentOrange)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { statusDropdownOpen = true }
                    )
                    DropdownMenu(
                        expanded = statusDropdownOpen,
                        onDismissRequest = { statusDropdownOpen = false },
                        modifier = Modifier
                            .background(CardBackground)
                            .fillMaxWidth(0.85f)
                    ) {
                        statuses.forEach { stat ->
                            DropdownMenuItem(
                                text = { Text(stat, color = Color.White) },
                                onClick = {
                                    status = stat
                                    statusDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Assigned Officer Name Input
                OutlinedTextField(
                    value = officer,
                    onValueChange = { officer = it },
                    label = { Text("Assign Investigating Officer Name", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_assign_field")
                )

                // Reply Message / closure remarks
                OutlinedTextField(
                    value = messageReply,
                    onValueChange = { messageReply = it },
                    label = { Text("Type reply to citizen / Resolution action", color = TextGray) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remarks_dispatch_field")
                )

                // Supporting Document Attachment Path
                OutlinedTextField(
                    value = attachmentPath,
                    onValueChange = { attachmentPath = it },
                    label = { Text("Attach Supporting Document Name (Optional)", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attachment_path_field")
                )

                // Save changes Button
                Button(
                    onClick = {
                        if (status == "Assigned" && officer.trim().isEmpty()) {
                            Toast.makeText(context, "Officer name must be specified for 'Assigned' status", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (messageReply.trim().isEmpty()) {
                            Toast.makeText(context, "Please enter a message to send to the citizen node.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        
                        // 1. Sync assigned officer
                        if (officer != complaint.assignedOfficer) {
                            viewModel.updateComplaintStatusByAuthority(
                                complaintId = complaint.id,
                                status = status,
                                assignedOfficer = officer,
                                remarks = messageReply
                            ) { _, _ -> }
                        }

                        // 2. Submit reply thread
                        val attachVal = if (attachmentPath.trim().isEmpty()) null else attachmentPath.trim()
                        viewModel.sendComplaintReply(
                            complaintId = complaint.id,
                            message = messageReply,
                            updatedStatus = status,
                            attachmentPath = attachVal
                        ) { success, msg ->
                            scope.launch {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    onBack()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("save_dispatch_btn")
                ) {
                    Text("Apply & Send Response", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun AuthorityAnalyticsView(complaints: List<CitizenComplaint>, lang: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Legal Dispatch Performance Analytics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

        val total = complaints.size
        val pending = complaints.count { it.status == "Submitted" }
        val progress = complaints.count { it.status == "In Progress" || it.status == "Under Review" || it.status == "Assigned" }
        val resolved = complaints.count { it.status == "Resolved" }
        val rejected = complaints.count { it.status == "Rejected" }

        // Grid Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalyticsStatCard(title = "Total Cases", value = total.toString(), color = Color.White, modifier = Modifier.weight(1f))
            AnalyticsStatCard(title = "Resolved", value = resolved.toString(), color = SuccessGreen, modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalyticsStatCard(title = "Submitted", value = pending.toString(), color = LightJusticeBlue, modifier = Modifier.weight(1f))
            AnalyticsStatCard(title = "Rejected", value = rejected.toString(), color = WarningRed, modifier = Modifier.weight(1f))
        }

        // Custom Interactive Chart 1: Status Distribution Bar Chart
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Node Distribution", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                val maxCount = maxOf(total, 1)
                
                StatusRowProgress(name = "Submitted", count = pending, maxCount = maxCount, color = LightJusticeBlue)
                Spacer(modifier = Modifier.height(10.dp))
                StatusRowProgress(name = "Under Action", count = progress, maxCount = maxCount, color = AccentOrange)
                Spacer(modifier = Modifier.height(10.dp))
                StatusRowProgress(name = "Resolved", count = resolved, maxCount = maxCount, color = SuccessGreen)
                Spacer(modifier = Modifier.height(10.dp))
                StatusRowProgress(name = "Rejected", count = rejected, maxCount = maxCount, color = WarningRed)
            }
        }

        // Custom Interactive Chart 2: Category distribution canvas representation
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Category distribution Map", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val groupMap = complaints.groupBy { it.category }.mapValues { it.value.size }
                if (groupMap.isEmpty()) {
                    Text("No category records", color = TextGray, fontSize = 12.sp)
                } else {
                    groupMap.forEach { (cat, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, color = Color.White, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("$count cases", color = TextGray, fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(LightSlateBorder)
                                ) {
                                    val percent = count.toFloat() / maxOf(total, 1).toFloat()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(percent)
                                            .background(AccentOrange)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Custom Native Canvas Rendered Line Graph: Monthly Trends of Complaints
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(I18n.getString("monthly_trends", lang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Canvas line graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Draw Grid lines
                        drawLine(
                            color = LightSlateBorder,
                            start = androidx.compose.ui.geometry.Offset(0f, height / 2),
                            end = androidx.compose.ui.geometry.Offset(width, height / 2),
                            strokeWidth = 1f
                        )

                        // Data points: (Jan, Feb, Mar, Apr, May, Jun, Jul)
                        // Mock counts representing a polished interactive visual data graph
                        val dataPoints = listOf(4f, 8f, 15f, 12f, 22f, 18f, 30f)
                        val maxVal = dataPoints.maxOrNull() ?: 10f

                        val path = Path()
                        val stepX = width / (dataPoints.size - 1)
                        
                        dataPoints.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = height - (value / maxVal) * (height - 20f) - 10f
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = AccentOrange,
                            style = Stroke(width = 4f)
                        )

                        // Draw points as circles
                        dataPoints.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = height - (value / maxVal) * (height - 20f) - 10f
                            drawCircle(
                                color = LightJusticeBlue,
                                radius = 6f,
                                center = androidx.compose.ui.geometry.Offset(x, y)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul").forEach { month ->
                        Text(month, color = TextGray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = TextGray, fontSize = 11.sp)
            Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusRowProgress(
    name: String,
    count: Int,
    maxCount: Int,
    color: Color
) {
    val percent = count.toFloat() / maxCount.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, color = Color.White, fontSize = 12.sp)
            Text("$count cases (${(percent * 100).toInt()}%)", color = TextGray, fontSize = 11.sp)
        }
        LinearProgressIndicator(
            progress = { percent },
            color = color,
            trackColor = LightSlateBorder,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}
