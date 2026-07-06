package com.example.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// User Profile Entity
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String = "current_user",
    val name: String = "Citizen Defender",
    val email: String = "user@nyaya.ai",
    val points: Int = 0,
    val badges: String = "", // Comma-separated badge names: "Community Helper, Top Contributor"
    val role: String = "Citizen"
)

// User Account for Login/Register
@Entity(tableName = "user_accounts")
data class UserAccount(
    @PrimaryKey val email: String,
    val name: String,
    val passwordHash: String,
    val role: String, // "Citizen", "Authority", "Admin"
    val isDisabled: Boolean = false,
    val isApproved: Boolean = true,
    val department: String = "Police",
    val district: String = "New Delhi",
    val contact: String = "+91 98765 43210",
    val performanceScore: Int = 85
)

// Incident Reports (Authority and Admin View)
@Entity(tableName = "incident_reports")
data class IncidentReport(
    @PrimaryKey val id: String,
    val reporterName: String,
    val reporterEmail: String,
    val title: String,
    val description: String,
    val category: String, // "Civil", "Criminal", "Harassment", "Cybercrime", "Traffic", "SOS Alert"
    val status: String = "Pending", // "Pending", "In Investigation", "Resolved"
    val timestamp: Long = System.currentTimeMillis(),
    val locationLat: Double = 0.0,
    val locationLng: Double = 0.0,
    val authorityNotes: String = ""
)

// Citizen Request for Legal Assistance (Authority & Admin View)
@Entity(tableName = "citizen_requests")
data class CitizenRequest(
    @PrimaryKey val id: String,
    val citizenName: String,
    val citizenEmail: String,
    val subject: String,
    val details: String,
    val status: String = "Open", // "Open", "Under Review", "Answered"
    val reply: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// Forum Post Entity
@Entity(tableName = "forum_posts")
data class ForumPost(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val postType: String, // "Question", "Resource", "Discussion"
    val authorName: String,
    val authorRole: String = "Citizen",
    val upvotes: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isLikedByMe: Boolean = false
)

// Forum Comment Entity
@Entity(tableName = "forum_comments")
data class ForumComment(
    @PrimaryKey val id: String,
    val postId: String,
    val content: String,
    val authorName: String,
    val authorRole: String = "Citizen",
    val timestamp: Long = System.currentTimeMillis()
)

// AI Feedback Entity
@Entity(tableName = "ai_feedback")
data class AiFeedback(
    @PrimaryKey val id: String,
    val query: String,
    val response: String,
    val isHelpful: Boolean, // true for thumbs-up, false for thumbs-down
    val starRating: Int, // 1 to 5 stars
    val textFeedback: String?,
    val timestamp: Long = System.currentTimeMillis()
)

// Citizen Complaint Entity
@Entity(tableName = "citizen_complaints")
data class CitizenComplaint(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String, // e.g. "Police", "Cyber Crime", "Women Safety", etc.
    val state: String,
    val district: String,
    val address: String,
    val imageUri: String?, // Image path (base64 or local URI)
    val timestamp: Long = System.currentTimeMillis(),
    val isAnonymous: Boolean = false,
    val reporterName: String,
    val reporterEmail: String,
    val status: String = "Submitted", // "Submitted", "Under Review", "Assigned", "In Progress", "Resolved", "Rejected"
    val aiPredictedDepartment: String = "", // Predicted department
    val priority: String = "Medium", // "Low", "Medium", "High", "Critical"
    val assignedOfficer: String = "",
    val authorityRemarks: String = ""
)

// Notification Entity
@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val userEmail: String, // "all" for authority / admin, or specific user email
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isAuthority: Boolean = false,
    val isHighPriority: Boolean = false
)

// Complaint Reply Entity for Ticketing System
@Entity(tableName = "complaint_replies")
data class ComplaintReply(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val complaintId: String,
    val authorityName: String,
    val department: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val updatedStatus: String,
    val attachmentPath: String? = null
)

