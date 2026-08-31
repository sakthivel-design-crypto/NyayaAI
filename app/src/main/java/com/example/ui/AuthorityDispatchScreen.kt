package com.example.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.CitizenComplaint
import com.example.db.CitizenRequest
import com.example.util.TimeUtils
import com.example.viewmodel.NyayaViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun AuthorityDispatchScreen(viewModel: NyayaViewModel) {
    AuthorityComplaintsScreen(viewModel = viewModel)
}

/**
 * AUTHORITY COMPLAINTS MODULE
 * Manages citizen complaints with real-time Firestore sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorityComplaintsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val allComplaints by viewModel.allComplaints.collectAsState()

    var selectedComplaintForAudit by remember { mutableStateOf<CitizenComplaint?>(null) }
    var fullImageModalUri by remember { mutableStateOf<String?>(null) }

    // Search and Filter State
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }
    var filterStatus by remember { mutableStateOf("All") }
    var filterPriority by remember { mutableStateOf("All") }

    var categoryDropdownOpen by remember { mutableStateOf(false) }
    var statusDropdownOpen by remember { mutableStateOf(false) }
    var priorityDropdownOpen by remember { mutableStateOf(false) }

    val categories = listOf("All", "Police", "Cyber Crime", "Women Safety", "Consumer Protection", "Traffic", "Municipality", "Water Supply", "Electricity", "Revenue", "Land Dispute", "Health", "Education", "Environment", "Public Grievance", "Others")
    val statuses = listOf("All", "Submitted", "Under Review", "Assigned", "In Investigation", "More Information Required", "Resolved", "Rejected", "Closed")
    val priorities = listOf("All", "Critical", "High", "Medium", "Low")

    val filteredComplaints = allComplaints.filter { cmp ->
        val query = searchQuery.lowercase().trim()
        val idMatches = cmp.id.lowercase().contains(query) ||
                        cmp.title.lowercase().contains(query) ||
                        cmp.reporterName.lowercase().contains(query) ||
                        cmp.district.lowercase().contains(query) ||
                        cmp.assignedOfficer.lowercase().contains(query)
        val catMatches = filterCategory == "All" || cmp.category == filterCategory || cmp.aiPredictedDepartment == filterCategory
        val statusMatches = filterStatus == "All" || cmp.status.equals(filterStatus, ignoreCase = true)
        val priorityMatches = filterPriority == "All" || cmp.priority.equals(filterPriority, ignoreCase = true)
        idMatches && catMatches && statusMatches && priorityMatches
    }

    if (selectedComplaintForAudit != null) {
        IncidentSecurityAuditScreen(
            complaint = selectedComplaintForAudit!!,
            viewModel = viewModel,
            lang = lang,
            onClose = { selectedComplaintForAudit = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkIndigo)
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Citizen Complaints Console",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    Text(
                        text = "Live real-time ledger synced via Firebase Firestore",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🟢 Live Sync Active",
                        fontSize = 10.sp,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Complaint ID, Citizen Name, Title...", color = TextGray, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentOrange) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextGray)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = LightSlateBorder,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Filter
                Box {
                    Button(
                        onClick = { categoryDropdownOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, if (filterCategory != "All") AccentOrange else LightSlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Dept: $filterCategory", fontSize = 11.sp, color = if (filterCategory != "All") AccentOrange else TextDarkSlate)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = categoryDropdownOpen,
                        onDismissRequest = { categoryDropdownOpen = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    filterCategory = cat
                                    categoryDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Priority Filter
                Box {
                    Button(
                        onClick = { priorityDropdownOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, if (filterPriority != "All") WarningRed else LightSlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Priority: $filterPriority", fontSize = 11.sp, color = if (filterPriority != "All") WarningRed else TextDarkSlate)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = WarningRed, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = priorityDropdownOpen,
                        onDismissRequest = { priorityDropdownOpen = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        priorities.forEach { prio ->
                            DropdownMenuItem(
                                text = { Text(prio, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    filterPriority = prio
                                    priorityDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Status Filter
                Box {
                    Button(
                        onClick = { statusDropdownOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, if (filterStatus != "All") JusticeBlue else LightSlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Status: $filterStatus", fontSize = 11.sp, color = if (filterStatus != "All") LightJusticeBlue else TextDarkSlate)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = JusticeBlue, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = statusDropdownOpen,
                        onDismissRequest = { statusDropdownOpen = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        statuses.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    filterStatus = st
                                    statusDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                if (filterCategory != "All" || filterPriority != "All" || filterStatus != "All") {
                    TextButton(onClick = {
                        filterCategory = "All"
                        filterPriority = "All"
                        filterStatus = "All"
                    }) {
                        Text("Reset Filters", fontSize = 11.sp, color = AccentOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Complaints List
            if (filteredComplaints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                        Text("No complaints found matching criteria.", color = TextGray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredComplaints, key = { it.id }) { cmp ->
                        ComplaintAuthorityCard(
                            cmp = cmp,
                            onOpenAudit = { selectedComplaintForAudit = cmp },
                            onEnlargeImage = { fullImageModalUri = cmp.imageUri }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Image Enlarge Modal Dialog
    fullImageModalUri?.let { uriStr ->
        AlertDialog(
            onDismissRequest = { fullImageModalUri = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Evidence Image View", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccentOrange)
                    IconButton(onClick = { fullImageModalUri = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextGray)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    MockImageRenderer(drawableName = uriStr, modifier = Modifier.fillMaxSize())
                }
            },
            confirmButton = {
                Button(
                    onClick = { fullImageModalUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Close Preview", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardBackground
        )
    }
}

/**
 * COMPLAINT AUTHORITY CARD
 * Displays complete complaint details required by Citizen & Authority guidelines.
 */
