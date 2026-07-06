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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.db.CitizenComplaint
import com.example.viewmodel.NyayaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.font.FontStyle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenComplaintsScreen(viewModel: NyayaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lang by viewModel.currentLanguage.collectAsState()
    
    val myComplaints by viewModel.myComplaints.collectAsState()
    val allNotifications by viewModel.myNotifications.collectAsState()
    
    var showForm by remember { mutableStateOf(false) }
    var editingComplaint by remember { mutableStateOf<CitizenComplaint?>(null) }
    var selectedComplaintForDetail by remember { mutableStateOf<CitizenComplaint?>(null) }
    var showNotificationsDrawer by remember { mutableStateOf(false) }

    // Form fields state
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Police") }
    var description by remember { mutableStateOf("") }
    var selectedState by remember { mutableStateOf("Delhi") }
    var selectedDistrict by remember { mutableStateOf("New Delhi") }
    var address by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<String?>(null) }

    // Dropdown visibility states
    var categoryExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember { mutableStateOf(false) }
    var districtExpanded by remember { mutableStateOf(false) }
    
    // Upload image simulation state
    var isUploadingImage by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // Lists of options
    val categories = listOf(
        "Police", "Cyber Crime", "Women Safety", "Consumer Protection", "Traffic", 
        "Municipality", "Water Supply", "Electricity", "Revenue", "Land Dispute", 
        "Health", "Education", "Environment", "Public Grievance", "Others"
    )
    val states = listOf("Delhi", "Tamil Nadu", "Karnataka", "Kerala", "Maharashtra")
    val districtsMap = mapOf(
        "Delhi" to listOf("New Delhi", "North Delhi", "South Delhi", "West Delhi", "Central Delhi", "East Delhi"),
        "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai", "Trichy", "Salem", "Tirunelveli"),
        "Karnataka" to listOf("Bengaluru", "Mysore", "Hubli", "Mangalore", "Belgaum"),
        "Kerala" to listOf("Thiruvananthapuram", "Kochi", "Kozhikode", "Thrissur", "Kollam"),
        "Maharashtra" to listOf("Mumbai", "Pune", "Nagpur", "Thane", "Nashik")
    )

    // Set fields when editing starts
    LaunchedEffect(editingComplaint) {
        editingComplaint?.let {
            title = it.title
            category = it.category
            description = it.description
            selectedState = it.state
            selectedDistrict = it.district
            address = it.address
            isAnonymous = it.isAnonymous
            imageUri = it.imageUri
        }
    }

    // Unread notification count
    val unreadNotificationsCount = allNotifications.count { !it.isRead }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showForm) {
                            if (editingComplaint != null) I18n.getString("update_button", lang) else I18n.getString("submit_new_complaint", lang)
                        } else {
                            I18n.getString("nav_complaints", lang)
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (showForm || selectedComplaintForDetail != null) {
                        IconButton(
                            onClick = {
                                if (selectedComplaintForDetail != null) {
                                    selectedComplaintForDetail = null
                                } else {
                                    showForm = false
                                    editingComplaint = null
                                    // Reset form
                                    title = ""
                                    category = "Police"
                                    description = ""
                                    address = ""
                                    isAnonymous = false
                                    imageUri = null
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (!showForm && selectedComplaintForDetail == null) {
                        // Language code indicator next to notify bell
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .border(1.dp, AccentOrange, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(lang.take(3).uppercase(), fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                        }

                        // Notifications indicator
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(containerColor = WarningRed) {
                                        Text(unreadNotificationsCount.toString(), color = Color.White)
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { showNotificationsDrawer = true }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
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
                showNotificationsDrawer -> {
                    NotificationsDrawer(
                        notifications = allNotifications,
                        lang = lang,
                        onClose = { showNotificationsDrawer = false },
                        onMarkRead = {
                            viewModel.markAllNotificationsRead(viewModel.userProfile.value.email)
                        }
                    )
                }
                selectedComplaintForDetail != null -> {
                    ComplaintDetailView(
                        complaint = selectedComplaintForDetail!!,
                        lang = lang,
                        viewModel = viewModel,
                        onBack = { selectedComplaintForDetail = null },
                        onEdit = {
                            editingComplaint = selectedComplaintForDetail
                            selectedComplaintForDetail = null
                            showForm = true
                        },
                        onDelete = {
                            viewModel.deleteComplaint(selectedComplaintForDetail!!.id) { success, msg ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        selectedComplaintForDetail = null
                                    }
                                }
                            }
                        }
                    )
                }
                showForm -> {
                    // Complaint filing/editing form
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(I18n.getString("complaint_title", lang), color = TextGray) },
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
                                .testTag("complaint_title_field")
                        )

                        // Category Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(I18n.getString("complaint_category", lang), color = TextGray) },
                                trailingIcon = {
                                    IconButton(onClick = { categoryExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = AccentOrange)
                                    }
                                },
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
                                    .clickable { categoryExpanded = true }
                            )
                            DropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false },
                                modifier = Modifier
                                    .background(CardBackground)
                                    .fillMaxWidth(0.9f)
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, color = Color.White) },
                                        onClick = {
                                            category = cat
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Description
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(I18n.getString("complaint_description", lang), color = TextGray) },
                            minLines = 4,
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
                                .testTag("complaint_desc_field")
                        )

                        // State Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedState,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(I18n.getString("state", lang), color = TextGray) },
                                trailingIcon = {
                                    IconButton(onClick = { stateExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = AccentOrange)
                                    }
                                },
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
                                    .clickable { stateExpanded = true }
                            )
                            DropdownMenu(
                                expanded = stateExpanded,
                                onDismissRequest = { stateExpanded = false },
                                modifier = Modifier
                                    .background(CardBackground)
                                    .fillMaxWidth(0.9f)
                            ) {
                                states.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s, color = Color.White) },
                                        onClick = {
                                            selectedState = s
                                            selectedDistrict = districtsMap[s]?.first() ?: ""
                                            stateExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // District Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedDistrict,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(I18n.getString("district", lang), color = TextGray) },
                                trailingIcon = {
                                    IconButton(onClick = { districtExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = AccentOrange)
                                    }
                                },
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
                                    .clickable { districtExpanded = true }
                            )
                            DropdownMenu(
                                expanded = districtExpanded,
                                onDismissRequest = { districtExpanded = false },
                                modifier = Modifier
                                    .background(CardBackground)
                                    .fillMaxWidth(0.9f)
                            ) {
                                districtsMap[selectedState]?.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d, color = Color.White) },
                                        onClick = {
                                            selectedDistrict = d
                                            districtExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Address / Location
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(I18n.getString("address", lang), color = TextGray) },
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
                                .testTag("complaint_address_field")
                        )

                        // Anonymous Option toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAnonymous = !isAnonymous }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isAnonymous,
                                onCheckedChange = { isAnonymous = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentOrange,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = TextGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = I18n.getString("anonymous_option", lang),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        // Image attachment layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, LightSlateBorder), RoundedCornerShape(8.dp))
                                .background(CardBackground)
                                .padding(12.dp)
                        ) {
                            Text(I18n.getString("upload_image", lang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (imageUri != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                ) {
                                    // Visual Mock Images Renderer based on mock tags
                                    val drawableResName = imageUri!!
                                    MockImageRenderer(drawableResName, modifier = Modifier.fillMaxSize())
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                            .clickable { imageUri = null }
                                            .padding(6.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = WarningRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { showImageSourceDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = LightBlueHighlight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(I18n.getString("upload_image", lang), color = Color.White)
                                }
                            }

                            if (isUploadingImage) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { uploadProgress },
                                        color = AccentOrange,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "${I18n.getString("uploading_image", lang)}: ${(uploadProgress * 100).toInt()}%",
                                        color = TextGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                if (title.isEmpty() || description.isEmpty() || address.isEmpty()) {
                                    Toast.makeText(context, I18n.getString("fill_all_fields", lang), Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (editingComplaint != null) {
                                    viewModel.updateComplaint(
                                        complaintId = editingComplaint!!.id,
                                        title = title,
                                        category = category,
                                        description = description,
                                        state = selectedState,
                                        district = selectedDistrict,
                                        address = address,
                                        imageUri = imageUri,
                                        isAnonymous = isAnonymous
                                    ) { success, msg ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            if (success) {
                                                showForm = false
                                                editingComplaint = null
                                            }
                                        }
                                    }
                                } else {
                                    viewModel.submitComplaint(
                                        title = title,
                                        category = category,
                                        description = description,
                                        state = selectedState,
                                        district = selectedDistrict,
                                        address = address,
                                        imageUri = imageUri,
                                        isAnonymous = isAnonymous
                                    ) { success, msg ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            if (success) {
                                                showForm = false
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("submit_complaint_btn")
                        ) {
                            Text(
                                text = if (editingComplaint != null) I18n.getString("update_button", lang) else I18n.getString("submit_button", lang),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                else -> {
                    // List of My Complaints Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header with floating action to file new
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = I18n.getString("my_complaints", lang),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Button(
                                onClick = {
                                    showForm = true
                                    editingComplaint = null
                                    title = ""
                                    category = "Police"
                                    description = ""
                                    address = ""
                                    isAnonymous = false
                                    imageUri = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("file_complaint_trigger")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(I18n.getString("submit_new_complaint", lang), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (myComplaints.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(1.dp, LightSlateBorder, RoundedCornerShape(8.dp))
                                    .background(CardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Assignment, contentDescription = "No complaints", tint = TextGray, modifier = Modifier.size(60.dp))
                                    Text("No complaints submitted yet", color = TextGray, fontSize = 14.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(myComplaints) { complaint ->
                                    ComplaintItemCard(
                                        complaint = complaint,
                                        lang = lang,
                                        onClick = { selectedComplaintForDetail = complaint }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Image Source Picker Dialog with Predefined Mock Categories
            if (showImageSourceDialog) {
                Dialog(onDismissRequest = { showImageSourceDialog = false }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Attach Incident Image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Select an incident archetype to simulate image upload.", color = TextGray, fontSize = 12.sp)
                            
                            val archetypes = listOf(
                                Triple("Road Damage / Pothole", "ic_pothole", Icons.Default.AddRoad),
                                Triple("Open Street Garbage Dump", "ic_garbage", Icons.Default.DeleteSweep),
                                Triple("Water Mainline Leakage", "ic_water_leak", Icons.Default.WaterDrop),
                                Triple("Cyber Online Scam Chat", "ic_scam_chat", Icons.Default.Phishing)
                            )

                            archetypes.forEach { arch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LightBlueHighlight.copy(alpha = 0.5f))
                                        .clickable {
                                            showImageSourceDialog = false
                                            isUploadingImage = true
                                            uploadProgress = 0f
                                            coroutineScope.launch {
                                                // progress timeline animation
                                                for (i in 1..10) {
                                                    delay(150)
                                                    uploadProgress = i / 10f
                                                }
                                                isUploadingImage = false
                                                imageUri = arch.second
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(arch.third, contentDescription = arch.first, tint = AccentOrange)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(arch.first, color = Color.White, fontSize = 14.sp)
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
fun ComplaintItemCard(
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

    val localizedStatus = when (complaint.status) {
        "Submitted" -> I18n.getString("status_submitted", lang)
        "Under Review" -> I18n.getString("status_under_review", lang)
        "Assigned" -> I18n.getString("status_assigned", lang)
        "In Progress" -> I18n.getString("status_in_progress", lang)
        "Resolved" -> I18n.getString("status_resolved", lang)
        "Rejected" -> I18n.getString("status_rejected", lang)
        else -> complaint.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LightSlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = complaint.id,
                    color = AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = localizedStatus,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = complaint.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = complaint.description,
                color = TextGray,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = complaint.category,
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }

                // Date Time
                val date = Date(complaint.timestamp)
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                Text(
                    text = sdf.format(date),
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ComplaintDetailView(
    complaint: CitizenComplaint,
    lang: String,
    viewModel: NyayaViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val replies by viewModel.getRepliesForComplaint(complaint.id).collectAsState(initial = emptyList())
    var citizenReplyMessage by remember { mutableStateOf("") }

    val statusColor = when (complaint.status) {
        "Submitted" -> Color(0xFF60A5FA)
        "Under Review" -> Color(0xFFFBBF24)
        "Assigned" -> Color(0xFFA78BFA)
        "In Progress" -> Color(0xFFFB923C)
        "Resolved" -> SuccessGreen
        "Rejected" -> WarningRed
        else -> TextGray
    }

    val localizedStatus = when (complaint.status) {
        "Submitted" -> I18n.getString("status_submitted", lang)
        "Under Review" -> I18n.getString("status_under_review", lang)
        "Assigned" -> I18n.getString("status_assigned", lang)
        "In Progress" -> I18n.getString("status_in_progress", lang)
        "Resolved" -> I18n.getString("status_resolved", lang)
        "Rejected" -> I18n.getString("status_rejected", lang)
        else -> complaint.status
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Tracker Pipeline (Beautiful M3 Timeline Stepper)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = I18n.getString("track_status", lang),
                    color = AccentOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val steps = listOf("Submitted", "Under Review", "Assigned", "In Progress", "Resolved/Rejected")
                val currentStepIndex = when (complaint.status) {
                    "Submitted" -> 0
                    "Under Review" -> 1
                    "Assigned" -> 2
                    "In Progress" -> 3
                    "Resolved", "Rejected" -> 4
                    else -> 0
                }

                steps.forEachIndexed { index, stepName ->
                    val isCompleted = index < currentStepIndex
                    val isActive = index == currentStepIndex
                    val stepColor = if (isCompleted) SuccessGreen else if (isActive) statusColor else TextGray
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (isActive) stepColor else stepColor.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(14.dp))
                            } else {
                                Text((index + 1).toString(), color = if (isActive) Color.Black else stepColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        val dispStepName = when (stepName) {
                            "Submitted" -> I18n.getString("status_submitted", lang)
                            "Under Review" -> I18n.getString("status_under_review", lang)
                            "Assigned" -> I18n.getString("status_assigned", lang)
                            "In Progress" -> I18n.getString("status_in_progress", lang)
                            "Resolved/Rejected" -> if (complaint.status == "Rejected") I18n.getString("status_rejected", lang) else I18n.getString("status_resolved", lang)
                            else -> stepName
                        }

                        Text(
                            text = dispStepName,
                            color = if (isActive) Color.White else TextGray,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }

                    if (index < steps.size - 1) {
                        Box(
                            modifier = Modifier
                                .padding(start = 11.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(if (index < currentStepIndex) SuccessGreen else LightSlateBorder)
                        )
                    }
                }
            }
        }

        // Details Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(I18n.getString("complaint_id", lang), color = TextGray, fontSize = 12.sp)
                    Text(complaint.id, color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Divider(color = LightSlateBorder)

                Text(complaint.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(complaint.description, color = Color.White, fontSize = 14.sp)

                Divider(color = LightSlateBorder)

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(I18n.getString("complaint_category", lang), color = TextGray, fontSize = 11.sp)
                        Text(complaint.category, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(I18n.getString("ai_predicted_dept", lang), color = TextGray, fontSize = 11.sp)
                        Text(complaint.aiPredictedDepartment, color = JusticeBlue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(I18n.getString("district", lang), color = TextGray, fontSize = 11.sp)
                        Text("${complaint.district}, ${complaint.state}", color = Color.White, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(I18n.getString("priority", lang), color = TextGray, fontSize = 11.sp)
                        val priorityText = when(complaint.priority) {
                            "Critical" -> I18n.getString("priority_critical", lang)
                            "High" -> I18n.getString("priority_high", lang)
                            "Medium" -> I18n.getString("priority_medium", lang)
                            else -> I18n.getString("priority_low", lang)
                        }
                        Text(priorityText, color = if (complaint.priority == "Critical") WarningRed else AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column {
                    Text(I18n.getString("address", lang), color = TextGray, fontSize = 11.sp)
                    Text(complaint.address, color = Color.White, fontSize = 13.sp)
                }

                // If image is attached
                if (complaint.imageUri != null) {
                    Divider(color = LightSlateBorder)
                    Text(I18n.getString("image_preview", lang), color = TextGray, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        MockImageRenderer(complaint.imageUri, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Action details remarks / officer
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(I18n.getString("assigned_officer", lang), color = TextGray, fontSize = 11.sp)
                Text(
                    text = if (complaint.assignedOfficer.isEmpty()) I18n.getString("not_assigned", lang) else complaint.assignedOfficer,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(I18n.getString("authority_remarks", lang), color = TextGray, fontSize = 11.sp)
                Text(
                    text = if (complaint.authorityRemarks.isEmpty()) I18n.getString("no_remarks", lang) else complaint.authorityRemarks,
                    color = Color.White,
                    fontStyle = if (complaint.authorityRemarks.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 13.sp
                )
            }
        }

        // Real-Time Chat & Conversation History Section
        if (replies.isNotEmpty()) {
            Text("Conversation History & Updates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
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

        // Two-way reply communication box
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightSlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send Message to Authority Portal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                
                OutlinedTextField(
                    value = citizenReplyMessage,
                    onValueChange = { citizenReplyMessage = it },
                    placeholder = { Text("Type any additional details or messages here...", color = TextGray) },
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
                        .testTag("citizen_reply_message_input")
                )

                Button(
                    onClick = {
                        if (citizenReplyMessage.trim().isEmpty()) {
                            Toast.makeText(context, "Please type a message first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.sendComplaintReply(
                            complaintId = complaint.id,
                            message = citizenReplyMessage,
                            updatedStatus = complaint.status,
                            attachmentPath = null
                        ) { success, msg ->
                            coroutineScope.launch {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    citizenReplyMessage = ""
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(14.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Message", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Editable context buttons
        if (complaint.status == "Submitted") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onEdit() },
                    colors = ButtonDefaults.buttonColors(containerColor = LightBlueHighlight),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(I18n.getString("edit_button", lang), color = Color.White)
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(I18n.getString("delete_button", lang), color = Color.White)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Complaint?", color = Color.White) },
            text = { Text("Are you sure you want to permanently delete this complaint?", color = TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = WarningRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
fun NotificationsDrawer(
    notifications: List<com.example.db.Notification>,
    lang: String,
    onClose: () -> Unit,
    onMarkRead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkIndigo)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(I18n.getString("notifications", lang), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            if (notifications.any { !it.isRead }) {
                TextButton(onClick = onMarkRead) {
                    Text(I18n.getString("mark_all_read", lang), color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "No alerts", tint = TextGray, modifier = Modifier.size(50.dp))
                    Text(I18n.getString("no_notifications", lang), color = TextGray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { notif ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) CardBackground else LightBlueHighlight.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (notif.isHighPriority) WarningRed else LightSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.title,
                                    color = if (notif.isHighPriority) WarningRed else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (!notif.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(AccentOrange, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = notif.message, color = TextGray, fontSize = 12.sp)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            val date = Date(notif.timestamp)
                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            Text(
                                text = sdf.format(date),
                                color = TextGray.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockImageRenderer(drawableName: String?, modifier: Modifier = Modifier) {
    // Elegant adaptive vector graphic representation of mock uploaded images to guarantee zero runtime crashes
    val backgroundBrush = when (drawableName) {
        "ic_pothole" -> Brush.verticalGradient(listOf(Color(0xFF451A03), Color(0xFF1C1917)))
        "ic_garbage" -> Brush.verticalGradient(listOf(Color(0xFF065F46), Color(0xFF064E3B)))
        "ic_water_leak" -> Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF172554)))
        "ic_scam_chat" -> Brush.verticalGradient(listOf(Color(0xFF581C87), Color(0xFF3B0764)))
        else -> Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A)))
    }

    val icon = when (drawableName) {
        "ic_pothole" -> Icons.Default.AddRoad
        "ic_garbage" -> Icons.Default.DeleteSweep
        "ic_water_leak" -> Icons.Default.WaterDrop
        "ic_scam_chat" -> Icons.Default.Phishing
        else -> Icons.Default.Image
    }

    val labelText = when (drawableName) {
        "ic_pothole" -> "Uploaded Image: Pothole & Road Cracks Detected"
        "ic_garbage" -> "Uploaded Image: Garbage Dumping & Public Waste"
        "ic_water_leak" -> "Uploaded Image: Main Waterline Leakage"
        "ic_scam_chat" -> "Uploaded Image: Online Phishing / Scam Chat Transcript"
        else -> "Attached Image"
    }

    Box(
        modifier = modifier
            .background(backgroundBrush)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = "Mock Uploaded Image Icon", tint = AccentOrange, modifier = Modifier.size(48.dp))
            Text(
                text = labelText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "GPS STAMP: 28.6139° N, 77.2090° E (Authenticated Secure Storage)",
                color = TextGray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
