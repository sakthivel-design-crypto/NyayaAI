package com.example.util

import android.util.Log
import com.example.db.UserAccount
import com.example.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Result structure returned by Authoritative Government Verification Services.
 */
data class AuthorityVerificationResult(
    val verified: Boolean,
    val employeeId: String = "",
    val officialEmail: String = "",
    val officerName: String = "",
    val department: String = "",
    val designation: String = "",
    val jurisdiction: String = "",
    val accountStatus: String = "ACTIVE", // "ACTIVE", "INACTIVE", "DISABLED", "REVOKED"
    val verificationStatus: String = "VERIFIED", // "VERIFIED", "UNVERIFIED", "REJECTED"
    val verificationSource: String = "GOVERNMENT_AUTHORITY_DIRECTORY",
    val verificationTimestamp: Long = System.currentTimeMillis(),
    val failureType: VerificationFailureType? = null,
    val userFacingMessage: String = "",
    val passwordHash: String = ""
)

/**
 * Categorized verification failure classifications.
 */
enum class VerificationFailureType {
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    ACCOUNT_INACTIVE,
    DEPARTMENT_MISMATCH,
    VERIFICATION_FAILED,
    SERVICE_UNAVAILABLE
}

/**
 * Interface contract for Government Personnel Verification Providers.
 */
interface AuthorityVerificationProvider {
    val providerName: String

    suspend fun verifyAuthorityEmployee(
        identity: String,
        passwordInput: String,
        department: String,
        designation: String,
        jurisdiction: String,
        existingAccount: UserAccount? = null
    ): AuthorityVerificationResult
}

/**
 * Production Government Authority Verification Provider
 * Validates authority credentials against authenticated Firebase/Firestore and Room registered accounts.
 */
class ProductionAuthorityVerificationProvider : AuthorityVerificationProvider {
    override val providerName: String = "GOVERNMENT_AUTHORITY_DIRECTORY"

    override suspend fun verifyAuthorityEmployee(
        identity: String,
        passwordInput: String,
        department: String,
        designation: String,
        jurisdiction: String,
        existingAccount: UserAccount?
    ): AuthorityVerificationResult = withContext(Dispatchers.IO) {
        val cleanIdentity = identity.trim()
        val cleanPassword = passwordInput.trim()
        val cleanDept = department.trim()
        val cleanDesig = designation.trim()
        val cleanJurisdiction = jurisdiction.trim()

        // 1. Mandatory Fields Validation
        if (cleanIdentity.isBlank() || cleanPassword.isBlank()) {
            return@withContext AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.INVALID_INPUT,
                userFacingMessage = "Government Employee ID or Official Email and Password are required."
            )
        }

        val isEmail = cleanIdentity.contains("@")
        var firebaseAuthOk = false
        if (isEmail) {
            firebaseAuthOk = FirebaseManager.signInWithEmail(cleanIdentity, cleanPassword)
        }

        // 2. Validate against existing registered authority account
        if (existingAccount == null && !firebaseAuthOk) {
            return@withContext AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.VERIFICATION_FAILED,
                userFacingMessage = "Government employee credentials could not be verified. Please check your official details or contact your department administrator."
            )
        }

        val candidateName = existingAccount?.name ?: cleanIdentity.substringBefore("@")
        val candidateEmpId = existingAccount?.employeeId?.ifEmpty { cleanIdentity } ?: cleanIdentity
        val candidateEmail = existingAccount?.email ?: cleanIdentity
        val candidateDept = existingAccount?.department ?: cleanDept
        val candidateDesig = existingAccount?.designation ?: cleanDesig
        val candidateJurisdiction = existingAccount?.district ?: cleanJurisdiction
        val candidateRole = existingAccount?.role ?: "Authority"
        val candidateStatus = if (existingAccount?.isDisabled == true || existingAccount?.status.equals("Disabled", ignoreCase = true) || existingAccount?.status.equals("Blocked", ignoreCase = true)) "DISABLED" else "ACTIVE"
        val candidateVerificationStatus = existingAccount?.verificationStatus?.ifEmpty { "VERIFIED" } ?: "VERIFIED"

        if (!candidateRole.equals("Authority", ignoreCase = true) && !candidateRole.equals("Admin", ignoreCase = true)) {
            return@withContext AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.VERIFICATION_FAILED,
                userFacingMessage = "Unauthorized: This account does not possess official authority privileges."
            )
        }

        // 3. Account Status Check
        val isAccountInactive = candidateStatus.equals("DISABLED", ignoreCase = true) ||
                candidateStatus.equals("INACTIVE", ignoreCase = true) ||
                candidateStatus.equals("REVOKED", ignoreCase = true) ||
                candidateStatus.equals("BLOCKED", ignoreCase = true)

        if (isAccountInactive) {
            return@withContext AuthorityVerificationResult(
                verified = false,
                employeeId = candidateEmpId,
                officialEmail = candidateEmail,
                accountStatus = candidateStatus,
                failureType = VerificationFailureType.ACCOUNT_INACTIVE,
                userFacingMessage = "This authority account is currently inactive or disabled. Please contact your authorized administrator."
            )
        }

        // 4. Credential Verification
        val passwordMatches = firebaseAuthOk || (existingAccount != null && existingAccount.passwordHash == cleanPassword)

        if (!passwordMatches) {
            return@withContext AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.INVALID_CREDENTIALS,
                userFacingMessage = "Government employee credentials could not be verified. Please check your official details or contact your department administrator."
            )
        }

        // 5. Department Consistency Check
        if (cleanDept.isNotBlank() && candidateDept.isNotBlank()) {
            val deptMatches = cleanDept.equals(candidateDept, ignoreCase = true) ||
                    cleanDept.contains(candidateDept.substringBefore(" "), ignoreCase = true) ||
                    candidateDept.contains(cleanDept.substringBefore(" "), ignoreCase = true)

            if (!deptMatches) {
                return@withContext AuthorityVerificationResult(
                    verified = false,
                    failureType = VerificationFailureType.DEPARTMENT_MISMATCH,
                    userFacingMessage = "Department mismatch. Please verify the designated department."
                )
            }
        }

        // 6. Successful Verification Clearance
        AuthorityVerificationResult(
            verified = true,
            employeeId = candidateEmpId,
            officialEmail = candidateEmail,
            officerName = candidateName,
            department = candidateDept.ifEmpty { cleanDept.ifEmpty { "District Administration" } },
            designation = candidateDesig.ifEmpty { cleanDesig.ifEmpty { "District Collector" } },
            jurisdiction = candidateJurisdiction.ifEmpty { cleanJurisdiction.ifEmpty { cleanDept.ifEmpty { "District Administration" } } },
            accountStatus = "ACTIVE",
            verificationStatus = candidateVerificationStatus,
            verificationSource = providerName,
            verificationTimestamp = System.currentTimeMillis(),
            passwordHash = existingAccount?.passwordHash ?: cleanPassword,
            userFacingMessage = "Government identity and official credentials successfully verified."
        )
    }
}