@Composable
fun ComplaintAuthorityCard(
    cmp: CitizenComplaint,
    onOpenAudit: () -> Unit,
    onEnlargeImage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAudit() }
            .testTag("auth_complaint_card_${cmp.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: ID + Priority + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cmp.id,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentOrange
                    )
                    // Priority Badge
                    val prioBg = when (cmp.priority) {
                        "Critical" -> WarningRed
                        "High" -> WarningRed.copy(alpha = 0.8f)
                        "Medium" -> AccentOrange
                        else -> TextGray
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = prioBg),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = cmp.priority.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Status Badge
                val statusColor = when (cmp.status) {
                    "Submitted", "Pending" -> WarningRed
                    "Under Review", "Assigned", "In Investigation" -> JusticeBlue
                    "Resolved", "Closed" -> SuccessGreen
                    else -> TextGray
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = cmp.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Title & Description
            Text(
                text = cmp.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkSlate
            )
            Text(
                text = cmp.description,
                fontSize = 12.sp,
                color = TextGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Image Thumbnail (if present)
            if (!cmp.imageUri.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkIndigo)
                        .clickable { onEnlargeImage() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            MockImageRenderer(drawableName = cmp.imageUri, modifier = Modifier.fillMaxSize())
                        }
                        Column {
                            Text("Evidence Attached", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                            Text("Tap to view image modal", fontSize = 9.sp, color = AccentOrange)
                        }
                    }
                    Icon(Icons.Default.ZoomIn, contentDescription = "Enlarge", tint = AccentOrange)
                }
            }

            HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

            // Metadata Grid: Department, Citizen Name, Location, Date & Time, Assigned Officer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Department: ${cmp.aiPredictedDepartment.ifEmpty { cmp.category }}", fontSize = 10.sp, color = TextDarkSlate, fontWeight = FontWeight.Medium)
                    Text("Citizen: ${if (cmp.isAnonymous) "Anonymous" else cmp.reporterName}", fontSize = 10.sp, color = TextGray)
                    Text("Location: ${cmp.district}, ${cmp.state}", fontSize = 10.sp, color = TextGray)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Submitted: ${TimeUtils.formatDate(cmp.createdAt)} ${TimeUtils.formatTime(cmp.createdAt)}", fontSize = 10.sp, color = TextGray)
                    Text("Officer: ${if (cmp.assignedOfficer.isNotEmpty()) cmp.assignedOfficer else "Unassigned"}", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Manage Complaint & Sync →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
        }
    }
}

