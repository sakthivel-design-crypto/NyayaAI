package com.example.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

import com.example.model.LawRecord

// User Profile Entity
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String = "current_user",
    val name: String = "Citizen Defender",
    val email: String = "user@nyaya.ai",
    val phone: String = "",
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
    val department: String = "",
    val district: String = "",
    val contact: String = "",
    val performanceScore: Int = 0,
    val uid: String = "",
    val phone: String = "",
    val status: String = "Active", // "Active", "PENDING_APPROVAL", "Disabled", "Rejected"
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val device: String = "Android Device",
    val onlineStatus: String = "Offline",
    val designation: String = "",
    val profilePhoto: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val employeeId: String = "",
    val approvalStatus: String = "APPROVED", // "PENDING_VERIFICATION", "APPROVED", "REJECTED"
    val approvedAt: Long = 0L,
    val authProvider: String = "",
    val verificationStatus: String = "",
    val lastVerifiedAt: Long = 0L,
    val proofImage: String = "",
    val rejectionReason: String = ""
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
    val citizenId: String = "",
    val citizenName: String,
    val citizenEmail: String,
    val subject: String,
    val details: String,
    val status: String = "Open", // "Open", "Answered"
    val reply: String = "",
    val officerName: String = "",
    val officerDepartment: String = "",
    val officerDesignation: String = "",
    val officerId: String = "",
    val respondedAt: Long = 0L,
    val edited: Boolean = false,
    val editedAt: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis(),
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