// Data Access Object (DAO)
@Dao
interface NyayaDao {
    // User Profile
    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun getUserProfile(id: String = "current_user"): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("UPDATE user_profiles SET points = points + :addPoints WHERE id = :id")
    suspend fun addPointsToUser(addPoints: Int, id: String = "current_user")

    @Query("UPDATE user_profiles SET badges = :newBadges WHERE id = :id")
    suspend fun updateBadges(newBadges: String, id: String = "current_user")

    // User Accounts (Auth)
    @Query("SELECT * FROM user_accounts WHERE email = :email")
    suspend fun getUserAccount(email: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccount(account: UserAccount)

    @Query("SELECT * FROM user_accounts")
    fun getAllUserAccounts(): Flow<List<UserAccount>>

    @Query("DELETE FROM user_accounts WHERE email = :email")
    suspend fun deleteUserAccount(email: String)

    // Incident Reports
    @Query("SELECT * FROM incident_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<IncidentReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: IncidentReport)

    @Query("UPDATE incident_reports SET status = :status, authorityNotes = :notes WHERE id = :reportId")
    suspend fun updateReportStatus(reportId: String, status: String, notes: String)

    // Citizen Requests
    @Query("SELECT * FROM citizen_requests ORDER BY timestamp DESC")
    fun getAllCitizenRequests(): Flow<List<CitizenRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCitizenRequest(request: CitizenRequest)

    @Query("UPDATE citizen_requests SET reply = :reply, status = :status WHERE id = :requestId")
    suspend fun updateCitizenRequestReply(requestId: String, reply: String, status: String)

    // Forum Posts
    @Query("SELECT * FROM forum_posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<ForumPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: ForumPost)

    @Query("UPDATE forum_posts SET upvotes = upvotes + :change, isLikedByMe = :isLiked WHERE id = :postId")
    suspend fun updatePostLike(postId: String, change: Int, isLiked: Boolean)

    // Forum Comments
    @Query("SELECT * FROM forum_comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getCommentsForPost(postId: String): Flow<List<ForumComment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: ForumComment)

    // AI Feedback
    @Query("SELECT * FROM ai_feedback ORDER BY timestamp DESC")
    fun getAllFeedback(): Flow<List<AiFeedback>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: AiFeedback)

    // Citizen Complaints
    @Query("SELECT * FROM citizen_complaints ORDER BY timestamp DESC")
    fun getAllComplaints(): Flow<List<CitizenComplaint>>

    @Query("SELECT * FROM citizen_complaints WHERE reporterEmail = :email ORDER BY timestamp DESC")
    fun getComplaintsByReporter(email: String): Flow<List<CitizenComplaint>>

    @Query("SELECT * FROM citizen_complaints WHERE id = :id LIMIT 1")
    suspend fun getComplaintById(id: String): CitizenComplaint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: CitizenComplaint)

    @Query("DELETE FROM citizen_complaints WHERE id = :id")
    suspend fun deleteComplaintById(id: String)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<Notification>>

    @Query("SELECT * FROM notifications WHERE userEmail = :email OR userEmail = 'all' ORDER BY timestamp DESC")
    fun getNotificationsForUser(email: String): Flow<List<Notification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userEmail = :email OR userEmail = 'all'")
    suspend fun markAllNotificationsAsReadForUser(email: String)

    // Complaint Replies
    @Query("SELECT * FROM complaint_replies WHERE complaintId = :complaintId ORDER BY timestamp ASC")
    fun getRepliesForComplaint(complaintId: String): Flow<List<ComplaintReply>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaintReply(reply: ComplaintReply)
}

// Database definition
@Database(
    entities = [
        UserProfile::class, 
        ForumPost::class, 
        ForumComment::class, 
        AiFeedback::class,
        UserAccount::class,
        IncidentReport::class,
        CitizenRequest::class,
        CitizenComplaint::class,
        Notification::class,
        ComplaintReply::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NyayaDatabase : RoomDatabase() {
    abstract fun nyayaDao(): NyayaDao

    companion object {
        @Volatile
        private var INSTANCE: NyayaDatabase? = null

        fun getDatabase(context: Context): NyayaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NyayaDatabase::class.java,
                    "nyaya_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