/**
 * AUTHORITY INQUIRIES MODULE
 * Manages citizen legal queries and answers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorityInquiriesScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val inquiries by viewModel.allCitizenRequests.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var replyingInquiry by remember { mutableStateOf<CitizenRequest?>(null) }
    var officerName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var inquiryToDelete by remember { mutableStateOf<CitizenRequest?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("All") }

    val filteredInquiries = inquiries.filter { req ->
        val query = searchQuery.lowercase().trim()
        val matchesQuery = req.id.lowercase().contains(query) ||
                           req.subject.lowercase().contains(query) ||
                           req.details.lowercase().contains(query) ||
                           req.citizenName.lowercase().contains(query)
        val matchesStatus = filterStatus == "All" || req.status.equals(filterStatus, ignoreCase = true)
        matchesQuery && matchesStatus
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(14.dp)
    ) {
        // Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Citizen Legal Inquiries", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Text("Real-time government legal consultation & official officer responses", fontSize = 11.sp, color = TextGray)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, SuccessGreen)
            ) {
                Text(
                    text = "${inquiries.count { it.status == "Open" }} Open",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search legal questions, citizen name...", color = TextGray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentOrange) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = LightSlateBorder,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Open", "Answered").forEach { st ->
                val isSel = filterStatus == st
                FilterChip(
                    selected = isSel,
                    onClick = { filterStatus = st },
                    label = { Text(st, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange,
                        selectedLabelColor = Color.Black,
                        containerColor = CardBackground,
                        labelColor = TextGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Inquiries List
        if (filteredInquiries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No legal inquiries found.", color = TextGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredInquiries, key = { it.id }) { req ->
                    InquiryAuthorityCard(
                        req = req,
                        onOpenResponseModal = {
                            replyingInquiry = req
                            validationError = null
                            isSubmitting = false
                            if (req.reply.isNotEmpty() || req.status == "Answered") {
                                replyText = req.reply
                                officerName = req.officerName.ifBlank { userProfile.name }
                                department = req.officerDepartment.ifBlank { "Department of Legal Affairs" }
                                designation = req.officerDesignation.ifBlank { "Legal Authority Officer" }
                            } else {
                                // NEW INQUIRY: MUST BE COMPLETELY EMPTY RESPONSE TEXT
                                replyText = ""
                                officerName = userProfile.name.ifBlank { "" }
                                department = "Department of Legal Affairs"
                                designation = "Legal Officer"
                            }
                        },
                        onDeleteResponse = {
                            inquiryToDelete = req
                        }
                    )
                }
            }
        }
    }

    // STEP 2 & 3 & 4 & 5 & 9: Official Reply / Edit Dialog Modal
    replyingInquiry?.let { req ->
        val isEditing = req.reply.isNotEmpty() || req.status == "Answered"
        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) replyingInquiry = null
            },
            title = {
                Text(
                    text = if (isEditing) "Edit Official Response" else "Official Legal Response",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AccentOrange
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // READ ONLY Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                        border = BorderStroke(1.dp, LightSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Inquiry ID: ${req.id}", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
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
                            }
                            Text("Citizen: ${req.citizenName}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Email: ${req.citizenEmail}", fontSize = 10.sp, color = AccentOrange)
                            Text("Submitted Date: ${TimeUtils.formatDate(req.timestamp)} ${TimeUtils.formatTime(req.timestamp)}", fontSize = 9.sp, color = TextGray)
                            HorizontalDivider(color = LightSlateBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            Text("Subject: ${req.subject}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                            Text("Citizen Question: ${req.details}", fontSize = 11.sp, color = TextGray)
                        }
                    }

                    HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                    // Officer Information Section
                    Text("Officer Information", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)

                    OutlinedTextField(
                        value = officerName,
                        onValueChange = {
                            officerName = it
                            validationError = null
                        },
                        placeholder = { Text("Enter Officer Name", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Officer Name *", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("officer_name_input"),
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
                        value = department,
                        onValueChange = {
                            department = it
                            validationError = null
                        },
                        placeholder = { Text("Enter Department", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Department *", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("officer_department_input"),
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
                        value = designation,
                        onValueChange = {
                            designation = it
                            validationError = null
                        },
                        placeholder = { Text("Enter Designation", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Designation *", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("officer_designation_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // STEP 3: Response Text Box (MUST BE EMPTY FOR NEW INQUIRY)
                    Text("Official Legal Response", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = {
                            replyText = it
                            validationError = null
                        },
                        placeholder = { Text("Type the official legal response here...", color = TextGray, fontSize = 12.sp) },
                        label = { Text("Official Response *", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("official_response_input"),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = LightSlateBorder,
                            focusedContainerColor = DarkIndigo,
                            unfocusedContainerColor = DarkIndigo,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Validation Message Display
                    validationError?.let { err ->
                        Text(
                            text = err,
                            color = WarningRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // STEP 4: Form Validation
                        val nameClean = officerName.trim()
                        val deptClean = department.trim()
                        val desigClean = designation.trim()
                        val respClean = replyText.trim()

                        if (nameClean.isEmpty()) {
                            validationError = "Officer Name is required."
                            Toast.makeText(context, "Officer Name is required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (deptClean.isEmpty()) {
                            validationError = "Department is required."
                            Toast.makeText(context, "Department is required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (desigClean.isEmpty()) {
                            validationError = "Designation is required."
                            Toast.makeText(context, "Designation is required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (respClean.isEmpty()) {
                            validationError = "Official Response cannot be empty."
                            Toast.makeText(context, "Official Response cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        validationError = null

                        viewModel.submitOfficialInquiryResponse(
                            requestId = req.id,
                            officerName = nameClean,
                            department = deptClean,
                            designation = desigClean,
                            replyText = respClean
                        ) { success, msg ->
                            isSubmitting = false
                            if (success) {
                                Toast.makeText(context, "Official Response Submitted Successfully.", Toast.LENGTH_LONG).show()
                                replyingInquiry = null
                            } else {
                                Toast.makeText(context, "Unable to submit response. Please try again.", Toast.LENGTH_LONG).show()
                                validationError = msg
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.testTag("submit_official_response_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submitting Official Response...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    } else {
                        Text("Submit Official Response", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { replyingInquiry = null },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }

    // STEP 10: Delete Confirmation Dialog Modal
    inquiryToDelete?.let { req ->
        AlertDialog(
            onDismissRequest = { inquiryToDelete = null },
            title = {
                Text("Delete Response", color = WarningRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text("Delete this response?", color = Color.White, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = req.id
                        inquiryToDelete = null
                        viewModel.deleteOfficialInquiryResponse(targetId) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { inquiryToDelete = null }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }
}

/**
 * INQUIRY AUTHORITY CARD
 */
