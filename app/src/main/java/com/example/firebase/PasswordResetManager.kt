package com.example.firebase

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Enterprise-Grade Firebase Authentication Password Reset Manager.
 * Uses official Firebase Authentication Password Reset Email delivery.
 * Enforces Google-Only account detection, email formatting checks,
 * network status validation, and rate-limiting / error translation.
 */
object PasswordResetManager {
    private const val TAG = "PasswordResetManager"

    /**
     * Check if email exists exclusively as a Google Sign-In account.
     */
    suspend fun isGoogleOnlyAccount(email: String): Boolean = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val normalizedEmail = email.trim().lowercase()
        try {
            val result = auth.fetchSignInMethodsForEmail(normalizedEmail).await()
            val methods = result.signInMethods ?: emptyList()
            val hasGoogle = methods.contains("google.com")
            val hasPassword = methods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD) || methods.contains("password")
            hasGoogle && !hasPassword
        } catch (e: Exception) {
            Log.w(TAG, "Notice checking sign-in methods: ${e.message}")
            false
        }
    }

    /**
     * Send Real Firebase Authentication Password Reset Email.
     * Uses FirebaseAuth.getInstance().sendPasswordResetEmail(email).
     */
    suspend fun sendPasswordResetEmail(
        email: String,
        onResult: (success: Boolean, message: String, isGoogleOnly: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            withContext(Dispatchers.Main) {
                onResult(false, "Please enter a valid email address.", false)
            }
            return@withContext
        }

        // 1. Google-Only Account Guard
        if (isGoogleOnlyAccount(normalizedEmail)) {
            withContext(Dispatchers.Main) {
                onResult(false, "This account uses Google Sign-In. Please continue with Google.", true)
            }
            return@withContext
        }

        // 2. Dispatch real Firebase Authentication Password Reset Email
        try {
            val auth = FirebaseAuth.getInstance()
            auth.sendPasswordResetEmail(normalizedEmail).await()
            Log.d(TAG, "Real Firebase password reset email sent to $normalizedEmail")
            withContext(Dispatchers.Main) {
                onResult(
                    true,
                    "Password reset link sent. Please check your email and follow the instructions to create a new password.",
                    false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendPasswordResetEmail failed: ${e.message}", e)
            val errorMessage = when (e) {
                is FirebaseNetworkException ->
                    "Unable to connect to the server. Please check your internet connection and try again."

                is FirebaseTooManyRequestsException ->
                    "Too many password reset requests. Please wait and try again later."

                is FirebaseAuthInvalidCredentialsException ->
                    "Please enter a valid email address."

                is FirebaseAuthInvalidUserException -> {
                    // For account enumeration protection, Firebase might throw invalid user.
                    // Deliver safe response or generic notification.
                    "If an account exists for this email, a password reset link has been sent. Please check your inbox."
                }

                else -> {
                    val msg = e.message ?: ""
                    if (msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true)) {
                        "Unable to connect to the server. Please check your internet connection and try again."
                    } else if (msg.contains("too-many-requests", ignoreCase = true) || msg.contains("TOO_MANY_ATTEMPTS_TRY_LATER", ignoreCase = true)) {
                        "Too many password reset requests. Please wait and try again later."
                    } else if (msg.contains("badly formatted", ignoreCase = true) || msg.contains("invalid email", ignoreCase = true)) {
                        "Please enter a valid email address."
                    } else {
                        "Unable to send the password reset email. Please try again later."
                    }
                }
            }

            withContext(Dispatchers.Main) {
                // If account enumeration returned the safe message, we can mark as true so user is notified appropriately
                val isSafeEnumerationResponse = e is FirebaseAuthInvalidUserException
                onResult(isSafeEnumerationResponse, errorMessage, false)
            }
        }
    }
}
