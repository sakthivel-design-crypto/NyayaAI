package com.example.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiApiClient
import com.example.firebase.FirebaseManager
import com.example.firebase.PasswordResetManager
import com.example.util.EmailSender
import com.example.util.GovernmentVerificationEngine
import com.example.model.*
import com.example.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID
import java.util.Locale

const val DEMO_MODE = false

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTitle: String? = null,
    val sourceUrl: String? = null,
    val isWarningNotLocal: Boolean = false
)

class NyayaViewModel : ViewModel() {

    private var sharedPrefs: android.content.SharedPreferences? = null
    private var appContext: Context? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _allUserAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val allUserAccounts: StateFlow<List<UserAccount>> = _allUserAccounts.asStateFlow()

    private val _allReports = MutableStateFlow<List<IncidentReport>>(emptyList())
    val allReports: StateFlow<List<IncidentReport>> = _allReports.asStateFlow()

    private val _allCitizenRequests = MutableStateFlow<List<CitizenRequest>>(emptyList())
    val allCitizenRequests: StateFlow<List<CitizenRequest>> = _allCitizenRequests.asStateFlow()

    private val _isInquiriesLoading = MutableStateFlow(false)
    val isInquiriesLoading: StateFlow<Boolean> = _isInquiriesLoading.asStateFlow()

    private val _isSubmittingInquiry = MutableStateFlow(false)
    val isSubmittingInquiry: StateFlow<Boolean> = _isSubmittingInquiry.asStateFlow()

    private val _inquiryError = MutableStateFlow<String?>(null)
    val inquiryError: StateFlow<String?> = _inquiryError.asStateFlow()

    private val _allAuditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val allAuditLogs: StateFlow<List<AuditLog>> = _allAuditLogs.asStateFlow()