@Composable
fun InquiryAuthorityCard(
    req: CitizenRequest,
    onOpenResponseModal: () -> Unit,
    onDeleteResponse: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenResponseModal() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, LightSlateBorder)
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
                Text("Inquiry ID: ${req.id} • ${TimeUtils.formatDate(req.timestamp)}", fontSize = 9.sp, color = TextGray)
            }

            Text(req.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDarkSlate)
            Text(req.details, fontSize = 11.sp, color = TextGray)
            Text("By: ${req.citizenName} (${req.citizenEmail})", fontSize = 10.sp, color = TextGray)

            if (req.reply.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightBlueHighlight)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("⚖️ Official Response:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (req.edited) {
                                    Text("(Edited)", fontSize = 9.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                                }
                                Text("Tap to edit", fontSize = 9.sp, color = TextGray)
                            }
                        }
                        Text(req.reply, fontSize = 11.sp, color = TextDarkSlate)
                        if (req.officerName.isNotEmpty()) {
                            Text(
                                "Responded by: ${req.officerName} • ${req.officerDepartment} (${req.officerDesignation})",
                                fontSize = 9.sp,
                                color = TextGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (req.respondedAt > 0) {
                            Text(
                                "Response Date: ${TimeUtils.formatDate(req.respondedAt)} ${TimeUtils.formatTime(req.respondedAt)}",
                                fontSize = 8.sp,
                                color = TextGray
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onDeleteResponse() }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = WarningRed, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Response", color = WarningRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { onOpenResponseModal() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Response", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { onOpenResponseModal() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Write Official Response", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * INCIDENT SECURITY AUDIT SCREEN
 * Complete detail inspection and editing console for an authority officer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentSecurityAuditScreen(
    complaint: CitizenComplaint,
    viewModel: NyayaViewModel,
    lang: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    var currentStatus by remember { mutableStateOf(complaint.status) }
    var assignedOfficerName by remember { mutableStateOf(complaint.assignedOfficer) }
    var selectedDept by remember { mutableStateOf(complaint.aiPredictedDepartment.ifEmpty { complaint.category }) }
    var officerNotesText by remember { mutableStateOf(complaint.authorityRemarks) }

    var deptDropdownOpen by remember { mutableStateOf(false) }
    var showFullImageDialog by remember { mutableStateOf(false) }

    val departments = listOf("Police", "Cyber Crime", "Women Safety", "Consumer Protection", "Traffic", "Municipality", "Water Supply", "Electricity", "Revenue", "Land Dispute", "Health", "Education", "Environment", "Public Grievance", "Others")
    val statusOptions = listOf("Submitted", "Under Review", "Assigned", "In Investigation", "More Information Required", "Resolved", "Rejected", "Closed")

    // Parse Timeline
    val timelineList = remember(complaint.timeline) {
        val list = mutableListOf<Map<String, String>>()
        try {
            if (complaint.timeline.isNotEmpty() && complaint.timeline.startsWith("[")) {
                val arr = JSONArray(complaint.timeline)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        mapOf(
                            "status" to obj.optString("status", "Updated"),
                            "note" to obj.optString("note", ""),
                            "by" to obj.optString("by", "Authority"),
                            "dateStr" to obj.optString("dateStr", ""),
                            "timeStr" to obj.optString("timeStr", ""),
                            "relativeTime" to obj.optString("relativeTime", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentOrange)
            }
            Text("Complaint Detail & Action Console", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
            Box(modifier = Modifier.width(48.dp))
        }

        // Card 1: Main Complaint Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ID: ${complaint.id}", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JusticeBlue.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, JusticeBlue)
                    ) {
                        Text(currentStatus, color = LightJusticeBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }

                Text(complaint.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                Text(complaint.description, fontSize = 12.sp, color = TextGray)

                HorizontalDivider(color = LightSlateBorder, thickness = 1.dp)

                // Citizen Metadata
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Citizen Name: ${if (complaint.isAnonymous) "Anonymous Citizen" else complaint.reporterName}", fontSize = 11.sp, color = TextDarkSlate)
                    Text("Email: ${complaint.reporterEmail.ifEmpty { "Not Provided" }}", fontSize = 11.sp, color = TextGray)
                    Text("Phone: ${complaint.citizenPhone}", fontSize = 11.sp, color = TextGray)
                    Text("Address: ${complaint.address}, ${complaint.district}, ${complaint.state}", fontSize = 11.sp, color = TextGray)
                    Text("Submitted: ${TimeUtils.formatDate(complaint.createdAt)} ${TimeUtils.formatTime(complaint.createdAt)}", fontSize = 10.sp, color = TextGray)
                }

                // Google Maps Location Button
                Button(
                    onClick = {
                        val geoUri = Uri.parse("geo:${complaint.latitude},${complaint.longitude}?q=${complaint.latitude},${complaint.longitude}(${Uri.encode(complaint.title)})")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${complaint.latitude},${complaint.longitude}"))
                            context.startActivity(browserIntent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkIndigo),
                    border = BorderStroke(1.dp, AccentOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Incident Location on Google Maps", color = Color.White, fontSize = 11.sp)
                }

                // Evidence Image Attachment
                if (!complaint.imageUri.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Evidence Photo:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkIndigo)
                            .clickable { showFullImageDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        MockImageRenderer(drawableName = complaint.imageUri, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Card 2: Timeline
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Complete Timeline & Audit Progress", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                
                if (timelineList.isEmpty()) {
                    Text("Submitted on ${TimeUtils.formatDate(complaint.createdAt)}", fontSize = 11.sp, color = TextGray)
                } else {
                    timelineList.forEach { step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${step["status"]} - ${step["by"]}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                                if (!step["note"].isNullOrEmpty()) {
                                    Text(step["note"]!!, fontSize = 10.sp, color = TextGray)
                                }
                                Text("${step["dateStr"]} ${step["timeStr"]}", fontSize = 9.sp, color = AccentOrange)
                            }
                        }
                        HorizontalDivider(color = LightSlateBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Card 2.5: Real-Time Two-Way Conversation (Citizen Messages Read-Only, Authority Reply Box)
        ComplaintConversationSection(
            complaintId = complaint.id,
            currentRole = "AUTHORITY",
            viewModel = viewModel
        )

        // Card 3: Authority Action & Update Panel

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, AccentOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Authority Action & Firestore Sync", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentOrange)

                // Select Status
                Text("Change Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSlate)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.forEach { st ->
                        val isSel = currentStatus == st
                        FilterChip(
                            selected = isSel,
                            onClick = { currentStatus = st },
                            label = { Text(st, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkIndigo,
                                labelColor = TextGray
                            )
                        )
                    }
                }

                // Department Selection
                Box {
                    OutlinedButton(
                        onClick = { deptDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, LightSlateBorder)
                    ) {
                        Text("Department: $selectedDept", color = TextDarkSlate, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = deptDropdownOpen,
                        onDismissRequest = { deptDropdownOpen = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        departments.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    selectedDept = d
                                    deptDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Assign Officer
                OutlinedTextField(
                    value = assignedOfficerName,
                    onValueChange = { assignedOfficerName = it },
                    label = { Text("Assign Investigating Officer") },
                    placeholder = { Text("e.g. Officer Inspector Sharma") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Authority Remarks
                OutlinedTextField(
                    value = officerNotesText,
                    onValueChange = { officerNotesText = it },
                    label = { Text("Authority Resolution Notes / Instructions") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Apply Updates Button (BUG 1 & BUG 8)
                Button(
                    onClick = {
                        val statusToApply = currentStatus.ifBlank { complaint.status }
                        val officerToApply = assignedOfficerName.trim()
                        val notesToApply = officerNotesText.trim()

                        if (statusToApply.isBlank()) {
                            Toast.makeText(context, "Please select a complaint status.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (officerToApply.isBlank()) {
                            Toast.makeText(context, "Please enter an assigned officer name.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (notesToApply.isBlank()) {
                            Toast.makeText(context, "Please enter authority resolution notes.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        try {
                            viewModel.updateComplaintStatusByAuthority(
                                complaintId = complaint.id,
                                status = statusToApply,
                                assignedOfficer = officerToApply,
                                remarks = notesToApply,
                                department = selectedDept
                            ) { success, msg ->
                                try {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    Toast.makeText(context, "Complaint updated and synced to Firestore in real-time", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("AuthorityDispatchScreen", "Callback UI error: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AuthorityDispatchScreen", "Exception on Apply Updates click: ${e.message}", e)
                            Toast.makeText(context, "An error occurred. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_updates_sync_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APPLY UPDATES & SYNC FIRESTORE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }

    // Full Image Modal
    if (showFullImageDialog && !complaint.imageUri.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { showFullImageDialog = false },
            title = { Text("Evidence Image Attachment", color = AccentOrange, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    MockImageRenderer(drawableName = complaint.imageUri, modifier = Modifier.fillMaxSize())
                }
            },
            confirmButton = {
                Button(onClick = { showFullImageDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                    Text("Close Preview", color = Color.Black)
                }
            },
            containerColor = CardBackground
        )
    }
}
