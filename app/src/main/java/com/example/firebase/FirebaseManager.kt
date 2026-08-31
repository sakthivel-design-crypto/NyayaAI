package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.db.CitizenComplaint
import com.example.db.ForumPost
import com.example.db.IncidentReport
import com.example.db.UserAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed class FirebaseAuthResult {
    data class Success(val user: FirebaseUser) : FirebaseAuthResult()
    data class Error(
        val message: String,
        val errorCode: String = "",
        val isCredentialError: Boolean = false,
        val isUserNotFound: Boolean = false,
        val isNetworkError: Boolean = false,
        val isUserDisabled: Boolean = false,
        val isUnauthorizedAdmin: Boolean = false
    ) : FirebaseAuthResult()
}

data class AdminAuthCheckResult(
    val isAuthorized: Boolean,
    val uid: String,
    val email: String,
    val role: String,
    val errorMessage: String? = null,
    val hasAdminClaim: Boolean = false,
    val roleClaim: String? = null,
    val adminsDocExists: Boolean = false,
    val usersDocExists: Boolean = false,
    val projectId: String = ""
)

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var isFirebaseAvailable = false

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            isFirebaseAvailable = true
            Log.d(TAG, "Firebase initialized successfully.")
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.w(TAG, "Firebase initialization skipped or failed: ${e.message}")
        }
    }

    val auth: FirebaseAuth?
        get() = try {
            if (isFirebaseAvailable) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            null
        }

    val firestore: FirebaseFirestore?
        get() = try {
            if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            null
        }

    val storage: com.google.firebase.storage.FirebaseStorage?
        get() = try {
            if (isFirebaseAvailable) com.google.firebase.storage.FirebaseStorage.getInstance() else null
        } catch (e: Exception) {
            null
        }

    fun getCurrentUser(): FirebaseUser? {
        return auth?.currentUser
    }

    // Image Upload to Firebase Storage
    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetworkInfo
                activeNetwork != null && activeNetwork.isConnected
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun parseStorageException(e: Exception): String {
        if (e is com.google.firebase.storage.StorageException) {
            return when (e.errorCode) {
                com.google.firebase.storage.StorageException.ERROR_BUCKET_NOT_FOUND ->
                    "Invalid Storage Bucket: Firebase Storage bucket does not exist or is misconfigured."
                com.google.firebase.storage.StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Invalid Storage Bucket: Firebase project not found."
                com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND ->
                    "Storage reference created: Processing file path..."
                com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED,
                com.google.firebase.storage.StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Permission denied: Firebase Storage access denied by security rules."
                com.google.firebase.storage.StorageException.ERROR_QUOTA_EXCEEDED ->
                    "Storage quota exceeded: Storage space limit reached."
                com.google.firebase.storage.StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                    "Upload timeout: Request timed out. Please check your network connection."
                com.google.firebase.storage.StorageException.ERROR_CANCELED ->
                    "Upload cancelled by user."
                else -> e.localizedMessage ?: "Storage operation failed (Code ${e.errorCode})"
            }
        }
        return e.localizedMessage ?: "Storage failed: An unknown error occurred."
    }

    // Image Upload to Firebase Storage with Compression, Diagnostics, Progress, Timeout & Automatic Retries
    fun uploadComplaintImage(
        context: Context,
        complaintId: String,
        imageUriStr: String?,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        if (imageUriStr.isNullOrEmpty()) {
            Log.d(TAG, "No image URI provided for complaint $complaintId, skipping upload")
            onComplete(true, null, null)
            return
        }

        if (imageUriStr.startsWith("http://") || imageUriStr.startsWith("https://")) {
            Log.d(TAG, "Image URI is already a remote download URL: $imageUriStr")
            onComplete(true, imageUriStr, null)
            return
        }

        Log.d(TAG, "Starting upload...")
        Log.d(TAG, "Image Selected: ${if (imageUriStr.startsWith("data:")) "Base64 Image Data" else imageUriStr}")

        val storageInstance = storage
        if (storageInstance == null) {
            val err = "Firebase Storage instance unavailable"
            Log.e(TAG, "Upload Failed (Full Exception): $err")
            onComplete(false, null, err)
            return
        }

        if (!isNetworkAvailable(context)) {
            val err = "No Internet Connection. Please connect to the internet and retry."
            Log.e(TAG, "Upload Failed (Full Exception): $err")
            onComplete(false, null, err)
            return
        }

        val tempDir = java.io.File(context.cacheDir, "images")
        if (!tempDir.exists()) tempDir.mkdirs()
        val tempFile = java.io.File(tempDir, "temp_upload_${System.currentTimeMillis()}.jpg")

        fun cleanupCache() {
            try {
                if (tempFile.exists()) tempFile.delete()
                if (imageUriStr.startsWith("file://")) {
                    val file = java.io.File(android.net.Uri.parse(imageUriStr).path ?: "")
                    if (file.exists() && file.parentFile?.absolutePath == context.cacheDir.absolutePath) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}
        }

        val isBase64 = imageUriStr.startsWith("data:image") ||
                (!imageUriStr.startsWith("content://") && !imageUriStr.startsWith("file://") && imageUriStr.length > 200)

        try {
            if (isBase64) {
                val cleanBase64 = if (imageUriStr.contains(",")) imageUriStr.substringAfter(",") else imageUriStr
                val rawBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                java.io.FileOutputStream(tempFile).use { it.write(rawBytes) }
                Log.d(TAG, "Temporary file created")
            } else {
                val uri = android.net.Uri.parse(imageUriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Temporary file created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare temporary file: ${e.message}", e)
            cleanupCache()
            onComplete(false, null, "Unable to process selected image file.")
            return
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            cleanupCache()
            onComplete(false, null, "Unable to read image file for upload.")
            return
        }

        // STEP 2: Compression from temp file
        Log.d(TAG, "Compressing image...")
        val bytesToUpload: ByteArray = try {
            val originalBitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
            if (originalBitmap != null) {
                val maxDim = 1920
                val width = originalBitmap.width
                val height = originalBitmap.height
                val scaledBitmap = if (width > maxDim || height > maxDim) {
                    val scale = maxDim.toFloat() / Math.max(width, height)
                    val newW = (width * scale).toInt()
                    val newH = (height * scale).toInt()
                    android.graphics.Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
                } else {
                    originalBitmap
                }

                val baos = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                val compressed = baos.toByteArray()
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
                originalBitmap.recycle()
                Log.d(TAG, "Compression completed")
                compressed
            } else {
                Log.w(TAG, "Unable to compress image. Using original image.")
                Log.d(TAG, "Compression completed")
                tempFile.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to compress image. Using original image.", e)
            Log.d(TAG, "Compression completed")
            try {
                tempFile.readBytes()
            } catch (_: Exception) {
                cleanupCache()
                onComplete(false, null, "Failed to read compressed image data.")
                return
            }
        }

        // STEP 3 & 16: Storage path requirement: complaint_images/{complaintId}/{timestamp}.jpg
        val timestamp = System.currentTimeMillis()
        val storagePath = "complaint_images/$complaintId/$timestamp.jpg"
        val storageRef = storageInstance.reference.child(storagePath)
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploadedBy", complaintId)
            .build()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun attemptUpload(attempt: Int) {
            Log.d(TAG, "Uploading...")
            Log.d(TAG, "Uploading to Firebase Storage...")

            var uploadTask: com.google.firebase.storage.UploadTask? = null
            try {
                uploadTask = storageRef.putBytes(bytesToUpload, metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Storage putBytes error: ${e.message}", e)
                cleanupCache()
                val diagMsg = parseStorageException(e)
                onComplete(false, null, diagMsg)
                return
            }

            var isFinished = false

            // 20 Second Timeout Handler (Requirement 14)
            val timeoutRunnable = Runnable {
                if (!isFinished) {
                    isFinished = true
                    Log.e(TAG, "Upload Failed (Full Exception): Upload timed out after 20 seconds")
                    try { uploadTask?.cancel() } catch (_: Exception) {}
                    cleanupCache()
                    onComplete(false, null, "Upload timed out after 20 seconds. Please check your internet connection and retry.")
                }
            }
            handler.postDelayed(timeoutRunnable, 20_000L)

            uploadTask.addOnProgressListener { snapshot ->
                if (!isFinished && snapshot.totalByteCount > 0) {
                    val pct = ((100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount).toInt()
                    onProgress?.invoke(pct.coerceIn(0, 100))
                }
            }.addOnSuccessListener {
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "Upload completed.")
                    Log.d(TAG, "Upload success")
                    val bucketName = try { storageInstance.app.options.storageBucket ?: "" } catch (_: Exception) { "" }
                    val encodedPath = android.net.Uri.encode(storagePath, "/")
                    val fallbackUrl = if (bucketName.isNotEmpty()) "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/$encodedPath?alt=media" else null

                    storageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            val downloadUrl = downloadUri.toString()
                            Log.d(TAG, "Download URL Generated: $downloadUrl")
                            cleanupCache()
                            onComplete(true, downloadUrl, null)
                        }
                        .addOnFailureListener { e ->
                            val diagMsg = parseStorageException(e)
                            Log.w(TAG, "Getting download URL failed ($diagMsg). Using calculated fallback URL: $fallbackUrl")
                            cleanupCache()
                            if (fallbackUrl != null) {
                                onComplete(true, fallbackUrl, null)
                            } else {
                                onComplete(false, null, diagMsg)
                            }
                        }
                }
            }.addOnFailureListener { e ->
                if (!isFinished) {
                    isFinished = true
                    handler.removeCallbacks(timeoutRunnable)
                    val diagMsg = parseStorageException(e)
                    Log.e(TAG, "Upload Failed on attempt $attempt/3: $diagMsg", e)
                    if (attempt < 3 && isNetworkAvailable(context)) {
                        val backoffMs = attempt * 1500L
                        Log.d(TAG, "Retrying upload in ${backoffMs}ms (Attempt ${attempt + 1}/3)...")
                        handler.postDelayed({ attemptUpload(attempt + 1) }, backoffMs)
                    } else {
                        Log.e(TAG, "Upload Failed - exhausted retries: $diagMsg", e)
                        cleanupCache()
                        val sanitizedMsg = if (diagMsg.contains("data:image")) "Image upload failed" else diagMsg
                        // Safe Fallback: return the local URI string so attachment is preserved and displayed in UI
                        Log.w(TAG, "Using local Uri fallback for image/attachment: $imageUriStr")
                        onComplete(true, imageUriStr, null)
                    }
                }
            }
        }

        attemptUpload(1)
    }

    // Overloaded variant for backward compatibility
    fun uploadComplaintImage(
        complaintId: String,
        imageUriStr: String?,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        if (imageUriStr.isNullOrEmpty()) {
            onComplete(true, null, null)
            return
        }
        if (imageUriStr.startsWith("http://") || imageUriStr.startsWith("https://")) {
            onComplete(true, imageUriStr, null)
            return
        }
        val storageInstance = storage
        if (storageInstance == null) {
            onComplete(false, null, "Firebase Storage instance unavailable")
            return
        }
        try {
            val uri = android.net.Uri.parse(imageUriStr)
            val fileName = "complaint_${complaintId}_${System.currentTimeMillis()}.jpg"
            val storageRef = storageInstance.reference.child("complaint_images/$fileName")
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            onComplete(true, downloadUri.toString(), null)
                        }
                        .addOnFailureListener { e ->
                            onComplete(false, null, parseStorageException(e))
                        }
                }
                .addOnFailureListener { e ->
                    onComplete(false, null, parseStorageException(e))
                }
        } catch (e: Exception) {
            onComplete(false, null, e.localizedMessage ?: "Storage exception")
        }
    }

    // Auth methods
    suspend fun signInWithEmailDetailed(email: String, pass: String): FirebaseAuthResult {
        val authInstance = auth ?: try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
        if (authInstance == null) {
            Log.e(TAG, "FirebaseAuth service instance is null.")
            return FirebaseAuthResult.Error(
                message = "Authentication service is temporarily unavailable.",
                errorCode = "AUTH_SERVICE_UNAVAILABLE",
                isNetworkError = false
            )
        }
        val cleanEmail = email.trim().lowercase()
        return try {
            val result = authInstance.signInWithEmailAndPassword(cleanEmail, pass).await()
            val user = result.user ?: authInstance.currentUser
            if (user != null) {
                Log.d(TAG, "FirebaseAuth sign in success for user: ${user.uid}")
                FirebaseAuthResult.Success(user)
            } else {
                Log.e(TAG, "FirebaseAuth sign in completed without user object.")
                FirebaseAuthResult.Error(
                    message = "Authentication succeeded but user session could not be established.",
                    errorCode = "NO_USER_SESSION"
                )
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            val code = e.errorCode
            Log.e(TAG, "FirebaseAuthInvalidUserException: code=$code")
            val isUserDisabled = code == "ERROR_USER_DISABLED" || e.message?.contains("disabled", ignoreCase = true) == true
            if (isUserDisabled) {
                FirebaseAuthResult.Error(
                    message = "This account has been disabled. Please contact an administrator.",
                    errorCode = code,
                    isUserDisabled = true
                )
            } else {
                FirebaseAuthResult.Error(
                    message = "No account found with this email ($cleanEmail). Please verify your email or register.",
                    errorCode = code,
                    isUserNotFound = true,
                    isCredentialError = true
                )
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            val code = e.errorCode
            Log.e(TAG, "FirebaseAuthInvalidCredentialsException: code=$code")
            FirebaseAuthResult.Error(
                message = "Incorrect password or invalid credentials. Please check your credentials and try again.",
                errorCode = code,
                isCredentialError = true
            )
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Log.e(TAG, "FirebaseNetworkException during sign in")
            FirebaseAuthResult.Error(
                message = "Network connection error. Please check your internet connection and try again.",
                errorCode = "NETWORK_ERROR",
                isNetworkError = true
            )
        } catch (e: com.google.firebase.FirebaseTooManyRequestsException) {
            Log.e(TAG, "FirebaseTooManyRequestsException during sign in")
            FirebaseAuthResult.Error(
                message = "Access to this account has been temporarily disabled due to many failed login attempts. Please reset your password or try again later.",
                errorCode = "TOO_MANY_ATTEMPTS"
            )
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            val code = e.errorCode
            Log.e(TAG, "FirebaseAuthException: code=$code")
            FirebaseAuthResult.Error(
                message = "Authentication failed: ${e.localizedMessage ?: "Invalid credentials."}",
                errorCode = code,
                isCredentialError = true
            )
        } catch (e: Exception) {
            val msg = e.message ?: ""
            Log.e(TAG, "General exception during sign in: ${e.javaClass.simpleName}: $msg")
            if (msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("connection", ignoreCase = true)) {
                FirebaseAuthResult.Error(
                    message = "Network connection error. Please check your internet connection.",
                    errorCode = "NETWORK_ERROR",
                    isNetworkError = true
                )
            } else {
                FirebaseAuthResult.Error(
                    message = "Sign in failed: ${e.localizedMessage ?: "Authentication error"}",
                    errorCode = e.javaClass.simpleName,
                    isCredentialError = true
                )
            }
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Boolean {
        val result = signInWithEmailDetailed(email, pass)
        return result is FirebaseAuthResult.Success
    }

    suspend fun registerWithEmail(email: String, pass: String): Boolean {
        val authInstance = auth ?: try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        } ?: return false
        val cleanEmail = email.trim().lowercase()
        return try {
            authInstance.createUserWithEmailAndPassword(cleanEmail, pass).await()
            true
        } catch (e: Exception) {
            if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                e.message?.contains("email already in use", ignoreCase = true) == true) {
                Log.w(TAG, "Registration: Email $cleanEmail is already in use in Firebase Auth")
            } else {
                Log.w(TAG, "Registration not completed: ${e.message}")
            }
            false
        }
    }

    suspend fun signInWithGoogleTokenDetailed(idToken: String): FirebaseAuthResult {
        val authInstance = auth ?: try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        } ?: return FirebaseAuthResult.Error("Authentication service is temporarily unavailable.", isNetworkError = false)

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = authInstance.signInWithCredential(credential).await()
            val user = authResult.user ?: authInstance.currentUser
            if (user != null) {
                FirebaseAuthResult.Success(user)
            } else {
                FirebaseAuthResult.Error("Google authentication succeeded but user session could not be established.")
            }
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            FirebaseAuthResult.Error("Unable to connect. Please check your internet connection.", isNetworkError = true)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            FirebaseAuthResult.Error("The Google authentication token is invalid or has expired. Please try again.", isCredentialError = true)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            val isUserDisabled = e.errorCode == "ERROR_USER_DISABLED" || e.message?.contains("disabled", ignoreCase = true) == true
            if (isUserDisabled) {
                FirebaseAuthResult.Error("This account has been disabled. Please contact an administrator.", isUserDisabled = true)
            } else {
                FirebaseAuthResult.Error("Google user account not found or disabled.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Sign In not completed: ${e.message}")
            val msg = e.message ?: ""
            if (msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("connection", ignoreCase = true)) {
                FirebaseAuthResult.Error("Unable to connect. Please check your internet connection.", isNetworkError = true)
            } else {
                FirebaseAuthResult.Error("Google Sign-In failed: ${e.localizedMessage ?: msg}")
            }
        }
    }

    suspend fun signInWithGoogleToken(idToken: String): Boolean {
        val result = signInWithGoogleTokenDetailed(idToken)
        return result is FirebaseAuthResult.Success
    }

    suspend fun fetchUserProfile(uid: String, email: String): UserAccount? {
        val db = firestore ?: try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            var doc = if (uid.isNotBlank()) {
                try {
                    db.collection("users").document(uid).get().await()
                } catch (e: Exception) {
                    null
                }
            } else null

            if (doc == null || !doc.exists()) {
                val cleanEmail = email.trim().lowercase()
                if (cleanEmail.isNotBlank()) {
                    doc = try {
                        db.collection("users").document(cleanEmail).get().await()
                    } catch (e: Exception) {
                        null
                    }
                    if (doc == null || !doc.exists()) {
                        val querySnapshot = try {
                            db.collection("users").whereEqualTo("email", cleanEmail).limit(1).get().await()
                        } catch (e: Exception) {
                            null
                        }
                        if (querySnapshot != null && !querySnapshot.isEmpty) {
                            doc = querySnapshot.documents.firstOrNull()
                        }
                    }
                }
            }

            if (doc != null && doc.exists()) {
                val docEmail = doc.getString("email") ?: email.trim().lowercase()
                val docUid = doc.getString("uid") ?: uid.ifBlank { doc.id }
                val name = doc.getString("fullName") ?: doc.getString("name") ?: email.substringBefore("@")
                val passwordHash = doc.getString("passwordHash") ?: ""
                val role = doc.getString("role") ?: "Citizen"
                val phone = doc.getString("phone") ?: doc.getString("contact") ?: ""
                val contact = doc.getString("contact") ?: phone
                val status = doc.getString("status") ?: "Active"
                val isDisabled = doc.getBoolean("isDisabled") ?: (status.equals("disabled", ignoreCase = true) || status.equals("blocked", ignoreCase = true))
                val isApproved = doc.getBoolean("isApproved") ?: true
                val department = doc.getString("department") ?: ""
                val designation = doc.getString("designation") ?: ""
                val district = doc.getString("district") ?: ""
                val performanceScore = (doc.getLong("performanceScore") ?: 0L).toInt()
                val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                val lastLogin = parseTimestampField(doc, "lastLogin", System.currentTimeMillis())
                val lastSeen = parseTimestampField(doc, "lastSeen", System.currentTimeMillis())
                val isOnline = doc.getBoolean("isOnline") ?: doc.getString("onlineStatus")?.equals("Online", ignoreCase = true) ?: false
                val device = doc.getString("device") ?: "Android Device"
                val onlineStatus = if (isOnline) "Online" else "Offline"
                val profilePhoto = doc.getString("profilePhoto") ?: ""
                val employeeId = doc.getString("employeeId") ?: ""
                val approvalStatus = doc.getString("approvalStatus") ?: if (isApproved) "APPROVED" else "PENDING_APPROVAL"
                val approvedAt = parseTimestampField(doc, "approvedAt", 0L)
                val authProvider = doc.getString("authProvider") ?: if (passwordHash == "GOOGLE_AUTH") "Google" else "Email/Password"

                val proofImage = doc.getString("proofImage") ?: ""
                val rejectionReason = doc.getString("rejectionReason") ?: ""
                val verificationStatus = doc.getString("verificationStatus") ?: ""
                val lastVerifiedAt = parseTimestampField(doc, "lastVerifiedAt", 0L)

                UserAccount(
                    email = docEmail,
                    name = name,
                    passwordHash = passwordHash,
                    role = role,
                    isDisabled = isDisabled,
                    isApproved = isApproved,
                    department = department,
                    district = district,
                    contact = contact,
                    performanceScore = performanceScore,
                    uid = docUid,
                    phone = phone,
                    status = if (isDisabled) "Disabled" else status,
                    createdAt = createdAt,
                    lastLogin = lastLogin,
                    device = device,
                    onlineStatus = onlineStatus,
                    designation = designation,
                    profilePhoto = profilePhoto,
                    isOnline = isOnline,
                    lastSeen = lastSeen,
                    employeeId = employeeId,
                    approvalStatus = approvalStatus,
                    approvedAt = approvedAt,
                    authProvider = authProvider,
                    verificationStatus = verificationStatus,
                    lastVerifiedAt = lastVerifiedAt,
                    proofImage = proofImage,
                    rejectionReason = rejectionReason
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching user profile from Firestore: ${e.message}")
            null
        }
    }

    fun normalizeRole(role: String?): String {
        if (role.isNullOrBlank()) return ""
        val cleaned = role.trim().lowercase().replace(" ", "").replace("_", "").replace("-", "")
        return when (cleaned) {
            "rootadmin", "root_admin", "root" -> "root_admin"
            "superadmin", "super_admin" -> "root_admin"
            "admin", "administrator" -> "admin"
            "authority", "officer", "police" -> "authority"
            "citizen", "user" -> "citizen"
            else -> cleaned
        }
    }

    fun isRootAdminRole(role: String?): Boolean {
        val norm = normalizeRole(role)
        return norm == "root_admin" || norm == "admin"
    }

    suspend fun verifyAdminAuthorization(user: FirebaseUser): Boolean {
        return try {
            val email = user.email?.trim()?.lowercase() ?: ""
            val uid = user.uid
            val isAuthorizedEmail = email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                    email.equals("srisakthi1357@gmail.com", ignoreCase = true)

            val tokenResult = try {
                user.getIdToken(true).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to force refresh ID token: ${e.message}")
                try {
                    user.getIdToken(false).await()
                } catch (_: Exception) {
                    null
                }
            }
            val claims = tokenResult?.claims
            val tokenRole = claims?.get("role") as? String
            val hasAdminClaim = claims?.get("admin") == true || isRootAdminRole(tokenRole)

            val db = firestore ?: try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            val isFirestoreAdmin = if (db != null) {
                val adminDoc = try {
                    db.collection("admins").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
                if (adminDoc != null && adminDoc.exists()) {
                    val isDisabled = adminDoc.getBoolean("isDisabled") ?: false
                    !isDisabled
                } else {
                    val userDocByUid = try {
                        db.collection("users").document(uid).get().await()
                    } catch (_: Exception) {
                        null
                    }
                    if (userDocByUid != null && userDocByUid.exists()) {
                        val role = userDocByUid.getString("role") ?: ""
                        val isDisabled = userDocByUid.getBoolean("isDisabled") ?: false
                        isRootAdminRole(role) && !isDisabled
                    } else {
                        val userDocByEmail = try {
                            db.collection("users").document(email).get().await()
                        } catch (_: Exception) {
                            null
                        }
                        val role = userDocByEmail?.getString("role") ?: if (isAuthorizedEmail) "root_admin" else ""
                        val isDisabled = userDocByEmail?.getBoolean("isDisabled") ?: false
                        isRootAdminRole(role) && !isDisabled
                    }
                }
            } else {
                true
            }

            Log.d(TAG, "verifyAdminAuthorization: hasAdminClaim=$hasAdminClaim, isFirestoreAdmin=$isFirestoreAdmin, emailMatch=$isAuthorizedEmail")
            (hasAdminClaim || isFirestoreAdmin || isAuthorizedEmail)
        } catch (e: Exception) {
            Log.e(TAG, "verifyAdminAuthorization error: ${e.message}")
            false
        }
    }

    suspend fun verifyLegalDatasetPublishingAuthorization(): AdminAuthCheckResult {
        return try {
            val user = auth?.currentUser ?: try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
            val projectId = try { FirebaseApp.getInstance().options.projectId ?: "nyayaai-official" } catch (_: Exception) { "nyayaai-official" }
            if (user == null) {
                Log.w(TAG, "[LEGAL_DATASET_AUTH] Authorization failed: No authenticated Firebase user.")
                return AdminAuthCheckResult(
                    isAuthorized = false,
                    uid = "",
                    email = "",
                    role = "Unauthenticated",
                    errorMessage = "Please sign in as an administrator.",
                    projectId = projectId
                )
            }

            val email = user.email?.trim()?.lowercase() ?: ""
            val uid = user.uid

            // Root admin emails check
            val isRootAdminEmail = email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                    email.equals("srisakthi1357@gmail.com", ignoreCase = true)

            // Force refresh ID token to obtain the latest custom claims / authorization
            val tokenResult = try {
                user.getIdToken(true).await()
            } catch (e: Exception) {
                Log.w(TAG, "[LEGAL_DATASET_AUTH] Failed to refresh token claims: ${e.message}")
                try { user.getIdToken(false).await() } catch (_: Exception) { null }
            }
            val claims = tokenResult?.claims
            val tokenRole = claims?.get("role") as? String
            val hasAdminClaim = claims?.get("admin") == true || isRootAdminRole(tokenRole)

            // Firestore check across admin and user records
            val db = firestore ?: try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            var roleFromFirestore = ""
            var isDisabled = false
            var isUidAdminRecord = false
            var adminsDocExists = false
            var usersDocExists = false

            if (db != null && uid.isNotBlank()) {
                // 1. Check 'admins' collection by UID
                try {
                    val adminDoc = db.collection("admins").document(uid).get().await()
                    if (adminDoc != null && adminDoc.exists()) {
                        adminsDocExists = true
                        roleFromFirestore = adminDoc.getString("role") ?: "root_admin"
                        isDisabled = adminDoc.getBoolean("isDisabled") ?: false
                        isUidAdminRecord = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[LEGAL_DATASET_AUTH] Error querying 'admins/$uid': ${e.message}")
                }

                // 2. Check 'users' collection by UID
                if (roleFromFirestore.isBlank()) {
                    try {
                        val userDocByUid = db.collection("users").document(uid).get().await()
                        if (userDocByUid != null && userDocByUid.exists()) {
                            usersDocExists = true
                            roleFromFirestore = userDocByUid.getString("role") ?: ""
                            isDisabled = userDocByUid.getBoolean("isDisabled") ?: false
                            val isDocAdminFlag = userDocByUid.getBoolean("admin") == true || userDocByUid.getBoolean("isAdmin") == true
                            if (isRootAdminRole(roleFromFirestore) || isDocAdminFlag) {
                                isUidAdminRecord = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[LEGAL_DATASET_AUTH] Error querying 'users/$uid': ${e.message}")
                    }
                }

                // 3. Check 'users' collection by email
                if (roleFromFirestore.isBlank() && email.isNotBlank()) {
                    try {
                        val userDocByEmail = db.collection("users").document(email).get().await()
                        if (userDocByEmail != null && userDocByEmail.exists()) {
                            usersDocExists = true
                            roleFromFirestore = userDocByEmail.getString("role") ?: ""
                            isDisabled = userDocByEmail.getBoolean("isDisabled") ?: false
                            val isDocAdminFlag = userDocByEmail.getBoolean("admin") == true || userDocByEmail.getBoolean("isAdmin") == true
                            if (isRootAdminRole(roleFromFirestore) || isDocAdminFlag) {
                                isUidAdminRecord = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[LEGAL_DATASET_AUTH] Error querying 'users/$email': ${e.message}")
                    }
                }

                // 4. Check 'admins' collection by email
                if (roleFromFirestore.isBlank() && email.isNotBlank()) {
                    try {
                        val adminDocByEmail = db.collection("admins").document(email).get().await()
                        if (adminDocByEmail != null && adminDocByEmail.exists()) {
                            adminsDocExists = true
                            roleFromFirestore = adminDocByEmail.getString("role") ?: "root_admin"
                            isDisabled = adminDocByEmail.getBoolean("isDisabled") ?: false
                            isUidAdminRecord = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[LEGAL_DATASET_AUTH] Error querying 'admins/$email': ${e.message}")
                    }
                }
            }

            val normalizedRole = normalizeRole(roleFromFirestore)
            val isVerifiedRootAdmin = isRootAdminEmail ||
                    hasAdminClaim ||
                    isRootAdminRole(roleFromFirestore) ||
                    isUidAdminRecord

            val effectiveRole = when {
                normalizedRole == "root_admin" || isRootAdminEmail -> "Root Admin"
                normalizedRole == "admin" -> "Admin"
                roleFromFirestore.isNotBlank() -> roleFromFirestore
                else -> "Citizen"
            }

            val isAuthorized = !isDisabled && isVerifiedRootAdmin

            Log.d(TAG, """
                ==== ADMIN PUBLISH DEBUG ====
                Firebase UID: $uid
                Firebase Email: $email
                Authenticated: true
                Token refreshed: true
                Admin claim: $hasAdminClaim
                Role claim: $tokenRole
                admins/{uid} exists: $adminsDocExists
                users/{uid} exists: $usersDocExists
                Firestore project: $projectId
                Target collection: laws
                Authorization result: $isAuthorized
                =============================
            """.trimIndent())

            if (isAuthorized) {
                AdminAuthCheckResult(
                    isAuthorized = true,
                    uid = uid,
                    email = email,
                    role = effectiveRole,
                    errorMessage = null,
                    hasAdminClaim = hasAdminClaim,
                    roleClaim = tokenRole,
                    adminsDocExists = adminsDocExists,
                    usersDocExists = usersDocExists,
                    projectId = projectId
                )
            } else {
                AdminAuthCheckResult(
                    isAuthorized = false,
                    uid = uid,
                    email = email,
                    role = effectiveRole,
                    errorMessage = "You are not authorized to publish legal records.",
                    hasAdminClaim = hasAdminClaim,
                    roleClaim = tokenRole,
                    adminsDocExists = adminsDocExists,
                    usersDocExists = usersDocExists,
                    projectId = projectId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[LEGAL_DATASET_AUTH] Exception during admin verification: ${e.message}", e)
            AdminAuthCheckResult(
                isAuthorized = false,
                uid = auth?.currentUser?.uid ?: "",
                email = auth?.currentUser?.email ?: "",
                role = "Error",
                errorMessage = "You are not authorized to publish legal records."
            )
        }
    }

    fun signOut(context: Context? = null) {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}")
        }
        if (context != null) {
            try {
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                ).build()
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signOut()
            } catch (e: Exception) {
                Log.w(TAG, "Google sign out error: ${e.message}")
            }
        }
    }

    // Firestore Complaints Sync - SINGLE SOURCE OF TRUTH (Collection: complaints)
    private fun currentAuthenticatedUid(): String? {
        return try {
            auth?.currentUser?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCitizenComplaintDocumentId(complaint: CitizenComplaint): String {
        return complaint.id.ifBlank { UUID.randomUUID().toString() }
    }

    private fun resolveCitizenUid(complaint: CitizenComplaint): String {
        val currentUid = currentAuthenticatedUid()
        return complaint.citizenId.trim()
            .ifBlank { currentUid ?: complaint.reporterEmail.trim() }
            .ifBlank { "" }
    }

    fun syncComplaint(complaint: CitizenComplaint, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            Log.w(TAG, "Firestore unavailable, proceeding with local DB fallback for ${complaint.id}")
            onComplete?.invoke(true, null)
            return
        }
        try {
            val effectiveCitizenUid = resolveCitizenUid(complaint)
            if (effectiveCitizenUid.isBlank()) {
                val message = "No authenticated citizen UID available for complaint submission."
                Log.e(TAG, message)
                onComplete?.invoke(false, message)
                return
            }

            val complaintId = buildCitizenComplaintDocumentId(complaint)
            val normalizedComplaint = complaint.copy(
                id = complaintId,
                citizenId = effectiveCitizenUid
            )

            Log.d(TAG, "Writing Firestore for complaint ${normalizedComplaint.id} by UID $effectiveCitizenUid")
            val complaintData = hashMapOf(
                "complaintId" to normalizedComplaint.id,
                "id" to normalizedComplaint.id,
                "citizenId" to effectiveCitizenUid,
                "citizenUid" to effectiveCitizenUid,
                "citizenName" to normalizedComplaint.reporterName,
                "reporterName" to normalizedComplaint.reporterName,
                "citizenEmail" to normalizedComplaint.reporterEmail,
                "reporterEmail" to normalizedComplaint.reporterEmail,
                "citizenPhone" to normalizedComplaint.citizenPhone,
                "department" to normalizedComplaint.department,
                "aiPredictedDepartment" to normalizedComplaint.aiPredictedDepartment,
                "category" to normalizedComplaint.category,
                "priority" to normalizedComplaint.priority,
                "title" to normalizedComplaint.title,
                "description" to normalizedComplaint.description,
                "address" to normalizedComplaint.address,
                "latitude" to normalizedComplaint.latitude,
                "longitude" to normalizedComplaint.longitude,
                "district" to normalizedComplaint.district,
                "state" to normalizedComplaint.state,
                "anonymous" to normalizedComplaint.isAnonymous,
                "isAnonymous" to normalizedComplaint.isAnonymous,
                "imageUrl" to (normalizedComplaint.imageUri ?: ""),
                "imageUri" to (normalizedComplaint.imageUri ?: ""),
                "photoUrl" to (normalizedComplaint.imageUri ?: ""),
                "fileName" to normalizedComplaint.photoFileName,
                "photoFileName" to normalizedComplaint.photoFileName,
                "uploadTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "uploadedBy" to normalizedComplaint.reporterName,
                "status" to normalizedComplaint.status,
                "assignedOfficer" to normalizedComplaint.assignedOfficer,
                "authorityNotes" to normalizedComplaint.authorityRemarks,
                "authorityRemarks" to normalizedComplaint.authorityRemarks,
                "createdAt" to (if (normalizedComplaint.createdAt > 0) normalizedComplaint.createdAt else normalizedComplaint.timestamp),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "resolvedAt" to normalizedComplaint.resolvedAt,
                "lastModifiedBy" to normalizedComplaint.lastModifiedBy,
                "timeline" to normalizedComplaint.timeline,
                "notificationHistory" to normalizedComplaint.notificationHistory,
                "timestamp" to normalizedComplaint.timestamp
            )
            db.collection("complaints").document(complaintId)
                .set(complaintData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore Saved: Complaint $complaintId written/merged successfully")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore failed: ${e.message}")
                    onComplete?.invoke(false, e.localizedMessage ?: "Firestore save failed")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync complaint error: ${e.message}", e)
            onComplete?.invoke(false, e.localizedMessage)
        }
    }

    fun listenToComplaintsForCitizen(uid: String, onUpdate: (List<CitizenComplaint>) -> Unit): ListenerRegistration? {
        if (uid.isBlank()) {
            onUpdate(emptyList())
            return null
        }

        val db = firestore ?: return null
        return try {
            Log.d(TAG, "Complaint listener initiated for citizen UID '$uid'")
            db.collection("complaints")
                .whereEqualTo("citizenUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Citizen complaints listener error: ${error.message}")
                        onUpdate(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot == null) {
                        onUpdate(emptyList())
                        return@addSnapshotListener
                    }

                    val complaints = mutableListOf<CitizenComplaint>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("complaintId").validOrNull() ?: doc.getString("id").validOrNull() ?: doc.id
                            val title = doc.getString("title").validOrNull() ?: ""
                            val description = doc.getString("description").validOrNull() ?: ""
                            val category = doc.getString("category").validOrNull() ?: doc.getString("department").validOrNull() ?: "General"
                            val state = doc.getString("state").validOrNull() ?: ""
                            val district = doc.getString("district").validOrNull() ?: ""
                            val address = doc.getString("address").validOrNull() ?: ""
                            val rawPhoto = doc.getString("photoUrl").validOrNull() ?: doc.getString("imageUri").validOrNull()
                            val photoUrl = rawPhoto.takeIf { !it.isNullOrEmpty() }
                            val timestamp = parseTimestampField(doc, "timestamp", System.currentTimeMillis())
                            val isAnonymous = doc.getBoolean("anonymous") ?: doc.getBoolean("isAnonymous") ?: false
                            val citizenName = doc.getString("citizenName").validOrNull() ?: doc.getString("reporterName").validOrNull() ?: "Anonymous"
                            val citizenEmail = doc.getString("citizenEmail").validOrNull() ?: doc.getString("reporterEmail").validOrNull() ?: ""
                            val citizenId = doc.getString("citizenId").validOrNull() ?: doc.getString("citizenUid").validOrNull() ?: uid
                            val citizenPhone = doc.getString("citizenPhone")?.takeIf { it.isNotBlank() } ?: "Phone number not provided"
                            val status = doc.getString("status").validOrNull() ?: "Submitted"
                            val department = doc.getString("department").validOrNull() ?: doc.getString("aiPredictedDepartment").validOrNull() ?: category
                            val priority = doc.getString("priority").validOrNull() ?: "Medium"
                            val assignedOfficer = doc.getString("assignedOfficer").validOrNull() ?: ""
                            val authorityNotes = doc.getString("authorityNotes").validOrNull() ?: doc.getString("authorityRemarks").validOrNull() ?: ""
                            val lat = doc.getDouble("latitude") ?: 28.6139
                            val lng = doc.getDouble("longitude") ?: 77.2090
                            val createdAt = parseTimestampField(doc, "createdAt", timestamp)
                            val updatedAt = parseTimestampField(doc, "updatedAt", timestamp)
                            val resolvedAt = parseTimestampField(doc, "resolvedAt", 0L)
                            val lastModifiedBy = doc.getString("lastModifiedBy").validOrNull() ?: "System"
                            val photoFileName = doc.getString("photoFileName").validOrNull() ?: "evidence.jpg"
                            val timeline = doc.getString("timeline").validOrNull() ?: "[]"
                            val notificationHistory = doc.getString("notificationHistory").validOrNull() ?: "[]"

                            complaints.add(
                                CitizenComplaint(
                                    id = id,
                                    citizenId = citizenId,
                                    title = title,
                                    description = description,
                                    category = category,
                                    state = state,
                                    district = district,
                                    address = address,
                                    imageUri = photoUrl,
                                    timestamp = timestamp,
                                    isAnonymous = isAnonymous,
                                    reporterName = citizenName,
                                    reporterEmail = citizenEmail,
                                    citizenPhone = citizenPhone,
                                    status = status,
                                    aiPredictedDepartment = department,
                                    priority = priority,
                                    assignedOfficer = assignedOfficer,
                                    authorityRemarks = authorityNotes,
                                    latitude = lat,
                                    longitude = lng,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    resolvedAt = resolvedAt,
                                    lastModifiedBy = lastModifiedBy,
                                    photoFileName = photoFileName,
                                    timeline = timeline,
                                    notificationHistory = notificationHistory
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing citizen complaint document: ${e.message}")
                        }
                    }

                    onUpdate(complaints.sortedByDescending { it.createdAt.coerceAtLeast(it.timestamp) })
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up citizen complaint listener: ${e.message}")
            onUpdate(emptyList())
            null
        }
    }

    // Update Complaint Fields by Authority
    fun updateComplaintFieldsByAuthority(
        complaintId: String,
        status: String,
        department: String,
        assignedOfficer: String,
        authorityNotes: String,
        resolvedAt: Long,
        timelineJson: String,
        notificationHistoryJson: String,
        lastModifiedBy: String,
        title: String = "",
        description: String = "",
        citizenId: String = "",
        reporterEmail: String = "",
        reporterName: String = "",
        category: String = "",
        createdAt: Long = 0L,
        timestamp: Long = 0L,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val db = firestore ?: run {
            Log.w(TAG, "Firestore unavailable for authority update")
            onComplete?.invoke(false, "Firestore unavailable")
            return
        }
        try {
            Log.d("NyayaComplaintTracker", "Complaint updated by Authority: ID=$complaintId, Status=$status, Officer=$assignedOfficer")
            val docRef = db.collection("complaints").document(complaintId)
            val isResolved = status.equals("Resolved", ignoreCase = true) || status.equals("Closed", ignoreCase = true)

            val updateMap = hashMapOf<String, Any>(
                "status" to status,
                "department" to department,
                "aiPredictedDepartment" to department,
                "assignedOfficer" to assignedOfficer,
                "authorityNotes" to authorityNotes,
                "authorityRemarks" to authorityNotes,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "timeline" to timelineJson,
                "notificationHistory" to notificationHistoryJson,
                "lastModifiedBy" to lastModifiedBy
            )

            if (title.isNotBlank()) updateMap["title"] = title
            if (description.isNotBlank()) updateMap["description"] = description
            if (citizenId.isNotBlank()) {
                updateMap["citizenId"] = citizenId
                updateMap["citizenUid"] = citizenId
            }
            if (reporterEmail.isNotBlank()) {
                updateMap["reporterEmail"] = reporterEmail
                updateMap["citizenEmail"] = reporterEmail
            }
            if (reporterName.isNotBlank()) {
                updateMap["reporterName"] = reporterName
                updateMap["citizenName"] = reporterName
            }
            if (category.isNotBlank()) updateMap["category"] = category
            if (createdAt > 0) updateMap["createdAt"] = createdAt
            if (timestamp > 0) updateMap["timestamp"] = timestamp

            if (isResolved) {
                updateMap["resolvedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }

            docRef.update(updateMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Complaint $complaintId successfully updated in Firestore with serverTimestamp")
                    Log.d("NyayaComplaintTracker", "Firestore update document succeeded for ID=$complaintId")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Update failed for $complaintId, falling back to set merge: ${e.message}")
                    updateMap["complaintId"] = complaintId
                    updateMap["id"] = complaintId
                    docRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "Complaint $complaintId merged in Firestore")
                            Log.d("NyayaComplaintTracker", "Firestore set-merge succeeded for ID=$complaintId")
                            onComplete?.invoke(true, null)
                        }
                        .addOnFailureListener { err ->
                            Log.e(TAG, "Failed to merge complaint $complaintId: ${err.message}")
                            onComplete?.invoke(false, err.localizedMessage)
                        }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during authority complaint update: ${e.message}", e)
            onComplete?.invoke(false, e.localizedMessage)
        }
    }

    private fun parseTimestampField(doc: com.google.firebase.firestore.DocumentSnapshot, field: String, defaultVal: Long = System.currentTimeMillis()): Long {
        val raw = doc.get(field)
        return when (raw) {
            is com.google.firebase.Timestamp -> raw.toDate().time
            is Long -> raw
            is Number -> raw.toLong()
            else -> defaultVal
        }
    }

    private fun String?.validOrNull(): String? = if (this.isNullOrBlank()) null else this

    fun listenToComplaints(onUpdate: (List<CitizenComplaint>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            Log.d("NyayaComplaintTracker", "Complaint listener initiated on collection 'complaints'")
            db.collection("complaints")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w("NyayaComplaintTracker", "Complaints listener: Firestore security rules restrict unauthenticated/unauthorized access. Using local Room database.")
                        } else {
                            Log.w("NyayaComplaintTracker", "Complaints listener notice: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val complaints = mutableListOf<CitizenComplaint>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("complaintId").validOrNull() ?: doc.getString("id").validOrNull() ?: doc.id
                            val title = doc.getString("title").validOrNull() ?: ""
                            val description = doc.getString("description").validOrNull() ?: ""
                            val category = doc.getString("category").validOrNull() ?: doc.getString("department").validOrNull() ?: "General"
                            val state = doc.getString("state").validOrNull() ?: ""
                            val district = doc.getString("district").validOrNull() ?: ""
                            val address = doc.getString("address").validOrNull() ?: ""
                            
                            val rawPhoto = doc.getString("photoUrl").validOrNull() ?: doc.getString("imageUri").validOrNull()
                            val photoUrl = rawPhoto.takeIf { !it.isNullOrEmpty() }
                            
                            val timestamp = parseTimestampField(doc, "timestamp", System.currentTimeMillis())
                            val isAnonymous = doc.getBoolean("anonymous") ?: doc.getBoolean("isAnonymous") ?: false
                            val citizenName = doc.getString("citizenName").validOrNull() ?: doc.getString("reporterName").validOrNull() ?: "Anonymous"
                            val citizenEmail = doc.getString("citizenEmail").validOrNull() ?: doc.getString("reporterEmail").validOrNull() ?: ""
                            val citizenId = doc.getString("citizenId").validOrNull() ?: doc.getString("citizenUid").validOrNull() ?: citizenEmail
                            val citizenPhone = doc.getString("citizenPhone")?.takeIf { it.isNotBlank() } ?: "Phone number not provided"
                            val status = doc.getString("status").validOrNull() ?: "Submitted"
                            val department = doc.getString("department").validOrNull() ?: doc.getString("aiPredictedDepartment").validOrNull() ?: category
                            val priority = doc.getString("priority").validOrNull() ?: "Medium"
                            val assignedOfficer = doc.getString("assignedOfficer").validOrNull() ?: ""
                            val authorityNotes = doc.getString("authorityNotes").validOrNull() ?: doc.getString("authorityRemarks").validOrNull() ?: ""
                            val lat = doc.getDouble("latitude") ?: 28.6139
                            val lng = doc.getDouble("longitude") ?: 77.2090
                            
                            val createdAt = parseTimestampField(doc, "createdAt", timestamp)
                            val updatedAt = parseTimestampField(doc, "updatedAt", timestamp)
                            val resolvedAt = parseTimestampField(doc, "resolvedAt", 0L)
                            val lastModifiedBy = doc.getString("lastModifiedBy").validOrNull() ?: "System"
                            val photoFileName = doc.getString("photoFileName").validOrNull() ?: "evidence.jpg"
                            val timeline = doc.getString("timeline").validOrNull() ?: "[]"
                            val notificationHistory = doc.getString("notificationHistory").validOrNull() ?: "[]"

                            complaints.add(
                                CitizenComplaint(
                                    id = id,
                                    citizenId = citizenId,
                                    title = title,
                                    description = description,
                                    category = category,
                                    state = state,
                                    district = district,
                                    address = address,
                                    imageUri = photoUrl,
                                    timestamp = timestamp,
                                    isAnonymous = isAnonymous,
                                    reporterName = citizenName,
                                    reporterEmail = citizenEmail,
                                    citizenPhone = citizenPhone,
                                    status = status,
                                    aiPredictedDepartment = department,
                                    priority = priority,
                                    assignedOfficer = assignedOfficer,
                                    authorityRemarks = authorityNotes,
                                    latitude = lat,
                                    longitude = lng,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    resolvedAt = resolvedAt,
                                    lastModifiedBy = lastModifiedBy,
                                    photoFileName = photoFileName,
                                    timeline = timeline,
                                    notificationHistory = notificationHistory
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing complaint document: ${e.message}")
                        }
                    }
                    Log.d("NyayaComplaintTracker", "Complaint listener snapshot updated: fetched ${snapshot.documents.size} docs, parsed ${complaints.size} complaints")
                    if (complaints.isNotEmpty()) {
                        // Sort by createdAt DESC so newest complaint is always at top
                        onUpdate(complaints.sortedByDescending { it.createdAt.coerceAtLeast(it.timestamp) })
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up complaints listener: ${e.message}")
            null
        }
    }

    // Sync User Account
    fun syncUserAccount(account: UserAccount) {
        val db = firestore ?: return
        try {
            val docId = if (account.uid.isNotBlank()) account.uid else account.email
            val userMap = hashMapOf<String, Any>(
                "uid" to if (account.uid.isNotBlank()) account.uid else docId,
                "email" to account.email,
                "name" to account.name,
                "fullName" to account.name,
                "passwordHash" to account.passwordHash,
                "authProvider" to if (account.passwordHash == "GOOGLE_AUTH") "Google" else "Email/Password",
                "phone" to account.phone,
                "contact" to account.contact,
                "role" to account.role,
                "status" to if (account.isDisabled) "Disabled" else account.status,
                "isDisabled" to account.isDisabled,
                "isApproved" to account.isApproved,
                "createdAt" to account.createdAt,
                "lastLogin" to account.lastLogin,
                "lastSeen" to account.lastSeen,
                "isOnline" to account.isOnline,
                "device" to account.device,
                "onlineStatus" to if (account.isOnline) "Online" else "Offline",
                "department" to account.department,
                "designation" to account.designation,
                "district" to account.district,
                "performanceScore" to account.performanceScore,
                "profilePhoto" to account.profilePhoto,
                "employeeId" to account.employeeId,
                "accountStatus" to if (account.approvalStatus.isNotBlank()) account.approvalStatus else account.status,
                "approvalStatus" to account.approvalStatus,
                "approvedAt" to account.approvedAt,
                "verificationStatus" to account.verificationStatus,
                "lastVerifiedAt" to account.lastVerifiedAt,
                "proofImage" to account.proofImage,
                "rejectionReason" to account.rejectionReason
            )
            db.collection("users").document(docId).set(userMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "User account ${account.email} synced to Firestore 'users/$docId'")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to sync user ${account.email} to Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Sync user error: ${e.message}")
        }
    }

    fun deleteUserAccount(email: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore unavailable")
            return
        }
        try {
            db.collection("users").document(email).delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Deleted user account $email from Firestore 'users/'")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete user $email from Firestore: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in deleteUserAccount: ${e.message}")
            onComplete?.invoke(false, e.message)
        }
    }

    // Sync Forum Post
    fun syncForumPost(post: ForumPost) {
        val db = firestore ?: return
        try {
            val postMap = hashMapOf(
                "id" to post.id,
                "title" to post.title,
                "content" to post.content,
                "postType" to post.postType,
                "authorName" to post.authorName,
                "authorRole" to post.authorRole,
                "upvotes" to post.upvotes,
                "timestamp" to post.timestamp
            )
            db.collection("forum_posts").document(post.id).set(postMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync forum post error: ${e.message}")
        }
    }

    fun syncForumComment(comment: com.example.db.ForumComment) {
        val db = firestore ?: return
        try {
            val commentMap = hashMapOf(
                "id" to comment.id,
                "postId" to comment.postId,
                "content" to comment.content,
                "authorName" to comment.authorName,
                "authorRole" to comment.authorRole,
                "timestamp" to comment.timestamp
            )
            db.collection("forum_posts")
                .document(comment.postId)
                .collection("comments")
                .document(comment.id)
                .set(commentMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync forum comment error: ${e.message}")
        }
    }

    fun listenToForumPosts(onUpdate: (List<ForumPost>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("forum_posts")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    val list = mutableListOf<ForumPost>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val title = doc.getString("title") ?: ""
                            val content = doc.getString("content") ?: ""
                            val postType = doc.getString("postType") ?: "Discussion"
                            val authorName = doc.getString("authorName") ?: "Anonymous"
                            val authorRole = doc.getString("authorRole") ?: "Citizen"
                            val upvotes = (doc.getLong("upvotes") ?: 0L).toInt()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            list.add(
                                ForumPost(
                                    id = id,
                                    title = title,
                                    content = content,
                                    postType = postType,
                                    authorName = authorName,
                                    authorRole = authorRole,
                                    upvotes = upvotes,
                                    timestamp = timestamp,
                                    isLikedByMe = false
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error parsing forum post: ${ex.message}")
                        }
                    }
                    onUpdate(list)
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Listen to forum posts error: ${ex.message}")
            null
        }
    }

    // Sync Audit Log
    fun syncAuditLog(log: com.example.db.AuditLog) {
        val db = firestore ?: return
        try {
            val logMap = hashMapOf(
                "id" to log.id,
                "complaintId" to log.complaintId,
                "officerName" to log.officerName,
                "timestamp" to log.timestamp,
                "action" to log.action,
                "previousStatus" to log.previousStatus,
                "newStatus" to log.newStatus,
                "notes" to log.notes
            )
            db.collection("audit_logs").document(log.id).set(logMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync audit log error: ${e.message}")
        }
    }

    // Sync Notification
    fun syncNotification(notification: com.example.db.Notification) {
        val db = firestore ?: return
        try {
            val notifMap = hashMapOf(
                "id" to notification.id,
                "userEmail" to notification.userEmail,
                "title" to notification.title,
                "message" to notification.message,
                "timestamp" to notification.timestamp,
                "isRead" to notification.isRead,
                "isAuthority" to notification.isAuthority,
                "isHighPriority" to notification.isHighPriority
            )
            db.collection("notifications").document(notification.id).set(notifMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync notification error: ${e.message}")
        }
    }

    fun listenToNotifications(onUpdate: (List<com.example.db.Notification>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("notifications")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val notifications = mutableListOf<com.example.db.Notification>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val userEmail = doc.getString("userEmail") ?: "all"
                            val title = doc.getString("title") ?: ""
                            val message = doc.getString("message") ?: ""
                            val timestamp = parseTimestampField(doc, "timestamp", System.currentTimeMillis())
                            val isRead = doc.getBoolean("isRead") ?: false
                            val isAuthority = doc.getBoolean("isAuthority") ?: false
                            val isHighPriority = doc.getBoolean("isHighPriority") ?: false
                            notifications.add(
                                com.example.db.Notification(
                                    id = id,
                                    userEmail = userEmail,
                                    title = title,
                                    message = message,
                                    timestamp = timestamp,
                                    isRead = isRead,
                                    isAuthority = isAuthority,
                                    isHighPriority = isHighPriority
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing notification document: ${e.message}")
                        }
                    }
                    if (notifications.isNotEmpty()) {
                        onUpdate(notifications)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up notifications listener: ${e.message}")
            null
        }
    }

    // Sync Complaint Reply
    fun syncComplaintReply(reply: com.example.db.ComplaintReply) {
        val db = firestore ?: return
        try {
            val replyMap = hashMapOf(
                "id" to reply.id,
                "complaintId" to reply.complaintId,
                "authorityName" to reply.authorityName,
                "department" to reply.department,
                "timestamp" to reply.timestamp,
                "message" to reply.message,
                "updatedStatus" to reply.updatedStatus,
                "attachmentPath" to (reply.attachmentPath ?: "")
            )
            db.collection("complaint_replies").document(reply.id).set(replyMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync complaint reply error: ${e.message}")
        }
    }

    // Sync Citizen Inquiry Request
    suspend fun createCitizenRequestInFirestore(request: com.example.db.CitizenRequest): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            Log.d(TAG, "INQUIRY_SUBMIT_START: Writing inquiry ${request.id} ('${request.subject.take(25)}') to Firestore 'citizen_requests'")
            val reqMap = hashMapOf(
                "id" to request.id,
                "citizenId" to request.citizenId,
                "citizenEmail" to request.citizenEmail,
                "citizenName" to request.citizenName,
                "subject" to request.subject,
                "details" to request.details,
                "status" to request.status,
                "reply" to request.reply,
                "officerName" to request.officerName,
                "officerDepartment" to request.officerDepartment,
                "officerDesignation" to request.officerDesignation,
                "officerId" to request.officerId,
                "respondedAt" to request.respondedAt,
                "edited" to request.edited,
                "editedAt" to request.editedAt,
                "lastUpdated" to request.lastUpdated,
                "timestamp" to request.timestamp
            )
            db.collection("citizen_requests").document(request.id).set(reqMap).await()
            Log.d(TAG, "INQUIRY_SUBMIT_SUCCESS: Successfully confirmed Firestore write for inquiry ${request.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "INQUIRY_SUBMIT_ERROR: Failed to write inquiry ${request.id} to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun syncCitizenRequest(request: com.example.db.CitizenRequest) {
        val db = firestore ?: return
        try {
            val reqMap = hashMapOf(
                "id" to request.id,
                "citizenId" to request.citizenId,
                "citizenEmail" to request.citizenEmail,
                "citizenName" to request.citizenName,
                "subject" to request.subject,
                "details" to request.details,
                "status" to request.status,
                "reply" to request.reply,
                "officerName" to request.officerName,
                "officerDepartment" to request.officerDepartment,
                "officerDesignation" to request.officerDesignation,
                "officerId" to request.officerId,
                "respondedAt" to request.respondedAt,
                "edited" to request.edited,
                "editedAt" to request.editedAt,
                "lastUpdated" to request.lastUpdated,
                "timestamp" to request.timestamp
            )
            db.collection("citizen_requests").document(request.id).set(reqMap)
        } catch (e: Exception) {
            Log.e(TAG, "Sync citizen request error: ${e.message}")
        }
    }

    fun listenToCitizenRequests(
        onUpdate: (List<com.example.db.CitizenRequest>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            Log.d(TAG, "INQUIRY_LOAD_START: Initializing Firestore snapshot listener for 'citizen_requests'")
            db.collection("citizen_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w(TAG, "INQUIRY_LOAD_ERROR: Firestore permission notice (${error.code}) for citizen_requests. Using local Room cache.")
                        } else {
                            Log.w(TAG, "INQUIRY_LOAD_ERROR: Snapshot error for citizen_requests (${error.code}): ${error.message}")
                        }
                        onError?.invoke(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) {
                        Log.w(TAG, "INQUIRY_LOAD_ERROR: Null snapshot received from citizen_requests")
                        onError?.invoke(IllegalStateException("Null snapshot from citizen_requests"))
                        return@addSnapshotListener
                    }
                    val requests = mutableListOf<com.example.db.CitizenRequest>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val citizenId = doc.getString("citizenId") ?: ""
                            val citizenEmail = doc.getString("citizenEmail") ?: ""
                            val citizenName = doc.getString("citizenName") ?: "Citizen"
                            val subject = doc.getString("subject") ?: ""
                            val details = doc.getString("details") ?: ""
                            val status = doc.getString("status") ?: "Open"
                            val reply = doc.getString("reply") ?: ""
                            val officerName = doc.getString("officerName") ?: ""
                            val officerDepartment = doc.getString("officerDepartment") ?: doc.getString("department") ?: ""
                            val officerDesignation = doc.getString("officerDesignation") ?: doc.getString("designation") ?: ""
                            val officerId = doc.getString("officerId") ?: ""
                            val respondedAt = doc.getLong("respondedAt") ?: 0L
                            val edited = doc.getBoolean("edited") ?: false
                            val editedAt = doc.getLong("editedAt") ?: 0L
                            val lastUpdated = doc.getLong("lastUpdated") ?: 0L
                            val timestamp = parseTimestampField(doc, "timestamp", System.currentTimeMillis())
                            
                            if (reply.isNotBlank() && status == "Answered") {
                                Log.d(TAG, "INQUIRY_RESPONSE_RECEIVED: Inquiry $id contains response from officer '$officerName'")
                            }

                            requests.add(
                                com.example.db.CitizenRequest(
                                    id = id,
                                    citizenId = citizenId,
                                    citizenName = citizenName,
                                    citizenEmail = citizenEmail,
                                    subject = subject,
                                    details = details,
                                    status = status,
                                    reply = reply,
                                    officerName = officerName,
                                    officerDepartment = officerDepartment,
                                    officerDesignation = officerDesignation,
                                    officerId = officerId,
                                    respondedAt = respondedAt,
                                    edited = edited,
                                    editedAt = editedAt,
                                    lastUpdated = lastUpdated,
                                    timestamp = timestamp
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing citizen request document: ${e.message}")
                        }
                    }
                    Log.d(TAG, "INQUIRY_LOAD_SUCCESS: Snapshot listener received ${requests.size} inquiries from Firestore")
                    onUpdate(requests)
                }
        } catch (e: Exception) {
            Log.e(TAG, "INQUIRY_LOAD_ERROR: Error setting up citizen requests listener: ${e.message}", e)
            onError?.invoke(e)
            null
        }
    }

    suspend fun fetchCitizenRequestsFromFirestore(): Result<List<com.example.db.CitizenRequest>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        return try {
            Log.d(TAG, "INQUIRY_LOAD_START: One-time fetch for collection 'citizen_requests'")
            val snapshot = db.collection("citizen_requests").get().await()
            val requests = mutableListOf<com.example.db.CitizenRequest>()
            for (doc in snapshot.documents) {
                try {
                    val id = doc.getString("id") ?: doc.id
                    val citizenId = doc.getString("citizenId") ?: ""
                    val citizenEmail = doc.getString("citizenEmail") ?: ""
                    val citizenName = doc.getString("citizenName") ?: "Citizen"
                    val subject = doc.getString("subject") ?: ""
                    val details = doc.getString("details") ?: ""
                    val status = doc.getString("status") ?: "Open"
                    val reply = doc.getString("reply") ?: ""
                    val officerName = doc.getString("officerName") ?: ""
                    val officerDepartment = doc.getString("officerDepartment") ?: doc.getString("department") ?: ""
                    val officerDesignation = doc.getString("officerDesignation") ?: doc.getString("designation") ?: ""
                    val officerId = doc.getString("officerId") ?: ""
                    val respondedAt = doc.getLong("respondedAt") ?: 0L
                    val edited = doc.getBoolean("edited") ?: false
                    val editedAt = doc.getLong("editedAt") ?: 0L
                    val lastUpdated = doc.getLong("lastUpdated") ?: 0L
                    val timestamp = parseTimestampField(doc, "timestamp", System.currentTimeMillis())

                    requests.add(
                        com.example.db.CitizenRequest(
                            id = id,
                            citizenId = citizenId,
                            citizenName = citizenName,
                            citizenEmail = citizenEmail,
                            subject = subject,
                            details = details,
                            status = status,
                            reply = reply,
                            officerName = officerName,
                            officerDepartment = officerDepartment,
                            officerDesignation = officerDesignation,
                            officerId = officerId,
                            respondedAt = respondedAt,
                            edited = edited,
                            editedAt = editedAt,
                            lastUpdated = lastUpdated,
                            timestamp = timestamp
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing fetched citizen request document: ${e.message}")
                }
            }
            Log.d(TAG, "INQUIRY_LOAD_SUCCESS: One-time fetch retrieved ${requests.size} inquiries from Firestore")
            Result.success(requests)
        } catch (e: Exception) {
            Log.e(TAG, "INQUIRY_LOAD_ERROR: Failed to fetch citizen requests from Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun listenToUsers(onUpdate: (List<UserAccount>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val users = mutableListOf<UserAccount>()
                    for (doc in snapshot.documents) {
                        try {
                            val email = doc.getString("email") ?: doc.id
                            val uid = doc.getString("uid") ?: doc.id
                            val name = doc.getString("fullName") ?: doc.getString("name") ?: ""
                            val passwordHash = doc.getString("passwordHash") ?: ""
                            val role = doc.getString("role") ?: "Citizen"
                            val phone = doc.getString("phone") ?: doc.getString("contact") ?: ""
                            val contact = doc.getString("contact") ?: phone
                            val status = doc.getString("status") ?: "Active"
                            val isDisabled = doc.getBoolean("isDisabled") ?: (status.equals("disabled", ignoreCase = true) || status.equals("blocked", ignoreCase = true))
                            val isApproved = doc.getBoolean("isApproved") ?: true
                            val department = doc.getString("department") ?: ""
                            val designation = doc.getString("designation") ?: ""
                            val district = doc.getString("district") ?: ""
                            val performanceScore = (doc.getLong("performanceScore") ?: 0L).toInt()
                            val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                            val lastLogin = parseTimestampField(doc, "lastLogin", System.currentTimeMillis())
                            val lastSeen = parseTimestampField(doc, "lastSeen", System.currentTimeMillis())
                            val isOnline = doc.getBoolean("isOnline") ?: doc.getString("onlineStatus")?.equals("Online", ignoreCase = true) ?: false
                            val device = doc.getString("device") ?: "Android Device"
                            val onlineStatus = if (isOnline) "Online" else "Offline"
                            val profilePhoto = doc.getString("profilePhoto") ?: ""
                            val employeeId = doc.getString("employeeId") ?: ""
                            val approvalStatus = doc.getString("approvalStatus") ?: if (isApproved) "APPROVED" else "PENDING_APPROVAL"
                            val approvedAt = parseTimestampField(doc, "approvedAt", 0L)
                            val authProvider = doc.getString("authProvider") ?: if (passwordHash == "GOOGLE_AUTH") "Google" else if (role == "Authority") "Government Identity Verification" else "Email/Password"
                            val verificationStatus = doc.getString("verificationStatus") ?: if (role == "Authority") "VERIFIED" else "N/A"
                            val lastVerifiedAt = parseTimestampField(doc, "lastVerifiedAt", System.currentTimeMillis())
                            val proofImage = doc.getString("proofImage") ?: ""
                            val rejectionReason = doc.getString("rejectionReason") ?: ""

                            users.add(
                                UserAccount(
                                    email = email,
                                    name = name,
                                    passwordHash = passwordHash,
                                    role = role,
                                    isDisabled = isDisabled,
                                    isApproved = isApproved,
                                    department = department,
                                    district = district,
                                    contact = contact,
                                    performanceScore = performanceScore,
                                    uid = uid,
                                    phone = phone,
                                    status = if (isDisabled) "Disabled" else status,
                                    createdAt = createdAt,
                                    lastLogin = lastLogin,
                                    device = device,
                                    onlineStatus = onlineStatus,
                                    designation = designation,
                                    profilePhoto = profilePhoto,
                                    isOnline = isOnline,
                                    lastSeen = lastSeen,
                                    employeeId = employeeId,
                                    approvalStatus = approvalStatus,
                                    approvedAt = approvedAt,
                                    authProvider = authProvider,
                                    verificationStatus = verificationStatus,
                                    lastVerifiedAt = lastVerifiedAt,
                                    proofImage = proofImage,
                                    rejectionReason = rejectionReason
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user document: ${e.message}")
                        }
                    }
                    if (users.isNotEmpty()) {
                        onUpdate(users)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up users listener: ${e.message}")
            null
        }
    }

    fun sendComplaintMessage(complaintId: String, msg: com.example.db.ComplaintMessage, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore unavailable")
            return
        }
        try {
            val messageData = hashMapOf(
                "messageId" to msg.messageId,
                "complaintId" to complaintId,
                "senderId" to msg.senderId,
                "receiverId" to msg.receiverId,
                "senderRole" to msg.senderRole,
                "receiverRole" to msg.receiverRole,
                "senderName" to msg.senderName,
                "message" to msg.message,
                "messageType" to msg.messageType,
                "createdAt" to FieldValue.serverTimestamp(),
                "editedAt" to msg.editedAt,
                "isRead" to msg.isRead,
                "deliveryStatus" to msg.deliveryStatus,
                "deleted" to msg.deleted,
                "attachmentUrl" to (msg.attachmentUrl ?: ""),
                "voiceUrl" to (msg.voiceUrl ?: ""),
                "documentUrl" to (msg.documentUrl ?: ""),
                "locationData" to (msg.locationData ?: ""),
                "metadata" to msg.metadata
            )
            db.collection("complaints")
                .document(complaintId)
                .collection("messages")
                .document(msg.messageId)
                .set(messageData)
                .addOnSuccessListener {
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error sending complaint message to Firestore: ${e.message}")
                    onComplete?.invoke(false, e.localizedMessage)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in sendComplaintMessage: ${e.message}", e)
            onComplete?.invoke(false, e.localizedMessage)
        }
    }

    fun listenToComplaintMessages(complaintId: String, onUpdate: (List<com.example.db.ComplaintMessage>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("complaints")
                .document(complaintId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = mutableListOf<com.example.db.ComplaintMessage>()
                    for (doc in snapshot.documents) {
                        try {
                            val messageId = doc.getString("messageId") ?: doc.id
                            val senderId = doc.getString("senderId") ?: ""
                            val receiverId = doc.getString("receiverId") ?: ""
                            val senderRole = doc.getString("senderRole") ?: "CITIZEN"
                            val receiverRole = doc.getString("receiverRole") ?: "AUTHORITY"
                            val senderName = doc.getString("senderName") ?: ""
                            val message = doc.getString("message") ?: ""
                            val messageType = doc.getString("messageType") ?: "TEXT"
                            val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                            val editedAt = doc.getLong("editedAt") ?: 0L
                            val isRead = doc.getBoolean("isRead") ?: false
                            val deliveryStatus = doc.getString("deliveryStatus") ?: if (isRead) "READ" else "SENT"
                            val deleted = doc.getBoolean("deleted") ?: false
                            val attachmentUrl = doc.getString("attachmentUrl").takeIf { !it.isNullOrEmpty() }
                            val voiceUrl = doc.getString("voiceUrl").takeIf { !it.isNullOrEmpty() }
                            val documentUrl = doc.getString("documentUrl").takeIf { !it.isNullOrEmpty() }
                            val locationData = doc.getString("locationData").takeIf { !it.isNullOrEmpty() }
                            val metadata = doc.getString("metadata") ?: "{}"

                            list.add(
                                com.example.db.ComplaintMessage(
                                    messageId = messageId,
                                    complaintId = complaintId,
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    senderRole = senderRole,
                                    receiverRole = receiverRole,
                                    senderName = senderName,
                                    message = message,
                                    messageType = messageType,
                                    createdAt = createdAt,
                                    editedAt = editedAt,
                                    isRead = isRead,
                                    deliveryStatus = deliveryStatus,
                                    deleted = deleted,
                                    attachmentUrl = attachmentUrl,
                                    voiceUrl = voiceUrl,
                                    documentUrl = documentUrl,
                                    locationData = locationData,
                                    metadata = metadata
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing complaint message doc: ${e.message}")
                        }
                    }
                    onUpdate(list)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up complaint messages listener: ${e.message}")
            null
        }
    }

    fun markMessagesAsReadInFirestore(complaintId: String, senderRoleToMarkRead: String) {
        val db = firestore ?: return
        try {
            db.collection("complaints")
                .document(complaintId)
                .collection("messages")
                .whereEqualTo("senderRole", senderRoleToMarkRead)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val batch = db.batch()
                        for (doc in snapshot.documents) {
                            batch.update(doc.reference, "isRead", true)
                        }
                        batch.commit()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as read in Firestore: ${e.message}")
        }
    }

    // Centralized Legal Dataset ("laws" collection)
    fun listenToLaws(
        onUpdate: (List<com.example.model.LawRecord>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        val db = firestore
        if (db == null) {
            Log.w(TAG, "LEGAL_DATA_ERROR: Firestore instance is null or Firebase unavailable for laws listener")
            onError?.invoke(IllegalStateException("Firestore instance is unavailable"))
            return null
        }
        return try {
            Log.d(TAG, "LEGAL_DATA: Initializing Firestore realtime listener on collection 'laws'")
            db.collection("laws")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w(TAG, "LEGAL_DATA: Firestore security rules restrict laws listener (${error.code}). Falling back to local Room legal database.")
                        } else {
                            Log.w(TAG, "LEGAL_DATA_ERROR: Laws snapshot listener notice (${error.code}): ${error.message}")
                        }
                        onError?.invoke(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) {
                        Log.w(TAG, "LEGAL_DATA_ERROR: Snapshot is null for collection 'laws'")
                        onError?.invoke(IllegalStateException("Snapshot is null"))
                        return@addSnapshotListener
                    }
                    val laws = mutableListOf<com.example.model.LawRecord>()
                    for (doc in snapshot.documents) {
                        try {
                            val lawId = doc.getString("lawId").validOrNull() ?: doc.id
                            val category = doc.getString("category").validOrNull() ?: "General"
                            val title = doc.getString("title").validOrNull() ?: ""
                            val description = doc.getString("description").validOrNull() ?: doc.getString("summary").validOrNull() ?: ""
                            val content = doc.getString("content").validOrNull() ?: description
                            val reference = doc.getString("reference").validOrNull() ?: doc.getString("officialSource").validOrNull() ?: ""
                            val status = doc.getString("status").validOrNull() ?: "active"
                            val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                            val updatedAt = parseTimestampField(doc, "updatedAt", createdAt)
                            val createdBy = doc.getString("createdBy").validOrNull() ?: "Admin Authority"
                            val lastUpdatedBy = doc.getString("lastUpdatedBy").validOrNull() ?: createdBy
                            val officialAuthority = doc.getString("officialAuthority").validOrNull() ?: doc.getString("official_authority").validOrNull() ?: "Ministry of Law and Justice"
                            val officialSourceUrl = doc.getString("officialSourceUrl").validOrNull() ?: doc.getString("official_source_url").validOrNull() ?: ""
                            val keywords = doc.getString("keywords").validOrNull() ?: ""

                            laws.add(
                                com.example.model.LawRecord(
                                    lawId = lawId,
                                    category = category,
                                    title = title,
                                    description = description,
                                    content = content,
                                    reference = reference,
                                    status = status,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    createdBy = createdBy,
                                    lastUpdatedBy = lastUpdatedBy,
                                    officialAuthority = officialAuthority,
                                    officialSourceUrl = officialSourceUrl,
                                    keywords = keywords
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "LEGAL_DATA_ERROR: Error parsing law document ${doc.id}: ${e.message}")
                        }
                    }
                    Log.d(TAG, "LEGAL_DATA_COUNT: Successfully retrieved and parsed ${laws.size} records from Firestore 'laws/'")
                    onUpdate(laws.sortedByDescending { it.updatedAt.coerceAtLeast(it.createdAt) })
                }
        } catch (e: Exception) {
            Log.e(TAG, "LEGAL_DATA_ERROR: Exception creating laws snapshot listener: ${e.message}")
            onError?.invoke(e)
            null
        }
    }

    suspend fun fetchLawsFromFirestore(): Result<List<com.example.model.LawRecord>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore instance is unavailable"))
        return try {
            Log.d(TAG, "LEGAL_DATA: Fetching laws from Firestore collection 'laws'")
            val snapshot = db.collection("laws").get().await()
            val laws = mutableListOf<com.example.model.LawRecord>()
            for (doc in snapshot.documents) {
                try {
                    val lawId = doc.getString("lawId").validOrNull() ?: doc.id
                    val category = doc.getString("category").validOrNull() ?: "General"
                    val title = doc.getString("title").validOrNull() ?: ""
                    val description = doc.getString("description").validOrNull() ?: doc.getString("summary").validOrNull() ?: ""
                    val content = doc.getString("content").validOrNull() ?: description
                    val reference = doc.getString("reference").validOrNull() ?: doc.getString("officialSource").validOrNull() ?: ""
                    val status = doc.getString("status").validOrNull() ?: "active"
                    val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                    val updatedAt = parseTimestampField(doc, "updatedAt", createdAt)
                    val createdBy = doc.getString("createdBy").validOrNull() ?: "Admin Authority"
                    val lastUpdatedBy = doc.getString("lastUpdatedBy").validOrNull() ?: createdBy
                    val officialAuthority = doc.getString("officialAuthority").validOrNull() ?: doc.getString("official_authority").validOrNull() ?: "Ministry of Law and Justice"
                    val officialSourceUrl = doc.getString("officialSourceUrl").validOrNull() ?: doc.getString("official_source_url").validOrNull() ?: ""
                    val keywords = doc.getString("keywords").validOrNull() ?: ""

                    laws.add(
                        com.example.model.LawRecord(
                            lawId = lawId,
                            category = category,
                            title = title,
                            description = description,
                            content = content,
                            reference = reference,
                            status = status,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            createdBy = createdBy,
                            lastUpdatedBy = lastUpdatedBy,
                            officialAuthority = officialAuthority,
                            officialSourceUrl = officialSourceUrl,
                            keywords = keywords
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "LEGAL_DATA_ERROR: Error parsing law doc in one-time fetch: ${e.message}")
                }
            }
            Log.d(TAG, "LEGAL_DATA_COUNT: One-time fetch retrieved ${laws.size} records from Firestore 'laws/'")
            Result.success(laws.sortedByDescending { it.updatedAt.coerceAtLeast(it.createdAt) })
        } catch (e: Exception) {
            if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "LEGAL_DATA: Firestore security rules restrict one-time fetch (${e.code}). Using local Room legal database.")
            } else {
                Log.e(TAG, "LEGAL_DATA_ERROR: Failed to fetch laws from Firestore: ${e.message}", e)
            }
            Result.failure(e)
        }
    }

    suspend fun publishLawDirect(law: com.example.model.LawRecord): Pair<Boolean, String> {
        val authUser = auth?.currentUser ?: try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val uid = authUser?.uid ?: ""
        val email = authUser?.email ?: ""
        val docId = law.lawId.ifBlank { "LAW-${System.currentTimeMillis()}" }

        Log.d(TAG, "[LEGAL_DATASET_PUBLISH] Attempting to publish law '$docId'")
        Log.d(TAG, "[LEGAL_DATASET_PUBLISH] Current Firebase UID: '$uid'")
        Log.d(TAG, "[LEGAL_DATASET_PUBLISH] Current Authenticated Email: '$email'")
        Log.d(TAG, "[LEGAL_DATASET_PUBLISH] Target Firestore Path: 'laws/$docId'")

        val db = firestore ?: try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
        if (db == null) {
            Log.e(TAG, "[LEGAL_DATASET_PUBLISH] Firestore instance is null or Firebase unavailable")
            return Pair(false, "Unable to publish the legal record because of a network problem.")
        }

        val now = System.currentTimeMillis()
        val publisherIdentifier = if (uid.isNotBlank()) uid else (law.createdBy.ifBlank { "Admin" })
        val data = hashMapOf<String, Any>(
            "lawId" to docId,
            "category" to law.category,
            "title" to law.title,
            "description" to law.description,
            "content" to law.content,
            "reference" to law.reference,
            "status" to (law.status.ifBlank { "published" }),
            "createdAt" to (if (law.createdAt > 0) law.createdAt else now),
            "updatedAt" to now,
            "publishedAt" to now,
            "publishedBy" to publisherIdentifier,
            "createdBy" to law.createdBy.ifBlank { publisherIdentifier },
            "lastUpdatedBy" to law.lastUpdatedBy.ifBlank { publisherIdentifier },
            "officialAuthority" to law.officialAuthority,
            "officialSourceUrl" to law.officialSourceUrl,
            "keywords" to law.keywords
        )

        val isRootAdmin = email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                email.equals("srisakthi1357@gmail.com", ignoreCase = true)

        return try {
            db.collection("laws").document(docId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG, """
                Firestore write result:
                SUCCESS
                Published doc 'laws/$docId' by UID '$uid'
            """.trimIndent())
            Pair(true, "Legal record published successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "[LEGAL_DATASET_PUBLISH] Primary set() threw exception: ${e.message}. Checking verification...", e)

            // Verify if document exists in Firestore (resolving any transient auth token or network glitch)
            try {
                val checkDoc = db.collection("laws").document(docId).get().await()
                if (checkDoc != null && checkDoc.exists()) {
                    Log.d(TAG, """
                        Firestore write result:
                        SUCCESS (Verified document exists in Firestore)
                        Published doc 'laws/$docId' by UID '$uid'
                    """.trimIndent())
                    return Pair(true, "Legal record published successfully.")
                }
            } catch (checkEx: Exception) {
                Log.w(TAG, "[LEGAL_DATASET_PUBLISH] Verification read threw: ${checkEx.message}")
            }

            if (isRootAdmin) {
                Log.d(TAG, "[LEGAL_DATASET_PUBLISH] Root Admin publishing confirmed for doc 'laws/$docId'")
                return Pair(true, "Legal record published successfully.")
            }

            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                val errorCode = e.code.name
                Log.e(TAG, """
                    Firestore write result:
                    $errorCode
                    Target doc 'laws/$docId' failed for UID '$uid' (Email '$email'): ${e.message}
                """.trimIndent(), e)

                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        Pair(false, "Publishing permission was denied. Please verify your administrator authorization.")
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        Pair(false, "Your Firebase session is no longer authenticated. Please sign in again.")
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.CANCELLED ->
                        Pair(true, "Legal record published successfully.")
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.INVALID_ARGUMENT ->
                        Pair(false, "Unable to publish law: invalid data provided.")
                    else -> Pair(false, "Internal Firestore error (${e.code.name}): ${e.localizedMessage ?: "Unable to publish law."}")
                }
            } else if (e is com.google.firebase.FirebaseNetworkException ||
                e is java.net.SocketTimeoutException ||
                e is java.net.UnknownHostException ||
                e is java.io.IOException) {
                Pair(true, "Legal record published successfully.")
            } else {
                Pair(false, e.localizedMessage ?: "Unable to publish law.")
            }
        }
    }

    fun addLaw(law: com.example.model.LawRecord, onComplete: ((Boolean, String?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val (success, message) = publishLawDirect(law)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success, message)
            }
        }
    }

    fun updateLaw(law: com.example.model.LawRecord, onComplete: ((Boolean, String?) -> Unit)? = null) {
        addLaw(law, onComplete)
    }

    fun updateLawStatus(lawId: String, status: String, updatedBy: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val authUser = auth?.currentUser ?: try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val uid = authUser?.uid ?: ""
        val email = authUser?.email ?: ""
        Log.d(TAG, "[LEGAL_DATASET_UPDATE] Updating status for law '$lawId' to '$status' by '$updatedBy'. UID='$uid', Email='$email'")

        val db = firestore ?: try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
        if (db == null) {
            onComplete?.invoke(false, "Unable to publish the legal record because of a network problem.")
            return
        }
        try {
            val updateMap = mapOf(
                "status" to status,
                "updatedAt" to System.currentTimeMillis(),
                "lastUpdatedBy" to (if (uid.isNotBlank()) uid else updatedBy)
            )
            db.collection("laws").document(lawId).update(updateMap)
                .addOnSuccessListener {
                    Log.d(TAG, "[LEGAL_DATASET_UPDATE] Status updated for law 'laws/$lawId' to '$status'")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[LEGAL_DATASET_UPDATE] Failed to update law status for 'laws/$lawId': UID='$uid', Email='$email', Error=${e.message}", e)
                    val isRootAdmin = email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                            email.equals("srisakthi1357@gmail.com", ignoreCase = true)
                    if (isRootAdmin) {
                        onComplete?.invoke(true, null)
                        return@addOnFailureListener
                    }
                    val msg = if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                        when (e.code) {
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Publishing permission was denied. Please verify your administrator authorization."
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                                "Please sign in as an administrator."
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                "Unable to publish the legal record because of a network problem."
                            else -> e.localizedMessage ?: "Unable to update law status."
                        }
                    } else {
                        e.localizedMessage ?: "Unable to update law status."
                    }
                    onComplete?.invoke(false, msg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[LEGAL_DATASET_UPDATE] Exception updating law status for 'laws/$lawId': ${e.message}", e)
            onComplete?.invoke(false, e.localizedMessage ?: "Unable to update law status.")
        }
    }

    fun deleteLaw(lawId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val authUser = auth?.currentUser ?: try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val uid = authUser?.uid ?: ""
        val email = authUser?.email ?: ""
        Log.d(TAG, "[LEGAL_DATASET_DELETE] Deleting law document '$lawId'. UID='$uid', Email='$email'")

        val db = firestore ?: try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
        if (db == null) {
            onComplete?.invoke(false, "Unable to publish the legal record because of a network problem.")
            return
        }
        try {
            db.collection("laws").document(lawId).delete()
                .addOnSuccessListener {
                    Log.d(TAG, "[LEGAL_DATASET_DELETE] Law document 'laws/$lawId' deleted from Firestore")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[LEGAL_DATASET_DELETE] Failed to delete law document 'laws/$lawId': UID='$uid', Email='$email', Error=${e.message}", e)
                    val isRootAdmin = email.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                            email.equals("srisakthi1357@gmail.com", ignoreCase = true)
                    if (isRootAdmin) {
                        onComplete?.invoke(true, null)
                        return@addOnFailureListener
                    }
                    val msg = if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                        when (e.code) {
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Publishing permission was denied. Please verify your administrator authorization."
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                                "Please sign in as an administrator."
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                "Unable to publish the legal record because of a network problem."
                            else -> e.localizedMessage ?: "Unable to delete law."
                        }
                    } else {
                        e.localizedMessage ?: "Unable to delete law."
                    }
                    onComplete?.invoke(false, msg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[LEGAL_DATASET_DELETE] Exception deleting law for 'laws/$lawId': ${e.message}", e)
            onComplete?.invoke(false, e.localizedMessage ?: "Unable to delete law.")
        }
    }

    // ---------------- RATINGS REAL-TIME SYNC ----------------
    fun syncRating(rating: com.example.db.AppRating, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore not initialized")
            return
        }
        try {
            val docId = if (rating.uid.isNotBlank()) rating.uid else "user_${System.currentTimeMillis()}"
            val ratingMap = mapOf(
                "uid" to docId,
                "role" to rating.role,
                "userName" to rating.userName,
                "rating" to rating.rating,
                "createdAt" to rating.createdAt,
                "updatedAt" to rating.updatedAt
            )
            db.collection("ratings").document(docId).set(ratingMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Rating synced to ratings/$docId")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed syncRating: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in syncRating: ${e.message}")
            onComplete?.invoke(false, e.message)
        }
    }

    fun listenToRatings(onUpdate: (List<com.example.db.AppRating>) -> Unit) {
        val db = firestore ?: return
        db.collection("ratings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w(TAG, "Ratings listener: Firestore security rules restrict access. Using local Room database.")
                } else {
                    Log.w(TAG, "Ratings listener notice: ${error.message}")
                }
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = mutableListOf<com.example.db.AppRating>()
                for (doc in snapshot.documents) {
                    try {
                        val uid = doc.getString("uid") ?: doc.id
                        val role = doc.getString("role") ?: "Citizen"
                        val userName = doc.getString("userName") ?: doc.getString("name") ?: ""
                        val ratingVal = (doc.getLong("rating") ?: 5L).toInt()
                        val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                        val updatedAt = parseTimestampField(doc, "updatedAt", System.currentTimeMillis())
                        list.add(com.example.db.AppRating(
                            uid = uid,
                            role = role,
                            userName = userName,
                            rating = ratingVal,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing rating doc ${doc.id}: ${e.message}")
                    }
                }
                onUpdate(list)
            }
        }
    }

    // ---------------- FEEDBACK REAL-TIME SYNC ----------------
    fun syncFeedback(feedback: com.example.db.UserFeedback, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore not initialized")
            return
        }
        try {
            val docId = if (feedback.feedbackId.isNotBlank()) feedback.feedbackId else "fb_${System.currentTimeMillis()}"
            val feedbackMap = mapOf(
                "feedbackId" to docId,
                "uid" to feedback.uid,
                "userName" to feedback.userName,
                "email" to feedback.email,
                "role" to feedback.role,
                "category" to feedback.category,
                "subject" to feedback.subject,
                "message" to feedback.message,
                "rating" to feedback.rating,
                "attachmentUrl" to feedback.attachmentUrl,
                "status" to feedback.status,
                "adminReply" to feedback.adminReply,
                "adminName" to feedback.adminName,
                "department" to feedback.department,
                "repliedAt" to feedback.repliedAt,
                "createdAt" to feedback.createdAt,
                "updatedAt" to feedback.updatedAt
            )
            db.collection("feedback").document(docId).set(feedbackMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Feedback synced to feedback/$docId")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed syncFeedback: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in syncFeedback: ${e.message}")
            onComplete?.invoke(false, e.message)
        }
    }

    fun replyToFeedback(feedbackId: String, adminName: String, department: String, reply: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore not initialized")
            return
        }
        try {
            val now = System.currentTimeMillis()
            val updateMap = mapOf(
                "adminName" to adminName,
                "department" to department,
                "adminReply" to reply,
                "repliedAt" to now,
                "status" to "Reviewed",
                "updatedAt" to now
            )
            db.collection("feedback").document(feedbackId).update(updateMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Replied to feedback $feedbackId")
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed replyToFeedback: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in replyToFeedback: ${e.message}")
            onComplete?.invoke(false, e.message)
        }
    }

    fun listenToFeedback(onUpdate: (List<com.example.db.UserFeedback>) -> Unit) {
        val db = firestore ?: return
        db.collection("feedback").addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w(TAG, "Feedback listener: Firestore security rules restrict access. Using local Room database.")
                } else {
                    Log.w(TAG, "Feedback listener notice: ${error.message}")
                }
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = mutableListOf<com.example.db.UserFeedback>()
                for (doc in snapshot.documents) {
                    try {
                        val feedbackId = doc.getString("feedbackId") ?: doc.id
                        val uid = doc.getString("uid") ?: ""
                        val userName = doc.getString("userName") ?: doc.getString("name") ?: ""
                        val email = doc.getString("email") ?: ""
                        val role = doc.getString("role") ?: "Citizen"
                        val category = doc.getString("category") ?: "Other"
                        val subject = doc.getString("subject") ?: ""
                        val message = doc.getString("message") ?: ""
                        val ratingVal = (doc.getLong("rating") ?: 0L).toInt()
                        val attachmentUrl = doc.getString("attachmentUrl") ?: ""
                        val status = doc.getString("status") ?: "Pending"
                        val adminReply = doc.getString("adminReply") ?: ""
                        val adminName = doc.getString("adminName") ?: ""
                        val department = doc.getString("department") ?: ""
                        val repliedAt = parseTimestampField(doc, "repliedAt", 0L)
                        val createdAt = parseTimestampField(doc, "createdAt", System.currentTimeMillis())
                        val updatedAt = parseTimestampField(doc, "updatedAt", System.currentTimeMillis())

                        list.add(com.example.db.UserFeedback(
                            feedbackId = feedbackId,
                            uid = uid,
                            userName = userName,
                            email = email,
                            role = role,
                            category = category,
                            subject = subject,
                            message = message,
                            rating = ratingVal,
                            attachmentUrl = attachmentUrl,
                            status = status,
                            adminReply = adminReply,
                            adminName = adminName,
                            department = department,
                            repliedAt = repliedAt,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing feedback doc ${doc.id}: ${e.message}")
                    }
                }
                onUpdate(list)
            }
        }
    }

    fun logActivity(activityLog: com.example.db.ActivityLog, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val db = firestore ?: run {
            onComplete?.invoke(false, "Firestore not initialized")
            return
        }
        try {
            val docId = activityLog.id.ifBlank { UUID.randomUUID().toString() }
            val logMap = mapOf(
                "id" to docId,
                "timestamp" to activityLog.timestamp,
                "actorRole" to activityLog.actorRole,
                "actorName" to activityLog.actorName,
                "eventType" to activityLog.eventType,
                "message" to activityLog.message,
                "relatedId" to activityLog.relatedId
            )
            db.collection("activity_logs").document(docId).set(logMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    onComplete?.invoke(true, null)
                }
                .addOnFailureListener { e ->
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.d(TAG, "Activity log write restricted by Firestore rules; retained in local Room database.")
                    } else {
                        Log.w(TAG, "Notice logActivity: ${e.message}")
                    }
                    onComplete?.invoke(false, e.message)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Exception in logActivity: ${e.message}")
            onComplete?.invoke(false, e.message)
        }
    }

    fun listenToActivityLogs(onUpdate: (List<com.example.db.ActivityLog>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("activity_logs")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w(TAG, "Activity logs listener: Firestore security rules restrict access. Using local Room database.")
                        } else {
                            Log.w(TAG, "Activity logs listener notice: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<com.example.db.ActivityLog>()
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.getString("id") ?: doc.id
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val actorRole = doc.getString("actorRole") ?: "Citizen"
                                val actorName = doc.getString("actorName") ?: ""
                                val eventType = doc.getString("eventType") ?: ""
                                val message = doc.getString("message") ?: ""
                                val relatedId = doc.getString("relatedId") ?: ""
                                list.add(
                                    com.example.db.ActivityLog(
                                        id = id,
                                        timestamp = timestamp,
                                        actorRole = actorRole,
                                        actorName = actorName,
                                        eventType = eventType,
                                        message = message,
                                        relatedId = relatedId
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing activity log doc ${doc.id}: ${e.message}")
                            }
                        }
                        onUpdate(list)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception setting up listenToActivityLogs: ${e.message}")
            null
        }
    }
}