// App Rating Entity (1 rating per user in ratings/{uid})
@Entity(tableName = "app_ratings")
data class AppRating(
    @PrimaryKey val uid: String,
    val role: String = "Citizen",
    val userName: String = "",
    val rating: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// User Feedback Entity (Collection: feedback)
@Entity(tableName = "user_feedbacks")
data class UserFeedback(
    @PrimaryKey val feedbackId: String,
    val uid: String = "",
    val userName: String = "",
    val email: String = "",
    val role: String = "Citizen",
    val category: String = "General",
    val subject: String = "",
    val message: String = "",
    val rating: Int = 0,
    val attachmentUrl: String = "",
    val status: String = "Pending", // "Pending", "Reviewed", "Resolved"
    val adminReply: String = "",
    val adminName: String = "",
    val department: String = "",
    val repliedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Citizen Complaint Entity
@Entity(tableName = "citizen_complaints")
data class CitizenComplaint(
    @PrimaryKey val id: String, // complaintId
    val citizenId: String = "",
    val title: String,
    val description: String,
    val category: String, // e.g. "Police", "Cyber Crime", "Women Safety", etc.
    val state: String,
    val district: String,
    val address: String,
    val imageUri: String? = null, // photoUrl
    val timestamp: Long = System.currentTimeMillis(),
    val isAnonymous: Boolean = false,
    val reporterName: String = "Anonymous", // citizenName
    val reporterEmail: String = "", // citizenEmail
    val citizenPhone: String = "Phone number not provided",
    val status: String = "Submitted", // "Submitted", "Under Review", "Assigned", "In Investigation", "Resolved", "Closed", "Rejected"
    val aiPredictedDepartment: String = "", // department
    val priority: String = "Medium", // "Low", "Medium", "High", "Critical"
    val assignedOfficer: String = "",
    val authorityRemarks: String = "", // authorityNotes
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0L,
    val lastModifiedBy: String = "System",
    val photoFileName: String = "evidence.jpg",
    val timeline: String = "[]", // JSON representation of timeline events
    val notificationHistory: String = "[]" // JSON representation of notifications
) {
    @get:Ignore val complaintId: String get() = id
    @get:Ignore val citizenName: String get() = reporterName
    @get:Ignore val citizenEmail: String get() = reporterEmail
    @get:Ignore val department: String get() = aiPredictedDepartment.ifEmpty { category }
    @get:Ignore val anonymous: Boolean get() = isAnonymous
    @get:Ignore val photoUrl: String? get() = imageUri
    @get:Ignore val authorityNotes: String get() = authorityRemarks
}

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

// Authority Audit Log Entity
@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val complaintId: String,
    val officerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val previousStatus: String,
    val newStatus: String,
    val notes: String
)

// System Activity Log Entity
@Entity(tableName = "system_activity_logs")
data class ActivityLog(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val actorRole: String = "Citizen", // "Citizen", "Authority", "Admin", "System"
    val actorName: String = "",
    val eventType: String = "",
    val message: String = "",
    val relatedId: String = ""
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

// Emergency Contact Entity for Pre-configured Emergency Contacts
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relationship: String = "Family",
    val isPrimary: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// Real-Time Complaint Conversation Message Entity
@Entity(tableName = "complaint_messages")
data class ComplaintMessage(
    @PrimaryKey val messageId: String = java.util.UUID.randomUUID().toString(),
    val complaintId: String,
    val senderId: String = "",
    val receiverId: String = "",
    val senderRole: String = "CITIZEN", // "CITIZEN", "AUTHORITY", "SYSTEM"
    val receiverRole: String = "AUTHORITY",
    val senderName: String = "",
    val message: String = "",
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "DOCUMENT", "LOCATION", "VOICE", "SYSTEM"
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long = 0L,
    val isRead: Boolean = false,
    val deliveryStatus: String = "SENT", // "SENT", "DELIVERED", "READ"
    val deleted: Boolean = false,
    val attachmentUrl: String? = null,
    val voiceUrl: String? = null,
    val documentUrl: String? = null,
    val locationData: String? = null,
    val metadata: String = "{}"
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

    @Query("SELECT * FROM user_accounts WHERE uid = :uid LIMIT 1")
    suspend fun getUserAccountByUid(uid: String): UserAccount?

    @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:identity) OR (employeeId != '' AND LOWER(employeeId) = LOWER(:identity)) LIMIT 1")
    suspend fun getUserAccountByIdentity(identity: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccount(account: UserAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccounts(accounts: List<UserAccount>)

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

    // App Ratings (1 rating per user uid)
    @Query("SELECT * FROM app_ratings ORDER BY updatedAt DESC")
    fun getAllAppRatings(): Flow<List<AppRating>>

    @Query("SELECT * FROM app_ratings WHERE uid = :uid LIMIT 1")
    suspend fun getAppRatingByUid(uid: String): AppRating?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppRating(rating: AppRating)

    // User Feedbacks
    @Query("SELECT * FROM user_feedbacks ORDER BY createdAt DESC")
    fun getAllUserFeedbacks(): Flow<List<UserFeedback>>

    @Query("SELECT * FROM user_feedbacks WHERE email = :email OR uid = :uid ORDER BY createdAt DESC")
    fun getUserFeedbacksForUser(email: String, uid: String): Flow<List<UserFeedback>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserFeedback(feedback: UserFeedback)

    @Query("UPDATE user_feedbacks SET adminReply = :reply, adminName = :adminName, department = :department, status = :status, repliedAt = :repliedAt, updatedAt = :updatedAt WHERE feedbackId = :feedbackId")
    suspend fun updateUserFeedbackReply(feedbackId: String, adminName: String, department: String, reply: String, status: String, repliedAt: Long, updatedAt: Long)

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

    // Emergency Contacts
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, timestamp ASC")
    fun getAllEmergencyContacts(): Flow<List<EmergencyContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyContact(contact: EmergencyContact)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteEmergencyContact(id: String)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE complaintId = :complaintId ORDER BY timestamp DESC")
    fun getAuditLogsForComplaint(complaintId: String): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)

    // Activity Logs
    @Query("SELECT * FROM system_activity_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLogs(logs: List<ActivityLog>)

    // Complaint Messages (Real-time Conversation System)
    @Query("SELECT * FROM complaint_messages WHERE complaintId = :complaintId AND deleted = 0 ORDER BY createdAt ASC")
    fun getMessagesForComplaint(complaintId: String): Flow<List<ComplaintMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaintMessage(message: ComplaintMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaintMessages(messages: List<ComplaintMessage>)

    @Query("UPDATE complaint_messages SET isRead = 1 WHERE complaintId = :complaintId AND senderRole != :myRole")
    suspend fun markComplaintMessagesRead(complaintId: String, myRole: String)

    // Legal Knowledge Base Dataset (Centralized Laws)
    @Query("SELECT * FROM laws ORDER BY updatedAt DESC")
    fun getAllLaws(): Flow<List<LawRecord>>

    @Query("SELECT * FROM laws WHERE status = 'active' ORDER BY updatedAt DESC")
    fun getActiveLaws(): Flow<List<LawRecord>>

    @Query("SELECT * FROM laws WHERE lawId = :id LIMIT 1")
    suspend fun getLawById(id: String): LawRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaw(law: LawRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaws(laws: List<LawRecord>)

    @Query("DELETE FROM laws WHERE lawId = :id")
    suspend fun deleteLawById(id: String)
}

val MIGRATION_14_17 = object : Migration(14, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `laws`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `laws` (
                `lawId` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `reference` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `lastUpdatedBy` TEXT NOT NULL,
                `officialAuthority` TEXT NOT NULL,
                `officialSourceUrl` TEXT NOT NULL,
                `keywords` TEXT NOT NULL,
                PRIMARY KEY(`lawId`)
            )
        """.trimIndent())
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `laws`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `laws` (
                `lawId` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `reference` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `lastUpdatedBy` TEXT NOT NULL,
                `officialAuthority` TEXT NOT NULL,
                `officialSourceUrl` TEXT NOT NULL,
                `keywords` TEXT NOT NULL,
                PRIMARY KEY(`lawId`)
            )
        """.trimIndent())
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

val MIGRATION_15_17 = object : Migration(15, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {}
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
        ComplaintReply::class,
        EmergencyContact::class,
        AuditLog::class,
        ComplaintMessage::class,
        LawRecord::class,
        AppRating::class,
        UserFeedback::class,
        ActivityLog::class
    ],
    version = 22,
    exportSchema = false
)
abstract class NyayaDatabase : RoomDatabase() {
    abstract fun nyayaDao(): NyayaDao

    companion object {
        @Volatile
        private var INSTANCE: NyayaDatabase? = null

        fun getDatabase(context: Context): NyayaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun resetDatabase(context: Context): NyayaDatabase {
            return synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Exception) {}
                INSTANCE = null
                try {
                    context.applicationContext.deleteDatabase("nyaya_database")
                } catch (_: Exception) {}
                buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): NyayaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NyayaDatabase::class.java,
                "nyaya_database"
            )
            .addMigrations(MIGRATION_14_17, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_15_17)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        }
    }
}
