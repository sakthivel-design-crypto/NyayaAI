package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiApiClient
import com.example.model.*
import com.example.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

const val DEMO_MODE = true

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

            val account = dao.getUserAccount(normalizedEmailOrId)
            
            when (portalType) {
                "Citizen" -> {
                    if (!isEmailFormat) {
                        onResult(false, "Invalid email address.")
                        return@launch
                    }
                    if (account == null) {
                        onResult(false, "No account found with this email.")
                        return@launch
                    }
                    if (account.passwordHash != password) {
                        onResult(false, "Incorrect password.")
                        return@launch
                    }
                    if (account.isDisabled) {
                        onResult(false, "This account has been disabled.")
                        return@launch
                    }
                }
                "Authority" -> {
                    if (account == null) {
                        onResult(false, "Invalid Authority ID.")
                        return@launch
                    }
                    if (account.passwordHash != password) {
                        onResult(false, "Incorrect password.")
                        return@launch
                    }
                    if (!account.isApproved) {
                        onResult(false, "Authority account not approved.")
                        return@launch
                    }
                    if (account.role != "Authority") {
                        onResult(false, "Unauthorized access. Authority privileges required.")
                        return@launch
                    }
                }
                "Admin" -> {
                    if (!isEmailFormat) {
                        onResult(false, "Invalid email address.")
                        return@launch
                    }
                    if (account == null) {
                        onResult(false, "No account found with this email.")
                        return@launch
                    }
                    if (account.passwordHash != password) {
                        onResult(false, "Incorrect password.")
                        return@launch
                    }
                    if (account.role != "Admin") {
                        onResult(false, "Unauthorized access. Admin privileges required.")
                        return@launch
                    }
                }
            }

            // Successful Auth, update profile
            if (account != null) {
                val profile = UserProfile(
                    id = "current_user",
                    name = account.name,
                    email = account.email,
                    points = 50, // default initial points
                    badges = if (account.role == "Admin") "Legal Expert" else "",
                    role = account.role
                )
                dao.insertUserProfile(profile)
                sharedPrefs?.edit()?.putBoolean("is_logged_in", true)?.apply()
                _isLoggedIn.value = true
                onResult(true, "Welcome back, ${account.name}!")
            }
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
                val newAccount = UserAccount(
                    email = normalizedEmail,
                    name = name,
                    passwordHash = password,
                    role = role,
                    isApproved = (role != "Authority")
                )
                dao.insertUserAccount(newAccount)
                
                // Set initial profile
                val profile = UserProfile(
                    id = "current_user",
                    name = name,
                    email = normalizedEmail,
                    points = 25,
                    role = role
                )
                dao.insertUserProfile(profile)
                onResult(true, if (role == "Authority") "Registration submitted! Awaiting Admin approval." else "Registration successful as $role!")
            }
        }
    }

    // Logout
    fun logout() {
        sharedPrefs?.edit()?.putBoolean("is_logged_in", false)?.apply()
        _isLoggedIn.value = false
        viewModelScope.launch {
            dao.insertUserProfile(UserProfile(role = "Guest"))
        }
    }

    // Forgot Password
    fun forgotPassword(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalizedEmail = email.trim().lowercase()
            val account = dao.getUserAccount(normalizedEmail)
            if (account != null) {
                onResult(true, "Reset link simulated! Password is: ${account.passwordHash}")
            } else {
                onResult(false, "No account found with this email.")
            }
        }
    }

    // Reset Password
    fun resetPassword(email: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalizedEmail = email.trim().lowercase()
            val account = dao.getUserAccount(normalizedEmail)
            if (account != null) {
                dao.insertUserAccount(account.copy(passwordHash = newPass))
                onResult(true, "Password re-encrypted successfully.")
            } else {
                onResult(false, "Critical: Account not found.")
            }
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
    fun submitCitizenRequest(subject: String, details: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val reqId = "req_" + UUID.randomUUID().toString().take(6)
            val profile = _userProfile.value
            val newRequest = CitizenRequest(
                id = reqId,
                citizenName = profile.name,
                citizenEmail = profile.email,
                subject = subject,
                details = details,
                status = "Open"
            )
            dao.insertCitizenRequest(newRequest)
            
            // Points for participating
            dao.addPointsToUser(10)
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
            dao.deleteUserAccount(email)
        }
    }

    // Toggle Account Approval (Admin Action)
    fun toggleAccountApproval(email: String, approve: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                dao.insertUserAccount(account.copy(isApproved = approve))
            }
        }
    }

    // Toggle Account Disabled (Admin Action)
    fun toggleAccountDisabled(email: String, disable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = dao.getUserAccount(email)
            if (account != null) {
                dao.insertUserAccount(account.copy(isDisabled = disable))
            }
        }
    }

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

    private val _userProfile = MutableStateFlow<UserProfile>(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _forumPosts = MutableStateFlow<List<ForumPost>>(emptyList())
    val forumPosts: StateFlow<List<ForumPost>> = _forumPosts.asStateFlow()

    private val _feedbacks = MutableStateFlow<List<AiFeedback>>(emptyList())
    val feedbacks: StateFlow<List<AiFeedback>> = _feedbacks.asStateFlow()

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

    // Filter helpers
    private fun updateMyComplaints() {
        val email = _userProfile.value.email
        _myComplaints.value = _allComplaints.value.filter { it.reporterEmail == email }
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
        if (::db.isInitialized) return
        db = NyayaDatabase.getDatabase(context)
        dao = db.nyayaDao()
        
        sharedPrefs = context.getSharedPreferences("nyaya_prefs", Context.MODE_PRIVATE)
        val savedLogin = sharedPrefs?.getBoolean("is_logged_in", false) ?: false
        _isLoggedIn.value = savedLogin

        // Load language
        val savedLang = sharedPrefs?.getString("selected_language", "English") ?: "English"
        _currentLanguage.value = savedLang

        // Reactive profile updates
        viewModelScope.launch(Dispatchers.IO) {
            dao.getUserProfile().collect { profile ->
                if (profile != null) {
                    _userProfile.value = profile
                    updateMyComplaints()
                    updateMyNotifications()
                    
                    // Automatically manage milestones / badges based on points
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
        }

        // Reactive citizen complaints
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllComplaints().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultComplaints()
                } else {
                    _allComplaints.value = list
                    updateMyComplaints()
                }
            }
        }

        // Reactive notifications
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllNotifications().collect { list ->
                _allNotifications.value = list
                updateMyNotifications()
            }
        }

        // Reactive forum post updates
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllPosts().collect { posts ->
                if (posts.isEmpty()) {
                    seedInitialPosts()
                } else {
                    _forumPosts.value = posts
                }
            }
        }

        // Reactive AI Feedback updates
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllFeedback().collect { list ->
                _feedbacks.value = list
            }
        }

        // Reactive user accounts
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllUserAccounts().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultAccounts()
                } else {
                    _allUserAccounts.value = list
                    // Always guarantee demo credentials exist
                    if (list.none { it.email == "citizen@demo.com" }) {
                        dao.insertUserAccount(UserAccount("citizen@demo.com", "Demo Citizen", "Citizen@123", "Citizen"))
                    }
                    if (list.none { it.email == "police001@nyaya.ai" }) {
                        dao.insertUserAccount(UserAccount("police001@nyaya.ai", "Officer Demo", "Authority@123", "Authority", isApproved = true))
                    }
                    if (list.none { it.email == "admin@demo.com" }) {
                        dao.insertUserAccount(UserAccount("admin@demo.com", "Demo Admin", "Admin@123", "Admin"))
                    }
                }
            }
        }

        // Reactive incident reports
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllReports().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultReports()
                } else {
                    _allReports.value = list
                }
            }
        }

        // Reactive citizen requests
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllCitizenRequests().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultRequests()
                } else {
                    _allCitizenRequests.value = list
                }
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
        val accounts = listOf(
            UserAccount("citizen@nyaya.ai", "Aarav Sharma", "citizen123", "Citizen"),
            UserAccount("citizen@demo.com", "Demo Citizen", "Citizen@123", "Citizen"),
            UserAccount("disabled@nyaya.ai", "Disabled User", "citizen123", "Citizen", isDisabled = true),
            UserAccount("authority@nyaya.ai", "Inspector Verma", "police123", "Authority", isApproved = true),
            UserAccount("police001@nyaya.ai", "Officer Demo", "Authority@123", "Authority", isApproved = true),
            UserAccount("unapproved@nyaya.ai", "Officer Pending", "police123", "Authority", isApproved = false),
            UserAccount("admin@nyaya.ai", "Chief Officer Roy", "admin123", "Admin"),
            UserAccount("admin@demo.com", "Demo Admin", "Admin@123", "Admin")
        )
        accounts.forEach { dao.insertUserAccount(it) }
    }

    private suspend fun seedDefaultReports() {
        val reports = listOf(
            IncidentReport(
                id = "rep_1",
                reporterName = "Ananya Patel",
                reporterEmail = "ananya@example.com",
                title = "Attempted Cyber Phishing on Senior Citizen",
                description = "Received a WhatsApp message demanding electric bill payment with an APK file link. Blocked number and warned family.",
                category = "Cybercrime",
                status = "In Investigation",
                timestamp = System.currentTimeMillis() - 86400000 * 2,
                locationLat = 19.0760,
                locationLng = 72.8777,
                authorityNotes = "Checking domain hosting and IP ranges of malicious link."
            ),
            IncidentReport(
                id = "rep_2",
                reporterName = "Rohan Das",
                reporterEmail = "rohan@example.com",
                title = "Reckless Driving & Traffic Obstruction near Market",
                description = "Two commercial delivery vehicles blocking critical lanes on Sector-5 main avenue.",
                category = "Traffic",
                status = "Resolved",
                timestamp = System.currentTimeMillis() - 86400000,
                locationLat = 28.6139,
                locationLng = 77.2090,
                authorityNotes = "Traffic wardens deployed. Vehicles towed and fines issued under Section 122 MVA."
            )
        )
        reports.forEach { dao.insertReport(it) }
    }

    private suspend fun seedDefaultRequests() {
        val requests = listOf(
            CitizenRequest(
                id = "req_1",
                citizenName = "Vikram Singh",
                citizenEmail = "vikram@example.com",
                subject = "Right to Information (RTI) query delay",
                details = "I submitted an RTI application on municipal expenditure 45 days ago but haven't received a response. What is the direct escalation process?",
                status = "Answered",
                reply = "According to Section 19(1) of the RTI Act, 2005, you should immediately file a First Appeal with the designated First Appellate Authority of that public department. If still unanswered, approach the Central/State Information Commission."
            )
        )
        requests.forEach { dao.insertCitizenRequest(it) }
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
            
            // Earn points: +10 for replying/commenting
            dao.addPointsToUser(10)
        }
    }

    fun toggleLikePost(post: ForumPost) {
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlyLiked = post.isLikedByMe
            val change = if (isCurrentlyLiked) -1 else 1
            dao.updatePostLike(post.id, change, !isCurrentlyLiked)
            
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
            val updated = _userProfile.value.copy(name = newName, email = newEmail)
            dao.insertUserProfile(updated)
        }
    }

    // Load legal database from assets using Android's native JSONObject & JSONArray
    fun loadLegalDatabase(context: Context) {
        initDatabase(context)
        if (_legalTopics.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("legal_knowledge_base.json").bufferedReader().use { it.readText() }
                val topics = mutableListOf<LegalTopic>()
                val jsonArray = JSONArray(jsonString)
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    
                    val keywords = mutableListOf<String>()
                    val keywordsArr = obj.optJSONArray("keywords")
                    if (keywordsArr != null) {
                        for (j in 0 until keywordsArr.length()) {
                            keywords.add(keywordsArr.getString(j))
                        }
                    }
                    
                    val nextSteps = mutableListOf<String>()
                    val nextStepsArr = obj.optJSONArray("next_steps")
                    if (nextStepsArr != null) {
                        for (j in 0 until nextStepsArr.length()) {
                            nextSteps.add(nextStepsArr.getString(j))
                        }
                    }
                    
                    topics.add(
                        LegalTopic(
                            id = obj.getInt("id"),
                            title = obj.getString("title"),
                            category = obj.getString("category"),
                            keywords = keywords,
                            summary = obj.getString("summary"),
                            next_steps = nextSteps,
                            official_authority = obj.getString("official_authority"),
                            official_source = obj.getString("official_source"),
                            official_source_url = obj.getString("official_source_url")
                        )
                    )
                }
                
                _legalTopics.value = topics
                
                // Add initial warm welcome message from AI
                _chatHistory.value = listOf(
                    ChatMessage(
                        sender = "ai",
                        text = "Namaste! I am NyayaAI, your automated legal assistant. I can help you understand laws, employee rights, consumer protection, women's safety, cybercrime provisions, and traffic regulations in India.\n\nType your query below, or click on any of the suggested prompts to get started!",
                        isWarningNotLocal = false
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
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
        if (text.isBlank()) return
        
        // Add User Message
        val userMsg = ChatMessage(sender = "user", text = text)
        _chatHistory.value = _chatHistory.value + userMsg
        
        _isLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            // RAG step: Search relevant topics from our local JSON
            val matchedTopic = findMostRelevantTopic(text)
            
            // Build the prompt context
            val promptBuilder = StringBuilder()
            if (matchedTopic != null) {
                promptBuilder.append("AUTHORIZED LOCAL LEGAL CONTEXT:\n")
                promptBuilder.append("Title: ${matchedTopic.title}\n")
                promptBuilder.append("Category: ${matchedTopic.category}\n")
                promptBuilder.append("Summary of Law: ${matchedTopic.summary}\n")
                promptBuilder.append("Official Authority: ${matchedTopic.official_authority}\n")
                promptBuilder.append("Applicable Act/Source: ${matchedTopic.official_source}\n")
                promptBuilder.append("Official Source URL: ${matchedTopic.official_source_url}\n")
                if (matchedTopic.next_steps.isNotEmpty()) {
                    promptBuilder.append("Next Steps for Citizen:\n")
                    matchedTopic.next_steps.forEach { step ->
                        promptBuilder.append("- $step\n")
                    }
                }
                promptBuilder.append("\nINSTRUCTIONS:\n")
                promptBuilder.append("1. Answer the user's question directly and concisely using ONLY the provided local legal context.\n")
                promptBuilder.append("2. Format the response beautifully and professionally. Present key elements in clean bullet points, emphasizing citizen rights and required next steps.\n")
                promptBuilder.append("3. Under your answer, strictly append a dedicated section called 'REMEDY DETAILS' detailing:\n")
                promptBuilder.append("   - ⚖️ Applicable Act: ${matchedTopic.official_source}\n")
                promptBuilder.append("   - 🏢 Authority: ${matchedTopic.official_authority}\n")
                promptBuilder.append("   - 🌐 Source URL: ${matchedTopic.official_source_url}\n")
                promptBuilder.append("4. Do NOT say 'relying on context' or mention internal prompt terms.\n")
            } else {
                promptBuilder.append("NO MATCHING LOCAL CONTEXT FOUND IN THE DATABASE.\n")
                promptBuilder.append("INSTRUCTIONS:\n")
                promptBuilder.append("1. Answer the user's question using your general knowledge about Indian law.\n")
                promptBuilder.append("2. Since this is outside our local JSON database, you MUST strictly start your response with this exact warning notice at the very beginning of your reply:\n")
                promptBuilder.append("   '⚠️ Note: This information is not sourced from the local legal database.'\n")
                promptBuilder.append("3. After the notice, give a helpful, high-quality, general explanation of the law or procedure in India.\n")
            }
            
            promptBuilder.append("\nUser Question: $text\n")

            val systemInstruction = "You are NyayaAI, a premium, production-ready legal assistance AI designed to help Indian citizens understand their rights, procedures, and remedies. You represent the finest craftsmanship in conversational AI, offering supportive, clear, accurate, and completely reliable legal guidance in simple English. You MUST follow user instructions regarding context matches and warning notices strictly to ensure 100% compliance with zero hallucinations."

            try {
                // Call raw client
                val aiResponseText = GeminiApiClient.generateContent(
                    prompt = promptBuilder.toString(),
                    systemInstruction = systemInstruction
                )
                
                val cleanResponse = aiResponseText.trim()
                val isWarning = cleanResponse.startsWith("⚠️ Note:") || matchedTopic == null

                val aiMsg = ChatMessage(
                    sender = "ai",
                    text = cleanResponse,
                    sourceTitle = matchedTopic?.title,
                    sourceUrl = matchedTopic?.official_source_url,
                    isWarningNotLocal = isWarning
                )
                
                _chatHistory.value = _chatHistory.value + aiMsg
            } catch (e: Exception) {
                e.printStackTrace()
                _chatHistory.value = _chatHistory.value + ChatMessage(
                    sender = "ai",
                    text = "Error: Could not retrieve a response. Please check your Gemini API Key in the Secrets panel."
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Helper to find the most relevant local JSON topic based on keyword overlap
    private fun findMostRelevantTopic(query: String): LegalTopic? {
        val cleanQuery = query.lowercase()
        var bestMatch: LegalTopic? = null
        var maxScore = 0

        _legalTopics.value.forEach { topic ->
            var score = 0
            
            // Check direct match in title
            if (cleanQuery.contains(topic.title.lowercase())) {
                score += 15
            }
            
            // Check category match
            if (cleanQuery.contains(topic.category.lowercase())) {
                score += 5
            }
            
            // Count matching keywords
            topic.keywords.forEach { keyword ->
                if (cleanQuery.contains(keyword.lowercase())) {
                    score += 8
                }
            }
            
            // Check context summary
            val summaryWords = topic.summary.lowercase().split(" ", ",", ".", "-")
            val queryWords = cleanQuery.split(" ", ",", ".", "-")
            val commonWords = summaryWords.intersect(queryWords.toSet())
            score += commonWords.size
            
            if (score > maxScore && score >= 4) {
                maxScore = score
                bestMatch = topic
            }
        }
        
        return bestMatch
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

    // Add custom legal topic
    fun addCustomLegalTopic(title: String, category: String, summary: String, officialAuthority: String, officialSource: String, officialSourceUrl: String) {
        val currentList = _legalTopics.value.toMutableList()
        val nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        val newTopic = LegalTopic(
            id = nextId,
            title = title,
            category = category,
            keywords = listOf(title.lowercase(), category.lowercase()),
            summary = summary,
            next_steps = listOf("Check official government website", "Seek legal advice from a registered professional"),
            official_authority = officialAuthority,
            official_source = officialSource,
            official_source_url = officialSourceUrl
        )
        currentList.add(newTopic)
        _legalTopics.value = currentList
    }

    // Select Language
    fun selectLanguage(language: String) {
        _currentLanguage.value = language
        sharedPrefs?.edit()?.putString("selected_language", language)?.apply()
    }

    // Submit Citizen Complaint
    fun submitComplaint(
        title: String,
        category: String,
        description: String,
        state: String,
        district: String,
        address: String,
        imageUri: String?,
        isAnonymous: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val complaintId = "CMP-" + UUID.randomUUID().toString().uppercase().take(6)
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

            val newComplaint = CitizenComplaint(
                id = complaintId,
                title = title,
                description = description,
                category = category,
                state = state,
                district = district,
                address = address,
                imageUri = imageUri,
                timestamp = System.currentTimeMillis(),
                isAnonymous = isAnonymous,
                reporterName = if (isAnonymous) "Anonymous" else profile.name,
                reporterEmail = profile.email,
                status = "Submitted",
                aiPredictedDepartment = predictedDept,
                priority = priority,
                assignedOfficer = "",
                authorityRemarks = ""
            )
            
            dao.insertComplaint(newComplaint)
            
            // Notification for citizen
            createNotification(
                userEmail = profile.email,
                title = "Complaint Submitted Successfully",
                message = "Your complaint '$title' (ID: $complaintId) has been filed and automatically classified into '$predictedDept' department.",
                isAuthority = false,
                isHighPriority = (priority == "Critical")
            )
            
            // Notification for authority
            createNotification(
                userEmail = "all",
                title = "New Complaint Received",
                message = "A new $priority-priority complaint regarding '$title' has been received in $district district.",
                isAuthority = true,
                isHighPriority = (priority == "Critical")
            )
            
            // Reward points
            dao.addPointsToUser(30)
            
            onResult(true, "Complaint submitted successfully! Classified under '$predictedDept'.")
        }
    }

    // Update Complaint (Citizen edit before review starts)
    fun updateComplaint(
        complaintId: String,
        title: String,
        category: String,
        description: String,
        state: String,
        district: String,
        address: String,
        imageUri: String?,
        isAnonymous: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getComplaintById(complaintId)
            if (existing == null) {
                onResult(false, "Complaint not found.")
                return@launch
            }
            if (existing.status != "Submitted") {
                onResult(false, "Cannot edit complaint after review has started.")
                return@launch
            }
            
            val predictedDept = classifyComplaintAI(description)
            val updated = existing.copy(
                title = title,
                category = category,
                description = description,
                state = state,
                district = district,
                address = address,
                imageUri = imageUri,
                isAnonymous = isAnonymous,
                reporterName = if (isAnonymous) "Anonymous" else _userProfile.value.name,
                aiPredictedDepartment = predictedDept
            )
            dao.insertComplaint(updated)
            
            createNotification(
                userEmail = _userProfile.value.email,
                title = "Complaint Updated",
                message = "Your complaint '$title' (ID: $complaintId) has been updated successfully.",
                isAuthority = false
            )
            onResult(true, "Complaint updated successfully.")
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
            if (existing.status != "Submitted") {
                onResult(false, "Cannot delete complaint after review has started.")
                return@launch
            }
            
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
        status: String, // "Under Review", "Assigned", "In Progress", "Resolved", "Rejected"
        assignedOfficer: String,
        remarks: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getComplaintById(complaintId)
            if (existing == null) {
                onResult(false, "Complaint not found.")
                return@launch
            }
            
            val updated = existing.copy(
                status = status,
                assignedOfficer = assignedOfficer,
                authorityRemarks = remarks
            )
            dao.insertComplaint(updated)
            
            // Notify citizen
            createNotification(
                userEmail = existing.reporterEmail,
                title = "Complaint Status Updated",
                message = "Your complaint '${existing.title}' (ID: $complaintId) is now marked as '$status'. Remarks: $remarks",
                isAuthority = false,
                isHighPriority = (status == "Rejected")
            )
            
            // Milestone notifications
            if (status == "Assigned" && assignedOfficer.isNotEmpty()) {
                createNotification(
                    userEmail = existing.reporterEmail,
                    title = "Officer Assigned",
                    message = "Officer '$assignedOfficer' has been assigned to investigate your complaint.",
                    isAuthority = false
                )
            } else if (status == "Resolved") {
                createNotification(
                    userEmail = existing.reporterEmail,
                    title = "Complaint Resolved Successfully",
                    message = "Your complaint '${existing.title}' has been marked as resolved! Thank you for your civic participation.",
                    isAuthority = false
                )
            } else if (status == "Rejected") {
                createNotification(
                    userEmail = existing.reporterEmail,
                    title = "Complaint Rejected",
                    message = "Your complaint '${existing.title}' has been rejected. Reason: $remarks",
                    isAuthority = false,
                    isHighPriority = true
                )
            }
            
            onResult(true, "Complaint status successfully updated to '$status'.")
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

            // Sync complaint's status and update authority remarks
            val updatedComplaint = existing.copy(
                status = updatedStatus,
                authorityRemarks = message
            )
            dao.insertComplaint(updatedComplaint)

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

    // Mock Seeding of Complaints
    private suspend fun seedDefaultComplaints() {
        val defaultComplaints = listOf(
            CitizenComplaint(
                id = "CMP-873911",
                title = "Severe Potholes on Main Bazar Road",
                description = "The main bazaar road has multiple huge potholes causing severe traffic jams and accidents. It is very dangerous for two-wheelers.",
                category = "Municipality",
                state = "Delhi",
                district = "New Delhi",
                address = "Main Bazar Road, Near Metro Pillar 42",
                imageUri = null,
                timestamp = System.currentTimeMillis() - 86400000 * 3, // 3 days ago
                isAnonymous = false,
                reporterName = "Aarav Sharma",
                reporterEmail = "citizen@nyaya.ai",
                status = "In Progress",
                aiPredictedDepartment = "Municipality",
                priority = "High",
                assignedOfficer = "Inspector Rajesh Kumar",
                authorityRemarks = "Enquiry ordered, road patching scheduled for Monday."
            ),
            CitizenComplaint(
                id = "CMP-492048",
                title = "Suspicious Phishing Call on WhatsApp",
                description = "Received a WhatsApp call from an unknown international number (+1-809...) claiming to be lottery support and demanding bank login OTP.",
                category = "Cyber Crime",
                state = "Delhi",
                district = "Central Delhi",
                address = "Connaught Place, New Delhi",
                imageUri = null,
                timestamp = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
                isAnonymous = true,
                reporterName = "Anonymous",
                reporterEmail = "citizen@demo.com",
                status = "Resolved",
                aiPredictedDepartment = "Cyber Crime",
                priority = "Medium",
                assignedOfficer = "Cyber Analyst Priya Sen",
                authorityRemarks = "The phone number has been flagged and submitted to telecom authority for blocking."
            ),
            CitizenComplaint(
                id = "CMP-104928",
                title = "Unregulated Street Garbage Dumps",
                description = "Garbage is being dumped openly at the corner of Sector 4. The odor is intolerable and is breeding mosquitoes.",
                category = "Municipality",
                state = "Delhi",
                district = "South Delhi",
                address = "Sector 4 Park Corner",
                imageUri = null,
                timestamp = System.currentTimeMillis() - 3600000 * 4, // 4 hours ago
                isAnonymous = false,
                reporterName = "Demo Citizen",
                reporterEmail = "citizen@demo.com",
                status = "Submitted",
                aiPredictedDepartment = "Municipality",
                priority = "Medium",
                assignedOfficer = "",
                authorityRemarks = ""
            ),
            CitizenComplaint(
                id = "CMP-302918",
                title = "Water Leakage from Main Supply Pipe",
                description = "Main drinking water pipe cracked and thousands of gallons of clean water is getting wasted on the road.",
                category = "Water Supply",
                state = "Delhi",
                district = "North Delhi",
                address = "Gali No 3, Shakti Nagar",
                imageUri = null,
                timestamp = System.currentTimeMillis() - 3600000 * 1, // 1 hour ago
                isAnonymous = false,
                reporterName = "Demo Citizen",
                reporterEmail = "citizen@demo.com",
                status = "Under Review",
                aiPredictedDepartment = "Water Supply",
                priority = "High",
                assignedOfficer = "",
                authorityRemarks = ""
            )
        )
        defaultComplaints.forEach { dao.insertComplaint(it) }
    }
}