    // Login Method
    fun login(emailOrId: String, password: String, portalType: String = "Citizen", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (!isNetworkAvailable()) {
                onResult(false, "Network error. Please check your internet connection.")
                return@launch
            }

            val normalizedEmailOrId = emailOrId.trim().lowercase()
            val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
            val isEmailFormat = normalizedEmailOrId.matches(emailRegex.toRegex())

            var account = dao.getUserAccount(normalizedEmailOrId)

            when (portalType) {
                "Citizen" -> {
                    if (!isEmailFormat) {
                        onResult(false, "Please enter a valid email address.")
                        return@launch
                    }

                    Log.d("NyayaAuth", "Login email normalized: true")

                    // 1. Authenticate directly against Firebase Authentication (Source of Truth)
                    val authResult = com.example.firebase.FirebaseManager.signInWithEmailDetailed(normalizedEmailOrId, password)

                    when (authResult) {
                        is com.example.firebase.FirebaseAuthResult.Success -> {
                            Log.d("NyayaAuth", "Firebase authentication success: true")
                            val fbUser = authResult.user
                            val uid = fbUser.uid
                            Log.d("NyayaAuth", "Firebase UID received: true")

                            // 2. Fetch / load Citizen profile from Room or Firestore
                            var citizenAccount = dao.getUserAccount(normalizedEmailOrId)
                            if (citizenAccount == null && uid.isNotBlank()) {
                                citizenAccount = dao.getUserAccountByUid(uid)
                            }
                            if (citizenAccount == null) {
                                citizenAccount = com.example.firebase.FirebaseManager.fetchUserProfile(uid, normalizedEmailOrId)
                            }

                            Log.d("NyayaAuth", "Firestore profile lookup completed: true")

                            if (citizenAccount == null) {
                                val defaultName = if (!fbUser.displayName.isNullOrBlank()) {
                                    fbUser.displayName!!
                                } else {
                                    normalizedEmailOrId.substringBefore("@")
                                }
                                citizenAccount = UserAccount(
                                    email = normalizedEmailOrId,
                                    name = defaultName,
                                    passwordHash = "",
                                    role = "Citizen",
                                    uid = uid,
                                    createdAt = fbUser.metadata?.creationTimestamp ?: System.currentTimeMillis(),
                                    lastLogin = System.currentTimeMillis(),
                                    status = "Active",
                                    isApproved = true,
                                    isDisabled = false
                                )
                            } else {
                                citizenAccount = citizenAccount.copy(
                                    uid = if (citizenAccount.uid.isNotBlank()) citizenAccount.uid else uid,
                                    email = normalizedEmailOrId,
                                    role = "Citizen",
                                    name = if (citizenAccount.name.isNotBlank()) citizenAccount.name else (fbUser.displayName?.ifBlank { null } ?: normalizedEmailOrId.substringBefore("@")),
                                    passwordHash = ""
                                )
                            }

                            Log.d("NyayaAuth", "Role detected: ${citizenAccount.role.lowercase()}")

                            if (citizenAccount.isDisabled || citizenAccount.status.equals("Disabled", ignoreCase = true) || citizenAccount.status.equals("Blocked", ignoreCase = true)) {
                                onResult(false, "This account has been disabled. Please contact an administrator.")
                                return@launch
                            }

                            if (!citizenAccount.role.equals("Citizen", ignoreCase = true) && !citizenAccount.role.equals("Admin", ignoreCase = true)) {
                                onResult(false, "Unauthorized access. Citizen role required.")
                                return@launch
                            }

                            account = citizenAccount
                        }
                        is com.example.firebase.FirebaseAuthResult.Error -> {
                            Log.d("NyayaAuth", "Firebase authentication success: false")
                            onResult(false, authResult.message)
                            return@launch
                        }
                    }
                }
                "Authority" -> {
                    val cleanIdentity = normalizedEmailOrId.trim()
                    if (cleanIdentity.isBlank()) {
                        onResult(false, "INVALID CREDENTIALS: Employee ID/email or password is incorrect.")
                        return@launch
                    }

                    var matched = dao.getUserAccountByIdentity(cleanIdentity)
                    if (matched == null) {
                        matched = dao.getUserAccount(cleanIdentity)
                    }

                    if (matched == null) {
                        onResult(false, "INVALID CREDENTIALS: Employee ID/email or password is incorrect.")
                        return@launch
                    }

                    account = matched

                    if (!account.role.equals("Authority", ignoreCase = true) && !account.role.equals("Admin", ignoreCase = true)) {
                        onResult(false, "ACCESS DENIED: This account is not authorized for the Authority Portal.")
                        return@launch
                    }

                    if (account.isDisabled || account.status.equals("Disabled", ignoreCase = true) || account.status.equals("Blocked", ignoreCase = true)) {
                        onResult(false, "DISABLED: Your authority account has been disabled by an administrator.")
                        return@launch
                    }

                    if (account.status.equals("Rejected", ignoreCase = true) || account.approvalStatus.equals("REJECTED", ignoreCase = true) || account.verificationStatus.equals("REJECTED", ignoreCase = true)) {
                        onResult(false, "REJECTED: Your Authority login request has been cancelled/rejected by the Administrator. Please contact the Administrator for further assistance.")
                        return@launch
                    }

                    val isPending = !account.isApproved ||
                            account.status.equals("PENDING_VERIFICATION", ignoreCase = true) ||
                            account.approvalStatus.equals("PENDING_VERIFICATION", ignoreCase = true) ||
                            account.status.equals("PENDING_APPROVAL", ignoreCase = true) ||
                            account.approvalStatus.equals("PENDING_APPROVAL", ignoreCase = true) ||
                            account.verificationStatus.equals("PENDING_VERIFICATION", ignoreCase = true)

                    if (isPending) {
                        onResult(false, "PENDING: Your Authority account is awaiting Administrator verification. Please try again after your account has been approved.")
                        return@launch
                    }

                    val validPass = (account.passwordHash == password)

                    if (!validPass) {
                        recordActivityLog(
                            actorRole = "Authority",
                            actorName = account.name,
                            eventType = "AUTHORITY_LOGIN_FAILED",
                            message = "Failed authority login attempt for ID: ${account.employeeId.ifEmpty { account.email }}",
                            relatedId = account.employeeId.ifEmpty { account.email }
                        )
                        onResult(false, "INVALID CREDENTIALS: Employee ID/email or password is incorrect.")
                        return@launch
                    }
                }
                "Admin" -> {
                    if (normalizedEmailOrId.isBlank()) {
                        onResult(false, "Please enter the admin email.")
                        return@launch
                    }
                    if (password.isBlank()) {
                        onResult(false, "Please enter the admin password.")
                        return@launch
                    }

                    // 1. Firebase Authentication verifies the password (Source of Truth)
                    val authResult = FirebaseManager.signInWithEmailDetailed(normalizedEmailOrId, password)

                    when (authResult) {
                        is com.example.firebase.FirebaseAuthResult.Success -> {
                            val fbUser = authResult.user
                            val uid = fbUser.uid

                            // 2. Verify that the authenticated Firebase account is an authorized Admin
                            val isAuthorizedAdminEmail = normalizedEmailOrId.equals("sakthivel.s8317@gmail.com", ignoreCase = true) ||
                                    normalizedEmailOrId.equals("srisakthi1357@gmail.com", ignoreCase = true)
                            
                            var adminAccount = dao.getUserAccount(normalizedEmailOrId)
                            if (adminAccount == null && uid.isNotBlank()) {
                                adminAccount = dao.getUserAccountByUid(uid)
                            }
                            if (adminAccount == null) {
                                adminAccount = FirebaseManager.fetchUserProfile(uid, normalizedEmailOrId)
                            }

                            // Strict Authorization Verification: Must be the designated Admin account and authorized by Firebase
                            val isAuthorizedByFirebase = FirebaseManager.verifyAdminAuthorization(fbUser)
                            if (!isAuthorizedAdminEmail || !isAuthorizedByFirebase) {
                                FirebaseManager.signOut(appContext)
                                onResult(false, "Your account is authenticated but is not authorized as an administrator.")
                                return@launch
                            }

                            if (adminAccount != null && (adminAccount.isDisabled || adminAccount.status.equals("Disabled", ignoreCase = true) || adminAccount.status.equals("Blocked", ignoreCase = true))) {
                                FirebaseManager.signOut(appContext)
                                onResult(false, "This account has been disabled. Please contact an administrator.")
                                return@launch
                            }

                            if (adminAccount == null) {
                                adminAccount = UserAccount(
                                    email = normalizedEmailOrId,
                                    name = if (!fbUser.displayName.isNullOrBlank()) fbUser.displayName!! else "System Administrator",
                                    passwordHash = "",
                                    role = "Admin",
                                    uid = uid,
                                    createdAt = fbUser.metadata?.creationTimestamp ?: System.currentTimeMillis(),
                                    lastLogin = System.currentTimeMillis(),
                                    status = "Active",
                                    isApproved = true,
                                    isDisabled = false
                                )
                                dao.insertUserAccount(adminAccount)
                                FirebaseManager.syncUserAccount(adminAccount)
                            } else {
                                adminAccount = adminAccount.copy(
                                    uid = if (adminAccount.uid.isNotBlank()) adminAccount.uid else uid,
                                    role = "Admin",
                                    passwordHash = ""
                                )
                            }

                            account = adminAccount
                        }
                        is com.example.firebase.FirebaseAuthResult.Error -> {
                            onResult(false, authResult.message)
                            return@launch
                        }
                    }
                }
            }

            // Successful Auth, update profile & Firestore
            if (account != null) {
                val now = System.currentTimeMillis()
                val cleanDisplayName = formatDisplayName(account.name, account.email)
                val updatedAccount = account.copy(
                    name = cleanDisplayName,
                    lastLogin = now,
                    lastSeen = now,
                    isOnline = true,
                    onlineStatus = "Online",
                    status = "Active"
                )
                dao.insertUserAccount(updatedAccount)
                FirebaseManager.syncUserAccount(updatedAccount)
                _currentUserAccount.value = updatedAccount

                if (portalType == "Authority") {
                    recordActivityLog(
                        actorRole = "Authority",
                        actorName = updatedAccount.name,
                        eventType = "AUTHORITY_LOGIN",
                        message = "Authority login successful for ID: ${updatedAccount.employeeId.ifEmpty { updatedAccount.email }}, Dept: ${updatedAccount.department}",
                        relatedId = updatedAccount.employeeId.ifEmpty { updatedAccount.email }
                    )
                }

                val profile = UserProfile(
                    id = "current_user",
                    name = cleanDisplayName,
                    email = updatedAccount.email,
                    points = 300,
                    badges = if (updatedAccount.role == "Admin") "Legal Expert" else "",
                    role = updatedAccount.role
                )
                dao.insertUserProfile(profile)
                sharedPrefs?.edit()?.putBoolean("is_logged_in", true)?.putString("logged_in_email", updatedAccount.email)?.apply()
                _isLoggedIn.value = true
                _userProfile.value = profile
                recordActivityLog(
                    actorRole = updatedAccount.role,
                    actorName = updatedAccount.name,
                    eventType = "USER_LOGIN",
                    message = "User authenticated and logged into portal",
                    relatedId = updatedAccount.email
                )
                onResult(true, "Welcome back, ${cleanDisplayName}!")
            }
        }
    }

    fun registerAuthorityAccount(
        fullName: String,
        officialEmail: String,
        password: String,
        department: String,
        designation: String,
        proofImage: String,
        onResult: (Boolean, String, UserAccount?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = fullName.trim()
            val email = officialEmail.trim().lowercase()
            val pass = password.trim()
            val dept = department.trim()
            val desig = designation.trim()
            val proof = proofImage.trim()

            if (name.isBlank() || email.isBlank() || pass.isBlank() || dept.isBlank() || desig.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "All fields are mandatory. Please enter your name, official email, password, department, and designation.", null)
                }
                return@launch
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Please enter a valid official email address.", null)
                }
                return@launch
            }

            if (pass.length < 6) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Password must be at least 6 characters long.", null)
                }
                return@launch
            }

            if (proof.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Official Government Staff Proof is mandatory. Please upload your proof image.", null)
                }
                return@launch
            }

            var existing = dao.getUserAccount(email)
            if (existing == null) {
                existing = _allUserAccounts.value.find { it.email.equals(email, ignoreCase = true) }
            }
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    onResult(false, "An account with this official email address already exists.", null)
                }
                return@launch
            }

            val now = System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date(now))
            val timeStr = timeFormat.format(java.util.Date(now))

            val empId = "GOV-" + (1000..9999).random()
            val newAccount = UserAccount(
                email = email,
                name = name,
                passwordHash = pass,
                role = "Authority",
                isDisabled = false,
                isApproved = false,
                status = "PENDING_ADMIN_VERIFICATION",
                approvalStatus = "PENDING_ADMIN_VERIFICATION",
                verificationStatus = "PENDING_ADMIN_VERIFICATION",
                department = dept,
                designation = desig,
                district = dept,
                employeeId = empId,
                proofImage = proof,
                createdAt = now,
                lastLogin = now,
                authProvider = "Government Identity Verification"
            )

            dao.insertUserAccount(newAccount)
            FirebaseManager.syncUserAccount(newAccount)

            val regNotif = Notification(
                id = "not_reg_" + UUID.randomUUID().toString().take(8),
                userEmail = email,
                title = "Authority verification request submitted",
                message = "Verification request submitted\nDate: $dateStr\nTime: $timeStr\nStatus: Pending Admin Verification",
                timestamp = now,
                isRead = false,
                isAuthority = true,
                isHighPriority = true
            )
            dao.insertNotification(regNotif)
            FirebaseManager.syncNotification(regNotif)

            recordActivityLog(
                actorRole = "Authority",
                actorName = name,
                eventType = "AUTHORITY_REGISTER_PENDING",
                message = "New Authority account submitted for verification: $name ($email), Department: $dept, Designation: $desig",
                relatedId = email
            )

            withContext(Dispatchers.Main) {
                onResult(true, "Registration submitted successfully. Your account is pending administrator verification. You will be able to access the Authority Portal only after approval.", newAccount)
            }
        }
    }

    fun loginAuthorityAccount(
        department: String,
        designation: String,
        emailInput: String,
        passwordInput: String,
        onResult: (Boolean, String, UserAccount?, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanDept = department.trim()
            val cleanDesig = designation.trim()
            val cleanEmail = emailInput.trim().lowercase()
            val cleanPass = passwordInput.trim()

            if (cleanDept.isBlank() || cleanDesig.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Please select both Department and Designation / Official Rank.", null, "ERROR")
                }
                return@launch
            }

            if (cleanEmail.isBlank() || cleanPass.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Please enter your official registered email and password.", null, "ERROR")
                }
                return@launch
            }

            var account = dao.getUserAccount(cleanEmail)
            if (account == null) {
                account = _allUserAccounts.value.find { it.email.equals(cleanEmail, ignoreCase = true) }
            }
            if (account == null) {
                account = FirebaseManager.fetchUserProfile("", cleanEmail)
            }

            if (account == null) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = cleanEmail,
                    eventType = "AUTHORITY_LOGIN_FAILED",
                    message = "Authority login failed: Account not found for $cleanEmail",
                    relatedId = cleanEmail
                )
                withContext(Dispatchers.Main) {
                    onResult(false, "Invalid credentials. Please check your email and password.", null, "ERROR")
                }
                return@launch
            }

            val validPassword = account.passwordHash == cleanPass ||
                    (account.passwordHash == "GOVERNMENT_VERIFIED" && cleanPass.isNotEmpty()) ||
                    (account.passwordHash == "GOOGLE_AUTH")

            if (!validPassword) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = account.name.ifEmpty { cleanEmail },
                    eventType = "AUTHORITY_LOGIN_FAILED",
                    message = "Authority login failed: Incorrect password for $cleanEmail",
                    relatedId = cleanEmail
                )
                withContext(Dispatchers.Main) {
                    onResult(false, "Invalid credentials. Please check your email and password.", null, "ERROR")
                }
                return@launch
            }

            if (!account.role.equals("Authority", ignoreCase = true) && !account.role.equals("Admin", ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Access Denied: This account is not registered as an Authority.", null, "ERROR")
                }
                return@launch
            }

            val isRejected = account.status.equals("Rejected", ignoreCase = true) ||
                    account.approvalStatus.equals("REJECTED", ignoreCase = true) ||
                    account.verificationStatus.equals("REJECTED", ignoreCase = true) ||
                    account.status.equals("Cancelled", ignoreCase = true) ||
                    account.approvalStatus.equals("CANCELLED", ignoreCase = true)

            if (isRejected) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = account.name,
                    eventType = "AUTHORITY_LOGIN_REJECTED",
                    message = "Authority login rejected: Account has been cancelled/rejected by administrator for ${account.email}",
                    relatedId = account.email
                )
                withContext(Dispatchers.Main) {
                    onResult(
                        false,
                        "Admin cancelled your login due to invalid credentials or unsuccessful verification.",
                        account,
                        "REJECTED"
                    )
                }
                return@launch
            }

            if (account.isDisabled || account.status.equals("Disabled", ignoreCase = true) || account.status.equals("Blocked", ignoreCase = true)) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = account.name,
                    eventType = "AUTHORITY_LOGIN_BLOCKED",
                    message = "Login blocked: Account is disabled by administrator for ${account.email}",
                    relatedId = account.email
                )
                withContext(Dispatchers.Main) {
                    onResult(
                        false,
                        "Admin cancelled your login due to invalid credentials or unsuccessful verification.",
                        account,
                        "DISABLED"
                    )
                }
                return@launch
            }

            val isPending = !account.isApproved ||
                    account.status.equals("PENDING_ADMIN_VERIFICATION", ignoreCase = true) ||
                    account.approvalStatus.equals("PENDING_ADMIN_VERIFICATION", ignoreCase = true) ||
                    account.verificationStatus.equals("PENDING_ADMIN_VERIFICATION", ignoreCase = true) ||
                    account.status.equals("PENDING_VERIFICATION", ignoreCase = true) ||
                    account.approvalStatus.equals("PENDING_VERIFICATION", ignoreCase = true) ||
                    account.status.equals("PENDING_APPROVAL", ignoreCase = true) ||
                    account.approvalStatus.equals("PENDING_APPROVAL", ignoreCase = true)

            if (isPending) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = account.name,
                    eventType = "AUTHORITY_LOGIN_PENDING",
                    message = "Authority login denied: Account is pending verification for ${account.email}",
                    relatedId = account.email
                )
                withContext(Dispatchers.Main) {
                    onResult(
                        false,
                        "Your authority account is pending administrator verification.",
                        account,
                        "PENDING_VERIFICATION"
                    )
                }
                return@launch
            }

            val now = System.currentTimeMillis()
            val updatedAccount = account.copy(
                lastLogin = now,
                lastSeen = now,
                isOnline = true,
                onlineStatus = "Online",
                department = if (cleanDept.isNotBlank()) cleanDept else account.department,
                designation = if (cleanDesig.isNotBlank()) cleanDesig else account.designation
            )

            dao.insertUserAccount(updatedAccount)
            FirebaseManager.syncUserAccount(updatedAccount)

            _currentUserAccount.value = updatedAccount

            val profile = UserProfile(
                id = "current_user",
                name = updatedAccount.name,
                email = updatedAccount.email,
                points = 500,
                badges = "Verified Authority",
                role = "Authority"
            )
            dao.insertUserProfile(profile)
            _userProfile.value = profile
            _isLoggedIn.value = true

            sharedPrefs?.edit()
                ?.putBoolean("is_logged_in", true)
                ?.putString("logged_in_email", updatedAccount.email)
                ?.apply()

            recordActivityLog(
                actorRole = "Authority",
                actorName = updatedAccount.name,
                eventType = "AUTHORITY_LOGIN_SUCCESSFUL",
                message = "Authority Login Successful: ${updatedAccount.name}, Department: ${updatedAccount.department}, Designation: ${updatedAccount.designation}",
                relatedId = updatedAccount.email
            )

            withContext(Dispatchers.Main) {
                onResult(true, "Government identity verified. Welcome, ${updatedAccount.name}!", updatedAccount, "SUCCESS")
            }
        }
    }

    fun loginAuthority(
        department: String,
        designation: String,
        identity: String = "",
        password: String = "",
        jurisdiction: String = "",
        onResult: (Boolean, String, UserAccount?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanDept = department.trim()
            val cleanDesig = designation.trim()

            if (cleanDept.isBlank() || cleanDesig.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Please select both Department and Designation / Official Rank.", null)
                }
                return@launch
            }

            // Run Authoritative Government Identity Verification Engine
            val verification = GovernmentVerificationEngine.verifyAuthorityRole(
                department = cleanDept,
                designation = cleanDesig
            )

            if (!verification.verified) {
                // Record Security Audit Failure Log
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = cleanDesig.ifEmpty { "Authority Officer" },
                    eventType = "AUTHORITY_LOGIN_FAILED",
                    message = "Authority authentication rejected: ${verification.userFacingMessage}",
                    relatedId = cleanDept
                )

                withContext(Dispatchers.Main) {
                    onResult(false, verification.userFacingMessage, null)
                }
                return@launch
            }

            // Look up or initialize account for this official authority node
            val officerEmail = verification.officialEmail
            val officerName = verification.officerName
            val officerEmpId = verification.employeeId
            val now = System.currentTimeMillis()

            var existingAccount = dao.getUserAccount(officerEmail)
            if (existingAccount == null) {
                existingAccount = dao.getUserAccountByIdentity(officerEmpId)
            }

            // Check if existing account is disabled
            val account = existingAccount
            if (account != null && (account.isDisabled || account.status.equals("Disabled", ignoreCase = true) || account.status.equals("Blocked", ignoreCase = true))) {
                recordActivityLog(
                    actorRole = "Authority",
                    actorName = account.name,
                    eventType = "AUTHORITY_LOGIN_BLOCKED",
                    message = "Login blocked: Account is disabled by administrator for ${account.email}",
                    relatedId = officerEmpId
                )
                withContext(Dispatchers.Main) {
                    onResult(false, "Your authority account has been disabled. Please contact your administrator.", null)
                }
                return@launch
            }

            val updatedAccount = (existingAccount ?: UserAccount(
                email = officerEmail,
                name = officerName,
                passwordHash = "GOVERNMENT_VERIFIED",
                role = "Authority",
                createdAt = now
            )).copy(
                name = officerName,
                email = officerEmail,
                employeeId = officerEmpId,
                department = cleanDept,
                designation = cleanDesig,
                district = cleanDept,
                role = "Authority",
                status = "Active",
                isDisabled = false,
                isApproved = true,
                approvalStatus = "APPROVED",
                authProvider = "Government Identity Verification",
                verificationStatus = "VERIFIED",
                lastVerifiedAt = now,
                lastLogin = now,
                lastSeen = now,
                isOnline = true,
                onlineStatus = "Online"
            )

            // Save to Room DAO and synchronize to Firestore
            dao.insertUserAccount(updatedAccount)
            FirebaseManager.syncUserAccount(updatedAccount)

            _currentUserAccount.value = updatedAccount

            // Set current user profile session
            val profile = UserProfile(
                id = "current_user",
                name = officerName,
                email = officerEmail,
                points = 500,
                badges = "Verified Authority",
                role = "Authority"
            )
            dao.insertUserProfile(profile)
            _userProfile.value = profile
            _isLoggedIn.value = true

            sharedPrefs?.edit()
                ?.putBoolean("is_logged_in", true)
                ?.putString("logged_in_email", officerEmail)
                ?.apply()

            // Record Authoritative Successful Login Audit Log
            recordActivityLog(
                actorRole = "Authority",
                actorName = officerName,
                eventType = "AUTHORITY_LOGIN_SUCCESSFUL",
                message = "Authority Login Successful: $officerName, Department: $cleanDept, Designation: $cleanDesig",
                relatedId = officerEmpId
            )

            withContext(Dispatchers.Main) {
                onResult(true, "Government identity verified. Welcome, $officerName!", updatedAccount)
            }
        }
    }

    // Helper to format clean display names from name or email prefix
    fun formatDisplayName(currentName: String?, email: String?): String {
        val cleanName = currentName?.trim() ?: ""
        val emailStr = email?.trim()?.lowercase() ?: ""
        val placeholders = setOf("demo citizen", "aarav sharma", "citizen user", "user", "citizen defender", "citizen", "guest", "")

        if (cleanName.isNotBlank() && cleanName.lowercase() !in placeholders) {
            return cleanName
        }

        if (emailStr.contains("@")) {
            val prefix = emailStr.substringBefore("@")
            val words = prefix.split(".", "_", "-").map { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }.filter { it.isNotBlank() && !it.all { char -> char.isDigit() } }

            if (words.isNotEmpty()) {
                return words.joinToString(" ")
            }
        }

        return "Citizen"
    }

    // Google Sign-In Handler
    fun signInWithGoogle(
        idToken: String?,
        googleEmail: String?,
        googleName: String?,
        googlePhotoUrl: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (!isNetworkAvailable()) {
                onResult(false, "Network error. Please check your internet connection.")
                return@launch
            }

            var email = googleEmail?.trim()?.lowercase() ?: ""
            var rawName = googleName?.trim() ?: ""
            var uid = ""
            var photoUrl = googlePhotoUrl ?: ""

            if (!idToken.isNullOrBlank()) {
                val authResult = FirebaseManager.signInWithGoogleTokenDetailed(idToken)
                when (authResult) {
                    is com.example.firebase.FirebaseAuthResult.Success -> {
                        val fbUser = authResult.user
                        uid = fbUser.uid
                        if (!fbUser.email.isNullOrBlank()) email = fbUser.email!!.lowercase().trim()
                        if (!fbUser.displayName.isNullOrBlank()) rawName = fbUser.displayName!!
                        if (fbUser.photoUrl != null) photoUrl = fbUser.photoUrl.toString()
                    }
                    is com.example.firebase.FirebaseAuthResult.Error -> {
                        Log.w("NyayaAuth", "Firebase Google Sign-In failed: ${authResult.message}")
                        if (authResult.isNetworkError) {
                            onResult(false, authResult.message)
                            return@launch
                        }
                    }
                }
            }

            if (email.isBlank()) {
                val fbUser = FirebaseManager.auth?.currentUser
                if (fbUser?.email != null) {
                    email = fbUser.email!!.lowercase().trim()
                    rawName = if (!fbUser.displayName.isNullOrBlank()) fbUser.displayName!! else rawName
                    uid = fbUser.uid
                }
            }

            if (email.isBlank()) {
                onResult(false, "Failed to retrieve Google account details. Please try again.")
                return@launch
            }

            val existingAccount = dao.getUserAccount(email)
            if (existingAccount != null) {
                if (existingAccount.isDisabled || existingAccount.status.equals("Disabled", ignoreCase = true) || existingAccount.status.equals("Blocked", ignoreCase = true)) {
                    onResult(false, "This account is disabled or blocked. Please contact an administrator.")
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val finalUid = if (uid.isNotBlank()) uid else (existingAccount?.uid?.ifBlank { "google_$email" } ?: "google_$email")
            val resolvedName = formatDisplayName(if (rawName.isNotBlank()) rawName else existingAccount?.name, email)

            val accountToSync = if (existingAccount != null) {
                existingAccount.copy(
                    name = resolvedName,
                    uid = finalUid,
                    passwordHash = if (existingAccount.passwordHash.isBlank() || existingAccount.passwordHash == "GOOGLE_AUTH") "GOOGLE_AUTH" else existingAccount.passwordHash,
                    role = "Citizen",
                    lastLogin = now,
                    lastSeen = now,
                    isOnline = true,
                    onlineStatus = "Online",
                    status = "Active",
                    profilePhoto = if (photoUrl.isNotBlank()) photoUrl else existingAccount.profilePhoto
                )
            } else {
                UserAccount(
                    email = email,
                    name = resolvedName,
                    passwordHash = "GOOGLE_AUTH",
                    role = "Citizen",
                    uid = finalUid,
                    isDisabled = false,
                    isApproved = true,
                    createdAt = now,
                    lastLogin = now,
                    lastSeen = now,
                    isOnline = true,
                    onlineStatus = "Online",
                    profilePhoto = photoUrl,
                    status = "Active",
                    contact = "+91 98765 43210"
                )
            }

            dao.insertUserAccount(accountToSync)
            FirebaseManager.syncUserAccount(accountToSync)
            _currentUserAccount.value = accountToSync

            val profile = UserProfile(
                id = "current_user",
                name = resolvedName,
                email = accountToSync.email,
                points = 300,
                badges = if (accountToSync.role == "Admin") "Legal Expert" else "",
                role = accountToSync.role
            )
            dao.insertUserProfile(profile)

            sharedPrefs?.edit()?.putBoolean("is_logged_in", true)?.putString("logged_in_email", email)?.apply()
            _isLoggedIn.value = true
            _userProfile.value = profile

            recordActivityLog(
                actorRole = accountToSync.role,
                actorName = resolvedName,
                eventType = "GOOGLE_LOGIN",
                message = "User authenticated via Google Sign-In",
                relatedId = email
            )

            onResult(true, "Welcome, ${resolvedName}!")
        }
    }

    // Register Method
    fun register(name: String, email: String, password: String, role: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (!isNetworkAvailable()) {
                onResult(false, "Network error. Please check your internet connection.")
                return@launch
            }

            val normalizedEmail = email.trim().lowercase()
            val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
            val isEmailFormat = normalizedEmail.matches(emailRegex.toRegex())

            if (!isEmailFormat) {
                onResult(false, "Invalid email address.")
                return@launch
            }

            val existing = dao.getUserAccount(normalizedEmail)
            if (existing != null) {
                onResult(false, "Email is already registered.")
            } else {
                // Try creating Firebase Auth user
                FirebaseManager.registerWithEmail(normalizedEmail, password)
                val fbUser = FirebaseManager.auth?.currentUser
                val uid = if (fbUser != null && fbUser.uid.isNotBlank()) fbUser.uid else ("usr_" + java.util.UUID.randomUUID().toString().take(8))

                val newAccount = UserAccount(
                    email = normalizedEmail,
                    name = name,
                    passwordHash = "",
                    role = role,
                    isDisabled = false,
                    isApproved = (role != "Authority"),
                    uid = uid,
                    phone = "",
                    status = "Active",
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis(),
                    device = "Android Device",
                    onlineStatus = "Online"
                )
                dao.insertUserAccount(newAccount)
                FirebaseManager.syncUserAccount(newAccount)
                
                // Set initial profile
                val profile = UserProfile(
                    id = "current_user",
                    name = name,
                    email = normalizedEmail,
                    points = 300,
                    role = role
                )
                dao.insertUserProfile(profile)

                if (role == "Citizen") {
                    sharedPrefs?.edit()?.putBoolean("is_logged_in", true)?.putString("logged_in_email", normalizedEmail)?.apply()
                    _isLoggedIn.value = true
                    _userProfile.value = profile
                }

                onResult(true, if (role == "Authority") "Registration submitted! Awaiting Admin approval." else "Registration successful as $role!")
            }
        }
    }

    // Logout
    fun logout() {
        val currentEmail = _userProfile.value.email
        if (currentEmail.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val account = dao.getUserAccount(currentEmail)
                if (account != null) {
                    val now = System.currentTimeMillis()
                    val updated = account.copy(
                        isOnline = false,
                        onlineStatus = "Offline",
                        lastSeen = now
                    )
                    dao.insertUserAccount(updated)
                    FirebaseManager.syncUserAccount(updated)
                }
            }
        }
        FirebaseManager.signOut(appContext)
        sharedPrefs?.edit()?.putBoolean("is_logged_in", false)?.remove("logged_in_email")?.apply()
        _isLoggedIn.value = false
        _currentUserAccount.value = null
        adminResetAuthorizedEmail = null
        adminResetToken = null
        adminResetTokenTimestamp = 0L
        viewModelScope.launch {
            dao.insertUserProfile(UserProfile(role = "Guest"))
        }
    }

    // Secure Admin Password Reset & Verification Engine
    private var adminResetAuthorizedEmail: String? = null
    private var adminResetToken: String? = null
    private var adminResetTokenTimestamp: Long = 0L
    private var adminCodeAttemptCount: Int = 0
    private var adminCodeLockoutUntil: Long = 0L

    // Constant-time cryptographic digest verification (Hex: 19bb51e9d8c56c781be0c24a6903f7793ba2a5da3fed3ef0fd9b73d532660fea)
    private val ADMIN_VERIFICATION_DIGEST_BYTES = byteArrayOf(
        0x19.toByte(), 0xbb.toByte(), 0x51.toByte(), 0xe9.toByte(), 0xd8.toByte(), 0xc5.toByte(), 0x6c.toByte(), 0x78.toByte(),
        0x1b.toByte(), 0xe0.toByte(), 0xc2.toByte(), 0x4a.toByte(), 0x69.toByte(), 0x03.toByte(), 0xf7.toByte(), 0x79.toByte(),
        0x3b.toByte(), 0xa2.toByte(), 0xa5.toByte(), 0xda.toByte(), 0x3f.toByte(), 0xed.toByte(), 0x3e.toByte(), 0xf0.toByte(),
        0xfd.toByte(), 0x9b.toByte(), 0x73.toByte(), 0xd5.toByte(), 0x32.toByte(), 0x66.toByte(), 0x0f.toByte(), 0xea.toByte()
    )

    fun verifyAdminEmailForReset(email: String, onResult: (Boolean, String) -> Unit) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onResult(false, "Please enter a valid email address.")
            return
        }

        val isAuthorizedAdmin = cleanEmail == "sakthivel.s8317@gmail.com" || cleanEmail == "srisakthi1357@gmail.com"
        if (!isAuthorizedAdmin) {
            adminResetAuthorizedEmail = null
            onResult(false, "No authorized administrator account found for this email.")
            return
        }

        adminResetAuthorizedEmail = cleanEmail
        onResult(true, "Admin account verified. Please enter the verification code.")
    }

    fun verifyAdminVerificationCode(email: String, codeInput: String, onResult: (Boolean, String) -> Unit) {
        val now = System.currentTimeMillis()
        if (now < adminCodeLockoutUntil) {
            val remainingSecs = (adminCodeLockoutUntil - now) / 1000 + 1
            onResult(false, "Too many failed attempts. Please wait $remainingSecs seconds before trying again.")
            return
        }

        val cleanEmail = email.trim().lowercase()
        if (cleanEmail != "sakthivel.s8317@gmail.com" && cleanEmail != "srisakthi1357@gmail.com") {
            onResult(false, "No authorized administrator account found for this email.")
            return
        }

        val trimmedCode = codeInput.trim()
        if (trimmedCode.isEmpty()) {
            onResult(false, "Please enter the admin verification code.")
            return
        }

        // Rate-abuse protection
        val inputDigest = try {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(trimmedCode.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            onResult(false, "Security verification service encountered an error.")
            return
        }

        val isValid = java.security.MessageDigest.isEqual(inputDigest, ADMIN_VERIFICATION_DIGEST_BYTES)

        if (!isValid) {
            adminCodeAttemptCount++
            if (adminCodeAttemptCount >= 5) {
                adminCodeLockoutUntil = now + 60_000L // 1 minute lockout
                adminCodeAttemptCount = 0
                onResult(false, "Too many failed attempts. Verification locked for 60 seconds.")
            } else {
                val attemptsLeft = 5 - adminCodeAttemptCount
                onResult(false, "Invalid verification code. ($attemptsLeft attempts remaining)")
            }
            return
        }

        // Reset rate counters upon success
        adminCodeAttemptCount = 0
        adminCodeLockoutUntil = 0L

        // Generate one-time cryptographic reset token valid for 10 minutes
        adminResetToken = java.util.UUID.randomUUID().toString()
        adminResetTokenTimestamp = now
        adminResetAuthorizedEmail = cleanEmail

        onResult(true, "Verification successful. You may now create a new password.")
    }

    fun verifySecretNumber(secretInput: String, onResult: (Boolean, String) -> Unit) {
        verifyAdminVerificationCode("sakthivel.s8317@gmail.com", secretInput, onResult)
    }

    fun resetAdminPasswordWithToken(newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) {
        val now = System.currentTimeMillis()
        val token = adminResetToken
        val email = adminResetAuthorizedEmail ?: "sakthivel.s8317@gmail.com"

        if (token.isNullOrEmpty() || (now - adminResetTokenTimestamp) > 10 * 60 * 1000) {
            adminResetToken = null
            adminResetAuthorizedEmail = null
            onResult(false, "Verification session expired. Please verify your admin credentials again.")
            return
        }

        val p1 = newPass.trim()
        val p2 = confirmPass.trim()

        if (p1.isEmpty() || p2.isEmpty()) {
            onResult(false, "Please fill in both password fields.")
            return
        }

        if (p1 != p2) {
            onResult(false, "Passwords do not match.")
            return
        }

        val hasMinLength = p1.length >= 8
        val hasUppercase = p1.any { it.isUpperCase() }
        val hasLowercase = p1.any { it.isLowerCase() }
        val hasDigit = p1.any { it.isDigit() }
        val hasSpecial = p1.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }

        if (!hasMinLength || !hasUppercase || !hasLowercase || !hasDigit || !hasSpecial) {
            onResult(false, "Password must be at least 8 characters with uppercase, lowercase, number, and special character.")
            return
        }

        viewModelScope.launch {
            try {
                // Invalidate one-time reset token immediately after consumption
                adminResetToken = null
                adminResetAuthorizedEmail = null

                var account = dao.getUserAccount(email)
                if (account == null) {
                    account = UserAccount(
                        email = email,
                        name = "System Administrator",
                        passwordHash = "",
                        role = "Admin",
                        uid = "admin_sakthivel",
                        createdAt = System.currentTimeMillis(),
                        lastLogin = System.currentTimeMillis(),
                        status = "Active",
                        isApproved = true
                    )
                } else {
                    account = account.copy(passwordHash = "")
                }

                dao.insertUserAccount(account)
                FirebaseManager.syncUserAccount(account)

                // Update real Firebase Authentication password if session is active
                try {
                    FirebaseManager.auth?.currentUser?.updatePassword(p1)
                } catch (_: Exception) {}

                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "ADMIN_PASSWORD_RESET",
                    message = "Administrator password updated successfully via secure verification.",
                    relatedId = email
                )
                onResult(true, "Your admin password has been updated successfully.")
            } catch (e: Exception) {
                onResult(false, "Failed to update password: ${e.message}")
            }
        }
    }

    fun resetAdminPasswordWithSecret(newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) {
        resetAdminPasswordWithToken(newPass, confirmPass, onResult)
    }

    // Forgot Password State & Firebase Auth Reset Link
    /**
     * Send Real Firebase Authentication Password Reset Email for Citizen.
     * Handles Google-Only account detection, email validation, and Firebase error handling.
     */
    fun sendCitizenPasswordResetEmail(
        email: String,
        onResult: (success: Boolean, message: String, isGoogleOnly: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            PasswordResetManager.sendPasswordResetEmail(email) { success, message, isGoogleOnly ->
                onResult(success, message, isGoogleOnly)
            }
        }
    }

    fun forgotPassword(email: String, onResult: (Boolean, String) -> Unit) {
        sendCitizenPasswordResetEmail(email) { success, msg, _ ->
            onResult(success, msg)
        }
    }

    fun resetPassword(email: String, newPass: String, confirmPass: String = newPass, onResult: (Boolean, String) -> Unit) {
        sendCitizenPasswordResetEmail(email) { success, msg, _ ->
            onResult(success, msg)
        }
    }

    // File Incident / SOS Report
    fun fileReport(title: String, description: String, category: String, lat: Double = 0.0, lng: Double = 0.0) {
        viewModelScope.launch(Dispatchers.IO) {
            val reportId = "rep_" + UUID.randomUUID().toString().take(6)
            val profile = _userProfile.value
            val newReport = IncidentReport(
                id = reportId,
                reporterName = profile.name,
                reporterEmail = profile.email,
                title = title,
                description = description,
                category = category,
                status = "Pending",
                timestamp = System.currentTimeMillis(),
                locationLat = lat,
                locationLng = lng
            )
            dao.insertReport(newReport)
            
            // Earn points for civic responsibility
            dao.addPointsToUser(20)
        }
    }

    // Update Report Status (Authority Action)
    fun updateReportStatus(reportId: String, status: String, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateReportStatus(reportId, status, notes)
        }
    }

    // Submit Citizen Legal Assistance Request (Citizen Action)
    fun submitCitizenRequest(subject: String, details: String, onResult: ((Boolean, String) -> Unit)? = null) {
        if (subject.trim().isBlank() || details.trim().isBlank()) {
            onResult?.invoke(false, "Subject and Inquiry details are required.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSubmittingInquiry.value = true
            _inquiryError.value = null
            try {
                Log.d("NyayaViewModel", "INQUIRY_SUBMIT_START: Submitting inquiry '${subject.take(20)}...'")
                val reqId = "INQ-" + System.currentTimeMillis().toString().takeLast(6)
                val profile = _userProfile.value
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val citizenId = profile.id.ifBlank { currentAuthUser?.uid ?: "CITIZEN-${System.currentTimeMillis().toString().takeLast(4)}" }
                val citizenEmail = profile.email.ifBlank { currentAuthUser?.email ?: "citizen@nyaya.ai" }
                val citizenName = profile.name.ifBlank { currentAuthUser?.displayName ?: "Citizen" }

                val newRequest = CitizenRequest(
                    id = reqId,
                    citizenId = citizenId,
                    citizenName = citizenName,
                    citizenEmail = citizenEmail,
                    subject = subject.trim(),
                    details = details.trim(),
                    status = "Open",
                    reply = "",
                    timestamp = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis()
                )
                
                // 1. Immediately persist to Room for instant local availability
                dao.insertCitizenRequest(newRequest)

                // 2. Persist to Firestore with non-blocking timeout safeguard
                try {
                    kotlinx.coroutines.withTimeoutOrNull(6000L) {
                        FirebaseManager.createCitizenRequestInFirestore(newRequest)
                    }
                } catch (e: Exception) {
                    Log.w("NyayaViewModel", "INQUIRY_SUBMIT_NOTICE: Firestore async sync notice: ${e.message}")
                }
                
                // Points for participating
                dao.addPointsToUser(10)
                
                _isSubmittingInquiry.value = false
                Log.d("NyayaViewModel", "INQUIRY_SUBMIT_SUCCESS: Inquiry $reqId submitted and registered")
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "Legal inquiry submitted successfully.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NyayaViewModel", "INQUIRY_SUBMIT_ERROR: Failed to submit inquiry: ${e.message}", e)
                _isSubmittingInquiry.value = false
                _inquiryError.value = e.message
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.message ?: "Failed to submit inquiry")
                }
            }
        }
    }

    fun refreshInquiries() {
        viewModelScope.launch(Dispatchers.IO) {
            _isInquiriesLoading.value = true
            _inquiryError.value = null
            Log.d("NyayaViewModel", "INQUIRY_LOAD_START: Refreshing citizen inquiries")
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    FirebaseManager.fetchCitizenRequestsFromFirestore()
                }
                if (result != null && result.isSuccess) {
                    val list = result.getOrNull() ?: emptyList()
                    Log.d("NyayaViewModel", "INQUIRY_LOAD_SUCCESS: Retrieved ${list.size} inquiries from cloud")
                    list.forEach { dao.insertCitizenRequest(it) }
                    _inquiryError.value = null
                } else {
                    Log.w("NyayaViewModel", "INQUIRY_LOAD_NOTICE: Inquiries fetch completed or timed out")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NyayaViewModel", "INQUIRY_LOAD_ERROR: Refresh inquiries error: ${e.message}", e)
                _inquiryError.value = e.message
            } finally {
                _isInquiriesLoading.value = false
            }
        }
    }

    // Answer Citizen Request (Authority / Admin Action)
    fun answerCitizenRequest(requestId: String, reply: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateCitizenRequestReply(requestId, reply, "Answered")
        }
    }

    // Delete Account (Admin Action)
    fun deleteAccount(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            dao.deleteUserAccount(email)
            if (account != null && account.uid.isNotBlank()) {
                FirebaseManager.deleteUserAccount(account.uid)
            }
            FirebaseManager.deleteUserAccount(email)
        }
    }

    // Authority Account Registration
    fun requestAuthorityAccount(
        fullName: String,
        employeeId: String,
        officialEmail: String,
        department: String,
        designation: String,
        jurisdiction: String,
        contactNumber: String,
        password: String,
        confirmPassword: String,
        onResult: (Boolean, String, UserAccount?) -> Unit
    ) {
        viewModelScope.launch {
            if (!isNetworkAvailable()) {
                onResult(false, "Network error. Please check your internet connection.", null)
                return@launch
            }

            val p1 = password.trim()
            val p2 = confirmPassword.trim()
            val email = officialEmail.trim().lowercase()
            val empId = employeeId.trim().uppercase()

            if (fullName.isBlank() || empId.isBlank() || email.isBlank() ||
                department.isBlank() || designation.isBlank() || jurisdiction.isBlank() ||
                contactNumber.isBlank() || p1.isBlank() || p2.isBlank()
            ) {
                onResult(false, "Please fill in all required official registration fields.", null)
                return@launch
            }

            if (p1 != p2) {
                onResult(false, "Passwords do not match.", null)
                return@launch
            }

            if (p1.length < 8) {
                onResult(false, "Password must be at least 8 characters long.", null)
                return@launch
            }

            val personalDomains = listOf("@gmail.com", "@yahoo.com", "@hotmail.com", "@outlook.com", "@aol.com", "@icloud.com")
            if (personalDomains.any { email.endsWith(it, ignoreCase = true) }) {
                onResult(false, "Personal email providers (e.g., Gmail, Yahoo) are not permitted for official authority accounts. Please use your official government email address (e.g., officer@department.gov.in).", null)
                return@launch
            }

            val existingEmail = dao.getUserAccount(email)
            val existingId = dao.getUserAccountByIdentity(empId)
            if (existingEmail != null || existingId != null) {
                onResult(false, "An authority account with this Employee ID or Official Email already exists.", null)
                return@launch
            }

            val newAuthority = UserAccount(
                email = email,
                name = fullName.trim(),
                passwordHash = p1,
                role = "Authority",
                isDisabled = false,
                isApproved = false,
                status = "PENDING_APPROVAL",
                approvalStatus = "PENDING_APPROVAL",
                department = department,
                designation = designation,
                district = jurisdiction,
                contact = contactNumber,
                phone = contactNumber,
                employeeId = empId,
                createdAt = System.currentTimeMillis(),
                lastLogin = System.currentTimeMillis()
            )

            dao.insertUserAccount(newAuthority)
            FirebaseManager.syncUserAccount(newAuthority)

            recordActivityLog(
                actorRole = "Authority",
                actorName = fullName.trim(),
                eventType = "AUTHORITY_ACCESS_REQUESTED",
                message = "New authority access requested by ${fullName.trim()} ($empId) for $department",
                relatedId = empId
            )

            onResult(true, "Authority access request submitted. Your account is waiting for administrator verification.", newAuthority)
        }
    }

    // Admin Authority Management Actions
    fun approveAuthorityAccount(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanEmail = email.trim().lowercase()
            var account = dao.getUserAccount(cleanEmail)
            if (account == null) {
                account = _allUserAccounts.value.find { it.email.equals(cleanEmail, ignoreCase = true) }
            }
            if (account != null) {
                val now = System.currentTimeMillis()
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date(now))
                val timeStr = timeFormat.format(java.util.Date(now))

                val updated = account.copy(
                    status = "Active",
                    approvalStatus = "APPROVED",
                    verificationStatus = "APPROVED",
                    isApproved = true,
                    isDisabled = false,
                    approvedAt = now
                )
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)

                val approveNotif = Notification(
                    id = "not_appr_" + UUID.randomUUID().toString().take(8),
                    userEmail = cleanEmail,
                    title = "Authority account activated by Admin",
                    message = "Authority account activated by Admin\nDate: $dateStr\nTime: $timeStr\nStatus: Active / Approved\nYou can now log in and access the Authority Dashboard.",
                    timestamp = now,
                    isRead = false,
                    isAuthority = true,
                    isHighPriority = true
                )
                dao.insertNotification(approveNotif)
                FirebaseManager.syncNotification(approveNotif)

                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "AUTHORITY_ACCOUNT_APPROVED",
                    message = "Activated authority account for ${account.name} (${account.employeeId.ifEmpty { cleanEmail }})",
                    relatedId = account.employeeId.ifEmpty { cleanEmail }
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "Authority account for ${account.name} activated successfully.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Account not found.")
                }
            }
        }
    }

    fun activateAuthorityAccount(email: String, onResult: (Boolean, String) -> Unit) {
        approveAuthorityAccount(email, onResult)
    }

    fun rejectAuthorityAccount(email: String, reason: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanEmail = email.trim().lowercase()
            var account = dao.getUserAccount(cleanEmail)
            if (account == null) {
                account = _allUserAccounts.value.find { it.email.equals(cleanEmail, ignoreCase = true) }
            }
            if (account != null) {
                val now = System.currentTimeMillis()
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date(now))
                val timeStr = timeFormat.format(java.util.Date(now))

                val updated = account.copy(
                    status = "Rejected",
                    approvalStatus = "REJECTED",
                    verificationStatus = "REJECTED",
                    rejectionReason = reason.ifBlank { "Invalid credentials or unsuccessful verification" },
                    isApproved = false
                )
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)

                val rejectNotif = Notification(
                    id = "not_rej_" + UUID.randomUUID().toString().take(8),
                    userEmail = cleanEmail,
                    title = "Authority login request cancelled by Admin",
                    message = "Authority login request cancelled by Admin\nDate: $dateStr\nTime: $timeStr\nStatus: Cancelled\nReason: Invalid credentials or unsuccessful verification.",
                    timestamp = now,
                    isRead = false,
                    isAuthority = true,
                    isHighPriority = true
                )
                dao.insertNotification(rejectNotif)
                FirebaseManager.syncNotification(rejectNotif)

                if (_currentUserAccount.value?.email.equals(cleanEmail, ignoreCase = true)) {
                    _currentUserAccount.value = null
                    _isLoggedIn.value = false
                }

                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "AUTHORITY_ACCOUNT_REJECTED",
                    message = "Cancelled/Rejected authority access for ${account.name} (${account.employeeId.ifEmpty { cleanEmail }})",
                    relatedId = account.employeeId.ifEmpty { cleanEmail }
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "Authority account for ${account.name} has been rejected/cancelled.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Account not found.")
                }
            }
        }
    }

    fun disableAuthorityAccount(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanEmail = email.trim().lowercase()
            var account = dao.getUserAccount(cleanEmail)
            if (account == null) {
                account = _allUserAccounts.value.find { it.email.equals(cleanEmail, ignoreCase = true) }
            }
            if (account != null) {
                val now = System.currentTimeMillis()
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date(now))
                val timeStr = timeFormat.format(java.util.Date(now))

                val updated = account.copy(
                    status = "Disabled",
                    isDisabled = true
                )
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)

                val disableNotif = Notification(
                    id = "not_dis_" + UUID.randomUUID().toString().take(8),
                    userEmail = cleanEmail,
                    title = "Authority account disabled by Admin",
                    message = "Authority account disabled by Admin\nDate: $dateStr\nTime: $timeStr\nStatus: Disabled\nAccess to the Authority Dashboard is currently suspended.",
                    timestamp = now,
                    isRead = false,
                    isAuthority = true,
                    isHighPriority = true
                )
                dao.insertNotification(disableNotif)
                FirebaseManager.syncNotification(disableNotif)

                if (_currentUserAccount.value?.email.equals(cleanEmail, ignoreCase = true)) {
                    _currentUserAccount.value = null
                }

                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "AUTHORITY_ACCOUNT_DISABLED",
                    message = "Disabled authority account ${account.name} (${account.employeeId})",
                    relatedId = account.employeeId.ifEmpty { cleanEmail }
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "Authority account for ${account.name} disabled.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Account not found.")
                }
            }
        }
    }

    fun reEnableAuthorityAccount(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                val updated = account.copy(
                    status = "Active",
                    approvalStatus = "APPROVED",
                    isApproved = true,
                    isDisabled = false
                )
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)
                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "AUTHORITY_ACCOUNT_ENABLED",
                    message = "Re-enabled authority account ${account.name} (${account.employeeId})",
                    relatedId = account.employeeId.ifEmpty { email }
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "Authority account for ${account.name} re-enabled.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Account not found.")
                }
            }
        }
    }

    fun removeAuthorityAccount(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                dao.deleteUserAccount(email)
                if (account.uid.isNotBlank()) {
                    FirebaseManager.deleteUserAccount(account.uid)
                }
                FirebaseManager.deleteUserAccount(email)
                if (_currentUserAccount.value?.email.equals(email, ignoreCase = true)) {
                    _currentUserAccount.value = null
                }
                recordActivityLog(
                    actorRole = "Admin",
                    actorName = "System Administrator",
                    eventType = "AUTHORITY_ACCOUNT_REMOVED",
                    message = "Removed authority account ${account.name} (${account.employeeId})",
                    relatedId = account.employeeId.ifEmpty { email }
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "Authority account removed successfully.")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Account not found.")
                }
            }
        }
    }

    // Update User Account Status (Active, Disabled, Blocked, Inactive)
    fun updateUserStatus(email: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                val isDisabled = newStatus.equals("Disabled", ignoreCase = true) || newStatus.equals("Blocked", ignoreCase = true)
                val updated = account.copy(status = newStatus, isDisabled = isDisabled)
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)
            }
        }
    }

    // Toggle Account Approval (Admin Action)
    fun toggleAccountApproval(email: String, approve: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                val updated = account.copy(isApproved = approve)
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)
            }
        }
    }

    // Toggle Account Disabled (Admin Action)
    fun toggleAccountDisabled(email: String, disable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                val updated = account.copy(isDisabled = disable, status = if (disable) "disabled" else "active")
                dao.insertUserAccount(updated)
                FirebaseManager.syncUserAccount(updated)
            }
        }
    }

    private val _allLaws = MutableStateFlow<List<LawRecord>>(emptyList())
    val allLaws: StateFlow<List<LawRecord>> = _allLaws.asStateFlow()

    val activeLaws: StateFlow<List<LawRecord>> = _allLaws.map { list ->
        list.filter { it.status.equals("active", ignoreCase = true) || it.status.equals("published", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isAppInitializing = MutableStateFlow(true)
    val isAppInitializing: StateFlow<Boolean> = _isAppInitializing.asStateFlow()

    private val _isLawsLoading = MutableStateFlow(true)
    val isLawsLoading: StateFlow<Boolean> = _isLawsLoading.asStateFlow()

    private val _lawsError = MutableStateFlow<String?>(null)
    val lawsError: StateFlow<String?> = _lawsError.asStateFlow()

    private val _legalTopics = MutableStateFlow<List<LegalTopic>>(emptyList())
    val legalTopics: StateFlow<List<LegalTopic>> = _legalTopics.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Room Database and DAO
    private lateinit var db: NyayaDatabase
    lateinit var dao: NyayaDao

    private val _currentUserAccount = MutableStateFlow<UserAccount?>(null)
    val currentUserAccount: StateFlow<UserAccount?> = _currentUserAccount.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile>(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _forumPosts = MutableStateFlow<List<ForumPost>>(emptyList())
    val forumPosts: StateFlow<List<ForumPost>> = _forumPosts.asStateFlow()

    private val _feedbacks = MutableStateFlow<List<AiFeedback>>(emptyList())
    val feedbacks: StateFlow<List<AiFeedback>> = _feedbacks.asStateFlow()

    private val _appRatings = MutableStateFlow<List<com.example.db.AppRating>>(emptyList())
    val appRatings: StateFlow<List<com.example.db.AppRating>> = _appRatings.asStateFlow()

    private val _userFeedbacks = MutableStateFlow<List<com.example.db.UserFeedback>>(emptyList())
    val userFeedbacks: StateFlow<List<com.example.db.UserFeedback>> = _userFeedbacks.asStateFlow()

    // Multi-Language Support
    private val _currentLanguage = MutableStateFlow("English")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Citizen Complaints
    private val _allComplaints = MutableStateFlow<List<CitizenComplaint>>(emptyList())
    val allComplaints: StateFlow<List<CitizenComplaint>> = _allComplaints.asStateFlow()

    private val _myComplaints = MutableStateFlow<List<CitizenComplaint>>(emptyList())
    val myComplaints: StateFlow<List<CitizenComplaint>> = _myComplaints.asStateFlow()

    // Notifications
    private val _allNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val allNotifications: StateFlow<List<Notification>> = _allNotifications.asStateFlow()

    private val _myNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val myNotifications: StateFlow<List<Notification>> = _myNotifications.asStateFlow()

    // Emergency Contacts & Realtime Activity Log state
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _systemActivityLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val systemActivityLogs: StateFlow<List<ActivityLog>> = _systemActivityLogs.asStateFlow()

    private var activityLogListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    // Filter helpers
    private fun updateMyComplaints() {
        val profile = _userProfile.value
        val email = profile.email.trim().lowercase()
        val userId = profile.id.trim().lowercase()
        val role = profile.role
        val allList = _allComplaints.value

        _myComplaints.value = allList.filter { complaint ->
            val cEmail = complaint.reporterEmail.trim().lowercase()
            val cId = complaint.citizenId.trim().lowercase()

            when {
                email.isNotEmpty() && cEmail == email -> true
                email.isNotEmpty() && cId == email -> true
                userId.isNotEmpty() && cId == userId -> true
                userId.isNotEmpty() && cEmail == userId -> true
                cEmail.isEmpty() && cId.isEmpty() -> true
                role == "Citizen" && (email.isNotEmpty() && (cEmail == email || cId == email)) -> true
                else -> false
            }
        }.sortedByDescending { it.createdAt.coerceAtLeast(it.timestamp) }

        Log.d("NyayaComplaintTracker", "Complaint query: Total in DB=${allList.size}, Filtered for Citizen '$email' / '$userId' (Role=$role)=${_myComplaints.value.size}")
    }

    private fun updateMyNotifications() {
        val email = _userProfile.value.email
        val role = _userProfile.value.role
        _myNotifications.value = _allNotifications.value.filter {
            it.userEmail == email || it.userEmail == "all" || (role == "Authority" && it.isAuthority)
        }
    }

    // Initializer for Room Database inside loadLegalDatabase
    fun initDatabase(context: Context) {
        appContext = context.applicationContext
        if (::db.isInitialized) {
            _isAppInitializing.value = false
            return
        }
        try {
            try {
                db = NyayaDatabase.getDatabase(context)
                db.openHelper.writableDatabase
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Room open/migration failed, resetting corrupt DB: ${e.message}", e)
                db = NyayaDatabase.resetDatabase(context)
                db.openHelper.writableDatabase
            }
            dao = db.nyayaDao()
            
            sharedPrefs = context.getSharedPreferences("nyaya_prefs", Context.MODE_PRIVATE)
            val savedLogin = sharedPrefs?.getBoolean("is_logged_in", false) ?: false
            _isLoggedIn.value = savedLogin

            // Load language
            val savedLang = sharedPrefs?.getString("selected_language", "English") ?: "English"
            _currentLanguage.value = savedLang

            // Reactive profile updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getUserProfile().collect { profile ->
                        if (profile != null) {
                            _userProfile.value = profile
                            updateMyComplaints()
                            updateMyNotifications()
                            
                            val currentBadges = profile.badges.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toMutableList()
                            var badgeChanged = false

                            if (profile.points >= 50 && !currentBadges.contains("Community Helper")) {
                                currentBadges.add("Community Helper")
                                badgeChanged = true
                            }
                            if (profile.points >= 100 && !currentBadges.contains("Top Contributor")) {
                                currentBadges.add("Top Contributor")
                                badgeChanged = true
                            }
                            if (profile.points >= 200 && !currentBadges.contains("Legal Expert")) {
                                currentBadges.add("Legal Expert")
                                badgeChanged = true
                            }

                            if (badgeChanged) {
                                dao.updateBadges(currentBadges.joinToString(","))
                            }
                        } else {
                            dao.insertUserProfile(UserProfile())
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Profile collection error: ${e.message}")
                }
            }

            // Reactive citizen complaints
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllComplaints().collect { list ->
                        _allComplaints.value = list
                        updateMyComplaints()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Complaints collection error: ${e.message}")
                }
            }

            // Firebase Firestore Realtime Complaints Listener
            try {
                FirebaseManager.listenToComplaints { cloudComplaints ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            Log.d("NyayaComplaintTracker", "Complaint fetched/listener event: received ${cloudComplaints.size} complaints from cloud")
                            cloudComplaints.forEach { complaint ->
                                val local = dao.getComplaintById(complaint.id)
                                val mergedComplaint = if (local != null) {
                                    local.copy(
                                        title = complaint.title.ifBlank { local.title },
                                        description = complaint.description.ifBlank { local.description },
                                        category = complaint.category.ifBlank { local.category },
                                        state = complaint.state.ifBlank { local.state },
                                        district = complaint.district.ifBlank { local.district },
                                        address = complaint.address.ifBlank { local.address },
                                        imageUri = complaint.imageUri ?: local.imageUri,
                                        timestamp = if (complaint.timestamp > 0) complaint.timestamp else local.timestamp,
                                        isAnonymous = complaint.isAnonymous,
                                        reporterName = complaint.reporterName.ifBlank { local.reporterName },
                                        reporterEmail = complaint.reporterEmail.ifBlank { local.reporterEmail },
                                        citizenId = complaint.citizenId.ifBlank { local.citizenId.ifEmpty { local.reporterEmail } },
                                        citizenPhone = complaint.citizenPhone.ifBlank { local.citizenPhone },
                                        status = complaint.status.ifBlank { local.status },
                                        aiPredictedDepartment = complaint.aiPredictedDepartment.ifBlank { local.aiPredictedDepartment },
                                        priority = complaint.priority.ifBlank { local.priority },
                                        assignedOfficer = complaint.assignedOfficer.ifBlank { local.assignedOfficer },
                                        authorityRemarks = complaint.authorityRemarks.ifBlank { local.authorityRemarks },
                                        createdAt = if (complaint.createdAt > 0) complaint.createdAt else local.createdAt,
                                        updatedAt = if (complaint.updatedAt > 0) complaint.updatedAt else local.updatedAt,
                                        resolvedAt = if (complaint.resolvedAt > 0) complaint.resolvedAt else local.resolvedAt,
                                        lastModifiedBy = complaint.lastModifiedBy.ifBlank { local.lastModifiedBy },
                                        photoFileName = complaint.photoFileName.ifBlank { local.photoFileName },
                                        timeline = if (complaint.timeline.isNotBlank() && complaint.timeline != "[]") complaint.timeline else local.timeline,
                                        notificationHistory = if (complaint.notificationHistory.isNotBlank() && complaint.notificationHistory != "[]") complaint.notificationHistory else local.notificationHistory
                                    )
                                } else {
                                    complaint
                                }
                                dao.insertComplaint(mergedComplaint)
                            }
                        } catch (e: Exception) {
                            Log.e("NyayaViewModel", "Error merging cloud complaints: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error listening to complaints: ${e.message}")
            }

            // Firebase Firestore Realtime Citizen Requests Listener
            try {
                Log.d("NyayaViewModel", "INQUIRY_LOAD_START: Initializing Firestore citizen_requests listener")
                FirebaseManager.listenToCitizenRequests(
                    onUpdate = { cloudRequests ->
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                Log.d("NyayaViewModel", "INQUIRY_LOAD_SUCCESS: Received ${cloudRequests.size} inquiries from Firestore")
                                cloudRequests.forEach { req ->
                                    if (req.reply.isNotBlank() && req.status == "Answered") {
                                        Log.d("NyayaViewModel", "INQUIRY_RESPONSE_RECEIVED: Inquiry ${req.id} has official response from ${req.officerName}")
                                    }
                                    dao.insertCitizenRequest(req)
                                }
                                _isInquiriesLoading.value = false
                                _inquiryError.value = null
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("NyayaViewModel", "INQUIRY_LOAD_ERROR: Error merging citizen requests: ${e.message}", e)
                                _isInquiriesLoading.value = false
                            }
                        }
                    },
                    onError = { err ->
                        viewModelScope.launch(Dispatchers.IO) {
                            Log.w("NyayaViewModel", "INQUIRY_LOAD_ERROR: Firestore citizen_requests listener error: ${err.message}")
                            _isInquiriesLoading.value = false
                        }
                    }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NyayaViewModel", "INQUIRY_LOAD_ERROR: Error listening to citizen requests: ${e.message}", e)
                _isInquiriesLoading.value = false
            }

            // Safeguard timeout (5s) for inquiries initial loading
            viewModelScope.launch {
                kotlinx.coroutines.delay(5000L)
                if (_isInquiriesLoading.value) {
                    Log.d("NyayaViewModel", "INQUIRY_LOAD: Initial inquiries loading safeguard timeout (5s). Releasing loading state.")
                    _isInquiriesLoading.value = false
                }
            }

            // Firebase Firestore Realtime Notifications Listener
            try {
                FirebaseManager.listenToNotifications { cloudNotifications ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            cloudNotifications.forEach { notif ->
                                dao.insertNotification(notif)
                            }
                        } catch (e: Exception) {
                            Log.e("NyayaViewModel", "Error merging notifications: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error listening to notifications: ${e.message}")
            }

            // Firebase Firestore Realtime Forum Posts Listener
            try {
                FirebaseManager.listenToForumPosts { cloudPosts ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            cloudPosts.forEach { post ->
                                dao.insertPost(post)
                            }
                        } catch (e: Exception) {
                            Log.e("NyayaViewModel", "Error merging forum posts: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error listening to forum posts: ${e.message}")
            }

            // Firebase Firestore Realtime Users Listener ("users" collection)
            try {
                FirebaseManager.listenToUsers { cloudUsers ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            if (cloudUsers.isNotEmpty()) {
                                dao.insertUserAccounts(cloudUsers)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("NyayaViewModel", "Notice merging cloud users: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("NyayaViewModel", "Notice setting up users listener: ${e.message}")
            }

            // Reactive notifications
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllNotifications().collect { list ->
                        _allNotifications.value = list
                        updateMyNotifications()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in notifications flow: ${e.message}")
                }
            }

            // Reactive forum post updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllPosts().collect { posts ->
                        if (posts.isEmpty()) {
                            seedInitialPosts()
                        } else {
                            _forumPosts.value = posts
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in forum posts flow: ${e.message}")
                }
            }

            // Reactive AI Feedback updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllFeedback().collect { list ->
                        _feedbacks.value = list
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in feedback flow: ${e.message}")
                }
            }

            // Reactive App Ratings updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllAppRatings().collect { list ->
                        _appRatings.value = list
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in app ratings flow: ${e.message}")
                }
            }

            // Reactive User Feedbacks updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllUserFeedbacks().collect { list ->
                        _userFeedbacks.value = list
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in user feedbacks flow: ${e.message}")
                }
            }

            // Attach Firestore Real-time Snapshot Listeners
            try {
                FirebaseManager.listenToRatings { remoteRatings ->
                    viewModelScope.launch(Dispatchers.IO) {
                        remoteRatings.forEach { r -> dao.insertAppRating(r) }
                    }
                }
                FirebaseManager.listenToFeedback { remoteFeedbacks ->
                    viewModelScope.launch(Dispatchers.IO) {
                        remoteFeedbacks.forEach { fb -> dao.insertUserFeedback(fb) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NyayaViewModel", "Error setting up ratings/feedback listeners: ${e.message}")
            }

            // Reactive user accounts
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllUserAccounts().collect { list ->
                        _allUserAccounts.value = list
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in user accounts flow: ${e.message}")
                }
            }

            // Reactive incident reports
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllReports().collect { list ->
                        _allReports.value = list
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in incident reports flow: ${e.message}")
                }
            }

            // Reactive citizen requests
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllCitizenRequests().collect { list ->
                        _allCitizenRequests.value = list
                        Log.d("NyayaViewModel", "INQUIRY_LOAD_SUCCESS: Room emitted ${list.size} citizen inquiries")
                        if (list.isNotEmpty()) {
                            _isInquiriesLoading.value = false
                            _inquiryError.value = null
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "INQUIRY_LOAD_ERROR: Error in citizen requests flow: ${e.message}")
                    _isInquiriesLoading.value = false
                }
            }

            // Reactive Emergency Contacts
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Remove any legacy Primary Guardian contact
                    dao.deleteEmergencyContact("em_1")
                    dao.getAllEmergencyContacts().collect { contacts ->
                        val filtered = contacts.filter {
                            it.id != "em_1" &&
                            !it.name.contains("Guardian", ignoreCase = true) &&
                            !it.relationship.contains("Guardian", ignoreCase = true)
                        }
                        if (filtered.isEmpty() && contacts.isEmpty()) {
                            seedDefaultEmergencyContacts()
                        } else {
                            _emergencyContacts.value = filtered
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in emergency contacts flow: ${e.message}")
                }
            }

            // Reactive local Laws database from Room
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllLaws().collect { laws ->
                        _allLaws.value = laws
                        _legalTopics.value = laws.map { it.toLegalTopic() }
                        Log.d("NyayaViewModel", "LEGAL_DATA_COUNT: Room emitted ${laws.size} law records")
                        if (laws.isNotEmpty()) {
                            _isLawsLoading.value = false
                            _lawsError.value = null
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "LEGAL_DATA_ERROR: Error in laws flow: ${e.message}")
                    _isLawsLoading.value = false
                }
            }

            // Immediately seed baseline legal knowledge base into Room if local database is empty
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val existingFirstLaw = dao.getLawById("LAW-1")
                    if (existingFirstLaw == null) {
                        Log.d("NyayaViewModel", "LEGAL_DATA: Local legal database is empty, seeding base legal catalog...")
                        seedLawsFromAssetsIfEmpty(context)
                    } else {
                        _isLawsLoading.value = false
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("NyayaViewModel", "LEGAL_DATA_ERROR: Error checking local laws: ${e.message}")
                    seedLawsFromAssetsIfEmpty(context)
                }
            }

            // Firebase Firestore Realtime Laws Listener ("laws" collection)
            try {
                FirebaseManager.listenToLaws(
                    onUpdate = { cloudLaws ->
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                Log.d("NyayaViewModel", "LEGAL_DATA_COUNT: Firestore listener pushed ${cloudLaws.size} records")
                                if (cloudLaws.isNotEmpty()) {
                                    dao.insertLaws(cloudLaws)
                                    _allLaws.value = cloudLaws
                                    _legalTopics.value = cloudLaws.map { it.toLegalTopic() }
                                }
                                _isLawsLoading.value = false
                                _lawsError.value = null
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("NyayaViewModel", "LEGAL_DATA_ERROR: Error handling cloud laws: ${e.message}", e)
                                _isLawsLoading.value = false
                            }
                        }
                    },
                    onError = { err ->
                        viewModelScope.launch(Dispatchers.IO) {
                            Log.w("NyayaViewModel", "LEGAL_DATA_ERROR: Firestore realtime laws notice (${err.message})")
                            if (_allLaws.value.isEmpty()) {
                                seedLawsFromAssetsIfEmpty(context)
                            }
                            _isLawsLoading.value = false
                        }
                    }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("NyayaViewModel", "LEGAL_DATA_ERROR: Notice listening to laws: ${e.message}")
                _isLawsLoading.value = false
            }

            // Safeguard timeout (4s) to ensure the loading indicator never stays active indefinitely
            viewModelScope.launch {
                kotlinx.coroutines.delay(4000L)
                if (_isLawsLoading.value) {
                    Log.d("NyayaViewModel", "LEGAL_DATA: Loading laws timed out (4s). Ensuring UI stops spinner.")
                    if (_allLaws.value.isEmpty()) {
                        seedLawsFromAssetsIfEmpty(context)
                    }
                    _isLawsLoading.value = false
                }
            }

            // Reactive Audit Logs
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllAuditLogs().collect { logs ->
                        _allAuditLogs.value = logs
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in audit logs flow: ${e.message}")
                }
            }

            // Reactive Activity Logs
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    dao.getAllActivityLogs().collect { logs ->
                        _systemActivityLogs.value = logs
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NyayaViewModel", "Error in activity logs flow: ${e.message}")
                }
            }
            startListeningToActivityLogs()
        } catch (e: Exception) {
            Log.e("NyayaViewModel", "Critical error initializing NyayaDatabase: ${e.message}", e)
        } finally {
            _isAppInitializing.value = false
        }
    }

    private suspend fun seedDefaultEmergencyContacts() {
        val defaultContacts = listOf(
            EmergencyContact(
                id = "em_2",
                name = "Police Emergency Control",
                phone = "112",
                relationship = "Law Enforcement",
                isPrimary = true
            ),
            EmergencyContact(
                id = "em_3",
                name = "Women Helpline Safety",
                phone = "1091",
                relationship = "Safety Helpline",
                isPrimary = false
            ),
            EmergencyContact(
                id = "em_4",
                name = "National Emergency Ambulance",
                phone = "108",
                relationship = "Medical Services",
                isPrimary = false
            )
        )
        defaultContacts.forEach { dao.insertEmergencyContact(it) }
    }

    fun addEmergencyContact(name: String, phone: String, relationship: String, isPrimary: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = EmergencyContact(
                name = name,
                phone = phone,
                relationship = relationship,
                isPrimary = isPrimary
            )
            dao.insertEmergencyContact(contact)
        }
    }

    fun deleteEmergencyContact(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteEmergencyContact(id)
        }
    }

    fun startListeningToActivityLogs(): com.google.firebase.firestore.ListenerRegistration? {
        activityLogListenerRegistration?.remove()
        val reg = FirebaseManager.listenToActivityLogs { remoteLogs ->
            if (remoteLogs.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertActivityLogs(remoteLogs)
                }
            }
        }
        activityLogListenerRegistration = reg
        return reg
    }

    fun recordActivityLog(
        actorRole: String,
        actorName: String,
        eventType: String,
        message: String,
        relatedId: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val log = ActivityLog(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    actorRole = actorRole.ifBlank { "Citizen" },
                    actorName = actorName,
                    eventType = eventType,
                    message = message,
                    relatedId = relatedId
                )
                dao.insertActivityLog(log)
                FirebaseManager.logActivity(log)
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error recording activity log: ${e.message}")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val context = appContext ?: return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        @Suppress("DEPRECATION")
        val activeNetwork = cm?.activeNetworkInfo
        @Suppress("DEPRECATION")
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }

    private suspend fun seedDefaultAccounts() {
        val rootAdmin = UserAccount(
            email = "sakthivel.s8317@gmail.com",
            name = "System Administrator",
            passwordHash = "",
            role = "Admin",
            employeeId = "SYS-ADM-001",
            department = "District Administration",
            designation = "Administrative Officer",
            isApproved = true,
            status = "Active"
        )
        dao.insertUserAccount(rootAdmin)
        FirebaseManager.syncUserAccount(rootAdmin)
    }

    private suspend fun seedDefaultReports() {
        // No-op: Real application relies on authentic incident reports
    }

    private suspend fun seedDefaultRequests() {
        // No-op: Real application relies on authentic citizen requests
    }

    private suspend fun seedInitialPosts() {
        val initialPosts = listOf(
            ForumPost(
                id = "seed_1",
                title = "What is the procedure to file an online FIR in Maharashtra?",
                content = "I am trying to file an FIR regarding a lost passport but the physical police station is asking me to submit it online first. Does anyone have the exact portal link and list of mandatory documents?",
                postType = "Question",
                authorName = "Aarav Mehta",
                authorRole = "Citizen",
                upvotes = 12,
                timestamp = System.currentTimeMillis() - 86400000 * 2
            ),
            ForumPost(
                id = "seed_2",
                title = "Compendium of Central Labour Laws - PDF Guide",
                content = "Hello community, I have compiled a neat summary of major Central Labour Laws, including minimum wages, working hours limits, and maternity benefit acts. Hope this helps anyone preparing for legal compliance audits!",
                postType = "Resource",
                authorName = "Adv. Priya Sharma",
                authorRole = "Legal Expert",
                upvotes = 34,
                timestamp = System.currentTimeMillis() - 86400000 * 1
            ),
            ForumPost(
                id = "seed_3",
                title = "Discussion on recent changes in the Consumer Protection Act, 2019",
                content = "The shift towards e-commerce liability is a game-changer. Sellers on Amazon/Flipkart can no longer hide behind third-party terms of service. What are your thoughts on product liability provisions?",
                postType = "Discussion",
                authorName = "Prof. S. Verma",
                authorRole = "Academic Scholar",
                upvotes = 19,
                timestamp = System.currentTimeMillis() - 3600000 * 4
            )
        )
        for (post in initialPosts) {
            dao.insertPost(post)
        }
        
        // Seed some initial comments for seed_1 to show the forum active
        dao.insertComment(ForumComment(
            id = "c_seed_1",
            postId = "seed_1",
            content = "You can use the Maharashtra Police Citizen Portal. Go to the 'E-Complaint' section. Make sure to have a soft copy of the lost item application or notary affidavit.",
            authorName = "Adv. Priya Sharma",
            authorRole = "Legal Expert",
            timestamp = System.currentTimeMillis() - 86400000 * 1
        ))
    }

    fun createPost(title: String, content: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val author = _userProfile.value.name
            val postId = UUID.randomUUID().toString()
            val newPost = ForumPost(
                id = postId,
                title = title,
                content = content,
                postType = type,
                authorName = author,
                authorRole = "Citizen Advocate",
                timestamp = System.currentTimeMillis()
            )
            dao.insertPost(newPost)
            FirebaseManager.syncForumPost(newPost)
            
            // Add points based on type (+15 for shared resources, +5 for questions/discussions)
            val pointsEarned = if (type == "Resource") 15 else 5
            dao.addPointsToUser(pointsEarned)
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val author = _userProfile.value.name
            val commentId = UUID.randomUUID().toString()
            val newComment = ForumComment(
                id = commentId,
                postId = postId,
                content = content,
                authorName = author,
                authorRole = "Citizen Advocate",
                timestamp = System.currentTimeMillis()
            )
            dao.insertComment(newComment)
            FirebaseManager.syncForumComment(newComment)
            
            // Earn points: +10 for replying/commenting
            dao.addPointsToUser(10)
        }
    }

    fun toggleLikePost(post: ForumPost) {
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlyLiked = post.isLikedByMe
            val change = if (isCurrentlyLiked) -1 else 1
            val updatedPost = post.copy(upvotes = (post.upvotes + change).coerceAtLeast(0), isLikedByMe = !isCurrentlyLiked)
            dao.updatePostLike(post.id, change, !isCurrentlyLiked)
            FirebaseManager.syncForumPost(updatedPost)
            
            // Liker gets +2 points for upvoting/engaging
            val pointsChangeForLiker = if (isCurrentlyLiked) -2 else 2
            dao.addPointsToUser(pointsChangeForLiker)
        }
    }

    fun submitFeedback(query: String, response: String, isHelpful: Boolean, stars: Int, text: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val feedbackId = UUID.randomUUID().toString()
            val newFeedback = AiFeedback(
                id = feedbackId,
                query = query,
                response = response,
                isHelpful = isHelpful,
                starRating = stars,
                textFeedback = text,
                timestamp = System.currentTimeMillis()
            )
            dao.insertFeedback(newFeedback)
            
            // Give user +5 points for providing feedback to encourage engagement!
            dao.addPointsToUser(5)
        }
    }

    fun getComments(postId: String): Flow<List<ForumComment>> {
        return dao.getCommentsForPost(postId)
    }

    fun updateProfileName(newName: String, newEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val formatted = formatDisplayName(newName, newEmail)
            val updatedProfile = _userProfile.value.copy(name = formatted, email = newEmail)
            dao.insertUserProfile(updatedProfile)
            _userProfile.value = updatedProfile

            val existingAccount = dao.getUserAccount(newEmail.ifEmpty { _userProfile.value.email })
            if (existingAccount != null) {
                val updatedAccount = existingAccount.copy(name = formatted)
                dao.insertUserAccount(updatedAccount)
                FirebaseManager.syncUserAccount(updatedAccount)
                _currentUserAccount.value = updatedAccount
            } else if (newEmail.isNotEmpty()) {
                val newAcc = UserAccount(
                    email = newEmail,
                    passwordHash = "",
                    name = formatted,
                    role = _userProfile.value.role,
                    status = "Active"
                )
                dao.insertUserAccount(newAcc)
                FirebaseManager.syncUserAccount(newAcc)
                _currentUserAccount.value = newAcc
            }
        }
    }

    // Load legal database from Firestore "laws" collection
    fun loadLegalDatabase(context: Context) {
        initDatabase(context)
        if (_chatHistory.value.isEmpty()) {
            _chatHistory.value = listOf(
                ChatMessage(
                    sender = "ai",
                    text = "Namaste! I am NyayaAI, your automated legal assistant. I can help you understand laws, employee rights, consumer protection, women's safety, cybercrime provisions, and traffic regulations in India.\n\nType your query below, or click on any of the suggested prompts to get started!",
                    isWarningNotLocal = false
                )
            )
        }
    }

    // Seed initial laws from assets into local Room database for instantaneous offline access
    fun seedLawsFromAssetsIfEmpty(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("NyayaViewModel", "LEGAL_DATA: Reading legal_knowledge_base.json from assets...")
                val jsonString = context.assets.open("legal_knowledge_base.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val initialRecords = mutableListOf<LawRecord>()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getInt("id")
                    val keywordsArr = obj.optJSONArray("keywords")
                    val keywordsList = mutableListOf<String>()
                    if (keywordsArr != null) {
                        for (j in 0 until keywordsArr.length()) {
                            keywordsList.add(keywordsArr.getString(j))
                        }
                    }
                    val record = LawRecord(
                        lawId = "LAW-$id",
                        category = obj.getString("category"),
                        title = obj.getString("title"),
                        description = obj.getString("summary"),
                        content = obj.getString("summary"),
                        reference = obj.optString("official_source", "Gazette of India"),
                        status = "active",
                        createdAt = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * (100 - i)),
                        updatedAt = System.currentTimeMillis(),
                        createdBy = "Central Authority",
                        lastUpdatedBy = "Central Authority",
                        officialAuthority = obj.optString("official_authority", "Ministry of Law and Justice"),
                        officialSourceUrl = obj.optString("official_source_url", ""),
                        keywords = keywordsList.joinToString(", ")
                    )
                    initialRecords.add(record)
                }
                
                dao.insertLaws(initialRecords)
                _allLaws.value = initialRecords
                _legalTopics.value = initialRecords.map { it.toLegalTopic() }
                Log.d("NyayaViewModel", "LEGAL_DATA_COUNT: Seeded ${initialRecords.size} baseline law records to local database")
                _isLawsLoading.value = false
                _lawsError.value = null
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "LEGAL_DATA_ERROR: Error seeding initial laws: ${e.message}", e)
                _isLawsLoading.value = false
            }
        }
    }

    // Refresh and sync laws on-demand from Firestore with timeout & error handling
    fun refreshLaws(context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLawsLoading.value = true
            _lawsError.value = null
            try {
                kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    val result = FirebaseManager.fetchLawsFromFirestore()
                    result.onSuccess { cloudLaws ->
                        Log.d("NyayaViewModel", "LEGAL_DATA_COUNT: Manual refresh retrieved ${cloudLaws.size} laws from Firestore")
                        if (cloudLaws.isNotEmpty()) {
                            dao.insertLaws(cloudLaws)
                            _allLaws.value = cloudLaws
                            _legalTopics.value = cloudLaws.map { it.toLegalTopic() }
                        } else if (_allLaws.value.isEmpty() && context != null) {
                            seedLawsFromAssetsIfEmpty(context)
                        }
                        _isLawsLoading.value = false
                        _lawsError.value = null
                    }.onFailure { err ->
                        Log.e("NyayaViewModel", "LEGAL_DATA_ERROR: Failed to refresh laws: ${err.message}")
                        if (_allLaws.value.isEmpty()) {
                            if (context != null) {
                                seedLawsFromAssetsIfEmpty(context)
                            } else {
                                _lawsError.value = "Unable to connect to Central Legal Collection. Please check your connection."
                            }
                        }
                        _isLawsLoading.value = false
                    }
                } ?: run {
                    Log.w("NyayaViewModel", "LEGAL_DATA_ERROR: Refreshing laws timed out (5s)")
                    if (_allLaws.value.isEmpty()) {
                        if (context != null) {
                            seedLawsFromAssetsIfEmpty(context)
                        } else {
                            _lawsError.value = "Connection timed out. Please check your network connection and retry."
                        }
                    }
                    _isLawsLoading.value = false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NyayaViewModel", "LEGAL_DATA_ERROR: Exception in refreshLaws: ${e.message}")
                if (_allLaws.value.isEmpty()) {
                    if (context != null) {
                        seedLawsFromAssetsIfEmpty(context)
                    } else {
                        _lawsError.value = "Error: ${e.localizedMessage ?: "Unable to fetch laws"}"
                    }
                }
                _isLawsLoading.value = false
            }
        }
    }

    // Set search query for laws browser
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _nearbyServices = MutableStateFlow<List<EmergencyService>>(emptyList())
    val nearbyServices: StateFlow<List<EmergencyService>> = _nearbyServices.asStateFlow()

    // Update GPS coordinates
    fun updateLocation(lat: Double, lng: Double) {
        _userLocation.value = Pair(lat, lng)
    }

    // Parse and update nearby emergency services list
    fun updateNearbyServices(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = org.json.JSONArray(jsonString)
                val servicesList = mutableListOf<EmergencyService>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    servicesList.add(
                        EmergencyService(
                            id = obj.optInt("id"),
                            name = obj.optString("name"),
                            category = obj.optString("category"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            address = obj.optString("address"),
                            phone = obj.optString("phone"),
                            icon = obj.optString("icon", "📍"),
                            rating = obj.optDouble("rating", 4.2),
                            distance = obj.optDouble("distance", 0.0),
                            details = obj.optString("details", "")
                        )
                    )
                }
                // Sort by distance ascending so closest services appear first!
                _nearbyServices.value = servicesList.sortedBy { it.distance }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Send a message to the AI Assistant using RAG over the local JSON database
    fun sendChatMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return

        // Prevent duplicate calls if a request is already in progress
        if (_isLoading.value) {
            Log.w("NyayaViewModel", "sendChatMessage ignored: A request is already in progress.")
            return
        }

        // Immediately set loading state to true
        _isLoading.value = true
        
        // Add User Message
        val userMsg = ChatMessage(sender = "user", text = trimmedText)
        _chatHistory.value = _chatHistory.value + userMsg
        
        viewModelScope.launch(Dispatchers.IO) {
            val matchedTopics = findRelevantTopics(trimmedText)
            val bestScore = matchedTopics.firstOrNull()?.score ?: 0
            
            val confidenceText = when {
                bestScore >= 18 -> "96% — High Confidence (Verified using NyayaAI Knowledge Base)"
                bestScore >= 10 -> "90% — High Confidence (Verified using NyayaAI Knowledge Base)"
                bestScore >= 4  -> "82% — Medium Confidence (Matched with NyayaAI Knowledge Base)"
                else -> "General Guidance"
            }

            // Build structured context for Gemini
            val promptBuilder = StringBuilder()
            if (matchedTopics.isNotEmpty()) {
                promptBuilder.append("AUTHORIZED LOCAL LEGAL CONTEXT FROM NYAYAAI DATABASE:\n\n")
                matchedTopics.forEachIndexed { idx, match ->
                    val t = match.topic
                    promptBuilder.append("PROVISION #${idx + 1}:\n")
                    promptBuilder.append("- Title: ${t.title}\n")
                    promptBuilder.append("- Category: ${t.category}\n")
                    promptBuilder.append("- Summary: ${t.summary}\n")
                    promptBuilder.append("- Official Source / Act: ${t.official_source}\n")
                    promptBuilder.append("- Official Authority: ${t.official_authority}\n")
                    promptBuilder.append("- Official URL: ${t.official_source_url}\n")
                    if (t.next_steps.isNotEmpty()) {
                        promptBuilder.append("- Action Steps: ${t.next_steps.joinToString("; ")}\n")
                    }
                    promptBuilder.append("\n")
                }
                
                promptBuilder.append("REQUIRED RESPONSE STRUCTURE:\n")
                promptBuilder.append("You MUST format your answer using these exact headers:\n\n")
                promptBuilder.append("Relevant Law / Provision\n")
                promptBuilder.append("[Name of the law/provision/Act]\n\n")
                promptBuilder.append("What it means\n")
                promptBuilder.append("[Simple explanation of the law in plain English]\n\n")
                promptBuilder.append("Your Rights\n")
                promptBuilder.append("- [Right 1]\n- [Right 2]\n- [Right 3]\n\n")
                promptBuilder.append("What you can do\n")
                promptBuilder.append("1. [Step 1]\n2. [Step 2]\n3. [Step 3]\n\n")
                promptBuilder.append("Applicable authority / portal\n")
                promptBuilder.append("[Relevant authority]\n\n")
                promptBuilder.append("Source\n")
                promptBuilder.append("📚 [Official Act or Database Source]\n\n")
                promptBuilder.append("Confidence\n")
                promptBuilder.append("$confidenceText\n")
            } else {
                promptBuilder.append("NO SUFFICIENTLY VERIFIED PROVISION FOUND IN LOCAL KNOWLEDGE BASE.\n\n")
                promptBuilder.append("INSTRUCTIONS:\n")
                promptBuilder.append("Start your response with: 'I couldn't find a sufficiently verified provision for this question in the current NyayaAI knowledge base.'\n")
                promptBuilder.append("Then provide a safe general explanation of Indian law on this topic.\n")
                promptBuilder.append("Include: Source: NyayaAI General Guidance\nConfidence: General Guidance\n")
            }
            
            promptBuilder.append("\nUser Question: $trimmedText\n")

            val systemInstruction = "You are NyayaAI, an expert, empathetic citizen legal assistance AI for India. You explain Indian laws, citizen rights, traffic rules, consumer protection, cybercrime procedures, and emergency actions in clean, simple English. You MUST strictly stick to the requested response headers and never invent false section numbers or acts."

            var finalResponseText = ""
            var sourceTitle: String? = matchedTopics.firstOrNull()?.topic?.title
            var sourceUrl: String? = matchedTopics.firstOrNull()?.topic?.official_source_url
            var isWarning = matchedTopics.isEmpty()

            try {
                val aiResult = GeminiApiClient.generateContent(
                    prompt = promptBuilder.toString(),
                    systemInstruction = systemInstruction
                )

                if (aiResult.startsWith("ERROR_") || aiResult.isBlank()) {
                    Log.w("NyayaViewModel", "Gemini API returned $aiResult. Generating grounded answer directly from knowledge base.")
                    finalResponseText = buildFallbackGroundedAnswer(trimmedText, matchedTopics, confidenceText)
                } else {
                    finalResponseText = aiResult.trim()
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error calling Gemini API: ${e.message}", e)
                finalResponseText = buildFallbackGroundedAnswer(trimmedText, matchedTopics, confidenceText)
            } finally {
                _isLoading.value = false
            }

            val aiMsg = ChatMessage(
                sender = "ai",
                text = finalResponseText,
                sourceTitle = sourceTitle,
                sourceUrl = sourceUrl,
                isWarningNotLocal = isWarning
            )
            
            _chatHistory.value = _chatHistory.value + aiMsg
        }
    }

    private data class TopicScore(val topic: LegalTopic, val score: Int)

    // Advanced search that retrieves matching topics and ranks them
    private fun findRelevantTopics(query: String): List<TopicScore> {
        val cleanQuery = query.lowercase().trim()
        val results = mutableListOf<TopicScore>()

        _legalTopics.value.forEach { topic ->
            var score = 0
            val tTitle = topic.title.lowercase()
            val tCategory = topic.category.lowercase()
            val tSummary = topic.summary.lowercase()

            // Exact or strong title match
            if (cleanQuery.contains(tTitle)) score += 20

            // Specific topic phrase match heuristics
            if (cleanQuery.contains("salary") || cleanQuery.contains("wage") || cleanQuery.contains("pay") || cleanQuery.contains("unpaid")) {
                if (tTitle.contains("salary") || tTitle.contains("employee")) score += 15
            }
            if (cleanQuery.contains("helmet") || cleanQuery.contains("two wheeler")) {
                if (tTitle.contains("helmet") || tCategory.contains("traffic")) score += 20
            }
            if (cleanQuery.contains("fir") || cleanQuery.contains("police complaint")) {
                if (tTitle.contains("fir") || tTitle.contains("cybercrime")) score += 15
            }
            if (cleanQuery.contains("cyber") || cleanQuery.contains("online fraud") || cleanQuery.contains("upi") || cleanQuery.contains("phishing") || cleanQuery.contains("scam")) {
                if (tCategory.contains("cybercrime") || tTitle.contains("cybercrime")) score += 20
            }
            if (cleanQuery.contains("legal aid") || cleanQuery.contains("free lawyer") || cleanQuery.contains("nalsa")) {
                if (tTitle.contains("legal aid")) score += 20
            }
            if (cleanQuery.contains("harass") || cleanQuery.contains("posh") || cleanQuery.contains("women")) {
                if (tCategory.contains("women") || tTitle.contains("posh") || tTitle.contains("women")) score += 20
            }
            if (cleanQuery.contains("defective") || cleanQuery.contains("refund") || cleanQuery.contains("consumer") || cleanQuery.contains("1915")) {
                if (tCategory.contains("consumer") || tTitle.contains("consumer") || tTitle.contains("refund")) score += 20
            }
            if (cleanQuery.contains("emergency") || cleanQuery.contains("sos") || cleanQuery.contains("112")) {
                if (tCategory.contains("emergency") || tTitle.contains("emergency")) score += 20
            }

            // Keyword matches
            topic.keywords.forEach { kw ->
                if (cleanQuery.contains(kw.lowercase())) {
                    score += 8
                }
            }

            // Category match
            if (cleanQuery.contains(tCategory)) {
                score += 6
            }

            // Word overlap check
            val queryWords = cleanQuery.split(" ", ",", ".", "?", "!", "-").filter { it.length > 3 }
            queryWords.forEach { word ->
                if (tSummary.contains(word) || tTitle.contains(word)) {
                    score += 2
                }
            }

            if (score >= 4) {
                results.add(TopicScore(topic, score))
            }
        }

        return results.sortedByDescending { it.score }.take(3)
    }

    // Direct grounded answer generator when Gemini API is unavailable or offline
    private fun buildFallbackGroundedAnswer(query: String, matched: List<TopicScore>, confidence: String): String {
        if (matched.isEmpty()) {
            return """
                I couldn't find a sufficiently verified provision for this question in the current NyayaAI knowledge base.

                What you can do:
                1. Dial 112 for immediate emergency assistance.
                2. Contact the National Legal Services Authority (NALSA) at https://nalsa.gov.in/ for free legal advice.
                3. Visit the nearest District Legal Services Authority (DLSA) office.

                Source: NyayaAI General Legal Guidance
                Confidence: General Guidance
            """.trimIndent()
        }

        val sb = StringBuilder()
        sb.append("Relevant Law / Provision\n")
        matched.forEachIndexed { idx, match ->
            val t = match.topic
            sb.append("• ${t.official_source} (${t.title})\n")
        }
        sb.append("\nWhat it means\n")
        matched.forEach { match ->
            sb.append("${match.topic.summary}\n\n")
        }

        sb.append("Your Rights\n")
        matched.forEach { match ->
            val t = match.topic
            sb.append("• Right to legal protection under ${t.official_source}.\n")
            sb.append("• Right to approach ${t.official_authority} for formal grievance redressal.\n")
        }

        sb.append("\nWhat you can do\n")
        var stepNum = 1
        matched.flatMap { it.topic.next_steps }.distinct().take(4).forEach { step ->
            sb.append("$stepNum. $step\n")
            stepNum++
        }

        val primary = matched.first().topic
        sb.append("\nApplicable authority / portal\n")
        sb.append("${primary.official_authority}\n\n")

        sb.append("Source\n")
        sb.append("📚 ${primary.official_source} (${primary.official_source_url})\n\n")

        sb.append("Confidence\n")
        sb.append(confidence)

        return sb.toString().trim()
    }
    
    // Clear chat history
    fun clearChat() {
        _chatHistory.value = listOf(
            ChatMessage(
                sender = "ai",
                text = "Chat history cleared. I am ready to assist you with new legal inquiries from our local knowledge base!"
            )
        )
    }

    // Centralized Law CRUD Operations (Admin Source of Truth)
    fun addLaw(
        title: String,
        category: String,
        description: String,
        content: String = "",
        reference: String = "",
        status: String = "active",
        officialAuthority: String = "Ministry of Law and Justice",
        officialSourceUrl: String = "",
        keywords: String = "",
        lawId: String = "",
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Publish button clicked - validating law form inputs...")
        viewModelScope.launch(Dispatchers.IO) {
            var callbackFired = false
            fun sendResult(success: Boolean, message: String) {
                if (!callbackFired) {
                    callbackFired = true
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult?.invoke(success, message)
                    }
                }
            }

            try {
                // 1. Authenticated session check
                val currentAccount = _currentUserAccount.value
                val profile = _userProfile.value
                val isAppLoggedIn = _isLoggedIn.value && (currentAccount != null || profile.id.isNotBlank() || profile.email.isNotBlank())
                if (!isAppLoggedIn) {
                    Log.w("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Publishing rejected: User is not logged in.")
                    sendResult(false, "Please sign in as an administrator.")
                    return@launch
                }

                // 2. Real Firebase Auth User & Admin Authorization Verification
                val authCheck = FirebaseManager.verifyLegalDatasetPublishingAuthorization()
                Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Current Firebase UID: '${authCheck.uid}'")
                Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Current Authenticated Email: '${authCheck.email}'")
                Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Current Role: '${authCheck.role}'")
                Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Authorization Result: isAuthorized=${authCheck.isAuthorized}")

                if (!authCheck.isAuthorized) {
                    val errMessage = authCheck.errorMessage ?: "You are not authorized to publish legal records."
                    Log.w("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Authorization failed: $errMessage")
                    sendResult(false, errMessage)
                    return@launch
                }

                val targetLawId = lawId.ifBlank { "LAW-${System.currentTimeMillis()}" }
                val publisherId = authCheck.uid.ifBlank { _userProfile.value.name.ifEmpty { currentAccount?.name ?: "Root Admin" } }
                val formattedUrl = if (officialSourceUrl.isNotBlank() &&
                    !officialSourceUrl.startsWith("http://", ignoreCase = true) &&
                    !officialSourceUrl.startsWith("https://", ignoreCase = true)
                ) {
                    "https://${officialSourceUrl.trim()}"
                } else {
                    officialSourceUrl.trim()
                }

                val newLaw = LawRecord(
                    lawId = targetLawId,
                    category = category.ifBlank { "General" }.trim(),
                    title = title.trim(),
                    description = description.trim(),
                    content = content.ifBlank { description }.trim(),
                    reference = reference.ifBlank { "Official Legal Gazette" }.trim(),
                    status = status.ifBlank { "published" }.trim(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    createdBy = publisherId,
                    lastUpdatedBy = publisherId,
                    officialAuthority = officialAuthority.ifBlank { "Ministry of Law and Justice" }.trim(),
                    officialSourceUrl = formattedUrl,
                    keywords = keywords.ifBlank { "$title, $category" }.trim()
                )

                // 3. Publish to Firestore 'laws/' (Single Source of Truth)
                Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Initializing Firestore write for collection 'laws/' doc: $targetLawId...")
                val (success, msg) = FirebaseManager.publishLawDirect(newLaw)
                if (success) {
                    // 4. Store permanently in local Room database on verified publish
                    dao.insertLaw(newLaw)
                    Log.d("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Law document permanently stored in local Room DB with ID: $targetLawId")
                    
                    recordActivityLog(
                        actorRole = "Admin",
                        actorName = _userProfile.value.name.ifEmpty { "Root Admin" },
                        eventType = "LAW_PUBLISHED",
                        message = "Published law to dataset: ${newLaw.title} (${newLaw.lawId})",
                        relatedId = newLaw.lawId
                    )
                    sendResult(true, "Legal record published successfully.")
                } else {
                    Log.e("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Firestore write failed for ID $targetLawId: $msg")
                    sendResult(false, msg)
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "[LEGAL_DATASET_PUBLISH] Exception in addLaw: ${e.message}", e)
                sendResult(false, "Unable to publish the legal record because of a network problem.")
            } finally {
                if (!callbackFired) {
                    sendResult(false, "Publishing operation completed.")
                }
            }
        }
    }

    fun updateLaw(
        lawId: String,
        title: String,
        category: String,
        description: String,
        content: String = "",
        reference: String = "",
        status: String = "published",
        officialAuthority: String = "Ministry of Law and Justice",
        officialSourceUrl: String = "",
        keywords: String = "",
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authCheck = FirebaseManager.verifyLegalDatasetPublishingAuthorization()
                if (!authCheck.isAuthorized) {
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, authCheck.errorMessage ?: "You are not authorized to publish legal records.")
                    }
                    return@launch
                }

                val existing = _allLaws.value.find { it.lawId == lawId }
                val publisherId = authCheck.uid.ifBlank { _userProfile.value.name.ifEmpty { "Root Admin" } }
                val updatedLaw = LawRecord(
                    lawId = lawId,
                    category = category,
                    title = title,
                    description = description,
                    content = content.ifBlank { description },
                    reference = reference.ifBlank { existing?.reference ?: "Official Legal Gazette" },
                    status = status,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    createdBy = existing?.createdBy ?: publisherId,
                    lastUpdatedBy = publisherId,
                    officialAuthority = officialAuthority.ifBlank { existing?.officialAuthority ?: "Ministry of Law and Justice" },
                    officialSourceUrl = officialSourceUrl.ifBlank { existing?.officialSourceUrl ?: "" },
                    keywords = keywords.ifBlank { existing?.keywords ?: "$title, $category" }
                )

                val (success, msg) = FirebaseManager.publishLawDirect(updatedLaw)
                if (success) {
                    dao.insertLaw(updatedLaw)
                    recordActivityLog(
                        actorRole = "Admin",
                        actorName = _userProfile.value.name.ifEmpty { "Root Admin" },
                        eventType = "LAW_UPDATED",
                        message = "Updated legal record: ${updatedLaw.title} (${updatedLaw.lawId})",
                        relatedId = updatedLaw.lawId
                    )
                }
                withContext(Dispatchers.Main) {
                    onResult?.invoke(success, msg)
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error in updateLaw: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Unable to publish the legal record because of a network problem.")
                }
            }
        }
    }

    fun updateLawStatus(lawId: String, status: String, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authCheck = FirebaseManager.verifyLegalDatasetPublishingAuthorization()
                if (!authCheck.isAuthorized) {
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, authCheck.errorMessage ?: "You are not authorized to publish legal records.")
                    }
                    return@launch
                }

                val adminName = _userProfile.value.name.ifEmpty { "Root Admin" }
                val existing = _allLaws.value.find { it.lawId == lawId }
                if (existing != null) {
                    val updated = existing.copy(
                        status = status,
                        updatedAt = System.currentTimeMillis(),
                        lastUpdatedBy = adminName
                    )
                    dao.insertLaw(updated)
                }
                FirebaseManager.updateLawStatus(lawId, status, adminName) { success, err ->
                    viewModelScope.launch(Dispatchers.Main) {
                        val action = if (status.equals("active", ignoreCase = true)) "Activated" else "Deactivated"
                        if (success) {
                            onResult?.invoke(true, "Law $action in Legal Dataset.")
                        } else {
                            onResult?.invoke(false, err ?: "Unable to update law status.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.localizedMessage ?: "Error updating status.")
                }
            }
        }
    }

    fun deleteLaw(lawId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authCheck = FirebaseManager.verifyLegalDatasetPublishingAuthorization()
                if (!authCheck.isAuthorized) {
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, authCheck.errorMessage ?: "You are not authorized to publish legal records.")
                    }
                    return@launch
                }

                dao.deleteLawById(lawId)
                FirebaseManager.deleteLaw(lawId) { success, err ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (success) {
                            onResult?.invoke(true, "Law deleted successfully from Legal Dataset.")
                        } else {
                            onResult?.invoke(false, err ?: "Unable to delete law.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.localizedMessage ?: "Error deleting law.")
                }
            }
        }
    }

    // Deprecated wrapper for backward compatibility
    fun addCustomLegalTopic(title: String, category: String, summary: String, officialAuthority: String, officialSource: String, officialSourceUrl: String) {
        addLaw(
            title = title,
            category = category,
            description = summary,
            content = summary,
            reference = officialSource,
            status = "active",
            officialAuthority = officialAuthority,
            officialSourceUrl = officialSourceUrl
        )
    }

    // Select Language
    fun selectLanguage(language: String) {
        val normalized = if (language.equals("Tamil", ignoreCase = true) || language.equals("தமிழ்", ignoreCase = true)) "Tamil" else "English"
        _currentLanguage.value = normalized
        sharedPrefs?.edit()?.putString("selected_language", normalized)?.apply()
        appContext?.getSharedPreferences("nyaya_prefs", Context.MODE_PRIVATE)?.edit()?.putString("selected_language", normalized)?.apply()
        Log.d("NyayaViewModel", "Language selected and persisted: $normalized")
    }

    // Submit Citizen Complaint
    fun submitComplaint(
        context: Context,
        title: String,
        category: String,
        description: String,
        state: String,
        district: String,
        address: String,
        imageUri: String?,
        isAnonymous: Boolean,
        latitude: Double = 28.6139,
        longitude: Double = 77.2090,
        onProgress: ((Int) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val randomNum = String.format(Locale.US, "%06d", (10000..999999).random())
            val complaintId = "CMP-$currentYear-$randomNum"
            val profile = _userProfile.value

            // AI Classification
            val predictedDept = classifyComplaintAI(description)

            // Priority
            val textForPriority = (title + " " + description).lowercase()
            val priority = when {
                textForPriority.contains("sos") || textForPriority.contains("emergency") || textForPriority.contains("attack") || textForPriority.contains("weapon") || textForPriority.contains("harassment") || textForPriority.contains("danger") || textForPriority.contains("safety") -> "Critical"
                textForPriority.contains("theft") || textForPriority.contains("cyber") || textForPriority.contains("robbery") || textForPriority.contains("fraud") || textForPriority.contains("accident") -> "High"
                else -> "Medium"
            }

            val now = System.currentTimeMillis()
            val initialTimelineJson = try {
                val arr = org.json.JSONArray()
                val obj = org.json.JSONObject().apply {
                    put("status", "Submitted")
                    put("timestamp", now)
                    put("note", "Complaint Submitted")
                    put("by", if (isAnonymous) "Anonymous" else profile.name)
                    put("dateStr", com.example.util.TimeUtils.formatDate(now))
                    put("timeStr", com.example.util.TimeUtils.formatTime(now))
                    put("relativeTime", "Just now")
                }
                arr.put(obj)
                arr.toString()
            } catch (e: Exception) {
                "[]"
            }

            val initialComplaint = CitizenComplaint(
                id = complaintId,
                citizenId = profile.email.ifEmpty { profile.id.ifEmpty { "citizen@demo.com" } },
                title = title,
                description = description,
                category = category,
                state = state,
                district = district,
                address = address,
                imageUri = imageUri,
                timestamp = now,
                isAnonymous = isAnonymous,
                reporterName = if (isAnonymous) "Anonymous" else profile.name,
                reporterEmail = profile.email,
                citizenPhone = if (!isAnonymous && profile.phone.isNotBlank()) profile.phone else "Phone number not provided",
                status = "Submitted",
                aiPredictedDepartment = predictedDept,
                priority = priority,
                assignedOfficer = "",
                authorityRemarks = "",
                latitude = latitude,
                longitude = longitude,
                createdAt = now,
                updatedAt = now,
                resolvedAt = 0L,
                lastModifiedBy = if (isAnonymous) "Anonymous" else profile.name,
                photoFileName = if (!imageUri.isNullOrEmpty()) "evidence_${System.currentTimeMillis()}.jpg" else "",
                timeline = initialTimelineJson,
                notificationHistory = "[]"
            )

            Log.d("NyayaComplaintTracker", "Complaint created: ID=$complaintId, Citizen UID/Email=${initialComplaint.citizenId}, Title=$title")

            // 1. Save locally in Room DAO immediately
            dao.insertComplaint(initialComplaint)

            // 2. Initial System Message in Complaint Conversation Subcollection
            val dateStr = com.example.util.DateUtils.formatDate(now)
            val timeStr = com.example.util.DateUtils.formatTime(now)
            val initSysMsg = com.example.db.ComplaintMessage(
                messageId = "msg_sys_" + java.util.UUID.randomUUID().toString().take(8),
                complaintId = complaintId,
                senderId = "SYSTEM",
                senderRole = "SYSTEM",
                senderName = "System",
                message = "Complaint #$complaintId submitted successfully on $dateStr at $timeStr.",
                messageType = "SYSTEM",
                createdAt = now,
                isRead = true
            )

            dao.insertComplaintMessage(initSysMsg)

            // 3. Sync to Firestore immediately
            FirebaseManager.syncComplaint(initialComplaint)
            FirebaseManager.sendComplaintMessage(complaintId, initSysMsg)

            // 4. Notifications
            createNotification(
                userEmail = profile.email,
                title = "Complaint Submitted",
                message = "Your complaint '$title' (ID: $complaintId) has been submitted successfully.",
                isAuthority = false,
                isHighPriority = (priority == "Critical")
            )
            createNotification(
                userEmail = "all",
                title = "New Complaint Assigned",
                message = "New $priority-priority complaint ($complaintId) received in $district district regarding '$title'.",
                isAuthority = true,
                isHighPriority = (priority == "Critical" || priority == "High")
            )

            // Reward points
            dao.addPointsToUser(30)

            // 5. Return success to UI immediately on Main thread!
            withContext(Dispatchers.Main) {
                onResult(true, "Complaint Registered Successfully! Complaint ID: $complaintId")
            }

            // 6. Background image upload handling if an image was provided
            if (!imageUri.isNullOrEmpty()) {
                FirebaseManager.uploadComplaintImage(context, complaintId, imageUri, onProgress) { imageSuccess, downloadUrl, storageError ->
                    viewModelScope.launch(Dispatchers.IO) {
                        if (imageSuccess && !downloadUrl.isNullOrEmpty()) {
                            Log.d("NyayaViewModel", "Image upload succeeded: $downloadUrl")
                            val updatedComplaint = initialComplaint.copy(imageUri = downloadUrl)
                            dao.insertComplaint(updatedComplaint)
                            FirebaseManager.syncComplaint(updatedComplaint)

                            val photoMsg = com.example.db.ComplaintMessage(
                                messageId = "msg_photo_" + java.util.UUID.randomUUID().toString().take(8),
                                complaintId = complaintId,
                                senderId = if (isAnonymous) "ANONYMOUS" else profile.email,
                                senderRole = "CITIZEN",
                                senderName = if (isAnonymous) "Anonymous Citizen" else profile.name,
                                message = "Uploaded supporting evidence photo.",
                                messageType = "IMAGE",
                                attachmentUrl = downloadUrl,
                                createdAt = System.currentTimeMillis()
                            )
                            dao.insertComplaintMessage(photoMsg)
                            FirebaseManager.sendComplaintMessage(complaintId, photoMsg)
                        } else {
                            Log.w("NyayaViewModel", "Image upload failed: ${storageError ?: "Unknown error"}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Complaint Registered Successfully. Image upload failed. You can upload supporting evidence later.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }


    // Update Complaint (Citizen edit before review starts)
    fun updateComplaint(
        context: Context,
        complaintId: String,
        title: String,
        category: String,
        description: String,
        state: String,
        district: String,
        address: String,
        imageUri: String?,
        isAnonymous: Boolean,
        onProgress: ((Int) -> Unit)? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getComplaintById(complaintId)
            if (existing == null) {
                onResult(false, "Complaint not found.")
                return@launch
            }
            if (existing.status != "Submitted" && existing.status != "Pending") {
                onResult(false, "Cannot edit complaint after review has started.")
                return@launch
            }
            
            val hasImage = !imageUri.isNullOrEmpty()
            
            FirebaseManager.uploadComplaintImage(context, complaintId, imageUri, onProgress) { imageSuccess, downloadUrl, storageError ->
                viewModelScope.launch(Dispatchers.IO) {
                    if (!imageSuccess && hasImage) {
                        Log.e("NyayaViewModel", "Storage failed during update: ${storageError ?: "Image upload failed"}")
                        viewModelScope.launch(Dispatchers.Main) {
                            onResult(false, "IMAGE_UPLOAD_FAILED: ${storageError ?: "Image upload failed"}")
                        }
                        return@launch
                    }

                    val finalImageUri = downloadUrl ?: imageUri
                    val predictedDept = classifyComplaintAI(description)
                    val updated = existing.copy(
                        title = title,
                        category = category,
                        description = description,
                        state = state,
                        district = district,
                        address = address,
                        imageUri = finalImageUri,
                        isAnonymous = isAnonymous,
                        reporterName = if (isAnonymous) "Anonymous" else _userProfile.value.name,
                        aiPredictedDepartment = predictedDept,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.insertComplaint(updated)
                    FirebaseManager.syncComplaint(updated)
                    
                    createNotification(
                        userEmail = _userProfile.value.email,
                        title = "Complaint Updated",
                        message = "Your complaint '$title' (ID: $complaintId) has been updated successfully.",
                        isAuthority = false
                    )
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(true, "Complaint updated successfully.")
                    }
                }
            }
        }
    }

    // Delete Complaint (Citizen delete before review starts)
    fun deleteComplaint(complaintId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getComplaintById(complaintId)
            if (existing == null) {
                onResult(false, "Complaint not found.")
                return@launch
            }
            if (existing.status != "Submitted" && existing.status != "Pending") {
                onResult(false, "Cannot delete complaint after review has started.")
                return@launch
            }
            
            Log.d("NyayaComplaintTracker", "Complaint deleted: ID=$complaintId")
            dao.deleteComplaintById(complaintId)
            
            createNotification(
                userEmail = _userProfile.value.email,
                title = "Complaint Deleted",
                message = "Your complaint '${existing.title}' has been deleted.",
                isAuthority = false
            )
            onResult(true, "Complaint deleted successfully.")
        }
    }

    // Update Complaint Status (Authority action)
    fun updateComplaintStatusByAuthority(
        complaintId: String,
        status: String, // "Submitted", "Under Review", "Assigned", "In Investigation", "Resolved", "Rejected", "Closed"
        assignedOfficer: String,
        remarks: String,
        department: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Strict Validation (BUG 1 & BUG 8)
                if (complaintId.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Error: Invalid Complaint ID.") }
                    return@launch
                }
                if (status.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Error: Please select a status.") }
                    return@launch
                }
                if (assignedOfficer.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Error: Please assign an investigating officer.") }
                    return@launch
                }
                if (remarks.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Error: Please enter authority notes or instructions.") }
                    return@launch
                }

                val existing = dao.getComplaintById(complaintId) ?: _allComplaints.value.find { it.id == complaintId }
                if (existing == null) {
                    withContext(Dispatchers.Main) { onResult(false, "Error: Complaint #$complaintId not found in system.") }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val dateStr = com.example.util.TimeUtils.formatDate(now)
                val timeStr = com.example.util.TimeUtils.formatTime(now)
                val relativeTime = com.example.util.TimeUtils.formatRelativeTime(now)
                val officerName = assignedOfficer.trim()
                val authorityNotes = remarks.trim()
                val selectedDept = if (department.isNotBlank()) department.trim() else existing.aiPredictedDepartment.ifEmpty { existing.category }
                val currentUser = _userProfile.value.name.ifEmpty { "Authority Officer" }

                // 2. Append Timeline Action (BUG 5)
                val existingTimeline = existing.timeline
                val updatedTimelineJson = try {
                    val arr = if (existingTimeline.isNotEmpty() && existingTimeline.startsWith("[")) {
                        org.json.JSONArray(existingTimeline)
                    } else {
                        org.json.JSONArray()
                    }
                    val obj = org.json.JSONObject().apply {
                        put("status", status)
                        put("officer", officerName)
                        put("by", "Officer $officerName")
                        put("note", authorityNotes)
                        put("notes", authorityNotes)
                        put("dateStr", dateStr)
                        put("timeStr", timeStr)
                        put("relativeTime", relativeTime)
                        put("timestamp", now)
                    }
                    arr.put(obj)
                    arr.toString()
                } catch (e: Exception) {
                    Log.e("NyayaViewModel", "Timeline formatting exception: ${e.message}")
                    existingTimeline
                }

                val isResolvedOrClosed = (status.equals("Resolved", ignoreCase = true) || status.equals("Closed", ignoreCase = true))
                val resolvedTime = if (isResolvedOrClosed) (if (existing.resolvedAt > 0) existing.resolvedAt else now) else existing.resolvedAt

                val updatedComplaint = existing.copy(
                    status = status,
                    aiPredictedDepartment = selectedDept,
                    category = selectedDept,
                    assignedOfficer = officerName,
                    authorityRemarks = authorityNotes,
                    updatedAt = now,
                    resolvedAt = resolvedTime,
                    lastModifiedBy = currentUser,
                    timeline = updatedTimelineJson
                )

                // 3. Save to local Room DB first (Offline Support - BUG 9)
                dao.insertComplaint(updatedComplaint)

                // 4. Create Audit Log
                val auditLog = AuditLog(
                    id = "aud_" + java.util.UUID.randomUUID().toString().take(8),
                    complaintId = complaintId,
                    officerName = officerName,
                    timestamp = now,
                    action = "Updated status to '$status'",
                    previousStatus = existing.status,
                    newStatus = status,
                    notes = authorityNotes
                )
                dao.insertAuditLog(auditLog)
                FirebaseManager.syncAuditLog(auditLog)

                // 5. Create Notification for Citizen (BUG 4)
                val notifTitle = "Complaint Update: $status"
                val notifMessage = "Complaint #$complaintId status is now '$status'.\nAssigned Officer: $officerName ($selectedDept)\nNotes: $authorityNotes\nUpdated on $dateStr at $timeStr"
                val notif = Notification(
                    id = "not_" + java.util.UUID.randomUUID().toString().take(6),
                    userEmail = existing.reporterEmail.ifEmpty { "all" },
                    title = notifTitle,
                    message = notifMessage,
                    timestamp = now,
                    isRead = false,
                    isAuthority = false,
                    isHighPriority = isResolvedOrClosed
                )
                dao.insertNotification(notif)
                FirebaseManager.syncNotification(notif)

                // 6. System Message in Conversation Timeline
                val sysMsg = com.example.db.ComplaintMessage(
                    messageId = "msg_sys_" + java.util.UUID.randomUUID().toString().take(8),
                    complaintId = complaintId,
                    senderId = "SYSTEM",
                    senderRole = "SYSTEM",
                    senderName = "System",
                    message = "Status updated to '$status'. Assigned Officer: $officerName ($selectedDept). Notes: $authorityNotes",
                    messageType = "SYSTEM",
                    createdAt = now,
                    isRead = true
                )
                dao.insertComplaintMessage(sysMsg)
                FirebaseManager.sendComplaintMessage(complaintId, sysMsg)

                Log.d("NyayaComplaintTracker", "Complaint updated by Authority UID=${_userProfile.value.id}: Complaint ID=$complaintId, Status=$status, AssignedOfficer=$officerName")

                // 7. Update existing document in Firestore with serverTimestamp and return callback
                FirebaseManager.updateComplaintFieldsByAuthority(
                    complaintId = complaintId,
                    status = status,
                    department = selectedDept,
                    assignedOfficer = officerName,
                    authorityNotes = authorityNotes,
                    resolvedAt = resolvedTime,
                    timelineJson = updatedTimelineJson,
                    notificationHistoryJson = existing.notificationHistory,
                    lastModifiedBy = currentUser,
                    title = existing.title,
                    description = existing.description,
                    citizenId = existing.citizenId.ifEmpty { existing.reporterEmail },
                    reporterEmail = existing.reporterEmail,
                    reporterName = existing.reporterName,
                    category = existing.category,
                    createdAt = existing.createdAt,
                    timestamp = existing.timestamp
                ) { success, errMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (success) {
                            onResult(true, "Complaint #$complaintId updated to '$status' & synced live!")
                        } else {
                            onResult(false, errMsg ?: "Failed to update complaint in Firestore.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Exception in updateComplaintStatusByAuthority: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Update saved locally. Network error: ${e.localizedMessage ?: "Reconnecting..."}")
                }
            }
        }
    }

    // --- Real-Time Complaint Conversation System ---

    fun getMessagesForComplaint(complaintId: String): Flow<List<com.example.db.ComplaintMessage>> {
        return dao.getMessagesForComplaint(complaintId)
    }

    fun listenToComplaintMessages(complaintId: String): com.google.firebase.firestore.ListenerRegistration? {
        return FirebaseManager.listenToComplaintMessages(complaintId) { messages ->
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertComplaintMessages(messages)
            }
        }
    }

    fun sendComplaintMessage(
        complaintId: String,
        messageText: String,
        senderRole: String, // "CITIZEN" or "AUTHORITY"
        messageType: String = "TEXT",
        attachmentUrl: String? = null,
        voiceUrl: String? = null,
        documentUrl: String? = null,
        locationData: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (complaintId.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Invalid complaint ID.") }
                    return@launch
                }
                if (messageText.trim().isBlank() && attachmentUrl.isNullOrEmpty() && voiceUrl.isNullOrEmpty() && documentUrl.isNullOrEmpty() && locationData.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { onResult(false, "Message cannot be empty.") }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val currentProfile = _userProfile.value
                val senderName = if (senderRole.equals("AUTHORITY", ignoreCase = true)) {
                    if (currentProfile.name.isNotEmpty() && currentProfile.name != "Citizen Defender") currentProfile.name else "Official Authority"
                } else {
                    currentProfile.name.ifEmpty { "Citizen" }
                }

                val msgObj = com.example.db.ComplaintMessage(
                    messageId = "msg_" + java.util.UUID.randomUUID().toString().take(10),
                    complaintId = complaintId,
                    senderId = currentProfile.email,
                    senderRole = senderRole,
                    senderName = senderName,
                    message = messageText.trim(),
                    messageType = messageType,
                    createdAt = now,
                    isRead = false,
                    attachmentUrl = attachmentUrl,
                    voiceUrl = voiceUrl,
                    documentUrl = documentUrl,
                    locationData = locationData
                )

                // Save locally first (offline queue support)
                dao.insertComplaintMessage(msgObj)

                // Sync to Firestore subcollection
                FirebaseManager.sendComplaintMessage(complaintId, msgObj)

                // Generate notification
                val existingComplaint = dao.getComplaintById(complaintId) ?: _allComplaints.value.find { it.id == complaintId }
                if (existingComplaint != null) {
                    val isCitizen = senderRole.equals("CITIZEN", ignoreCase = true)
                    val notifRecipient = if (isCitizen) "all" else existingComplaint.reporterEmail
                    val notifTitle = if (isCitizen) "New Message from Citizen" else "Authority Replied"
                    val notifMsg = "$senderName: \"${messageText.trim()}\" (Complaint #$complaintId)"
                    
                    val notification = Notification(
                        id = "not_" + java.util.UUID.randomUUID().toString().take(6),
                        userEmail = notifRecipient.ifEmpty { "all" },
                        title = notifTitle,
                        message = notifMsg,
                        timestamp = now,
                        isRead = false,
                        isAuthority = isCitizen
                    )
                    dao.insertNotification(notification)
                    FirebaseManager.syncNotification(notification)
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Message sent")
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Exception in sendComplaintMessage: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error: ${e.localizedMessage ?: "Failed to send message"}")
                }
            }
        }
    }

    fun markComplaintMessagesRead(complaintId: String, myRole: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.markComplaintMessagesRead(complaintId, myRole)
            FirebaseManager.markMessagesAsReadInFirestore(complaintId, if (myRole.equals("CITIZEN", ignoreCase = true)) "AUTHORITY" else "CITIZEN")
        }
    }


    // Get replies for a complaint
    fun getRepliesForComplaint(complaintId: String): Flow<List<com.example.db.ComplaintReply>> {
        return dao.getRepliesForComplaint(complaintId)
    }

    // Send a reply on a complaint (Authority, Admin or Citizen)
    fun sendComplaintReply(
        complaintId: String,
        message: String,
        updatedStatus: String,
        attachmentPath: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getComplaintById(complaintId)
            if (existing == null) {
                onResult(false, "Complaint not found.")
                return@launch
            }
            
            val profile = _userProfile.value
            val authorName = if (profile.role == "Citizen" && existing.isAnonymous) "Anonymous Citizen" else profile.name
            val dept = if (profile.role == "Authority") {
                profile.badges.split(",").firstOrNull()?.trim() ?: "Public Grievance"
            } else if (profile.role == "Admin") {
                "System Administrator"
            } else {
                "Citizen Node"
            }

            Log.d("NyayaComplaintTracker", "Complaint response: ID=$complaintId, SenderRole=${profile.role}, UpdatedStatus=$updatedStatus, Message=$message")

            val newReply = com.example.db.ComplaintReply(
                complaintId = complaintId,
                authorityName = authorName,
                department = dept,
                timestamp = System.currentTimeMillis(),
                message = message,
                updatedStatus = updatedStatus,
                attachmentPath = attachmentPath
            )
            dao.insertComplaintReply(newReply)
            FirebaseManager.syncComplaintReply(newReply)

            // Sync complaint's status and update authority remarks
            val updatedComplaint = existing.copy(
                status = updatedStatus,
                authorityRemarks = message
            )
            dao.insertComplaint(updatedComplaint)
            FirebaseManager.syncComplaint(updatedComplaint)

            // Send Realtime Notification
            val isFromAuthority = (profile.role == "Authority" || profile.role == "Admin")
            if (isFromAuthority) {
                // Instantly notify citizen
                createNotification(
                    userEmail = existing.reporterEmail,
                    title = "New Reply on Case #${existing.id.takeLast(4)}",
                    message = "Authority node ($authorName) has dispatched a reply: \"$message\". Status: $updatedStatus",
                    isAuthority = false,
                    isHighPriority = (updatedStatus == "Critical" || updatedStatus == "Rejected")
                )
            } else {
                // Instantly notify authority
                createNotification(
                    userEmail = "all",
                    title = "New Citizen Reply on Case #${existing.id.takeLast(4)}",
                    message = "Citizen has added a response: \"$message\"",
                    isAuthority = true,
                    isHighPriority = false
                )
            }

            onResult(true, "Response successfully transmitted across the ledger node.")
        }
    }

    // Submit Official Legal Response (Authority Action - STEP 5)
    fun submitOfficialInquiryResponse(
        requestId: String,
        officerName: String,
        department: String,
        designation: String,
        replyText: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (requestId.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Invalid inquiry request ID.") }
                    return@launch
                }
                if (officerName.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Officer Name is required.") }
                    return@launch
                }
                if (department.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Department is required.") }
                    return@launch
                }
                if (designation.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Designation is required.") }
                    return@launch
                }
                if (replyText.trim().isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Official Response cannot be empty.") }
                    return@launch
                }

                val existing = _allCitizenRequests.value.find { it.id == requestId }
                if (existing == null) {
                    withContext(Dispatchers.Main) { onResult(false, "Inquiry request #$requestId not found.") }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val isEditing = existing.status == "Answered" || existing.reply.isNotBlank()

                val updated = existing.copy(
                    reply = replyText.trim(),
                    officerName = officerName.trim(),
                    officerDepartment = department.trim(),
                    officerDesignation = designation.trim(),
                    officerId = _userProfile.value.id,
                    status = "Answered",
                    respondedAt = if (isEditing && existing.respondedAt > 0) existing.respondedAt else now,
                    edited = isEditing,
                    editedAt = if (isEditing) now else existing.editedAt,
                    lastUpdated = now
                )

                dao.insertCitizenRequest(updated)
                FirebaseManager.syncCitizenRequest(updated)

                val dateStr = com.example.util.TimeUtils.formatDate(now)
                val timeStr = com.example.util.TimeUtils.formatTime(now)
                createNotification(
                    userEmail = existing.citizenEmail,
                    title = "Official Legal Response Received",
                    message = "Officer ${officerName.trim()} (${department.trim()}) responded to inquiry #$requestId on $dateStr at $timeStr.",
                    isAuthority = false
                )

                withContext(Dispatchers.Main) {
                    onResult(true, "Official Response Submitted Successfully.")
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Exception in submitOfficialInquiryResponse: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Unable to submit response. Please try again.")
                }
            }
        }
    }

    // Delete Official Legal Response (Authority Action - STEP 10)
    fun deleteOfficialInquiryResponse(requestId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (requestId.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Invalid inquiry request ID.") }
                    return@launch
                }
                val existing = _allCitizenRequests.value.find { it.id == requestId }
                if (existing == null) {
                    withContext(Dispatchers.Main) { onResult(false, "Inquiry request #$requestId not found.") }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val updated = existing.copy(
                    reply = "",
                    officerName = "",
                    officerDepartment = "",
                    officerDesignation = "",
                    officerId = "",
                    respondedAt = 0L,
                    edited = false,
                    editedAt = 0L,
                    status = "Open",
                    lastUpdated = now
                )

                dao.insertCitizenRequest(updated)
                FirebaseManager.syncCitizenRequest(updated)

                withContext(Dispatchers.Main) {
                    onResult(true, "Official response removed by Authority.")
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Exception in deleteOfficialInquiryResponse: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to delete response: ${e.localizedMessage ?: "Please retry"}")
                }
            }
        }
    }

    // Legacy reply helper
    fun replyToCitizenRequest(requestId: String, replyMessage: String, onResult: (Boolean, String) -> Unit) {
        val officerName = _userProfile.value.name.ifEmpty { "Official Legal Officer" }
        submitOfficialInquiryResponse(
            requestId = requestId,
            officerName = officerName,
            department = "Legal Cell",
            designation = "Legal Officer",
            replyText = replyMessage,
            onResult = onResult
        )
    }

    // Create/Add Authority Account (Admin Action)
    fun addAuthorityAccount(
        name: String,
        email: String,
        passwordHash: String,
        department: String,
        district: String,
        contact: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getUserAccount(email)
            if (existing != null) {
                onResult(false, "An account with this email already exists.")
                return@launch
            }
            val account = com.example.db.UserAccount(
                email = email,
                name = name,
                passwordHash = passwordHash,
                role = "Authority",
                isDisabled = false,
                isApproved = true,
                department = department,
                district = district,
                contact = contact,
                performanceScore = (75..98).random()
            )
            dao.insertUserAccount(account)
            FirebaseManager.syncUserAccount(account)
            onResult(true, "Authority account successfully registered on the ledger.")
        }
    }

    // Edit/Update Authority Account (Admin Action)
    fun updateAuthorityAccount(
        email: String,
        name: String,
        department: String,
        district: String,
        contact: String,
        isDisabled: Boolean,
        performanceScore: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getUserAccount(email)
            if (existing == null) {
                onResult(false, "Account not found.")
                return@launch
            }
            val updated = existing.copy(
                name = name,
                department = department,
                district = district,
                contact = contact,
                isDisabled = isDisabled,
                performanceScore = performanceScore
            )
            dao.insertUserAccount(updated)
            FirebaseManager.syncUserAccount(updated)
            onResult(true, "Authority details successfully synced.")
        }
    }

    // Create Notification Utility
    fun createNotification(
        userEmail: String,
        title: String,
        message: String,
        isAuthority: Boolean = false,
        isHighPriority: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val notif = Notification(
                id = "not_" + UUID.randomUUID().toString().take(6),
                userEmail = userEmail,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                isAuthority = isAuthority,
                isHighPriority = isHighPriority
            )
            dao.insertNotification(notif)
            FirebaseManager.syncNotification(notif)
        }
    }

    fun markAllNotificationsRead(userEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.markAllNotificationsAsReadForUser(userEmail)
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.markNotificationAsRead(id)
        }
    }

    // AI Classification logic
    private fun classifyComplaintAI(description: String): String {
        val categories = listOf("Police", "Cyber Crime", "Women Safety", "Consumer Protection", "Traffic", "Municipality", "Water Supply", "Electricity", "Revenue", "Land Dispute", "Health", "Education", "Environment", "Public Grievance", "Others")
        
        // Try Gemini API if key is available
        if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) {
            val systemInstruction = "You are a legal complaint classification AI. Analyze the user's complaint description and output EXACTLY one of the following departments, and absolutely nothing else: ${categories.joinToString(", ")}."
            try {
                val response = GeminiApiClient.generateContent(
                    prompt = description,
                    systemInstruction = systemInstruction
                ).trim()
                val cleanResponse = response.replace("\"", "").replace("'", "").replace(".", "").trim()
                if (categories.any { it.equals(cleanResponse, ignoreCase = true) }) {
                    return categories.first { it.equals(cleanResponse, ignoreCase = true) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // High fidelity fallback keyword classifier
        val desc = description.lowercase()
        return when {
            desc.contains("robbery") || desc.contains("theft") || desc.contains("fight") || desc.contains("assault") || desc.contains("weapon") || desc.contains("murder") || desc.contains("illegal") || desc.contains("police") || desc.contains("crime") -> "Police"
            desc.contains("hack") || desc.contains("scam") || desc.contains("phishing") || desc.contains("online fraud") || desc.contains("cyber") || desc.contains("internet") || desc.contains("social media") -> "Cyber Crime"
            desc.contains("women") || desc.contains("eve teasing") || desc.contains("harassment") || desc.contains("domestic violence") || desc.contains("abuse") -> "Women Safety"
            desc.contains("consumer") || desc.contains("shopkeeper") || desc.contains("defective") || desc.contains("refund") || desc.contains("overcharging") || desc.contains("fake product") -> "Consumer Protection"
            desc.contains("traffic") || desc.contains("parking") || desc.contains("signal") || desc.contains("road block") || desc.contains("speeding") -> "Traffic"
            desc.contains("garbage") || desc.contains("street light") || desc.contains("drainage") || desc.contains("pothole") || desc.contains("municipality") || desc.contains("waste") -> "Municipality"
            desc.contains("water") || desc.contains("leakage") || desc.contains("contamination") || desc.contains("drinking water") || desc.contains("supply") -> "Water Supply"
            desc.contains("electricity") || desc.contains("power cut") || desc.contains("meter") || desc.contains("voltage") || desc.contains("transformer") -> "Electricity"
            desc.contains("land") || desc.contains("property") || desc.contains("boundary") || desc.contains("encroachment") || desc.contains("dispute") || desc.contains("patwari") -> "Land Dispute"
            desc.contains("hospital") || desc.contains("doctor") || desc.contains("health") || desc.contains("medicine") || desc.contains("clinic") || desc.contains("disease") -> "Health"
            desc.contains("school") || desc.contains("college") || desc.contains("education") || desc.contains("teacher") || desc.contains("fees") || desc.contains("admission") -> "Education"
            desc.contains("pollution") || desc.contains("forest") || desc.contains("tree") || desc.contains("environment") || desc.contains("noise") || desc.contains("river") -> "Environment"
            desc.contains("tax") || desc.contains("revenue") || desc.contains("stamp") || desc.contains("registration") -> "Revenue"
            else -> "Public Grievance"
        }
    }

    private suspend fun seedDefaultComplaints() {
        // No-op: Real application relies on authentic citizen complaints
    }

    private fun seedInitialAppRatings() {
        // No-op: Real application relies on authentic user ratings
    }

    private fun seedInitialUserFeedbacks() {
        // No-op: Real application relies on authentic user feedback submissions
    }

    fun submitAppRating(ratingScore: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = userProfile.value.id.ifBlank { userProfile.value.email.ifBlank { "anonymous_user" } }
                val ratingObj = com.example.db.AppRating(
                    uid = uid,
                    role = userProfile.value.role,
                    userName = userProfile.value.name,
                    rating = ratingScore.coerceIn(1, 5),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertAppRating(ratingObj)
                FirebaseManager.syncRating(ratingObj)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error submitting app rating: ${e.message}")
            }
        }
    }

    fun submitUserFeedback(
        category: String,
        subject: String,
        message: String,
        rating: Int,
        attachmentUrl: String = "",
        roleOverride: String? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val feedbackId = UUID.randomUUID().toString()
                val uid = userProfile.value.id.ifBlank { userProfile.value.email.ifBlank { "user_${System.currentTimeMillis()}" } }
                val effectiveRole = roleOverride?.ifBlank { null } ?: userProfile.value.role.ifBlank { "Citizen" }
                val feedback = com.example.db.UserFeedback(
                    feedbackId = feedbackId,
                    uid = uid,
                    userName = userProfile.value.name.ifBlank { userProfile.value.email.ifBlank { "User" } },
                    email = userProfile.value.email,
                    role = effectiveRole,
                    category = category.ifBlank { "Other" },
                    subject = subject,
                    message = message,
                    rating = rating.coerceIn(1, 5),
                    attachmentUrl = attachmentUrl,
                    status = "Pending",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertUserFeedback(feedback)
                FirebaseManager.syncFeedback(feedback)
                recordActivityLog(
                    actorRole = effectiveRole,
                    actorName = feedback.userName,
                    eventType = "FEEDBACK_SUBMITTED",
                    message = "Feedback submitted: ${category} (${rating}★)",
                    relatedId = feedbackId
                )
                withContext(Dispatchers.Main) {
                    onComplete(true, "Feedback submitted successfully.")
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error submitting feedback: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Failed to submit feedback.")
                }
            }
        }
    }

    fun replyToFeedback(
        feedbackId: String,
        adminName: String,
        department: String,
        replyText: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                dao.updateUserFeedbackReply(
                    feedbackId = feedbackId,
                    adminName = adminName.ifBlank { "Admin" },
                    department = department.ifBlank { "Nyaya Administration" },
                    reply = replyText,
                    status = "Reviewed",
                    repliedAt = now,
                    updatedAt = now
                )
                FirebaseManager.replyToFeedback(feedbackId, adminName, department, replyText)
                recordActivityLog(
                    actorRole = "Admin",
                    actorName = adminName.ifBlank { "Admin" },
                    eventType = "FEEDBACK_REPLIED",
                    message = "Replied to user feedback",
                    relatedId = feedbackId
                )
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("NyayaViewModel", "Error replying to feedback: ${e.message}")
            }
        }
    }
}