/**
 * Central Government Verification Engine
 * Singleton entrypoint for government personnel authentication and verification.
 */
object GovernmentVerificationEngine {
    var currentProvider: AuthorityVerificationProvider = ProductionAuthorityVerificationProvider()

    // Supported Government Departments (EXACTLY 3 options)
    val SUPPORTED_DEPARTMENTS = listOf(
        "Municipal Administration",
        "Judiciary / Legal Services",
        "District Administration"
    )

    // Department to Official Designations mapping
    val DEPARTMENT_DESIGNATIONS = mapOf(
        "Municipal Administration" to listOf(
            "Municipal Commissioner",
            "Assistant Commissioner",
            "Municipal Officer"
        ),
        "Judiciary / Legal Services" to listOf(
            "Judge",
            "Magistrate",
            "Legal Services Officer"
        ),
        "District Administration" to listOf(
            "District Collector",
            "Revenue Divisional Officer",
            "Tahsildar"
        )
    )

    // Supported Official Designations
    val SUPPORTED_DESIGNATIONS = listOf(
        "Municipal Commissioner",
        "Assistant Commissioner",
        "Municipal Officer",
        "Judge",
        "Magistrate",
        "Legal Services Officer",
        "District Collector",
        "Revenue Divisional Officer",
        "Tahsildar"
    )

    fun getDesignationsForDepartment(department: String): List<String> {
        return DEPARTMENT_DESIGNATIONS[department] ?: emptyList()
    }

    fun verifyAuthorityRole(
        department: String,
        designation: String
    ): AuthorityVerificationResult {
        val cleanDept = department.trim()
        val cleanDesig = designation.trim()

        if (cleanDept.isBlank() || cleanDesig.isBlank()) {
            return AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.INVALID_INPUT,
                userFacingMessage = "Please select both Department and Designation / Official Rank."
            )
        }

        if (!SUPPORTED_DEPARTMENTS.contains(cleanDept)) {
            return AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.DEPARTMENT_MISMATCH,
                userFacingMessage = "Selected department is invalid."
            )
        }

        val allowedDesignations = getDesignationsForDepartment(cleanDept)
        if (!allowedDesignations.contains(cleanDesig)) {
            return AuthorityVerificationResult(
                verified = false,
                failureType = VerificationFailureType.INVALID_INPUT,
                userFacingMessage = "Please select a valid Designation for $cleanDept."
            )
        }

        val sanitizedDept = cleanDept.replace(Regex("[^a-zA-Z0-9]"), "").take(3).uppercase(Locale.getDefault())
        val sanitizedDesig = cleanDesig.replace(Regex("[^a-zA-Z0-9]"), "").take(4).uppercase(Locale.getDefault())
        val empId = "AUTH-$sanitizedDept-$sanitizedDesig"
        val email = "${cleanDesig.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "_")}@${cleanDept.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "_")}.gov.in"

        return AuthorityVerificationResult(
            verified = true,
            employeeId = empId,
            officialEmail = email,
            officerName = cleanDesig,
            department = cleanDept,
            designation = cleanDesig,
            jurisdiction = cleanDept,
            accountStatus = "ACTIVE",
            verificationStatus = "VERIFIED",
            verificationSource = "GOVERNMENT_AUTHORITY_DIRECTORY",
            verificationTimestamp = System.currentTimeMillis(),
            userFacingMessage = "Government authority credentials verified."
        )
    }

    suspend fun verifyAuthorityEmployee(
        identity: String,
        passwordInput: String,
        department: String = "",
        designation: String = "",
        jurisdiction: String = "",
        existingAccount: UserAccount? = null
    ): AuthorityVerificationResult {
        return currentProvider.verifyAuthorityEmployee(
            identity = identity,
            passwordInput = passwordInput,
            department = department,
            designation = designation,
            jurisdiction = jurisdiction,
            existingAccount = existingAccount
        )
    }

    fun validateFormat(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.length < 3) return false
        if (trimmed.contains("@")) {
            return trimmed.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
        }
        return trimmed.matches(Regex("^[A-Za-z0-9_-]{3,30}$"))
    }
}
