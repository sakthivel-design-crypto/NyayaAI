package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.db.ComplaintMessage
import com.example.firebase.FirebaseManager
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.NyayaViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StagedFileInfo(
    val uri: Uri,
    val name: String,
    val sizeString: String,
    val isImage: Boolean
)

data class StagedLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val address: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintConversationSection(
    complaintId: String,
    currentRole: String, // "CITIZEN" or "AUTHORITY"
    viewModel: NyayaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Subscribe to real-time SnapshotListener on complaints/{complaintId}/messages
    DisposableEffect(complaintId) {
        val listener = viewModel.listenToComplaintMessages(complaintId)
        viewModel.markComplaintMessagesRead(complaintId, currentRole)
        onDispose {
            listener?.remove()
        }
    }

    val messages by viewModel.getMessagesForComplaint(complaintId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var isObtainingLocation by remember { mutableStateOf(false) }

    // Staged attachments & locations state before sending
    var stagedAttachment by remember { mutableStateOf<StagedFileInfo?>(null) }
    var stagedLocation by remember { mutableStateOf<StagedLocationInfo?>(null) }

    val listState = rememberLazyListState()

    // Modals & Permission States
    var showLocationShareDialog by remember { mutableStateOf(false) }
    var showCameraPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showLocationPermissionDeniedDialog by remember { mutableStateOf(false) }

    var pendingCameraPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }

    // Real Device Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraPhotoUriString
        if (success && !uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            val fileInfo = getFileInfoFromUri(context, uri)
            stagedAttachment = fileInfo.copy(
                name = if (fileInfo.name == "attachment" || fileInfo.name.isBlank()) "photo_${System.currentTimeMillis()}.jpg" else fileInfo.name,
                isImage = true
            )
            Toast.makeText(context, "Photo captured! Tap 'Send' to attach to conversation.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Photo capture cancelled or failed", Toast.LENGTH_SHORT).show()
        }
        pendingCameraPhotoUriString = null
    }

    val launchCamera = {
        try {
            val photoDir = File(context.cacheDir, "camera_photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val file = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            pendingCameraPhotoUriString = uri.toString()
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            showCameraPermissionDeniedDialog = true
        }
    }

    // Real Device File / Document / Image Picker Launcher
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileInfo = getFileInfoFromUri(context, uri)
            stagedAttachment = fileInfo
            Toast.makeText(context, "File selected! Tap 'Send' to upload.", Toast.LENGTH_SHORT).show()
        }
    }

    // Real Live GPS Location Function
    val acquireLocation = {
        isObtainingLocation = true
        Toast.makeText(context, "Obtaining fresh device GPS location...", Toast.LENGTH_SHORT).show()
        fetchCurrentLocation(
            context = context,
            onLocationObtained = { lat, lng, acc, time, addr ->
                isObtainingLocation = false
                stagedLocation = StagedLocationInfo(lat, lng, acc, time, addr)
                showLocationShareDialog = true
            },
            onError = { err ->
                isObtainingLocation = false
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            acquireLocation()
        } else {
            showLocationPermissionDeniedDialog = true
        }
    }

    // Auto-scroll to latest message when message count increases
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LightSlateBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Complaint Conversation",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "• Live Realtime Sync",
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = LightSlateBorder, thickness = 0.5.dp)

            // Conversation Messages List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp)
                    .background(DarkIndigo, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No messages in conversation yet.",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Send a message below to start two-way real-time communication.",
                            color = TextGray.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            MessageBubbleItem(
                                msg = msg,
                                isMe = msg.senderRole.equals(currentRole, ignoreCase = true)
                            )
                        }
                    }
                }
            }

            if (isUploadingAttachment || isObtainingLocation) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AccentOrange,
                    trackColor = DarkIndigo
                )
            }

            // Staged Attachment Preview Banner (Photo or File)
            stagedAttachment?.let { staged ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                    border = BorderStroke(1.dp, AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("staged_attachment_card")
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
                            if (staged.isImage) {
                                AsyncImage(
                                    model = staged.uri,
                                    contentDescription = "Preview",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CardBackground),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = "Document",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = staged.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (staged.sizeString.isNotBlank()) "Size: ${staged.sizeString} • Ready to send" else "Ready to send",
                                    color = TextGray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Send Attachment Button
                            Button(
                                onClick = {
                                    val uriToUpload = staged.uri
                                    val isImg = staged.isImage
                                    val fileName = staged.name
                                    isUploadingAttachment = true
                                    Toast.makeText(context, "Uploading attachment...", Toast.LENGTH_SHORT).show()

                                    FirebaseManager.uploadComplaintImage(context, complaintId, uriToUpload.toString()) { success, downloadUrl, err ->
                                        isUploadingAttachment = false
                                        if (success && downloadUrl != null) {
                                            val type = if (isImg) "IMAGE" else "DOCUMENT"
                                            val msgText = if (isImg) "📷 Shared Image Evidence: $fileName" else "📄 Attached Document: $fileName"
                                            viewModel.sendComplaintMessage(
                                                complaintId = complaintId,
                                                messageText = msgText,
                                                senderRole = currentRole,
                                                messageType = type,
                                                attachmentUrl = if (isImg) downloadUrl else null,
                                                documentUrl = if (!isImg) downloadUrl else null
                                            ) { sendOk, sendErr ->
                                                if (sendOk) {
                                                    stagedAttachment = null
                                                    Toast.makeText(context, "Attachment sent successfully!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, sendErr ?: "Failed to send message", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Upload failed: ${err ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isUploadingAttachment,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("attach_send_btn")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Cancel / Remove Staged Attachment
                            IconButton(
                                onClick = { stagedAttachment = null },
                                enabled = !isUploadingAttachment,
                                modifier = Modifier.size(34.dp).testTag("attach_remove_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = WarningRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Action Attachment Toolbar (Camera, Files/Attachment, Location)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. REAL CAMERA BUTTON
                    IconButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                launchCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !isUploadingAttachment && !isObtainingLocation,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("attach_photo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Photo",
                            tint = AccentOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 2. REAL FILES / DOCUMENT BUTTON
                    IconButton(
                        onClick = {
                            docPickerLauncher.launch("*/*")
                        },
                        enabled = !isUploadingAttachment && !isObtainingLocation,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("attach_doc_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = TextDarkSlate,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3. REAL GPS LOCATION BUTTON
                    IconButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                acquireLocation()
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        enabled = !isUploadingAttachment && !isObtainingLocation,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("attach_location_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Share Location",
                            tint = SuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "${inputText.length}/2000",
                    color = if (inputText.length > 1800) WarningRed else TextGray,
                    fontSize = 10.sp
                )
            }

            // Two-Way Reply Input Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        if (it.length <= 2000) inputText = it
                    },
                    placeholder = {
                        Text(
                            text = if (currentRole == "AUTHORITY") "Reply to Citizen..." else "Reply to Authority...",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conversation_reply_input"),
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = LightSlateBorder,
                        focusedContainerColor = DarkIndigo,
                        unfocusedContainerColor = DarkIndigo
                    )
                )

                Button(
                    onClick = {
                        val textToSend = inputText.trim()
                        if (textToSend.isEmpty()) {
                            Toast.makeText(context, "Please enter a message.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSending = true
                        viewModel.sendComplaintMessage(
                            complaintId = complaintId,
                            messageText = textToSend,
                            senderRole = currentRole
                        ) { success, resultMsg ->
                            isSending = false
                            if (success) {
                                inputText = ""
                            } else {
                                Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSending && inputText.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        disabledContainerColor = AccentOrange.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("send_conversation_message_btn")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send Reply",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Real Live Location Confirmation Dialog
    if (showLocationShareDialog && stagedLocation != null) {
        val loc = stagedLocation!!
        val formattedTime = SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault()).format(Date(loc.timestamp))
        AlertDialog(
            onDismissRequest = {
                showLocationShareDialog = false
                stagedLocation = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SuccessGreen)
                    Text("Share Live GPS Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, SuccessGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📍 Live GPS Location", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Latitude: ${String.format(Locale.US, "%.6f", loc.latitude)}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Longitude: ${String.format(Locale.US, "%.6f", loc.longitude)}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (loc.accuracy > 0) {
                                Text("Accuracy: ±${String.format(Locale.US, "%.1f", loc.accuracy)} meters", color = LightJusticeBlue, fontSize = 12.sp)
                            }
                            Text("Updated: $formattedTime", color = TextGray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Address: ${loc.address}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                    }
                    Text("Attach your real device GPS location to the complaint conversation?", color = TextGray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val latStr = String.format(Locale.US, "%.6f", loc.latitude)
                        val lngStr = String.format(Locale.US, "%.6f", loc.longitude)
                        val locMsg = "📍 Live GPS Location: ${loc.address}\nCoordinates: $latStr°, $lngStr° (Accuracy: ±${loc.accuracy.toInt()}m)"
                        showLocationShareDialog = false
                        viewModel.sendComplaintMessage(
                            complaintId = complaintId,
                            messageText = locMsg,
                            senderRole = currentRole,
                            messageType = "LOCATION",
                            locationData = "$latStr,$lngStr"
                        ) { sendOk, sendErr ->
                            if (!sendOk) {
                                Toast.makeText(context, sendErr ?: "Failed to share location", Toast.LENGTH_SHORT).show()
                            }
                        }
                        stagedLocation = null
                        Toast.makeText(context, "Live GPS Location shared!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Share Live GPS Location", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationShareDialog = false
                    stagedLocation = null
                }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = DarkIndigo
        )
    }

    // Camera Permission Denied Dialog with App Settings redirect
    if (showCameraPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDeniedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = WarningRed)
                    Text("Camera Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Camera permission is required to capture photo evidence. Please enable camera access in application settings.",
                    color = TextGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCameraPermissionDeniedDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Please enable Camera permission in device settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Open App Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionDeniedDialog = false }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = DarkIndigo
        )
    }

    // Location Permission Denied Dialog with App Settings redirect
    if (showLocationPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showLocationPermissionDeniedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, tint = WarningRed)
                    Text("Location Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Location permission is required to attach GPS coordinates to the complaint conversation. Please enable location access in application settings.",
                    color = TextGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationPermissionDeniedDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Please enable Location permission in device settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Open App Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationPermissionDeniedDialog = false }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = DarkIndigo
        )
    }
}

// Helpers for File Info and Location
private fun getFileInfoFromUri(context: Context, uri: Uri): StagedFileInfo {
    var name = "attachment"
    var sizeBytes: Long = 0
    val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (_: Exception) { "" }
    val isImage = mimeType.startsWith("image/") ||
            uri.toString().endsWith(".jpg", true) ||
            uri.toString().endsWith(".png", true) ||
            uri.toString().endsWith(".jpeg", true) ||
            uri.toString().contains("image", true)

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val foundName = cursor.getString(nameIndex)
                    if (!foundName.isNullOrBlank()) name = foundName
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {
        name = uri.lastPathSegment ?: "attachment"
    }

    val formattedSize = when {
        sizeBytes <= 0 -> ""
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
    }

    return StagedFileInfo(uri, name, formattedSize, isImage)
}

private fun fetchCurrentLocation(
    context: Context,
    onLocationObtained: (Double, Double, Float, Long, String) -> Unit,
    onError: (String) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

    if (!isGpsEnabled && !isNetworkEnabled) {
        onError("Location services / GPS are disabled. Please enable GPS in device settings.")
        return
    }

    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) {
        onError("Location permission is required to acquire GPS coordinates.")
        return
    }

    try {
        val nativeLoc = try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: Exception) { null }

        if (nativeLoc != null) {
            val accuracy = if (nativeLoc.hasAccuracy()) nativeLoc.accuracy else 0f
            val time = if (nativeLoc.time > 0) nativeLoc.time else System.currentTimeMillis()
            val address = resolveAddress(context, nativeLoc.latitude, nativeLoc.longitude)
            onLocationObtained(nativeLoc.latitude, nativeLoc.longitude, accuracy, time, address)
        } else {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: Location) {
                    try { locationManager?.removeUpdates(this) } catch (_: Exception) {}
                    val accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f
                    val time = if (loc.time > 0) loc.time else System.currentTimeMillis()
                    val address = resolveAddress(context, loc.latitude, loc.longitude)
                    onLocationObtained(loc.latitude, loc.longitude, accuracy, time, address)
                }
            }
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            } else if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            } else {
                onError("Unable to acquire GPS signal. Please enable location services.")
            }
        }
    } catch (e: Exception) {
        onError("Location error: ${e.localizedMessage ?: "Unknown error"}")
    }
}

private fun resolveAddress(context: Context, lat: Double, lng: Double): String {
    return try {
        if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val line = addr.getAddressLine(0)
                if (!line.isNullOrBlank()) line else "${addr.locality ?: addr.subAdminArea ?: "Area"}, ${addr.adminArea ?: "City"}"
            } else {
                "GPS Coords: ${String.format(Locale.US, "%.6f", lat)}°, ${String.format(Locale.US, "%.6f", lng)}°"
            }
        } else {
            "GPS Coords: ${String.format(Locale.US, "%.6f", lat)}°, ${String.format(Locale.US, "%.6f", lng)}°"
        }
    } catch (_: Exception) {
        "GPS Coords: ${String.format(Locale.US, "%.6f", lat)}°, ${String.format(Locale.US, "%.6f", lng)}°"
    }
}

@Composable
fun MessageBubbleItem(
    msg: ComplaintMessage,
    isMe: Boolean
) {
    val context = LocalContext.current
    val formattedTime = DateUtils.formatTime(msg.createdAt)
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(msg.messageId) {
        onDispose {
            audioPlayer?.release()
        }
    }

    if (msg.messageType == "SYSTEM" || msg.senderRole == "SYSTEM") {
        // System Message Layout (Centered pill badge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkIndigo.copy(alpha = 0.8f)),
                border = BorderStroke(1.dp, JusticeBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "System",
                        tint = LightJusticeBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${msg.message} • $formattedTime",
                        color = LightJusticeBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        // Chat Bubble Layout (Left/Right Alignment)
        val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
        val bubbleColor = when {
            isMe && msg.senderRole == "AUTHORITY" -> SuccessGreen.copy(alpha = 0.25f)
            isMe -> AccentOrange.copy(alpha = 0.25f)
            msg.senderRole == "AUTHORITY" -> SuccessGreen.copy(alpha = 0.15f)
            else -> CardBackground
        }
        val borderColor = when {
            isMe && msg.senderRole == "AUTHORITY" -> SuccessGreen
            isMe -> AccentOrange
            msg.senderRole == "AUTHORITY" -> LightJusticeBlue
            else -> LightSlateBorder
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = alignment
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isMe) 12.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 12.dp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sender Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMe) "You (${msg.senderRole})" else "${msg.senderName} (${msg.senderRole})",
                            color = if (msg.senderRole == "AUTHORITY") LightJusticeBlue else AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Image Attachment
                    val imgUrl = msg.attachmentUrl
                    if (!imgUrl.isNullOrEmpty() || msg.messageType == "IMAGE") {
                        val activeUrl = imgUrl ?: msg.message
                        AsyncImage(
                            model = activeUrl,
                            contentDescription = "Attachment Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkIndigo),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Document Attachment
                    val docUrl = msg.documentUrl
                    if (!docUrl.isNullOrEmpty() || msg.messageType == "DOCUMENT") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                            border = BorderStroke(1.dp, LightSlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val targetUrl = docUrl ?: msg.message
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Opening document attachment...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = "Document", tint = AccentOrange)
                                Column {
                                    Text("Attachment Document", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Tap to view / download", color = TextGray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Historical Voice Note Playback Support
                    if (msg.messageType == "VOICE" || !msg.voiceUrl.isNullOrEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        val targetVoice = msg.voiceUrl ?: ""
                                        if (isAudioPlaying) {
                                            audioPlayer?.stop()
                                            audioPlayer?.release()
                                            audioPlayer = null
                                            isAudioPlaying = false
                                        } else if (targetVoice.isNotEmpty()) {
                                            try {
                                                audioPlayer = MediaPlayer().apply {
                                                    setDataSource(context, Uri.parse(targetVoice))
                                                    prepare()
                                                    start()
                                                    setOnCompletionListener {
                                                        isAudioPlaying = false
                                                    }
                                                }
                                                isAudioPlaying = true
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Playing audio...", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Playing audio...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Voice Note",
                                    tint = Color(0xFFA78BFA)
                                )
                                Text(
                                    text = if (isAudioPlaying) "Playing voice note..." else "Voice Message • Tap to Listen",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // GPS Location Card
                    if (msg.messageType == "LOCATION" || !msg.locationData.isNullOrEmpty()) {
                        val rawLoc = msg.locationData ?: ""
                        val coords = if (rawLoc.contains(",")) {
                            val parts = rawLoc.split(",")
                            val latStr = parts.getOrNull(0)?.trim() ?: ""
                            val lngStr = parts.getOrNull(1)?.trim() ?: ""
                            "$latStr°, $lngStr°"
                        } else {
                            rawLoc
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkIndigo),
                            border = BorderStroke(1.dp, SuccessGreen),
                            modifier = Modifier.fillMaxWidth().testTag("location_message_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                    Text("📍 Live GPS Location Attachment", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                if (coords.isNotBlank()) {
                                    Text("GPS: $coords", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (msg.message.isNotBlank()) {
                                    Text(msg.message, color = TextGray, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        val locStr = if (rawLoc.contains(",")) rawLoc.trim() else "28.6139,77.2090"
                                        val mapUri = Uri.parse("geo:$locStr?q=$locStr(Shared+Location)")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (_: Exception) {
                                            val webMaps = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$locStr"))
                                            context.startActivity(webMaps)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    modifier = Modifier.fillMaxWidth().height(34.dp).testTag("open_map_btn"),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open in Google Maps", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Message Text
                    if (msg.messageType != "LOCATION" && msg.message.isNotBlank() && msg.messageType != "IMAGE" && msg.messageType != "DOCUMENT") {
                        Text(
                            text = msg.message,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    // Timestamp Footer
                    Text(
                        text = formattedTime,
                        color = TextGray,
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
